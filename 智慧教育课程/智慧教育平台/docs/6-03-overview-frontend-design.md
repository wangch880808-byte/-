# 6-03 三端总览前端接入设计与阶段1实现记录

> 版本：0.1｜日期：2026-11-05｜负责人：第 6 组 6A/6B
> 状态：**阶段1、阶段2已本地实现并自测；角色 HTML 已接入；未创建 PR、未转 Ready、未合并；远端发布待完成。**
> 依据：`main@f21f51d`、PR #32 `six@0624225`、PR #24 `feature/6-01-overview-api@233ee45`。
> 说明：本文和阶段1 Mock 只证明页面/契约消费行为，不证明真实 HTTP、登录、授权、审计、生产数据或 MySQL。

## 1. 范围与边界

阶段1只交付：
- 总览 API 模块；
- 共享页面组件；
- 教师/学生/管理员/管理员待办四份配置；
- 总览样式；
- 独立 Mock 自测预览；
- 本设计/实现文档。

阶段1**不修改** `teacher.html`、`student.html`、`admin.html`，因此总览尚未挂到真实角色页；阶段2再做公共外壳接入。

不开发：校园资讯、留言、通知模板、运行概况、过程分析、问题研判、AI/模型/聊天页。

## 2. 基线与依赖

| 对象 | SHA/状态 | 用途 |
| --- | --- | --- |
| `origin/main` | `f21f51d55c363738c03ee6f626c7c67b904d6b36` | 任务分支起点 |
| PR #32 `six` | `0624225baa444462962f066d764f51e03d193d47`，Draft，base main | 复用 http/ui-kit/view-host、6-02 页面、打包/静态资源基础 |
| PR #24 | `233ee45a2af2d73e96fe09f110a87bcb39362df2`，Draft，base #23 | 四个总览端点的接口依据；不进入前端分支 |
| 任务分支 | `feat/frontend-6-03-overview-pages` | 从 main 创建后普通 merge `six`，merge commit `c194e71` |

#32 相比旧 `ffa1cf2` 的最新修订：
`0624225ba fix(6-02): 按学长收口意见修正 503 分级、401 统一处理与文档同步`，只改 `http.js`、`ui-kit.js` 和两份 6-02 文档。阶段1未改这些共享文件（`mock.js` 除外，见第 6 节）。

## 3. 设计修正落实

| 修正要求 | 落实位置 |
| --- | --- |
| 教师/管理员 `class.count` 名称“授权班级范围” | `teacher/overview.js`、`admin/overview.js` |
| 学生 `class.count` 名称“在修班级范围” | `student/overview.js` |
| 学生 `session.active` 名称“活跃课堂”，不暗示可加入 | `student/overview.js` |
| 教师/管理员 `assignment.pending` 名称“待提交人次”，说明非跨任务去重、非任务数 | 两份配置 |
| 学生 `assignment.due` 名称“待办记录” | `student/overview.js` |
| 正式卡片不显示 metricCode/端口/PR/binding 数量 | `overview-page.js` 的 `cardHtml`；指标编码只在 `overview-preview.html` 调试区 |
| 页面级时间改为“响应时间” | `overview-page.js` 的 `renderMeta` |
| 自定义窗口不再写“今日课堂/今日待办” | `student/overview.js` 的 `customLabel`，`groupLabel()` 按窗口切换 |
| 管理员待办真实调用接口，只有合法 NO_DATA 显示固定文案 | `admin/todos.js` + `overview-page.js` 的 `requireNoData/expectedSource/requireNullActionPath` |
| 管理员待办 401/403/失败/违约显示错误 | `handleError`、`renderPageState`；Mock 覆盖 401/403/error/非法 OK 0 |
| classId 按字符串保留，不经过 Number/parseInt | `overview-page.js` 的 `buildQuery`；API 模块原样传递 |
| 按 metricCode 映射卡片 | `overview-page.js` 的 `byCode`/`normalizeItem` |
| 单项违约只降级对应卡片 | `normalizeItem` 返回卡片级 `contract` 状态 |
| 整体结构违约显示页面错误 | `handleSuccess` 校验 data/items/asOf/window |
| 阶段2只开放已核验导航 | 学生通知卡跳既有学生通知菜单；其他入口不点击、不读取 `actionPath` |
| 所有真实请求入口统一等待登录 | `beginAuth()` + `requestLoad()`；筛选、清除、恢复默认、刷新、BFCache 恢复都不能绕过 |
| pending 期间只保留最新筛选 | `pendingReload`；登录通过后只请求一次，不重复派发 |
| 核验失败/超时不能直接请求 | `authState=failed` 时 `requestLoad()` 只显示登录错误；重试走 `retry-auth` 重新核验 |
| pagehide/BFCache 使旧登录等待失效 | `authTicket`、`authState=stale`；pageshow 重新 `beginAuth(true)` |
| cleanup 后迟到核验/请求不写页面 | `disposed` + `authTicket` + `seq` 三重检查 |
| 重复 metricCode 降级 | `duplicateCodes` → 单卡 `contract`，不保留首项、不求和、不取最大值 |
| unit 必须 COUNT | `normalizeItem` 校验 `raw.unit === 'COUNT'` |
| source 必须与配置一致 | `normalizeItem` 校验 `raw.source === metric.source` |

## 4. 页面与指标

### 4.1 教师 · 教学总览（6 指标）

| metricCode | 卡片 | 当前口径 | 窗口 | 可下钻 |
| --- | --- | --- | --- | --- |
| `teacher.class.count` | 授权班级范围 | `scopes.size()`，未按教学班去重 | 快照 | 否 |
| `teacher.student.count` | 学生数（按班汇总） | 各范围求和，未跨班去重 | 快照 | 否 |
| `teacher.session.running` | 活跃课堂 | RUNNING + PAUSED | 是 | 否 |
| `teacher.assignment.pending` | 待提交人次 | 各任务待提交人数合计，非跨任务去重人数、非任务数 | 是 | 否 |
| `teacher.grade.pending` | 待处理成绩 | `pendingGradeCount + pendingPublishCount` | 快照 | 否 |
| `teacher.notice.unread` | 未读通知 | 本人 `unreadCount`，不随 classId | 快照 | 否 |

### 4.2 学生 · 学习总览 / 今日待办（4 指标）

| metricCode | 卡片 | 当前口径 | 窗口 | 可下钻 |
| --- | --- | --- | --- | --- |
| `student.class.count` | 在修班级范围 | `scopes.size()`，未按教学班去重 | 快照 | 否 |
| `student.session.active` | 活跃课堂 | RUNNING + PAUSED，不表示一定可加入 | 是 | 否 |
| `student.assignment.due` | 待办记录 | 端口返回记录条数；空 List 为 NO_DATA，无 OK 0 | 是 | 否 |
| `student.notice.unread` | 未读通知 | 本人全部未读；目标通知页按当前班级上下文筛选 | 快照 | 已开放既有学生通知菜单 |

### 4.3 管理员 · 管理总览（5 指标）

| metricCode | 卡片 | 当前口径 | 窗口 |
| --- | --- | --- | --- |
| `admin.class.count` | 授权班级范围 | `scopes.size()`，未按教学班去重 | 快照 |
| `admin.student.count` | 学生数（按班汇总） | 各范围求和，未跨班去重 | 快照 |
| `admin.session.running` | 活跃课堂 | RUNNING + PAUSED | 是 |
| `admin.assignment.pending` | 待提交人次 | 各绑定 `pendingCount` 求和 | 是 |
| `admin.grade.pending` | 待处理成绩 | 两个计数直接相加 | 快照 |

### 4.4 管理员 · 待办事项（1 指标）

- `admin.todo.pending`；
- 真实调用 `GET /education/admin/todos`；
- 仅当状态为合法 `NO_DATA`、`value=null`、`source=pending_product_confirmation`、`actionPath=null` 时显示“待办来源尚未确定，当前暂无数据”；
- 任何 401/403/400/404/500/网络/契约违约都显示页面错误，不使用固定空态；
- 不生成待办数量或列表，不显示 0 条/全部完成。

### 4.5 三态与数值

| 状态 | 显示 | 约束 |
| --- | --- | --- |
| OK | 非负整数；0 显示 0 | value 必须为非负整数 |
| NO_DATA | 暂无数据（管理员待办使用固定文案） | value 必须为 null |
| UNAVAILABLE | 暂不可用 | value 必须为 null；单项失败保留其他卡片 |
| 单项违约 | 数据异常 | 缺指标/非法 status/value/来源不符等，只降级当前卡片 |
| 整体违约 | 页面错误 | 缺 data、items 为空、asOf/window 非法 |

`sampleCount` 属于契约元数据，正式卡片不展示；预览调试区显示原始 sampleCount，便于核对口径，不当统一分母。

## 5. 请求生命周期与交互

- 请求序号 `requestSeq`：每次刷新/筛选递增；响应返回时序号不匹配或视图已销毁则丢弃。
- `disposed`：`cleanup` 后不再写页面、不再挂新监听。
- `authState`：`idle/pending/ready/failed/stale`。真实模式所有请求入口经 `requestLoad()`：
  - `ready` 才允许 `load()`；
  - `pending` 只设置 `pendingReload=true`，保留最新筛选，不调用 API；
  - `failed/stale` 只显示登录错误，筛选/刷新不能绕过；
  - 重试按钮 `retry-auth` 重新调用公共 `Views.whenAuthorized()`，核验完成前仍不请求。
- `authTicket`：每次核验递增；pagehide、cleanup、重新核验都会使旧核验结果失效。
- `pagehide`：使在途 API 和旧登录等待同时失效；`pageshow(persisted)` 清空旧状态、重新 `beginAuth(true)`，核验完成前零调用。
- cleanup 后，迟到的登录核验结果、尚未执行的 `load()` 微任务和 API 响应都被 `disposed/authTicket/seq` 拦截，不重新请求、不写页面。
- 筛选应用后立即清空旧卡片为骨架，旧值不当作新结果。
- 旧响应后到不会覆盖新筛选。
- `load()` 在 `Promise.resolve().then` 内、真正调用 API 前再次检查 `ticket/disposed/pageHidden/authState`；已排队但被 cleanup/pagehide/新筛选/登录回退失效的请求直接跳过，不调用 API，也不把跳过值送入 `handleSuccess` 制造契约错误。
- 当前 `http.js` 不支持 `AbortController.signal`，阶段1只做“失效丢弃”，**不宣称底层 HTTP 已取消**。TODO：共享客户端支持 signal 后再补真正取消；解除条件为 `http.js` 增加兼容 signal 参数并通过回归。
- 时间输入使用 `datetime-local`，提交转 `toISOString()`；前后端都做 `from/to` 成对和顺序校验。
- `classId` 输入只接受 `/^[1-9][0-9]*$/` 字符串；不 `Number()`/`parseInt()`，避免 Long 精度损失。
- 学生自定义时间窗时，分组标题改为“时间窗内课堂/时间窗内待办”。

## 6. Mock 与隔离

- 新增 `frontend/overview-preview.html`，固定黄色警示：Mock、不连后端、不校验登录、不是真实联调。
- 预览调用 `EduMock.enable({ memoryOnly: true, preserveBinding: true })`：
  - 只设置当前页面内存开关，不写 `sessionStorage`；
  - 不自动写入演示 `bindingId`；
  - 不改真实登录令牌/角色/教学班上下文。
- `mock.js` 新增四个总览路由和场景：normal、zero、nodata、unavailable、mixed、missing、invalid、empty-items、missing-data、401、403、400、404、500、network、adminTodoOkZero、adminTodosError。
- 总览请求在 Mock 中由 handler 自己控制延迟，并在请求进入时固定响应快照，用于并发乱序测试；未覆盖路径仍返回“Mock 未覆盖该接口”，真实失败不会回落到 Mock。
- 正式卡片不显示调试信息；预览调试区才显示 metricCode/status/value/source/sampleCount/actionPath/原始响应。
- 预览的全局 Mock 浮标已隐藏，避免遮挡 390px 表单；预览自身保留固定警示条。

## 7. 文件清单（阶段1）

新增：
- `frontend/src/api/education/overview.js`
- `frontend/src/components/education/overview-page.js`
- `frontend/src/views/education/overview.css`
- `frontend/src/views/education/teacher/overview.js`
- `frontend/src/views/education/student/overview.js`
- `frontend/src/views/education/admin/overview.js`
- `frontend/src/views/education/admin/todos.js`
- `frontend/overview-preview.html`
- `frontend/tests/overview-stage1.html`
- `frontend/tests/overview-stage1.js`
- `frontend/tests/README.md`
- `docs/6-03-overview-frontend-design.md`

修改：
- `frontend/src/api/education/mock.js`：总览路由/场景 + memoryOnly 预览隔离。
- `frontend/README.md`：6-03 阶段1说明。

未修改：`teacher.html`、`student.html`、`admin.html`、Java、SQL、`pom.xml`、`SecurityConfig`、#24/#32 文档残留。

## 8. 阶段1验证结果

工具与环境：
- Node.js `v24.21.0`（仅语法/测试）；
- 本机 Edge + Playwright `1.64.1`（npx 缓存工具，不建产品依赖）；
- Python 静态服务器；
- 未运行 Maven、未启动后端、未连接 MySQL/Redis。

命令：
```bash
cd "G:/智慧教育全过程/_workspace/6-03-repo/智慧教育课程/智慧教育平台"
node --check frontend/src/api/education/overview.js
node --check frontend/src/components/education/overview-page.js
node --check frontend/src/views/education/teacher/overview.js
node --check frontend/src/views/education/student/overview.js
node --check frontend/src/views/education/admin/overview.js
node --check frontend/src/views/education/admin/todos.js
node --check frontend/src/api/education/mock.js

cd frontend
python -m http.server 8125 --bind 127.0.0.1

# 仓库内阶段1回归（真实模式登录等待 + 契约违约）
PLAYWRIGHT_MODULE="<playwright 模块路径>" PLAYWRIGHT_CHANNEL=msedge OVERVIEW_TEST_BASE=http://127.0.0.1:8125 node frontend/tests/overview-stage1.js

# 既有 Mock 预览回归（脚本位于仓库外）
cd "G:/智慧教育全过程/_workspace"
node check-overview.js
```

结果：
- 全部新增/修改 JS 语法检查通过。
- `frontend/tests/overview-stage1.js` 首轮修复前（当时 30 项断言）：**11 passed，19 failures**；首轮修复后（新增 retry-auth 3 项）：**33 passed，0 failures**。
- 微任务窗口修订前（38 项断言）：**35 passed，3 failures**；修订后：**38 passed，0 failures，0 console/page errors**。测试 context 显式设置 `timezoneId: 'Asia/Shanghai'`，不依赖执行机器时区。
- `check-overview.js`：**0 failures，0 console/page errors**。
- 覆盖：
  - 四端点路径/参数；不含 userId/studentId/organizationId；
  - `classId='9007199254740993'` 原字符串透传，无精度损失；
  - 16 指标卡片数量和标题；
  - 真实零、NO_DATA、UNAVAILABLE、混合；
  - 缺指标、非法 status/value、空 items、缺 data；
  - from/to 单边/倒置不调用 API；
  - 连续筛选乱序，旧响应不覆盖新结果；
  - cleanup 后迟到响应不写入；
  - 管理员待办正常 NO_DATA、401、失败、非法 OK 0 均不被固定空态遮盖；
  - Mock 不调用真实 `fetch`；
  - 预览不写 `sessionStorage`、不改 bindingId、不写登录令牌；
  - 6-02 教师公告/学生通知 Mock 回归通过；
  - 390×844 实际视口单列、无水平溢出；
  - 登录 pending 时筛选/清除/刷新零 API 调用；
  - pending 连续操作只保留最新筛选，核验通过后只请求一次；
  - 核验 false/超时后筛选不能绕过，重试先重新核验；
  - BFCache 恢复重新核验，核验完成前零调用；
  - cleanup 后迟到核验不请求、不写页面；
  - 重复 metricCode（相同/冲突、交换顺序）单卡降级；
  - unit=PERCENT、source 与配置不一致单卡降级；
  - 正常 COUNT + 合法 source 不回归；
  - 登录核验 resolve 后、API 微任务派发前 cleanup 不调用 API；
  - request 已排队但 pagehide/新筛选使其失效，只保留最新有效请求。

截图：
- `G:\智慧教育全过程\_workspace\preview-teacher.png`
- `preview-student.png`
- `preview-admin.png`
- `preview-admin-todos.png`
- `preview-mixed-debug.png`
- `preview-403.png`
- `preview-400.png`
- `preview-admin-todos-error.png`
- `preview-mobile-390.png`

未验证：
- 真实 HTTP 四端点；
- 真实登录/401/403/权限串；
- 生产适配器数据、时间窗、性能；
- MySQL 8.0.46；
- 公共外壳下实际挂载（阶段2）。

## 9. 阶段2角色页接入（已完成）

### 9.1 挂载与脚本顺序

| 页面 | 菜单注册 | 脚本顺序 |
| --- | --- | --- |
| `teacher.html` | `teacher (0,0)` 教学总览；保留 `(0,3)` 公告 | `http → mock → announcement → overview API → ui-kit → view-host → overview-page → teacher/announcements → teacher/overview → 原 shell → role-home` |
| `student.html` | `student (0,0)` 学习总览；保留 `(0,3)` 通知/公告 | `http → mock → announcement → notice → overview API → ui-kit → view-host → overview-page → student/announcements → student/notices → student/overview → 原 shell → role-home` |
| `admin.html` | `admin (0,0)` 管理总览、`(0,1)` 待办事项 | `http → mock → overview API → ui-kit → view-host → overview-page → admin/overview → admin/todos → 原 shell → role-home` |

管理员页只新增静态资源、`#viewRoot`、`select()` 中的 `EduViews.mount`；未改菜单、身份、权限、登录、页签和退出逻辑。教师/学生页只追加样式和脚本引用。

### 9.2 学生通知导航与范围

- 通知卡入口：`?g=0&i=3`，即既有学生通知菜单；不读取服务端 `actionPath`。
- 卡片值来自 `student.notice.unread`，是本人全部未读。
- 目标通知页当前使用 `EduViews.getBinding()` 的课程—教学班上下文；有 binding 时只显示该范围。
- 因此卡片文案明确写“本人全部未读；通知页按当前班级上下文显示”，不把两个范围写成同一口径。
- 其他未实现来源不点击；不替其他组实现页面。

### 9.3 阶段2测试

仓库内脚本：`frontend/tests/overview-stage2.js`（角色 HTML + Playwright 路由夹具）。

覆盖：
- 教师/学生/管理员总览和管理员待办实际挂载；教师刷新/时间筛选；
- 登录核验完成前零总览请求、核验失败后重试；
- 错角色、401、403；
- 教师公告、学生通知/公告页签、学生角标回归；
- 管理员非总览占位；
- 视图切换后迟到响应不写回；
- 学生通知入口与范围提示；目标通知页继承 binding 时请求带 `bindingId`，卡片仍标注本人全部未读；
- 390px 总览单列、无溢出，离开总览后移除局部样式类且原外壳无水平溢出；
- 退出登录。

结果：**41 passed，0 failures**（路由夹具测试，不是真实后端/登录联调）。

截图：
- `G:\智慧教育全过程\_workspace\stage2-teacher.png`
- `stage2-student.png`
- `stage2-admin.png`
- `stage2-admin-todos.png`
- `stage2-student-notices.png`
- `stage2-student-announcements.png`
- `stage2-student-mobile.png`
- `stage2-student-notices-390.png`

## 10. 阶段2公共外壳复审清单与风险

已修改，需复审：
- `frontend/teacher.html`：引入 `overview.css` 和 `teacher/overview.js`；不改登录/菜单/页签。
- `frontend/student.html`：引入 `overview.css` 和 `student/overview.js`；不改通知角标和 6-02 页面。
- `frontend/admin.html`：增加 `education-views.css`、`overview.css`、`http.js`、`mock.js`、`ui-kit.js`、`view-host.js`、总览脚本、`#viewRoot`，并在 `select()` 调用 `EduViews.mount`；保留登录/菜单/页签/退出。

风险：
- `admin.html` 是公共外壳，需第 1 组/负责人 review；改动只在挂载和静态资源，不重写登录态。
- `teacher.html`/`student.html` 已有 #32 脚本顺序；追加总览脚本必须保持 `http -> mock -> api -> ui-kit -> view-host -> overview-page -> role config` 顺序。
- 学生手机局部样式依赖 `body.edu-overview-active`；只在总览挂载期间生效，不影响 #32 页面。离开总览后恢复原公共外壳；原学生通知页在外壳层面仍是桌面布局，390px 截图不能作为通知页移动适配结论。
- 真实环境没有 #24 时，总览显示 404/接口不可用，不能显示 0 或示例数据。
- #32 若在阶段2前更新，先普通 merge 最新 `six`，复核 `http.js/ui-kit.js/view-host.js` 与三个 HTML 的最终语义。

## 11. 待确认口径

- 默认 UTC 自然日 vs 业务时区/学期；
- `class.count` 是否按教学班去重；
- `student.count` 是否跨班去重；
- 教师/管理员 pending、学生 due 的产品卡片名称与统计单位；
- PAUSED 是否计入“进行中”；
- 成绩两个计数是否互斥；
- `deptId` 与组织范围映射；
- `sampleCount`/`asOf` 对外语义；
- `actionPath` 到角色 HTML/Views.register 的映射；
- 管理员待办来源、处理人、权限、状态机；
- 2-04 课程/教学班列表接口（当前 classId 只能手工输入）。

前端按当前代码口径展示，不自行补算、不修改后端公式、不把建议写成已批准。

## 12. 参考网站

`https://nus.zknu.edu.cn/` 本轮只访问到公开登录页（HTTP 200；左侧品牌区、右侧登录卡）；未登录、未提交表单、未执行写操作，没有参考登录后的工作台。设计沿用本项目现有浅色外壳和主色，不复制参考站品牌、数据或内部模块。

## 13. 停止点

- 已完成代码、文档和自测；
- 待普通提交、推送任务分支；
- 不创建 PR、不转 Ready、不合并、不改角色 HTML；
- 阶段2需用户审查后继续。
