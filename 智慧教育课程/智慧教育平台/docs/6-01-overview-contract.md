# 6-01 三端总览聚合契约（草案）

> **草案：路径与口径待评审，供前端 6-03/6-04 提前对齐方向，不得作为正式对接依据。**

- 日期：2026-10-30
- 任务：6-01 / 6A / M0
- 范围：教师教学总览、学生今日待办、管理员管理总览与待办
- 阶段：第一阶段只定义契约、端口、DTO 与失败语义；本阶段不含 Controller，不接任何生产适配器
- 依赖：第 1/2/3/4/5 组服务契约；6-02 通知契约（未合并）

## 1. 范围与非目标

本任务只做聚合：读取各领域已授权的聚合结果，组装为三端总览；不拥有、不直读第 1/2/3/4/5 组的业务表，不复制课程、课堂、作业、成绩或统计明细。

非目标：

- 不做课程、课堂、作业、成绩、通知的写操作。
- 不在本任务重算正式成绩、能力分数或统计口径；指标口径由第 5 组提供，6-01 只消费。
- 不实现生产授权适配器；授权缺失或异常按 `UNAVAILABLE` 处理。
- 不实现前端页面；前端 6-03/6-04 按本草案对齐字段和状态，不把草案当冻结协议。

## 2. 通用约定

### 2.1 身份与权限

- 操作者身份从服务端认证上下文取得，不接受客户端传入 `userId`、`studentId` 冒充。
- 教师只看本人被授权的教学班；学生只看本人有效在班数据；管理员只看获授权组织范围。
- 越权对象按 404/403 的业务约定处理；本阶段无 HTTP 层，权限失败在适配器或服务层抛出业务异常。

### 2.2 时间

- 所有时间使用 UTC、RFC 3339 毫秒精度，例如 `2026-10-30T06:00:00.000Z`。
- 时间窗为左闭右开 `[startsAt, endsAt)`，由服务端确定并在响应的 `window` 返回。
- 客户端不自行推算“今天”或“当前学期”；倒计时仅用于展示。
- 具体默认窗口（当前学期、自然日或自定义）待评审；评审前由服务端配置固定值，响应必须回传实际窗口。

### 2.3 响应包装

未来 Controller 使用若依统一响应体；`data` 为 `OverviewResponse`：

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

### 2.6 批量与空值

- 端口按用户 ID、教学班 ID 或绑定 ID 批量查询；适配器必须一次查询，禁止逐 ID 调用造成 N+1。
- 端口返回的 Map 必须包含所有请求 key；缺 key 视为契约违约，调用方映射为 `UNAVAILABLE`。
- 空 List 表示“该 key 下没有数据/没有待办”，调用方映射为 `NO_DATA`，不是 `UNAVAILABLE`。
- 真实测量值 `0` 用 `OK` + `value=0` 表达；不能用 `NO_DATA` 代替真实 0。

## 3. 端点草案

以下路径、查询参数、指标编码、`actionPath` 均为草案，待评审；评审通过前不得作为正式对接依据。

### 3.1 教师教学总览

`GET /education/teacher/overview`

- 角色：教师
- 查询参数（草案）：`from`、`to`、`classId`（可选，必须在本人授权范围内）
- 建议指标：

| `metricCode` | `unit` | `source` | 含义 |
| --- | --- | --- | --- |
| `teacher.class.count` | `COUNT` | `course` | 本人当前授权教学班数 |
| `teacher.student.count` | `COUNT` | `course` | 授权班内有效学生数 |
| `teacher.session.running` | `COUNT` | `classroom` | 当前进行中课堂数 |
| `teacher.assignment.pending` | `COUNT` | `assignment` | 待批/待确认任务数 |
| `teacher.grade.pending` | `COUNT` | `grade` | 待确认/待发布成绩数 |
| `teacher.notice.unread` | `COUNT` | `notice` | 本人未读通知数 |

- `actionPath` 示例：`/teacher/classes`、`/teacher/classes/{classId}/assignments`、`/teacher/notices`；无下钻时为 `null`。

### 3.2 学生今日待办

`GET /education/student/today-todos`

- 角色：学生
- 查询参数（草案）：`date`（可选；服务端按配置时区解析为 UTC 窗口）
- 建议指标：

| `metricCode` | `unit` | `source` | 含义 |
| --- | --- | --- | --- |
| `student.class.count` | `COUNT` | `course` | 本人当前有效在班数 |
| `student.session.active` | `COUNT` | `classroom` | 当前可加入/进行中的课堂数 |
| `student.assignment.due` | `COUNT` | `assignment` | 今日截止且本人未提交的预习/作业数 |
| `student.notice.unread` | `COUNT` | `notice` | 本人未读通知数 |

- `actionPath` 示例：`/student/todos`、`/student/classes/{classId}`、`/student/notices`；无下钻时为 `null`。

### 3.3 管理员管理总览

`GET /education/admin/overview`

- 角色：管理员
- 查询参数（草案）：`organizationId`（可选；只能查询本人获授权组织）、`from`、`to`
- 建议指标：

| `metricCode` | `unit` | `source` | 含义 |
| --- | --- | --- | --- |
| `admin.class.count` | `COUNT` | `course` | 授权组织内教学班数 |
| `admin.student.count` | `COUNT` | `course` | 授权组织内有效学生数 |
| `admin.session.running` | `COUNT` | `classroom` | 授权组织内进行中课堂数 |
| `admin.assignment.pending` | `COUNT` | `assignment` | 授权组织内待处理任务数 |
| `admin.grade.pending` | `COUNT` | `grade` | 授权组织内待确认/待发布成绩数 |

- 管理员看不到教师私有内容；统计只返回授权组织范围。
- `actionPath` 示例：`/admin/classes`、`/admin/assignments`；无下钻时为 `null`。

### 3.4 管理员待办事项

`GET /education/admin/todos`

- 角色：管理员
- 查询参数（草案）：`organizationId`（可选，受授权范围限制）
- **管理员待办来源：待产品确认。本任务不定义、不自造待办来源。**
- 产品确认前，本端点只允许返回 `NO_DATA`（`source=pending_product_confirmation`）或 `UNAVAILABLE` 项；不得返回虚构的待办数量、标题或下钻路径。
- 产品确认后，再补充来源领域、`metricCode`、权限和 `actionPath`；补充前不实现生产适配器。

## 4. 端口与批量签名

本阶段只定义接口，不实现生产适配器；测试可使用 Mockito/内存替身，生产适配器待第 1/2/3/4/5 组契约到位后接入。

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
- Map 必须覆盖所有请求的绑定 ID；数值为 0 时返回 0，由调用方决定 `OK` 还是 `NO_DATA`。

### 4.5 `NoticeSummaryPort`

```java
Map<Long, NoticeSummary> findUnreadSummaries(Collection<Long> recipientUserIds);
```

- 只返回未读汇总，不返回通知正文或明细。
- **6-02 未合并：本接口只定义签名，不 import 6-02 的任何类；6-02 合并后接适配器。**
- 适配器缺失时调用方必须映射为 `UNAVAILABLE`，不能返回 0 未读冒充。
- Map 必须覆盖所有请求的接收人 ID；0 未读为真实 0，返回 `OK` + `value=0`。

## 5. 失败语义与映射

| 情况 | 映射 | 说明 |
| --- | --- | --- |
| 适配器存在且返回有效数据 | `OK` | `value` 非 null；真实 0 也是 `OK` |
| 适配器存在但返回 null/空集合 | `NO_DATA` | 不写 0、不写空数组 |
| 适配器缺失（注入为 null） | `UNAVAILABLE` | 统一失败语义；不伪造生产适配器 |
| 调用超时 | `UNAVAILABLE` | 任务取消；记录 source 与超时值 |
| 适配器抛异常 | `UNAVAILABLE` | 记录异常类型；不向前端暴露堆栈 |
| 线程被中断 | `UNAVAILABLE` | 恢复中断标记 |
| 执行器拒绝任务 | `UNAVAILABLE` | 不阻塞主流程 |
| Map 缺请求 key | `UNAVAILABLE` | 契约违约，按源失败处理 |

- 超时、异常、缺适配器都收敛到 `UNAVAILABLE`，由前端提示“暂不可用”；不得降级成 0 或空列表。
- 单项失败不影响其他项；响应中保留失败项，状态为 `UNAVAILABLE`。
- 失败原因写服务端日志，响应只给状态，不返回异常文本。

## 6. 6-02 边界

- 6-02 的 PR #18/#20/#21/#22 未合并；本任务不从 6-02 分支拉代码，不 import 6-02 的类。
- `NoticeSummaryPort` 只定义接口；6-02 合并后新增适配器，读取 6-02 已授权的未读汇总契约。
- 本任务不写 `edu_user_notice`，不扩展 `NoticeKind`，不复制 6-02 的通知表。
- 6-02 合并前，通知项统一返回 `UNAVAILABLE`；不得用 0 未读或空列表冒充。

## 7. 待评审与待联调

- 四个端点路径、查询参数、指标编码、`unit` 枚举、`actionPath` 规则待评审。
- 默认时间窗（当前学期/自然日/自定义）与业务时区待评审。
- 管理员待办来源待产品确认；确认前不实现、不自造。
- 第 1/2/3/4/5 组聚合契约未落地；本阶段只有端口和测试替身。
- 生产授权适配器缺失；授权异常必须 `UNAVAILABLE` 或按权限拒绝，不能放行。
- 6-02 未合并；`NoticeSummaryPort` 适配器待 6-02 合并后接入。
- 真实 HTTP、登录、角色拦截、审计落库未验证；本阶段无 Controller，不做 MockMvc。
