# 第一组 Vue 前端

Vue 3 与 Vite，生产入口 /vue/。包含三角色登录、授权导航、页签、课程/教学班上下文、公共请求、顶部通知接入，以及用户、角色、菜单、部门、审计五类管理页面。保留原静态页兼容。

在本目录执行：
```powershell
pnpm install --frozen-lockfile
pnpm test
pnpm build
```

pnpm build 默认生成 /vue/ 前缀；后端 ruoyi-admin 打包 frontend/vue/dist 到 static/vue。先构建前端，再在 backend 执行 mvn -B -pl ruoyi-admin -am package，配置本地 .env 后运行 run-local.ps1。

开发使用 pnpm dev，接口代理到 http://127.0.0.1:8080；可通过 VITE_API_TARGET 更换本地后端。

页面、通知及第 2 组上下文契约见 ../../docs/group1-integration.md。当前不包含其他组的课程、课堂、资源、作业或消息内容。接口不存在时明确显示未就绪，不生成生产假数据。

权限在服务端兜底。前端入口必须同时匹配已注册页面、当前登录端角色、操作权限、getRouters 授权路由；403 卸载正文和弹窗，401 回登录；切换上下文取消旧请求，浏览器历史恢复重新核验。测试浏览器夹具仅位于 tests，不进入生产包。
