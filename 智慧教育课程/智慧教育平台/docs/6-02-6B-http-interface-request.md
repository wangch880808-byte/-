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

无请求体。权限 `education:announcement:publish`。仅 `DRAFT → PUBLISHED` 合法。
前端已做二次确认，`409` 时提示"状态已变化，请刷新后重试"并自动刷新列表。

### A-6 归档公告

```
POST /education/teacher/announcements/{announcementId}/archive
```

无请求体。权限 `education:announcement:archive`。仅 `PUBLISHED → ARCHIVED` 合法，不可逆。

### A-7 学生公告列表

```
GET /education/student/announcements?bindingId={bindingId}&pageNum=1&pageSize=10
```

| 项 | 内容 |
| --- | --- |
| 权限 | `hasRole('student') and lacksRole('admin') and lacksRole('teacher')` |
| 参数 | `bindingId` 可空（空 = 本人全部班级）；`pageNum`/`pageSize` 规则同上 |

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

响应 `data` = 列表行字段 + `body`。失败：`403`（非本人班级）、`404`。

---

## B. 待实现接口（学生通知 5 个，6A 尚未提交 Controller）

前端代码 `src/api/education/notice.js` 已按下列契约写完，Controller 一上线即可联调，无需改前端。
当前状态：请求返回 404，页面显示"通知读取失败 / 接口未就绪"，**没有本地假数据兜底**。

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

## D. 需要 6A 确认的 4 件事

1. **菜单权限种子**：`education:announcement:add|edit|publish|archive|list` 目前 `sql/` 里没有任何 `sys_menu` 记录，教师角色也未分配。代码已就绪，但教师端会全部 403。这块属 1 组，能否请你确认是否已有人跟进？
2. **授权端口生产实现**：`TeachingClassAuthorizationPort` / `StudentAnnouncementAuthorizationPort` 在 `main` 上仍只有测试 Fake，真实请求会走 `AnnouncementAuthorizationUnavailableException`（503）。2-01 未交付前，接口是否打算先给一个只读实现？
3. **发布是否 fan-out 通知**：教师发布公告后，是否要为每个学生生成 `kind=ANNOUNCEMENT` 的 `edu_user_notice` 记录？如果不生成，B-1 里永远不会有公告类型通知，学生只能从"课程公告"页签看。
4. **A-1 列表的 `visibilityState` 语义**：`DRAFT` / `NOT_YET_OPEN` / `OPEN` / `CLOSED` / `ARCHIVED` 是服务端按当前时刻算的，我按它渲染状态徽标。如果后续要改成前端计算，请提前说明。

---

## E. 我已按这些约定做完前端适配

- 学生公告页**不展示** `status`/`visibilityState`（因为 A-7 不返回），只显示开放/关闭时间。
- 教师页状态徽标读 `visibilityState`，缺失时回退 `status`。
- `closesAt=null` 显示"不关闭"。
- 所有 ID 用 `esc()` 输出到 `data-id`，不做数值转换。
- 失败态带上 `method + url` 与错误码，方便你按日志定位。
- 404 与"对象不存在"文案已区分：接口未注册时提示"后端尚未部署对应 Controller"。

---

## F. 我的自测方式（不依赖你的服务）

后端接口未就绪时，我用 `frontend/mock-preview.html` 做页面自测：内置与上述字段**完全一致**的 Mock 数据（含 400/403/404/409 分支），无需登录、无需后端。
这只用于我这边页面验证，不写进交付逻辑，也不会在真实页面里静默兜底假数据。
