# 6-03 管理员 Vue 总览交接记录

日期：2026-11-20

本地接入更新：2026-11-26
状态：**本地临时集成分支已完成管理员页面注册、自动侧边栏和实际入口回归；第 1 组 #41@549ef8e 已提供四个总览菜单锚点并报告其环境真实账号验证，目标环境执行与真实联调仍待完成。**

当前快照基线（2026-11-26 发布审阅快照时核验）：

- main `1e3109f803689cf7a4a25dbb81bfac89029d5252`
- #41 `codex/group1-overview-permission-handoff@549ef8e0470bcd44322953371557605379dbf530`（Draft）
- #37 `feature/6-03-vue-admin-overview@97460b21c5b1175ac47b5ed460dde3a0a2d66841`（Draft）
- 组合基线 `2e154aee19ed1591ebc4f13105308ffd2c9c9e87`（main、#41、#37 的普通合并）
- 本轮产品代码提交 `c4a92c6fa80a492e3a02e9f22049d776f1c4c7ae`

以下两段是 **#37 组件交付时的历史依据与同步说明**，不代表本地当前快照状态；当前接线见 5.1。

（历史）依据：#24 `feature/6-01-overview-api@233ee45a2af2d73e96fe09f110a87bcb39362df2`；#36 同步基线 `fe4f4f228a8a16a13bd0f7c36c619fba4223a2fb`；main `232f463f73800a40d6b8e48b8b4f6b47b78a3170`。

（历史）同步说明：管理员组件已预合并到 #36 同步结果，但仍只保留隔离测试入口。#24 仍为 Draft，其接口、Controller 和后端实现未合入 main；本文的后端依据是合同与实现分支，不代表这些接口已在 main 可用。

## 1. 本轮范围

新增：

- `frontend/src/views/education/admin/AdminOverview.vue`
- `frontend/src/views/education/admin/AdminTodos.vue`
- `frontend/tests/admin-overview.html`
- `frontend/tests/admin-overview-entry.mjs`
- `frontend/tests/admin-overview.spec.mjs`

扩展：

- `frontend/src/api/education/overview.js`
- `frontend/src/components/education/OverviewBoard.vue`
- `frontend/tests/README.md`

未修改生产路由、`permission.js`、`ui-pages.js`、`overview-pages.js`、`education/index.js`、`HomeView.vue`、`AppLayout.vue`、后端、SQL 或依赖文件。

## 2. 页面与端点

| 页面 | 端点 | 指标 |
|---|---|---|
| 管理员管理总览 | `GET /education/admin/overview` | 5 个：class/student/session/assignment/grade |
| 管理员待办 | `GET /education/admin/todos` | 1 个：`admin.todo.pending` |

两个端点只发送可选 `from`、`to`。组织范围由服务端身份上下文 `deptId` 决定，前端不发送 `organizationId`、`deptId`、`userId`、`classId` 或 `bindingId`。

`OverviewBoard` 使用明确端点映射；未知 `endpoint` 显示配置错误并停止请求，不默认调用学生端点。

## 3. 管理员待办契约

只有以下字段全部符合时才显示“待办口径待确认”：

- `metricCode=admin.todo.pending`
- `status=NO_DATA`
- `value=null`
- `unit=COUNT`
- `source=pending_product_confirmation`
- `sampleCount=null`
- `actionPath=null`

任何 401/403/网络/503/顶层结构异常或字段违约都显示页面错误或卡片数据异常，不显示 0 条、全部完成或固定空态。

## 4. 隔离测试入口

生产构建入口仍是 `src/main.js`；`tests/admin-overview.html` 与 `tests/admin-overview-entry.mjs` 只在 Vite 开发服务器下加载，不进入 `dist`。

```bash
cd frontend
npm run build
npx vite --host 127.0.0.1 --port 4175
# 另开终端
ADMIN_OVERVIEW_BASE=http://127.0.0.1:4175/tests/admin-overview.html \
  node tests/admin-overview.spec.mjs
```

截图默认跳过；需要时设置仓库外目录：

```bash
ADMIN_OVERVIEW_SCREENSHOT_DIR="$HOME/admin-overview-shots" \
ADMIN_OVERVIEW_BASE=http://127.0.0.1:4175/tests/admin-overview.html \
  node tests/admin-overview.spec.mjs
```

## 5. 历史状态与风险记录

以下是 **#37@97460b2 / 旧 #24 检查时**的历史状态，不是当前本地 `proto/6-03-vue-admin-entry` 的完成状态。当前状态见 5.1。

### 权限和路由（历史）

- 当时教师/学生总览由 #36 工作台快捷入口进入独立路由；管理员页面尚无生产路由或快捷入口。
- 权限编码 `education:overview:admin` 已被 #24 `@PreAuthorize` 使用。
- 当时 serviceRoute 只是候选：`education/admin/overview`、`education/admin/todos`。
- 当时 `sys_menu`、管理员角色授权和 `getRouters` 尚未配置。

### 管理员路由守卫循环风险（历史）

当时 `frontend/src/permission.js` 会把管理员首页导向 `/admin/overview`。若先注册管理员总览页面但 `canOpen=false`，曾担心再次导向 `/admin?denied=1` 形成重定向循环。

第一组守卫修复已进入本地组合版本；当前实测缺权限、缺 serviceRoute、登录后撤权和角色不匹配均稳定落到 `/admin/forbidden` 或角色工作台，无循环。仍待公共外壳复审和目标环境验证，不能笼统写成“守卫仍有循环”。

### 适配器和联调

- `CourseScopePort`、`SessionStatusPort`、`AssignmentTodoPort`、`GradeTodoPort`、`NoticeSummaryPort` 生产适配器未接入。
- 真实登录、HTTP、服务端权限拦截、审计、数据库和真实数据联调未完成。
- 组件隔离测试只证明组件、参数、三态、请求失效和布局；新增实际入口夹具验证本地构建的路由/守卫/侧边栏，但不代表真实后端、登录或数据库。
- 管理员总览的班级/学生未去重、pending 为待提交人次、PAUSED 计入活跃、成绩计数直接相加等仍按 #24 当前口径，产品冻结前不得改写。

## 5.1 本地临时集成接线（2026-11-26）

本地临时分支 `proto/6-03-vue-admin-entry` 已在普通合入 #41、#37 后完成以下接线；未 push、未修改 PR。

### 页面注册

使用统一 `registerBusinessPage` 注册：

| 页面 | path | roles | permission | serviceRoute | 组件 |
|---|---|---|---|---|---|
| 管理员教学指标 | `teaching-metrics` | `admin` | `education:overview:admin` | `education/admin/overview` | `AdminOverview.vue` |
| 管理员待办 | `todos` | `admin` | `education:overview:admin` | `education/admin/todos` | `AdminTodos.vue` |

注册 id：`admin-teaching-metrics`、`admin-todos`；与已有 `teacher-overview`、`student-overview`、`overview` 不重复。
本轮**不新增静态菜单条目**；管理员页面依靠 `group='工作台'` 由现有 `buildAdminGroups` 自动加入侧边栏。静态菜单文件 `ui-menu.js`、`navigation.js` 保持组合基线 `2e154ae` 内容，默认分组折叠行为不变。路径分别为 `/admin/teaching-metrics`、`/admin/todos`。第一组 `/admin/overview` 保持原页面和 `/education/overview` 请求不变。

### 第一组 #41 锚点与当前边界

- 第 1 组 #41@549ef8e 已提供四个总览菜单锚点，并在其环境报告真实账号验证；`serviceRoute` 值已双方确认。
- 管理员锚点：`path=admin/overview`、`component=education/admin/overview`；`path=admin/todos`、`component=education/admin/todos`。
- 本轮组合分支包含 #41 菜单脚本，但**没有在本轮目标库执行 SQL**，也没有做真实账号联调。
- 正式 #37@97460b2 仍未注册生产入口；本地 `proto/6-03-vue-admin-entry` 已注册两个管理员页面，二者分开表述。
- 第一组守卫修复已进入本地组合版本；仍待公共外壳复审和目标环境验证。

### 路由与拒绝

- 仅删除 `frontend/src/router/index.js` 中 `/admin/todos` → `/admin/overview#overview-tasks` 的临时重定向；其余重定向、`/admin/forbidden`、CurrentTerm 和课程上下文保留。
- 未修改 `permission.js`、`ui-pages.js` 注册器、公共守卫或后端。
- 缺权限、缺目标 `serviceRoute`、登录后撤权均由既有 `canOpen` 和 `permission.js` 导向 `/admin/forbidden`；新增浏览器用例通过应用自身链接导航触发重新核验，验证无跳转循环、拒绝后不新增管理员总览请求。
- 教师、学生直达管理员路径由公共角色守卫拒绝并返回自身工作台；教师 `/teacher/teaching-overview` 仍按原注册打开。

### 验证入口

新增 `frontend/tests/admin-entry.spec.mjs`，通过 `tests/serve-dist.mjs` 加载实际构建，驱动实际 Vue Router、canOpen、自动侧边栏和页面组件；Playwright 仅提供 `/getInfo`、`/getRouters`、上下文和总览接口夹具，不连接真实后端或数据库。`getRouters` 使用第一组实际 `admin/overview`、`admin/todos`、`teacher/overview`、`student/today-todos` path/component，不使用虚构组件名。

```bash
cd frontend
corepack pnpm install --frozen-lockfile
corepack pnpm build
node tests/serve-dist.mjs
# 另开终端
ADMIN_ENTRY_TEST_BASE=http://127.0.0.1:4174 node tests/admin-entry.spec.mjs
```

本轮未执行生产菜单 SQL、真实登录、HTTP 服务端授权、审计或数据库联调。生产目标库的 `sys_menu`、管理员角色授权和 `getRouters` 仍需第一组执行并复核；`permission.js` 与 `router/index.js` 为共享外壳文件，需第一组复审。

### 本地验证结果（2026-11-26 收口）

- 前端构建（本轮）：`corepack pnpm install --frozen-lockfile`、`corepack pnpm build` 成功。
- 管理员实际入口（本轮修订后）：`admin-entry.spec.mjs` **7/7 通过**。
  - 每次导航/点击只检查新增请求；教学指标、待办和第一组总览分别断言精确 GET 端点。
  - 两页分别覆盖缺权限、缺自身 serviceRoute、登录后通过 SPA 链接撤权、教师直达、学生直达；拒绝后无管理员总览请求。
- 以下历史回归来自原接线提交 `33a2891` 的验证，本轮因未修改对应组件而未重跑：
  - 教师/学生总览：`overview-vue.spec.mjs` 24/24。
  - 公告/通知：`notice-vue.spec.mjs` 85/85。
  - 管理员组件隔离：`admin-overview.spec.mjs` 10/10。
  - 公共弹窗冒烟：`confirm-dialog-smoke.spec.mjs` 1/1。
- 后端 `mvn -pl ruoyi-admin -am -DskipTests package` 来自组合基线验证，本轮未重跑。

以上均为 Playwright 接口夹具和本地静态构建结果；未启动真实后端或数据库，不代表真实登录、HTTP、服务端授权、审计、生产菜单或 MySQL 联调通过。

## 6. 后续接入条件

TODO(6-03-admin-route)：第 1 组 #41@549ef8e 已提供四个总览菜单锚点并报告其环境真实账号验证；本轮组合分支包含脚本但未在目标库执行 SQL。正式 #37@97460b2 仍未注册生产入口；本地 proto 已注册。第一组守卫修复已进入本地组合版本，仍待公共外壳复审和目标环境验证。解除条件：

1. 在目标环境执行第一组菜单脚本，确认 `education:overview:admin` 管理员角色授权和 `getRouters` 返回 `admin/overview`、`admin/todos`、`teacher/overview`、`student/today-todos`；
2. 公共外壳负责人复审 `permission.js` / `router/index.js` 的拒绝和占位行为；
3. 完成真实登录、HTTP、服务端授权、审计和目标数据库联调；
4. 保留 `admin-entry.spec.mjs` 等有效回归测试；真实联调是独立验证层次，不以删除浏览器夹具作为完成条件。
