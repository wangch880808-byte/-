# 前端工作区

第一组以 Vue 的 /vue/ 作为主要交付入口，原 login.html 与三角色静态页保留兼容。两者都调用若依真实系统接口，业务对象权限由服务端逐请求判断。

- Vue：vue/，包括公共布局、页签、课程/教学班上下文和管理员五类管理页面。
- 静态兼容：login.html、admin.html、teacher.html、student.html、shell/、pages/。
- 教学业务由第 2～6 组在实际接口和授权路由就绪后接入，未实现入口不开放。
- 第一组不新增受限管理员，维持 admin/teacher/student 三端。

构建、验证和状态见 ../docs/group1-delivery.md。Vue 接入协议见 ../docs/group1-integration.md；静态兼容接口可参考 shell/auth.js、shell/context.js 和 shell/shell.js。
