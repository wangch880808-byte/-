# 6-02 前端（6B）HTTP 接口清单与联调需求

> 用途：这是 **6B 前端页面实际发出的全部 HTTP 请求**，不是草案。6A 可直接据此实现/核对接口，无需再猜前端传什么、读什么字段。
> 编号说明：`A-*` 教师/学生公告 8 个、`B-*` 学生通知 5 个，**均已合并到 `main`**，我按 `main@f21f51d` 的 Controller 与 DTO 逐字段核对；`C-*` 为外部阻塞项。
> 对应页面：教师「班级消息 / 公告」、学生「学习工作台 → 学习通知 / 课程公告」。
> 最后更新：2026-11-03（对应 `main@f21f51d`，`six` 分支已合并该基线）

### 本轮核对状态（务必区分两件事）

| 项目 | 状态 |
| --- | --- |
| 源码核对 | **一致**：13 个接口的路径、参数、响应字段与 `main@f21f51d` 实现对应 |
| 真实联调 | **未通过**：本机无 MySQL/Redis/JDK 运行环境，且生产授权契约、`bindingId` 来源仍未接入 |
| 外部阻塞 | 2 项：`bindingId` 来源（2-04）、生产授权契约（2 组） |

本文档中"已合并"仅表示代码在 `main` 上可读、字段可核对，**不代表接口已在运行环境验证过**。

---

## 0. 通用约定（前端已按此实现，请勿单方面改动）

| 项目 | 约定 | 前端处理位置 |
| --- | --- | --- |
| 认证 | `Authorization: Bearer <token>`，取自 `sessionStorage['zhijiao.login.token']` | `src/api/education/http.js` |
| 请求体 | `Content-Type: application/json`，字段名小驼峰 | 同上 |
| 成功判定 | **HTTP 200 且 `code === 200`** | 同上 |
| 业务失败 | **HTTP 200 + `body.code` ∈ {400,401,403,404,409,500,503}**，`body.msg` 直接作为用户可读文案 | 同上 |
| 401 处理 | 清会话 → 跳 `/login.html?logout=expired` | 同上 |
| ID | 一律**字符串**传递与返回，前端不做 `Number()` 转换（避免 19 位雪花 ID 精度丢失） | 全部视图 |
| 时间 | 输出 `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`（UTC 毫秒）；请求入参为 RFC3339 带偏移量，必须毫秒精度 | `TeacherAnnouncementResponseMapper` / `TeacherAnnouncementRequestParser` |
| 分页 | 请求 `pageNum`（默认 1）、`pageSize`（默认 10，上限 100）；响应 `total` + `rows` | 全部列表页 |
| 空值 | 空列表 `total=0, rows=[]` 且 `code=200`；**前端不会把失败渲染成 0 或空列表** | `ui-kit.js` |

前端已按错误码区分文案：`400` 参数问题、`403` 权限/上下文、`404` 对象不存在或当前不可见、`409` 状态冲突、`503` 服务暂不可用、`0` 网络异常。

两条与 6A 对齐过的展示规则（2026-11-03）：

1. **404 不归因为"Controller 未部署"**。接口已全部合并，页面统一提示"对象不存在或当前不可见：可能不属于当前账号、尚未开放，或你已不在对应教学班"。
2. **503 按证据分级，不凭"授权"字样断定未接入**。`msg` 含"授权"（如"学生教学班授权查询失败"）→ 提示"教学班授权服务暂不可用"，原因以服务端返回为准（查询失败、结果不完整等），并**原样保留服务端 `msg`**；`msg` 明确含"尚未接入"时才额外说明生产契约未落地；无授权证据 → 按通用服务不可用处理。
3. 两种情况下都**原样保留**服务端 `msg`、业务 `code` 与实际请求 `method + url`，便于按日志定位。

---

## A. 已合并接口（教师公告 6 个 + 学生公告 2 个）

### A-1 教师公告列表

```
GET /education/teacher/announcements?bindingId={bindingId}&status={DRAFT|PUBLISHED|ARCHIVED}&pageNum=1&pageSize=10
```

| 项 | 内容 |
| --- | --- |
| 权限 | `education:announcement:list` |
| 必填参数 | `bindingId`（缺失 → 400） |
| 可选参数 | `status`、`pageNum`、`pageSize` |
| 前端调用点 | 教师页进入、切状态筛选、翻页、新建/发布/归档后刷新 |

响应：`TableDataInfo`，`total` + `rows[]`，每行**不含正文**：

```json
{
  "total": 2,
  "rows": [
    {
      "id": "820000000000000001",
      "bindingId": "910000000001",
      "title": "第 5 周随堂测验安排",
      "status": "PUBLISHED",
      "visibilityState": "OPEN",
      "opensAt": "2026-10-20T01:00:00.000Z",
      "closesAt": null,
      "createdByUserId": "102",
      "publishedByUserId": "102",
      "createdAt": "2026-10-19T08:00:00.000Z"
    }
  ],
  "code": 200,
  "msg": "查询成功"
}
```

失败：`400`（bindingId 缺失/非正整数、status 非法、分页越界）、`403`（无权限或无该教学班权限）、`503`（授权端口不可用）。

### A-2 教师公告详情

```
GET /education/teacher/announcements/{announcementId}
```

响应 `data` 字段 = 列表行字段 + `body`（正文）。失败：`400`、`403`、`404`（不存在或不可访问）。

### A-3 新建公告草稿

```
POST /education/teacher/announcements
```

```json
{
  "bindingId": "910000000001",
  "title": "第 5 周随堂测验安排",
  "body": "正文…",
  "opensAt": "2026-10-20T01:00:00.000Z",
  "closesAt": null
}
```

| 项 | 内容 |
| --- | --- |
| 权限 | `education:announcement:add` |
| 必填 | `bindingId`、`title`、`body` |
| 可空 | `opensAt`（null/省略 = 取发布时刻）、`closesAt`（null = 不关闭） |
| 前端已做的前置校验 | 标题 1–200 字；正文 ≤20000 字且 UTF-8 ≤65535 字节；`closesAt` 必须晚于 `opensAt`；提交中禁用按钮防重复 |

响应：`data` 为公告详情（状态必为 `DRAFT`）。失败：`400`、`403`、`503`。
（创建草稿是新建对象，不存在状态冲突，不会返回 `409`。）

### A-4 编辑公告草稿

```
PUT /education/teacher/announcements/{announcementId}
```

请求体：`title`、`body`、`opensAt`、`closesAt`（**不含 `bindingId`**）。权限 `education:announcement:edit`。
失败：`400`、`403`、`404`、`409`（非草稿或并发更新）、`503`。

### A-5 发布公告

```
POST /education/teacher/announcements/{announcementId}/publish
```

无请求体。权限 `education:announcement:publish`。
`DRAFT → PUBLISHED` 为正常流转；**对已 `PUBLISHED` 的公告重复发布是幂等成功**，返回 `200` 与当前对象，不是 `409`。
只有非法转换（如 `ARCHIVED` 再发布）才返回 `409`。前端已做二次确认，`409` 时提示"状态已变化，请刷新后重试"并自动刷新列表。

### A-6 归档公告

```
POST /education/teacher/announcements/{announcementId}/archive
```

无请求体。权限 `education:announcement:archive`。
`PUBLISHED → ARCHIVED` 为正常流转，归档不可逆；**对已 `ARCHIVED` 的公告重复归档是幂等成功**（`200` + 当前对象），
草稿归档才返回 `409`。

### A-7 学生公告列表

```
GET /education/student/announcements?bindingId={bindingId}&pageNum=1&pageSize=10
```

| 项 | 内容 |
| --- | --- |
| 权限 | `hasRole('student') and lacksRole('admin') and lacksRole('teacher')` |
| 参数 | `bindingId` **只有完全不传**才表示本人全部有效班级；传空串 `""` 或纯空白返回 `400`；`pageNum`/`pageSize` 规则同上 |

前端 `http.js` 在参数为 `null/undefined/''` 时直接不拼该参数，因此"未选上下文"等价于"不传"，不会误触发 `400`。

响应行字段（注意：**与教师端不同，没有 `status`、`visibilityState`、`createdByUserId`**，前端页面正是按此渲染的）：

```json
{
  "total": 1,
  "rows": [
    {
      "id": "820000000000000001",
      "bindingId": "910000000001",
      "title": "第 5 周随堂测验安排",
      "opensAt": "2026-10-20T01:00:00.000Z",
      "closesAt": null,
      "createdAt": "2026-10-19T08:00:00.000Z"
    }
  ],
  "code": 200,
  "msg": "查询成功"
}
```

只返回当前学生可见（`OPEN`）的公告，服务端已过滤，前端不再二次判断。

### A-8 学生公告详情

```
GET /education/student/announcements/{announcementId}
```

响应 `data` = 列表行字段 + `body`。
失败语义：**外班、已退班、未开放、已关闭、已归档、不存在统一返回 `404`**（前端不区分，统一提示"公告不存在或当前不可访问"）；
**只有角色不匹配才返回 `403`**（如教师账号调学生接口）。

---

## B. 学生通知 5 个接口（已合并到 main@f21f51d）

**状态更新（2026-11-03）**：PR #18 已合并，`StudentNoticeController` 已在 `main` 上，5 个接口全部可用，
字段与下列契约一致，`frontend/src/api/education/notice.js` 无需改动。

前端按 `main@f21f51d` 的 `StudentNoticeResponseMapper` 核对后的字段事实：

| 接口 | 响应 `data` 字段 |
| --- | --- |
| B-1 列表 | `id, kind, bindingId, sourceId, title, visibleAt, readAt, isRead, createdAt`（**不返回 `body`**，通知只是提醒，正文由来源页面提供） |
| B-2 详情 | 同上（与列表同一 DTO） |
| B-3 未读数 | `unreadCount, asOf` |
| B-4 单条已读 | `id, readAt, alreadyRead` |
| B-5 全部已读 | `markedCount, unreadCount, operatedAt` |

**B-4 的可见性语义（2026-11-03 确认）**：单条已读重复请求**只有在通知仍可见时**才返回原 `readAt`
（`alreadyRead=true`，`readAt` 不变）；若已退班或来源内容不可见，服务端返回 `404`，
不会复用旧 `readAt`。前端据此在 404 时提示"该通知已不可见"并刷新列表与未读数。

### B-1 通知列表

```
GET /education/student/notices?kind={kind}&bindingId={bindingId}&unreadOnly=true&pageNum=1&pageSize=10
```

响应行字段：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 否 | 通知 ID |
| `kind` | string | 否 | `ANNOUNCEMENT` / `PREVIEW` / `ACTIVITY` / `ASSIGNMENT` / `GRADE` |
| `bindingId` | string | 是 | 所属课程—教学班 |
| `sourceId` | string | 否 | 来源业务对象 ID（`ANNOUNCEMENT` 时即公告 ID） |
| `title` | string | 否 | 标题快照 |
| `visibleAt` | string | 否 | 可见时间 |
| `readAt` | string | 是 | 首次已读时间，未读为 `null` |
| `isRead` | boolean | 否 | 是否已读 |
| `createdAt` | string | 否 | 创建时间 |

失败：`400`（kind 不在枚举、分页越界）、`403`（非学生 / bindingId 非本人班级）、`500`。

### B-2 通知详情

```
GET /education/student/notices/{noticeId}
```

响应 `data` = 列表行字段（**不含正文**）。他人通知、未到 `visibleAt`、已退班统一 `404`。

### B-3 未读数

```
GET /education/student/notices/unread-count?bindingId={bindingId}
```

```json
{ "code": 200, "msg": "操作成功", "data": { "unreadCount": 3, "asOf": "2026-10-24T01:05:00.000Z" } }
```

`bindingId` 可空 = 本人全部。无未读时仍为成功、`unreadCount=0`。
前端用于顶栏角标与列表页头部；**失败时角标显示为空白，不显示 0**。

### B-4 单条标记已读

```
POST /education/student/notices/{noticeId}/read
```

请求体 `{}`。响应：

```json
{ "code": 200, "msg": "操作成功", "data": { "id": "30001", "readAt": "2026-10-24T01:06:00.000Z", "alreadyRead": false } }
```

幂等：**重复调用在通知当前仍可见时**返回同一 `readAt`，`alreadyRead=true`；
若通知已不可见（退班、来源内容下线等），返回 `404`，不复用旧 `readAt`；他人通知同样 `404`。
前端标记成功后会重新拉取列表与未读数（不做本地减一）。

### B-5 全部标记已读

```
PUT /education/student/notices/read-all
```

响应 `data`：`{ "markedCount": 3, "unreadCount": 0, "operatedAt": "…" }`。
`markedCount=0` 是成功，前端提示"没有需要标记的通知"，不报错。无 `bindingId` 参数，作用于本人全部。

---

## C. 外部阻塞（不由 6A 解决，前端不硬编码、不绕过权限）

### C-1 课程—教学班上下文（`bindingId` 来源，属 2-04）

**现状**：教师页必须手工填 `bindingId`，演示库用 `910000000001` / `910000000002`；学生页从 `sessionStorage` 读取，没有真实来源。
**影响**：真实用户无法自己选课；联调时容易填错上下文导致误判 403。
**处理约定（2026-11-03）**：记为**外部阻塞**，等第 2 组提供公开契约后再接。前端**不硬编码**教学班 ID、
**不绕过**权限校验、也不在页面里内置任何"免校验"入口。

阻塞解除需要的契约（具体归属 2 组，不由 6A 代实现）：

```
GET /education/teacher/bindings            → [{ bindingId, courseName, className, term }]
GET /education/student/bindings            → [{ bindingId, courseName, className, term }]
```

只要 `bindingId + 展示名` 就够，前端用它渲染下拉框替代手工输入框。契约到位前，前端保持手工输入且**不内置默认值**。

### C-2 生产授权契约（属第 2 组）

`TeachingClassAuthorizationPort` / `StudentAnnouncementAuthorizationPort` 及通知侧授权查询目前在 `main` 上仍只有测试 Fake，
真实请求按安全设计返回 `503`（`msg` 含"授权"字样）。前端不实现替代逻辑、不读第 2 组私有表、不默认放行。
阻塞期间页面按"服务暂不可用"展示，并保留服务端 `msg` 与请求路径。

---

## D. 4 个问题的答复（2026-11-02 确认，2026-11-03 按合并状态更新）

| # | 问题 | 结论 | 前端处理 |
| --- | --- | --- | --- |
| 1 | 菜单权限种子 | 属第 1 组/负责人统一处理，6A 不改 `sys_menu` 与角色初始化 SQL | 保留 403 失败态与提示，等权限落地后再联调 |
| 2 | 授权端口生产实现 | 第 2 组尚无公开契约；不读私有表做临时实现、不默认放行，接入前按安全设计返回 `503` | 503 按证据分级展示：含"授权"→"教学班授权服务暂不可用"（保留服务端 `msg`）；明确"尚未接入"→说明契约未落地；无授权证据→通用服务不可用。不当作页面缺陷，也不冒充实为空列表 |
| 3 | 公告通知 fan-out | **已合并**（PR #20，`AnnouncementNoticeFanoutService`）：发布时为班内有效学生生成 `ANNOUNCEMENT` 通知，唯一约束防重 | 通知列表已能出现公告类型；点"打开公告"用 `sourceId` 跳到课程公告页签 |
| 4 | `visibilityState` 语义 | 继续服务端按当前时刻计算，教师端固定返回 5 种状态 | 前端只认 `visibilityState`；缺失按"未知状态"展示并记录错误，**不再用 `status` 推算** |

两条沿用现状的约定：学生通知在生产授权不可用时同样可能返回 `503`，前端按失败态处理，不显示成空列表或未读数 0。

---

## E. 我已按这些约定做完前端适配

- 学生公告页**不展示** `status`/`visibilityState`（因为 A-7 不返回），只显示开放/关闭时间。
- 教师页状态徽标**只读 `visibilityState`**；缺失时显示"未知状态"并 `console.error` 记录，
  不用 `status` 推算（`PUBLISHED` 无法说明当前是否在开放窗口内）。
- 重复发布、重复归档按幂等成功处理，不弹错误；只有非法状态转换才提示"状态已变化"。
- `closesAt=null` 显示"不关闭"。
- 所有 ID 用 `esc()` 输出到 `data-id`，不做数值转换。
- 失败态带上 `method + url`、业务码与服务端 `msg`，方便你按日志定位。
- **404 不再提示"Controller 未部署"**：统一为"对象不存在或当前不可见：可能不属于当前账号、尚未开放，或你已不在对应教学班"。
- **503 按证据分级**：`msg` 含"授权"→"教学班授权服务暂不可用"（保留服务端 `msg`）；`msg` 明确含"尚未接入"才说明契约未落地；否则按通用服务不可用。始终保留原始 `msg` 与请求路径。
- 单条已读返回 `404` 时提示"该通知已不可见"并刷新列表与未读数，不把它当成接口缺失。

### E-1 字段使用方式（回应 `asOf` / `alreadyRead` / `operatedAt`）

这三项前端**不做展示**，也不做推算，只按下述方式消费：

| 字段 | 是否展示 | 使用方式 |
| --- | --- | --- |
| `asOf`（未读数快照时间） | 不展示 | 仅用于确认未读数是某一时刻快照，不做本地"减一" |
| `alreadyRead`（单条已读幂等标记） | 不展示 | 不参与提示文案分支；仅当前仍可见时重复请求才返回 `alreadyRead=true`；标记成功后统一重拉列表与未读数 |
| `operatedAt`（全部已读操作时间） | 不展示 | 同上；只用 `markedCount` 决定提示"已标记 N 条"或"没有需要标记的通知" |
| `unreadCount`（全部已读后） | 不展示 | 以重拉的 `GET /unread-count` 结果为准，避免与列表不一致 |

即：**成功后重新拉列表和未读数**，不依赖上述字段在前端做增量更新或状态推断。`markedCount` 是唯一用于文案的返回值。

---

## F. 我的自测方式（不替代真实联调）

即使接口已合并，我本机仍无 MySQL/Redis/JDK 运行环境，因此用 `frontend/mock-preview.html` 做离线页面自测：
预览脚本位于 `frontend/preview/education-mock.js`，只由预览页引用，不进入打包范围；
开关、演示教学班和预览数据只存在当前页面内存，不读写真实会话。
内置与上述字段**完全一致**的 Mock 数据（含 400/403/404/409/503 分支），无需登录、无需后端。

这只是我这边页面验证的手段，**不写进交付逻辑**，也不会在真实页面里静默兜底假数据；
它证明的是"页面能正确处理这些字段与错误码"，**不等于接口已在运行环境联调通过**。
