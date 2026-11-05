# Vue 前端工程补全记录

日期：2026-10-30。

## 本次范围

用户在检查现有前端后授权补全 Vue 工程。本次将已有 Vue 业务源码接入可安装、启动和构建的工程，不扩展未实现的课堂、作业、成绩等业务，不改动数据库和原型来源。

选用与已有 `<script setup>`、Element Plus 源码一致的 Vue 3 技术栈。Vite 7.3.7 与现有 Node.js 20.20.0 兼容；使用 Vue Router 4、Pinia 3。具体版本见 `frontend/package.json`，传递依赖以真实生成的锁文件为准。旧文档中“Vue 尚未选定、src 仅目录预留”的描述已不适用本次工程状态。

## 改动

1. 增加应用入口、Vite 配置、依赖和锁文件、开发代理、生产配置和环境示例。
2. 接入 9 个原有 Vue 页面、2 个教学组件及 6 个业务 API 文件。
3. 增加三角色登录、验证码、服务端身份确认、Token 生命周期与安全的站内返回地址。
4. 增加共用布局、角色切换、路由守卫、系统细粒度权限过滤及 403/404/连接错误页。
5. 补齐分页、`$modal`、中文 UI 和响应式样式；使用 Element Plus 官方的全局注册方式，减少构建插件依赖。
6. 将教学请求层统一到公共客户端，消除原先失效的 Vuex `store.dispatch('LogOut')` 依赖，区分网络、401、403 和业务错误。
7. 为原有 `/system/user`、`/system/dept` 跳转提供真实接口只读查询。完整用户和部门管理仍在后续业务范围。
8. 后端 Maven 与本地构建脚本仅打包旧静态资源和 `dist/`，Vue 产物在 `static/app/`；匿名 GET `/app/**` 只用于加载前端静态资源。

## 运行与验证

使用说明在 `frontend/README.md`。浏览器用例只使用测试进程接口拦截，无内置演示登录或生产假数据模式。真实后端未运行时，应明确区分“工程构建和浏览器交互验证”与“真实业务联调通过”。

实际执行结果：

- npm install 成功；npm ls --depth=0 显示全部直接依赖版本一致。
- npm run build 成功，生成完整 dist 目录，页面使用懒加载。
- npm test：3 项通过，覆盖角色与权限组合及登录返回地址。
- 浏览器使用本机 Chrome；15 项用例全部通过，覆盖三角色、验证码开关、入口与服务端角色不符、分页、课程深链与刷新、HTTP/JSON 401、403、断网恢复、退出及手机导航。
- 生产 dist 由 Vite preview 提供后验证 /app/index.html 深链刷新，以及根路径 API 请求。
- 桌面与 390px 手机登录页截图已人工查看，未见裁切或横向溢出。
- Maven XML 和 PowerShell 构建脚本的语法解析通过；静态资源复制逻辑将 Vue 产物放入 static/app。
- 后端全量编译未通过：本机 Maven 缓存只有 Spring 5/7、Spring Boot 2/4 等版本，缺少该项目所需的 Spring 6、Spring Boot 3 依赖。未启动后端、数据库或 Redis，未执行真实账号业务联调。
- Element Plus 全局注册带来约 1.07 MB 的主 JS（gzip 约 358 KB），构建产生体积提示，不影响构建成功。后续可按实际首屏性能需求改为显式按需注册。

## 后续边界

若其他小组引入完整若依 UI，应统一入口、路由、状态和请求层，再迁移本次业务页面，避免形成两套登录基础设施。新增菜单需同时提交 Vue 页面、路由元数据和真实接口契约。

参考：[Vite 构建与部署路径](https://vite.dev/guide/build)、[Pinia 在组件外使用](https://pinia.vuejs.org/core-concepts/outside-component-usage.html)、[Element Plus 安装](https://element-plus.org/en-US/guide/installation.html)。
