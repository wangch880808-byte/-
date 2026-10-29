# Vue 前端工程（试运行）

这是新增的 **Vue 3 + Vite** 前端工程，与现有静态页（`frontend/*.html`、`frontend/shell`、`frontend/pages`）**并存**，不修改静态页与后端代码。

目标是给后续业务页面一个可维护的 Vue 基座；现有静态页仍可正常使用，迁移按页面逐步进行。

## 已实现

| 能力 | 说明 |
|---|---|
| 登录 | 三角色入口、验证码、角色校验、错误提示、退出 |
| 会话 | `sessionStorage` 存令牌与角色；刷新时重新 `getInfo` 校验 |
| 权限 | 根据 `permissions`（含 `*:*:*`）过滤菜单与按钮 |
| 布局 | 顶栏、侧栏、面包屑、移动端抽屉；403 提示条 |
| 请求 | Bearer、15 秒超时、401 回登录、403 全局提示、取消 |
| 页面 | 管理员五个页面全部迁移：用户、角色、菜单、组织架构、审计日志 |

**未实现**：课程/教学班上下文（`shell/context.js` 的 Vue 版）、教师与学生业务页、页签与顶部通知入口。

## 运行

前置：后端已启动（`http://127.0.0.1:8080`），Redis 可用。

```powershell
cd 'D:\Project\--main\--main\智慧教育课程\智慧教育平台\frontend\vue'
& 'C:\Users\Asus\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe' `
  'C:\Users\Asus\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\pnpm\bin\pnpm.mjs' install --registry=https://registry.npmmirror.com
& 'C:\Users\Asus\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe' `
  'C:\Users\Asus\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\pnpm\bin\pnpm.mjs' dev
```

浏览器打开 <http://127.0.0.1:5173>，用 `admin / admin123` 登录。

Vite 已把以下路径代理到 8080：`/captchaImage`、`/login`、`/logout`、`/getInfo`、`/getRouters`、`/system`、`/monitor`、`/education`、`/logo.png`。

## 构建与后端托管（已接通）

Vue 产物已由后端同源托管在 **`/vue/`**，原有静态页（`/login.html` 等）不受影响。

```powershell
cd frontend/vue
$env:VITE_BASE = '/vue/'
pnpm build

cd ../../backend
$env:JAVA_HOME = 'C:\Users\Asus\.jdks\temurin-17'
mvn.cmd -B -pl ruoyi-admin -am package -DskipTests
.\run-local.ps1
```

后端做了三件事（已配置好）：

1. `ruoyi-admin/pom.xml` 增加资源目录 `frontend/vue/dist` → `static/vue`；
2. `SecurityConfig` 放行 `GET /vue`、`/vue/**`；
3. `VueUiController` 把 `/vue`、`/vue/` 转发到 `/vue/index.html`。

访问：<http://127.0.0.1:8080/vue/>（hash 路由：`/vue/#/login`、`/vue/#/admin/users`）。

> hash 路由是为了避免后端为 `/vue/**` 做 SPA 回退；如果以后改用 nginx 的 `try_files`，可以换回 history 路由。
>
> **打包顺序**：先 `pnpm build` 再 `mvn package`。`dist/` 默认被 gitignore，换机器或清理后必须先构建，否则 jar 里没有 Vue 页面（静态页不受影响）。

## 目录

```text
frontend/vue/
├── index.html
├── vite.config.js        # 代理与构建配置
├── package.json
├── README.md
└── src/
    ├── main.js           # 挂载、401/403 处理器
    ├── App.vue
    ├── styles.css        # 全局样式（沿用静态版视觉）
    ├── api/client.js     # 请求客户端（Bearer/超时/401/403/取消）
    ├── stores/session.js # 令牌、用户、角色、权限
    ├── config/menu.js    # 按角色的菜单与权限
    ├── router/index.js   # 路由与守卫
    ├── utils/toast.js    # 轻提示
    ├── layout/AppLayout.vue
    ├── components/
    │   ├── AppModal.vue          # 通用弹窗
    │   ├── ConfirmDialog.vue     # 确认框
    │   ├── PermissionTree.vue    # 菜单/部门勾选树（可补祖先）
    │   └── TreeManagePage.vue    # 菜单/部门共用的树表 CRUD
    └── views/
        ├── LoginView.vue
        ├── HomeView.vue
        └── admin/
            ├── UserListView.vue
            ├── RoleListView.vue
            ├── MenuListView.vue
            ├── DeptListView.vue
            └── OperLogView.vue
```

## 约定

- 所有请求走 `src/api/client.js`，不要直接 `fetch`；
- 权限判断用 `hasPermission('system:user:list')`，服务端仍会再次校验；
- 登录后角色存放在 `sessionStorage` 的 `zhijiao.login.role`，与静态版一致；
- 新增页面时：加路由 → 在 `config/menu.js` 加菜单 → 声明权限，**不要**用假数据填充。

## 与静态版的差异（迁移时注意）

| 项 | 静态版 | Vue 版 |
|---|---|---|
| 菜单过滤 | 注册页 + 角色 + 权限 + `getRouters` 路由 | 角色 + 权限（暂未做路由校验） |
| 上下文 | `shell/context.js` 已适配（接口待 2A） | 暂未实现 |
| 页签 | 有 | 暂未实现（用路由） |
| 顶部业务入口 | `headerEntry` + 未读角标 | 暂未实现 |
