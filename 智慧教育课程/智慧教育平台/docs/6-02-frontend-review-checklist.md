# 6-02 前后端契约核对清单

> 状态：**核对中，不是联调完成，也不是 6B 已确认。**
>
> 文档收口：2026-11-03 已在本 PR 修正 handoff 的 A-3 错误码、逐端点 503 条件、B-4 已读可见性条件和 404 文案；不代表 6B 已确认或真实联调完成。
>
> 核对日期：2026-11-03
>
> 范围：6-02 教师公告 6 个、学生公告 2 个、学生通知 5 个接口，共 13 个；另核对 C-1 bindingId 来源和 6B 失败态。
>
> 后端：`main@f21f51d55c363738c03ee6f626c7c67b904d6b36`（#18/#20/#21/#22 已合并）。
>
> 6B：`six@97ad8bda702949c9710afe44694bd878e443d92b`，无 PR；分支基于 `4f7b801`，相对 main 领先 4、落后 14。
>
> 说明：输入文件 `6-02-6B-http-interface-request.md` 与 6B 分支同文件一致；其“B 待实现/待合并、fan-out 后续实现”等状态已过时，本文按 main 代码更正。

## 0. 证据层级与状态口径

| 层级 | 含义 |
| --- | --- |
| L1 | 静态源码、DTO、Mapper XML、配置、现有文档 |
| L2 | MockMvc/Mockito/隔离测试源码；本轮未重跑 |
| L3 | 真实 HTTP、真实登录、角色拦截、真实数据库/授权适配器 |

必须分开记录：

- **已合并**：代码在 main。
- **已部署**：有可访问运行实例；本轮无证据。
- **契约已确认**：6A/6B/负责人签署；本轮无。
- **真实联调**：L3 通过；本轮无。

## 1. 版本与评审状态

| 项目 | 版本/引用 | 证据 | 6A 确认 | 6B 确认 | 真实联调 |
| --- | --- | --- | --- | --- | --- |
| 6-02 后端 | `main@f21f51d55c363738c03ee6f626c7c67b904d6b36`；#18/#20/#21/#22 已合并 | `git ls-remote`；Controller/Service/Mapper | 待确认（不代签） | 待确认 | 未完成 |
| 6B 前端 | `six@97ad8bda702949c9710afe44694bd878e443d92b`；无 PR | 分支树、代码、`docs/6-02-frontend-integration.md` | 待确认 | 待确认 | 未完成 |
| 6B 输入清单 | 同 6B 分支 `docs/6-02-6B-http-interface-request.md`；最后更新 2026-10-28，对应 main `4f7b801` | 与 E: 输入文件逐字相同（diff 为空） | 不适用 | 待 6B 更新 | 未完成 |
| 交接文档 | `docs/6-02-frontend-api-handoff.md` v0.3（2026-11-03 文档收口） | 本 PR 修订 | 待确认 | 待 6B | 未完成 |
| 第 2 组 #26 | PR #26 head `3e74e875edf759a0887f49f56e5b82103339878b`；state=closed、draft=true；base main `4f7b801` | GitHub API；`origin/pr26`；`docs/group2/api-provided.md` v1.0-draft | 待确认 | 待确认 | 未完成 |

### 1.1 合并、部署、确认、联调

| 项 | 状态 | 证据 |
| --- | --- | --- |
| 6-02 后端合并 main | 是 | `f21f51d` 含 #18/#20/#21/#22 |
| 6B 前端合并 | 否 | `six` 无 PR，落后 main 14 |
| 后端部署 | 无证据 | 未提供运行地址/部署记录 |
| 契约确认 | 未完成 | 6A/6B/负责人记录为空 |
| 生产授权适配器 | 未接入 | `TeachingClassAuthorizationPort`、`StudentAnnouncementAuthorizationPort` 仅接口/测试替身 |
| 真实联调 | 未完成 | 无真实 HTTP、登录、角色/审计证据 |
| 公告 fan-out | 已实现并合并 | #20；`CourseAnnouncementService.publish` 同事务调用 `AnnouncementNoticeFanoutService` |
| 上游事件接收 | 已实现并合并 | #21；PREVIEW/ACTIVITY/ASSIGNMENT/GRADE 内部端口 |

## 2. 13 个接口逐项矩阵

> 所有接口当前若依风格为 HTTP 200 + `body.code`；真实网关是否映射 4xx/5xx 未验证。401 由认证入口返回，403 由 `@PreAuthorize`/业务异常返回，503 主要来自授权端口缺失/异常或 fan-out 授权失败。

### 2.1 A 类：教师公告 6 个 + 学生公告 2 个

| ID | 方法/路径 | 参数/请求体 | 角色/权限 | 后端实际行为（证据） | 6B 实际调用（证据） | 一致/差异 | 幂等/错误 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A-1 | `GET /education/teacher/announcements` | 查询 `bindingId` 必填；`status` 可选；`pageNum`/`pageSize` | 教师；`education:announcement:list` | `TableDataInfo total/rows`；行不含 body；ID 字符串、时间 `.SSSZ`；bindingId 缺失/空/非正整数 400；status 非法 400；pageSize 1–100；授权缺失 503；非本班 403 | `announcement.js:teacherList`；`teacher/announcements.js:104-121` 无 bindingId 不发请求；`http.js` 同时判 HTTP/code | 形状/参数一致；输入已列 503，交接文档端点失败表漏列 | 只读 |
| A-2 | `GET /education/teacher/announcements/{announcementId}` | 路径 ID | 教师；`education:announcement:list` | 详情含 body；不存在/跨班/草稿无权 → 404；授权端口缺失 → 503；角色不符 → 403 | `announcement.js:teacherDetail`；`teacher/announcements.js:176-199` | 字段一致；503 未在输入失败表列出 | 只读 |
| A-3 | `POST /education/teacher/announcements` | JSON `bindingId/title/body/opensAt/closesAt` | 教师；`education:announcement:add` | 创建 DRAFT；标题 1–200 码点，正文非空 ≤20000 码点且 ≤65535 UTF-8 字节；时间毫秒精度；无 404、无 409；授权 503；参数 400；持久化 500 | `announcement.js:teacherCreate`；`teacher/announcements.js:315-360` | **差异**：输入期望 409，代码无 409；输入/交接的 binding 404 不成立 | 创建无幂等键；网络重试可能重复草稿 |
| A-4 | `PUT /education/teacher/announcements/{announcementId}` | 路径 ID；JSON 不含 bindingId | 教师；`education:announcement:edit` | 完整覆盖；只有 DRAFT；不存在 404；跨班/无该班权限 403；非草稿/真实并发 409；授权 503；参数 400 | `announcement.js:teacherUpdate`；`teacher/announcements.js:315-360` | 字段一致；输入已列 503，交接文档端点失败表漏列 | 幂等：同内容 0 行重读后返回当前；不同内容并发 409 |
| A-5 | `POST /education/teacher/announcements/{announcementId}/publish` | 无请求体（可 `{}`） | 教师；`education:announcement:publish` | DRAFT→PUBLISHED；首次同事务 fan-out；已 PUBLISHED 返回 200 且不重复 fan-out；ARCHIVED 409；不存在 404；授权/fan-out 503；关闭时间非法 400 | `announcement.js:teacherPublish`；`teacher/announcements.js:201-226` | 幂等语义一致；503 条件未在输入完整列出 | 重复发布 200；授权不可用时可能 503 |
| A-6 | `POST /education/teacher/announcements/{announcementId}/archive` | 无请求体（可 `{}`） | 教师；`education:announcement:archive` | PUBLISHED→ARCHIVED；已 ARCHIVED 返回 200；DRAFT 409；不存在 404；授权 503 | `announcement.js:teacherArchive`；`teacher/announcements.js:201-226` | 幂等语义一致；503 未列 | 重复归档 200；授权不可用时可能 503 |
| A-7 | `GET /education/student/announcements` | 查询 `bindingId` 可空；`pageNum/pageSize` | 学生角色（非 admin/teacher） | bindingId 不传 = 本人全部有效班级；空串/空白 400；显式非本班 403；仅返回 PUBLISHED 且 `[opensAt,closesAt)` 内；授权缺失 503；分页 400/500 | `announcement.js:studentList`；`student/announcements.js`；`http.js` 空串剔除 | 形状/参数一致；503 未列 | 只读 |
| A-8 | `GET /education/student/announcements/{announcementId}` | 路径 ID | 学生角色（非 admin/teacher） | 不存在/外班/退班/未开放/已关闭/已归档 → 404；角色不符 403；可见但授权端口缺失 503；参数非法 400 | `announcement.js:studentDetail`；`student/announcements.js` | 404 语义基本一致；503 未列 | 只读 |

### 2.2 B 类：学生通知 5 个

| ID | 方法/路径 | 参数/请求体 | 角色/权限 | 后端实际行为（证据） | 6B 实际调用（证据） | 一致/差异 | 幂等/错误 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| B-1 | `GET /education/student/notices` | 查询 `kind`、`bindingId`、`unreadOnly`、`pageNum/pageSize` | 学生角色（非 admin/teacher） | 返回本人当前可见通知；`bindingId` 不传 = 全部；空串/空白 400；显式非本班 403；`kind` 非法/空白 400；`unreadOnly` 只接受 true/false；授权缺失 503；`isRead = readAt != null` | `notice.js:list`；`student/notices.js:175-220`；空串由 `http.js` 剔除 | 字段/参数一致；503 未列 | 只读 |
| B-2 | `GET /education/student/notices/{noticeId}` | 路径 ID | 学生角色 | 返回列表字段，**不含正文**；他人/未到 visibleAt/退班/来源公告不可见 → 404；授权缺失 503 | `notice.js:detail`；`student/notices.js:244-298` | 字段一致；503 未列 | 只读 |
| B-3 | `GET /education/student/notices/unread-count` | 查询 `bindingId` 可空 | 学生角色 | `unreadCount` 为 number；`asOf` 为服务端 UTC 毫秒；无未读仍 200/0；空串 400；显式非本班 403；授权缺失 503 | `notice.js:unreadCount`；`student/notices.js:163-173` 仅读 unreadCount，未用 asOf | 字段一致；前端未消费 asOf | 只读 |
| B-4 | `POST /education/student/notices/{noticeId}/read` | 无请求体（可 `{}`） | 学生角色 | 首次写 `readAt`，`alreadyRead=false`；已读且当前可见返回原 `readAt`、`alreadyRead=true`；已读但当前不可见 → 404；并发 0 行重读；授权缺失 503 | `notice.js:markRead`；`student/notices.js:261-280` 只用 readAt，未用 alreadyRead | **差异**：输入称重复调用总返回同一 readAt；代码受可见性约束 | 幂等条件：当前可见；不可见 404 |
| B-5 | `PUT /education/student/notices/read-all` | 无路径/查询/请求体；可 `{}` | 学生角色 | 仅本人当前可见未读；`markedCount` 实际更新数；`unreadCount` 操作后剩余；`operatedAt` 服务端时间；0 条仍 200；授权缺失 503 | `notice.js:markAllRead`；`student/notices.js:222-240` 用 markedCount 后重拉列表/未读；未用 unreadCount/operatedAt | 字段一致；前端未消费 unreadCount/operatedAt | 重复调用 markedCount=0 成功 |

### 2.3 错误码矩阵

> 当前若依实现为 HTTP 200 + `body.code`；真实网关是否映射 4xx/5xx 未验证。`—` 表示当前代码没有该业务码路径。

| ID | 400 | 401 | 403 | 404 | 409 | 500 | 503 | 主要触发 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A-1 | 是 | 是 | 是 | — | — | 是 | 是 | bindingId/status/page；非本班；授权缺失 |
| A-2 | 是 | 是 | 是 | 是 | — | 是 | 是 | ID 非法；角色；不存在/跨班/草稿无权；授权缺失 |
| A-3 | 是 | 是 | 是 | — | — | 是 | 是 | 参数；权限；持久化；授权缺失。无 404/409 |
| A-4 | 是 | 是 | 是 | 是 | 是 | 是 | 是 | 参数；不存在/跨班；非草稿/并发；授权缺失 |
| A-5 | 是 | 是 | 是 | 是 | 是 | 是 | 是 | 关闭时间；不存在；已归档/并发；fan-out/授权缺失 |
| A-6 | 是 | 是 | 是 | 是 | 是 | 是 | 是 | ID 非法；不存在；草稿/并发；授权缺失 |
| A-7 | 是 | 是 | 是 | — | — | 是 | 是 | bindingId/page；显式非本班；授权缺失 |
| A-8 | 是 | 是 | 是 | 是 | — | 是 | 是 | ID；角色；不可见；授权缺失 |
| B-1 | 是 | 是 | 是 | — | — | 是 | 是 | kind/bindingId/unreadOnly/page；显式非本班；授权缺失 |
| B-2 | 是 | 是 | 是 | 是 | — | 是 | 是 | ID；角色；不可见；授权缺失 |
| B-3 | 是 | 是 | 是 | — | — | 是 | 是 | bindingId；显式非本班；授权缺失 |
| B-4 | 是 | 是 | 是 | 是 | 是 | 是 | 是 | ID；不可见；并发；授权缺失 |
| B-5 | — | 是 | 是 | — | — | 是 | 是 | 无参数；角色；持久化；授权缺失 |

## 3. B 类重点核对

### 3.1 字符串 ID、isRead、首次 readAt

- 后端 `StudentNoticeResponseMapper.id(Long)` 统一 `String.valueOf`；`StudentNoticeResponse` 字段 `id/bindingId/sourceId` 均为 String（`StudentNoticeResponseMapper.java`）。
- `isRead` 由 `notice.getReadAt() != null` 生成；`readAt` 是首次已读时间。
- `markRead` 只在 `read_at IS NULL` 时更新；重复调用返回原 `readAt`（`StudentNoticeService.java:126-171`；`StudentNoticeMapper.xml` `markRead`）。
- 前端 `row.isRead/row.readAt` 直接使用；`esc()` 输出 `data-id`，不做数值转换（`student/notices.js:207-215`）。
- 结论：字段形状一致；B-4 的“重复调用总返回同一 readAt”只在通知仍可见时成立（见 D-08）。

### 3.2 通知详情不含正文

- `StudentNoticeResponse` 没有 `body`；`StudentNoticeResponseMapper.toResponse` 只映射元数据（`StudentNoticeResponse.java`、`StudentNoticeResponseMapper.java`）。
- 前端详情只显示标题、类型、可见时间、已读状态与来源跳转（`student/notices.js:250-266`），并明确“正文由来源页面提供”。
- 结论：一致。

### 3.3 bindingId：不传 vs 空字符串

- `StudentNoticeRequestParser.optionalPositiveId`：`null` → null（全部）；`isBlank()` → 400；非正整数/非数字 → 400（`StudentNoticeRequestParser.java:17-42`）。
- 学生公告列表 `StudentAnnouncementController`：`bindingId == null` → null（全部）；非 null 但空/空白 → `requiredId` → 400（`StudentAnnouncementController.java:35-44`）。
- 6B `http.js:buildQuery` 把 `null/undefined/''` 都剔除（`http.js:69-82`）；`view-host.getBinding()` 无值时返回 `''`；因此前端“未选上下文”等价于不传，不会误触发 400。
- 结论：不传与空串语义在后端明确区分；前端实际不会发送空串。教师公告列表例外：`teacher/announcements.js:104-121` 在 bindingId 为空时根本不发请求。

### 3.4 unreadCount/asOf

- 后端 `StudentNoticeUnreadCountResult(unreadCount, at)`；`asOf` 即本次请求的服务端 UTC 时间（`StudentNoticeService.java:111-124`；`StudentNoticeResponseMapper.toUnreadCount`）。
- `unreadCount` 只统计当前可见、本人、未读；无未读为 0，仍为 200。
- 6B `refreshBadge/loadUnread` 只用 `data.unreadCount`；**未读取/展示 `asOf`**。该字段的使用方式待 6B/产品确认，不构成缺陷；重新拉列表或未读数可接受（D-09）。

### 3.5 单条已读 id/readAt/alreadyRead

- 后端响应 `StudentNoticeReadResponse(id, readAt, alreadyRead)`；`markRead` 首次返回 `alreadyRead=false`，已读可见返回原时间/true；0 行并发重读后返回当前读结果；仍不可见 404；异常并发 409（`StudentNoticeService.java:126-171`）。
- 6B `markRead` 只用 `data.readAt` 做 toast；**未使用 `alreadyRead`**（`student/notices.js:271-280`）。功能不受影响，但“已读”状态由刷新列表决定；“open 详情是否自动已读”仍是待确认项（handoff 10.7）。

### 3.6 全部已读 markedCount/unreadCount/operatedAt

- 后端 `StudentNoticeReadAllResult(markedCount, unreadCount, operatedAt)`；`markedCount` 是实际更新行数，`unreadCount` 是操作后当前读统计，`operatedAt` 是本请求时间（`StudentNoticeService.java:173-189`；Mapper `markAllRead`/`selectVisibleUnreadIdsForUpdate`）。
- `markedCount=0` 是成功；重复调用不会报错。
- 6B `markAll` 只用 `markedCount` 显示 toast，然后重新拉列表/未读；**未直接使用响应中的 `unreadCount/operatedAt`**（`student/notices.js:232-238`）。可接受但需确认。

### 3.7 授权不可用 503

- 授权端口缺失、异常、返回 null/不完整/跨绑定，或 fan-out 授权失败 → `AnnouncementAuthorizationUnavailableException` / `NoticeAuthorizationUnavailableException` → `code=503`（异常类；`AnnouncementExceptionHandler.java:25-34`）。
- 6B `http.js` 把 `code=503` 放入 `ApiError`；`ui-kit.stateError` 对 503 统一写“教学班授权服务暂不可用（第 2 组公开契约未接入）”（`ui-kit.js:153-172`）。
- **问题**：并非所有 503 都来自授权端口；网关/其他上游 503 没有 body.code 时也会被映射为 503，前端却一律当作“已知授权阻塞”。见 D-04。

### 3.8 重复发布、归档、已读幂等

| 操作 | 后端实际 | 前置条件/例外 | 6B 实际 |
| --- | --- | --- | --- |
| 重复发布 PUBLISHED | 200 + 当前对象；不重复 fan-out | 仍需授权可解析；授权缺失 503；ARCHIVED 409 | `changeState` 把成功/409 分开处理（`teacher/announcements.js:201-226`） |
| 重复归档 ARCHIVED | 200 + 当前对象 | 仍需授权可解析；授权缺失 503；DRAFT 409 | 同上 |
| 重复单条已读 | 已读且可见：原 `readAt` + `alreadyRead=true` | 已读但当前不可见：404 | `student/notices.js` 只 toast；不区分 alreadyRead |
| 重复全部已读 | 200 + `markedCount=0` + 当前 unreadCount | 授权缺失 503 | 显示“没有需要标记的通知”；可接受 |

## 4. C-1：bindingId / 课程 / 班级 / 学期来源核对

### 4.1 现有第 2 组接口（#26，草案）

| 能力 | 端点/方法 | 实际字段 | 证据 |
| --- | --- | --- | --- |
| 当前学期 | `GET /education/scope/term/current`（S1） | termId/name/code/startsOn/endsOn/current | `EduScopeController.java` `currentTerm` |
| 我可教/可学班级 | `GET /education/scope/my/classes`（S2） | classId/name/code/termId/termName/organizationId/status/classKind/teacher/student/teacherRole/canPublish/canGrade/canExport；**无 bindingId、无 courseName** | `EduScopeController.java` `myClasses`/`classMap` |
| 我在班级身份 | `GET /education/scope/classes/{classId}/me`（S3） | classId/teacher/student/teacherRole/canPublish/canGrade/canExport/validTo（student 的 validTo 为 null） | `EduScopeController.java` `myMembership`；`EduClassMembership` |
| 有效成员 | `GET /education/scope/classes/{classId}/members`（S4） | classId/memberId/userId/userName/nickName/joinedAt；仅管理员/本班教师 | `EduScopeController.java` `members` |
| 当前绑定 | `GET /education/scope/classes/{classId}/binding`（S5） | bindingId/classId/courseSpaceId/publishedAt；仅管理员/本班教师；未绑定 null | `EduScopeController.java` `binding`；`EduClassBindingRef` |
| 课程列表 | `GET /education/course/list`（K1） | `id`=courseSpaceId、name/title、className、semesterName、classCount/studentCount；**无 bindingId** | `EduCourseController.java`；`EduPortalMapper.xml` `courseColumns` |
| 课程详情 | `GET /education/course/{courseSpaceId}`（K2） | 课程 title/name；`classes[]` 含 `id`=bindingId、classId、name/className、termName、publishedAt、studentCount | `EduCourseController.java`；`EduPortalServiceImpl.courseDetail`；Mapper `selectCourseBindings` |

### 4.2 教师、学生可取得项

| 需要 | 教师 | 学生 |
| --- | --- | --- |
| bindingId | 先 S2 取 classId，再 S5（管理员/本班教师）取 bindingId | S5 不允许学生；可 K1 取 courseSpaceId，再 K2 `classes[].id` 取 bindingId（需课程 ACTIVE 且在班），多步 |
| courseName | K1/K2 仅本人创建课程（owner）或管理员；**非 owner 教师无 courseName**；S5 只有 courseSpaceId | K1/K2 对 ACTIVE 且在班课程可用；可取得 title/name |
| className | S2 `name`；S5 无 | S2 `name`；K1 `className`；K2 `classes[].className` |
| 学期 | S2 `termName`；S1 当前学期 | S2 `termName`；K1 `semesterName`；K2 `classes[].termName` |
| 有效范围 | S3 `validTo`（教师授权）；S2 班级 status/teacher 标志；S5 只返回当前有效绑定，无 endedAt | S2 `status`/`student` 标志；S3 `validTo` 为 null；S2 mapper 虽有 joinedAt/leftAt 但 `classMap` 未返回，S4 仅管理员/教师；无法取得学生有效日期区间 |

### 4.3 ID 区分

| 名称 | 实际含义 | 现有返回/使用 |
| --- | --- | --- |
| `classId` | `edu_teaching_class.id` | S2/S3/S5 的 classId；不能当 bindingId |
| `courseSpaceId` | `edu_course_space.id` | K1 的 `id`、K2 路径、S5 的 courseSpaceId；不能当 classId/bindingId |
| `bindingId` | `edu_course_class_binding.id` | S5 的 bindingId；K2 `classes[].id`；K7 删除绑定用它；不能当 classId/courseSpaceId |

### 4.4 结论

- 现有接口**没有** `/education/teacher/bindings` 或 `/education/student/bindings`；不得承诺新增或由 6A 代为实现。
- 教师：S2+S5 可得 bindingId/className/term/有效授权；**非课程 owner 教师拿不到 courseName**（K1/K2 教师权限只覆盖本人创建课程；S5 无课程名）。需要第 2 组补聚合字段或新端点。
- 学生：可通过 K1+K2 多步取得 bindingId/courseName/className/term；S5/S4 学生无权；**没有单一下拉数据源**；学生有效日期区间（joinedAt/leftAt）未提供。
- #26 是 `closed`/`draft=true`、base `4f7b801`、api-provided `v1.0-draft`；编译/构建通过但**运行联调未做**。不能写成契约已冻结或生产授权已接入。

## 5. 6B 失败态核对

| 检查项 | 6B 实际 | 问题 | 证据 |
| --- | --- | --- | --- |
| 404 与“Controller 未部署” | `http.js` 只有 `code===404` 的 `isNotFound`；无法区分路径未注册与对象不存在；`ui-kit` 通用提示把两者合并；`student/notices.js` 还保留“6A 交付 NoticeController 前”的旧提示 | 不能仅凭 404 断言 Controller 未部署；也不能把对象 404 说成部署问题 | `http.js:56-57`；`ui-kit.js:153-172`；`student/notices.js:191` |
| 503 归因 | `ui-kit` 把所有 `code=503` 固定成“教学班授权服务暂不可用（第 2 组公开契约未接入）” | 网关/其他 503 被误归为已知授权阻塞；“正常阻塞”不能覆盖所有 503 | `ui-kit.js:164-167`；`http.js:65-66` |
| 403/401/400/409/500/网络 | `http.js` 同时判 HTTP 与 body.code；401 清会话跳登录；403 权限；400 参数；409 冲突；500/503 `isServerSide`；网络 code 0/502/504 | 基本符合契约；真实 HTTP/网关未验证 | `http.js:11-22,180-215` |
| 失败不渲染 0/空列表 | `stateError` 独立失败态；`buildQuery`/`normalizeQuery` 保留空值语义；Mock 显式开启 | 方向正确；需用真实接口验证 | `ui-kit.js:150-180`；`http.js:69-99` |
| Mock 预览 | `mock-preview.html` 明确“不连后端、不校验登录”；显式 `?mock=1`；未覆盖路径报错不回落真实请求 | **只算页面自测，不算真实接口联调**；不得写成联调证据 | `mock-preview.html`；`docs/6-02-frontend-integration.md` §8 |
| 旧状态文案 | `notice.js:5`、`student/notices.js:5,191`、`teacher/announcements.js:206`、`docs/6-02-frontend-integration.md:6,19,29,93,121` 仍称学生通知未实现/当前不生成通知 | 与 main 已合并事实冲突；会误导联调 | 见行号 |

## 6. 过时状态更正

| 位置 | 旧表述 | 2026-11-03 实际 |
| --- | --- | --- |
| 6B 输入清单 line 4 | `B-*` 尚未实现 | #18 已合并；`StudentNoticeController`/Service/DTO/Mapper 均在 main |
| 6B 输入清单 line 178/181 | 学生通知“待合并”，合并前 404 | #18 已合并；当前接口已注册 |
| 6B 输入清单 line 276（D-3） | fan-out 作为独立批次后续实现 | #20 已实现：发布事务内 fan-out，唯一键防重 |
| 6B 输入清单 line 292（E） | 404 已区分 Controller 未部署 | `http.js` 无法区分；`ui-kit` 通用提示；旧提示仍在 |
| 6B 输入清单 line 293（E） | 503 统一按授权不可用 | 6B 代码把所有 503 归为授权；网关/其他 503 未区分 |
| `docs/6-02-frontend-integration.md:6,19,29,93,121` | 学生通知未实现/待补 | 已合并；需 6B 更新文档 |
| `notice.js:5-6`、`student/notices.js:5,191` | 后端无 StudentNoticeController | main 已有；需更新注释/提示 |
| `teacher/announcements.js:206` | “当前版本不生成站内通知” | #20 已生成 ANNOUNCEMENT fan-out；需更新文案 |
| 6B 分支 | 无 PR；落后 main 14 | 需要 6B 提供 PR/head 或同步 main 后复核 |

## 7. 差异与确认记录

| 编号 | 问题 | 影响 | 证据 | 建议负责方 | 处理建议 | 阻塞契约确认 |
| --- | --- | --- | --- | --- | --- | --- |
| D-01 | 6B 输入清单状态过期（B 待实现/待合并、fan-out 后续） | 6B 可能误判后端未就绪 | 输入 4/178/181/276；main #18/#20 | 6B | 更新清单/前端注释；以 main 为准 | 是 |
| D-02 | 6B `six@97ad8bd` 无 PR，落后 main 14 | 无 PR 评审、代码可能漂移 | `git` 引用；分支 log | 6B | 提供 PR/head；同步 main 后复核 | 是 |
| D-03 | 404 不能区分路径未注册与对象不存在 | 错误文案/联调判断失真 | `http.js:56-57`；`ui-kit.js:162-163`；`student/notices.js:191` | 6B | 用真实 HTTP/路由上下文区分；不要凭 404 断言 Controller 未部署 | 是 |
| D-04 | 所有 503 被标为授权服务不可用 | 网关/其他 503 被误归为已知阻塞 | `ui-kit.js:164-167`；`http.js:65-66` | 6B | 区分已知授权 503 与通用 503；保留 body.msg/路径 | 是 |
| D-05 | 交接文档逐端点失败表未列 503；A-3 写 binding 404、输入写 409，代码均无 | 错误码契约不完整 | `AnnouncementExceptionHandler`；`CourseAnnouncementService`；handoff §2.6、§4.1–§5.7 | 6A 文档 | **本 PR 已修正**：按实际异常路径补逐端点 503；A-3 明确无 404/409 | 否（文档已修，待 6B 确认） |
| D-06 | A-3 创建无幂等键；网络重试可能重复草稿 | 重复草稿 | `TeacherAnnouncementController.create`；`CourseAnnouncementService.createDraft`；handoff §9.4 | 6A/6B/产品 | 前端先查列表；后续决定 Idempotency-Key/409 | 是（幂等） |
| D-07 | 发布/归档“重复 200”仍需授权可解析；授权缺失 503 | 6B 可能把 503 当幂等失败 | `CourseAnnouncementService.publish/archive` | 6A/6B | **本 PR 已修正 6A 文档**：handoff §4.3/§4.4 写明重复 200 仍以授权可解析为前提、授权缺失 503；6B 仍需按 503 失败态处理 | 部分（6B 待改） |
| D-08 | 重复单条已读仅在通知仍可见时幂等；不可见返回 404 | 输入“总是返回同一 readAt”不成立 | `StudentNoticeService.markRead`；`StudentNoticeServiceTest` | 6A/6B | **本 PR 已修正 6A 文档**：handoff §3.4/§5.6/§9.3 写明可见性条件、404 与异常并发 409；前端 404 移除 | 否（文档已修，待 6B 确认） |
| D-09 | 6B 未消费 `asOf`/`alreadyRead`/`unreadCount`/`operatedAt` | 使用方式待确认；未消费不构成缺陷 | `student/notices.js:163-173,232-238,271-280` | 6B/产品 | 记录是否需要展示/使用；重新拉列表或未读数可接受；不强制展示全部字段 | 否 |
| D-10 | C-1 无 binding 汇总端点；教师非 owner 无 courseName；学生 binding 需 K1+K2；学生有效日期区间缺失 | 前端仍需手工填 bindingId；课程名不完整 | #26 S2/S5/K1/K2；`EduClassRef`/`EduCourseRef` | 第 2 组/负责人 | 由 2 组决定聚合端点/字段；6A 不实现 | 是 |
| D-11 | #26 为 closed/draft、base 4f7b801、api-provided v1.0-draft、运行未联调 | 不能当契约冻结/生产授权已接入 | GitHub PR #26；docs/group2/README | 第 2 组/负责人 | 冻结/联调后再引用；授权仍缺失 | 是 |
| D-12 | 6B 注释/文档仍称学生通知未实现、fan-out 未实现 | 误导联调 | `notice.js:5-6`；`student/notices.js:5,191`；`teacher/announcements.js:206`；integration doc | 6B | 更新注释/文档；不改后端 | 否 |
| D-13 | bindingId/classId/courseSpaceId 易混用 | 传错 ID → 403/404/空数据 | #26 docs §3.6/§4；`teacher/announcements.js` 手工 bindingId | 6B/第 2 组 | 下拉只存 bindingId；展示名与 ID 分离；D-10 解决来源 | 是 |
| D-14 | 公告 404 文案 `公告不存在或不可访问` 与 handoff 的“不可查看”不一致 | 前端若硬编码文案会不一致 | `AnnouncementNotFoundException`；handoff 全文 | 6A 文档/6B | **本 PR 已修正**：handoff 统一为代码 msg“公告不存在或不可访问”；前端展示服务端 msg，不硬编码 | 否 |

## 8. 三份清单

### 8.1 需要 6B 确认或调整

- D-01/D-02：更新输入清单状态；提供 6-02 分支/PR/head；同步 main 后复核。
- D-03/D-04：修正 404/503 失败态，不要把 404 一律当 Controller 未部署、不要把 503 一律当授权阻塞。
- D-09：记录 `asOf`、`alreadyRead`、`unreadCount`、`operatedAt` 的使用方式；当前未消费不构成缺陷，不强制展示全部字段。
- D-12：更新 `notice.js`、`student/notices.js`、`teacher/announcements.js` 的旧状态文案。
- D-13：bindingId/classId/courseSpaceId 不得混用；下拉只保存 bindingId。
- 用真实接口验证 13 个请求、字段、空值、分页、错误码；Mock 只算页面自测。

### 8.2 本轮 #31 文档收口的处理结果

- **未发现会改变 13 个接口形状的代码级阻断缺陷。**
- 已在本 PR 修正 handoff：
  - A-3 创建草稿：删除不存在的 404 示例和“`bindingId` 不存在/已结束 404”行，明确当前无 404/409；非法 `bindingId` 为 400，无权限为 403，授权不可用为 503。
  - 逐端点失败表：A-1–A-8、B-1–B-5 按实际异常路径补 503；发布 A-5 另列 fan-out 学生授权 503，并说明发布事务回滚。
  - B-4 单条已读：明确只有通知仍可见时幂等；已读但当前不可见返回 404；异常并发返回 409。
  - 404 文案：全文统一为后端 msg“公告不存在或不可访问”“通知不存在或不可访问”。
  - 删除“前端已按现清单对齐”的过早表述，改为已做源码核对、6B 未确认、真实联调未完成。
- 仍待产品/技术决定：A-3 无幂等键/无 409，是否补 Idempotency-Key 或维持“先查列表”策略（`TeacherAnnouncementController.create`；`CourseAnnouncementService.createDraft`）。
- 仍待 6B 确认：`asOf`/`alreadyRead`/`unreadCount`/`operatedAt` 的使用方式；不强制全部展示，重新拉列表/未读数可接受。
- B-4 若产品要求“永远返回原 readAt”，需另议；当前代码以可见性安全优先，已在文档写明。

### 8.3 需要第 1/2 组或负责人提供

- 第 1 组：`education:announcement:*` 的 `sys_menu`/角色授权；真实登录、角色拦截、审计；否则公告接口 403 阻塞。
- 第 2 组：`StudentAnnouncementAuthorizationPort`、`TeachingClassAuthorizationPort` 的生产实现/公开契约；否则公告/通知/ fan-out 503。
- 第 2 组/负责人：C-1 binding 汇总方案；教师非 owner courseName；学生有效日期区间；#26 冻结/联调。
- 产品：公告/通知 404 文案、打开通知是否自动已读、`asOf`/`alreadyRead`/剩余量的使用方式、创建幂等策略。

## 9. 检查记录

- 后端代码静态核对：Controller/DTO/Service/Mapper XML/异常处理/授权端口/fan-out。
- 6B 分支 `six@97ad8bd` 只读核对：`frontend/src/api/education/http.js`、`announcement.js`、`notice.js`；`frontend/src/views/education/teacher/announcements.js`、`student/announcements.js`、`student/notices.js`、`ui-kit.js`、`view-host.js`、`frontend/mock-preview.html`、`docs/6-02-frontend-integration.md`。
- 第 2 组 #26：`origin/pr26@3e74e875` 的 `backend/ruoyi-admin/.../education/EduScopeController.java`、`EduCourseController.java`、`EduTeachingClassController.java`，以及 `backend/ruoyi-education/.../course/api/*` 与 `docs/group2/*`。
- 引用说明：`docs/6-02-6B-http-interface-request.md`、`docs/6-02-frontend-integration.md` 只存在于 6B 分支；`docs/group2/api-provided.md` 只存在于 #26；main 中不存在这些文件，引用时已注明来源分支。
- #31 文档收口静态检查：`git diff --check` 通过；Markdown 表格列数通过；handoff 13 个端点均按实际异常路径列出 503；A-3 不再包含 404/409；404 文案 grep 无旧串。Java 行号引用 7 处均在文件范围内（上轮核对，本轮未改代码）。
- 未运行 Maven/测试；未执行 SQL/数据库；未做真实 HTTP/登录/权限/审计/MySQL 联调；Mock 不计入真实联调。

## 10. 停止点

- 本轮只新增/修订文档，不改业务代码、前端、SQL、权限或生产适配器。
- 不修改 #23/#24/#29 已合并/在途分支；本轮在 #31 既有分支修订文档，不新建实现分支。
- 等待独立复审、6B 回应和第 2 组契约确认。
