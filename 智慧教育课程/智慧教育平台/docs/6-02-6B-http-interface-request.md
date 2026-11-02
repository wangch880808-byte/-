# 6-02 前端（6B）HTTP 接口清单与联调需求

> 用途：这是 **6B 前端页面实际发出的全部 HTTP 请求**，不是草案。6A 可直接据此实现/核对接口，无需再猜前端传什么、读什么字段。
> 编号说明：`A-*` 已在 `main` 合并，我按代码逐字段核对过；`B-*` 尚未实现；`C-*` 缺失但前端需要。
> 对应页面：教师「班级消息 / 公告」、学生「学习工作台 → 学习通知 / 课程公告」。
> 最后更新：2026-10-28（对应 `main` 提交 `4f7b801`）

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

前端已按错误码区分文案：`400` 参数问题、`403` 权限/上下文、`404` 不存在或路径未注册、`409` 状态冲突、`503` 授权服务不可用、`0` 网络异常。

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

响应：`data` 为公告详情（状态必为 `DRAFT`）。失败：`400`、`403`、`409`、`503`。

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

## B. 学生通知 5 个接口（已在 PR #18 实现，待合并）

**状态更新（2026-11-02）**：PR #18 已实现这 5 个接口，字段与下列契约一致，前端 `notice.js` 无需改动。
合并前请求仍返回 404，页面按"接口未就绪"提示，**没有本地假数据兜底**；合并后即可直接联调。

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

幂等：重复调用返回同一 `readAt`，`alreadyRead=true`；他人通知 `404`。
前端标记成功后会重新拉取列表与未读数（不做本地减一）。

### B-5 全部标记已读

```
PUT /education/student/notices/read-all
```

响应 `data`：`{ "markedCount": 3, "unreadCount": 0, "operatedAt": "…" }`。
`markedCount=0` 是成功，前端提示"没有需要标记的通知"，不报错。无 `bindingId` 参数，作用于本人全部。

---

## C. 缺失接口（阻塞我这边页面体验）

### C-1 课程—教学班上下文（bindingId 来源，属 2-04）

**现状**：教师页必须手工填 `bindingId`，演示库用 `910000000001` / `910000000002`；学生页从 `sessionStorage` 读取，没有来源。
**影响**：真实用户无法自己选课；联调时容易填错上下文导致误判 403。

需要的最小契约（具体归 2 组，请 6A 转达或确认由谁给）：

```
GET /education/teacher/bindings            → [{ bindingId, courseName, className, term }]
GET /education/student/bindings            → [{ bindingId, courseName, className, term }]
```

只要 `bindingId + 展示名` 就够，前端用它渲染下拉框替代手工输入框。

---

## D. 4 个问题的答复（2026-11-02 已确认）

| # | 问题 | 结论 | 前端处理 |
| --- | --- | --- | --- |
| 1 | 菜单权限种子 | 属第 1 组/负责人统一处理，6A 不改 `sys_menu` 与角色初始化 SQL | 保留 403 失败态与提示，等权限落地后再联调 |
| 2 | 授权端口生产实现 | 第 2 组尚无公开契约；不读私有表做临时实现、不默认放行，接入前按安全设计返回 `503` | 503 按"服务不可用"展示，不再当作功能缺陷，也不冒充实为空列表 |
| 3 | 公告通知 fan-out | 计划在教师发布时为班内有效学生生成 `ANNOUNCEMENT` 通知，唯一约束防重；作为独立批次后续实现 | 学生当前仍可从"课程公告"页签查看，不依赖通知存在；通知列表把 `ANNOUNCEMENT` 与公告详情打通 |
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
- 失败态带上 `method + url` 与错误码，方便你按日志定位。
- 404 与"对象不存在"文案已区分：接口未注册时提示"后端尚未部署对应 Controller"。
- `503`（授权服务不可用）独立文案，不显示成空列表、不显示未读数 0。

---

## F. 我的自测方式（不依赖你的服务）

后端接口未就绪时，我用 `frontend/mock-preview.html` 做页面自测：内置与上述字段**完全一致**的 Mock 数据（含 400/403/404/409 分支），无需登录、无需后端。
这只用于我这边页面验证，不写进交付逻辑，也不会在真实页面里静默兜底假数据。
