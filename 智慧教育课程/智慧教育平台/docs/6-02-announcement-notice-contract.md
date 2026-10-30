# 6-02 教学班公告与个人通知 M0 后端契约草案

> 版本：0.2 草案｜日期：2026-10-25｜负责人：6A｜状态：教师公告、学生公告只读、学生通知列表/详情/未读数/单条已读/全部已读以及公告发布 ANNOUNCEMENT fan-out 的后端代码与隔离测试已落地；上游事件接收、生产学生授权适配器和真实 HTTP/登录/权限/审计联调未完成
>
> 本文只覆盖 `docs/分工.md` 的 6-02：教学班公告发布/读取、学生本班公告、学生本人任务与成绩通知、列表/详情/未读数/单条标记已读/全部标记已读。
> 本文中的 URL、字段名、错误码、权限标识仍为**待评审建议**，不是已冻结协议；公告与学生通知 Service/Controller 的已实现行为以第 3、4、7 节和实现代码为准。

## 0. 事实基线

### 0.1 已经实现

- 若依 3.9.2 三角色登录、验证码、`/getInfo`、`/getRouters`、退出已在本机联调。
- 服务端可从 `SecurityUtils.getLoginUser()` / `SecurityUtils.getUserId()` 取得可信用户身份。
- 已有统一响应结构 `AjaxResult`、分页结构 `TableDataInfo`、全局异常处理、`@PreAuthorize` 和 `@Log` 审计能力。
- `sql/mysql-v3-m0-*.sql` 已在隔离 MySQL 8.0.46 测试库验证建表与部分约束。
- 演示数据中已出现 1 条 `PUBLISHED` 公告和 2 条个人通知（`ANNOUNCEMENT`、`GRADE`）。
- 教师公告已有 Entity、Mapper 读写、状态规则、Service、授权适配端口、Controller/DTO 和 H2 隔离测试；学生公告已有只读列表/详情 Controller/DTO/Service 和学生授权端口；学生通知已有列表/详情/未读数/单条已读/全部已读 Controller/DTO/Service/Mapper 和 H2/Mockito/MockMvc 隔离测试；公告发布时已实现 ANNOUNCEMENT fan-out（批量 500、唯一键幂等）；生产教师/学生授权适配器仍未接入，上游事件接收未实现。

### 0.2 仅有表设计或尚未接入

- `edu_course_announcement` 已有教师公告读写和学生公告只读 Controller/DTO/Service/Mapper。
- `edu_user_notice` 已有 Entity、Mapper、Service、Controller/DTO、学生通知读取/已读实现和公告发布 ANNOUNCEMENT fan-out；仍没有上游 PREVIEW/ACTIVITY/ASSIGNMENT/GRADE 事件接收。
- `edu_class_teacher`、`edu_class_student`、`edu_course_class_binding`、`edu_teaching_class` 已有表结构和演示数据，但没有教学业务接口；公告/通知模块通过教师教学班授权端口和学生公告授权端口隔离，当前没有生产适配器。
- `ruoyi-education` 已接入 Maven 构建；当前没有真实 MySQL、HTTP 或完整 Spring Boot 应用联调结论。

### 0.3 仍属待评审方案

- 公告发布时自动生成 `ANNOUNCEMENT` 通知（已实现，见 2.9）。
- 通知 `source_id` 在 `GRADE` 场景到底指向 `edu_grade_publication.id` 还是别的对象。
- 学生退班后历史通知是否仍可读。
- 公告草稿不归档（已确定）；当前 CHECK 约束不允许直接把 DRAFT 改为 ARCHIVED。
- 公告和通知两张表的领域归属：`docs/database-design.md` 把它们列在 `course` 领域，但 6-02 由第 6 组实现。
- 上游通知事件采用何种可靠投递方式。
- 管理员是否需要对公告/通知做只读治理视图；M0 未批准。

---

## 1. 任务边界理解

### 1.1 本次要设计的范围

1. 教师在**某个已生效的课程—教学班绑定**下创建、编辑草稿、发布、归档、列表和查看公告。
2. 学生在**本人当前有效教学班**对应的绑定下读取已发布且处于可见窗口内的公告。
3. 学生读取**发给本人**的任务、成绩发布等站内通知；通知只能由接收人读取。
4. 前端所需的最小接口：公告列表、公告详情、通知列表、通知详情、未读数、单条标记已读、全部标记已读。
5. 公告发布时按现有表结构生成 `ANNOUNCEMENT` 通知，复用 `edu_user_notice` 唯一约束去重。

### 1.2 明确不做

- 不做 6-01 教师/学生/管理员三端总览聚合。
- 不做 6-05 校园资讯、留言互动、系统通知模板、通知中心管理页。
- 不做 AI 公告生成、AI 摘要、AI 推荐。
- 不实现课程、教学班、作业、成绩、课堂等上游业务。
- 不直接读写其他领域的业务表；只通过第 1、2、3、4、5 组提供的服务契约取数。
- 不新增依赖、表、字段；现有模型不足处只列变更建议。
- 不把示例路径、字段、错误码描述成已冻结协议。
- 本阶段实现公告发布 ANNOUNCEMENT fan-out，不接收上游事件，也不修改 SQL；学生通知读取与已读 Controller/DTO/Service/Mapper 及隔离测试已落地，上游事件接收未实现。

### 1.3 公告与通知的职责区别

| 对象 | 作用 | 是否业务事实源 | 读取方 |
| --- | --- | --- | --- |
| `edu_course_announcement` | 教师面向一个教学班发布的公告正文 | 是，公告本身是事实源 | 教师、本班学生 |
| `edu_user_notice` | 发给某个用户的站内提醒/待办入口 | 否，只是指向来源业务的提醒 | 仅接收人 |

因此：

- 公告列表不能依赖通知表；学生即使没有收到通知行，也应按公告可见性看到公告。
- 通知列表不能反向成为作业、成绩的授权依据；点击通知后仍由来源业务接口校验访问权限。
- `edu_user_notice` 不是系统通知中心，也不替代若依 `sys_notice`。

---

## 2. 歧义、冲突和待确认问题

以下问题未确认前，不应把对应行为写成已实现能力。

### 2.1 领域归属冲突（必须先确认）

`docs/database-design.md` 将 `edu_course_announcement`、`edu_user_notice` 归在 `course` 领域；`docs/分工.md` 又将 6-02 公告/通知交给第 6 组。由此产生两种实现方式：

- **方案 A（建议）**：第 6 组在 `ruoyi-education` 的课程领域包内实现“课程沟通”子能力，拥有这两张表的写入权；第 2 组只提供教学班、绑定、成员和任课授权契约，不实现公告/通知。
- **方案 B**：第 2 组拥有表与写入服务，第 6 组只做聚合和页面；这会削弱 6-02 的后端交付边界，也与“第 6 组负责公告/通知”的分工不一致。

需要项目负责人确认方案 A，并在 `docs/architecture.md` 或 `docs/database-design.md` 中补一行归属说明。

### 2.2 公告状态与“尚未开放/已经关闭”的表达

`edu_course_announcement.status` 只有 `DRAFT`、`PUBLISHED`、`ARCHIVED`。没有“未开始/进行中/已关闭”状态，也没有 `published_at`、`updated_at`、`archived_at`。

规则（已确认）：

- 数据库状态仍只用三值。
- 服务端在响应中计算 `visibilityState`：`DRAFT`、`NOT_YET_OPEN`、`OPEN`、`CLOSED`、`ARCHIVED`。
- “尚未开放/已经关闭”由 `opens_at`、`closes_at` 与服务端当前时间推导，不新增状态值。
- `status` 不因时间自动变化；`closes_at` 到期后仍是 `PUBLISHED`，只是学生不可见。
- 允许 `PUBLISHED` 且 `opens_at` 在未来，表示“已发布、定时开放”；此时 `visibilityState = NOT_YET_OPEN`。
- 教师端公告响应必须返回非空 `visibilityState`；学生端只返回当前可见公告，可不展示该字段。
- 前端只做枚举到标签映射，不能用本机时间自行判断；若收到 `null`，按未知状态并记录错误，不得推测。
- `visibilityState` 不代替权限校验，详情和写操作仍由服务端重新鉴权。

### 2.3 草稿归档与 CHECK 约束冲突

现有约束：

```sql
(status = 'DRAFT' AND published_by_user_id IS NULL)
OR
(status <> 'DRAFT' AND published_by_user_id IS NOT NULL AND opens_at IS NOT NULL)
```

因此不能把 `DRAFT` 直接改成 `ARCHIVED`，否则必须伪造 `published_by_user_id` 和 `opens_at`。建议二选一：

- **建议 M0**：只允许 `PUBLISHED → ARCHIVED`；草稿不归档、不提供删除接口，草稿保留在教师列表。
- **备选**：为草稿增加物理删除或软删除字段；需要单独评审，不纳入 M0。

### 2.4 创建时间与开放时间

当前只有 `created_at` 和 `opens_at`，没有独立发布时间：

- `created_at` 是公告创建时间；教师端字段名为 `createdAt`，页面显示“创建时间”。
- `opens_at` 是允许学生查看的开放时间；学生端字段名为 `opensAt`，页面显示“开放时间”。
- 当前不返回 `publishedAt`，不新增 `published_at` 字段；不得用 `createdAt` 或 `opensAt` 冒充实际发布时间。
- 页面不得出现“发布时间”标签；如未来确需记录发布动作时间，另立字段与迁移评审，不在本批范围。
- 教师公告列表按 `created_at DESC, id DESC` 排序；学生公告列表按 `opens_at DESC, id DESC` 排序。第二个 `id` 字段用于时间相同时保持稳定顺序。

### 2.5 公告编辑并发

公告表没有 `updated_at` 或版本号，两个教师同时编辑草稿会最后写入者覆盖前者。建议：

- M0 接受最后写入者覆盖，并在契约中明确“不做乐观锁”；
- 若要求冲突提示，增加 `updated_at` 或 `version` 字段，再做条件更新。此项列为变更建议，不在本轮实现。

### 2.6 通知 `source_id` 语义未定义

`edu_user_notice.notice_kind` 有五个值，但 `source_id` 没有外键，也没有文档规定每种 kind 指向什么。建议先冻结以下映射：

| `notice_kind` | `source_id` 建议指向 | 生成方 | 说明 |
| --- | --- | --- | --- |
| `ANNOUNCEMENT` | `edu_course_announcement.id` | 第 6 组 | 公告发布时生成 |
| `PREVIEW` | `edu_assignment.id`（`assignment_kind = PREVIEW`） | 第 3 组 | 预习任务发布/开放 |
| `ACTIVITY` | `edu_activity_instance.id` | 第 4 组 | 签到或随堂题开放 |
| `ASSIGNMENT` | `edu_assignment.id`（`CLASSWORK` 或 `HOMEWORK`） | 第 5 组 | 作业发布 |
| `GRADE` | `edu_grade_publication.id` | 第 5 组 | 成绩正式发布；不要用 submission_id 或 assignment_id |

`GRADE` 必须用发布记录 ID，原因是同一作业可能有多次发布/更正；如果改用 `assignment_id`，同一学生、同一作业只能生成一条成绩通知，后续更正无法再通知。此点需第 5 组确认。

### 2.7 学生退班后的历史访问

现有表没有“通知生成时的成员关系快照”。可选策略：

- **策略 A（建议 M0）**：学生当前无有效入班记录时，公告、通知、未读数、详情全部拒绝；历史行保留，供教师/审计使用。退班后不再访问课程沟通内容，符合“退班即失去当前授权”的安全边界。
- **策略 B**：按通知 `visible_at` 是否落在某段历史入班区间内决定是否可见；当前无有效入班记录时仍可读历史通知。这会扩大退班后的数据访问面，需要学校数据保留政策确认。

本文按策略 A 设计；如果学校要求保留历史访问，需另行评审，并明确公告正文、成绩通知等敏感内容的保留期限。

### 2.8 学生重新入班

若采用策略 A，学生退班后重新入班，旧通知会因当前成员关系恢复而重新可见。若不允许，需要“通知接收时的成员关系快照”或“通知绑定入班记录 ID”，现有表不足。列为待确认。

### 2.9 公告发布是否自动生成通知

公告发布事务内为发布时点的当前有效学生生成 `ANNOUNCEMENT` 通知；实现按绑定查询当前在班学生，只保留发布时点落在 `[joinedAt, leftAt)` 的成员，并按 500 条分批写入。规则如下：

- 只给公告发布时的“当前有效学生”生成通知；
- 公告列表仍是事实源，通知只是提醒；
- 唯一约束 `(recipient_user_id, notice_kind, source_id)` 保证重试/重复发布不重复；
- 重复发布保持幂等 200，不重复 fan-out；
- 公告发布后新入班的学生不补发历史通知，但能在公告列表看到当前可见公告；若要求补发，需要额外对账任务。

### 2.10 管理员是否可读

`docs/分工.md` 没有给 6-02 分配管理员页面；6-05 才涉及校园资讯和留言。建议 M0：

- 教务管理员、超级管理员都**不默认**通过业务接口读取公告/通知正文；
- 若需要治理或审计，使用第 1 组审计日志或单独立项只读接口；
- 不能因为 `userId = 1` 或 `SecurityUtils.isAdmin()` 就绕过教学班对象权限。

### 2.11 通知撤回、过期、来源关闭

`edu_user_notice` 没有 `status`、`revoked_at`、`expires_at`。因此：

- 成绩撤回、作业关闭、公告归档后，通知行本身不会自动变化；
- 公告通知可以在第 6 组内联合公告表过滤；
- 其他来源的通知无法在第 6 组内判断来源是否仍可用，只能由来源页面再次鉴权。

建议 M0：

- 通知行不可删除、不可修改标题；已读状态只前进不后退；
- 公告归档后，公告通知在列表/详情/未读数中按“来源不可见”过滤；
- 作业/成绩通知保留为历史提醒，点击后由来源业务返回当前状态；未读数可能包含来源已关闭的提醒，这是已知限制。
- 若产品要求“撤回成绩时同步撤销通知”，需要第 5 组提供撤回事件，并增加通知状态/撤回时间字段，列入 M1。

### 2.12 通知失败与上游事务

要求“上游通知失败不能反向回滚已经成功的作业或成绩发布”。建议：

- 上游业务先提交自己的事务；
- 提交成功后通过事务后事件或消息通知第 6 组；
- 第 6 组写入失败只记录日志并重试，不向上游抛回导致回滚；
- 上游接口不因通知失败而返回失败。

但现有表没有 outbox。若进程在“上游提交”和“通知写入”之间崩溃，通知会丢失。M0 可接受“通知尽力送达 + 对账补偿”，并在实施时明确；若要求强一致，需要新增 outbox 表或使用消息中间件，单独立项。

### 2.13 其他待确认

1. 教师只有 `can_publish = 0` 时，草稿权限按严格规则执行：创建者可读取和编辑自己的 `DRAFT`；具有本班公告管理权限（本批以 `canPublish` 为管理权限）的教师可读取和编辑；其他教师不可读取或编辑。已发布/已归档公告可由当前任课教师读取。
2. 教学班 `ARCHIVED` 或绑定 `ended_at` 非空时，教师是否还能读取历史公告？本文建议业务接口拒绝，历史由审计/管理侧保留。
3. 学期已结束但教学班仍为 `ACTIVE` 时，公告是否还可见？本文暂不单独校验 `edu_term` 日期，以第 2 组返回的教学班/绑定有效性为准。
4. 公告正文长度上限。已确定为非空，最多 20,000 个 Unicode 码点，且 UTF-8 编码后不超过 65,535 字节；两个上限必须同时满足。部分四字节字符可能先触及 65,535 字节上限。
5. “全部标记已读”已确定纳入 M0，与单条标记已读同时支持；接口范围和并发语义见 3.6.1、6.13.1。后端代码与隔离测试已实现，尚未真实联调。
6. 通知列表按 `binding_id` 过滤已支持；不传时返回本人所有当前可见通知。

---

## 3. 正常业务流程与状态前置条件

### 3.1 公告状态机

```text
DRAFT --publish--> PUBLISHED --archive--> ARCHIVED
  |                    |                    |
  |                    +-- publish 再次调用：幂等成功，返回当前 PUBLISHED
  |                    +-- archive：进入 ARCHIVED
  |                    +-- opens_at 未到：NOT_YET_OPEN（计算态）
  |                    +-- opens_at <= now < closes_at：OPEN（计算态）
  |                    +-- closes_at <= now：CLOSED（计算态）
  |
  +-- archive：状态冲突；不提供草稿归档
ARCHIVED --publish--> 状态冲突
ARCHIVED --archive--> 幂等成功，返回当前 ARCHIVED
```

约束：

- `DRAFT`：创建者或具有本班公告管理权限（本批为 `canPublish`）的教师可读取/编辑；`published_by_user_id` 必须为空。
- `PUBLISHED`：`published_by_user_id` 非空，`opens_at` 非空；学生是否可见由时间窗口决定。
- `ARCHIVED`：仅从 `PUBLISHED` 进入；学生立即不可见；当前任课教师仍可读取。
- 状态不自动随时间变化；`closes_at` 到期不写库。
- 发布和归档均要求操作者当前具有该教学班的 `can_publish` 权限。
- 重复发布/归档在身份、绑定和权限校验通过后按幂等成功返回；已归档后再次发布仍为状态冲突。
- 条件更新影响 0 行时必须重新读取并按“已处于目标状态、公告不存在/跨绑定、非法状态、真实并发冲突”分类，不能一律成功或一律冲突。
- 0 行后的重读必须使用当前读（例如 `SELECT ... FOR UPDATE`），不能依赖 MySQL `REPEATABLE READ` 下的旧快照。

### 3.2 教师发布公告

前置条件：

1. 请求已认证，操作者是教师角色。
2. `bindingId` 存在，绑定未结束（`ended_at IS NULL`）。
3. 绑定对应的教学班状态为 `ACTIVE`。
4. 操作者在该教学班有当前有效的任课记录：
   - `valid_from <= 服务端当前时间`；
   - `valid_to IS NULL OR valid_to > 服务端当前时间`；
   - `can_publish = 1`。
5. 标题 1–200 字符；正文非空且在 API 上限内。
6. `closes_at` 为空，或严格晚于 `opens_at`。
7. 创建时若 `opens_at` 为空，先保存为 `NULL`；发布时再补服务端当前时间。

发布流程（同一数据库事务）：

1. 校验身份、绑定、当前任课关系和 `can_publish`。
2. 若公告已是 `PUBLISHED` 且发布字段完整，直接返回当前公告（幂等成功）。
3. 若公告是 `DRAFT` 且 `published_by_user_id IS NULL`，条件更新：
   `status = 'PUBLISHED'`、`published_by_user_id = 当前用户`、`opens_at = COALESCE(opens_at, 服务端当前时间)`。
4. 若条件更新影响 0 行，重新读取：已处于 `PUBLISHED` 则幂等返回；不存在/跨绑定按未找到；`ARCHIVED` 按状态冲突；仍为 `DRAFT` 按真实并发冲突。
5. 仅当第 3 步实际把草稿更新为 `PUBLISHED` 时，在同一事务内查询绑定当前有效学生，按 `[joinedAt, leftAt)` 过滤发布时点，再按每批最多 500 条写入 `ANNOUNCEMENT` 通知；重复键按幂等忽略。
6. 提交事务，记录审计日志。

说明：fan-out 与公告状态变更同事务；学生授权适配器缺失/异常/结果不完整返回 503 并回滚发布，通知写入失败同样回滚。重复发布走幂等返回，不重复 fan-out。第 2 组生产成员查询适配器仍未接入，当前按 503 失败关闭。

归档流程（同一数据库事务）：

1. 校验身份、绑定、当前任课关系和 `can_publish`。
2. 若公告已是 `ARCHIVED` 且发布字段完整，直接返回当前公告（幂等成功）。
3. 若公告是 `PUBLISHED` 且发布字段完整，条件更新为 `ARCHIVED`。
4. 若影响 0 行，重新读取并按 `ARCHIVED`（幂等）、未找到/跨绑定、非 `PUBLISHED`（状态冲突）、仍 `PUBLISHED`（真实并发）分类。
5. `DRAFT` 直接归档始终为状态冲突。

### 3.3 教师读取公告

- 列表必须带 `bindingId`，只返回该绑定下的公告。
- 当前有效任课教师可读取 `PUBLISHED`、`ARCHIVED`。
- `DRAFT` 采用严格规则：创建者可读取自己的草稿；具有本班公告管理权限（本批为 `canPublish`）的教师可读取本班草稿；其他教师不可读取。
- 默认按 `created_at DESC, id DESC` 排序；`id` 用于时间相同时保持稳定顺序。
- 详情按 ID 读取后，仍要校验该公告的绑定与当前教师的任课关系，不能只凭公告 ID。

### 3.4 学生读取公告

前置条件：

1. 已认证，学生角色。
2. 公告状态 `PUBLISHED`。
3. `opens_at <= 服务端当前时间`。
4. `closes_at IS NULL OR 服务端当前时间 < closes_at`。
5. 公告绑定未结束（`ended_at IS NULL`），教学班 `ACTIVE`。
6. 学生在绑定对应教学班有当前有效入班记录：
   - `joined_at <= 服务端当前时间`；
   - `left_at IS NULL OR left_at > 服务端当前时间`。

不满足任一条件时：

- 列表不返回该公告；
- 详情统一返回 `404`，前端提示“公告不存在或当前不可查看”（不返回正文、不泄露公告是否真实存在）；
- 已归档、未发布、未开放、已关闭、外班、退班均按不可见处理；内部日志可记录真实原因，但不直接返回给用户。

### 3.5 学生读取通知

前置条件：

1. 已认证，学生角色。
2. `recipient_user_id = 服务端当前用户 ID`；请求中不接收 `recipientUserId`。
3. `visible_at <= 服务端当前时间`。
4. 若 `binding_id` 非空：
   - 绑定未结束，教学班 `ACTIVE`；
   - 学生当前有效入班；
   - 通知的 `visible_at` 落在该学生某段有效入班区间内（用于避免把入班前或退班后的通知错误展示）。
5. 若 `notice_kind = 'ANNOUNCEMENT'`：
   - 对应公告当前仍为 `PUBLISHED`；
   - 公告仍在 `[opens_at, closes_at)` 窗口内。
6. 其他 kind 的来源可用性由来源业务接口判断；第 6 组只保证通知接收人正确。

### 3.6 单条标记已读

前置条件：

1. 通知存在。
2. `recipient_user_id = 服务端当前用户 ID`。
3. `visible_at <= 服务端当前时间`。
4. 绑定/成员关系满足 3.5。

写入规则：

- 仅当 `read_at IS NULL` 时写入服务端当前 UTC 时间。
- 已读再次请求不改变 `read_at`，返回同一个 `readAt`。
- 不允许“标记未读”，不覆盖首次已读时间。
- 并发请求使用条件更新，只有一个请求真正写入；其余请求读取已读结果。
- 非接收人、不可见或不存在统一按“未找到”处理，避免通过 ID 探测他人通知。

### 3.6.1 全部标为已读

M0 同时支持单条标记已读和“全部标记已读”。全部操作的范围必须与通知列表、未读数的数据范围一致：

- 只处理服务端当前登录学生本人的通知；不接受请求中的 `recipientUserId`。
- 只处理当前已经可见的通知：`visible_at <= 操作确定的服务端时点`。
- 只处理当前 `read_at IS NULL` 的未读通知。
- 绑定、成员关系、公告来源可见性沿用 3.5 和 3.7；`ANNOUNCEMENT` 通知仍要求对应公告当前可见。
- 不受当前页码、页大小、列表筛选或当前页面选中记录限制。
- 不影响其他用户的通知，不提前标记未来才可见的通知。
- 同一操作内新标记的记录使用同一个服务端 `operatedAt` 写入 `read_at`；已经已读的记录不重复更新时间。
- 操作后再产生、或在操作时点之后才到可见时间的通知，仍保持未读。
- 单条已读与全部已读并发时，只允许 `read_at` 从 `NULL` 变为时间，不覆盖已有时间。
- 返回的 `markedCount` 必须是实际由未读变为已读的记录数；`unreadCount` 是操作完成后的剩余未读数。
- 全部标记在单个服务端事务内完成；失败时不得留下部分更新。
- 幂等：没有未读通知或重复操作时仍成功，`markedCount` 可以为 0；不得因 0 而返回业务失败。

### 3.7 未读数

- 统计当前用户、当前可见、`read_at IS NULL` 的通知数。
- 可选 `bindingId` 过滤；传了就必须先校验该绑定下的当前成员关系。
- 公告通知额外要求对应公告当前可见。
- M0 直接查数据库，不使用缓存；不做“读时清零”或客户端本地估算。
- 返回 `asOf` 服务端时间，前端不得自行用本地时间推算。

### 3.8 时间边界

- 数据库所有 `DATETIME(3)` 按 UTC 写入、比较。
- `opens_at` 为**包含**边界：`now >= opens_at` 才可见。
- `closes_at` 为**不包含**边界：`now >= closes_at` 即关闭。
- 服务端时间是唯一权威；客户端倒计时只用于展示。
- 时间窗口校验使用同一服务端时间快照，避免一次请求内多次取时间造成边界抖动。

---

## 4. 权限矩阵

说明：

- “当前教师”= 教学班存在有效任课记录，且 `valid_from <= now`、`valid_to` 为空或晚于 now。
- “可发布”= 当前教师且 `can_publish = 1`。
- “当前学生”= 教学班存在有效入班记录，且 `joined_at <= now`、`left_at` 为空或晚于 now。
- 管理员没有默认业务权限；`userId = 1` 或超管角色不构成绕过对象权限的理由。
- 同一账号兼多角色时，按被调用端点的角色要求和对象权限同时校验；角色通过不等于对象权限通过。

| 操作 | 当前教师（可发布） | 当前教师（不可发布） | 当前学生 | 退班/外班学生 | 教务管理员 | 超级管理员 | 未登录 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 创建公告草稿 | 允许 | 拒绝 | 拒绝 | 拒绝 | 拒绝（M0 无接口） | 拒绝（M0 无接口） | 401 |
| 编辑公告草稿 | 允许 | 仅允许本人创建的 DRAFT | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 401 |
| 发布公告 | 允许（DRAFT；已 PUBLISHED 幂等成功） | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 401 |
| 归档公告 | 允许（PUBLISHED；已 ARCHIVED 幂等成功） | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 401 |
| 教师公告列表/详情 | 允许（本班全部状态） | 允许（本人草稿 + 本班已发布/已归档） | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 401 |
| 学生公告列表 | 拒绝（教师端不提供） | 拒绝 | 允许（本班可见公告） | 拒绝 | 拒绝 | 拒绝 | 401 |
| 学生公告详情 | 拒绝 | 拒绝 | 允许（本班可见公告） | 拒绝 | 拒绝 | 拒绝 | 401 |
| 通知列表/详情 | 不适用 | 不适用 | 允许（仅本人） | 拒绝 | 拒绝 | 拒绝 | 401 |
| 未读数 | 不适用 | 不适用 | 允许（仅本人） | 拒绝 | 拒绝 | 拒绝 | 401 |
| 单条标记已读 | 不适用 | 不适用 | 允许（仅本人可见通知） | 拒绝 | 拒绝 | 拒绝 | 401 |
| 全部标记已读 | 不适用 | 不适用 | 允许（仅本人当前可见未读通知） | 拒绝 | 拒绝 | 拒绝 | 401 |

补充：

- 草稿采用严格规则：创建者可读取/编辑自己的 `DRAFT`；具有本班公告管理权限（本批为 `canPublish`）的教师可读取/编辑本班草稿；其他教师不可读取或编辑。发布和归档仍必须有 `canPublish`。
- 重复发布/归档在身份、绑定和权限校验通过后幂等成功；无权限教师不能借重复请求探测或修改公告。
- 管理员若后续需要只读治理视图，必须新增独立权限标识和明确的数据范围，不能复用教师接口。
- 学生调用教师端接口返回 403；教师调用学生端接口返回 403；这属于角色不匹配，不是对象不存在。

---

## 5. 与第 1、2、3、4、5 组的依赖契约

### 5.1 第 1 组：身份、角色、权限与审计

第 6 组依赖：

1. 可信身份：`SecurityUtils.getUserId()` 返回当前登录用户；不得从请求参数、Header 或前端 token 载荷读取用户 ID。
2. 角色判定：服务端能判断当前账号是否具有 `teacher` / `student` 角色；不要只依赖前端菜单。
3. 统一拒绝：无权限、未登录、令牌失效沿用若依现有响应；403 不返回业务正文。
4. 审计：公告创建、发布、归档，通知批量生成失败，标记已读异常等操作可使用现有 `@Log` 或审计能力记录操作者、时间、对象 ID、结果。
5. 权限标识：后续建议申请 `education:announcement:list`、`education:announcement:add`、`education:announcement:edit`、`education:announcement:publish`、`education:announcement:archive`、`education:notice:list`、`education:notice:read`。这些标识尚未在菜单/角色中配置，需第 1 组评审后落库。

第 6 组不依赖第 1 组提供教学班权限；教学班对象权限由第 2 组契约负责。

### 5.2 第 2 组：教学班、绑定、任课授权、学生成员

第 6 组**不得直接查询** `edu_class_teacher`、`edu_class_student`、`edu_course_class_binding`、`edu_teaching_class` 等表，必须通过第 2 组服务契约。建议契约如下（方法名仅为讨论用）：

```text
BindingContext getBindingContext(bindingId)
  -> bindingId, teachingClassId, courseSpaceId, organizationId,
     bindingEndedAt, teachingClassStatus, courseSpaceStatus, termId

TeacherClassAuth checkTeacher(teachingClassId, userId, at)
  -> active, teacherRole, canPublish, validFrom, validTo

StudentClassAuth checkStudent(teachingClassId, userId, at)
  -> active, joinedAt, leftAt

List<Long> listActiveStudentIds(teachingClassId, at)
  -> 当前有效学生 user_id 列表

Map<Long, StudentClassAuth> batchCheckStudents(teachingClassId, userIds, at)
  -> 批量成员校验，供通知 fan-out 与读取过滤使用
```

要求：

- 所有方法以服务端 `now` 为准，不能接受客户端时间。
- `checkTeacher` 必须同时检查 `valid_from <= now` 和 `valid_to`，不能只看 `valid_to IS NULL`。
- `checkStudent` 必须同时检查 `joined_at <= now` 和 `left_at`。
- `listActiveStudentIds` 返回去重、当前有效的学生 ID；第 6 组仍会按唯一约束去重。
- 第 2 组应明确 `courseSpaceStatus` 是否影响公告；本文建议绑定有效 + 教学班 `ACTIVE` 即可，课程空间归档的读取策略由第 2 组定义。
- 第 2 组不实现公告/通知，避免同一能力两套写入。

### 5.3 第 3 组：预习任务通知

第 3 组在预习任务发布/开放成功后，向第 6 组提供事件：

```text
noticeKind   = PREVIEW
bindingId    = 教学班绑定 ID
sourceId     = edu_assignment.id（assignment_kind = PREVIEW）
title         = 预习任务标题快照
visibleAt     = 任务开放时间（UTC）
recipientUserIds = 本次发布目标中的当前有效学生
occurredAt    = 上游业务提交完成时间
eventId       = 上游生成的唯一事件标识（用于日志/排查，不要求落库）
```

约束：

- 只有任务真正发布/开放后才发事件；草稿不发。
- 事件在上游事务提交后发送；通知失败不回滚预习任务。
- 第 6 组不读取 `edu_assignment` 判断任务状态。
- 同一 `sourceId` 重复事件只生成一条通知。

### 5.4 第 4 组：课堂活动通知

第 4 组在签到/随堂题进入 `OPEN` 后发送：

```text
noticeKind   = ACTIVITY
bindingId    = 教学班绑定 ID
sourceId     = edu_activity_instance.id
title         = 活动标题快照
visibleAt     = 活动开放时间（UTC）
recipientUserIds = 活动目标快照中的当前有效学生
occurredAt    = 活动进入 OPEN 的服务端时间
```

约束：

- 只通知开放，不通知关闭；关闭/公布结果不作为 M0 通知。
- 目标快照由第 4 组决定，第 6 组不自行按全班重算。
- 未公布答案、教师私有内容不得出现在通知标题中。

### 5.5 第 5 组：作业与成绩通知

第 5 组发送两类事件：

```text
noticeKind   = ASSIGNMENT
sourceId     = edu_assignment.id
visibleAt     = 作业发布/开放时间
recipientUserIds = 作业目标中的当前有效学生

noticeKind   = GRADE
sourceId     = edu_grade_publication.id
visibleAt     = 正式成绩发布的服务端时间
recipientUserIds = 该次发布对应的学生
```

约束：

- `GRADE` 事件只在“教师确认 + 正式发布”完成后发送；第 6 组不判断成绩是否发布。
- 成绩撤回、更正是否再发通知，需要第 5 组明确；若更正产生新的 `edu_grade_publication.id`，建议生成新的 `GRADE` 通知；撤回不属于 M0 通知能力。
- 第 6 组不读取 `edu_assignment`、`edu_grade_publication`、`edu_submission`。
- 同一接收人、同一 kind、同一 source 只允许一条通知；重复事件按幂等成功处理。

### 5.6 统一事件载荷与失败语义

建议内部事件字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `eventId` | string | 是 | 上游生成的唯一事件标识，仅用于日志/排查 |
| `noticeKind` | enum | 是 | `PREVIEW`、`ACTIVITY`、`ASSIGNMENT`、`GRADE` |
| `bindingId` | string | 是 | 教学班绑定 ID |
| `sourceId` | string | 是 | 按第 2.6 节映射 |
| `title` | string | 是 | 上游生成的标题快照，1–200 字符 |
| `visibleAt` | RFC3339 UTC | 是 | 学生可见时间 |
| `recipientUserIds` | string[] | 是 | 接收人；由上游按目标快照给出 |
| `occurredAt` | RFC3339 UTC | 是 | 上游业务提交完成时间 |

失败语义：

- 事件消费失败：记录日志、保留可重试信息；不向上游抛出会导致上游事务回滚的异常。
- 事件重复：按唯一键幂等，不更新已存在的通知标题、可见时间、已读时间。
- 事件中的接收人不是该绑定当前有效学生：跳过该接收人并记录；不能把外班学生写入通知。
- 事件缺少必填字段或 `sourceId` 不属于该 kind：拒绝整条事件并告警，不能猜测来源。
- 事件乱序：以 `visibleAt` 为准；若同一 source 已存在通知，不因后到事件改写。
- 进程崩溃导致事件丢失：M0 记录已知限制；需要强一致时增加 outbox 或消息中间件，另行评审。

---

## 6. 最小接口草案

> 以下为契约草案。统一前缀 `/education` 只是建议；RuoYi 现有控制器风格、权限标识和错误码需在评审后确定。
> 响应体沿用 `AjaxResult`（对象/详情/操作）与 `TableDataInfo`（分页列表）。
> 所有 BIGINT ID 在 JSON 中按**字符串**传输，避免 JavaScript 精度丢失。

### 6.1 通用约定

**认证**：沿用若依 `Authorization` 令牌。用户 ID 由服务端认证上下文取得。

**时间**：

- 请求/响应时间均为 RFC3339 UTC，毫秒精度，例如 `2026-10-24T01:30:00.000Z`。
- 数据库 `DATETIME(3)` 按 UTC 存储。
- 服务端当前时间是开放/关闭/已读的唯一权威。
- `opensAt` 包含，`closesAt` 不包含。

**分页**：

- `pageNum`：整数，默认 1，最小 1。
- `pageSize`：整数，默认 10，最小 1，最大 100。
- 排序由服务端固定，不接受客户端 `orderByColumn`。
- 列表响应：`{ "total": 0, "rows": [], "code": 200, "msg": "查询成功" }`。
- 空结果不是错误：`total = 0`，`rows = []`，`code = 200`。

**空值**：

- `closesAt = null` 表示不关闭。
- `readAt = null` 表示未读。
- `bindingId` 在 6-02 的业务通知中必须非空；保留 null 仅用于未来非课程通知。
- 列表字段不返回 `null` 数组；空数组用 `[]`。

**错误**：

- 以响应体 `code` 为准；当前若依全局异常处理通常返回 HTTP 200 + 业务 `code`，前端不能只看 HTTP 状态。
- 建议错误码（草案，未冻结）：
  - `400` 参数/时间窗口/长度不合法
  - `401` 未登录或令牌失效
  - `403` 角色不匹配或没有教学班对象权限
  - `404` 对象不存在、不可见、非本人通知
  - `409` 状态冲突（已归档再发布、草稿归档、编辑非草稿、真实并发冲突）
  - `500` 服务端异常
  - `503` 教学班授权适配不可用（失败关闭，不泄露内部原因）
- `404` 对外不区分公告不存在、外班、退班、未开放、已关闭；前端统一提示“公告不存在或当前不可查看”。内部日志可记录真实原因。
- 错误响应不包含公告正文、通知标题、学生名单等敏感内容。
- 不返回数据库异常原文。

### 6.2 教师创建公告草稿

```http
POST /education/teacher/announcements
```

请求：

```json
{
  "bindingId": "10001",
  "title": "第 3 周课堂安排",
  "body": "本周课堂请携带笔记本电脑。",
  "opensAt": "2026-10-25T00:00:00.000Z",
  "closesAt": "2026-11-01T00:00:00.000Z"
}
```

字段：

| 字段 | 类型 | 必填 | 空值 | 说明 |
| --- | --- | --- | --- | --- |
| `bindingId` | string | 是 | 否 | 课程—教学班绑定 ID |
| `title` | string | 是 | 否 | 1–200 字符 |
| `body` | string | 是 | 否 | 非空；最多 20,000 个 Unicode 码点，且 UTF-8 编码后不超过 65,535 字节；两个上限同时满足 |
| `opensAt` | string/null | 否 | 是 | 为空表示发布时取服务端当前时间 |
| `closesAt` | string/null | 否 | 是 | 为空表示不关闭；非空必须晚于 `opensAt` |

响应 `data`：

```json
{
  "id": "20001",
  "bindingId": "10001",
  "title": "第 3 周课堂安排",
  "body": "本周课堂请携带笔记本电脑。",
  "status": "DRAFT",
  "visibilityState": "DRAFT",
  "opensAt": "2026-10-25T00:00:00.000Z",
  "closesAt": "2026-11-01T00:00:00.000Z",
  "createdByUserId": "90001",
  "publishedByUserId": null,
  "createdAt": "2026-10-24T01:00:00.000Z"
}
```

状态前置：见 3.2。  
错误：`400` 参数非法；`403` 无发布权限；`404` 绑定不存在/已结束（或按统一策略返回 403）。

### 6.3 教师编辑公告草稿

```http
PUT /education/teacher/announcements/{announcementId}
```

请求：

```json
{
  "title": "第 3 周课堂安排（更新）",
  "body": "请携带笔记本电脑，并提前完成预习。",
  "opensAt": "2026-10-25T00:00:00.000Z",
  "closesAt": null
}
```

约束：

- `bindingId` 不可修改；请求中不接收该字段。
- 仅 `DRAFT` 可编辑。
- 创建者可编辑自己的 `DRAFT`；其他教师必须具备本班公告管理权限（本批为 `canPublish`）。
- 未提供的字段是否按“保持不变”处理，需前端契约确认；建议采用完整覆盖（PUT 语义），避免部分更新歧义。
- 无乐观锁；并发编辑最后写入者覆盖；条件更新 0 行时重新读取并区分不存在/跨绑定、非草稿、真实并发。

响应：同 6.2 的公告对象。  
错误：`404` 公告不存在/不属于本班；`403` 非创建者且无公告管理权限；`409` 非草稿状态；`409` 真实并发冲突。

### 6.4 教师发布公告

```http
POST /education/teacher/announcements/{announcementId}/publish
```

请求体：无；发布使用已保存的 `opensAt`/`closesAt`。  
响应 `data`：公告对象，`status = "PUBLISHED"`；若已处于 `PUBLISHED`，重复调用在身份/绑定/权限校验通过后返回当前对象，`code = 200`。  
首次实际发布在同一事务内 fan-out `ANNOUNCEMENT` 通知；重复发布不重复写通知。授权适配器缺失时返回 503 并回滚发布。

错误：

- `403` 无发布权限；
- `404` 公告不存在/不属于本班；
- `409` 已归档，不能再次发布；
- `500` 数据库失败；本地发布事务回滚。

条件更新影响 0 行时：若重读为 `PUBLISHED` 则幂等成功；不存在/跨绑定为 404；`ARCHIVED` 为 409；仍为 `DRAFT` 为真实并发冲突 409。

### 6.5 教师归档公告

```http
POST /education/teacher/announcements/{announcementId}/archive
```

请求体：无。  
响应：公告对象，`status = "ARCHIVED"`；若已处于 `ARCHIVED`，重复调用在身份/绑定/权限校验通过后返回当前对象，`code = 200`。  
错误：`409` 草稿不能归档（受现有 CHECK 约束）；`404` 不存在/不属于本班；`403` 无权限；`409` 真实并发冲突。  
条件更新影响 0 行时：若重读为 `ARCHIVED` 则幂等成功；不存在/跨绑定为 404；非 `PUBLISHED` 为 409；仍为 `PUBLISHED` 为真实并发冲突 409。

### 6.6 教师公告列表

```http
GET /education/teacher/announcements?bindingId=10001&status=PUBLISHED&pageNum=1&pageSize=10
```

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `bindingId` | string | 是 | 必须属于当前教师有效任课班级 |
| `status` | enum | 否 | `DRAFT`/`PUBLISHED`/`ARCHIVED`；不传返回全部 |
| `pageNum` | int | 否 | 默认 1 |
| `pageSize` | int | 否 | 默认 10，最大 100 |

响应行字段：`id`、`bindingId`、`title`、`status`、`visibilityState`、`opensAt`、`closesAt`、`createdByUserId`、`publishedByUserId`、`createdAt`。列表不返回 `body`，详情再取。

排序：`created_at DESC, id DESC`；`id` 用于时间相同时保持稳定顺序。

### 6.7 教师公告详情

```http
GET /education/teacher/announcements/{announcementId}
```

响应：公告对象，含 `body`。  
权限：当前有效任课教师；不要求 `can_publish`。  
错误：`404` 不存在/不属于当前教师任课班级。

### 6.8 学生公告列表

```http
GET /education/student/announcements?bindingId=10001&pageNum=1&pageSize=10
```

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `bindingId` | string | 否 | 不传时返回本人所有当前有效班级的可见公告 |
| `pageNum` | int | 否 | 默认 1 |
| `pageSize` | int | 否 | 默认 10，最大 100 |

响应行字段：`id`、`bindingId`、`title`、`opensAt`、`closesAt`、`createdAt`。不返回 `body`、`publishedByUserId`、`createdByUserId`。

排序：`opens_at DESC, id DESC`；`id` 用于时间相同时保持稳定顺序。  
过滤：仅 `PUBLISHED` 且当前处于 `[opensAt, closesAt)`；绑定有效、教学班 `ACTIVE`、学生当前有效入班。

### 6.9 学生公告详情

```http
GET /education/student/announcements/{announcementId}
```

响应：`id`、`bindingId`、`title`、`body`、`opensAt`、`closesAt`、`createdAt`。  
错误：不存在、外班、退班、未开放、已关闭或已归档统一返回 `404`；前端统一提示“公告不存在或当前不可查看”。不得向无权限用户区分真实存在性；服务端内部日志可记录真实拒绝原因。

### 6.10 学生通知列表

```http
GET /education/student/notices?kind=GRADE&bindingId=10001&unreadOnly=true&pageNum=1&pageSize=10
```

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `kind` | enum | 否 | `ANNOUNCEMENT`/`PREVIEW`/`ACTIVITY`/`ASSIGNMENT`/`GRADE` |
| `bindingId` | string | 否 | 限定某个课程班级绑定 |
| `unreadOnly` | boolean | 否 | 默认 false；true 时只返回 `readAt = null` |
| `pageNum` | int | 否 | 默认 1 |
| `pageSize` | int | 否 | 默认 10，最大 100 |

响应行字段：

```json
{
  "id": "30001",
  "kind": "GRADE",
  "bindingId": "10001",
  "sourceId": "40001",
  "title": "课后作业成绩已发布",
  "visibleAt": "2026-10-24T01:00:00.000Z",
  "readAt": null,
  "isRead": false,
  "createdAt": "2026-10-24T01:00:00.000Z"
}
```

排序：`visible_at DESC, id DESC`；`id` 用于时间相同时保持稳定顺序。  
安全：服务端强制 `recipient_user_id = 当前用户`；请求中出现 `recipientUserId` 不生效。

### 6.11 学生通知详情

```http
GET /education/student/notices/{noticeId}
```

响应：通知对象（同上），不返回正文。  
说明：

- 通知只作为跳转入口，不是访问凭证；进入目标页面后必须调用目标详情接口重新鉴权。
- `ANNOUNCEMENT`：使用通知类型（接口字段 `kind`，路由参数可称 `noticeKind`）、`sourceId`（公告 ID）、`bindingId` 定位对应教学班公告，再调用 6.9 获取正文。目标公告不存在、外班、退班、未开放或已关闭时，目标页面统一显示“公告不存在或当前不可查看”。
- `PREVIEW`：目标页至少需要 `noticeKind`、`sourceId`（预习任务 ID）、`bindingId`；具体路由待确认。
- `ACTIVITY`：目标页至少需要 `noticeKind`、`sourceId`（课堂活动 ID）、`bindingId`；具体路由待确认。
- `ASSIGNMENT`：目标页至少需要 `noticeKind`、`sourceId`（作业 ID）、`bindingId`；具体路由待确认。
- `GRADE`：目标页至少需要 `noticeKind`、`sourceId`（成绩发布记录 ID）、`bindingId`；具体路由待确认。
- 来源撤回、退班、不存在或无权限时，目标页面显示“当前不可查看”；不得用通知数据替代来源详情。
- 本接口不自动标记已读。

错误：`404` 不存在/非本人/不可见；前端统一提示“通知不存在或当前不可访问”，不区分他人通知与不可见通知。

### 6.12 未读数

```http
GET /education/student/notices/unread-count?bindingId=10001
```

参数：`bindingId` 可选；传了必须校验当前成员关系。  
响应（统一 `AjaxResult` 包装）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "unreadCount": 3,
    "asOf": "2026-10-24T01:05:00.000Z"
  }
}
```

规则：只统计当前用户、`visible_at <= now`、`read_at IS NULL`、授权范围内可见的通知；`ANNOUNCEMENT` 通知还要求对应公告当前可见。  
M0 不缓存；每次实时查库。

### 6.13 单条标记已读

```http
POST /education/student/notices/{noticeId}/read
```

请求体：无。  
响应（统一 `AjaxResult` 包装）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "30001",
    "readAt": "2026-10-24T01:06:00.000Z",
    "alreadyRead": false
  }
}
```

幂等：

- 第一次成功写入 `read_at`，`alreadyRead = false`；
- 重复请求返回同一个 `readAt`，`alreadyRead = true`，`code = 200`；
- 并发请求只有一个写入成功，其余读取同一结果；
- 非本人/不可见/不存在返回 `404`，不返回 `readAt`。

### 6.13.1 全部标记已读

```http
PUT /education/student/notices/read-all
```

最终路径采用建议的 `PUT /education/student/notices/read-all`；现有学生通知路径没有其他冻结风格冲突。  
请求体：无；不接受 `recipientUserId`。操作者只来自服务端认证上下文。  
状态：契约已确认；Controller/DTO/Service/Mapper 与隔离测试已实现，尚未真实联调。

作用范围：

- 当前登录学生本人的通知；
- 操作确定的服务端时点之前已经可见的通知（`visible_at <= operatedAt`）；
- 当前 `read_at IS NULL` 的未读通知；
- 不受列表页码、页大小、筛选或当前页选中记录限制；
- 不处理未来才可见的通知，不影响其他用户。

响应（统一 `AjaxResult` 包装）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "markedCount": 3,
    "unreadCount": 0,
    "operatedAt": "2026-10-25T10:00:00.000Z"
  }
}
```

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.markedCount` | int | 本次由未读变为已读的实际记录数，最小 0 |
| `data.unreadCount` | int | 操作完成后的剩余未读数，最小 0 |
| `data.operatedAt` | string | 本次操作确定的服务端 UTC 时间；新标记记录共用该时间 |

没有未读通知时仍成功：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "markedCount": 0,
    "unreadCount": 0,
    "operatedAt": "2026-10-25T10:00:00.000Z"
  }
}
```

幂等与并发：

- 没有未读通知或重复调用仍成功，`markedCount = 0`；不得返回业务失败。
- 已读记录不重复更新 `read_at`。
- 单条已读与全部已读并发时，只允许 `read_at` 从 `NULL` 变为时间，不覆盖已有时间。
- 操作后新产生或新到可见时间的通知仍保持未读。
- `markedCount` 以实际更新行数为准；`unreadCount` 与操作后的未读数查询一致。
- 未登录/认证失效返回业务码 401；角色不匹配返回 403；服务端失败返回 500；HTTP 包装和 `AjaxResult` 关系沿用 6.1。该接口没有“目标通知不存在”的业务 404。

前端调用后必须刷新通知列表和未读数；不要只做本地减一。

### 6.14 接口汇总

共 13 个接口：教师端 6 个，学生端 7 个。教师公告、学生公告只读、学生通知读取/已读的 Controller/DTO/Service/Mapper 与隔离测试已实现；公告发布 ANNOUNCEMENT fan-out 已实现；上游事件接收和生产授权适配器未实现，真实联调未完成。

| 方法 | 路径（草案） | 角色 | 作用 |
| --- | --- | --- | --- |
| POST | `/education/teacher/announcements` | 教师 | 创建草稿 |
| PUT | `/education/teacher/announcements/{id}` | 教师 | 编辑草稿 |
| POST | `/education/teacher/announcements/{id}/publish` | 教师 | 发布；重复发布幂等；首次实际发布同事务 fan-out ANNOUNCEMENT 通知 |
| POST | `/education/teacher/announcements/{id}/archive` | 教师 | 归档；重复归档幂等 |
| GET | `/education/teacher/announcements` | 教师 | 本班公告列表 |
| GET | `/education/teacher/announcements/{id}` | 教师 | 本班公告详情 |
| GET | `/education/student/announcements` | 学生 | 本班可见公告列表 |
| GET | `/education/student/announcements/{id}` | 学生 | 本班可见公告详情 |
| GET | `/education/student/notices` | 学生 | 本人通知列表 |
| GET | `/education/student/notices/{id}` | 学生 | 本人通知详情 |
| GET | `/education/student/notices/unread-count` | 学生 | 本人未读数 |
| POST | `/education/student/notices/{id}/read` | 学生 | 标记单条本人通知已读 |
| PUT | `/education/student/notices/read-all` | 学生 | 标记本人全部当前可见未读通知 |

不提供：公告物理删除、通知删除、管理员业务接口、通知创建公开接口。

---

## 7. 公告读取与通知去重、已读规则

### 7.1 公告可见性判定

学生可见的公告必须同时满足：

```text
status = 'PUBLISHED'
AND opens_at <= now
AND (closes_at IS NULL OR now < closes_at)
AND binding.ended_at IS NULL
AND teaching_class.status = 'ACTIVE'
AND student.joined_at <= now
AND (student.left_at IS NULL OR now < student.left_at)
```

教师可见的公告必须满足：

```text
binding.ended_at IS NULL
AND teaching_class.status = 'ACTIVE'
AND teacher.valid_from <= now
AND (teacher.valid_to IS NULL OR now < teacher.valid_to)
```

教师写操作额外要求 `teacher.can_publish = 1`。

### 7.2 通知去重

数据库已有：

```sql
UNIQUE KEY uq_edu_notice_recipient_source
    (recipient_user_id, notice_kind, source_id)
```

规则：

1. 同一接收人、同一 `notice_kind`、同一 `source_id` 永远只有一条通知。
2. 重复事件/重试使用 `INSERT ... ON DUPLICATE KEY UPDATE id = id` 或捕获唯一键冲突，视为成功。
3. 冲突时不更新 `title`、`visible_at`、`read_at`；已读状态不会被重复事件重置。
4. 同一 `source_id` 在不同 `notice_kind` 下可以各有一条（例如作业发布和成绩发布）。
5. `GRADE` 必须使用 `edu_grade_publication.id`，不能用 `assignment_id`；否则更正发布无法再通知。
6. 公告发布时对同一公告重复 fan-out 不会产生重复通知。
7. 通知的 `title` 是生成时快照；来源标题后续变化不自动改写通知。
8. 通知行不级联删除；来源业务删除/归档时，通知按第 7.4 节处理。

### 7.3 已读规则

- `read_at` 表示**首次成功标记已读的服务端 UTC 时间**。
- 只有接收人本人可以标记；服务端不使用请求中的用户 ID。
- `read_at` 只从 `NULL` 变为时间，不覆盖、不清空、不改成“未读”。
- 重复请求幂等：返回第一次的 `read_at`。
- 通知在 `visible_at` 之前不可读；标记请求按不存在处理。
- 已读不等于已查看来源内容；第 6 组不记录点击、停留、阅读时长。
- 未读数 = 可见且未读的通知数；标记已读成功后，下一次未读数应减少。
- 公告详情阅读不会自动把对应通知标为已读；前端需要在用户进入公告后显式调用单条标记已读。是否“打开即已读”仍由产品决定，本文建议显式标记，避免误清零。
- 全部标记已读在 M0 同时支持：只处理本人、当前可见、当前未读的通知；不受分页影响；使用同一服务端 `operatedAt` 写入新标记记录；重复调用幂等，`markedCount` 可以为 0。详见 3.6.1、6.13.1。

### 7.4 来源状态变化

| 场景 | 公告 | 公告通知 | 其他通知 |
| --- | --- | --- | --- |
| 公告归档 | 学生不可见；教师可见 | 列表/详情/未读数按来源不可见过滤 | 不适用 |
| 公告关闭时间到 | 学生不可见；状态仍 `PUBLISHED` | 同上 | 不适用 |
| 学生退班 | 学生不可见 | 学生不可见 | 学生不可见 |
| 教师撤权 | 写操作立即拒绝；读操作按当前任课关系 | 不影响学生已生成通知 | 不适用 |
| 作业/成绩来源撤回 | 不适用 | 不适用 | 第 6 组不判断；点击后由来源接口拒绝 |
| 通知已读后来源关闭 | 保留 `read_at` | 保留 | 保留 |

### 7.5 通知与授权的关系

- 通知不是访问凭证。持有 `sourceId` 不能绕过来源业务的对象权限。
- 通知列表只证明“曾经应提醒该用户”，不证明用户当前仍有权访问来源。
- 学生退班后，即使通知行存在，第 6 组也不再返回该通知；历史数据保留供审计。
- 来源业务删除/归档后，第 6 组不删除通知行；是否展示由来源接口和产品规则决定。

---

## 8. 数据库现有字段是否足够与变更建议

### 8.1 `edu_course_announcement`

现有字段足以支撑 M0 基本流程：

- `binding_id` 能定位课程—教学班绑定；
- `title`、`body` 能承载正文；
- `status` 能表达草稿/已发布/已归档；
- `opens_at`、`closes_at` 能表达定时开放与关闭；
- `published_by_user_id` 能记录发布人；
- `created_by_user_id` 能记录创建人；
- `created_at` 能排序创建时间。

不足：

1. 当前不新增 `published_at`，也不返回 `publishedAt`；`created_at` 只表示创建时间，`opens_at` 只表示学生开放时间，不称为发布时间。
2. 没有 `updated_at`/版本号，编辑草稿无并发控制。
3. 没有 `archived_at`/`archived_by_user_id`，归档动作只能依赖审计日志。
4. CHECK 约束不允许直接归档草稿；已确定草稿不归档，服务层禁止 `DRAFT → ARCHIVED`。
5. 没有软删除/删除标记；草稿清理没有正式策略。
6. 没有发布目标快照；公告按“当前班级学生”读取，退班/入班后的可见性由成员关系决定。

建议（按优先级）：

- **P0 契约层**：已明确 `created_at` 为创建时间、`opens_at` 为学生开放时间；当前不返回 `publishedAt`、不新增 `published_at`；草稿不归档已确定。
- **P1 DDL（未来建议，不在本批）**：如未来需要审计编辑/归档动作，再评估 `updated_at`/`archived_at`；不新增 `published_at`。
- **P1 DDL**：增加 `archived_at DATETIME(3) NULL`、`archived_by_user_id BIGINT NULL`，或明确只用审计日志。
- **P1 DDL**：草稿归档不在本批范围；当前不调整 `chk_edu_announcement_publish`，由服务层禁止草稿归档。
- **P2**：增加 `version INT` 做乐观锁；增加 `deleted_at` 做软删除。二者非 M0 必需。

建议索引：

- 已有 `(binding_id, status, opens_at, closes_at)` 覆盖学生列表和教师状态筛选，保留。
- 教师列表按 `created_at` 排序，可评估增加 `(binding_id, created_at, id)`；数据量小时非必需。

### 8.2 `edu_user_notice`

现有字段足以支撑 M0：

- `recipient_user_id` 能定位接收人；
- `notice_kind` 能区分五类通知；
- `source_id` 能指向来源业务对象；
- `binding_id` 能限定课程班级；
- `title` 能显示提醒标题；
- `visible_at` 能控制可见时间；
- `read_at` 能表达首次已读；
- 唯一键 `(recipient_user_id, notice_kind, source_id)` 能防止重复生成；
- 索引 `(recipient_user_id, read_at, visible_at)` 能支持未读统计。

不足：

1. 没有 `status`/`revoked_at`/`expires_at`，来源撤回或过期后通知无法标记失效。
2. 没有 `source_kind` 外键或约束；`source_id` 语义完全依赖契约。
3. 没有 `event_id`/创建来源字段，重复事件只能靠业务唯一键，排查困难；`eventId` 可只记日志，不落库。
4. 没有 `updated_at`；通知本身设计为不可变，问题不大。
5. `binding_id` 可空；6-02 业务通知应始终非空，需服务层保证。
6. 列表按 `visible_at` 排序时，现有索引 `(recipient_user_id, read_at, visible_at)` 不是最优；可增加 `(recipient_user_id, visible_at, id)`。
7. 没有“通知生成时的成员关系快照”；退班/重新入班的历史可见性只能按当前成员关系或时间区间推断。
8. 没有通知正文/链接字段；本文建议只存标题，详情由来源页面提供，避免重复存储和越权。

建议（按优先级）：

- **P0 契约层**：冻结 `notice_kind → source_id` 映射；冻结 `GRADE` 使用 `edu_grade_publication.id`。
- **P1 DDL**：增加 `revoked_at DATETIME(3) NULL` 或 `status`，用于成绩撤回/来源失效；M0 如不做撤回通知，可缓。
- **P1 DDL**：增加 `expires_at DATETIME(3) NULL`，用于临时提醒过期；M0 非必需。
- **P2**：增加 `event_id` 或通用 outbox，用于可靠投递与排查；非 M0 必需。
- **P1 索引**：增加 `(recipient_user_id, visible_at, id)` 支持列表排序；增加 `(recipient_user_id, binding_id, read_at)` 支持按课程未读数（可选）。

### 8.3 `edu_metric_snapshot` 不用于 6-02

- 该表是带口径、时间窗、分母、样本数的学情指标快照，不是事务型收件箱。
- 未读数是 `edu_user_notice` 的实时聚合，不是学习指标；用 `edu_metric_snapshot` 会引入统计延迟和口径错误。
- 6-02 不读写 `edu_metric_snapshot`；6-01 若需要总览指标，另行设计。

### 8.4 相关表的结构性注意点

1. `edu_class_teacher.active_user_id` 生成列只对 `valid_to IS NULL` 产生非空值；若存在“未来结束时间”的任课记录，唯一索引仍可能允许重复。服务层必须同时校验 `valid_from`/`valid_to`，不能只看 `valid_to IS NULL`。
2. `edu_class_student.active_user_id` 同理，只对 `left_at IS NULL` 生成非空值；服务层必须校验 `joined_at` 和 `left_at`。
3. `edu_course_class_binding.active_space_id` 只对 `ended_at IS NULL` 生成；服务层必须检查 `ended_at`，并确认绑定、教学班、课程空间属于同一组织（外键已保证部分关系）。
4. 教学班 `status` 只有 `DRAFT`/`ACTIVE`/`ARCHIVED`；公告读写应要求 `ACTIVE`。学期日期是否纳入判定，需第 2 组确认。
5. 这些表属于第 2 组；第 6 组不得直接写 SQL 查询，必须通过服务契约。

---

## 9. 验收用例（Given-When-Then）

### 9.1 正常流程

**N-01 教师发布即时公告，学生可见**

- Given 教师 T 在教学班 C 有当前有效任课记录且 `can_publish = 1`；学生 S 在 C 有当前有效入班记录；绑定 B 未结束。
- When T 创建公告 A（`opensAt` 为空）并发布。
- Then A 状态为 `PUBLISHED`；`opensAt` 为服务端发布时刻；S 的公告列表包含 A；S 的详情返回正文；发布事务内为 S 生成一条 `ANNOUNCEMENT` 通知，`visibleAt = opensAt`。

**N-02 定时公告在开放前不可见**

- Given 公告 A 已发布，`opensAt` 为未来时间。
- When 学生在 `opensAt` 之前请求公告列表/详情。
- Then 列表不包含 A，详情返回 404；教师列表仍显示 A，`visibilityState = NOT_YET_OPEN`。
- When 服务端时间到达 `opensAt` 后再次请求。
- Then 学生可见 A；对应通知的 `visibleAt = opensAt`，开放前同样不可见；未读数规则见 3.7。

**N-06 重复发布幂等成功**

- Given 公告 A 已是 `PUBLISHED`，教师 T 仍有当前有效 `can_publish`。
- When T 再次调用发布。
- Then 返回当前公告，`code = 200`；`publishedByUserId`、`opensAt` 不被重置；不产生第二条公告。

**N-07 重复归档幂等成功**

- Given 公告 A 已是 `ARCHIVED`，教师 T 仍有当前有效 `can_publish`。
- When T 再次调用归档。
- Then 返回当前公告，`code = 200`；状态仍为 `ARCHIVED`。

**N-08 已归档公告不能再次发布**

- Given 公告 A 已是 `ARCHIVED`。
- When 有 `can_publish` 的教师 T 调用发布。
- Then 返回状态冲突；A 仍为 `ARCHIVED`，不写公告通知。

**N-09 列表时间相同按 id 倒序稳定排序**

- Given 教师公告两条 `created_at` 相同，或学生公告两条 `opens_at` 相同，或通知两条 `visible_at` 相同。
- When 请求对应列表。
- Then 先按对应时间倒序；时间相同时较大 `id` 在前；前端不自行重排。

**N-03 公告关闭后学生不可见**

- Given 公告 A 的 `closesAt` 已到。
- When 学生请求列表/详情。
- Then A 不在列表，详情 404；教师仍可查看 A，`visibilityState = CLOSED`；A 的状态仍是 `PUBLISHED`。

**N-04 标记已读幂等**

- Given 学生 S 有一条未读通知 N。
- When S 第一次调用标记已读。
- Then `read_at` 写入服务端当前时间；未读数减 1；返回 `alreadyRead = false`。
- When S 再次调用同一接口。
- Then 返回同一个 `readAt`；`alreadyRead = true`；未读数不再变化。

**N-05 上游重复事件不重复生成通知**

- Given 第 5 组针对同一成绩发布记录 P 连续发送两次 `GRADE` 事件给同一学生。
- When 第 6 组消费两次。
- Then 只存在一条 `(recipient, GRADE, P)` 通知；第二次视为幂等成功；该通知的 `read_at` 不被重置。

### 9.2 空数据

**E-01 无公告**

- Given 教师 T 的本班没有任何公告。
- When T 请求公告列表。
- Then 返回 `code = 200`、`total = 0`、`rows = []`。

**E-02 学生无可见公告**

- Given 本班只有草稿、已归档或未开放公告。
- When 学生请求公告列表。
- Then 返回空列表，不返回草稿标题/正文，不报错。

**E-03 无通知**

- Given 学生 S 没有任何通知。
- When S 请求通知列表/未读数。
- Then 列表 `total = 0`、`rows = []`；`unreadCount = 0`。

**E-04 发布时无有效学生**

- Given 绑定 B 当前没有有效学生。
- When 教师发布公告。
- Then 公告发布成功；教师列表可见；fan-out 写入 0 条通知，不报错。

### 9.3 无权限与越权

**U-01 教师无发布权限**

- Given 教师 T 在 C 有任课记录，但 `can_publish = 0`。
- When T 创建/发布/归档公告，或编辑其他教师创建的草稿。
- Then 返回 403；不写入公告。
- When T 编辑本人创建的 `DRAFT` 或读取本人草稿。
- Then 允许；但 T 仍不能发布或归档该草稿。

**U-08 无权限教师不能通过重复操作探测或修改公告**

- Given 教师 T 没有 C 的 `can_publish`，或不是 C 的当前任课教师。
- When T 对已发布公告重复调用发布/归档，或对不存在/外班公告调用写操作。
- Then 在身份/绑定/权限校验阶段返回 403/404；不因幂等分支返回公告对象；不修改任何公告。编辑操作即使需要加载公告，也必须在返回前通过创建者或公告管理权限校验。

**U-02 外班教师读取公告**

- Given 教师 T 不是教学班 C 的当前任课教师。
- When T 请求 C 绑定下的公告列表或详情。
- Then 返回 403 或 404（按统一策略）；不返回标题、正文、学生名单。

**U-03 外班学生读取公告**

- Given 学生 S 不在教学班 C。
- When S 请求 C 的公告列表/详情。
- Then 返回空列表或 404；不泄露公告存在性和正文。

**U-04 退班学生读取公告/通知**

- Given 学生 S 已退班，`left_at` 非空。
- When S 请求该班公告或通知。
- Then 公告列表不包含该班公告；详情 404；通知列表/未读数不包含该班通知；历史行仍在数据库中。

**U-05 伪造接收人**

- Given 学生 S 请求通知列表时传入 `recipientUserId = 其他学生`。
- When 服务端处理。
- Then 忽略该参数，只返回 S 本人的通知；不得返回他人通知；必要时记录异常请求日志。

**U-06 管理员调用业务接口**

- Given 教务管理员或超级管理员未获得 6-02 业务权限。
- When 调用教师/学生公告通知接口。
- Then 返回 403；不能凭超管身份读取公告正文或个人通知。

**U-07 教师撤权后立即失效**

- Given 教师 T 的任课记录 `valid_to` 已到或被提前结束。
- When T 再次发布/归档/读取本班公告。
- Then 写操作 403；读操作 403/404；已发布公告对学生仍按原可见性生效。

### 9.4 失败与异常

**F-01 参数非法**

- Given 标题为空、超过 200 字符，或 `closesAt <= opensAt`。
- When 教师创建/编辑公告。
- Then 返回 400；不写入数据库；不生成通知。

**F-02 状态冲突**

- Given 公告已 `ARCHIVED`。
- When 教师再次发布。
- Then 返回 409；公告状态不变；不生成新通知。

**F-03 草稿归档冲突**

- Given 公告为 `DRAFT`。
- When 教师调用归档。
- Then 返回 409；不伪造 `published_by_user_id`/`opens_at`。

**F-04 公告发布事务失败（包含 fan-out）**

- Given 公告状态更新、重读或 fan-out 授权/写入失败。
- When 发布事务执行。
- Then 公告状态与已写入的通知批次一起回滚；教师收到 500/503；不存在“已发布但无通知”或部分通知的半成品。

**F-05 上游通知失败不回滚来源**

- Given 第 5 组已成功发布成绩并提交事务；随后通知第 6 组失败。
- When 上游收到通知失败结果或异常。
- Then 成绩发布保持成功；学生仍可在来源接口看到已发布成绩；第 6 组记录失败并允许重试；上游接口不因通知失败回滚。

**F-06 并发标记已读**

- Given 同一学生同一通知被两个请求同时标记已读。
- When 两个请求并发执行。
- Then 只有一个请求写入 `read_at`；两个请求最终返回同一个 `readAt`；不会产生第二行或覆盖时间。

**F-07 通知不可见时标记已读**

- Given 通知 `visible_at` 在未来，或学生已退班。
- When 学生调用标记已读。
- Then 返回 404；不写 `read_at`。

**F-08 分页越界**

- Given `pageSize = 0` 或 `pageSize > 100`。
- When 请求列表。
- Then 返回 400；不执行无界查询。

**F-09 条件更新影响 0 行的分类**

- Given 发布/归档/编辑的条件更新影响 0 行。
- When Service 重新读取公告。
- Then 已处于目标状态（发布目标 `PUBLISHED`、归档目标 `ARCHIVED`）返回当前公告并视为幂等成功；不存在或跨绑定返回 404；非法状态返回 409 状态冲突；仍处于源状态返回 409 真实并发冲突。不得把全部 0 行统一返回成功或统一返回冲突。

### 9.5 全部标为已读

**R-01 多条未读全部标记**

- Given 学生 S 有 3 条当前可见未读通知。
- When S 调用全部标记已读。
- Then 3 条 `read_at` 均写入本次 `operatedAt`；`markedCount = 3`；`unreadCount = 0`。

**R-02 不影响其他用户**

- Given 学生 S 和另一学生 T 都有未读通知。
- When S 调用全部标记已读。
- Then 只更新 S 的通知；T 的 `read_at` 不变。

**R-03 已读记录不更新时间**

- Given S 有 1 条已读通知和 1 条未读通知。
- When S 调用全部标记已读。
- Then 已读通知的原 `read_at` 不变；只有未读通知更新；`markedCount = 1`。

**R-04 当前页只有一条仍处理全部**

- Given 通知列表当前页只显示 1 条，S 共有 4 条当前可见未读通知。
- When S 调用全部标记已读。
- Then 4 条全部变为已读；`markedCount = 4`；不受分页限制。

**R-05 没有未读通知**

- Given S 当前可见通知都已读。
- When S 调用全部标记已读。
- Then 返回成功；`markedCount = 0`；`unreadCount = 0`。

**R-06 重复调用幂等**

- Given S 已调用过一次全部标记已读。
- When S 再次调用。
- Then 返回成功；`markedCount = 0`；已有 `read_at` 不改变。

**R-07 操作后新通知保持未读**

- Given S 完成全部标记已读后，上游又生成一条新的可见通知。
- When S 查询未读数。
- Then 新通知仍为未读，`unreadCount` 包含它。

**R-08 未来通知不提前标记**

- Given S 有一条 `visible_at` 在操作时点之后的未读通知。
- When S 调用全部标记已读。
- Then 该通知 `read_at` 仍为 NULL；`markedCount` 不包含它。

**R-09 返回未读数与查询一致**

- Given S 调用全部标记已读返回 `unreadCount = N`。
- When S 立即调用未读数接口。
- Then 查询结果与 N 一致（同一数据范围、同一服务端时点口径）。

**R-10 未登录/认证失效**

- Given 请求未登录或令牌失效。
- When 调用全部标记已读。
- Then 返回 401；不修改任何通知；不泄露通知数量或内容。

---

## 10. 后续实施步骤与预计涉及文件

### 10.1 实施前置决策

在写代码前需要确认：

1. 2.1 的领域归属：第 6 组是否拥有公告/通知表写入权。
2. 2.6 的 `source_id` 映射，尤其 `GRADE` 使用 `edu_grade_publication.id`。
3. 2.7 的退班历史访问策略；本文按“当前有效成员才可读”设计。
4. 2.9 的公告发布通知：已在发布事务内实现 ANNOUNCEMENT fan-out，按 `[joinedAt, leftAt)` 过滤发布时点，唯一键去重；上游事件接收仍待后续批次。
5. 2.3 的草稿归档策略：已确定草稿不可归档，服务层禁止 `DRAFT → ARCHIVED`。
6. 2.10 的管理员边界；本文按“M0 无管理员业务接口”设计。
7. `published_at` 当前不新增、`publishedAt` 不返回；`archived_at`、`updated_at`、通知撤回字段仍为未来评审项，不在本批。

### 10.2 实施顺序

1. **冻结契约**：评审本文，确认接口、字段、错误、权限、时间、空值和状态。
2. **接入教育模块**：新增 `ruoyi-education/pom.xml`，在根 POM 注册模块，在 `ruoyi-admin` 增加依赖；核对组件扫描、Mapper 扫描和安全配置。
3. **落权限**：由第 1 组配置 `education:announcement:*`、`education:notice:*` 权限标识和角色菜单；未完成前不要用前端隐藏代替。
4. **实现第 2 组契约适配**：先完成绑定上下文、教师授权、学生成员、批量学生查询；第 6 组只调用契约。
5. **实现公告**：Entity/DTO/枚举、Mapper、Service、教师/学生 Controller；先做读取，再做创建/编辑/发布/归档。
6. **实现通知**：通知查询、未读数、单条标记已读、全部标记已读和公告发布 ANNOUNCEMENT fan-out 已实现（含隔离测试）；上游事件接收仍未实现；唯一键冲突按幂等处理。
7. **接上游事件**：与第 3、4、5 组联调 `PREVIEW`/`ACTIVITY`/`ASSIGNMENT`/`GRADE` 事件；确认失败隔离和重试。
8. **测试**：单元测试 + 独立测试库集成测试，覆盖第 9 节用例；使用虚构账号和演示数据。
9. **文档**：更新 `docs/api-contracts.md`、`docs/architecture.md`，把本文从“草案”改为“已评审/已实现”时必须附验证证据。
10. **验收**：按 G0～G3 关口留接口契约、演示账号、正常/空/无权限/失败用例、构建与联调记录、负责人和审核人。

### 10.3 预计涉及文件（教师公告、学生公告只读、学生通知读取/已读、公告 fan-out 已实现；上游事件未实现）

| 文件/目录 | 用途与当前状态 |
| --- | --- |
| `backend/pom.xml` | 已修改：注册 `ruoyi-education` 模块、依赖管理 |
| `backend/ruoyi-education/pom.xml` | 已创建：教育业务模块构建描述 |
| `backend/ruoyi-admin/pom.xml` | 已修改：增加对 `ruoyi-education` 的依赖 |
| `backend/ruoyi-education/src/main/java/com/ruoyi/education/course/controller/TeacherAnnouncementController.java` | 已实现：教师公告接口 |
| `.../controller/StudentAnnouncementController.java` | 已实现：学生公告只读列表/详情 |
| `.../controller/StudentNoticeController.java` | 已实现：学生通知列表/详情/未读数/单条已读/全部已读 |
| `.../domain/CourseAnnouncement.java`、`AnnouncementStatus.java`、`AnnouncementVisibility.java` | 已实现：公告实体/状态/可见性 |
| `.../domain/StudentNotice.java`、`NoticeKind.java` | 已实现：通知实体/类型 |
| `.../domain/TeacherAnnouncementAuthority.java`、`StudentAnnouncementAuthority.java` | 已实现：授权快照 |
| `.../authorization/TeachingClassAuthorizationPort.java`、`TeacherAnnouncementAccess.java` | 已实现：教师授权端口/快照；生产适配器未接入 |
| `.../authorization/StudentAnnouncementAuthorizationPort.java`、`StudentAnnouncementAccess.java`、`StudentAnnouncementMember.java` | 已实现：学生授权端口/快照/成员快照（含 joinedAt/leftAt）；生产适配器未接入 |
| `.../dto/TeacherAnnouncement*.java`、`StudentAnnouncement*.java`、`StudentNotice*.java` | 已实现：公告/通知请求与响应 DTO |
| `.../service/CourseAnnouncementService.java`、`StudentAnnouncementService.java`、`StudentNoticeService.java` | 已实现：公告读写、学生公告只读、学生通知读取/已读 |
| `.../service/AnnouncementNoticeFanoutPort.java`、`AnnouncementNoticeFanoutService.java` | 已实现：公告发布 fan-out（同事务、500 条分批、唯一键幂等）；生产学生授权适配器未接入 |
| 上游 PREVIEW/ACTIVITY/ASSIGNMENT/GRADE 事件接收服务 | 未实现：尚无对应生产类或事件接收入口 |
| `.../mapper/CourseAnnouncementMapper.java`、`StudentNoticeMapper.java` | 已实现：公告/通知 Mapper |
| `backend/ruoyi-education/src/main/resources/mapper/education/CourseAnnouncementMapper.xml`、`StudentNoticeMapper.xml` | 已实现：公告/通知 Mapper XML |
| `.../policy/AnnouncementStatePolicy.java`、`exception/*`、`web/AnnouncementExceptionHandler.java` | 已实现：状态规则、异常映射 |
| `backend/ruoyi-education/src/test/java/...` | 已落地：公告、学生公告、学生通知和公告 fan-out 的 H2/Mockito/MockMvc 隔离测试 |
| `sql/` | 权限菜单种子（若第 1 组要求）；不新增业务表 |
| `docs/api-contracts.md` | 评审后补契约摘要 |
| `docs/architecture.md` | 补充公告/通知归属和事件边界 |

现有实现位于 `com.ruoyi.education.course` 下的 `controller`、`domain`、`dto`、`exception`、`mapper`、`policy`、`service`、`authorization`、`support`、`web` 子包；没有新增 `communication` 领域。数据库设计中两张表仍归 `course` 领域，若未来决定新增独立领域，需要先修改九个领域的固定约定。

### 10.4 不在本轮交付

- Vue 页面；
- 上游 PREVIEW/ACTIVITY/ASSIGNMENT/GRADE 事件接收；
- 任何 SQL/数据库变更或测试库写入；
- 任何菜单/角色/权限数据变更；
- 管理员公告治理、校园资讯、留言、系统通知模板、AI 功能。
- 真实 MySQL、完整 Spring Boot 应用、HTTP、真实登录/角色拦截/审计和真实教学班授权联调；当前教师公告、学生公告只读、学生通知读取/已读的 Controller/DTO/Service/Mapper 及隔离测试已落地，但不等于真实联调完成。

---

## 11. 结论

- 6-02 的 M0 最小闭环可以用现有 `edu_course_announcement` 和 `edu_user_notice` 完成：公告创建/发布/归档、学生本班公告读取、个人通知列表/详情/未读数、单条标记已读和全部标记已读。
- 关键风险不在表数量，而在**归属、授权、来源 ID 语义、退班策略和通知可靠性**；这些必须先评审，不能用代码猜测。
- 成功标准：教师只能操作自己当前有效任教的班级；草稿读取/编辑按创建者或本班公告管理权限校验，发布/归档必须有 `canPublish`；学生只能读取自己当前有效班级的公告和本人通知；外班、退班、未发布、未开放、已关闭、已归档内容均不可见；重复发布/归档在权限校验通过后幂等成功，非法状态仍冲突；通知后续批次保证去重和已读幂等。
