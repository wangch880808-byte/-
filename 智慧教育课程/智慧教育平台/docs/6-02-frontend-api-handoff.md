# 6-02 前端接口交接文档（M0）

> 版本：0.1｜日期：2026-10-24｜读者：第六组前端 6B
>
> 依据：`docs/6-02-announcement-notice-contract.md` 的已评审业务契约。
>
> 范围：教师公告、学生本班公告、学生本人通知。
>
> 状态说明：
>
> - `[已确认]`：业务范围、角色、可见性规则、字段语义、已读规则。
> - `[草案]`：接口路径、字段名、分页默认值、错误码数字、页面路由。可先按本文联调，最终以联调冻结版为准。
> - 本文不包含上游内部通知接收接口、6-01 总览、6-05 校园资讯/留言/系统通知模板、AI 功能。

---

## 1. 文档范围

### 1.1 包含的接口

1. 教师公告草稿创建。
2. 教师公告草稿编辑。
3. 教师公告发布。
4. 教师公告归档。
5. 教师公告列表。
6. 教师公告详情。
7. 学生本班公告列表。
8. 学生本班公告详情。
9. 学生本人通知列表。
10. 学生本人通知详情。
11. 学生本人未读数。
12. 学生本人单条通知标记已读。
13. 学生本人全部通知标记已读。

当前状态：教师公告、学生公告只读列表/详情、学生通知列表/详情/未读数/单条已读/全部已读的后端领域对象、Controller/DTO/Service/Mapper XML 与隔离测试已实现；生产学生授权适配器仍缺失，公告发布 fan-out 和上游 PREVIEW/ACTIVITY/ASSIGNMENT/GRADE 事件接收未实现。本文档确认前端调用契约，不代表接口已可生产联调；真实 MySQL、HTTP、登录、角色拦截和审计联调仍未完成。

### 1.2 不包含

- 上游业务系统向本模块投递通知的内部接口；前端不得调用。
- 教师/学生/管理员三端总览（6-01）。
- 校园资讯、留言互动、系统通知模板、通知中心管理页（6-05）。
- AI 公告生成、AI 摘要、AI 推荐。
- 公告物理删除、通知删除、管理员公告/通知业务接口。

---

## 2. 公共约定

### 2.1 公共请求地址 `[草案]`

本地联调地址：

```text
http://127.0.0.1:8080
```

本文所有路径都相对于该地址。测试/生产地址由部署环境提供，前端使用环境配置，不要写死在业务代码中。

示例：

```text
http://127.0.0.1:8080/education/teacher/announcements
```

### 2.2 登录与 Token `[已确认]`

- 所有 6-02 接口都需要登录。
- 登录沿用现有若依登录页和 `/login`，不在本文重复登录接口。
- 每个请求都必须带：

```http
Authorization: Bearer <token>
```

- 现有前端把令牌存放在 `sessionStorage` 的 `zhijiao.login.token`，可以继续复用。
- 不要通过 URL 查询参数、Cookie 或请求体传令牌。
- 令牌失效、未登录、角色不符时，不要继续渲染业务内容；按第 9 节处理。

发送 JSON 请求体时加：

```http
Content-Type: application/json
```

成功时当前 HTTP 状态码为 200，响应体 `code` 为 200；列表接口同样如此。

### 2.3 统一响应包装 `[已确认]`

#### 对象、详情、操作类响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "20001"
  }
}
```

- `code`：业务结果码，前端判断成功/失败主要看它。
- `msg`：给用户看的提示，可直接展示；不要依赖它做逻辑判断。
- `data`：业务数据；没有数据时可能不出现该字段。
- 当前后端通常返回 HTTP 200，再用 `code` 表示 400/401/403/404/409/500。前端必须同时兼容“HTTP 200 + 非 200 业务码”和真实 HTTP 4xx/5xx 两种情况。

#### 分页列表响应

```json
{
  "total": 0,
  "rows": [],
  "code": 200,
  "msg": "查询成功"
}
```

- `total`：总记录数，数字。
- `rows`：当前页数据，数组；没有数据时必须是 `[]`，不是 `null`。
- 列表接口不使用 `data` 包装。

### 2.4 公共分页结构 `[草案]`

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | number | 否 | 1 | 页码，从 1 开始，最小 1 |
| `pageSize` | number | 否 | 10 | 每页条数，最小 1，最大 100 |

规则：

- 超过最大值按 400 处理，不要由前端静默截断。
- 空结果返回 `code = 200`、`total = 0`、`rows = []`，不是错误。
- 排序由服务端固定，前端不要传 `orderByColumn`、`isAsc` 等排序参数。
- 当前页超出范围时返回空 `rows`，`total` 仍为真实总数；前端可回到第 1 页。
- 参数表中的默认值写在“说明”列；必填字段没有默认值，必须由前端显式提供。

### 2.5 ID、时间、空值、排序统一约定

#### ID

- 所有 `id`、`bindingId`、`sourceId`、`createdByUserId`、`publishedByUserId` 都是**字符串**。
- 前端不要用 `Number()` 转换大整数 ID，否则可能精度丢失。
- 路径参数中的 ID 也按字符串拼接。

#### 时间

- 请求和响应的所有时间都是 RFC3339 UTC，毫秒精度，例如：

```text
2026-10-24T01:30:00.000Z
```

- 展示时转换成用户本地时区。
- `createdAt` 表示公告创建时间；教师端展示标签为“创建时间”。
- `opensAt` 表示学生允许查看的开放时间；学生端展示标签为“开放时间”。
- 当前不返回 `publishedAt`，不要展示“发布时间”，也不要用 `createdAt` 或 `opensAt` 冒充发布时间。
- `opensAt` 是**包含**边界：服务端时间到达 `opensAt` 后才可见。
- `closesAt` 是**不包含**边界：服务端时间到达 `closesAt` 即关闭。
- `closesAt = null` 表示不关闭。
- `operatedAt` 是全部标记已读时服务端确定的操作时间，不是客户端时间。
- 客户端倒计时只用于展示，不能用来判断是否已开放/已关闭；以服务端返回的 `visibilityState` 和服务端时间为准。

#### 空值

- `null` 和空字符串不是一回事：`null` 表示没有该值；空字符串表示传了空内容。
- `closesAt`、`readAt`、`publishedByUserId` 允许为 `null`。
- 列表无数据用 `[]`。
- 详情不存在或不可见返回 `code = 404`，不要返回空对象冒充成功。

#### 排序

| 列表 | 固定排序 |
| --- | --- |
| 教师公告列表 | `createdAt` 倒序，`id` 倒序 |
| 学生公告列表 | `opensAt` 倒序，`id` 倒序 |
| 学生通知列表 | `visibleAt` 倒序，`id` 倒序 |

第二个 `id` 字段用于时间相同时保证顺序稳定。前端不要自行重排，也不要传排序参数。

### 2.6 错误处理总则 `[已确认]`

当前后端错误响应示例：

```json
{
  "code": 403,
  "msg": "没有权限，请联系管理员授权"
}
```

| 场景 | HTTP 状态码（当前实现） | 业务码 `body.code` | 前端处理 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；若网关映射则 401 | 401 | 清除本地登录态，跳登录页；不要继续请求 |
| 角色不匹配 | 200 | 403 | 显示“无权限”，隐藏私有内容，不自动重试 |
| 没有班级/对象权限 | 200 | 403 | 显示“无权限或该内容不属于你”，清空当前上下文数据 |
| 对象不存在/不可见 | 200 | 404 | 公告统一显示“公告不存在或当前不可查看”；通知统一显示“通知不存在或当前不可访问”；从列表移除或刷新 |
| 参数错误 | 200 | 400 | 在表单字段旁显示错误；保留用户输入 |
| 状态冲突 | 200 | 409 | 刷新详情/列表；提示“状态已变化，请刷新后重试” |
| 服务端异常 | 200 | 500 | 显示通用失败提示；保留表单；提供重试 |
| 网络超时/网关错误 | 0/502/504 | 无 | 显示网络错误；GET 可重试；POST 先确认结果再重试 |

补充：

- 不要只判断 `response.ok`；必须同时判断 `result.code`。
- 401 要清除 `zhijiao.login.token` 和当前页面缓存的业务数据。
- 403 不要把上次缓存的公告正文、通知标题继续显示。
- 404 不要自动重试，重试也不会变成有权限。
- 409 不要盲目重复提交；先刷新当前对象状态。
- 500 可以重试，但创建公告、编辑公告等写操作要先防重复提交。

---

## 3. 枚举与显示建议

### 3.1 公告状态 `status` `[已确认]`

| 值 | 中文显示建议 | 说明 |
| --- | --- | --- |
| `DRAFT` | 草稿 | 仅教师可见，可编辑、可发布 |
| `PUBLISHED` | 已发布 | 学生是否可见由开放/关闭时间决定 |
| `ARCHIVED` | 已归档 | 学生不可见；教师仍可查看 |

### 3.2 公告可见状态 `visibilityState` `[已确认]`

服务端返回的计算态，前端只读。

| 值 | 中文显示建议 | 说明 |
| --- | --- | --- |
| `DRAFT` | 草稿 | 未发布 |
| `NOT_YET_OPEN` | 未开放 | 已发布，但未到 `opensAt` |
| `OPEN` | 展示中 | 当前处于 `[opensAt, closesAt)` |
| `CLOSED` | 已关闭 | 已过 `closesAt`，但状态仍是 `PUBLISHED` |
| `ARCHIVED` | 已归档 | 已归档 |

显示建议：

- 教师端优先显示 `visibilityState` 的中文；`status` 可用于筛选。
- 学生端只会看到 `OPEN` 的公告，不需要展示 `status` 或 `visibilityState`。
- `visibilityState` 由服务端根据 `status`、`opensAt`、`closesAt` 和服务端时间计算；前端只做枚举到标签的映射，不使用本机时间自行判断。
- 教师端公告响应中 `visibilityState` 不应为 `null`；若实际返回 `null`，按未知状态显示并记录错误，不要用本地时间猜测。学生端公告响应不依赖该字段。
- `visibilityState` 不代替权限校验；详情和写操作仍由后端重新鉴权。
- 当前 Service 已有计算规则，HTTP 响应已实现；不得把文档状态描述成已真实联调能力。

### 3.3 通知类型 `kind` `[已确认]`

| 值 | 中文显示建议 | `sourceId` 指向 | 跳转所需参数 | 点击后的目标页面 |
| --- | --- | --- | --- | --- |
| `ANNOUNCEMENT` | 公告 | 公告 ID | `kind`、`sourceId`、`bindingId` | 学生公告详情 |
| `PREVIEW` | 预习任务 | 预习任务 ID | `kind`、`sourceId`、`bindingId` | 预习任务页（路由待确认） |
| `ACTIVITY` | 课堂活动 | 课堂活动 ID | `kind`、`sourceId`、`bindingId` | 课堂活动页（路由待确认） |
| `ASSIGNMENT` | 作业 | 作业 ID | `kind`、`sourceId`、`bindingId` | 作业详情页（路由待确认） |
| `GRADE` | 成绩 | 成绩发布记录 ID | `kind`、`sourceId`、`bindingId` | 成绩/作业反馈页（路由待确认） |

说明：

- `sourceId` 对前端是字符串，不要解析成数字。
- 接口响应字段是 `kind`（对应后端 `notice_kind`）；路由参数可命名为 `noticeKind`，值必须来自 `kind`。
- `ANNOUNCEMENT`：用 `kind`（或路由参数 `noticeKind`）、`sourceId`、`bindingId` 调学生公告详情，`bindingId` 用于确认课程班级上下文。
- 除 `ANNOUNCEMENT` 外，其他类型的详情页路由和上游参数尚未确认；不要自造作业、成绩或课堂详情路径。
- 通知只作为跳转入口，不是访问凭证；进入目标页面后必须调用目标详情接口重新鉴权。
- 来源撤回、退班、不存在或无权限时，目标页面显示“当前不可查看”；公告目标统一显示“公告不存在或当前不可查看”。
- 通知详情接口只返回通知元数据，不返回作业正文、成绩正文或公告正文。

### 3.4 已读、未读字段 `[已确认]`

| 字段 | 类型 | 可为空 | 说明 |
| --- | --- | --- | --- |
| `readAt` | string | 是 | 首次成功标记已读的服务端 UTC 时间；`null` 表示未读 |
| `isRead` | boolean | 否 | `readAt !== null` 的派生字段；前端只读 |
| `alreadyRead` | boolean | 否 | 标记已读接口的返回字段：本次请求前是否已读 |

规则：

- 未读：`readAt = null`、`isRead = false`。
- 已读：`readAt` 有值、`isRead = true`。
- 重复标记已读不会改变第一次的 `readAt`。
- 不支持“标记未读”。
- 单条标记已读返回 `readAt`、`alreadyRead`；全部标记已读返回 `markedCount`、`unreadCount`、`operatedAt`，不返回单条的 `alreadyRead`。
- 通知列表和详情接口不会自动把通知标为已读；需要显式调用单条或全部标记已读接口。

---

## 4. 教师端接口

> 状态：角色、可见性、状态流转、已读/去重规则为 `[已确认]`；以下具体路径、字段名、分页默认值、错误码数字为 `[草案]`，最终以联调冻结版为准。
>
> 参数表按“位置”列出路径、查询、请求体三类参数；表中没有某位置的行，表示该位置无参数。GET 接口无请求体；无请求体的 POST 可传空对象 `{}`。

### 4.1 创建公告草稿

#### 页面与用途

教师端 → 我的课程 → 课程详情 → 班级公告 → “新建公告”保存草稿。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `POST` |
| 完整路径 | `/education/teacher/announcements` |
| 调用角色 | 教师 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | JSON |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 请求体 | `bindingId` | string | 是 | 否 | 当前课程—教学班上下文 ID；不能是其他班级 | `"10001"` |
| 请求体 | `title` | string | 是 | 否 | 公告标题，1–200 字符 | `"第 3 周课堂安排"` |
| 请求体 | `body` | string | 是 | 否 | 公告正文，非空；最多 20,000 个 Unicode 码点，且 UTF-8 编码后不超过 65,535 字节；两个上限须同时满足 | `"本周课堂请携带笔记本电脑。"` |
| 请求体 | `opensAt` | string | 否 | 是 | 开始可见时间；默认 `null`，发布时取服务端当前时间 | `"2026-10-25T00:00:00.000Z"` |
| 请求体 | `closesAt` | string | 否 | 是 | 关闭时间；默认 `null` 表示不关闭；`opensAt` 非空时必须晚于 `opensAt`，`opensAt` 为 `null` 时必须晚于服务端发布时间 | `"2026-11-01T00:00:00.000Z"` |

说明：`body` 同时受 20,000 个 Unicode 码点和 65,535 UTF-8 字节两个上限约束；部分四字节字符可能在达到 20,000 个码点之前就先触及 65,535 字节上限。

#### 请求 JSON 示例

```json
{
  "bindingId": "10001",
  "title": "第 3 周课堂安排",
  "body": "本周课堂请携带笔记本电脑。",
  "opensAt": "2026-10-25T00:00:00.000Z",
  "closesAt": "2026-11-01T00:00:00.000Z"
}
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `code` | number | 否 | 业务结果码，成功为 200 | `200` |
| `msg` | string | 否 | 提示文案 | `"操作成功"` |
| `data.id` | string | 否 | 公告 ID | `"20001"` |
| `data.bindingId` | string | 否 | 所属课程—教学班上下文 ID | `"10001"` |
| `data.title` | string | 否 | 公告标题 | `"第 3 周课堂安排"` |
| `data.body` | string | 否 | 公告正文 | `"本周课堂请携带笔记本电脑。"` |
| `data.status` | string | 否 | 公告状态，创建后固定为 `DRAFT` | `"DRAFT"` |
| `data.visibilityState` | string | 否 | 可见状态，创建后为 `DRAFT` | `"DRAFT"` |
| `data.opensAt` | string | 是 | 开始可见时间；未设置时为 `null` | `"2026-10-25T00:00:00.000Z"` |
| `data.closesAt` | string | 是 | 关闭时间；未设置时为 `null` | `"2026-11-01T00:00:00.000Z"` |
| `data.createdByUserId` | string | 否 | 创建人用户 ID | `"90001"` |
| `data.publishedByUserId` | string | 是 | 发布人用户 ID；草稿为 `null` | `null` |
| `data.createdAt` | string | 否 | 创建时间 | `"2026-10-24T01:00:00.000Z"` |

#### 成功响应 JSON

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
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
}
```

#### 空数据响应

本接口成功必返回公告对象，没有“空数据成功”分支。目标班级不存在或不可访问时：

```json
{
  "code": 404,
  "msg": "教学班不存在或不可访问"
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是教师 | 200 | 403 | 显示无权限，不渲染表单 |
| 无该班发布权限 | 200 | 403 | 禁用“保存/发布”，提示联系管理员 |
| `bindingId` 不存在/已结束 | 200 | 404 | 回到课程列表，刷新上下文 |
| 标题/正文/时间窗口非法 | 200 | 400 | 在对应字段显示错误，保留输入 |
| 服务端异常 | 200 | 500 | 保留表单，提示重试；不要假装草稿已保存 |

---

### 4.2 编辑公告草稿

#### 页面与用途

教师端 → 我的课程 → 课程详情 → 班级公告 → 草稿“编辑”。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `PUT` |
| 完整路径 | `/education/teacher/announcements/{announcementId}` |
| 调用角色 | 教师 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | JSON，完整覆盖 |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 路径 | `announcementId` | string | 是 | 否 | 公告 ID | `"20001"` |
| 请求体 | `title` | string | 是 | 否 | 公告标题，1–200 字符 | `"第 3 周课堂安排（更新）"` |
| 请求体 | `body` | string | 是 | 否 | 公告正文，非空；最多 20,000 个 Unicode 码点，且 UTF-8 编码后不超过 65,535 字节；两个上限须同时满足 | `"请携带笔记本电脑，并提前完成预习。"` |
| 请求体 | `opensAt` | string | 否 | 是 | 开始可见时间；不设置传 `null` | `"2026-10-25T00:00:00.000Z"` |
| 请求体 | `closesAt` | string | 否 | 是 | 关闭时间；不关闭传 `null`；`opensAt` 非空时必须晚于 `opensAt`，`opensAt` 为 `null` 时必须晚于服务端发布时间 | `null` |

说明：本文按“完整覆盖”设计，`title`、`body` 必须每次传；`opensAt`、`closesAt` 每次显式传值或 `null`。不支持只传部分字段的局部更新。

说明：`body` 同时受 20,000 个 Unicode 码点和 65,535 UTF-8 字节两个上限约束；部分四字节字符可能在达到 20,000 个码点之前就先触及 65,535 字节上限。

权限：创建者可以编辑自己的 `DRAFT`；其他教师必须具备本班公告管理权限（当前以 `canPublish` 判断）。非创建者且无 `canPublish` 的教师不可编辑。

#### 请求 JSON 示例

```json
{
  "title": "第 3 周课堂安排（更新）",
  "body": "请携带笔记本电脑，并提前完成预习。",
  "opensAt": "2026-10-25T00:00:00.000Z",
  "closesAt": null
}
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `code` | number | 否 | 业务结果码 | `200` |
| `msg` | string | 否 | 提示文案 | `"操作成功"` |
| `data.id` | string | 否 | 公告 ID | `"20001"` |
| `data.bindingId` | string | 否 | 所属课程—教学班上下文 ID | `"10001"` |
| `data.title` | string | 否 | 更新后的标题 | `"第 3 周课堂安排（更新）"` |
| `data.body` | string | 否 | 更新后的正文 | `"请携带笔记本电脑，并提前完成预习。"` |
| `data.status` | string | 否 | 仍为 `DRAFT` | `"DRAFT"` |
| `data.visibilityState` | string | 否 | 仍为 `DRAFT` | `"DRAFT"` |
| `data.opensAt` | string | 是 | 开始可见时间 | `"2026-10-25T00:00:00.000Z"` |
| `data.closesAt` | string | 是 | 关闭时间 | `null` |
| `data.createdByUserId` | string | 否 | 创建人用户 ID | `"90001"` |
| `data.publishedByUserId` | string | 是 | 草稿为 `null` | `null` |
| `data.createdAt` | string | 否 | 创建时间 | `"2026-10-24T01:00:00.000Z"` |

#### 成功响应 JSON

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "20001",
    "bindingId": "10001",
    "title": "第 3 周课堂安排（更新）",
    "body": "请携带笔记本电脑，并提前完成预习。",
    "status": "DRAFT",
    "visibilityState": "DRAFT",
    "opensAt": "2026-10-25T00:00:00.000Z",
    "closesAt": null,
    "createdByUserId": "90001",
    "publishedByUserId": null,
    "createdAt": "2026-10-24T01:00:00.000Z"
  }
}
```

#### 空数据响应

本接口成功必返回公告对象，没有“空数据成功”分支。公告不存在或不属于当前班级时：

```json
{
  "code": 404,
  "msg": "公告不存在或当前不可查看"
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是教师 | 200 | 403 | 显示无权限 |
| 非创建者且无公告管理权限 | 200 | 403 | 关闭编辑抽屉，提示联系管理员 |
| 公告不存在/不属于本班 | 200 | 404 | 关闭抽屉，刷新列表 |
| 公告不是草稿 | 200 | 409 | 刷新详情，提示“已发布/已归档，不能编辑” |
| 标题/正文/时间窗口非法 | 200 | 400 | 字段级错误提示，保留输入 |
| 服务端异常 | 200 | 500 | 保留输入，提示重试 |

---

### 4.3 发布公告

#### 页面与用途

教师端 → 我的课程 → 课程详情 → 班级公告 → 草稿“发布”。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `POST` |
| 完整路径 | `/education/teacher/announcements/{announcementId}/publish` |
| 调用角色 | 教师 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | 无；可传空对象 `{}`，服务端不读取 |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 路径 | `announcementId` | string | 是 | 否 | 公告 ID | `"20001"` |

#### 请求 JSON 示例

```json
{}
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `code` | number | 否 | 业务结果码 | `200` |
| `msg` | string | 否 | 提示文案 | `"操作成功"` |
| `data.id` | string | 否 | 公告 ID | `"20001"` |
| `data.bindingId` | string | 否 | 所属课程—教学班上下文 ID | `"10001"` |
| `data.title` | string | 否 | 公告标题 | `"第 3 周课堂安排（更新）"` |
| `data.body` | string | 否 | 公告正文 | `"请携带笔记本电脑，并提前完成预习。"` |
| `data.status` | string | 否 | 发布后为 `PUBLISHED` | `"PUBLISHED"` |
| `data.visibilityState` | string | 否 | `NOT_YET_OPEN` 或 `OPEN` | `"OPEN"` |
| `data.opensAt` | string | 否 | 发布后必有值；未设置时由服务端补当前时间 | `"2026-10-24T01:05:00.000Z"` |
| `data.closesAt` | string | 是 | 关闭时间；未设置时为 `null` | `null` |
| `data.createdByUserId` | string | 否 | 创建人用户 ID | `"90001"` |
| `data.publishedByUserId` | string | 否 | 发布人用户 ID | `"90001"` |
| `data.createdAt` | string | 否 | 创建时间 | `"2026-10-24T01:00:00.000Z"` |

#### 成功响应 JSON

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "20001",
    "bindingId": "10001",
    "title": "第 3 周课堂安排（更新）",
    "body": "请携带笔记本电脑，并提前完成预习。",
    "status": "PUBLISHED",
    "visibilityState": "OPEN",
    "opensAt": "2026-10-24T01:05:00.000Z",
    "closesAt": null,
    "createdByUserId": "90001",
    "publishedByUserId": "90001",
    "createdAt": "2026-10-24T01:00:00.000Z"
  }
}
```

#### 空数据响应

本接口成功必返回公告对象，没有“空数据成功”分支。公告不存在或不可见时：

```json
{
  "code": 404,
  "msg": "公告不存在或当前不可查看"
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是教师 | 200 | 403 | 显示无权限 |
| 无该班发布权限 | 200 | 403 | 禁止发布，提示联系管理员 |
| 公告不存在/不属于本班 | 200 | 404 | 关闭操作，刷新列表 |
| 公告已归档 | 200 | 409 | 刷新详情，提示不能再次发布 |
| 服务端异常 | 200 | 500 | 保留草稿，提示重试；先刷新确认是否已发布 |

说明：

- 重复发布已发布公告是幂等的：再次调用返回当前公告对象，`code = 200`。
- 不要因为网络超时就直接再次点击发布；先刷新列表/详情确认状态。

---

### 4.4 归档公告

#### 页面与用途

教师端 → 我的课程 → 课程详情 → 班级公告 → 已发布公告“归档”。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `POST` |
| 完整路径 | `/education/teacher/announcements/{announcementId}/archive` |
| 调用角色 | 教师 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | 无；可传空对象 `{}`，服务端不读取 |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 路径 | `announcementId` | string | 是 | 否 | 公告 ID | `"20001"` |

#### 请求 JSON 示例

```json
{}
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `code` | number | 否 | 业务结果码 | `200` |
| `msg` | string | 否 | 提示文案 | `"操作成功"` |
| `data.id` | string | 否 | 公告 ID | `"20001"` |
| `data.bindingId` | string | 否 | 所属课程—教学班上下文 ID | `"10001"` |
| `data.title` | string | 否 | 公告标题 | `"第 3 周课堂安排（更新）"` |
| `data.body` | string | 否 | 公告正文 | `"请携带笔记本电脑，并提前完成预习。"` |
| `data.status` | string | 否 | 归档后为 `ARCHIVED` | `"ARCHIVED"` |
| `data.visibilityState` | string | 否 | 归档后为 `ARCHIVED` | `"ARCHIVED"` |
| `data.opensAt` | string | 否 | 开始可见时间 | `"2026-10-24T01:05:00.000Z"` |
| `data.closesAt` | string | 是 | 关闭时间 | `null` |
| `data.createdByUserId` | string | 否 | 创建人用户 ID | `"90001"` |
| `data.publishedByUserId` | string | 否 | 发布人用户 ID | `"90001"` |
| `data.createdAt` | string | 否 | 创建时间 | `"2026-10-24T01:00:00.000Z"` |

#### 成功响应 JSON

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "20001",
    "bindingId": "10001",
    "title": "第 3 周课堂安排（更新）",
    "body": "请携带笔记本电脑，并提前完成预习。",
    "status": "ARCHIVED",
    "visibilityState": "ARCHIVED",
    "opensAt": "2026-10-24T01:05:00.000Z",
    "closesAt": null,
    "createdByUserId": "90001",
    "publishedByUserId": "90001",
    "createdAt": "2026-10-24T01:00:00.000Z"
  }
}
```

#### 空数据响应

本接口成功必返回公告对象，没有“空数据成功”分支。公告不存在或不可见时：

```json
{
  "code": 404,
  "msg": "公告不存在或当前不可查看"
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是教师 | 200 | 403 | 显示无权限 |
| 无该班发布权限 | 200 | 403 | 禁止归档，提示联系管理员 |
| 公告不存在/不属于本班 | 200 | 404 | 刷新列表 |
| 公告还是草稿 | 200 | 409 | 提示“草稿不能归档”；保留草稿 |
| 公告已归档 | 200 | 200 | 幂等成功，刷新状态即可 |
| 服务端异常 | 200 | 500 | 刷新详情确认状态，再决定是否重试 |

---

### 4.5 教师公告列表

#### 页面与用途

教师端 → 我的课程 → 课程详情 → 班级公告列表；支持按状态筛选、分页、进入详情。

权限：有本班公告管理权限（当前为 `canPublish`）的教师可看本班全部状态；无 `canPublish` 的教师只返回本人草稿和本班已发布/已归档公告。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `GET` |
| 完整路径 | `/education/teacher/announcements` |
| 调用角色 | 教师 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | 无 |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 查询 | `bindingId` | string | 是 | 否 | 当前课程—教学班上下文 ID | `"10001"` |
| 查询 | `status` | string | 否 | 是 | `DRAFT`/`PUBLISHED`/`ARCHIVED`；不传返回全部 | `"PUBLISHED"` |
| 查询 | `pageNum` | number | 否 | 否 | 默认 1，最小 1 | `1` |
| 查询 | `pageSize` | number | 否 | 否 | 默认 10，最大 100 | `10` |

#### 请求 JSON 示例

无请求体。完整请求示例：

```text
GET /education/teacher/announcements?bindingId=10001&status=PUBLISHED&pageNum=1&pageSize=10
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `total` | number | 否 | 总记录数 | `1` |
| `rows` | array | 否 | 当前页公告数组 | `[]` |
| `rows[].id` | string | 否 | 公告 ID | `"20001"` |
| `rows[].bindingId` | string | 否 | 所属上下文 ID | `"10001"` |
| `rows[].title` | string | 否 | 公告标题 | `"第 3 周课堂安排（更新）"` |
| `rows[].status` | string | 否 | `DRAFT`/`PUBLISHED`/`ARCHIVED` | `"PUBLISHED"` |
| `rows[].visibilityState` | string | 否 | 计算态 | `"OPEN"` |
| `rows[].opensAt` | string | 是 | 开始可见时间 | `"2026-10-24T01:05:00.000Z"` |
| `rows[].closesAt` | string | 是 | 关闭时间 | `null` |
| `rows[].createdByUserId` | string | 否 | 创建人 ID | `"90001"` |
| `rows[].publishedByUserId` | string | 是 | 发布人 ID；草稿为 `null` | `"90001"` |
| `rows[].createdAt` | string | 否 | 创建时间 | `"2026-10-24T01:00:00.000Z"` |
| `code` | number | 否 | 业务结果码 | `200` |
| `msg` | string | 否 | 提示文案 | `"查询成功"` |

说明：列表不返回 `body`，进入详情再取。

#### 成功响应 JSON

```json
{
  "total": 1,
  "rows": [
    {
      "id": "20001",
      "bindingId": "10001",
      "title": "第 3 周课堂安排（更新）",
      "status": "PUBLISHED",
      "visibilityState": "OPEN",
      "opensAt": "2026-10-24T01:05:00.000Z",
      "closesAt": null,
      "createdByUserId": "90001",
      "publishedByUserId": "90001",
      "createdAt": "2026-10-24T01:00:00.000Z"
    }
  ],
  "code": 200,
  "msg": "查询成功"
}
```

#### 空数据响应

```json
{
  "total": 0,
  "rows": [],
  "code": 200,
  "msg": "查询成功"
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是教师 | 200 | 403 | 显示无权限，不请求列表 |
| 不是该班教师 | 200 | 403 | 清空列表，提示无权限 |
| `bindingId` 为空 | 200 | 400 | 回到课程列表，重新选择课程/班级 |
| 分页参数越界 | 200 | 400 | 回第 1 页或修正参数 |
| 服务端异常 | 200 | 500 | 显示重试，不显示旧班级数据 |

---

### 4.6 教师公告详情

#### 页面与用途

教师端 → 我的课程 → 课程详情 → 班级公告 → 点击公告标题/“查看详情”。

权限：草稿仅创建者或有本班公告管理权限（当前为 `canPublish`）的教师可读；已发布/已归档可由当前任课教师读取。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `GET` |
| 完整路径 | `/education/teacher/announcements/{announcementId}` |
| 调用角色 | 教师 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | 无 |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 路径 | `announcementId` | string | 是 | 否 | 公告 ID | `"20001"` |

#### 请求 JSON 示例

无请求体。完整请求示例：

```text
GET /education/teacher/announcements/20001
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `code` | number | 否 | 业务结果码 | `200` |
| `msg` | string | 否 | 提示文案 | `"操作成功"` |
| `data.id` | string | 否 | 公告 ID | `"20001"` |
| `data.bindingId` | string | 否 | 所属上下文 ID | `"10001"` |
| `data.title` | string | 否 | 公告标题 | `"第 3 周课堂安排（更新）"` |
| `data.body` | string | 否 | 公告正文 | `"请携带笔记本电脑，并提前完成预习。"` |
| `data.status` | string | 否 | 公告状态 | `"PUBLISHED"` |
| `data.visibilityState` | string | 否 | 计算态 | `"OPEN"` |
| `data.opensAt` | string | 是 | 开始可见时间 | `"2026-10-24T01:05:00.000Z"` |
| `data.closesAt` | string | 是 | 关闭时间 | `null` |
| `data.createdByUserId` | string | 否 | 创建人 ID | `"90001"` |
| `data.publishedByUserId` | string | 是 | 发布人 ID；草稿为 `null` | `"90001"` |
| `data.createdAt` | string | 否 | 创建时间 | `"2026-10-24T01:00:00.000Z"` |

#### 成功响应 JSON

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "20001",
    "bindingId": "10001",
    "title": "第 3 周课堂安排（更新）",
    "body": "请携带笔记本电脑，并提前完成预习。",
    "status": "PUBLISHED",
    "visibilityState": "OPEN",
    "opensAt": "2026-10-24T01:05:00.000Z",
    "closesAt": null,
    "createdByUserId": "90001",
    "publishedByUserId": "90001",
    "createdAt": "2026-10-24T01:00:00.000Z"
  }
}
```

#### 空数据响应

公告不存在、不属于当前班级或不可见时：

```json
{
  "code": 404,
  "msg": "公告不存在或当前不可查看"
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是教师 | 200 | 403 | 显示无权限 |
| 公告不属于当前教师班级 | 200 | 404 | 关闭详情，刷新列表 |
| 公告不存在 | 200 | 404 | 显示“公告不存在或已删除” |
| 服务端异常 | 200 | 500 | 显示重试，不展示旧正文 |

---

## 5. 学生端接口

> 状态：角色、可见性、已读/去重规则为 `[已确认]`；以下具体路径、字段名、分页默认值、错误码数字为 `[草案]`，最终以联调冻结版为准。学生公告与学生通知 5 个接口的后端代码与隔离测试已实现；生产授权适配器未接入，真实联调未完成。
>
> 参数表按“位置”列出路径、查询、请求体三类参数；表中没有某位置的行，表示该位置无参数。GET 接口无请求体；无请求体的 POST 可传空对象 `{}`。

### 5.1 学生公告列表

#### 页面与用途

学生端 → 我的课程 → 课程详情 → 课程公告；或学习工作台进入某门课程的公告列表。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `GET` |
| 完整路径 | `/education/student/announcements` |
| 调用角色 | 学生 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | 无 |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 查询 | `bindingId` | string | 否 | 是 | 课程—教学班上下文 ID；不传返回本人所有当前班级的可见公告 | `"10001"` |
| 查询 | `pageNum` | number | 否 | 否 | 默认 1，最小 1 | `1` |
| 查询 | `pageSize` | number | 否 | 否 | 默认 10，最大 100 | `10` |

#### 请求 JSON 示例

无请求体。完整请求示例：

```text
GET /education/student/announcements?bindingId=10001&pageNum=1&pageSize=10
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `total` | number | 否 | 总记录数 | `1` |
| `rows` | array | 否 | 当前页公告数组 | `[]` |
| `rows[].id` | string | 否 | 公告 ID | `"20001"` |
| `rows[].bindingId` | string | 否 | 所属上下文 ID | `"10001"` |
| `rows[].title` | string | 否 | 公告标题 | `"第 3 周课堂安排（更新）"` |
| `rows[].opensAt` | string | 否 | 开放时间（学生可见） | `"2026-10-24T01:05:00.000Z"` |
| `rows[].closesAt` | string | 是 | 关闭时间 | `null` |
| `rows[].createdAt` | string | 否 | 创建时间 | `"2026-10-24T01:00:00.000Z"` |
| `code` | number | 否 | 业务结果码 | `200` |
| `msg` | string | 否 | 提示文案 | `"查询成功"` |

说明：

- 列表只返回当前可见公告；草稿、未开放、已关闭、已归档不会出现。
- 列表不返回 `body`、发布人、创建人。
- 学生端不展示 `status`、`visibilityState`。

#### 成功响应 JSON

```json
{
  "total": 1,
  "rows": [
    {
      "id": "20001",
      "bindingId": "10001",
      "title": "第 3 周课堂安排（更新）",
      "opensAt": "2026-10-24T01:05:00.000Z",
      "closesAt": null,
      "createdAt": "2026-10-24T01:00:00.000Z"
    }
  ],
  "code": 200,
  "msg": "查询成功"
}
```

#### 空数据响应

```json
{
  "total": 0,
  "rows": [],
  "code": 200,
  "msg": "查询成功"
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是学生 | 200 | 403 | 显示无权限，不请求列表 |
| 传入的 `bindingId` 不是本人班级 | 200 | 403 | 清空列表，回到课程列表 |
| 分页参数越界 | 200 | 400 | 回第 1 页或修正参数 |
| 服务端异常 | 200 | 500 | 显示重试，不显示旧公告 |

---

### 5.2 学生公告详情

#### 页面与用途

学生端 → 课程公告列表 → 点击公告；或从 `ANNOUNCEMENT` 通知进入。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `GET` |
| 完整路径 | `/education/student/announcements/{announcementId}` |
| 调用角色 | 学生 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | 无 |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 路径 | `announcementId` | string | 是 | 否 | 公告 ID | `"20001"` |

#### 请求 JSON 示例

无请求体。完整请求示例：

```text
GET /education/student/announcements/20001
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `code` | number | 否 | 业务结果码 | `200` |
| `msg` | string | 否 | 提示文案 | `"操作成功"` |
| `data.id` | string | 否 | 公告 ID | `"20001"` |
| `data.bindingId` | string | 否 | 所属上下文 ID | `"10001"` |
| `data.title` | string | 否 | 公告标题 | `"第 3 周课堂安排（更新）"` |
| `data.body` | string | 否 | 公告正文 | `"请携带笔记本电脑，并提前完成预习。"` |
| `data.opensAt` | string | 否 | 开放时间（学生可见） | `"2026-10-24T01:05:00.000Z"` |
| `data.closesAt` | string | 是 | 关闭时间 | `null` |
| `data.createdAt` | string | 否 | 创建时间 | `"2026-10-24T01:00:00.000Z"` |

#### 成功响应 JSON

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "20001",
    "bindingId": "10001",
    "title": "第 3 周课堂安排（更新）",
    "body": "请携带笔记本电脑，并提前完成预习。",
    "opensAt": "2026-10-24T01:05:00.000Z",
    "closesAt": null,
    "createdAt": "2026-10-24T01:00:00.000Z"
  }
}
```

#### 空数据响应

公告不存在、外班、退班、未开放、已关闭或已归档时，统一返回同一结果：

```json
{
  "code": 404,
  "msg": "公告不存在或当前不可查看"
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是学生 | 200 | 403 | 显示无权限 |
| 外班学生/已退班 | 200 | 404 | 统一显示“公告不存在或当前不可查看”，回列表 |
| 公告未开放/已关闭/已归档 | 200 | 404 | 同上；不要展示缓存正文 |
| 服务端异常 | 200 | 500 | 显示重试，不展示旧正文 |

---

### 5.3 学生通知列表

#### 页面与用途

学生端 → 学习工作台 → 学习通知/课程公告；或顶栏通知入口。按类型、课程、未读筛选。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `GET` |
| 完整路径 | `/education/student/notices` |
| 调用角色 | 学生 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | 无 |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 查询 | `kind` | string | 否 | 是 | 通知类型；不传返回全部 | `"GRADE"` |
| 查询 | `bindingId` | string | 否 | 是 | 课程—教学班上下文 ID；不传返回全部 | `"10001"` |
| 查询 | `unreadOnly` | boolean | 否 | 否 | 默认 `false`；`true` 只返回未读 | `true` |
| 查询 | `pageNum` | number | 否 | 否 | 默认 1，最小 1 | `1` |
| 查询 | `pageSize` | number | 否 | 否 | 默认 10，最大 100 | `10` |

#### 请求 JSON 示例

无请求体。完整请求示例：

```text
GET /education/student/notices?kind=GRADE&bindingId=10001&unreadOnly=true&pageNum=1&pageSize=10
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `total` | number | 否 | 总记录数 | `1` |
| `rows` | array | 否 | 当前页通知数组 | `[]` |
| `rows[].id` | string | 否 | 通知 ID | `"30001"` |
| `rows[].kind` | string | 否 | 通知类型 | `"GRADE"` |
| `rows[].bindingId` | string | 是 | 所属上下文 ID；6-02 中应为字符串 | `"10001"` |
| `rows[].sourceId` | string | 否 | 来源业务对象 ID | `"40001"` |
| `rows[].title` | string | 否 | 通知标题快照 | `"课后作业成绩已发布"` |
| `rows[].visibleAt` | string | 否 | 可见时间 | `"2026-10-24T01:00:00.000Z"` |
| `rows[].readAt` | string | 是 | 首次已读时间；未读为 `null` | `null` |
| `rows[].isRead` | boolean | 否 | 是否已读 | `false` |
| `rows[].createdAt` | string | 否 | 创建时间 | `"2026-10-24T01:00:00.000Z"` |
| `code` | number | 否 | 业务结果码 | `200` |
| `msg` | string | 否 | 提示文案 | `"查询成功"` |

说明：

- 服务端只返回当前登录学生本人的通知；请求里传 `recipientUserId` 不生效。
- 列表只返回当前可见通知；未来 `visibleAt` 的通知不会出现。
- 列表不返回通知正文；正文由来源页面提供。

#### 成功响应 JSON

```json
{
  "total": 1,
  "rows": [
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
  ],
  "code": 200,
  "msg": "查询成功"
}
```

#### 空数据响应

```json
{
  "total": 0,
  "rows": [],
  "code": 200,
  "msg": "查询成功"
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是学生 | 200 | 403 | 显示无权限，不请求列表 |
| 传入的 `bindingId` 不是本人班级 | 200 | 403 | 清空列表，回到当前课程 |
| `kind` 不在枚举内 | 200 | 400 | 重置为“全部” |
| 分页参数越界 | 200 | 400 | 回第 1 页或修正参数 |
| 服务端异常 | 200 | 500 | 显示重试，不显示旧通知 |

---

### 5.4 学生通知详情

#### 页面与用途

学生端 → 通知列表 → 点击某条通知；展示标题、类型、时间、已读状态，并给出进入来源页面的入口。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `GET` |
| 完整路径 | `/education/student/notices/{noticeId}` |
| 调用角色 | 学生 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | 无 |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 路径 | `noticeId` | string | 是 | 否 | 通知 ID | `"30001"` |

#### 请求 JSON 示例

无请求体。完整请求示例：

```text
GET /education/student/notices/30001
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `code` | number | 否 | 业务结果码 | `200` |
| `msg` | string | 否 | 提示文案 | `"操作成功"` |
| `data.id` | string | 否 | 通知 ID | `"30001"` |
| `data.kind` | string | 否 | 通知类型 | `"GRADE"` |
| `data.bindingId` | string | 是 | 所属上下文 ID | `"10001"` |
| `data.sourceId` | string | 否 | 来源业务对象 ID | `"40001"` |
| `data.title` | string | 否 | 通知标题快照 | `"课后作业成绩已发布"` |
| `data.visibleAt` | string | 否 | 可见时间 | `"2026-10-24T01:00:00.000Z"` |
| `data.readAt` | string | 是 | 首次已读时间；未读为 `null` | `null` |
| `data.isRead` | boolean | 否 | 是否已读 | `false` |
| `data.createdAt` | string | 否 | 创建时间 | `"2026-10-24T01:00:00.000Z"` |

说明：

- 本接口不返回公告正文、作业正文、成绩正文。
- `ANNOUNCEMENT` 类型：带 `kind=ANNOUNCEMENT`、`sourceId`、`bindingId` 调 5.2 获取公告正文；目标不可查看时统一显示“公告不存在或当前不可查看”。
- `PREVIEW`/`ACTIVITY`/`ASSIGNMENT`/`GRADE`：带 `kind`、`sourceId`、`bindingId` 跳转对应来源页面；路由和上游参数待确认。
- 通知只作为跳转入口，不是访问凭证；进入目标页面后必须调用目标详情接口重新鉴权。
- 来源撤回、退班、不存在或无权限时，目标页面显示“当前不可查看”。
- 本接口不会自动标记已读。

#### 成功响应 JSON

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
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
}
```

#### 空数据响应

通知不存在、不属于当前学生或当前不可见时：

```json
{
  "code": 404,
  "msg": "通知不存在或当前不可访问"
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是学生 | 200 | 403 | 显示无权限 |
| 通知属于其他学生 | 200 | 404 | 显示“通知不存在或当前不可访问”，不确认他人通知存在 |
| 通知未来才可见 | 200 | 404 | 从列表移除，提示稍后再看 |
| 学生已退班 | 200 | 404 | 清空该课程通知，回课程列表 |
| 服务端异常 | 200 | 500 | 显示重试，不显示旧通知 |

---

### 5.5 学生未读数

#### 页面与用途

学生端顶栏通知铃铛、学习工作台“未读通知”角标、通知列表页头部。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `GET` |
| 完整路径 | `/education/student/notices/unread-count` |
| 调用角色 | 学生 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | 无 |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 查询 | `bindingId` | string | 否 | 是 | 限定某个课程—教学班；不传统计本人全部 | `"10001"` |

#### 请求 JSON 示例

无请求体。完整请求示例：

```text
GET /education/student/notices/unread-count?bindingId=10001
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `code` | number | 否 | 业务结果码 | `200` |
| `msg` | string | 否 | 提示文案 | `"操作成功"` |
| `data.unreadCount` | number | 否 | 未读通知数，最小 0 | `3` |
| `data.asOf` | string | 否 | 统计时点，服务端 UTC 时间 | `"2026-10-24T01:05:00.000Z"` |

#### 成功响应 JSON

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

#### 空数据响应

没有未读通知时仍为成功：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "unreadCount": 0,
    "asOf": "2026-10-24T01:05:00.000Z"
  }
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是学生 | 200 | 403 | 不显示角标 |
| `bindingId` 不是本人班级 | 200 | 403 | 不显示角标，清除该课程上下文 |
| 服务端异常 | 200 | 500 | 角标显示为不可用或上次值；不要显示 0 冒充真实结果 |

---

### 5.6 单条标记通知已读

#### 页面与用途

学生端 → 通知详情/通知列表 → 用户打开通知或点击“标为已读”。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `POST` |
| 完整路径 | `/education/student/notices/{noticeId}/read` |
| 调用角色 | 学生 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | 无；可传空对象 `{}`，服务端不读取 |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 路径 | `noticeId` | string | 是 | 否 | 通知 ID | `"30001"` |

#### 请求 JSON 示例

```json
{}
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `code` | number | 否 | 业务结果码 | `200` |
| `msg` | string | 否 | 提示文案 | `"操作成功"` |
| `data.id` | string | 否 | 通知 ID | `"30001"` |
| `data.readAt` | string | 否 | 首次成功标记已读的时间；重复请求返回同一时间 | `"2026-10-24T01:06:00.000Z"` |
| `data.alreadyRead` | boolean | 否 | 本次请求前是否已读 | `false` |

#### 成功响应 JSON

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

#### 空数据响应

本接口成功必返回已读结果，没有“空数据成功”分支。通知不存在或不可见时：

```json
{
  "code": 404,
  "msg": "通知不存在或当前不可访问"
}
```

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色不是学生 | 200 | 403 | 显示无权限 |
| 通知属于其他学生 | 200 | 404 | 从列表移除，不确认他人通知 |
| 通知未来才可见/已退班 | 200 | 404 | 从列表移除，刷新未读数 |
| 重复标记已读 | 200 | 200 | 幂等成功；用返回的 `readAt` 更新本地状态 |
| 服务端异常 | 200 | 500 | 保留未读状态，提示重试 |

说明：

- 重复调用不会改变第一次的 `readAt`。
- 并发调用只有一个请求真正写入，两个请求最终返回同一个 `readAt`。
- 标记成功后建议重新请求未读数，不要只做本地减一，避免多端/多标签页不一致。

---

### 5.7 全部标记通知已读

#### 页面与用途

学生端 → 通知列表/学习工作台 → 点击“全部标为已读”。处理当前学生本人全部当前可见未读通知，不受当前页、页大小或筛选限制。

#### 请求

| 项目 | 内容 |
| --- | --- |
| 方法 | `PUT` |
| 完整路径 | `/education/student/notices/read-all` |
| 调用角色 | 学生 |
| 登录 | 需要；`Authorization: Bearer <token>` |
| 请求体 | 无；不接收 `recipientUserId` |

#### 参数表

| 位置 | 字段 | 类型 | 必填 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- | --- | --- |
| 路径 | 无 | - | - | - | 本接口无路径参数 | - |
| 查询 | 无 | - | - | - | 本接口无查询参数 | - |
| 请求体 | 无 | - | - | - | 不发送 JSON；操作者来自服务端认证上下文 | - |

#### 请求说明

无请求体。完整请求示例：

```text
PUT /education/student/notices/read-all
```

#### 成功响应字段表

| 字段 | 类型 | 可为空 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `code` | number | 否 | 业务结果码，成功为 200 | `200` |
| `msg` | string | 否 | 提示文案 | `"操作成功"` |
| `data.markedCount` | number | 否 | 本次由未读变为已读的实际通知数，最小 0 | `3` |
| `data.unreadCount` | number | 否 | 操作完成后当前剩余未读数，最小 0 | `0` |
| `data.operatedAt` | string | 否 | 本次操作确定的服务端 UTC 时间 | `"2026-10-25T10:00:00.000Z"` |

#### 成功响应 JSON

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

#### 没有未读通知时的响应

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

`markedCount = 0` 是成功，不是业务失败；不要显示错误提示。

#### 主要失败情况

| 场景 | HTTP 状态码（当前） | 业务码 | 前端处理建议 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页；不修改列表 |
| 角色不是学生 | 200 | 403 | 显示无权限，不发起操作 |
| 服务端异常 | 200 | 500 | 保留未读状态，提示重试 |
| 网络超时 | 0/502/504 | 无 | 可重试；接口幂等，但重试前不要本地清零 |

#### 并发与刷新

- 只标记操作时点之前已经可见、当前 `read_at IS NULL` 的通知。
- 只更新当前登录学生本人的通知；其他用户的通知不变。
- 同一次操作中新标记的记录共用返回的 `operatedAt` 作为 `read_at`。
- 操作后新产生或新到可见时间的通知仍保持未读。
- 已读记录不重复更新时间；单条已读与全部已读同时发生时，不覆盖已有 `read_at`。
- `markedCount` 以实际更新数为准；重复调用返回 0 仍成功。
- 成功后必须重新请求通知列表和未读数，不要只做本地减一。
- 失败时保留原有未读状态，不要乐观清零。

---

## 6. 页面与接口对应关系

| 页面/区域 | 功能 | 调用接口 | 触发时机 |
| --- | --- | --- | --- |
| 教师端 · 我的课程 · 课程详情 · 班级公告 | 公告列表、筛选、分页 | `GET /education/teacher/announcements` | 进入页面、切换状态/页码 |
| 教师端 · 班级公告 · 新建公告 | 保存草稿 | `POST /education/teacher/announcements` | 点击“保存草稿” |
| 教师端 · 班级公告 · 编辑草稿 | 更新草稿 | `PUT /education/teacher/announcements/{announcementId}` | 点击“保存” |
| 教师端 · 班级公告 · 发布 | 发布公告 | `POST /education/teacher/announcements/{announcementId}/publish` | 点击“发布”并确认 |
| 教师端 · 班级公告 · 归档 | 归档公告 | `POST /education/teacher/announcements/{announcementId}/archive` | 点击“归档”并确认 |
| 教师端 · 班级公告 · 详情 | 查看公告正文 | `GET /education/teacher/announcements/{announcementId}` | 点击公告标题/“查看” |
| 学生端 · 我的课程 · 课程详情 · 课程公告 | 公告列表、分页 | `GET /education/student/announcements` | 进入页面、切换页码 |
| 学生端 · 课程公告 · 详情 | 查看公告正文 | `GET /education/student/announcements/{announcementId}` | 点击公告 |
| 学生端 · 学习工作台 · 学习通知/课程公告 | 通知列表、类型/课程/未读筛选 | `GET /education/student/notices` | 进入页面、切换筛选/页码 |
| 学生端 · 通知列表 · 详情 | 查看通知元数据 | `GET /education/student/notices/{noticeId}` | 点击通知 |
| 学生端 · 顶栏通知铃铛/工作台角标 | 未读数 | `GET /education/student/notices/unread-count` | 页面加载、标记已读后、回到前台 |
| 学生端 · 通知详情/通知列表 | 单条标记已读 | `POST /education/student/notices/{noticeId}/read` | 用户打开通知或点击“标为已读” |
| 学生端 · 通知列表/学习工作台 | 全部标记已读 | `PUT /education/student/notices/read-all` | 点击“全部标为已读”并确认 |

---

## 7. 接口汇总表

共 13 个接口：教师端 6 个，学生端 7 个。教师端 6 个、学生公告列表/详情 2 个、学生通知 5 个接口的后端领域对象、Controller/DTO/Service/Mapper XML 与隔离测试已实现；生产授权适配器未接入，fan-out/上游 PREVIEW/ACTIVITY/ASSIGNMENT/GRADE 事件接收未实现，真实联调未完成。

### 7.1 教师端

| 页面 | 功能 | 方法 | 路径 | 调用角色 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 教师端 · 课程详情 · 班级公告 | 创建草稿 | `POST` | `/education/teacher/announcements` | 教师 | 创建后固定为 `DRAFT` |
| 教师端 · 课程详情 · 班级公告 | 编辑草稿 | `PUT` | `/education/teacher/announcements/{announcementId}` | 教师 | 仅草稿可编辑，完整覆盖 |
| 教师端 · 课程详情 · 班级公告 | 发布公告 | `POST` | `/education/teacher/announcements/{announcementId}/publish` | 教师 | 幂等；本批不生成通知 |
| 教师端 · 课程详情 · 班级公告 | 归档公告 | `POST` | `/education/teacher/announcements/{announcementId}/archive` | 教师 | 仅已发布可归档；幂等 |
| 教师端 · 课程详情 · 班级公告 | 公告列表 | `GET` | `/education/teacher/announcements` | 教师 | 必须带 `bindingId` |
| 教师端 · 课程详情 · 班级公告 | 公告详情 | `GET` | `/education/teacher/announcements/{announcementId}` | 教师 | 返回正文 |

### 7.2 学生端

| 页面 | 功能 | 方法 | 路径 | 调用角色 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 学生端 · 课程详情 · 课程公告 | 公告列表 | `GET` | `/education/student/announcements` | 学生 | 仅返回当前可见公告 |
| 学生端 · 课程详情 · 课程公告 | 公告详情 | `GET` | `/education/student/announcements/{announcementId}` | 学生 | 返回正文 |
| 学生端 · 学习工作台 · 通知 | 通知列表 | `GET` | `/education/student/notices` | 学生 | 仅返回本人通知 |
| 学生端 · 学习工作台 · 通知 | 通知详情 | `GET` | `/education/student/notices/{noticeId}` | 学生 | 不返回来源正文 |
| 学生端 · 顶栏/工作台 | 未读数 | `GET` | `/education/student/notices/unread-count` | 学生 | 支持按 `bindingId` 过滤 |
| 学生端 · 通知详情/列表 | 单条标记已读 | `POST` | `/education/student/notices/{noticeId}/read` | 学生 | 幂等，仅本人 |
| 学生端 · 通知列表/学习工作台 | 全部标记已读 | `PUT` | `/education/student/notices/read-all` | 学生 | 幂等，仅本人当前可见未读 |

---

## 8. 调用顺序

### 8.1 教师发布公告

1. 进入课程详情，取得当前 `bindingId`。
2. 调用 `POST /education/teacher/announcements` 创建草稿；成功后拿到 `announcementId`。
3. 如需修改，调用 `PUT /education/teacher/announcements/{announcementId}`；每次提交完整字段。
4. 调用 `POST /education/teacher/announcements/{announcementId}/publish`。
5. 成功后用返回对象更新页面；再调用 `GET /education/teacher/announcements?bindingId=...` 刷新列表。
6. 发布失败时保留草稿和表单；409 先刷新详情；网络超时先刷新列表确认是否已发布。

### 8.2 学生查看公告

1. 调用 `GET /education/student/announcements`，传当前 `bindingId` 或省略以获取全部。
2. 点击公告，调用 `GET /education/student/announcements/{announcementId}`。
3. 如果该公告是从 `ANNOUNCEMENT` 通知进入，调用 `POST /education/student/notices/{noticeId}/read` 标记对应通知已读。
4. 重新调用 `GET /education/student/notices/unread-count` 更新角标。
5. 公告详情 404 时，提示“公告不存在或当前不可查看”，并刷新公告列表；外班、退班、未开放、已关闭不区分具体原因。

### 8.3 学生查看通知

1. 调用 `GET /education/student/notices`，按需传 `kind`、`bindingId`、`unreadOnly`。
2. 点击通知，调用 `GET /education/student/notices/{noticeId}`。
3. 根据 `kind` 跳转：
   - `ANNOUNCEMENT`：带 `kind`、`sourceId`、`bindingId` 调用 `GET /education/student/announcements/{sourceId}`；
   - `PREVIEW`/`ACTIVITY`/`ASSIGNMENT`/`GRADE`：带 `kind`、`sourceId`、`bindingId` 跳转对应来源页面；路由和上游参数待确认。
4. 用户进入详情或点击“标为已读”后，调用 `POST /education/student/notices/{noticeId}/read`。
5. 刷新未读数；更新列表中的 `readAt`、`isRead`。
6. 目标页面必须重新调用来源详情接口鉴权；通知数据不能代替来源详情。

### 8.4 单条标记已读

1. 只在用户明确打开通知或点击“标为已读”时调用。
2. 调用 `POST /education/student/notices/{noticeId}/read`，不传请求体或传 `{}`。
3. 用返回的 `readAt` 更新该行；`alreadyRead = true` 也表示成功，不要报错。
4. 重新请求 `unread-count`，不要只做本地减一。
5. 404 时从列表移除该通知；500 时保留未读状态，提示重试。

### 8.5 全部标记已读

1. 用户点击“全部标为已读”并确认。
2. 调用 `PUT /education/student/notices/read-all`，不传请求体、不传 `recipientUserId`。
3. 用返回的 `markedCount` 显示本次处理数量；`markedCount = 0` 仍按成功处理。
4. 用返回的 `unreadCount` 更新角标，但建议同时重新请求 `unread-count` 校准多端状态。
5. 重新请求通知列表，更新 `readAt`、`isRead`；不要只改当前页。
6. 失败时保留未读状态，不清零角标；401 清除登录态。

---

## 9. 前端需要特别处理的状态

### 9.1 空数据

| 场景 | 展示建议 |
| --- | --- |
| 教师公告列表为空 | 显示“还没有公告”，展示“新建公告”按钮；不要显示错误 |
| 学生公告列表为空 | 显示“暂无课程公告”；不要显示教师的草稿/归档内容 |
| 通知列表为空 | 显示“暂无通知”；未读数显示 0 |
| 未读数为 0 | 隐藏角标或显示“无未读”；不要显示错误 |
| 全部标记已读时没有未读 | 显示“没有未读通知”或直接成功提示；`markedCount = 0` 不是错误 |
| 公告详情 404 | 统一显示“公告不存在或当前不可查看”；不要继续展示缓存正文，不区分外班/退班/未开放/已关闭 |
| 发布时班级无学生 | 公告发布成功；本批不生成通知，不要提示“发布失败” |

### 9.2 无权限

- 403：显示“无权限”，隐藏正文、标题、学生信息；不要自动重试。
- 公告 404：统一显示“公告不存在或当前不可查看”；不要提示“你没有权限查看”，避免泄露对象存在性。
- 通知 404：统一显示“通知不存在或当前不可访问”；不要确认他人通知是否存在。
- 全部标记已读没有业务 404；401 表示登录失效，403 表示角色不符。
- 教师撤权/学生退班后，清空当前课程上下文缓存，回到课程列表。
- 不要用前端路由隐藏代替接口校验；即使按钮隐藏，直接调用接口也必须处理 403。

### 9.3 失败与重试

| 情况 | 建议 |
| --- | --- |
| 网络超时/断网 | GET 可重试；POST 先刷新列表/详情确认结果，再决定是否重试 |
| 创建公告超时 | 先调用教师公告列表，确认是否已生成；不要直接重复创建，避免重复草稿 |
| 编辑公告超时 | 重新拉取详情，比较内容后再提交；本接口无版本冲突提示 |
| 发布/归档超时 | 重新拉取详情确认状态；发布/归档是幂等的，可安全重试 |
| 单条标记已读超时 | 可重试；接口幂等，重复调用返回同一 `readAt` |
| 全部标记已读超时 | 可重试；接口幂等，重复调用可能返回 `markedCount = 0`；重试前不要本地清零 |
| 401 | 清除 `zhijiao.login.token`，跳登录页 |
| 403 | 显示无权限；不要重试 |
| 404 | 从列表移除；刷新列表；全部标记已读没有业务 404 |
| 409 | 刷新详情；提示状态已变化，不要盲目重试 |
| 400 | 字段级提示；保留用户输入 |
| 500 | 通用失败提示；保留表单；提供重试按钮 |

### 9.4 防重复提交

- 创建、编辑、发布、归档按钮在请求进行中禁用。
- 发布、归档、单条标记已读、全部标记已读是幂等的，但仍建议禁用按钮，避免重复请求。
- 创建接口没有幂等键；网络失败后先查列表，再决定是否重新创建。
- 全部标记已读成功后必须刷新列表和未读数；不要只改当前页或只做本地减一。
- 不要在前端本地生成公告 ID 或伪造状态；以服务端返回为准。

---

## 10. 待确认项

以下内容尚未冻结，前端不要自行编造；联调前由接口负责人确认。

1. **接口路径前缀**：`/education/teacher/...`、`/education/student/...` 目前是草案；最终是否保留 `/education` 前缀待确认。
2. **错误码方案**：当前只约定数字 `400/401/403/404/409/500`；是否增加字符串业务码未确认。
3. **HTTP 状态码**：当前若依通常返回 HTTP 200 + 业务码；正式网关是否映射真实 HTTP 4xx/5xx 待确认。前端应同时兼容两种。
4. **创建人/发布人显示名**：当前只返回用户 ID，没有姓名；是否增加姓名/头像字段待确认。
5. **编辑语义**：本文按完整覆盖（PUT）设计；是否支持局部更新待确认。
6. **非公告通知的点击路由**：`PREVIEW`、`ACTIVITY`、`ASSIGNMENT`、`GRADE` 的前端目标页面尚未确认；跳转参数为 `kind`、`sourceId`、`bindingId`，不要自造路由。
7. **打开通知是否自动已读**：本文建议显式调用单条标记已读；产品是否要求“打开即已读”待确认。
8. **退班后的历史通知**：本文按“当前有效学生才可读”设计；历史保留策略待产品确认。
9. **管理员业务接口**：M0 不提供；后续是否需要只读治理视图待确认。
10. **`status`/`visibilityState` 中文文案**：本文为建议值，最终以产品文案为准。
11. **分页默认值**：本文默认 `pageNum=1`、`pageSize=10`、最大 100；最终是否调整待确认。
12. **`bindingId` 是否必传**：教师公告列表/创建必传；学生公告列表和通知列表可选。最终以联调为准。
13. **`sourceId` 对前端的路由含义**：`GRADE` 当前指向成绩发布记录 ID；前端只应把它当作跳转参数，不要解析业务含义。
14. **通知详情是否返回来源页面链接**：当前不返回；前端按 `kind + sourceId + bindingId` 自行路由，路由表待确认。

已确认（不再列为待确认）：

- 草稿权限：创建者可读/编辑自己的 `DRAFT`；有本班公告管理权限（当前为 `canPublish`）的教师可读/编辑；发布/归档必须有 `canPublish`。
- 公告正文：非空；最多 20,000 个 Unicode 码点，且 UTF-8 编码后不超过 65,535 字节；两个上限须同时满足。部分四字节字符可能先触及 65,535 字节上限。
- 不返回 `publishedAt`，不新增 `published_at`；教师端 `createdAt` 是“创建时间”，学生端 `opensAt` 是“开放时间”，页面不写“发布时间”。
- M0 同时支持单条标记已读和全部标记已读；全部标记已读接口为 `PUT /education/student/notices/read-all`，后端已实现并有隔离测试，尚未真实联调。
