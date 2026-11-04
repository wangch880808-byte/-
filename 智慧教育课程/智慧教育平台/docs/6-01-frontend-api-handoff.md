# 6-01 三端总览前端接口交接文档（M0）

> 版本：1.2｜日期：2026-11-04（基于 1.1/2026-11-02 第一轮修订）｜读者：第六组前端 6B
>
> 依据：`docs/6-01-overview-contract.md`（草案）。本文用于前端 6-03/6-04 对齐字段、状态和页面展示；路径、指标编码、默认时间窗仍可能随评审调整。
>
> 当前状态：后端已完成四端点 Service/Controller、DTO 和单元测试；**生产数据适配器未接入，当前没有真实业务数据**。所有 `OK` 示例仅用于说明字段，不代表联调结果。缺适配器时返回 `UNAVAILABLE`，禁止前端显示为 0 或空页面。
>
> 2026-11-02 后端静态核对更正：实际后端以 `feature/6-01-overview-api@22898ae27a90779709b0c29b846181aa7054e0b0` 为准；6B 6-03/6-04 尚未取得可核验版本。参数表已按实际 Controller 修正；逐项证据、差异和待确认项见 `docs/6-01-frontend-review-checklist.md`。本文不构成 6B 已确认或真实联调结论。
>
> 2026-11-04 第一轮修订：教师 `assignment.pending` 已按 `classId` 对应 binding 过滤；`classId<=0` 返回业务 400；一次请求共用单一总预算；端口嵌套结果违约按对应 item `UNAVAILABLE` 收敛。具体修复提交见 PR #24，不在正文写自引用 SHA。生产适配器仍未接入，真实 HTTP/登录/授权/审计联调仍未完成。

## 1. 端点一览

| 页面 | 方法/路径 | 角色 | 权限串（待第 1 组配置） |
| --- | --- | --- | --- |
| 教师教学总览 | `GET /education/teacher/overview` | 教师 | `education:overview:teacher` |
| 学生今日待办 | `GET /education/student/today-todos` | 学生 | `education:overview:student` |
| 管理员管理总览 | `GET /education/admin/overview` | 管理员 | `education:overview:admin` |
| 管理员待办事项 | `GET /education/admin/todos` | 管理员 | `education:overview:admin` |

查询参数按端点区分（以实际 Controller 为准）：

| 端点 | 参数 | 必填 | 说明 |
| --- | --- | --- | --- |
| 教师总览 | `from`、`to` | 否 | 必须成对；RFC 3339 时间；左闭右开 |
| 教师总览 | `classId` | 否 | 仅教师端点接收；`Long` 类型；必须为正整数；只能传本人授权教学班 |
| 学生今日待办 | `from`、`to` | 否 | 必须成对；实际不接收 `date`/`classId` |
| 管理员总览 | `from`、`to` | 否 | 必须成对；实际不接收 `organizationId` |
| 管理员待办 | `from`、`to` | 否 | 必须成对；实际不接收 `organizationId` |

- `from`/`to` 都不传或都为空：服务端使用当前 **UTC 自然日** `[00:00, 次日 00:00)`；只传一个、格式错误或 `from >= to` 返回业务码 400（HTTP 200）。
- `Instant.parse` 可接受 `Z` 或 RFC 3339 偏移并归一化为 UTC；前端建议统一发送 `Z`，不要依赖非 UTC 偏移。
- 默认 UTC 自然日可能与前端本地时区显示的“今天”不一致；这是待产品决策项（见核对清单 D-02），前端不得自行改口径。
- 教师 `classId`：`0` 或负数在调用课程端口前返回业务码 400（HTTP 200），不因课程范围源状态改变。课程范围源为 `OK` 且正数 `classId` 不在授权范围时返回业务码 403（HTTP 200）；若课程范围源为 `NO_DATA`/`UNAVAILABLE`，服务端无法判定授权，返回对应 `NO_DATA`/`UNAVAILABLE` item（HTTP 200），不返回 403。前端不得把后者当成“有权限”。
- `classId` 过滤作用于教师班级相关指标：`teacher.class.count`、`teacher.student.count`、`teacher.session.running`、`teacher.assignment.pending`、`teacher.grade.pending`。教师合法拥有但未选中的班级待办会被排除；适配器返回授权范围外 `bindingId` 的待办属于契约违约，该指标返回 `UNAVAILABLE`，不计入结果。`teacher.notice.unread` 是本人通知维度，不按 `classId` 过滤。
- 一次请求使用单一总预算（当前配置 2500ms），从请求开始计时，课程范围阶段和后续并行阶段共享；预算耗尽后未派发或未完成的来源返回 `UNAVAILABLE`，已完成来源的结果保留。future 取消只发出中断信号，不保证底层阻塞 I/O 已停止。
- 共享固定线程池和无界队列仍是未关闭风险；生产适配器接入前需完成容量、拒绝策略、隔离和压测评审，本轮未引入熔断框架或大规模线程池改造。
- 端口结果在聚合边界校验：列表含 null、Map 缺 key/值为 null、`SessionStatus`/`GradeTodo`/按绑定的 `AssignmentTodo` 的 `bindingId` 与 key 不一致、`NoticeSummary.recipientUserId` 与请求用户不一致，均使受影响指标为 `UNAVAILABLE`，不返回假零，也不让 NPE 变成整个请求 500；独立通知来源仍按自身结果处理。
- 学生/管理员端点的方法签名不接收 `classId`、`date`、`organizationId`；Spring MVC 默认不会因未知查询参数返回 400，但前端不得发送这些参数。
- 身份由服务端认证上下文取得，前端不传 `userId`/`studentId`。
- 管理员组织范围由服务端 `SecurityUtils.getDeptId()` 取得，前端不传 `organizationId`；为 null 时业务码 403。

## 2. 通用响应

后端沿用若依 `AjaxResult`，通常 HTTP 200 + `code`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "asOf": "2026-10-30T06:00:00.000Z",
    "window": {
      "startsAt": "2026-10-30T00:00:00.000Z",
      "endsAt": "2026-10-31T00:00:00.000Z"
    },
    "items": [
      {
        "metricCode": "teacher.class.count",
        "value": 1,
        "unit": "COUNT",
        "status": "OK",
        "source": "course",
        "sampleCount": 1,
        "actionPath": "/teacher/classes"
      }
    ]
  }
}
```

> 示例中的 `.000Z` 只示意；Java `Instant` 经 Jackson 序列化的小数位可能为 0/3/6/9 位，前端必须用 RFC 3339 解析器，不要硬编码三位毫秒。真实 HTTP 序列化仍待 L3 验证。

`data` 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `asOf` | string | 是 | 服务端计算截止时间，UTC RFC 3339 |
| `window.startsAt` | string | 是 | 时间窗开始，含 |
| `window.endsAt` | string | 是 | 时间窗结束，不含 |
| `items` | array | 是 | 至少一项；空数组不允许 |

`items[]` 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `metricCode` | string | 是 | 指标编码；前端按编码取卡片，不按数组下标 |
| `value` | number \| null | 是 | `OK` 时为真实值（可为 0）；其余状态为 null |
| `unit` | string | 是 | 当前为 `COUNT`；后续可能增加 `PERCENT`/`SCORE` |
| `status` | string | 是 | `OK` / `NO_DATA` / `UNAVAILABLE` |
| `source` | string | 是 | `course`/`classroom`/`assignment`/`grade`/`notice`/`pending_product_confirmation` |
| `sampleCount` | number \| null | 否 | 参与汇总的样本数；不适用为 null |
| `actionPath` | string \| null | 否 | 前端下钻相对路径；无下钻为 null |

## 3. 状态展示要求

| `status` | `value` | 前端展示 | 禁止行为 |
| --- | --- | --- | --- |
| `OK` | 非 null | 显示数值；0 显示为真实 0 | 不要把 0 显示成“暂无数据” |
| `NO_DATA` | null | 显示“暂无数据” | 不要显示 0、空列表或空白卡片 |
| `UNAVAILABLE` | null | 显示“暂不可用，请稍后重试” | 不要显示 0、空页面或上次缓存值 |

- 单 item 失败只影响该卡片；其他 `OK` 卡片照常显示。
- `UNAVAILABLE` 时必须保留卡片位置和指标标题，显示不可用提示；不能整页报错或隐藏卡片。
- 不要用 `items.length === 0` 判断无数据；后端不会返回空 items。
- `actionPath` 为 null 时卡片不可点击；不要拼接猜测路径。
- `asOf` 用于显示“数据截至时间”；`window` 用于显示统计范围。
- 前端可缓存同一响应，但刷新失败时不能把旧值继续标成最新；应显示不可用或最后更新时间。

## 4. 四端点响应示例

以下示例只说明字段和状态，**当前无生产适配器，真实调用会返回 UNAVAILABLE 或 NO_DATA，不会返回这些数值**。

> 指标名不能直接当口径：教师/管理员 `assignment.pending` 当前代码是待提交学生数合计，`student.assignment.due` 是待办记录条数；教师传入 `classId` 时 `assignment.pending` 只统计该教学班对应 binding 的记录；`sampleCount` 在不同指标上分别表示班级数/绑定数/记录数/null；`window` 只传给课堂和作业端口。逐项公式见 `docs/6-01-frontend-review-checklist.md` 第 3 节。

### 4.1 教师教学总览

`GET /education/teacher/overview`

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "asOf": "2026-10-30T06:00:00.000Z",
    "window": {
      "startsAt": "2026-10-30T00:00:00.000Z",
      "endsAt": "2026-10-31T00:00:00.000Z"
    },
    "items": [
      {
        "metricCode": "teacher.class.count",
        "value": 3,
        "unit": "COUNT",
        "status": "OK",
        "source": "course",
        "sampleCount": 3,
        "actionPath": "/teacher/classes"
      },
      {
        "metricCode": "teacher.student.count",
        "value": 86,
        "unit": "COUNT",
        "status": "OK",
        "source": "course",
        "sampleCount": 3,
        "actionPath": "/teacher/classes"
      },
      {
        "metricCode": "teacher.session.running",
        "value": 1,
        "unit": "COUNT",
        "status": "OK",
        "source": "classroom",
        "sampleCount": 3,
        "actionPath": "/teacher/classes"
      },
      {
        "metricCode": "teacher.assignment.pending",
        "value": 12,
        "unit": "COUNT",
        "status": "OK",
        "source": "assignment",
        "sampleCount": 4,
        "actionPath": "/teacher/assignments"
      },
      {
        "metricCode": "teacher.grade.pending",
        "value": 5,
        "unit": "COUNT",
        "status": "OK",
        "source": "grade",
        "sampleCount": 3,
        "actionPath": "/teacher/grades"
      },
      {
        "metricCode": "teacher.notice.unread",
        "value": 4,
        "unit": "COUNT",
        "status": "OK",
        "source": "notice",
        "sampleCount": null,
        "actionPath": "/teacher/notices"
      }
    ]
  }
}
```

### 4.2 学生今日待办

`GET /education/student/today-todos`

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "asOf": "2026-10-30T06:00:00.000Z",
    "window": {
      "startsAt": "2026-10-30T00:00:00.000Z",
      "endsAt": "2026-10-31T00:00:00.000Z"
    },
    "items": [
      {
        "metricCode": "student.class.count",
        "value": 2,
        "unit": "COUNT",
        "status": "OK",
        "source": "course",
        "sampleCount": 2,
        "actionPath": "/student/classes"
      },
      {
        "metricCode": "student.session.active",
        "value": 1,
        "unit": "COUNT",
        "status": "OK",
        "source": "classroom",
        "sampleCount": 2,
        "actionPath": "/student/todos"
      },
      {
        "metricCode": "student.assignment.due",
        "value": 3,
        "unit": "COUNT",
        "status": "OK",
        "source": "assignment",
        "sampleCount": 3,
        "actionPath": "/student/todos"
      },
      {
        "metricCode": "student.notice.unread",
        "value": 0,
        "unit": "COUNT",
        "status": "OK",
        "source": "notice",
        "sampleCount": null,
        "actionPath": "/student/notices"
      }
    ]
  }
}
```

### 4.3 管理员管理总览

`GET /education/admin/overview`

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "asOf": "2026-10-30T06:00:00.000Z",
    "window": {
      "startsAt": "2026-10-30T00:00:00.000Z",
      "endsAt": "2026-10-31T00:00:00.000Z"
    },
    "items": [
      {
        "metricCode": "admin.class.count",
        "value": 12,
        "unit": "COUNT",
        "status": "OK",
        "source": "course",
        "sampleCount": 12,
        "actionPath": "/admin/classes"
      },
      {
        "metricCode": "admin.student.count",
        "value": 420,
        "unit": "COUNT",
        "status": "OK",
        "source": "course",
        "sampleCount": 12,
        "actionPath": "/admin/classes"
      },
      {
        "metricCode": "admin.session.running",
        "value": 2,
        "unit": "COUNT",
        "status": "OK",
        "source": "classroom",
        "sampleCount": 12,
        "actionPath": "/admin/classes"
      },
      {
        "metricCode": "admin.assignment.pending",
        "value": 37,
        "unit": "COUNT",
        "status": "OK",
        "source": "assignment",
        "sampleCount": 12,
        "actionPath": "/admin/assignments"
      },
      {
        "metricCode": "admin.grade.pending",
        "value": 18,
        "unit": "COUNT",
        "status": "OK",
        "source": "grade",
        "sampleCount": 12,
        "actionPath": "/admin/grades"
      }
    ]
  }
}
```

### 4.4 管理员待办事项

`GET /education/admin/todos`

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "asOf": "2026-10-30T06:00:00.000Z",
    "window": {
      "startsAt": "2026-10-30T00:00:00.000Z",
      "endsAt": "2026-10-31T00:00:00.000Z"
    },
    "items": [
      {
        "metricCode": "admin.todo.pending",
        "value": null,
        "unit": "COUNT",
        "status": "NO_DATA",
        "source": "pending_product_confirmation",
        "sampleCount": null,
        "actionPath": null
      }
    ]
  }
}
```

管理员待办来源尚未经产品确认。当前只返回 `NO_DATA`，不得由前端拼造待办或数量；产品确认后再更新本文档和指标编码。

### 4.5 缺适配器/源失败示例

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "asOf": "2026-10-30T06:00:00.000Z",
    "window": {
      "startsAt": "2026-10-30T00:00:00.000Z",
      "endsAt": "2026-10-31T00:00:00.000Z"
    },
    "items": [
      {
        "metricCode": "teacher.notice.unread",
        "value": null,
        "unit": "COUNT",
        "status": "UNAVAILABLE",
        "source": "notice",
        "sampleCount": null,
        "actionPath": "/teacher/notices"
      }
    ]
  }
}
```

缺适配器、超时或单个来源异常时使用 `UNAVAILABLE`，HTTP 仍为 200；前端显示“暂不可用”，不能显示 0 或空页面。

## 5. 错误码

后端当前通常返回 HTTP 200 + `body.code`；前端必须同时兼容 HTTP 200 + 非 200 业务码和真实 HTTP 4xx/5xx。

| 场景 | HTTP 状态码（当前实现） | 业务码 `body.code` | 前端处理 |
| --- | --- | --- | --- |
| 未登录/令牌失效 | 200；网关可能 401 | 401 | 清除登录态，跳登录页 |
| 角色/权限串不匹配 | 200 | 403 | 显示“无权限”，不显示总览数据 |
| 教师班级越权、学生身份缺失、管理员组织范围缺失 | 200 | 403 | 显示“无权查看该范围”，清空旧数据 |
| 查询参数错误（from/to 单个缺失、格式错误、classId 类型错误或非正整数） | 200 | 400 | 显示参数错误；不要重试同样参数 |
| 服务端异常 | 200 | 500 | 显示通用失败提示；保留页面结构 |
| 缺适配器/单源超时 | 200 | 200 + item `UNAVAILABLE` | 该卡片显示“暂不可用”；其他卡片正常 |
| 网关/网络错误 | 0/502/504 | 无 | 显示网络错误；可重试 |

说明：

- 本阶段缺适配器**不返回 503**，而是 HTTP 200 + item `UNAVAILABLE`；如果网关返回 503，按“暂不可用”处理。
- 适配器返回嵌套结果违约时，对应指标为 `UNAVAILABLE`，不是 500，也不是 0；其他独立指标仍按自身结果返回。
- 总览接口不返回 404；教师越权班级在课程范围源可用时返回 403，避免用 404 泄露班级是否存在；课程范围源不可用时按 `NO_DATA/UNAVAILABLE` 返回，见 D-05。
- 任何错误都不要把旧缓存值继续标成最新数据。
- 2026-11-02 代码核对：`@PreAuthorize` 拒绝由若依 `GlobalExceptionHandler` 返回 HTTP 200 + `body.code=403`；认证失败由 `AuthenticationEntryPointImpl` 经 `ServletUtils.renderString` 返回 HTTP 200 + `body.code=401`。真实网关/HTTP 行为仍未验证。

## 6. 权限与联调状态

- 6B 6-03/6-04 分支/PR 尚未在远端仓库出现；本地 `frontend/` 是静态联调壳，未调用总览端点，不能作为 6B 已完成实现。前端字段、状态和路由均未确认。
- 权限串：`education:overview:teacher`、`education:overview:student`、`education:overview:admin`，待第 1 组配置菜单/角色后联调。
- 当前仓库没有生产课程/课堂/任务/成绩/通知适配器；后端通过端口聚合，端口缺失时返回 `UNAVAILABLE`。
- 6-02 通知 PR #18/#20/#21/#22 已合并到 main（`f21f51d55c363738c03ee6f626c7c67b904d6b36`）；但 6-02 未提供 `NoticeSummaryPort` 所需的批量未读汇总契约，适配器仍未接入。当前通知项为 `UNAVAILABLE`。
- 真实 HTTP、登录、角色拦截、审计落库、MySQL 8.0.46 均未验证。
- 当前前端只能按字段和状态对齐，不能把本文示例当作真实联调数据，也不能宣称 6-01 已完成生产联调。

## 7. 待评审与变更

- 路径、查询参数、指标编码、`unit` 枚举、默认时间窗、`actionPath` 仍待评审。
- 管理员待办来源待产品确认。
- 第 2-5 组聚合契约未落地；接入后需重新确认 `source`、`sampleCount`、指标口径和 `window`。
- 6-02 已合并；补充通知未读适配器仍需批量汇总契约、授权方式和调用方式确认，完成后再验证 `UNAVAILABLE` → `OK/NO_DATA` 的状态变化。
- 契约变更需同步更新本文档、`docs/6-01-overview-contract.md` 和前端字段映射。
