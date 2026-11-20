# 若依 Vue3 工程接入与原界面保留

更新日期：2026-11-06。按用户澄清：只采用若依前端工程基础，界面、菜单与业务内容继续使用原知教版本。

## 实现范围

- 唯一前端工程为 frontend/，基于官方 https://github.com/yangzongzhuan/RuoYi-Vue3 3.9.2，固定提交 838965c5a18d2c61b73ec30c6e288057aaa08b63，保留 MIT LICENSE。
- 保留若依 Vite 配置、依赖锁、Pinia store、user 登录/身份查询、权限模块、统一 Axios、公共组件与工具。身份使用 Admin-Token/Edu-Role Cookie，没有重新使用旧 sessionStorage 令牌。
- 原登录页和五个管理页面位于 src/views/education/；原顶栏、侧栏、面包屑与最多八个页签位于 src/layout/education/AppLayout.vue；原样式位于 src/assets/styles/education.css。
- 用户、角色、菜单、组织、审计日志仍使用原页面和操作方式，保留 Excel 导入导出。七组38项菜单中33项保持空白，首次登录和刷新后一级菜单默认收起。
- src/api/education/client.js 是旧业务调用方式到若依 Axios 的适配；src/education/session.js 从官方 Pinia user/permission 获取身份和后台路由，不另外保存一份账号或令牌。页面必须同时满足角色、操作权限与服务端路由。
- 教师/学生保留原工作台、课程与教学班上下文、业务页注册及通知入口；普通403恢复原“重新核验”交互，401清理私有内容并回到原登录页。

若依原生页面与默认布局源码保留为框架模板，当前入口采用上述原界面。停止使用静态 HTML，旧 .html 地址只跳转 /vue/。

## 运行与构建

在 frontend 执行 pnpm install --frozen-lockfile、pnpm dev，开发入口 http://127.0.0.1:5173/。执行 pnpm build 后在项目根运行 mvn -f backend/pom.xml -pl ruoyi-admin -am clean package，部署入口 http://127.0.0.1:8080/vue/。两种入口显示同一套原知教界面。

## 验证

原界面生产构建通过；24项隔离浏览器回归通过，覆盖原登录与布局、菜单收起/展开、33空白页、五个管理页、Excel上传下载、移动导航、401/403、无后台路由拒绝、旧请求与新身份竞态、教师/学生上下文及扩展入口。12项核心权限/上下文测试通过。

本次修正只调整前端，后端沿用此前已通过的Java测试结果（278项中272通过，6项按既有外部数据库条件跳过）。后端部署包重新清理构建，以替换前端资源。浏览器业务测试使用隔离响应，没有导入真实用户/角色或重置数据库。

## 源码保护

迁移前原源码保存于工作区 out/ruoyi-vue3-migration-20261105/backup/frontend，59个原源文件哈希已核验；依赖余项位于 backup/frontend-remaining。此次修正将原Vue组件迁回正式工程，并保留静态版停用状态。未提交或推送Git，原有暂存区保留。
