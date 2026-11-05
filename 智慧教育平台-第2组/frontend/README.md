# 智慧教育平台前端

本目录已补齐 Vue 3 工程，复用现有 9 个教学页面、2 个教学组件及 6 个业务 API 文件。旧的 `login.html`、三端 HTML 与原生 JS 页面保留。Vue 使用独立的 `/app/` 入口，两者共用现有若依后端，不需要修改数据库。

## 环境与启动

- Node.js 20.19+（20.x），或 22.12+。本次使用 Node.js 20.20.0、npm 10.8.2 验证。
- Vue 3.5.44、Vue Router 4.6.4、Pinia 3.0.4、Element Plus 2.13.7、Axios 1.21.0、Vite 7.3.7。
- 依赖版本由 `package.json` 与 npm 实际生成的 `package-lock.json` 固定。

在 `frontend` 目录运行：

```powershell
npm ci
npm run dev
```

打开 <http://127.0.0.1:5173/app/>。默认将 `/dev-api` 代理到 <http://127.0.0.1:8080>，重写后调用真实的 `/login`、`/getInfo`、`/education/...`。后端需要按 `backend/README.md` 准备 MySQL、Redis 与本地配置。账号由学校或开发环境管理员提供，前端不内置账号密码。

后端端口不同时，将 `.env.example` 复制为 `.env.development.local`，调整 `VITE_BACKEND_URL` 后重启 Vite。不要在任何 `VITE_*` 变量中填写服务端密钥。

## 构建和同源发布

```powershell
npm run build
npm run preview
```

预览入口为 <http://127.0.0.1:4173/app/>。预览使用生产配置；完整业务联调应使用开发代理或下面的后端同源部署。

构建产物在 `frontend/dist/`。Maven 资源配置及 `tools/build-backend.ps1` 已将该目录收集到 `classpath:/static/app/`。先构建前端，再在项目根目录运行现有后端构建和启动流程：

```powershell
.\start.cmd rebuild
```

或按后端说明执行 Maven 打包。启动后打开 <http://127.0.0.1:8080/app/index.html>。使用 Hash 路由，无须增加后端 SPA 回退控制器。后端仅对 GET `/app/**` 放行静态资源，业务接口继续鉴权。后端打包不包含 `node_modules`、前端源码、测试与环境文件。

## 工程结构

```text
frontend/
├── index.html、package.json、package-lock.json、vite.config.js
├── .env.development、.env.production、.env.example
├── src/
│   ├── main.js、App.vue
│   ├── router/           # 已实现页面白名单、角色与系统权限校验
│   ├── store/            # Pinia 登录态、身份、角色、权限及后端菜单
│   ├── utils/            # 统一请求、认证存储和访问规则
│   ├── plugins/modal.js  # 现有业务页使用的 $modal
│   ├── layout/           # 三端共用导航、角色切换与退出
│   ├── styles/           # 全局样式、窄屏布局与登录页
│   ├── views/auth/、error/、system/
│   ├── views/education/{admin,teacher,student}/
│   ├── components/Pagination/、education/
│   └── api/auth.js、education/
├── tests/unit/、tests/e2e/
└── 原有静态 HTML、JS、CSS 和 logo.png
```

## 登录、权限和请求

登录发送 `username/password/role/code/uuid`，支持后端启用或关闭验证码。登录后从 `/getInfo` 获取真实角色与权限，并读取 `/getRouters`。角色选择不能替代服务端角色；不匹配时撤销本次登录。密码不持久化。

Token 使用与旧页面相同的 `sessionStorage` 键 `zhijiao.login.token`，角色键为 `zhijiao.login.role`。只有同源、同标签页访问时可共享；开发端口与后端端口的登录态隔离。页面刷新重新查询身份，不信任本地角色作为权限依据。

导航只展示已接入的路由，按服务端身份过滤；系统查询还需对应 `system:user:list`、`system:dept:list` 权限。后端菜单保存在 store 中，不自动将未提供组件的菜单变为可用页面。前端菜单与路由校验仅改善体验，数据范围继续由服务端校验。

所有 API 共用 Axios 客户端。HTTP 401 或 JSON `code=401` 清理登录并返回登录页；403 保留会话并显示无权限；网络错误提供可重试状态。原业务 `api/education/http.js` 保留为兼容导出，不再引用 Vuex 或不存在的认证模块。

## 已接入与边界

- 管理员：学期、班级、教师、学生、组织架构。
- 教师：课程列表与详情、新建、修改、发布/撤回和教学班绑定，沿用现有接口。
- 学生：在修课程与只读详情。
- 系统用户、系统部门：真实接口的只读查询，使既有页面入口可达；不包含账户和部门的增删改、授权等完整若依系统管理功能。
- 公共能力：中文 UI、分页、弹窗、登录过期处理、403/404、断网重试、手机导航和全局异常页。

实时课堂、课程资料、章节编辑、预习、作业、成绩、AI 等尚未提供的业务仍显示原页面说明，本次不扩展接口或构造业务数据。

## 验证

```powershell
npm test
npx playwright install chromium
npm run test:e2e
```

也可使用本机 Chrome：先设置 `$env:PLAYWRIGHT_CHANNEL = 'chrome'`，再运行 `npm run test:e2e`。浏览器测试仅在测试进程中拦截接口，用来检查路由、会话、错误、分页与布局；不能代替真实 MySQL、Redis 环境的业务验收。

构建和验证记录见 `docs/vue-frontend-completion.md`。
