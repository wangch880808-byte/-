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
- 不实现前端页面、Vue 工程、MockMvc 之外的业务代码。
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
| `edu_message_reply` | 线程回复与内部备注 | `thread_id` → `edu_message_thread.id` |
| `edu_notification_template` | 系统通知模板及版本 | 独立 |
| `edu_notification_send_job` | 逻辑发送任务 | `template_id, template_version` → 模板 |
| `edu_notification_send_record` | 每接收人/每次尝试的发送记录 | `job_id` → 发送任务 |

- 六表只使用六表内部外键；组织、用户、课程、教学班、课堂、作业、成绩均为逻辑引用，不建跨组外键。
- 所有时间 `DATETIME(3)`，按 UTC 写入/比较，接口按 RFC 3339 返回。
- 所有主键 `BIGINT AUTO_INCREMENT`；接口 ID 建议字符串传输，待评审。
- 六表均不物理删除业务主记录；使用状态/软删除/归档保留审计。

### 2.2 逻辑发送与发送尝试

- `edu_notification_send_job` 表示“一次逻辑发送”：一个模板版本、一个受众、一个幂等键。
- `edu_notification_send_record` 表示“某接收人的某次尝试”：重试产生新的 `attempt_no` 行，保留历史。
- `UNKNOWN` 不等于失败：供应商超时/网络中断时不得自动重发，先对账或人工补偿。

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

### 4.1 线程

`edu_message_thread` 字段表达：

- 组织：`organization_id`。
- 范围：`scope_kind=PLATFORM/COURSE/TEACHING_CLASS`，`scope_ref_id` 为课程空间/教学班逻辑 ID。
- 参与：`initiator_user_id/initiator_role`，`target_role=TEACHER/ADMIN`，`target_user_id` 可空（空表示角色池）。
- 状态：`OPEN`（等待处理）、`WAITING`（等待发起人）、`CLOSED`（关闭只读）。
- 计数与已读：`last_reply_at/reply_count`，`initiator_last_read_at/target_last_read_at`。
- `target_last_read_at` 是角色池共享已读时间：多个管理员/教师处理同一线程时只能近似表示；若需逐人已读，需增加参与者/已读表，列入评审。

### 4.2 回复

`edu_message_reply`：

- `sender_user_id/sender_role`、`body`、`created_at`。
- `visibility=PARTICIPANTS/ADMIN_INTERNAL`；内部备注不返回给非管理员参与者。
- 软删除：`deleted_at/deleted_by_user_id/delete_reason`；删除后保留行用于审计，列表过滤。
- `thread_id + organization_id` 复合外键保证线程与回复同组织。

### 4.3 状态与并发

- 新建线程：校验对象范围（课程/教学班）和接收角色；`thread_no` 在组织内唯一，作为创建幂等键。
- 回复：事务内 `SELECT ... FOR UPDATE` 锁定线程；校验非 `CLOSED`；插入回复、更新 `last_reply_at/reply_count`、状态和已读时间。
- 关闭：发起人、目标处理人或组织管理员；记录原因；关闭后拒绝新回复。
- 已读：`POST /read` 只更新当前角色对应时间；不因列表浏览自动已读。
- 限流：建议按用户/组织限制创建和回复频率；超限 429。阈值待产品确认。

## 5. 系统通知设计

### 5.1 模板与版本

`edu_notification_template`：

- `template_code` 在组织内稳定；`template_version` 递增，历史版本保留。
- `active_template_code` 生成列 + 唯一键保证同一组织同一编码最多一个 `ACTIVE` 版本。
- `channel` 预留 `IN_APP/EMAIL/SMS`；M1 仅启用 `IN_APP`。
- `title_template/body_template` 只允许 `${variableName}`；`variable_schema_json` 声明类型、必填、长度；禁止 EL/SpEL/脚本/任意表达式。
- 模板 `DRAFT → ACTIVE → ARCHIVED`；更新模板产生新版本，不原地改已激活版本。

### 5.2 逻辑发送任务

`edu_notification_send_job`：

- `idempotency_key` 在组织内唯一；`request_hash` 检测同键不同载荷。
- `audience_kind=ORGANIZATION/ROLE/TEACHING_CLASS/USER_LIST`；`audience_ref_id`/`audience_json` 表达受众。
- `payload_json` 是已校验的模板变量；不含凭据/敏感原文。
- 状态：`PENDING → PROCESSING → SUCCEEDED/PARTIAL/FAILED/UNKNOWN`；`CANCELLED` 仅从 `PENDING`。
- 计数：`total_count/success_count/failure_count/unknown_count`；`row_version` 乐观锁。
- 创建任务在事务内完成幂等键和初始计数；实际发送在事务外执行，不长时间持锁。

### 5.3 发送记录

`edu_notification_send_record`：

- 唯一键 `(job_id, recipient_user_id, attempt_no)`；重试新增 `attempt_no`，不覆盖历史。
- `delivery_status=PENDING/SENT/DELIVERED/READ/FAILED/UNKNOWN`。
- `sent_at` 平台接受；`delivered_at` 渠道确认；`read_at` 用户显式已读；站内 `SENT/DELIVERED` 可同时间。
- `provider_message_id` 用于对账；`error_code/error_message` 只存受控短文本。
- `next_retry_at` 仅对 `FAILED`；`UNKNOWN` 不自动重试，进入人工对账。
- 发送记录保留审计，不物理删除。

## 6. 教学过程分析与教学问题研判

### 6.1 聚合契约

只消费第 2/4/5 组已授权契约，不直读其业务表：

- 范围：`organization_id` + `scopeKind=ORGANIZATION/COURSE/TEACHING_CLASS` + `scopeId`。
- 时间：`window.startsAt/endsAt` 左闭右开，UTC；默认当前学期，可选最近 30 天（待确认）。
- 口径：`definitionVersion`、`source`、`evidenceCutoffAt`、`asOf`。
- 数值：`value`、`unit`、`denominator`、`sampleCount`；缺失/分母为 0 时为 `NO_DATA`，源不可用为 `UNAVAILABLE`，均不回退为 0。
- 单源失败只影响对应指标；不因一个源失败丢弃整个响应。

### 6.2 教学问题研判

- 只输出“候选问题”：`issueCode`、`severity`、`evidenceRefs`、`window`、`sampleCount`、`status`。
- 不生成学生能力分数、不贴人格标签、不使用 AI 自动定性。
- 教务管理员可确认、通知教师、记录整改、复核和关闭；每次状态变更留操作者/时间/原因。
- 问题分类、阈值、通知对象、整改期限待产品确认。

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
| `POST /education/messages` | 教师/学生 | 创建线程；`Idempotency-Key` |
| `POST /education/messages/{threadId}/replies` | 参与者 | 回复；`Idempotency-Key` |
| `POST /education/messages/{threadId}/read` | 参与者 | 更新本人角色已读时间 |
| `POST /education/messages/{threadId}/close` | 发起人/目标/管理员 | 关闭；必填原因 |
| `GET /education/admin/messages` | 管理员 | 组织内线程分页 |
| `POST /education/admin/messages/{threadId}/replies` | 管理员 | 回复/内部备注 |
| `POST /education/admin/messages/{threadId}/close` | 管理员 | 关闭 |

字段：`threadNo`、`scopeKind`、`scopeRefId`、`targetRole`、`targetUserId`、`subject`、`body`、`visibility`、`closeReason`。分页 `pageNum/pageSize`，默认 1/20，最大 100；返回 `TableDataInfo` 的 `total/rows`。

### 7.3 系统通知

| 方法/路径 | 角色 | 说明 |
| --- | --- | --- |
| `GET/POST /education/admin/notification-templates` | 管理员 | 模板列表/新建 |
| `PUT /education/admin/notification-templates/{id}` | 管理员 | 新版本；`rowVersion` |
| `POST /education/admin/notification-templates/{id}/activate` | 管理员 | 激活；同编码唯一 ACTIVE |
| `POST /education/admin/notification-templates/{id}/archive` | 管理员 | 归档 |
| `POST /education/admin/notification-sends` | 管理员 | 创建逻辑发送；`Idempotency-Key` |
| `GET /education/admin/notification-sends` | 管理员 | 任务分页 |
| `GET /education/admin/notification-sends/{jobId}` | 管理员 | 任务与计数 |
| `POST /education/admin/notification-sends/{jobId}/retry` | 管理员 | 对已知失败重试；新增 attempt |
| `POST /education/admin/notification-sends/{jobId}/cancel` | 管理员 | 仅 PENDING 可取消 |
| `GET /education/notifications` | 教师/学生/管理员 | 本人站内通知分页 |
| `GET /education/notifications/unread-count` | 教师/学生/管理员 | 本人未读数 |
| `POST /education/notifications/{recordId}/read` | 本人 | 显式已读；幂等 |

发送请求：`templateId`、`templateVersion`、`audienceKind`、`audienceRefId`、`audienceUserIds`、`payload`、`scheduledAt`。响应：`jobId`、`status`、`totalCount`、`successCount`、`failureCount`、`unknownCount`。

幂等：`Idempotency-Key` 作用域 `(organization_id, key)`；同键同 `requestHash` 返回同一 job；同键不同载荷 409。重试创建新 `attempt_no`，不覆盖旧记录。

### 7.4 分析

| 方法/路径 | 角色 | 说明 |
| --- | --- | --- |
| `GET /education/admin/teaching-process-analysis` | 管理员 | 组织/课程/教学班过程指标 |
| `GET /education/admin/teaching-issue-analysis` | 管理员 | 候选教学问题与证据 |

参数：`scopeKind`、`scopeId`、`from`、`to`、`definitionVersion`。响应：`asOf`、`window`、`definitionVersion`、`items[]`（`metricCode`、`value`、`unit`、`status`、`source`、`denominator`、`sampleCount`、`updatedAt`）、`issues[]`（`issueCode`、`severity`、`evidenceRefs`、`sampleCount`、`status`）。

状态：`OK/NO_DATA/UNAVAILABLE`；缺失/分母为 0 → `NO_DATA`；源授权/契约不可用 → `UNAVAILABLE`（写操作涉及教学班授权不可用时按 503 失败关闭）。

### 7.5 错误、分页与权限

- 错误：400 参数/状态；401 未登录；403 无权限/跨组织/跨班；404 不存在或不可见；409 状态/幂等冲突/乐观锁；429 限流；500 服务端；503 授权契约不可用（写操作失败关闭）。
- 分页：`pageNum` 默认 1、最小 1；`pageSize` 默认 20、最大 100；排序由服务端固定。
- 权限编码草案（未配置）：`education:news:list/read/add/edit/publish/withdraw/archive`、`education:message:list/read/create/reply/close/manage`、`education:notification:template:list/add/edit/activate/archive`、`education:notification:send:list/create/retry/cancel`、`education:notification:read`、`education:analysis:process:view`、`education:analysis:issue:view`。
- 对象级授权：资讯按组织；留言按参与者/目标角色/组织/教学班授权；通知按组织和管理员/本人；分析按组织/课程/教学班授权。权限编码不等于第 1 组已完成菜单配置。
- 接口 camelCase 字段对应 DDL snake_case；`rowVersion` 对应 `row_version`，`templateVersion` 对应 `template_version`，`audienceUserIds` 序列化到 `audience_json`。

## 8. 事务、并发、审计与保留

### 8.1 事务边界

- 资讯发布/撤回/归档：单事务状态 CAS + 操作者/时间/原因；不直接发通知；如后续需要通知，提交后创建发送任务。
- 留言创建：单事务校验范围、插入线程；`thread_no` 唯一防重。
- 留言回复：单事务锁定线程、插入回复、更新计数/最后回复/状态；不在长事务内做外部调用。
- 通知发送：创建 job 单事务；worker 用状态 CAS 领取；供应商调用在事务外；每次 attempt 单独记录；不长时间持有行锁。
- 分析：只读；不写业务表；是否需要快照待评审。

### 8.2 并发与锁

- 资讯/模板/线程/任务使用 `row_version` 或状态条件更新，检查受影响行数。
- 留言回复先 `SELECT ... FOR UPDATE` 锁线程，避免计数丢失。
- 发送任务用 `UPDATE ... WHERE status='PENDING'` 领取；同一 job 不并发执行。
- 发送记录唯一键 `(job_id, recipient_user_id, attempt_no)` 防重复尝试。
- 唯一键只防重复，不替代业务授权和状态校验。

### 8.3 重试与未知结果

- 失败最多 3 次，建议 60s/300s/1800s；每次新增 attempt 行。
- `UNKNOWN` 不自动重试；按供应商回执对账，人工补偿需权限和审计。
- 重复提交由 `Idempotency-Key + request_hash` 拦截；同键不同载荷 409。
- 不把超时直接判为失败；先查询/对账再决定重试。

### 8.4 审计与保留

- 资讯发布/撤回/归档、留言关闭/隐藏、模板激活、发送/重试/取消/补偿记录操作者、时间、原因。
- 发送记录和 attempt 历史保留用于对账；M1 不物理删除。
- 资讯归档、留言软删除、线程关闭保留行；保留期限待学校政策确认。
- 审计可复用第 1 组审计能力；本次不实现。

### 8.5 索引与唯一键用途

- `edu_campus_news`：`(organization_id,status,published_at,id)` 支撑组织资讯列表；`(organization_id,audience_kind,audience_ref_id,status)` 支撑受众过滤。
- `edu_message_thread`：`(organization_id,status,last_reply_at,id)` 支撑管理员队列；`(initiator_user_id,created_at,id)` 支撑“我的留言”；`(target_user_id,status,last_reply_at,id)` 支撑处理人队列；`(organization_id,scope_kind,scope_ref_id,status)` 支撑课程/教学班过滤。
- `edu_message_reply`：`(thread_id,created_at,id)` 支撑线程详情；`(sender_user_id,created_at,id)` 支撑用户回复历史。
- `edu_notification_template`：`(organization_id,status,channel)` 支撑模板列表；`(organization_id,template_code,template_version)` 保留版本；`(organization_id,active_template_code)` 保证同编码唯一 ACTIVE。
- `edu_notification_send_job`：`(organization_id,status,scheduled_at,id)` 支撑待发送扫描；`(template_id,template_version)` 支撑模板审计；`(organization_id,idempotency_key)` 防重复任务。
- `edu_notification_send_record`：`(recipient_user_id,delivery_status,created_at,id)` 支撑个人通知/未读；`(job_id,delivery_status,attempt_no)` 支撑任务重试；`(channel,provider_message_id)` 支撑供应商对账；`(next_retry_at)` 支撑失败重试扫描。

## 9. 与 6-02 的边界

- 6-05 使用独立六表；不修改 `edu_user_notice`，不增加 `NoticeKind`，不 import 6-02 代码。
- 不复用 `UpstreamNoticeEventPort`；该端口只接收 PREVIEW/ACTIVITY/ASSIGNMENT/GRADE，拒绝 ANNOUNCEMENT，且无 HTTP 端点。
- `edu_user_notice`（6-02）是课程/教学事件通知与铃铛未读；`edu_notification_send_record`（6-05）是系统通知模板发送的送达/已读记录。两者不得互相冒充。
- 不允许同一系统通知同时写 6-02 铃铛和 6-05 发送记录造成重复计数；将来确需集成时，先定义唯一来源、映射、幂等和读模型，本次不实现适配器。
- 6-02 的 PR #18/#20/#21/#22 未合并；6-05 分支从 main 创建，不基于 6-02 分支。

## 10. 外部依赖与阻塞

- 第 2 组 Draft PR #26（head `3e74e875edf759a0887f49f56e5b82103339878b`）已有 `EduCourseAccessService` 草案，但 `activeMembers` 只返回当前有效成员，`EduMemberRef.joinedAt` 只有日期、没有 `leftAt` 或历史时点查询；不足以满足 6-02 事件时点入退班判断。**该草案未冻结、未接入、未完成消费者确认。**
- 第 1 组：用户、角色、组织/部门、菜单权限、审计未配置；6-05 权限编码只是草案。
- 第 2 组：教学班/课程范围与成员授权；用于留言对象范围和教学班定向资讯。授权缺失或不可用时，写操作按 503 失败关闭。
- 第 4/5 组：课堂、作业、成绩、学习统计；用于教学过程分析。6-05 只消费契约，不重算正式成绩，不伪造能力分数。
- 6-02：通知/铃铛边界未联调；不依赖未合并实现。
- `docs/缺口模块清单.md` 建议留言可作 M0 补充或 M1；当前以 `docs/分工.md` 的 M1 为准，不扩大 6A 职责。
- 六表 DDL 未执行；未验证 MySQL 8.0.46；不得声称已通过真实数据库验证。

## 11. 后续验证清单

- [ ] 产品确认第 1 节所有“建议默认值”。
- [ ] 第 2/4/5 组确认分析指标、来源、分母、样本、时间窗和口径版本。
- [ ] 第 1 组确认权限编码、组织范围、审计和数据保留。
- [ ] 第 2 组确认教学班/课程范围与成员契约，明确历史时点能力。
- [ ] 六表 DDL 在独立、可丢弃的 MySQL 8.0.43/8.0.46 测试库评审执行；记录版本和约束测试。
- [ ] 验证唯一键、CHECK、外键、索引、并发领取、幂等、重试和未知结果。
- [ ] 接口契约由 6B 与第 1/2/4/5 组共同评审；路径/字段/错误码冻结后再实现。
- [ ] 真实 HTTP、登录、角色拦截、审计落库联调另行安排。
