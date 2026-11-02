# 6-01 三端总览前后端逐项核对清单

> 状态：**核对中，不是联调完成，也不是前后端评审通过。**
>
> 核对日期：2026-11-02
>
> 范围：第六组后端 6A 的 6-01 四端点，与 6B 前端 6-03/6-04 的接口/字段/状态/路由对齐。
>
> 依据：`docs/6-01-overview-contract.md`、`docs/6-01-frontend-api-handoff.md`、`feature/6-01-overview-api@22898ae27a90779709b0c29b846181aa7054e0b0` 的实际源码与测试。
>
> 本文只记录核对事实和待确认项。未取得 6B 可核验版本，因此**不写“前后端已确认”，不写“已完成联调”**。

## 0. 证据层级与结论边界

| 层级 | 含义 | 本文用法 |
| --- | --- | --- |
| L0 | 文档/口头描述，未对代码或运行实例核对 | 仅作为待核对输入 |
| L1 | 静态源码、DTO、配置、现有文档 | 后端事实的主要证据 |
| L2 | MockMvc/Mockito/单元测试等隔离测试 | 只证明隔离行为，不证明真实 HTTP/登录/权限/数据库 |
| L3 | 真实 HTTP、真实登录、真实角色拦截、真实数据/数据库 | 本轮未完成 |

“代码看起来一致”与“6B 已确认”分开记录；“6B 已确认”与“真实联调通过”也分开记录。

## 1. 版本与评审状态

### 1.1 版本

| 项目 | 实际版本/引用 | 证据 | 代码核对状态 | 6A 确认 | 6B 确认 | 真实联调 |
| --- | --- | --- | --- | --- | --- | --- |
| 后端 6-01 API | `feature/6-01-overview-api@22898ae27a90779709b0c29b846181aa7054e0b0`，PR #24，base `feature/6-01-overview-contract@3ce2451cffc0b669844b11a00e27d2990f011464` | `git rev-parse` / `git ls-remote`；2026-11-02 核对 | 已做静态核对（L1）；现有测试源码已读（L2 用例，未重跑） | 待确认（不代签） | 未取得 | 未完成 |
| 后端契约分支 | `feature/6-01-overview-contract@3ce2451cffc0b669844b11a00e27d2990f011464`，PR #23 | 远端引用；契约文档 | 已读契约，未改 #23 | 待确认 | 不适用 | 未完成 |
| 6B 前端 6-03/6-04 | **未取得可核验版本** | 远端无 6-03/6-04 分支/PR；本地静态壳无总览调用；第 2 组 Vue/`feat/admin` 不是 6B 实现 | 无法核对 | 待确认 | 待 6B 提供 | 未完成 |
| 交接文档 | `docs/6-01-frontend-api-handoff.md` v1.1（2026-11-02 核对更正），位于 #24 分支 | 文档正文与本次修订 | 已核对并修订参数/时间/错误说明 | 待确认 | 待 6B | 不适用 |
| main 基线 | `f21f51d55c363738c03ee6f626c7c67b904d6b36` | `git ls-remote origin refs/heads/main` | 已核对 | 不适用 | 不适用 | 未完成 |

### 1.2 版本获取与前端检索证据

- 2026-11-02 `git fetch origin --prune` 成功；远端 `main=f21f51d...`、#23 `3ce2451...`、#24 `22898ae...` 与任务基线一致。
- GitHub API 查询：远端分支 28 个、PR（`state=all`）29 个；没有 `6-03`/`6-04` 命名的分支或 PR。
- 本地 `frontend/` 是静态联调壳：`frontend/README.md` 明确“内容区仍待实现”“尚未接入若依 Vue 前端”；`role-home.js` 只做登录/角色/菜单壳，不调用总览端点。
- 搜索过的非 6B 外壳：PR #26（第 2 组 `feat/course-2-03-2-04-teaching-pages`）文件清单无总览端点；`feature/login-role-style`、`feat/admin`、`liu`、`qln`、`six`、`hzt1254-patch-1`、`feature-demo` 的树中也未发现 overview/todo 命名文件。**这些都不作为 6B 已完成实现。**
- 结论：前端侧只能记录“未取得可核验版本”，不能填“字段一致”“页面已接”“路由可用”。

### 1.3 确认记录

| 角色 | 姓名/账号 | 日期 | 核对范围 | 结论 |
| --- | --- | --- | --- | --- |
| 6A | 待填 | 待填 | 后端四端点/字段/错误语义 | 待确认（不代签） |
| 6B | 待填 | 待填 | 6-03/6-04 页面、API、字段、状态、路由 | 待确认 |
| 负责人/产品 | 待填 | 待填 | 默认时间窗、指标口径、管理员待办来源 | 待确认 |

## 2. 四端点矩阵

> 前端“消费页面”均为候选描述；未取得 6B 版本，不能确认页面名称或路由。

| 端点 | 消费页面（待 6B 确认） | 方法/路径 | 参数 | 权限串 | 响应包装 | 后端证据 | 前端证据 | 差异/待确认 | 处理建议 | 确认状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 教师教学总览 | 教师教学总览（6-03 候选） | `GET /education/teacher/overview` | `from`、`to`、`classId` | `education:overview:teacher` | HTTP 200；`AjaxResult`：`code/msg/data`，`data=OverviewResponse` | `OverviewController.java:59-68`；`OverviewQueryService.java:153-213` | 未取得 | D-01、D-02、D-05、D-09、D-10、D-11、D-12、D-13 | 6B 提供版本后逐项核对；不要在无证据时标一致 | 后端 L1 已核；6B 待确认 |
| 学生今日待办 | 学生今日待办（6-04 候选） | `GET /education/student/today-todos` | `from`、`to` | `education:overview:student` | 同上 | `OverviewController.java:77-85`；`OverviewQueryService.java:224-263` | 未取得 | D-01、D-02、D-03、D-09、D-10、D-11、D-12、D-13 | 实际不接收 `date`/`classId`；6B 按 `from/to` 对齐或提出契约变更 | 后端 L1 已核；6B 待确认 |
| 管理员管理总览 | 管理员管理总览（6-03 候选） | `GET /education/admin/overview` | `from`、`to` | `education:overview:admin` | 同上 | `OverviewController.java:94-102`；`OverviewQueryService.java:275-316` | 未取得 | D-01、D-02、D-04、D-09、D-10、D-11、D-12、D-13、D-14 | 实际不接收 `organizationId`；组织来自服务端 `SecurityUtils.getDeptId()` | 后端 L1 已核；6B 待确认 |
| 管理员待办事项 | 管理员待办事项（6-04 候选） | `GET /education/admin/todos` | `from`、`to` | `education:overview:admin` | 同上 | `OverviewController.java:113-121`；`OverviewQueryService.java:331-337` | 未取得 | D-01、D-02、D-04、D-10、D-15 | 当前只返回 `admin.todo.pending` 的 `NO_DATA`；禁止前端显示 0/全部完成 | 后端 L1 已核；6B 待确认 |

通用差异 D-06（sampleCount）、D-07（window）、D-08（asOf）、D-09（时间序列化）适用于全部端点；行内只列端点特有或最直接相关的差异。

### 2.1 参数与时间窗细则

| 检查项 | 实际后端行为 | 证据 | 结论/待确认 |
| --- | --- | --- | --- |
| `from`/`to` 成对 | 都缺省/都空白 → 默认窗口；只传一个 → `OverviewValidationException`（400）；都传 → 解析后校验 `from < to` | `OverviewWindowFactory.java:46-82`；`OverviewWindowFactoryTest.java:40-52`；`OverviewWindowTest.java:26-40` | 成对和顺序已核；前端必须成对发送 |
| 时间格式 | `Instant.parse` 接受 `Z` 或 RFC 3339 偏移（JDK 17 实测 `+08:00` 归一化为 UTC）；不接受非 RFC 3339 字符串 | `OverviewWindowFactory.java:72-82`；JDK 17 `Instant.parse` 行为核对 | 建议前端统一发 `Z`；不依赖偏移格式 |
| 左闭右开 | DTO/Javadoc 规定 `[startsAt, endsAt)`；服务端只把窗口传给课堂/作业端口，不能从聚合层验证下游适配器是否遵守 | `OverviewWindow.java:8-32`；`OverviewQueryService.java:180-193,239-247,290-303` | 契约语义已记录；下游适配器未接入，无法 L3 验证 |
| 默认窗口 | 当前按服务端 UTC 自然日：`LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC)` 到次日 | `OverviewWindowFactory.java:66-70`；`OverviewWindowFactoryTest.java:21-28` | 与前端本地“今天”可能不一致，D-02 待产品决策 |
| `classId` 支持端点 | 仅教师端点接收，类型 `Long`；学生/管理员方法签名不接收 | `OverviewController.java:61-63,79-80,96-97,115-116` | 前端不得向学生/管理员发送 `classId`；未知参数不会触发 400 |
| `classId` 越权 | 课程范围源为 `OK` 且 `classId` 不在授权范围 → 403；课程范围源为 `NO_DATA/UNAVAILABLE` → 返回对应 `NO_DATA/UNAVAILABLE`，不返回 403 | `OverviewQueryService.java:162-172,339-363`；`OverviewQueryServiceTest.java:369-390` | D-05 待决策：是否要求 scope 不可用时也 fail-closed 403 |
| 管理员组织范围 | 服务端 `SecurityUtils.getDeptId()`；不读取客户端 `organizationId`；为 null → 403 | `SecurityOverviewIdentityProvider.java:24-38`；`OverviewQueryService.java:275-278,331-334` | 符合“不信任客户端”；契约草案的 `organizationId` 未实现，D-04 |
| 身份 | 服务端 `SecurityUtils.getUserId()`；不接收 `userId/studentId` | `SecurityOverviewIdentityProvider.java:24-27`；`OverviewController.java:66,83,100,119` | 已核 |
| 未知查询参数 | 方法签名不声明的参数（如学生 `date`、管理员 `organizationId`、学生/管理员 `classId`）按 Spring MVC 默认不会触发 400；未做真实 HTTP 验证 | Controller 方法签名；未做真实 HTTP 验证 | 前端不得发送；契约/文档需统一，D-03/D-04 |

### 2.2 HTTP 状态与 body.code

| 场景 | HTTP | body.code | 证据 | 前端动作 |
| --- | --- | --- | --- | --- |
| 正常聚合 | 200 | 200 | `OverviewController.java:67,84,101,120`；`AjaxResult.java:77` | 读 `data` |
| 业务参数/越权 | 200 | 400/403 | `OverviewExceptionHandler.java:38-57`；`OverviewException.java` | 同时判断 HTTP 与 `body.code` |
| `@PreAuthorize` 权限拒绝 | 200 | 403 | `GlobalExceptionHandler.java:35-41` | 显示无权限，清空旧数据 |
| 未认证/令牌失效 | 200（当前 RuoYi handler；网关可能 401） | 401 | `AuthenticationEntryPointImpl.java:27-33`；`ServletUtils.java:139-146` | 清登录态并跳登录 |
| 缺适配器/单源失败 | 200 | 200 + item `UNAVAILABLE` | `OverviewSourceGuard.java:96-157`；服务测试 | 单卡片“暂不可用”，其他卡片正常 |
| 网关/网络失败 | 非 200/无响应 | 无 | 未验证 | 网络错误，可重试；不得把缓存标最新 |

## 3. 字段与指标矩阵

### 3.1 通用字段

| 字段 | 实际类型/输出 | 证据 | 前端要求/待确认 |
| --- | --- | --- | --- |
| `asOf` | `Instant`，服务端响应时刻；不是每个来源的截止时间 | `OverviewResponse.java:18-58`；`OverviewQueryService.java:580-583` | 显示“数据截至”；D-08 |
| `window.startsAt/endsAt` | `Instant`；默认 UTC 自然日或显式 `from/to`；左闭右开 | `OverviewWindow.java:8-32`；`OverviewWindowFactory.java:46-82` | 显示服务端返回窗口，不自行算“今天”；D-02 |
| `items` | 非空数组；DTO 拒绝空数组 | `OverviewResponse.java:35-43` | 不得用 `items.length===0` 表示无数据 |
| `metricCode` | 稳定字符串；实际 16 个编码见 3.2-3.5 | `OverviewQueryService.java:201-211,255-262,308-315,335-336` | 按编码映射卡片，不按数组下标 |
| `value` | `BigDecimal` 或 null；OK 非 null，NO_DATA/UNAVAILABLE 为 null | `OverviewItem.java:57-64` | 0 是真实值；null 不得显示为 0 |
| `unit` | 当前所有指标均为 `COUNT` | `OverviewQueryService.java:397,418,454,483,516,543,560,576` | 不要假设 PERCENT/SCORE |
| `status` | `OK`/`NO_DATA`/`UNAVAILABLE` | `OverviewStatus.java`；`OverviewItem.java` | 三态分别展示 |
| `source` | `course`/`classroom`/`assignment`/`grade`/`notice`/`pending_product_confirmation` | `OverviewQueryService.java:62-72`；各 item 构造 | 用于诊断/标签，不替代 status |
| `sampleCount` | `Long` 或 null；不同指标含义不同 | 3.2-3.5；`OverviewQueryService.java:397,418,454,483,516,543,560` | 不得当统一分母；D-06 |
| `actionPath` | 后端相对路径或 null；实际值见 3.2-3.5 | `OverviewQueryService.java:201-211,255-262,308-315,335-336` | 必须由 6B 路由表确认；D-10 |

### 3.2 教师端点实际指标

| metricCode | 业务含义/实际公式 | source | unit | window | sampleCount | 空值/三态 | actionPath |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `teacher.class.count` | 授权课程范围条数 `scopes.size()`；适配器是否去重未证明 | course | COUNT | 否（快照） | 课程范围条数 | 范围空 List → NO_DATA；非空 → OK ≥1；无 OK 0 | `/teacher/classes` |
| `teacher.student.count` | 各范围 `studentCount` 求和；任一为 null → NO_DATA；是否跨班去重未证明（D-13） | course | COUNT | 否（快照） | 课程范围条数 | 范围空或任一学生数 null → NO_DATA；有范围且全非 null → OK（可为 0） | `/teacher/classes` |
| `teacher.session.running` | 绑定范围内 `RUNNING` 或 `PAUSED` 的课堂数；`PAUSED` 也计入（D-11） | classroom | COUNT | 是（传给端口） | 去重绑定 ID 数 | 无绑定/窗口内无课堂 → NO_DATA；Map 缺绑定 key → UNAVAILABLE；有课堂但无 RUNNING/PAUSED → OK 0 | `/teacher/classes` |
| `teacher.assignment.pending` | 各 `AssignmentTodo` 的 `max(0,targetCount-submittedCount)` 求和；是待提交学生数，不是任务条数（D-12） | assignment | COUNT | 是（传给端口） | 待办记录条数 | 无记录 → NO_DATA；Map 缺教师 key → UNAVAILABLE；有记录且合计 0 → OK 0 | `/teacher/assignments` |
| `teacher.grade.pending` | 各绑定 `pendingGradeCount + pendingPublishCount` 求和；两个计数是否互斥未定义（D-14） | grade | COUNT | 否（快照） | 去重绑定 ID 数 | 无绑定 → NO_DATA；Map 缺绑定 key → UNAVAILABLE；有绑定且合计 0 → OK 0 | `/teacher/grades` |
| `teacher.notice.unread` | `NoticeSummary.unreadCount`；0 是真实未读 0 | notice | COUNT | 否（当前快照） | null | 适配器缺失/摘要缺失 → UNAVAILABLE；0 → OK 0 | `/teacher/notices` |

### 3.3 学生端点实际指标

| metricCode | 业务含义/实际公式 | source | unit | window | sampleCount | 空值/三态 | actionPath |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `student.class.count` | 有效在班范围条数 `scopes.size()`；是否去重取决于适配器 | course | COUNT | 否（快照） | 课程范围条数 | 空 List → NO_DATA；非空 → OK ≥1；无 OK 0 | `/student/classes` |
| `student.session.active` | 绑定范围内 `RUNNING` 或 `PAUSED` 的课堂数；`PAUSED` 也计入（D-11） | classroom | COUNT | 是（传给端口） | 去重绑定 ID 数 | 无绑定/窗口内无课堂 → NO_DATA；Map 缺绑定 key → UNAVAILABLE；无活动但有课堂 → OK 0 | `/student/todos` |
| `student.assignment.due` | `AssignmentTodo` 记录条数 `todos.size()`；不是 `pendingCount` 求和（D-12） | assignment | COUNT | 是（传给端口） | 待办记录条数 | 空 List → NO_DATA；Map 缺学生 key → UNAVAILABLE；非空 → OK ≥1；无 OK 0 | `/student/todos` |
| `student.notice.unread` | `NoticeSummary.unreadCount`；0 是真实未读 0 | notice | COUNT | 否（当前快照） | null | 适配器缺失/摘要缺失 → UNAVAILABLE；0 → OK 0 | `/student/notices` |

### 3.4 管理员总览实际指标

| metricCode | 业务含义/实际公式 | source | unit | window | sampleCount | 空值/三态 | actionPath |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `admin.class.count` | 组织授权范围条数 `scopes.size()`；是否去重取决于适配器 | course | COUNT | 否（快照） | 课程范围条数 | 空 List → NO_DATA；非空 → OK ≥1；无 OK 0 | `/admin/classes` |
| `admin.student.count` | 各范围 `studentCount` 求和；任一 null → NO_DATA；是否跨班去重未证明（D-13） | course | COUNT | 否（快照） | 课程范围条数 | 空范围或任一 null → NO_DATA；有范围且全非 null → OK（可为 0） | `/admin/classes` |
| `admin.session.running` | 绑定范围内 `RUNNING`/`PAUSED` 课堂数；`PAUSED` 也计入（D-11） | classroom | COUNT | 是（传给端口） | 去重绑定 ID 数 | 无绑定/窗口内无课堂 → NO_DATA；Map 缺绑定 key → UNAVAILABLE；无活动 → OK 0 | `/admin/classes` |
| `admin.assignment.pending` | 各绑定 `pendingCount` 求和；是待提交学生数，不是任务条数（D-12） | assignment | COUNT | 是（传给端口） | 去重绑定 ID 数 | 无记录 → NO_DATA；Map 缺绑定 key → UNAVAILABLE；有记录且合计 0 → OK 0 | `/admin/assignments` |
| `admin.grade.pending` | 各绑定 `pendingGradeCount + pendingPublishCount` 求和；重叠未定义（D-14） | grade | COUNT | 否（快照） | 去重绑定 ID 数 | 无绑定 → NO_DATA；Map 缺绑定 key → UNAVAILABLE；有绑定且合计 0 → OK 0 | `/admin/grades` |

### 3.5 管理员待办实际指标

| metricCode | 业务含义/实际公式 | source | unit | window | sampleCount | 空值/三态 | actionPath |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `admin.todo.pending` | 当前固定 `NO_DATA`；不调用任何业务端口，不返回数量或待办明细 | pending_product_confirmation | COUNT | 否（无来源） | null | 恒为 NO_DATA，value=null；无 OK 0 | null |

### 3.6 指标口径结论

- 通用失败规则：端口缺失/异常/超时 → 对应 item `UNAVAILABLE`；Map 缺请求 key 或值为 null → `UNAVAILABLE`；空 List/空范围 → `NO_DATA`；显式计数 0 → `OK` + `value=0`（`OverviewQueryService.java:339-363,422-561`）。
- 代码实际输出的 metricCode 共 16 个：教师 6、学生 4、管理员总览 5、管理员待办 1；没有其他动态编码。
- `unit` 当前全部为 `COUNT`；`value` 是 JSON 数值，不是字符串。
- 响应没有独立 `denominator` 字段；`sampleCount` 不是统一分母：班级/学生数是范围条数，课堂/管理员作业/成绩是绑定数，教师/学生作业是记录条数，通知为 null（D-06）。
- `window` 只实际传给课堂和作业端口；班级/学生/成绩/通知是当前快照，管理员待办无来源（D-07）。
- `asOf` 是响应时刻；`NoticeSummary.asOf` 未被聚合层使用，不能当作每项来源的截止时间（D-08）。
- 教师/管理员的 `assignment.pending` 是“待提交学生数合计”，不是“待处理任务条数”；学生 `assignment.due` 是记录条数。前端卡片标题必须按实际口径写，不能只按字段名猜（D-12）。
- 学生数按班级求和，是否跨班去重未证明；若同一学生在多个班，可能重复计数（D-13）。
- 课堂/作业/成绩按绑定汇总；同一课堂/任务/成绩跨多个绑定是否重复未证明，需适配器保证粒度。
- 成绩待办把 `pendingGradeCount` 与 `pendingPublishCount` 直接相加；若同一成绩同时处于两个待办状态，可能重复（D-14）。
- 上述口径需要第 2/5 组适配器契约和产品负责人确认，不能由 6A 自行改口径。

### 3.7 actionPath 核对

| metricCode | 后端 actionPath | 现有前端路由证据 | 结论 |
| --- | --- | --- | --- |
| `teacher.class.count`/`teacher.student.count`/`teacher.session.running` | `/teacher/classes` | 未取得 6B 路由 | 未确认 |
| `teacher.assignment.pending` | `/teacher/assignments` | 未取得 | 未确认 |
| `teacher.grade.pending` | `/teacher/grades` | 未取得 | 未确认 |
| `teacher.notice.unread` | `/teacher/notices` | 未取得 | 未确认 |
| `student.class.count` | `/student/classes` | 未取得 | 未确认 |
| `student.session.active`/`student.assignment.due` | `/student/todos` | 未取得 | 未确认 |
| `student.notice.unread` | `/student/notices` | 未取得 | 未确认 |
| `admin.class.count`/`admin.student.count`/`admin.session.running` | `/admin/classes` | 未取得 | 未确认 |
| `admin.assignment.pending` | `/admin/assignments` | 未取得 | 未确认 |
| `admin.grade.pending` | `/admin/grades` | 未取得 | 未确认 |
| `admin.todo.pending` | `null` | 未取得 | 后端明确无下钻；前端不得拼接 |

**未取得 6B 路由表，以上路径不能宣称可定位；不得由 6A 擅自新增或改写前端路由。**

前端卡片名称、卡片分组、下钻参数和空态文案均未取得 6B 版本；3.2-3.5 只记录后端 `actionPath`，不能当作前端卡片/路由已存在。

## 4. 三态展示矩阵

> 后端三态语义来自 `OverviewStatus`、`OverviewItem`、`OverviewResponse`；前端证据均未取得。以下“前端预期”是核对要求，不是已实现结论。

| 场景 | 后端输入/预期 | 前端预期 | 证据 | 当前状态 |
| --- | --- | --- | --- | --- |
| OK 且 `value=0` | 真实测量 0，status=OK，value=0；例如通知未读 0、成绩待办 0 | 显示“0”，不得显示“暂无数据” | `OverviewItemTest.java:16-25`；`OverviewQueryServiceTest.java:227-250` | 现有 L2 用例（未重跑）；前端未取得 |
| OK 且有值 | status=OK，value>0 | 显示数值；保留卡片位置 | `OverviewQueryServiceTest.java:81-162` | 现有 L2 用例（未重跑）；前端未取得 |
| NO_DATA 且 `value=null` | 源可用但无证据/空列表；value=null | 显示“暂无数据”；不得显示 0、空数组或空白卡 | `OverviewQueryServiceTest.java:227-250`；`OverviewResponse.java:35-43` | 现有 L2 用例（未重跑）；前端未取得 |
| UNAVAILABLE 且 `value=null` | 适配器缺失/超时/异常/契约违约；value=null | 显示“暂不可用/稍后重试”；保留卡片标题，不显示 0 | `OverviewQueryServiceTest.java:165-190,253-263`；`OverviewSourceGuard.java:96-157` | 现有 L2 用例（未重跑）；前端未取得 |
| 单卡片失败、其他正常 | 单源 UNAVAILABLE，其他项 OK；HTTP 200/code 200 | 只替换对应卡片；其他卡片继续显示 | `OverviewQueryServiceTest.java:165-190` | 现有 L2 用例（未重跑）；前端未取得 |
| 请求整体失败 | 参数 400/越权 403/未认证 401/网络失败；无完整 data | 显示页面级错误；保留页面结构；清空或标记旧数据 | `OverviewControllerTest.java:188-227`；认证/网络仅 L1/L0 | 后端部分 L2；真实 HTTP/前端未取得 |
| 切换角色/课程/教学班 | 后端不保存前端状态；新请求返回新 window | 清理旧角色/班级数据、旧错误和旧缓存，显示加载态 | 无前端版本 | 未核对 |
| 刷新失败后缓存值 | 请求失败时无新 `asOf`/window | 若保留缓存，必须显示旧数据时间/失败标识，不能标“最新” | 无前端版本 | 未核对 |

补充：

- 后端不会返回空 `items`；`OverviewResponse` 构造器拒绝空列表（`OverviewResponse.java:35-43`）。前端不得用 `items.length===0` 判断无数据。
- `NO_DATA` 和 `UNAVAILABLE` 的 `value` 必须为 null；不得把“无数据/源不可用”显示为 0。
- 单卡片失败不等于整个请求失败；前端必须按 `metricCode` 合并，不应按数组下标。

## 5. 管理员待办专节

当前后端固定输出（`OverviewQueryService.java:331-337`；`OverviewQueryServiceTest.java:355-367`）：

| 字段 | 值 |
| --- | --- |
| `metricCode` | `admin.todo.pending` |
| `status` | `NO_DATA` |
| `source` | `pending_product_confirmation` |
| `value` | `null` |
| `unit` | `COUNT` |
| `sampleCount` | `null` |
| `actionPath` | `null` |

含义：

- 来源尚待产品确认；**不是待办数为 0，也不是“全部处理完成”**。
- 前端不得显示“0 条”“全部处理完成”，不得自行拼造待办标题、数量或下钻路径。
- `window` 仍返回请求/默认窗口，但该 item 没有来源，窗口对数值没有意义。
- 候选来源、处理人、权限和状态机只列为建议，待负责人确认；本轮不定义、不实现。
- 前端必须为这个 item 显示“待产品确认/暂无数据来源”，而不是普通“暂无数据”掩盖来源未定。

## 6. 错误与验证场景

> 下表的 L2 仅指现有测试源码对隔离行为的覆盖；本轮未重跑 Maven，不能把 L2 当成已执行通过。L3 一栏为空即未验证。

| 场景 | 输入 | 后端预期 | 前端预期 | 证据 | 验证层级 |
| --- | --- | --- | --- | --- | --- |
| `from`/`to` 单个缺失 | 只传 `from` 或只传 `to` | HTTP 200；body.code=400 | 参数错误提示；不重试同参数 | `OverviewWindowFactory.java:54-57`；`OverviewWindowFactoryTest.java:40-44` | L1/L2 |
| 时间格式错误 | `from=not-a-time` | HTTP 200；body.code=400 | 参数错误；不重试 | `OverviewWindowFactory.java:72-82`；`OverviewControllerTest.java:211-219` | L1/L2 |
| 时间窗为空/反向 | `from >= to` | HTTP 200；body.code=400 | 参数错误 | `OverviewWindow.java:22-32`；`OverviewWindowTest.java:26-40` | L1/L2 |
| `classId` 类型错误 | `classId=abc` | HTTP 200；body.code=400 | 参数错误 | `OverviewExceptionHandler.java:54-57`；`OverviewControllerTest.java:221-227` | L1/L2 |
| 教师班级越权（范围 OK） | `classId` 不在授权范围 | HTTP 200；body.code=403 | “无权查看该范围”；清空旧数据 | `OverviewQueryService.java:162-172`；`OverviewQueryServiceTest.java:369-390` | L1/L2 |
| 教师班级范围不可用 | `classId` 任意；课程范围源 NO_DATA/UNAVAILABLE | HTTP 200；body.code=200；对应 item NO_DATA/UNAVAILABLE，不返回 403 | 显示“暂无数据/暂不可用”，不得显示“有权限” | `OverviewQueryService.java:339-363`；D-05 | L1/L2 |
| 管理员组织范围缺失 | `SecurityUtils.getDeptId()` 为 null | HTTP 200；body.code=403 | 无权限；清空旧数据 | `OverviewQueryService.java:275-278,331-334`；`OverviewControllerTest.java:199-208` | L1/L2 |
| 未登录/令牌失效 | 无/失效 token | 当前 RuoYi handler：HTTP 200 + body.code=401；网关可能 HTTP 401 | 清登录态，跳登录 | `AuthenticationEntryPointImpl.java:27-33`；`ServletUtils.java:139-146` | L1；L3 未验证 |
| 权限串不匹配 | 有登录但无 `education:overview:*` | HTTP 200 + body.code=403（GlobalExceptionHandler） | 显示无权限；不显示数据 | `OverviewController.java:59,77,94,113`；`GlobalExceptionHandler.java:35-41`；`OverviewControllerTest.java:229-240`（仅反射） | L1/L2；L3 未验证 |
| 缺适配器/源不可用 | 端口为 null/超时/异常 | HTTP 200 + body.code=200；对应 item UNAVAILABLE | 单卡“暂不可用”；其他卡片正常 | `OverviewSourceGuard.java:96-157`；`OverviewQueryServiceTest.java:253-263` | L1/L2 |
| 部分源失败 | 一个端口抛异常，其他正常 | 该项 UNAVAILABLE，其他 OK | 只替换失败卡片 | `OverviewQueryServiceTest.java:165-190` | L1/L2 |
| 网络失败 | 请求未到后端/连接断开 | 无 body | 网络错误提示；可重试；不把缓存标最新 | 无 L3 证据 | L0 |
| 学生/管理员发未知参数 | 学生发 `date`/`classId`，管理员发 `organizationId` | 方法签名不接收；默认忽略，不 400 | 前端不得发送；按支持参数表调用 | Controller 签名；D-03/D-04 | L1；L3 未验证 |

说明：

- 现有 `OverviewControllerTest` 是 standalone MockMvc + Mockito，`@PreAuthorize` 不执行；权限只做了注解反射断言，不能当真实角色拦截证据。
- 现有 `OverviewQueryServiceTest` 是服务编排和失败收敛的隔离测试；不能证明生产适配器、真实 SQL、真实数据口径。
- 当前没有生产适配器；不能为演示制造适配器或假数据。
- 真实 HTTP、登录、角色拦截、审计落库、MySQL 8.0.46 均未验证。

## 7. 差异与确认记录

### 7.1 差异表

| 编号 | 问题 | 影响 | 证据 | 建议负责方 | 处理建议 | 是否阻塞契约确认 |
| --- | --- | --- | --- | --- | --- | --- |
| D-01 | 未取得 6B 6-03/6-04 可核验版本 | 无法核对页面、API 调用、字段映射、状态、路由；无法确认前后端一致 | 远端分支/PR 检索；本地静态壳无总览调用；PR #26/feat/admin 非 6B | 6B | 提供分支/PR/head SHA、页面与 API 文件、路由表 | 是（前端确认） |
| D-02 | 默认窗口是 UTC 自然日；前端可能按本地时区显示“今天” | 跨时区用户看到的“今日”范围与卡片/待办日期可能不一致 | `OverviewWindowFactory.java:66-70`；handoff 参数节；契约 2.2/3.2 待评审 | 产品/6A/6B | 决定业务时区或当前学期；前端显示服务端 window；不自行改口径 | 是（展示口径） |
| D-03 | 契约草案学生端点有 `date` 参数，实际 Controller 只接收 `from/to`；`date` 被忽略 | 按契约草案开发的前端传 `date` 不会生效，可能默认到 UTC 日 | 契约 3.2；`OverviewController.java:77-85`；handoff 参数节 | 6A 文档/6B | 统一用 `from/to`，或另立 `date` 需求；本轮不改代码 | 是（学生参数） |
| D-04 | 契约草案管理员端点有可选 `organizationId`，实际不接收；组织来自服务端 `getDeptId()` | 前端传 `organizationId` 无效；契约文字与实现不一致 | 契约 3.3/3.4；`OverviewController.java:94-121`；`SecurityOverviewIdentityProvider.java:35-38` | 6A 文档/6B | 保留服务端组织来源；更新契约文字；前端不传 | 是（管理员参数） |
| D-05 | `classId` 越权只在课程范围源 OK 时返回 403；范围源 NO_DATA/UNAVAILABLE 时返回 NO_DATA/UNAVAILABLE | 前端无法用 403 区分“无权限”和“范围源不可用”；无数据泄露但错误语义不同 | `OverviewQueryService.java:162-172,339-363` | 产品/6A/6B | 决定 scope 不可用时是否也 fail-closed 403；至少前端分别展示 | 是（权限语义） |
| D-06 | `sampleCount` 口径不统一：范围条数/绑定数/记录条数/null | 前端若当统一分母会误标“样本数” | `OverviewQueryService.java:397,418,454,483,516,543,560` | 6A/产品/6B | 逐指标定义 sampleCount 展示；必要时后续增加 denominator 字段 | 部分 |
| D-07 | `window` 只传给课堂/作业端口；班级/学生/成绩/通知是当前快照 | 前端把 window 当所有卡片统计范围会误导 | `OverviewQueryService.java:180-193,239-247,290-303,519-561` | 6A/产品/6B | 标出每个指标是否受 window 影响；不要统一显示“统计范围” | 部分 |
| D-08 | 顶层 `asOf` 是响应时刻；`NoticeSummary.asOf` 未使用；无逐项来源截止时间 | 前端“数据截至”可能早于/晚于实际来源；无法判断单项新鲜度 | `OverviewResponse.java:18-58`；`OverviewQueryService.java:546-561,580-583` | 6A/6B | 明确 asOf 语义；后续如需逐项 asOf 再扩展字段 | 部分 |
| D-09 | 契约/交接示例固定 `.000Z`；实际 Java `Instant`/Jackson 序列化精度未在真实 HTTP 验证 | 前端若硬编码毫秒位数可能解析失败 | 契约 2.2；`OverviewWindowFactory.java:72-82`；JDK 17 `Instant.parse` 核对 | 6A/6B | 前端用 RFC 3339 解析器；请求统一 `Z`；L3 再确认实际字符串 | 否（解析健壮即可） |
| D-10 | 后端 actionPath 已给出，但未取得 6B 路由表 | 下钻可能 404；无法确认卡片可点击 | `OverviewQueryService.java:201-211,255-262,308-315,335-336`；无 6B 版本 | 6B | 提供路由表并逐项确认；不存在就返回 null/改路径，不由 6A 猜 | 是（下钻确认） |
| D-11 | `session.running`/`session.active` 把 `PAUSED` 也计入 | 卡片写“进行中”会把暂停课堂算进去 | `OverviewQueryService.java:450-453` | 产品/6A/6B | 明确标签“进行中/暂停”或调整口径 | 部分 |
| D-12 | 教师/管理员 `assignment.pending` 是待提交学生数合计；学生 `assignment.due` 是记录条数 | 前端按“任务数”展示会误读；不同端不可直接比较 | `OverviewQueryService.java:457-517` | 6A/第 5 组/6B | 卡片标题按实际口径；确认适配器数据粒度 | 是（指标口径） |
| D-13 | 学生数是各范围 `studentCount` 求和，未证明跨班去重 | 同一学生多班可能重复计数 | `OverviewQueryService.java:401-420`；`CourseScope` 定义 | 第 2 组/产品/6A | 确认适配器是否按人去重；前端标题写“班内人次/人数”需明确 | 是（指标口径） |
| D-14 | 成绩待办 `pendingGradeCount + pendingPublishCount` 直接相加，重叠未定义 | 同一成绩可能被计两次 | `OverviewQueryService.java:519-544`；`GradeTodo` | 第 5 组/产品/6A | 要求适配器保证互斥或调整公式 | 是（指标口径） |
| D-15 | 管理员待办来源未确认，当前恒为 NO_DATA | 前端可能误显示 0/全部完成 | `OverviewQueryService.java:331-337`；服务测试 | 产品/6A/6B | 保持 NO_DATA；前端显示“待产品确认”；不拼造 | 是（产品口径） |
| D-16 | 生产适配器未接入；当前真实调用多为 UNAVAILABLE | 无法验证真实数据、样本、性能、时间窗 | `OverviewConfiguration.java:100-137`；`OverviewQueryServiceTest` 全 Mockito | 第 2/4/5 组/6A | 接入后重新 L3 核对 | 是（真实联调） |
| D-17 | 权限串/菜单/角色未配置；真实登录/拦截/审计未验证 | 无法确认 403/401 真实行为 | Controller 注解；`OverviewControllerTest.java:229-240` 仅反射 | 第 1 组/6A | 配置后用真实账号验证 | 是（真实联调） |

### 7.2 分类汇总

**6A 本 PR 可直接修正（文档/清单，不改业务代码）**

- D-03、D-04：handoff 已改为实际 `from/to`、服务端组织来源；契约草案文字需在 #23 另行确认，本轮不改 #23。
- D-05：handoff 已把 classId 403 条件写清；清单保留待决策。
- D-09：handoff 已提示不要依赖 `.000Z`；真实序列化仍需 L3。
- D-06、D-07、D-08、D-11、D-12、D-13、D-14：已在本清单逐项记录；是否改契约/产品口径不在本轮。

**需要 6B 修改或确认**

- D-01：提供 6-03/6-04 版本、页面、API、路由。
- D-03/D-04：只发支持的参数；不要发 `date`/`organizationId`/学生/管理员 `classId`。
- D-05：区分 403 与 NO_DATA/UNAVAILABLE。
- D-06/D-07：按指标口径展示 sampleCount/window。
- D-08：正确处理 asOf；不要把顶层 asOf 当逐项来源时间。
- D-10：逐项确认 actionPath 路由。
- D-11/D-12/D-13/D-14：按实际口径写卡片标题和单位。
- D-15：管理员待办不得显示 0/全部完成。
- 三态矩阵、单卡失败、整体失败、角色/班级切换、刷新失败缓存，全部需要 6B 证据。

**需要负责人/产品决定**

- D-02 默认 UTC 自然日 vs 业务时区/当前学期。
- D-05 课程范围不可用时 classId 是否 fail-closed 403。
- D-06 sampleCount 的对外语义。
- D-07 哪些指标受 window 影响。
- D-08 asOf 的对外语义。
- D-11 PAUSED 是否计入“进行中”。
- D-12 教师/管理员 assignment pending 的口径与卡片标题。
- D-13 学生数是否按人去重。
- D-14 成绩两个待办计数是否互斥。
- D-15 管理员待办来源、处理人、权限、状态机。

**外部契约/权限/真实数据阻塞**

- D-16：第 2/4/5 组生产适配器未接入；真实数据、样本、时间窗、性能未验证。
- D-17：第 1 组权限/菜单/角色/审计未配置；真实 401/403 未验证。
- 真实 HTTP、登录、角色拦截、审计落库、MySQL 8.0.46 未验证。

## 8. 给 6B 的核对请求（草稿，未发送）

以下内容只起草，不代发。发送前请负责人确认收件人和版本。

```text
6B 同学好，6-01 后端已完成四端点静态核对，但远端仓库未找到 6-03/6-04 分支或 PR，无法核对前端页面、API 调用、字段映射、状态和路由。请提供可核验版本并逐项回复。

文档：
https://github.com/wangch880808-byte/-/blob/feature/6-01-overview-api/智慧教育课程/智慧教育平台/docs/6-01-frontend-review-checklist.md

PR：#24 https://github.com/wangch880808-byte/-/pull/24
后端版本：feature/6-01-overview-api@22898ae27a90779709b0c29b846181aa7054e0b0
后端 base：#23 feature/6-01-overview-contract@3ce2451cffc0b669844b11a00e27d2990f011464
main 基线：f21f51d55c363738c03ee6f626c7c67b904d6b36

请回复：
1. 6-03/6-04 的分支、PR、head SHA；页面/API/字段映射/路由文件路径。
2. D-01：是否确认上述版本就是待核对前端；若不是，请给正确版本。
3. D-03/D-04：学生端点是否只发 from/to；管理员端点是否不传 organizationId；是否已移除 date/organizationId/学生/管理员 classId。
4. D-05：课程范围源 NO_DATA/UNAVAILABLE 时，前端如何区分“无权限”和“暂不可用”。
5. D-06/D-07/D-08：sampleCount、window、asOf 的卡片展示方式；是否把 window 当所有指标统一统计范围。
6. D-10：逐项确认 actionPath 能否定位到实际路由；列出缺失路由。
7. D-11/D-12/D-13/D-14：课堂 PAUSED、assignment pending 学生数口径、学生数去重、成绩两计数重叠的展示文案。
8. D-15：管理员待办当前 admin.todo.pending / NO_DATA / source=pending_product_confirmation / value=null / actionPath=null，前端是否避免显示 0 或“全部处理完成”。
9. 三态展示：OK 0、OK 有值、NO_DATA、UNAVAILABLE、单卡失败、整体失败、角色/班级切换旧数据清理、刷新失败缓存标识，分别给截图/代码/测试证据。
10. 错误场景：参数 400、业务 403、未登录 401、源不可用、网络失败、部分源失败；请说明是否同时判断 HTTP 状态和 body.code。

请明确区分“代码看起来一致”“6B 已确认”“真实联调通过”，不要只回复“已对齐”。在收到版本和逐项回复前，6A 不会宣称前后端评审完成或联调完成。
```

## 9. 检查记录与未验证项

### 9.1 已执行检查

- `git fetch origin --prune`：成功；main、#23、#24 引用与基线一致。
- 后端源码/测试静态核对：Controller、DTO、WindowFactory、QueryService、ExceptionHandler、IdentityProvider、RuoYi 安全处理链、AjaxResult。
- 证据路径：业务代码在 `backend/ruoyi-education/src/main/java/com/ruoyi/education/overview/`；测试在 `backend/ruoyi-education/src/test/java/com/ruoyi/education/overview/`；安全处理在 `backend/ruoyi-framework/src/main/java/com/ruoyi/framework/`；统一响应在 `backend/ruoyi-common/src/main/java/com/ruoyi/common/core/domain/AjaxResult.java`。
- 前端检索：远端分支/PR、本地 `frontend/`、PR #26/`feature/login-role-style`/`feat/admin` 树和文件；未发现 6-03/6-04 总览实现。
- `git diff --check`：通过（2026-11-02）。
- Markdown 表格列数检查：通过（含转义 `\|` 的表格）。
- 代码—文档一致性：实际 metricCode 16 个与清单完全一致；10 个 actionPath 在源码中均有对应；74 处 Java 行号引用均在文件范围内。
- 契约草案不一致处已列为 D-03/D-04/D-05/D-06/D-07/D-08；不修改 #23。

### 9.2 未验证

- 未运行真实 HTTP、未登录、未验证角色拦截/权限串、未验证审计落库。
- 未接入生产课程/课堂/作业/成绩/通知适配器；真实数据、样本、时间窗、性能未验证。
- 未执行 MySQL 8.0.46、未连接数据库、未执行 SQL。
- 未取得 6B 前端版本；页面、API 调用、字段映射、三态展示、路由、缓存/切换清理均未核对。
- 本轮未重跑 Maven/现有测试；文中 L2 指现有测试源码覆盖，不等于本轮执行结果。
- 现有 MockMvc/Mockito 仅隔离测试证据；不作为真实联调结论。
- 未由 6A/6B 负责人签署确认；确认记录见 1.3。

## 10. 停止点

- 本轮只新增/修订文档，不改业务代码、前端、SQL、权限配置或生产适配器。
- 不修改 #23/#29；#24 保持 Draft，不标 Ready、不合并。
- 等待独立复审和 6B 回应后再继续逐项确认。
