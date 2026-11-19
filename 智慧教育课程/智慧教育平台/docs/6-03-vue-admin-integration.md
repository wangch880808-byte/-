# 6-03 管理员 Vue 总览交接记录

日期：2026-11-19
状态：**组件与隔离测试已完成；生产菜单、路由和权限入口未接入。**
依据：#24 `feature/6-01-overview-api@233ee45a2af2d73e96fe09f110a87bcb39362df2`；#36 基线 `5213604d4a21768366c8e972ce349ae8b1b43b79`。

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

## 6. 后续接入条件

TODO(6-03-admin-route)：生产菜单、权限和 getRouters 尚未配置，且管理员 denied 守卫存在循环风险。解除条件：

1. 第 1 组写入 `education:overview:admin` 管理员菜单和管理员角色授权；
2. `getRouters` 返回 `education/admin/overview`、`education/admin/todos`；
3. 管理员 denied/占位路由不循环，并由公共外壳负责人复审；
4. 删除浏览器夹具补丁后，再注册生产页面。
