# 6-05 校园资讯、留言、系统通知与分析聚合设计

> 状态：**M1 设计准备，未排期**。本文是设计草案，不是实现授权，不代表数据库或接口已冻结。
>
> 依据：`docs/分工.md:97-98`（6-05/6-06 M1）、`docs/api-contracts.md`、`docs/architecture.md`、`docs/database-design.md`、`docs/缺口模块清单.md`、`功能架构优化方案.md` 6.3/6.4/6.5/6.8。`docs/team-division-plan.md` 是历史六人方案，不作为职责依据。
>
> 范围：第六组后端 6A 的设计准备。不写业务代码、不做前端、不实现第 1～5 组的数据生产能力，不实现 AI。

## 1. 职责、范围与非目标

### 1.1 职责

6-05 负责四类能力：

1. 校园资讯：组织范围内的资讯草稿、发布、撤回和归档。
2. 留言：师生/管理员之间的线程、回复、内部备注、关闭和已读。
3. 系统通知：模板、逻辑发送任务、每次发送尝试与送达/已读记录。
4. 聚合契约：教学过程分析、教学问题研判的只读聚合接口。

### 1.2 非目标

- 不拥有课程、教学班、成员、课堂、作业、成绩、学习轨迹数据。
- 不重算正式成绩、不生成学生能力分数、不实现 AI 研判或聊天。
- 本轮不新增任何实现代码或测试代码；只修改文档和 SQL 草案。
- 不实现前端页面、Vue 工程、Controller、Service、Mapper、配置或测试。
- 不修改 M0 两段 schema，不执行任何 SQL，不创建迁移挂接。
- 不把 `edu_user_notice` 改造成系统通知中心，不扩展 `notice_kind`。
- 不实现第 1～5 组的授权、成员、课堂、作业或统计服务。

### 1.3 排期

`docs/分工.md:32,136` 明确 M1 需单独估时、分配 Issue 后开工；当前 M1 未排期。本文和六表 DDL 只供评审。

## 2. 总体设计

### 2.1 六表

六表 DDL 见 [mysql-v3-m1-communication-schema.sql](../sql/mysql-v3-m1-communication-schema.sql)，文件头已标“待评审，禁止执行”。表名、拆分和字段均为建议，不是冻结 schema；如评审提出不同划分，需说明依据和取舍。

| 表 | 职责 | 关键关系 |
| --- | --- | --- |
| `edu_campus_news` | 校园资讯主记录 | 独立；`organization_id` 逻辑引用组织 |
| `edu_message_thread` | 留言线程主记录 | 独立；`scope_ref_id` 逻辑引用课程/教学班 |
| `edu_message_reply` | 线程回复与内部备注 | `(thread_id, organization_id)` → `edu_message_thread (id, organization_id)` |
| `edu_notification_template` | 系统通知模板及版本 | 独立 |
| `edu_notification_send_job` | 逻辑发送任务 | `(template_id, organization_id, template_version, channel)` → 模板 |
| `edu_notification_send_record` | 逻辑通知的每接收人/每次尝试记录 | `(job_id, organization_id, channel)` → 发送任务 |

- 六表只使用六表内部外键；组织、用户、课程、教学班、课堂、作业、成绩均为逻辑引用，不建跨组外键。
- 所有时间 `DATETIME(3)`，按 UTC 写入/比较，接口按 RFC 3339 返回。
- 所有主键 `BIGINT AUTO_INCREMENT`；接口 ID 建议字符串传输，待评审。
- 六表均不物理删除业务主记录；使用状态/软删除/归档保留审计。

### 2.2 逻辑发送、逻辑通知与发送尝试

- `edu_notification_send_job` 表示“一次逻辑发送”：一个模板版本、一个固定渠道、一个受众快照、一个幂等键。
- 用户看到的一条逻辑通知身份为 `(job_id, recipient_user_id)`；`edu_notification_send_record` 的 `id` 是某次发送尝试的标识。
- 同一逻辑通知重试多次时，旧的当前尝试通过 `superseded_at` 标记失效，`active_notification_key` 唯一约束保证每个 `(job_id, recipient_user_id)` 最多一条当前记录。
- 历史尝试保留给管理审计，不进入用户通知列表；`PENDING/FAILED/UNKNOWN` 不计入个人未读。
- `UNKNOWN` 不等于失败：没有明确证据时不得自动重发，先对账或人工补偿。

### 2.3 模板与任务的组织一致性

- 模板候选键：`(id, organization_id, template_version, channel)`；任务外键使用 `(template_id, organization_id, template_version, channel)`，因此不同组织不能引用同一模板版本，不存在的版本也会被数据库拒绝。
- 任务创建时从模板复制 `channel`，一个 job 固定一个渠道；发送记录再以 `(job_id, organization_id, channel)` 绑定任务，避免记录串渠道。
- `organization_id` 必须来自服务端可信身份/组织上下文，不能取客户端参数；数据库约束只防错配，不替代接口授权。

静态验收案例（设计预期，未执行）：

| 场景 | 预期 |
| --- | --- |
| 同组织、正确模板版本和渠道 | 允许插入 job |
| 不同组织引用同一模板版本 | 外键拒绝 |
| 不存在的模板版本 | 外键拒绝 |
| job 与记录渠道不一致 | 记录外键拒绝 |

## 3. 校园资讯设计

### 3.1 状态与规则

状态：`DRAFT → PUBLISHED → WITHDRAWN`；`DRAFT/PUBLISHED/WITHDRAWN → ARCHIVED`。撤回后 M1 不直接重新发布，需新建版本或由产品确认。

- 发布：校验组织范围、操作者权限、受众；写 `published_at/published_by_user_id`；同一状态重复发布幂等。
- 撤回：必须填写原因；记录 `withdrawn_at/by/reason`；立即从普通列表隐藏，详情按状态返回 404/403。
- 归档：记录 `archived_at/by`；归档内容只读保留。
- 正文按白名单清洗；封面只存私有对象键，不存公开 URL。

### 3.2 受众

M1 建议仅 `audience_kind='ORGANIZATION'`，`audience_ref_id` 为空。`ROLE`/`TEACHING_CLASS` 为预留枚举，启用前需产品确认和额外授权校验；多受众/指定用户不在六表内，需评审是否增加受众表。

### 3.3 列表与对象授权

- 教师/学生：本组织内 `PUBLISHED` 且未过撤回/归档的资讯；不返回其他组织内容。
- 管理员：本组织全部状态；撤回/归档需权限。
- 列表不返回正文；详情返回清洗后的正文和可选临时封面地址。

## 4. 留言设计

### 4.1 线程与首条正文

`edu_message_thread` 字段表达：

- 组织：`organization_id`。
- 范围：`scope_kind=PLATFORM/COURSE/TEACHING_CLASS`，`scope_ref_id` 为课程空间/教学班逻辑 ID。
- 参与：`initiator_user_id/initiator_role`，`target_role=TEACHER/ADMIN`，`target_user_id` 可空（空表示角色池）。
- 状态：`OPEN`（等待处理人）、`WAITING`（等待发起人）、`CLOSED`（关闭只读）。
- 计数与已读：`last_reply_at/reply_count`，`initiator_last_read_at/target_last_read_at`。
- 创建幂等：`create_idempotency_key` + `create_request_hash`，唯一键 `(organization_id, initiator_user_id, create_idempotency_key)`。
- `thread_no` 是组织内业务编号，不是请求幂等键；不能只靠 `thread_no` 唯一宣称创建请求已幂等。
- `target_last_read_at` 是角色池共享已读时间：多个管理员/教师处理同一线程时只能近似表示；若需逐人已读，需增加参与者/已读表，列入评审。

创建线程的正文不放在线程表：同一事务插入线程主记录和首条 `edu_message_reply`（`sequence_no=1`）。首条回复使 `reply_count=1`、`last_reply_at=created_at`、`last_reply_by_user_id=发起人`，线程状态为 `OPEN`（等待目标处理人）。创建请求重试时先按创建幂等键查找线程，返回原线程和首条回复，不重复插入。

### 4.2 回复与内部备注

`edu_message_reply`：

- `sequence_no`：线程内递增序号；首条正文为 1，后续回复事务内分配。
- `sender_user_id/sender_role`、`body`、`created_at`。
- `idempotency_key` + `request_hash`，唯一键 `(thread_id, sender_user_id, idempotency_key)`；普通回复、管理员回复、内部备注共用。
- `visibility=PARTICIPANTS/ADMIN_INTERNAL`；内部备注不返回给非管理员参与者。
- 软删除：`deleted_at/deleted_by_user_id/delete_reason`；删除后保留行用于审计，列表过滤；唯一键仍占用，幂等身份不释放。
- `thread_id + organization_id` 复合外键保证线程与回复同组织；`(thread_id, sequence_no)` 唯一防止重复序号。

### 4.3 幂等、状态与并发

- 创建：`Idempotency-Key` 作用域 `(organization_id, initiator_user_id, key)`；同键同 `request_hash` 返回原线程和首条回复；同键不同载荷返回 409；并发相同请求由唯一键和事务保证只插入一次。
- 回复：`Idempotency-Key` 作用域 `(thread_id, sender_user_id, key)`；同键同载荷返回原回复，不重复插入、不重复增加 `reply_count`；同键不同载荷 409。
- 回复事务：`SELECT ... FOR UPDATE` 锁定线程，校验非 `CLOSED`，分配 `sequence_no`（`MAX(sequence_no)+1`），插入回复，更新 `last_reply_at/reply_count` 和状态：目标角色回复后 `WAITING`，发起人回复后 `OPEN`。
- 软删除不释放幂等键；重试同键仍返回已删除的原记录，由调用方提示不可见，不创建新回复。
- 关闭：发起人、目标处理人或具有相应组织范围和操作权限的 admin 用户；记录原因；关闭后拒绝新回复。
- 已读：`POST /read` 只更新当前角色对应时间；不因列表浏览自动已读。
- 限流：建议按用户/组织限制创建和回复频率；超限 429。阈值待产品确认。

### 4.4 幂等静态验收案例（设计预期，未执行）

| 场景 | 预期 |
| --- | --- |
| 创建线程同键同载荷重试 | 只存在一条线程和一条 `sequence_no=1` 回复；`reply_count=1`；返回原 `threadId/firstReplyId` |
| 创建线程同键不同正文 | 409，不插入第二线程 |
| 并发两个相同创建请求 | 唯一键只允许一个成功；另一个读取并返回原结果 |
| 普通/管理员回复同键同载荷重试 | 只存在一条回复；`reply_count` 不重复增加 |
| 回复同键不同正文 | 409，不插入第二回复 |
| 首条/后续回复软删除后用同键重试 | 返回原已删除记录；不新建回复，不释放幂等键 |

## 5. 系统通知设计

### 5.1 模板与版本

`edu_notification_template`：

- `template_code` 在组织内稳定；`template_version` 递增，历史版本保留。
- `active_template_code` 生成列 + 唯一键保证同一组织同一编码最多一个 `ACTIVE` 版本。
- 候选键 `(id, organization_id, template_version, channel)` 供任务复合外键使用，保证模板版本、组织和渠道一致。
- `channel` 预留 `IN_APP/EMAIL/SMS`；M1 仅启用 `IN_APP`。
- `title_template/body_template` 只允许 `${variableName}`；`variable_schema_json` 声明类型、必填、长度；禁止 EL/SpEL/脚本/任意表达式。
- 模板 `DRAFT → ACTIVE → ARCHIVED`；更新模板产生新版本，不原地改已激活版本。

### 5.2 逻辑发送任务

`edu_notification_send_job`：

- `channel` 创建时从模板复制并固定；一个 job 一个渠道，不允许混用。
- `idempotency_key` 在组织内唯一；`request_hash` 检测同键不同载荷。
- `audience_kind=ORGANIZATION/ROLE/TEACHING_CLASS/USER_LIST`；`audience_ref_id`/`audience_json` 表达受众，创建时冻结目标逻辑接收人；重试沿用同一批接收人，不重新解析最新名单。
- `payload_json` 是已校验的模板变量；不含凭据/敏感原文。
- 状态：`PENDING → PROCESSING → SUCCEEDED/PARTIAL/FAILED/UNKNOWN`；`CANCELLED` 仅从 `PENDING`。
- 计数：`total_count/success_count/failure_count/unknown_count` 按逻辑接收人统计，不按尝试行数累加。
- `lease_owner/lease_expires_at` 表示处理权；`next_retry_at` 表示下次自动重试时间；`row_version` 乐观锁。
- 创建任务在事务内完成幂等键和初始计数；实际发送在事务外执行，不长时间持锁。

### 5.3 逻辑通知与发送记录

`edu_notification_send_record`：

- 逻辑通知身份：`(job_id, recipient_user_id)`，对外用不透明 `notificationId`；尝试记录身份：发送记录 `id`，并带 `attemptNo`。
- `superseded_at` 标记旧尝试失效；`active_notification_key` 生成列 + 唯一键保证每个逻辑通知最多一条当前记录。标记 `superseded_at` 时同一事务清空旧行 `next_retry_at`。
- 唯一键 `(job_id, recipient_user_id, attempt_no)`；重试新增 `attempt_no`，不覆盖历史。`attempt_no` 上限 4（首次 + 最多 3 次重试）。
- `delivery_status=PENDING/SENT/DELIVERED/READ/FAILED/UNKNOWN`。
- 用户列表只返回当前有效且 `SENT/DELIVERED/READ` 的记录；`PENDING/FAILED/UNKNOWN` 不进入个人列表、不计未读。历史尝试只用于管理审计。
- 未读统计只统计当前有效、`read_at IS NULL` 且已送达的记录；同一逻辑通知最多计一次。
- 显式已读更新当前有效记录；后续迟到回执只更新同一记录，不恢复未读；已读后不再自动重试。若必须补偿，创建新逻辑 job，而不是复活旧通知。
- job 的成功/失败/未知人数按当前有效记录统计；不能把多次失败尝试累加为多个失败人。
- `sent_at` 平台接受；`delivered_at` 渠道确认；`read_at` 用户显式已读；站内 `SENT/DELIVERED` 可同时间。
- `provider_message_id` 用于对账；`error_code/error_message` 只存受控短文本；`next_retry_at` 仅当前有效 `FAILED`。
- 发送记录保留审计，不物理删除。

### 5.4 领取、崩溃恢复与重试闭环

- 目标名单冻结：创建 job 时把受众解析为逻辑接收人快照（`audience_json` 和初始 `total_count`）；自动/手动重试只使用该快照，不重新读取最新班级名单；名单变化需创建新 job。
- 领取：worker 以条件更新领取，概念条件为 `status='PENDING' AND (next_retry_at IS NULL OR next_retry_at <= now) AND (lease_owner IS NULL OR lease_expires_at < now)`；成功后写 `status='PROCESSING'`、`lease_owner`、`lease_expires_at`、清空 `next_retry_at`，并增加 `row_version`。结束、失败、取消时清空租约字段。
- 崩溃恢复：租约过期的 `PROCESSING` job 由恢复 worker 重新领取；不能只把 `PROCESSING` 重置为 `PENDING`，必须先按逻辑通知当前记录检查已提交结果，再决定补发、标记 UNKNOWN 或人工处理。
- 站内恢复判据：当前有效记录为 `SENT/DELIVERED/READ` 表示事务已提交，不重发；为 `PENDING` 表示尚未完成，可在新租约下继续该 attempt；没有当前记录表示落库事务未提交，可安全重试。`active_notification_key` 唯一约束防止恢复产生第二条当前记录。
- 旧 worker 防护：所有结果写回必须带 `lease_owner` 且租约未过期（或等价的 fencing 条件）；旧 worker 延迟返回时更新失败，不能覆盖新处理者结果。
- 自动重试与人工重试互斥：两者都通过 job 状态/租约条件更新；`PROCESSING` 期间禁止人工重试。`attempt_no` 在 `(job_id, recipient_user_id)` 范围内事务内分配，唯一键保证并发不重复。
- 合法状态转换：`PENDING → PROCESSING`；`PROCESSING → SUCCEEDED/PARTIAL/FAILED/UNKNOWN`；`FAILED/PARTIAL → PENDING` 仅在还有重试额度且 `next_retry_at` 到期时由自动任务执行；`UNKNOWN → PENDING` 仅允许人工对账后显式恢复；`SUCCEEDED/CANCELLED` 终态；`CANCELLED` 只能从 `PENDING`。
- “重试三次”统一解释为首次之外最多三次，即 `attempt_no=1..4`；超限后只能新建 job 做补偿，不能继续增加 attempt。
- M1 仅 `IN_APP`：站内投递是本地数据库事务，不是外部供应商调用。事务提交成功即 `SENT`；提交结果不确定时按 `active_notification_key` 查询当前记录，存在则视为成功，不存在才允许重试。站内没有供应商回执，UNKNOWN 不作为自动重试依据。
- `EMAIL/SMS` 仅保留字段和枚举，不启用；未来外部渠道需供应商幂等键、回执对账和独立评审，不能把站内事务语义直接套用。

### 5.5 逻辑通知场景推演（设计预期，未执行）

| 场景 | 用户列表/未读 | job 计数/状态 |
| --- | --- | --- |
| 首次 FAILED，重试成功，再已读 | 列表仅一条当前记录；未读从 1 变 0 | job SUCCEEDED；当前记录 READ；成功人数按逻辑接收人计 1 |
| 连续 3 次 FAILED，第 4 次仍失败 | 列表无该通知；未读 0 | 当前记录 FAILED；attempt 到上限，job FAILED，不再自动重试 |
| UNKNOWN（站内提交结果不确定） | 列表无该通知；未读 0 | 当前记录 UNKNOWN；不自动重试；查询 active key 后人工对账 |
| 同一 attempt 重复回执 | 列表仍一条；已读时间不被回执重置 | 同一记录幂等更新，不新增 attempt |
| 已读后迟到回执 | 列表仍一条；`read_at` 保留 | 只更新非已读字段；不得恢复未读 |
| 旧 attempt 迟到回执 | 不影响当前列表 | 按 attempt id 识别为历史；不覆盖当前记录/job 计数 |

## 6. 教学过程分析与教学问题研判

### 6.1 聚合契约

只消费第 2/4/5 组已授权契约，不直读其业务表：

- 范围：`organization_id` + `scopeKind=ORGANIZATION/COURSE/TEACHING_CLASS` + `scopeId`。
- 时间：`window.startsAt/endsAt` 左闭右开，UTC；默认当前学期，可选最近 30 天（待确认）。
- 口径：`definitionVersion`、`source`、`evidenceCutoffAt`、`asOf`。
- 数值：`value`、`unit`、`denominator`、`sampleCount`；缺失/分母为 0 时为 `NO_DATA`，源不可用为 `UNAVAILABLE`，均不回退为 0。
- 单源失败只影响对应指标；不因一个源失败丢弃整个响应。

### 6.2 教学问题研判（本轮只读候选）

- 只输出“候选问题”：`issueCode`、`severity`、`evidenceRefs`、`window`、`sampleCount`、`status`；`status` 仅为聚合状态 `OK/NO_DATA/UNAVAILABLE`。
- 不生成学生能力分数、不贴人格标签、不使用 AI 自动定性。
- 本轮不实现确认、通知、整改、复核、关闭及持久化工作流；不建问题状态表，不写相关接口，不虚构其他组已提供工作流。
- 上述工作流属于后续待立项能力，数据归属、操作权限、状态机和保留策略待确定。
- 问题分类、阈值、通知对象、整改期限仅作为后续问题记录，不构成本轮承诺。

## 7. 接口草案

> 路径、字段、错误码、权限编码均为草案，按 `docs/api-contracts.md:15` 待评审；不得作为冻结协议。

### 7.1 资讯

| 方法/路径 | 角色 | 说明 | 幂等/并发 |
| --- | --- | --- | --- |
| `GET /education/news` | 教师/学生/管理员 | 组织内已发布列表；分页 | 只读 |
| `GET /education/news/{newsId}` | 教师/学生/管理员 | 详情；对象级组织校验 | 只读 |
| `POST /education/admin/news` | 管理员 | 创建草稿 | `Idempotency-Key` 可选 |
| `PUT /education/admin/news/{newsId}` | 管理员 | 修改草稿；`rowVersion` | 乐观锁，409 |
| `POST /education/admin/news/{newsId}/publish` | 管理员 | 发布 | 状态幂等 |
| `POST /education/admin/news/{newsId}/withdraw` | 管理员 | 撤回；必填原因 | 状态幂等 |
| `POST /education/admin/news/{newsId}/archive` | 管理员 | 归档 | 状态幂等 |

请求字段：`title`、`summary`、`body`、`audienceKind`、`audienceRefId`、`rowVersion`。响应：`id`、`title`、`summary`、`status`、`publishedAt`、`withdrawnAt`、`audienceKind`、`audienceRefId`、`rowVersion`；详情含 `body`。

### 7.2 留言

| 方法/路径 | 角色 | 说明 |
| --- | --- | --- |
| `GET /education/messages` | 教师/学生 | 我的线程分页 |
| `GET /education/messages/{threadId}` | 参与者 | 线程与回复；对象级校验 |
| `POST /education/messages` | 教师/学生 | 创建线程 + 首条正文；`Idempotency-Key` 必填；同事务插入线程和 `sequence_no=1` 回复 |
| `POST /education/messages/{threadId}/replies` | 参与者 | 回复；`Idempotency-Key` 必填 |
| `POST /education/messages/{threadId}/read` | 参与者 | 更新本人角色已读时间 |
| `POST /education/messages/{threadId}/close` | 发起人/目标/有权限的 admin 用户 | 关闭；必填原因 |
| `GET /education/admin/messages` | 管理员 | 组织内线程分页 |
| `POST /education/admin/messages/{threadId}/replies` | 管理员 | 回复/内部备注；`Idempotency-Key` 必填 |
| `POST /education/admin/messages/{threadId}/close` | 管理员 | 关闭 |

字段：`threadNo`、`scopeKind`、`scopeRefId`、`targetRole`、`targetUserId`、`subject`、`body`、`visibility`、`closeReason`。分页 `pageNum/pageSize`，默认 1/20，最大 100；返回 `TableDataInfo` 的 `total/rows`。

创建请求的 `body` 是首条回复正文，不是线程表字段；响应返回 `threadId`、`threadNo`、`firstReplyId`、`replyCount=1`、`status=OPEN`。回复响应返回 `replyId`、`sequenceNo`、`createdAt`。`threadNo` 由服务端生成，是业务编号，不是幂等键；创建和回复分别使用 `Idempotency-Key`，作用域为 `(organization_id, initiator_user_id, key)` 和 `(thread_id, sender_user_id, key)`。同键同载荷返回原结果，同键不同载荷 409；软删除不释放幂等键。

### 7.3 系统通知

| 方法/路径 | 角色 | 说明 |
| --- | --- | --- |
| `GET/POST /education/admin/notification-templates` | 管理员 | 模板列表/新建 |
| `PUT /education/admin/notification-templates/{id}` | 管理员 | 新版本；`rowVersion` |
| `POST /education/admin/notification-templates/{id}/activate` | 管理员 | 激活；同编码唯一 ACTIVE |
| `POST /education/admin/notification-templates/{id}/archive` | 管理员 | 归档 |
| `POST /education/admin/notification-sends` | 管理员 | 创建逻辑发送；模板固定渠道；`Idempotency-Key` |
| `GET /education/admin/notification-sends` | 管理员 | 任务分页 |
| `GET /education/admin/notification-sends/{jobId}` | 管理员 | 任务与计数（按逻辑接收人） |
| `POST /education/admin/notification-sends/{jobId}/retry` | 管理员 | 仅非 PROCESSING 且 `attemptNo<4`；新增 attempt |
| `POST /education/admin/notification-sends/{jobId}/cancel` | 管理员 | 仅 PENDING 可取消 |
| `GET /education/notifications` | 教师/学生/管理员 | 本人当前有效逻辑通知分页；不含历史尝试 |
| `GET /education/notifications/unread-count` | 教师/学生/管理员 | 本人未读数；仅当前有效已送达未读 |
| `POST /education/notifications/{notificationId}/read` | 本人 | 显式已读逻辑通知；幂等 |

发送请求：`templateId`、`templateVersion`、`audienceKind`、`audienceRefId`、`audienceUserIds`、`payload`、`scheduledAt`。请求不传 `channel`；服务端从模板版本读取并固定。响应：`jobId`、`channel`、`status`、`totalCount`、`successCount`、`failureCount`、`unknownCount`。

幂等：`Idempotency-Key` 作用域 `(organization_id, key)`；同键同 `requestHash` 返回同一 job；同键不同载荷 409。重试创建新 `attempt_no`（最多 4），不覆盖旧记录。

用户通知响应：`notificationId`（逻辑通知，`jobId:recipientUserId` 的不透明封装）、`attemptId`、`attemptNo`、`deliveryStatus`、`sentAt`、`deliveredAt`、`readAt`。列表只返回 `superseded_at IS NULL` 且 `SENT/DELIVERED/READ` 的记录；`PENDING/FAILED/UNKNOWN` 不进入个人列表和未读。已读更新当前有效记录，迟到回执不得恢复未读；历史尝试只给管理审计。

### 7.4 分析

| 方法/路径 | 角色 | 说明 |
| --- | --- | --- |
| `GET /education/admin/teaching-process-analysis` | 管理员 | 组织/课程/教学班过程指标 |
| `GET /education/admin/teaching-issue-analysis` | 管理员 | 候选教学问题与证据 |

参数：`scopeKind`、`scopeId`、`from`、`to`、`definitionVersion`。响应：`asOf`、`window`、`definitionVersion`、`items[]`（`metricCode`、`value`、`unit`、`status`、`source`、`denominator`、`sampleCount`、`updatedAt`）、`issues[]`（`issueCode`、`severity`、`evidenceRefs`、`sampleCount`、`status`）。

状态：`OK/NO_DATA/UNAVAILABLE`；缺失/分母为 0 → `NO_DATA`；源授权/契约不可用 → `UNAVAILABLE`（写操作涉及教学班授权不可用时按 503 失败关闭）。

`issues[].status` 只是聚合结果状态，不是整改工作流状态；本轮没有确认、通知、整改、复核、关闭接口或持久化字段，后续工作流另行立项。

### 7.5 错误、分页与权限

- 错误：400 参数/状态；401 未登录；403 无权限/跨组织/跨班；404 不存在或不可见；409 状态/幂等冲突/乐观锁；429 限流；500 服务端；503 授权契约不可用（写操作失败关闭）。
- 分页：`pageNum` 默认 1、最小 1；`pageSize` 默认 20、最大 100；排序由服务端固定。
- 权限编码草案（未配置）：`education:news:list/read/add/edit/publish/withdraw/archive`、`education:message:list/read/create/reply/close/manage`、`education:notification:template:list/add/edit/activate/archive`、`education:notification:send:list/create/retry/cancel`、`education:notification:read`、`education:analysis:process:view`、`education:analysis:issue:view`。
- 对象级授权：资讯按组织；留言按参与者/目标角色/组织/教学班授权；通知按组织和管理员/本人；分析按组织/课程/教学班授权。第 1 组已有身份/角色基础能力；本模块仍缺 `education:*` 菜单/按钮权限配置、组织范围契约确认和真实联调证据。
- 接口 camelCase 字段对应 DDL snake_case；`rowVersion` 对应 `row_version`，`templateVersion` 对应 `template_version`，`audienceUserIds` 序列化到 `audience_json`。

## 8. 事务、并发、审计与保留

### 8.1 事务边界

- 资讯发布/撤回/归档：单事务状态 CAS + 操作者/时间/原因；不直接发通知；如后续需要通知，提交后创建发送任务。
- 留言创建：单事务校验范围、插入线程和 `sequence_no=1` 首条回复、设置 `reply_count=1/last_reply_at`；创建幂等键唯一防重。
- 留言回复：单事务锁定线程、分配 `sequence_no`、插入回复、更新计数/最后回复/状态；同键同载荷返回原回复，不重复计数。
- 通知发送：创建 job 单事务；worker 用状态+租约 CAS 领取；IN_APP 的站内落库事务与 job 状态写回分开设计，不长时间持有行锁；外部渠道调用本轮不实现。
- 分析：只读；不写业务表；是否需要快照待评审。

### 8.2 并发与锁

- 资讯/模板/线程/任务使用 `row_version` 或状态条件更新，检查受影响行数。
- 留言创建由唯一键 `(organization_id, initiator_user_id, create_idempotency_key)` 防并发重复；回复先 `SELECT ... FOR UPDATE` 锁线程，唯一键 `(thread_id, sequence_no)` 和 `(thread_id, sender_user_id, idempotency_key)` 防重复。
- 发送任务用状态+租约条件领取；同一 job 不并发执行，旧 worker 的写回必须带租约 fencing 条件。
- 发送记录唯一键 `(job_id, recipient_user_id, attempt_no)` 防重复尝试；`active_notification_key` 唯一键保证每个逻辑通知最多一条当前记录。
- 唯一键只防重复，不替代业务授权和状态校验。

### 8.3 重试与未知结果

- 首次之外最多重试 3 次，即 `attempt_no=1..4`；建议 60s/300s/1800s；每次新增 attempt 行。超过上限只能新建 job 补偿。
- `UNKNOWN` 不自动重试；站内渠道按 `active_notification_key` 查询当前记录作为内部证据，人工补偿需权限和审计；外部渠道未来按供应商回执对账。
- 重复提交由 `Idempotency-Key + request_hash` 拦截；同键不同载荷 409；软删除不释放幂等键。
- 不把超时直接判为失败；先查询/对账再决定重试；已读逻辑通知不再自动重试。

### 8.4 审计与保留

- 资讯发布/撤回/归档、留言关闭/隐藏、模板激活、发送/重试/取消/补偿记录操作者、时间、原因。
- 发送记录和 attempt 历史保留用于对账；M1 不物理删除。
- 资讯归档、留言软删除、线程关闭保留行；保留期限待学校政策确认。
- 审计可复用第 1 组审计能力；本次不实现。

### 8.5 索引与唯一键用途

- `edu_campus_news`：`(organization_id,status,published_at,id)` 支撑组织资讯列表；`(organization_id,audience_kind,audience_ref_id,status)` 支撑受众过滤。
- `edu_message_thread`：`(organization_id,status,last_reply_at,id)` 支撑管理员队列；`(initiator_user_id,created_at,id)` 支撑“我的留言”；`(target_user_id,status,last_reply_at,id)` 支撑处理人队列；`(organization_id,scope_kind,scope_ref_id,status)` 支撑课程/教学班过滤；`(organization_id,initiator_user_id,create_idempotency_key)` 防创建重复。
- `edu_message_reply`：`(thread_id,created_at,id)` 支撑线程详情；`(sender_user_id,created_at,id)` 支撑用户回复历史；`(thread_id,sequence_no)` 防重复序号；`(thread_id,sender_user_id,idempotency_key)` 防回复重复。
- `edu_notification_template`：`(organization_id,status,channel)` 支撑模板列表；`(organization_id,template_code,template_version)` 保留版本；`(organization_id,active_template_code)` 保证同编码唯一 ACTIVE；`(id,organization_id,template_version,channel)` 供任务复合外键使用。
- `edu_notification_send_job`：`(organization_id,status,scheduled_at,id)` 支撑待发送扫描；`(organization_id,template_id,template_version,channel)` 支撑模板审计与组织一致；`(organization_id,idempotency_key)` 防重复任务；`(status,lease_expires_at)` 支撑过期租约恢复；`(status,next_retry_at)` 支撑重试扫描。
- `edu_notification_send_record`：`(recipient_user_id,superseded_at,read_at,delivery_status,created_at,id)` 支撑个人当前通知/未读；`(job_id,delivery_status,attempt_no)` 支撑任务重试；`(channel,provider_message_id)` 支撑供应商对账；`(next_retry_at)` 支撑失败重试扫描；`active_notification_key` 唯一约束保证逻辑通知当前记录唯一。

## 9. 与 6-02 的边界

- 6-05 使用独立六表；不修改 `edu_user_notice`，不增加 `NoticeKind`，不 import 6-02 代码。
- 不复用 `UpstreamNoticeEventPort`；该端口只接收 PREVIEW/ACTIVITY/ASSIGNMENT/GRADE，拒绝 ANNOUNCEMENT，且无 HTTP 端点。
- `edu_user_notice`（6-02）是课程/教学事件通知与铃铛未读；`edu_notification_send_record`（6-05）是系统通知模板发送的送达/已读记录。两者不得互相冒充。
- 不允许同一系统通知同时写 6-02 铃铛和 6-05 发送记录造成重复计数；将来确需集成时，先定义唯一来源、映射、幂等和读模型，本次不实现适配器。
- 6-02 的 PR #18/#20/#21/#22 已合并到 main（`f21f51d55c363738c03ee6f626c7c67b904d6b36`）；6-05 已同步该 main，但仍使用独立六表，不基于 6-02 分支、不复用 6-02 实现。

## 10. 外部依赖与阻塞

- 第 2 组 Draft PR #26（head `3e74e875edf759a0887f49f56e5b82103339878b`）已有 `EduCourseAccessService` 草案，但 `activeMembers` 只返回当前有效成员，`EduMemberRef.joinedAt` 只有日期、没有 `leftAt` 或历史时点查询；不足以满足 6-02 事件时点入退班判断。**该草案未冻结、未接入、未完成消费者确认。**
- 第 1 组：已有若依身份/角色基础能力；本模块仍缺 `education:*` 菜单/按钮权限配置、组织范围契约确认、审计落库和真实联调证据。6-05 权限编码只是草案。
- 第 2 组：教学班/课程范围与成员授权；用于留言对象范围和教学班定向资讯。授权缺失或不可用时，写操作按 503 失败关闭。
- 第 4/5 组：课堂、作业、成绩、学习统计；用于教学过程分析。6-05 只消费契约，不重算正式成绩，不伪造能力分数。
- 6-02：已合并到 main；通知/铃铛边界仍未联调，本设计不依赖其实现，也不写 `edu_user_notice`。
- `docs/缺口模块清单.md` 建议留言可作 M0 补充或 M1；当前以 `docs/分工.md` 的 M1 为准，不扩大 6A 职责。
- 六表 DDL 未执行；未验证 MySQL 8.0.46；不得声称已通过真实数据库验证。

## 11. 后续验证清单

- [ ] 产品确认第 1 节所有“建议默认值”。
- [ ] 第 2/4/5 组确认分析指标、来源、分母、样本、时间窗和口径版本。
- [ ] 第 1 组确认权限编码、组织范围、审计和数据保留。
- [ ] 第 2 组确认教学班/课程范围与成员契约，明确历史时点能力。
- [ ] 六表 DDL 在独立、可丢弃的 MySQL 8.0.43/8.0.46 测试库评审执行；记录版本和约束测试。
- [ ] 验证唯一键、CHECK、外键、索引、并发领取、幂等、重试和未知结果。
- [ ] 静态/测试验证同组织正确模板允许、跨组织引用拒绝、不存在模板版本拒绝、job/记录渠道不一致拒绝。
- [ ] 验证创建线程只插入一条首条回复、`reply_count=1`；同键同载荷不重复插入、不重复计数；同键不同载荷 409；软删除不释放幂等键。
- [ ] 验证同一逻辑通知多次重试个人列表最多一条、未读最多一次；显式已读后迟到回执不恢复未读；job 计数按逻辑接收人。
- [ ] 验证租约领取、崩溃恢复、旧 worker fencing、自动/人工重试互斥、`attempt_no=1..4` 上限。
- [ ] 确认问题研判仅只读候选，无确认/通知/整改/关闭持久化接口。
- [ ] 接口契约由 6B 与第 1/2/4/5 组共同评审；路径/字段/错误码冻结后再实现。
- [ ] 真实 HTTP、登录、角色拦截、审计落库联调另行安排。
