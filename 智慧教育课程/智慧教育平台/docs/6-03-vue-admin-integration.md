# 6-03 管理员 Vue 总览交接记录

日期：2026-11-20

本地接入更新：2026-11-26
状态：**本地临时集成分支已完成管理员页面注册、侧边栏锚点和实际入口回归；生产菜单、角色授权和 getRouters 仍需第一组配置与复审。**
依据：#24 `feature/6-01-overview-api@233ee45a2af2d73e96fe09f110a87bcb39362df2`；#36 同步基线 `fe4f4f228a8a16a13bd0f7c36c619fba4223a2fb`；main `232f463f73800a40d6b8e48b8b4f6b47b78a3170`。

同步说明：管理员组件已预合并到 #36 同步结果，但仍只保留隔离测试入口。#24 仍为 Draft，其接口、Controller 和后端实现未合入 main；本文的后端依据是合同与实现分支，不代表这些接口已在 main 可用。

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

## 5. 接入条件与风险

### 权限和路由候选

- 当前教师/学生总览由 #36 工作台快捷入口进入独立路由；管理员页面仍没有生产路由或快捷入口。
- 权限编码 `education:overview:admin` 已被 #24 `@PreAuthorize` 使用。
- serviceRoute 候选：`education/admin/overview`、`education/admin/todos`。
- `sys_menu`、管理员角色授权和 `getRouters` 尚未配置，需第 1 组/负责人确认。

### 管理员路由守卫循环风险

`frontend/src/permission.js` 当前会把管理员首页导向 `/admin/overview`。如果先注册管理员总览页面但 `canOpen` 为 false，守卫会再次导向 `/admin?denied=1`，可能形成重定向循环。

生产接入必须同时满足：

1. 菜单、管理员角色授权和 `getRouters` 配置正确；
2. `canOpen=false` 时拒绝路径不循环，覆盖缺权限、权限撤销、缺服务端路由等情况，并经公共外壳负责人复审。

权限齐全时能进入页面，不能证明拒绝路径正确。本轮不修守卫、不替换 `BlankAdminView` 占位，也不注册 `/admin/overview`、`/admin/todos`。

### 适配器和联调

- `CourseScopePort`、`SessionStatusPort`、`AssignmentTodoPort`、`GradeTodoPort`、`NoticeSummaryPort` 生产适配器未接入。
- 真实登录、HTTP、服务端权限拦截、审计、数据库和真实数据联调未完成。
- 隔离测试只证明组件、参数、三态、请求失效和布局；不代表管理员菜单、登录或路由守卫已验证。
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
管理员侧边栏“工作台”增加“管理员教学指标”“管理员待办”两个锚点，路径分别为
`/admin/teaching-metrics`、`/admin/todos`。第一组 `/admin/overview` 保持原页面和
`/education/overview` 请求不变。

### 路由与拒绝

- 仅删除 `frontend/src/router/index.js` 中 `/admin/todos` → `/admin/overview#overview-tasks` 的临时重定向；其余重定向、`/admin/forbidden`、CurrentTerm 和课程上下文保留。
- 未修改 `permission.js`、`ui-pages.js` 注册器、公共守卫或后端。
- 缺权限、缺目标 `serviceRoute`、登录后撤权均由既有 `canOpen` 和 `permission.js` 导向 `/admin/forbidden`；本轮新增浏览器用例验证无跳转循环、拒绝后不调用管理员总览接口。
- 教师、学生直达管理员路径由公共角色守卫拒绝；教师 `/teacher/teaching-overview` 仍按原注册打开。

### 验证入口

新增 `frontend/tests/admin-entry.spec.mjs`，通过 `tests/serve-dist.mjs` 加载实际构建，驱动实际 Vue Router、canOpen、侧边栏和页面组件；Playwright 仅提供 `/getInfo`、`/getRouters`、上下文和两个总览接口夹具，不连接真实后端或数据库。

```bash
cd frontend
corepack pnpm install --frozen-lockfile
corepack pnpm build
node tests/serve-dist.mjs
# 另开终端
ADMIN_ENTRY_TEST_BASE=http://127.0.0.1:4174 node tests/admin-entry.spec.mjs
```

本轮未执行生产菜单 SQL、真实登录、HTTP 服务端授权、审计或数据库联调。生产 `sys_menu`、管理员角色授权和 `getRouters` 仍需第一组配置；`permission.js` 与 `router/index.js` 为共享外壳文件，需第一组复审。

### 本地验证结果（2026-11-26）

- 前端构建：`corepack pnpm install --frozen-lockfile`、`corepack pnpm build` 成功。
- 新管理员实际入口：`admin-entry.spec.mjs` 6/6 通过。
- 教师/学生总览：`overview-vue.spec.mjs` 24/24 通过。
- 公告/通知：`notice-vue.spec.mjs` 85/85 通过。
- 管理员组件隔离：`admin-overview.spec.mjs` 10/10 通过。
- 公共弹窗冒烟：`confirm-dialog-smoke.spec.mjs` 1/1 通过。
- 后端合并基线：`mvn -pl ruoyi-admin -am -DskipTests package` 成功。

以上均为 Playwright 接口夹具和本地静态构建结果；未启动真实后端或数据库，不代表真实登录、HTTP、服务端授权、审计、生产菜单或 MySQL 联调通过。

## 6. 后续接入条件

TODO(6-03-admin-route)：生产菜单、权限和 getRouters 尚未配置，且管理员 denied 守卫存在循环风险。解除条件：

1. 第 1 组写入 `education:overview:admin` 管理员菜单和管理员角色授权；
2. `getRouters` 返回 `education/admin/overview`、`education/admin/todos`；
3. 管理员 denied/占位路由不循环，并由公共外壳负责人复审；
4. 删除浏览器夹具补丁后，再注册生产页面。
