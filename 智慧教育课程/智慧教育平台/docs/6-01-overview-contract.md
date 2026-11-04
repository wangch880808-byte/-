# 6-01 三端总览聚合契约（草案）

> **草案：路径与口径待评审，供前端 6-03/6-04 提前对齐方向，不得作为正式对接依据。**

- 日期：2026-10-30；2026-11-04 同步 #24 当前实现，仍为草案
- 任务：6-01 / 6A / M0
- 范围：教师教学总览、学生今日待办、管理员管理总览与待办
- 阶段：`#23` 第一阶段只定义契约、端口、DTO 与失败语义；本分支不含 Controller，不接任何生产适配器。四端点由后续 stacked `#24` 实现；本文只记录其当前行为，不能据此认为 Controller 已进入 `#23` 或 `main`
- 依赖：第 1/2/3/4/5 组服务契约；6-02 的 #18/#20/#21/#22 已合并到 main（`f21f51d55c363738c03ee6f626c7c67b904d6b36`），但通知汇总生产适配器尚未接入

## 0. 阶段与证据边界

| 版本 | 交付范围 | 当前状态 |
| --- | --- | --- |
| `#23` | 合同、五个端口、DTO、`OverviewSourceGuard` 第一阶段失败收敛 | Draft；不含 Controller、Service、生产适配器 |
| `#24` | 四端点 Controller/Service、身份与异常 advice、聚合测试、前端 handoff/checklist | Draft；stacked 在 #23 之上，未进入 main |
| `main` | `f21f51d...`，含 6-02 已合并代码 | 不含 6-01 Controller |

本文以 `#24` 当前可核验代码和 `docs/6-01-frontend-api-handoff.md` v1.3 为行为参考。`#24` 新增的请求总预算、来源提交/排队检查、嵌套结果校验属于后续实现；`#23` 的 `OverviewSourceGuard` 不包含这些新增重载，不能把 #24 的增强倒灌为 #23 已实现。

## 1. 范围与非目标

本任务只做聚合：读取各领域已授权的聚合结果，组装为三端总览；不拥有、不直读第 1/2/3/4/5 组的业务表，不复制课程、课堂、作业、成绩或统计明细。

非目标：

- 不做课程、课堂、作业、成绩、通知的写操作。
- 不在本任务重算正式成绩、能力分数或统计口径；指标口径由第 5 组提供，6-01 只消费。
- 不实现生产授权适配器；授权缺失或异常按 `UNAVAILABLE` 处理。
- 不实现前端页面；前端 6-03/6-04 按本草案对齐字段和状态，不把草案当冻结协议。

## 2. 通用约定

### 2.1 身份与权限

- `#23` 合同要求操作者身份从服务端认证上下文取得，不接受客户端传入 `userId`、`studentId` 冒充。
- `#24` 当前实现：教师/学生用户 ID 来自 `SecurityUtils.getUserId()`；管理员组织 ID 来自 `SecurityUtils.getDeptId()`，不读取客户端 `organizationId`。`deptId` 与业务组织范围的映射仍待第 1/2 组确认。
- 教师只看本人被授权的教学班；学生只看本人有效在班数据；管理员只看获授权组织范围。
- 教师 `classId` 仅教师端点接收，必须为正整数；非正数返回业务 400。合法但不在授权范围时返回 403；课程范围源 `NO_DATA/UNAVAILABLE` 时返回对应三态 item，不返回 403，前端不能把后者理解为“有权限”。
- `#23` 不含 HTTP 层，权限失败由适配器或服务层抛业务异常；`#24` 由 Controller、局部 advice 和若依安全处理链映射。权限串 `education:overview:teacher/student/admin` 的真实菜单/角色配置和审计仍未完成。

### 2.2 时间

- 所有时间使用 UTC、RFC 3339 毫秒精度，例如 `2026-10-30T06:00:00.000Z`。
- 时间窗为左闭右开 `[startsAt, endsAt)`，由服务端确定并在响应的 `window` 返回。
- `#24` 当前默认窗口是服务端 UTC 自然日 `[当天 00:00, 次日 00:00)`；`from`/`to` 可选但必须成对，只传一个、格式错误或 `from >= to` 返回业务 400。`Instant.parse` 接受 `Z` 或 RFC 3339 偏移并归一化为 UTC。
- 客户端不自行推算“今天”或“当前学期”；倒计时仅用于展示。默认窗口是否改为业务时区/当前学期仍待产品确认；前端必须展示服务端返回的 `window`。
- `window` 当前只实际传给课堂和作业端口；班级、学生、成绩、通知属于当前快照，管理员待办无来源，不能宣称所有指标同窗统计。

### 2.3 响应包装

`#24` 当前 Controller 使用若依统一响应体；`data` 为 `OverviewResponse`。`#23` 本身不含 Controller：

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
      }
    ]
  }
}
```

### 2.4 聚合项字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `metricCode` | string | 是 | 稳定指标编码；同一指标跨端复用同一编码 |
| `value` | number \| null | 是 | 指标值；`OK` 时为真实值（可为 0），`NO_DATA`/`UNAVAILABLE` 时必须为 `null` |
| `unit` | string | 是 | `COUNT`、`PERCENT`、`SCORE` 等；具体枚举待评审 |
| `status` | string | 是 | `OK` / `NO_DATA` / `UNAVAILABLE` |
| `source` | string | 是 | 来源领域，如 `course`、`classroom`、`assignment`、`grade`、`notice`、`identity` |
| `sampleCount` | integer \| null | 否 | 参与计算的样本数；不适用时为 `null`，不能用来伪装“无数据” |
| `actionPath` | string \| null | 否 | 前端可下钻的相对路径；无下钻时为 `null` |

### 2.5 状态语义

| 状态 | `value` | `sampleCount` | 含义 | 前端处理 |
| --- | --- | --- | --- | --- |
| `OK` | 非 null | 可空 | 数据源可用且有有效结果；`0` 是真实测量值 | 显示数值；0 不显示为“暂无数据” |
| `NO_DATA` | null | 0 或 null | 数据源可用，但没有证据、没有样本或分母为 0 | 显示“暂无数据”；不得写成 0 |
| `UNAVAILABLE` | null | null | 适配器缺失、超时、异常或契约违约；不能判断是否有数据 | 显示“暂不可用/稍后重试”；不得写成 0 或空列表 |

补充规则：

- 无数据不写 0；不能用空数组冒充 `NO_DATA` 或 `UNAVAILABLE`。
- 单个来源失败只影响对应项，其他项仍可返回 `OK`；整体响应不因单项失败而全部失败。
- `items` 不得为空；没有可展示项时也必须返回至少一个 `NO_DATA` 或 `UNAVAILABLE` 项。
- `UNAVAILABLE` 的具体原因只写日志，不向前端暴露内部异常、SQL 或堆栈。

三态不能把所有 null 混成一种结果：

| 输入/情况 | 当前映射 | 层级 |
| --- | --- | --- |
| 真实计数 0 | `OK` + `value=0` | 聚合消费层 |
| 顶层结果 null/空 Collection/空 Map（SourceGuard 默认） | `NO_DATA` | `#23` SourceGuard |
| 请求 key 缺失 | `UNAVAILABLE` | 聚合消费层契约违约 |
| Map 对应 value 为 null | `UNAVAILABLE` | 聚合消费层契约违约 |
| 列表含 null 元素 | `UNAVAILABLE` | `#24` 聚合消费层新增校验 |
| `SessionStatus`/`GradeTodo`/按绑定的 `AssignmentTodo` 的 `bindingId` 与 key 不一致 | `UNAVAILABLE` | `#24` 聚合消费层新增校验 |
| `NoticeSummary.recipientUserId` 与请求接收人不一致 | `UNAVAILABLE` | `#24` 聚合消费层新增校验 |
| 适配器缺失（注入 null） | `UNAVAILABLE` | SourceGuard |
| 单源超时、异常、中断、执行器拒绝 | `UNAVAILABLE` | SourceGuard |
| 请求总预算耗尽、排队后执行前预算耗尽 | `UNAVAILABLE` | `#24` 新增预算能力 |

`#24` 当前将空 List 视为该 key 下没有数据并映射 `NO_DATA`；缺 key 或 value 为 null 属于契约违约，映射 `UNAVAILABLE`。`#23` 的 SourceGuard 默认空集合映射 `NO_DATA`，但没有 #24 的嵌套校验和预算重载；不能把两者写成同一种实现。

### 2.6 批量与空值

- 端口按用户 ID、教学班 ID 或绑定 ID 批量查询；适配器必须一次查询，禁止逐 ID 调用造成 N+1。
- 端口返回的 Map 必须包含所有请求 key；缺 key 视为契约违约，调用方映射为 `UNAVAILABLE`。
- 空 List 表示“该 key 下没有数据/没有待办”，调用方映射为 `NO_DATA`，不是 `UNAVAILABLE`。
- Map 的 value 不得为 null；列表元素不得为 null。`#24` 聚合消费层还会校验对象内 `bindingId`/`recipientUserId` 与所属 key 一致；不一致映射 `UNAVAILABLE`。
- 真实测量值 `0` 用 `OK` + `value=0` 表达；不能用 `NO_DATA` 代替真实 0。

## 3. 端点与当前实现

以下路径、参数、指标编码和 `actionPath` 仍为草案；本节记录 `#24` 当前行为，不代表产品或 6B 已冻结。`#23` 本身不含 Controller。

### 3.1 教师教学总览

`GET /education/teacher/overview`

- 角色：教师。
- 查询参数：`from`、`to`、`classId`。

| 参数 | 位置 | 必填 | 当前行为 |
| --- | --- | --- | --- |
| `from`、`to` | query | 否 | 必须成对；RFC 3339；左闭右开；默认当前 UTC 自然日 |
| `classId` | query | 否 | `Long`；必须为正整数，非正数返回业务 400；仅教师端点 |

- `classId` 当前只筛教师班级相关指标；教师合法但未选中的班级被排除；适配器返回授权范围外 `bindingId` 的待办属于契约违约，对应指标 `UNAVAILABLE`。课程范围源 `NO_DATA/UNAVAILABLE` 时不返回 403，而返回对应三态 item。
- `teacher.notice.unread` 是本人通知维度，不随 `classId` 筛选。

| `metricCode` | 当前公式/行为（#24） | 建议产品含义（待确认） | `unit` | `window` | `sampleCount` | 三态备注 | `actionPath` 候选 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `teacher.class.count` | 授权课程范围条数 `scopes.size()`，未按 `teachingClassId` 去重 | 本人授权教学班数 | COUNT | 否（快照） | 范围条数 | 空范围 `NO_DATA`；非空 `OK ≥1` | `/teacher/classes` |
| `teacher.student.count` | 各范围 `studentCount` 求和，未跨班去重；任一 null → `NO_DATA` | 授权班内有效学生数 | COUNT | 否（快照） | 范围条数 | 空范围或任一 null `NO_DATA`；有范围且全非 null `OK`（可 0） | `/teacher/classes` |
| `teacher.session.running` | 绑定范围内 `RUNNING` 或 `PAUSED` 的会话数 | 当前进行中课堂数 | COUNT | 是 | 去重绑定数 | 无绑定/窗口内无会话 `NO_DATA`；缺 key 或绑定不一致 `UNAVAILABLE`；无活跃但存在会话 `OK 0` | `/teacher/classes` |
| `teacher.assignment.pending` | `classId` 选中 binding 过滤后，各 `AssignmentTodo` 的 `max(0,targetCount-submittedCount)` 求和；是待提交学生数，不是任务条数 | 待批/待确认任务数（待产品确认） | COUNT | 是 | 过滤后待办记录数 | 无记录/选中班无记录 `NO_DATA`；越权 binding 或列表 null `UNAVAILABLE`；合计 0 `OK 0` | `/teacher/assignments` |
| `teacher.grade.pending` | 各绑定 `pendingGradeCount + pendingPublishCount` 求和；两计数是否互斥未定义 | 待确认/待发布成绩数 | COUNT | 否（快照） | 去重绑定数 | 无绑定 `NO_DATA`；缺 key/绑定不一致 `UNAVAILABLE`；绑定存在且合计 0 `OK 0` | `/teacher/grades` |
| `teacher.notice.unread` | `NoticeSummary.unreadCount`；0 是真实未读 0 | 本人未读通知数 | COUNT | 否（快照） | null | 适配器缺失/摘要缺失/接收人不一致 `UNAVAILABLE`；0 `OK 0` | `/teacher/notices` |

### 3.2 学生今日待办

`GET /education/student/today-todos`

- 角色：学生。
- 查询参数：`from`、`to`；实际不接收 `date`、`classId`、`organizationId`。
- `from/to` 规则同上；默认 UTC 自然日仍是待产品确认的默认窗口。

| `metricCode` | 当前公式/行为（#24） | 建议产品含义（待确认） | `unit` | `window` | `sampleCount` | 三态备注 | `actionPath` 候选 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `student.class.count` | 有效在班范围条数 `scopes.size()`，未按教学班去重 | 本人当前有效在班数 | COUNT | 否（快照） | 范围条数 | 空范围 `NO_DATA`；非空 `OK ≥1` | `/student/classes` |
| `student.session.active` | 绑定范围内 `RUNNING` 或 `PAUSED` 的会话数 | 当前可加入/进行中的课堂数 | COUNT | 是 | 去重绑定数 | 无绑定/无会话 `NO_DATA`；缺 key/绑定不一致 `UNAVAILABLE`；无活跃 `OK 0` | `/student/todos` |
| `student.assignment.due` | 经授权 binding 校验后的 `AssignmentTodo` 记录条数；不是 `pendingCount` 求和 | 今日截止且本人未提交的预习/作业数 | COUNT | 是 | 过滤后待办记录数 | 空 List `NO_DATA`；缺 key/越权 binding/列表 null `UNAVAILABLE`；非空 `OK ≥1` | `/student/todos` |
| `student.notice.unread` | `NoticeSummary.unreadCount`；0 是真实未读 0 | 本人未读通知数 | COUNT | 否（快照） | null | 适配器缺失/摘要缺失/接收人不一致 `UNAVAILABLE`；0 `OK 0` | `/student/notices` |

说明：`student.assignment.due` 的“今日截止、本人未提交”具体入选规则由第 5 组端口契约提供；本聚合层只按端口返回记录计数，不能从当前代码单独证明过滤条件。

### 3.3 管理员管理总览

`GET /education/admin/overview`

- 角色：管理员。
- 查询参数：`from`、`to`；实际不接收客户端 `organizationId`。
- 组织范围当前来自服务端 `SecurityUtils.getDeptId()`；`deptId` 与业务组织范围的映射仍待第 1/2 组确认。

| `metricCode` | 当前公式/行为（#24） | 建议产品含义（待确认） | `unit` | `window` | `sampleCount` | 三态备注 | `actionPath` 候选 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `admin.class.count` | 组织授权范围条数 `scopes.size()`，未按教学班去重 | 授权组织内教学班数 | COUNT | 否（快照） | 范围条数 | 空范围 `NO_DATA`；非空 `OK ≥1` | `/admin/classes` |
| `admin.student.count` | 各范围 `studentCount` 求和，未跨班去重；任一 null → `NO_DATA` | 授权组织内有效学生数 | COUNT | 否（快照） | 范围条数 | 空范围或任一 null `NO_DATA`；有范围且全非 null `OK`（可 0） | `/admin/classes` |
| `admin.session.running` | 绑定范围内 `RUNNING` 或 `PAUSED` 的会话数 | 授权组织内进行中课堂数 | COUNT | 是 | 去重绑定数 | 无绑定/无会话 `NO_DATA`；缺 key/绑定不一致 `UNAVAILABLE`；无活跃 `OK 0` | `/admin/classes` |
| `admin.assignment.pending` | 各绑定 `AssignmentTodo.pendingCount` 求和；是待提交学生数，不是任务条数 | 授权组织内待处理任务数 | COUNT | 是 | 去重绑定数 | 无记录 `NO_DATA`；缺 key/绑定不一致 `UNAVAILABLE`；合计 0 `OK 0` | `/admin/assignments` |
| `admin.grade.pending` | 各绑定 `pendingGradeCount + pendingPublishCount` 求和；互斥性未定义 | 授权组织内待确认/待发布成绩数 | COUNT | 否（快照） | 去重绑定数 | 无绑定 `NO_DATA`；缺 key/绑定不一致 `UNAVAILABLE`；合计 0 `OK 0` | `/admin/grades` |

- 管理员看不到教师私有内容；统计只返回授权组织范围。组织范围、去重和成绩互斥仍需外部确认。

### 3.4 管理员待办事项

`GET /education/admin/todos`

- 角色：管理员。
- 查询参数：`from`、`to`；实际不接收客户端 `organizationId`。
- 当前固定返回一个 `admin.todo.pending`：
  - `status=NO_DATA`
  - `value=null`
  - `source=pending_product_confirmation`
  - `sampleCount=null`
  - `actionPath=null`
- 这不是“0 条”，也不是“全部处理完成”。管理员待办来源、处理人、权限和状态机仍待产品确认；确认前不实现生产适配器。

### 3.5 显示与路由

- `asOf` 当前是响应时刻，不是每个来源的截止时间；`NoticeSummary.asOf` 当前未用于聚合响应。
- `sampleCount` 按指标分别表示范围条数、绑定数、记录数或 null，不是统一分母。
- `actionPath` 只是后端候选路径，尚未取得 6B 路由表；前端未确认前不得宣称可跳转。
- 所有 `unit` 当前为 `COUNT`；不宣称 PERCENT/SCORE 已实现。

## 4. 端口与批量签名

本阶段只定义接口，不实现生产适配器；测试可使用 Mockito/内存替身，生产适配器待第 1/2/3/4/5 组契约到位后接入。`#24` 使用相同业务端口签名；#24 的来源预算检查、排队后检查和嵌套结果校验属于后续聚合消费层实现，不在 `#23` 的 `OverviewSourceGuard` 中，不能倒灌为 #23 已实现。

### 4.1 `CourseScopePort`

```java
Map<Long, List<CourseScope>> findTeacherScopes(Collection<Long> teacherUserIds);
Map<Long, List<CourseScope>> findStudentScopes(Collection<Long> studentUserIds);
List<CourseScope> findOrganizationScopes(Long organizationId);
```

- 批量按用户 ID 查询；管理员按组织一次查询。
- Map 必须覆盖所有请求 key；空 List 表示该用户没有授权教学班。
- 不返回学生名单明细，只返回聚合所需的班级/绑定范围。

### 4.2 `SessionStatusPort`

```java
Map<Long, List<SessionStatus>> findSessions(Collection<Long> bindingIds, OverviewWindow window);
```

- 按绑定 ID 批量查询时间窗内课堂；适配器一次查询。
- Map 必须覆盖所有请求的绑定 ID；空 List 表示窗口内没有课堂。

### 4.3 `AssignmentTodoPort`

```java
Map<Long, List<AssignmentTodo>> findStudentTodos(Collection<Long> studentUserIds, OverviewWindow window);
Map<Long, List<AssignmentTodo>> findTeacherTodos(Collection<Long> teacherUserIds, OverviewWindow window);
Map<Long, List<AssignmentTodo>> findBindingTodos(Collection<Long> bindingIds, OverviewWindow window);
```

- 按学生、教师或绑定批量查询；适配器不得逐用户/逐班查询。
- Map 必须覆盖所有请求 key；空 List 表示没有待办。

### 4.4 `GradeTodoPort`

```java
Map<Long, GradeTodo> findPendingGrades(Collection<Long> bindingIds);
```

- 按绑定批量查询待确认/待发布成绩汇总。
- Map 必须覆盖所有请求的绑定 ID。
- 有效成绩汇总的真实零为 `OK` + `value=0`；无数据、契约违约分别按既定 `NO_DATA`/`UNAVAILABLE` 规则处理，不能由调用方把真实零改写成 `NO_DATA`。

### 4.5 `NoticeSummaryPort`

```java
Map<Long, NoticeSummary> findUnreadSummaries(Collection<Long> recipientUserIds);
```

- 只返回未读汇总，不返回通知正文或明细。
- **6-02 已合并，但未提供本端口所需的批量未读汇总契约：本接口仍只定义签名，不直接查询 `edu_user_notice`，`NoticeSummaryPort` 不 import 6-02 通知类；适配器待契约、授权和调用方式确认后接入。**
- 适配器缺失时调用方必须映射为 `UNAVAILABLE`，不能返回 0 未读冒充。
- Map 必须覆盖所有请求的接收人 ID；0 未读为真实 0，返回 `OK` + `value=0`。

## 5. 失败语义与映射

| 情况 | 当前映射 | 说明 |
| --- | --- | --- |
| 适配器存在且返回有效数据 | `OK` | `value` 非 null；真实 0 也是 `OK` |
| 顶层结果 null/空 List/空 Map（SourceGuard 默认） | `NO_DATA` | 不写 0、不写空数组；`#24` 批量来源由消费层进一步判断 key/空 List |
| Map 缺请求 key | `UNAVAILABLE` | 契约违约，按源失败处理 |
| Map 对应 value 为 null | `UNAVAILABLE` | 契约违约；不能当作空数据 |
| 列表元素为 null | `UNAVAILABLE` | `#24` 聚合消费层校验；不能让 NPE 变成 500 |
| `SessionStatus`/`GradeTodo`/按绑定的 `AssignmentTodo` 的 `bindingId` 与 key 不一致 | `UNAVAILABLE` | `#24` 聚合消费层契约校验 |
| `NoticeSummary.recipientUserId` 与请求接收人不一致 | `UNAVAILABLE` | `#24` 聚合消费层契约校验，避免展示他人未读数 |
| 适配器缺失（注入为 null） | `UNAVAILABLE` | 统一失败语义；不伪造生产适配器 |
| 调用超时 | `UNAVAILABLE` | 任务取消；记录 source 与超时值 |
| 适配器抛异常 | `UNAVAILABLE` | 记录异常类型；不向前端暴露堆栈 |
| 线程被中断 | `UNAVAILABLE` | 恢复中断标记 |
| 执行器拒绝任务 | `UNAVAILABLE` | 不阻塞主流程 |
| `#24` 请求总预算耗尽/排队后执行前预算耗尽 | `UNAVAILABLE` | #24 后续实现；不属于 #23 SourceGuard |
| 管理员待办来源未确认 | `NO_DATA` | `value=null`、`actionPath=null`；不是 0 条或全部完成 |

- `#23` SourceGuard 默认把顶层空集合映射 `NO_DATA`；`#24` 聚合消费层另外校验缺 key、value null、嵌套 null 和对象 ID 一致性，违约映射 `UNAVAILABLE`。两者不能混写成同一种规则。
- 超时、异常、缺适配器都收敛到 `UNAVAILABLE`，由前端提示“暂不可用”；不得降级成 0 或空列表。
- 单项失败不影响其他项；响应中保留失败项，状态为 `UNAVAILABLE`。独立通知来源仍按自身结果返回。
- 失败原因写服务端日志，响应只给状态，不返回异常文本。

## 6. 6-02 边界

- 6-02 的 PR #18/#20/#21/#22 已合并到 main（`f21f51d55c363738c03ee6f626c7c67b904d6b36`）；本任务不直接查询 6-02 私有表。
- `NoticeSummaryPort` 只定义接口；6-02 已合并但未提供批量未读汇总契约，适配器仍需另行接入。
- 本任务不写 `edu_user_notice`，不扩展 `NoticeKind`，不复制 6-02 的通知表；仅复用公共 `EducationException` 作为异常基类。
- 通知汇总适配器未接入前，通知项统一返回 `UNAVAILABLE`；不得用 0 未读或空列表冒充。

## 7. 待评审与待联调

- 四个端点路径、查询参数、指标编码、`unit` 枚举、`actionPath` 规则仍待评审；本文记录的是 `#24` 当前行为，不是冻结协议。
- 默认时间窗（当前学期/自然日/自定义）与业务时区待评审；当前实现是 UTC 自然日。
- `class.count` 当前按授权范围条数计数，是否按教学班去重待确认；不能把范围条数直接称为去重教学班数。
- `student.count` 当前累加各范围人数，不能宣称跨班去重；不读取学生明细或他组私有表补算。
- `teacher.assignment.pending` / `admin.assignment.pending`：当前为各任务待提交人数合计（`max(0,targetCount-submittedCount)` 或绑定维度 `pendingCount` 求和），不能称为跨任务去重人数，也不是待批任务条数；卡片名称和产品口径待产品/第 5 组确认。
- `student.assignment.due`：当前为端口返回的待办记录条数，不是 `pendingCount` 求和；具体“今日截止、本人未提交”入选规则依赖第 5 组端口契约，卡片名称待确认。
- `PAUSED` 当前计入活跃；成绩 `pendingGradeCount + pendingPublishCount` 当前直接相加，互斥性待确认。
- `sampleCount` 按指标分别表示范围条数、绑定数、记录数或 null；`window` 只传给课堂/作业；`asOf` 当前为响应时刻。
- 管理员待办来源、处理人、权限和状态机待产品确认；确认前固定 `NO_DATA`，不自造。
- `deptId` 与业务组织范围映射待第 1/2 组确认。
- 第 1/2/3/4/5 组聚合契约未落地；生产授权适配器缺失，授权异常必须 `UNAVAILABLE` 或按权限拒绝，不能放行。
- 6-02 已合并，但 `NoticeSummaryPort` 仍缺批量未读汇总契约、授权方式和调用方式确认；适配器未接入。
- `#24` 的请求预算/嵌套校验属于后续实现，`#23` `OverviewSourceGuard` 未包含其新增重载；真实 HTTP、登录、角色拦截、审计落库未验证。
