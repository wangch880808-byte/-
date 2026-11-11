# 6-01 三端总览：6B 契约回执与排期

> 版本：0.1｜日期：2026-11-05｜作者：6B｜分支：`feature/6-01-frontend`（基线 `six@0624225`）
>
> 对应后端文档：`docs/6-01-frontend-api-handoff.md`（v1.4）、`docs/6-01-frontend-review-checklist.md`
> 对应后端分支：`feature/6-01-overview-api`（PR #24）、`feature/6-01-overview-contract`（PR #23）
>
> **边界**：本轮只做契约确认、口径意见与排期，**未写页面、未写 API 封装**。
> 本文不构成"源码核对一致"，更不是真实联调结论；真实 HTTP、登录、授权、时区行为均未验证。

## 1. 结论摘要

| 类别 | 条目 | 说明 |
| --- | --- | --- |
| 可直接确认 | 端点路径（4 个）、参数名（`from`/`to`/`classId`）、`AjaxResult` 信封、`items[]` 字段、三态语义 | 与 `OverviewController` 实际签名逐项对过，可直接按此实现 |
| 需后端变更 | `actionPath` 语义（13 条中仅 2 条能落到现有视图） | 建议改为"语义目标键"，详见第 5、6 节 |
| 需产品/负责人决策 | D-02 时区、D-12/D-13/D-14/D-18 指标口径、D-11 暂停计入 | 影响卡片标题与"今日"措辞，前端不自行改口径 |
| 前端已定 | 三态渲染规则、`value=0` 与 `null` 的区分、单卡失败隔离、刷新失败不标最新 | 见第 4 节，按 handoff 第 3 节执行 |
| 外部阻塞 | 权限串 `education:overview:*`、生产适配器 | 第 1 组 / 第 2、4、5 组；未解除前真实调用只会得到 `UNAVAILABLE` |

## 2. 参数确认（前端实际发送）

| 端点 | 前端发送 | 不发送 | 默认（不传时） |
| --- | --- | --- | --- |
| `GET /education/teacher/overview` | 无（v1）；后续可选 `classId` | 不发 `from`/`to`（用服务端默认窗）、不发 `userId` | 服务端 UTC 自然日 `[00:00, 次日 00:00)` |
| `GET /education/student/today-todos` | 无 | 不发 `date`、`classId`、`from`/`to` | 同上 |
| `GET /education/admin/overview` | 无 | 不发 `organizationId`、`from`/`to` | 同上 |
| `GET /education/admin/todos` | 无 | 不发 `organizationId`、`from`/`to` | 同上 |

三条补充：

1. **v1 不传 `classId`**：前端当前没有"binding → classId"映射来源（2-01/2-04 未提供列表），也没有班级选择器。
   在已有可靠来源前传 `classId` 只能靠硬编码，明确不做。等第 2 组提供教学班列表后再启用筛选。
2. **v1 不传 `from`/`to`**：默认窗口口径（D-02）未定；前端不自行拼"本地今天"的边界，
   否则等于单方面改口径。页面显示服务端返回的 `window`，并按第 4 节标注。
3. 将来若启用显式窗口，前端统一发送 `Z` 结尾的 UTC 串（如 `2026-11-04T16:00:00Z`），
   不发送 `+08:00` 偏移，避免后端归一化与前端显示出现两套解释。

## 3. 字段确认

| 字段 | 前端用法 | 备注 |
| --- | --- | --- |
| `asOf` | 页面右上角"数据截至 HH:mm"（本地时区，附 UTC 原值于 tooltip） | 是响应时刻，不是逐项来源时间（D-08），不得写成"各卡片截至" |
| `window.startsAt` / `endsAt` | 顶部一次性显示"统计范围"，含左闭右开说明 | 不逐卡重复显示（D-07：只有课堂/作业受窗口影响） |
| `items[].metricCode` | 按编码取卡片，**不按数组下标** | 未知 `metricCode` 忽略并 `console.warn`，不渲染空卡 |
| `items[].value` | `OK` 时显示数值（含 0）；`NO_DATA`/`UNAVAILABLE` 必须为 null | 值为 null 但状态为 `OK` 视为契约违约，按 `UNAVAILABLE` 渲染并记录 |
| `items[].status` | 三态渲染，见第 4 节 | 未知状态按 `UNAVAILABLE` 处理 |
| `items[].unit` | 当前只有 `COUNT`；预留 `PERCENT`/`SCORE` | 未知 unit 原样显示单位，不猜 |
| `items[].source` | 不展示，仅用于排查（放进卡片 `title` 属性） | |
| `items[].sampleCount` | **不当分母、不统一标注"样本数"**（D-06） | 见第 4 节表格，逐指标决定是否展示 |
| `items[].actionPath` | 仅当第 5 节映射确认后才可点击 | 为 null 时不跳；不拼接猜测路径 |
| `data.items` 为空 | 不出现（后端拒绝空列表）；若出现按整体异常处理 | 不用 `items.length===0` 判断无数据 |

## 4. 三态与展示口径

### 4.1 三态渲染（按 handoff 第 3 节执行）

| status | 卡片表现 | 禁止 |
| --- | --- | --- |
| `OK` + `value>0` | 数值 + 单位 | — |
| `OK` + `value=0` | **显示 0**（如"0 条"），可附"暂无待办"副文案，但数字必须是 0 | 不得整卡显示"暂无数据" |
| `NO_DATA` | 灰色"暂无数据" + 指标标题保留 | 不显示 0、不空白、不隐藏卡片 |
| `UNAVAILABLE` | 黄色"暂不可用，请稍后重试" + 指标标题保留 | 不显示 0、不显示上次缓存值、不整页报错 |

- 单卡 `UNAVAILABLE` 只影响该卡；其他 `OK` 卡照常显示。
- 请求整体失败（400/401/403/500/网络）→ 页面级错误态，保留卡片骨架，全部置为"未加载"，**不把旧值继续标成最新**。
- 刷新失败但保留上次成功数据时：顶部显示"数据可能已过期 · 上次更新 HH:mm"，且不得更新 `asOf` 文案。
- 切换角色/课程/班级：清空旧数据、旧错误、旧缓存后重新加载。

### 4.2 卡片标题与口径（按后端实际公式，不按指标名望文生义）

| metricCode | 建议标题 | 单位 | 说明 / 待决 |
| --- | --- | --- | --- |
| `teacher.class.count` | 课程范围 | 条 | 是授权范围条数，未按教学班去重（D-18），不叫"我的班级数" |
| `teacher.student.count` | 班内学生人次 | 人次 | 各范围 `studentCount` 累加，未证明跨班去重（D-13） |
| `teacher.session.running` | 进行中课堂（含暂停） | 个 | `PAUSED` 计入（D-11），标题必须体现 |
| `teacher.assignment.pending` | 待提交学生数 | 人 | 是待提交学生数合计，不是任务条数（D-12） |
| `teacher.grade.pending` | 待处理成绩项 | 项 | 两个计数直接相加，重叠未定义（D-14） |
| `teacher.notice.unread` | 未读通知 | 条 | 不随 `classId` 过滤 |
| `student.class.count` | 在学课程范围 | 条 | 同 D-18 |
| `student.session.active` | 进行中课堂（含暂停） | 个 | 同 D-11 |
| `student.assignment.due` | 待办作业记录 | 条 | 是记录条数，不是任务数（D-12） |
| `student.notice.unread` | 未读通知 | 条 | |
| `admin.class.count` | 课程范围 | 条 | 同 D-18 |
| `admin.student.count` | 学生人次 | 人次 | 同 D-13 |
| `admin.session.running` | 进行中课堂（含暂停） | 个 | 同 D-11 |
| `admin.assignment.pending` | 待提交学生数 | 人 | 同 D-12 |
| `admin.grade.pending` | 待处理成绩项 | 项 | 同 D-14 |
| `admin.todo.pending` | 待办事项 | — | 恒 `NO_DATA`，副文案"来源待产品确认"，不显示 0 或"全部完成"（D-15） |

`sampleCount` 处理：**不在卡片上统一展示**。仅在 tooltip 中逐指标说明含义
（范围条数 / 绑定数 / 记录条数 / 不适用），避免被当成"样本量"误读（D-06）。

## 5. 下钻核对：actionPath vs 现有菜单与视图挂载

现有前端没有路由框架。三端各一个 HTML，菜单是硬编码数组，选中项经
`EduViews.mount(role, groupIndex, itemIndex, label)` 找到
`Views.register(role, groupIndex, itemIndex, factory)` 注册的工厂并挂到 `#viewRoot`。

实际菜单结构（本次核对自源码）：

| 角色 | 组 0 | 组 1 |
| --- | --- | --- |
| 教师 | 教学工作台：0 教学总览 / 1 今日课程·快捷上课 / 2 教学待办 / 3 班级消息·公告 / 4 AI 教学助手 | 我的课程：0 课程详情 / 1 课程建设 / 2 课前准备 / 3 课堂教学 / 4 课后作业与反馈 / 5 学情分析与教学改进 / 6 AI 教学助手 |
| 学生 | 学习工作台：0 学习总览·今日待办 / 1 正在上课·一键加入 / 2 作业与预习截止提醒 / 3 学习通知·课程公告 / 4 AI 学习助手 | 我的课程：0 课程详情 / 1 课程资料 / 2 课前学习 / 3 同步课堂 / 4 课后学习 / 5 我的学情 / 6 AI 学习助手 |
| 管理员 | 工作台：0 管理总览 / 1 待办事项 / 2 运行概况 | —（`admin.html` 尚无 `#viewRoot`，也未接 view-host） |

逐条核对结果：

| metricCode | 后端 actionPath | 现有可落点 | 结论 |
| --- | --- | --- | --- |
| `teacher.notice.unread` | `/teacher/notices` | 教师 (0,3) 班级消息/公告（已注册视图） | **可跳**，映射 `teacher:0:3` |
| `student.notice.unread` | `/student/notices` | 学生 (0,3) 学习通知/课程公告（已注册视图） | **可跳**，映射 `student:0:3` |
| `teacher.session.running` | `/teacher/classes` | 教师 (0,1) 今日课程/快捷上课 更贴近"课堂"；无"班级"菜单 | 语义不符，待定 |
| `teacher.assignment.pending` | `/teacher/assignments` | 教师 (1,4) 课后作业与反馈（候选，页面未实现） | 候选，待确认 |
| `teacher.grade.pending` | `/teacher/grades` | 教师 (1,5) 学情分析与教学改进（候选，页面未实现） | 候选，待确认 |
| `teacher.class.count` | `/teacher/classes` | 无对应项（"我的课程"是课程视角，不是班级列表） | 无落点 |
| `teacher.student.count` | `/teacher/classes` | 同上 | 无落点 |
| `student.session.active` | `/student/todos` | 学生 (0,1) 正在上课/一键加入 更贴近；`/student/todos` 对应"待办" | 语义不符，待定 |
| `student.assignment.due` | `/student/todos` | 学生 (0,2) 作业与预习截止提醒（候选，页面未实现） | 候选，待确认 |
| `student.class.count` | `/student/classes` | 无对应项 | 无落点 |
| `admin.class.count` / `admin.student.count` / `admin.session.running` | `/admin/classes` | 管理员组 0 只有 管理总览/待办事项/运行概况，无班级管理 | 无落点 |
| `admin.assignment.pending` | `/admin/assignments` | 无对应项 | 无落点 |
| `admin.grade.pending` | `/admin/grades` | 无对应项 | 无落点 |
| `admin.todo.pending` | `null` | 管理员 (0,1) 待办事项存在，但后端来源未确认 | **保持 null**，不跳 |

**结论：13 条 actionPath 中，只有 2 条能落到已存在的视图。** `/teacher/classes`、
`/student/classes`、`/admin/classes`、`/admin/assignments`、`/admin/grades` 这些路径
对应的页面在现有菜单里根本不存在，直接拿去当路由只会 404 或被迫新增页面。

## 6. 提给 6A / 产品的意见（本轮即提，不等页面）

### 6.1 建议变更：`actionPath` 改成"语义目标键"，不要用 URL 形态

现状风险：`actionPath` 长得像 URL，但前端没有路由框架；若各方默认它是 URL，
要么前端被迫造路由，要么下钻全部失效。建议二选一：

**方案 A（推荐）**：字段改为语义键，前端维护映射表

```json
{ "actionTarget": "notice.list" }
```

前端内置 `role × target → (groupIndex, itemIndex)` 映射，未实现的目标键渲染为不可点击。
后端不需要知道前端的文件与菜单下标，也不会因为前端改菜单而失效。

**方案 B（最小改动）**：保留字段名与取值，但**明确声明它是"目标意图标识"而非 URL**，
前端负责解析；未实现的目标一律不跳。这样无需改后端代码，只改文档语义。

无论哪种，请求：

1. 明确 `null` 表示"无下钻"，前端据此禁用点击（当前只有 `admin.todo.pending` 为 null）。
2. 不要新增前端尚不存在的路径（如 `/admin/classes`），避免形成"契约里有、前端无"的悬空引用。
3. 若第 3/4/5 组后续提供班级/作业/成绩页面，再由前端补映射并回写本文档。

### 6.2 建议确认：默认窗口时区（D-02）

学生菜单叫"学习总览 / **今日**待办"，教师有"今日课程"。而服务端默认窗口是 **UTC 自然日**：
北京时间 00:00–08:00 之间，`window` 实际对应的是"昨天 08:00 到今天 08:00"，
卡片数字与"今日"措辞会错位 8 小时。

建议：默认窗口改用业务时区（`Asia/Shanghai`），或产品明确接受"UTC 日"并在页面标注。
在此之前，前端**不会**自行传本地日边界改口径，只在页面显示服务端 `window` 与"UTC"标注。

### 6.3 建议明确：三项口径后才能定标题

- D-12 `assignment.pending`（待提交学生数）与学生 `assignment.due`（记录条数）不同粒度：
  教师端与管理端不应出现"待办任务 N 个"这种写法。
- D-14 成绩两个计数是否互斥未定，若不互斥，卡片标题不能写"待处理成绩 N 项"而不加限定。
- D-18 `class.count` 是否按教学班去重未定，标题暂用"课程范围"而不是"班级数"。

### 6.4 确认：错误与三态边界（与 handoff 一致，无需变更）

- 缺适配器/超时/单源异常 → HTTP 200 + item `UNAVAILABLE`，不是 503，也不是 0。
- 教师 `classId` 越权：课程范围源 `OK` → 403；范围源 `NO_DATA`/`UNAVAILABLE` → 对应状态 item，
  **不返回 403**。前端不会把后者当"有权限"，两种都按失败/无数据展示。
- 总览不返回 404；前端不做"路径未注册"推断。

## 7. 后续排期

分支策略：`feature/6-01-frontend`（基线 `six@0624225`），PR 先以 Draft 指向 `six`；
待 `#32` 合入 `main` 后改基 `main`，避免把 6-02 的改动混进本 PR 的 diff。

| 阶段 | 时间 | 产物 | 依赖 / 风险 |
| --- | --- | --- | --- |
| S0 契约回执 | 11-05（已完成） | 本文档；`actionPath` 语义与 D-02 意见 | 等 6A 对 6.1 / 6.2 回话 |
| S1 骨架与三态 | 11-06 ~ 11-07 | `src/api/education/overview.js`；三端总览视图；卡片三态组件；`Views.register` 挂教师/学生 (0,0)；`admin.html` 接 view-host + `#viewRoot` | `admin.html` 属第 1 组公共外壳，改动需评审标记 |
| S2 接口对接 | 11-08 ~ 11-09 | 四端点调用；400/401/403/500/网络失败态；切换与刷新缓存语义 | 权限串未配置时全是 403，属预期 |
| S3 下钻落地 | 11-10 | 映射表（仅 `notice.list` 等已确认目标可点）；其余置灰并在 tooltip 说明"页面未上线" | 取决于 6.1 结论 |
| S4 自测与提 PR | 11-11 | Node 断言（三态/单卡失败/整体失败/缓存）+ 无头渲染；Draft PR（base `six`） | 真实数据仍为 `UNAVAILABLE` |
| S5 真实联调 | 外部依赖解除后 | 真实登录、角色拦截、适配器数据、时区行为核对 | 第 1 组权限、第 2/4/5 组适配器；D-16/D-17 |

排期只覆盖 6-03（三端总览页面）与 6-01 前端侧的接口封装；6-04 的"今日待办"页面复用
学生总览视图，不单列阶段。管理员待办（`/admin/todos`）在 S1 就接入，恒 `NO_DATA`，
仅显示"来源待产品确认"，不做假数据。

### 阻塞清单（不解除也能做 S1–S4，但无法宣称联调通过）

1. 权限串 `education:overview:teacher|student|admin` 未配置 → 真实请求 403（第 1 组）。
2. 生产适配器未接入 → 真实调用多为 `UNAVAILABLE`（第 2/4/5 组）。
3. `admin.html` 无 `#viewRoot`、未接 view-host（属公共外壳，需第 1 组评审）。
4. D-02 / D-11 / D-12 / D-13 / D-14 / D-18 未定 → 卡片标题与"今日"措辞可能返工。
5. `actionPath` 语义未定 → 下钻可能整体重做（S3 风险最高的一项）。

## 8. 状态声明

- 本文为**契约理解与意见**，不是接口确认，也不是联调结论。
- 前端页面/API/路由**尚未实现**；实现后补分支、PR、head、页面与 API 文件位置、截图与运行证据。
- 后端示例中的数值仅示意字段，当前真实调用应为 `UNAVAILABLE` / `NO_DATA`。
