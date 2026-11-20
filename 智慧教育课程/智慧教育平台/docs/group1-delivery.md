# 第一组 M0 交付说明

日期：2026-11-03。范围：1-01、1-02、1-03、1-04。按用户确认，保留 admin、teacher、student 三角色，不增加 edu_admin 或第四个登录入口。Vue 的 /vue/ 为主要入口，2026-11-05 起仅保留官方若依 Vue3 工程，原静态版已迁出备份。

## 实现

- 可信身份由 SecurityUtils.getUserId/getDeptId/getRoles 提供；GET /system/identity/current 是可运行的公共身份示例，前端传入的 userId、role 不影响响应身份。
- ScopeAssert 与 ForbiddenException 统一对象权限拒绝；前端同时处理 HTTP 状态和响应业务码。系统异常对外使用通用提示。
- 用户停用、删除、改密、授权变更失效该用户的全部在线令牌。角色状态、菜单、数据范围改变失效该角色的普通用户会话。菜单和部门变更失效普通会话，保留超级管理员会话。
- 批量角色授权/取消增加角色和每个用户的范围检查；非超级管理员不能授予 admin，也不能修改自己的角色。
- 管理端具备用户、角色、菜单、部门的查询和编辑能力及操作审计查询；审计支持操作者、模块、状态、时间及详情。
- Vue 公共外壳具备授权导航、面包屑、若依标签页、移动导航、401 清理、403 卸载私有内容、浏览器历史恢复核验。
- Vue 的菜单和直达路由必须同时满足当前入口角色、操作权限及 getRouters 的服务端路由；超管通配权限不会自动开放没有服务端路由或没有实现的业务页面。
- 课程上下文提供课程/教学班选择、绑定校验、按账号与角色隔离的 ID 持久化、大整数 ID、切换清理及过期响应丢弃。
- 第 2～6 组可注册实际 Vue 业务页和顶部通知入口，协议见 group1-integration.md。未读数失败显示不可用，不伪装为零。

## 构建与运行

需要 Node.js 20、pnpm、JDK 17、Maven，以及实际启动时的 MySQL 8 和 Redis。依赖版本由现有锁文件与 Maven BOM 管理，本次前端切换为若依官方 Vue3 3.9.2，依赖以 pnpm 锁文件为准。

1. 在 frontend 执行 pnpm install --frozen-lockfile，再执行 pnpm build。默认生产地址为 /vue/。
2. 在工程根执行 node --test tests/vue-security.test.mjs；隔离浏览器检查使用 tests/browser-smoke.cjs。
3. 在 backend 执行 mvn -B -pl ruoyi-admin -am package。新增第一组 Java 测试默认执行，不要求数据库。
4. 按 backend/.env.example 在本机配置 backend/.env，再执行 backend/run-local.ps1。配置文件不提交。
5. 浏览器访问 http://127.0.0.1:8080/vue/。旧 /login.html 仅跳转 Vue 入口，不再运行静态版。

可使用 scripts/verify-group1.ps1 顺序执行检查；Maven 不在 PATH 时传入 -Maven 的绝对路径，JDK 17 通过 JAVA_HOME 指定。

## 验证边界

本轮在外层工程实际通过：68 项 Node 回归（其中 51 项静态兼容、17 项 Vue 核心）、8 项 Java 权限与身份测试、Vue 生产构建、七模块 Maven 打包。另用本机 Chrome 对实际生产产物运行浏览器响应夹具测试。浏览器夹具 14 个场景通过；在远程主线快照应用补丁后，八模块构建通过，Java 合计 272 项测试中 266 项通过、6 项因外部数据库条件未启用而跳过。最终逐项记录见 group1-verification.json。

浏览器夹具测试不连接真实数据库，不是 MySQL/Redis 端到端联调。第 2 组 options/check 和第 2～6 组业务页面、公告/通知数据仍由对应组提供。第一组已完成接入能力；不伪造这些服务，也不把它们计为第一组已实现业务。

第 2～5 组对象权限审查需在对方实际接口交付后按 group1-audit-checklist.md 联合执行。本包不宣称这些外部接口已经通过跨班或撤权验收。

## 历史说明

backend-handoff.md、frontend-1b-delivery.md、frontend-takeover.md、ai-handoff.md 记载了早期接手状态。其中“后端尚未实现”“必须建立 edu_admin”“Vue 尚无上下文”等叙述已被本轮交付取代。原始需求 Word 保留，不反向修改。

## 2026-11-05 前端替换

当前架构和验证以 ruoyi-vue3-migration.md 为准，上述静态版测试数量属于历史记录。前端唯一源码目录为 frontend/。

## 2026-11-06 界面保留修正

若依用于工程和登录、权限、请求基础；界面和五个业务管理页恢复为原知教版本。当前接入与验证以 ruoyi-vue3-migration.md 为准。
