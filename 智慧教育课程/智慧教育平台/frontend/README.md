> 第一、二组对接版：优先阅读根目录 `运行说明.md` 和 `docs/group12-integration.md`。本轮仅构建检查，未启动联调。

# 知教·智慧教育课程平台前端

本工程基于若依官方 [RuoYi-Vue3](https://github.com/yangzongzhuan/RuoYi-Vue3)，版本 3.9.2，上游提交 838965c5a18d2c61b73ec30c6e288057aaa08b63。

使用 Vue 3、Element Plus、Pinia、Vue Router、Vite。保留上游 LICENSE。

## 本地开发

安装 Node.js 20.19+ 与 pnpm。进入本目录执行 `pnpm install --frozen-lockfile`，然后 `pnpm dev`。

访问 http://127.0.0.1:5173/，API 默认代理到 http://127.0.0.1:8080；可通过 VITE_API_TARGET 环境变量调整。

## 构建

执行 `pnpm build`，产物为 dist/。随后从项目根目录执行 `mvn -f backend/pom.xml -pl ruoyi-admin -am clean package`，后端会打包前端产物，入口为 http://127.0.0.1:8080/vue/。

仅保留这一套正式前端。原 Vue 登录页、布局与业务组件已迁入本工程；静态 HTML 版退出运行。旧 .html 入口将跳转到 Vue 登录页。

## 业务接入

系统管理沿用原界面与业务组件，身份和请求接入若依公共模块，保留用户/角色 Excel 导入导出。管理员七组菜单保留，登录后默认收起；33 个未实施菜单仍为空白。

教师、学生使用同一框架，课程上下文位于 src/education/context.js。业务页通过 window.EduVue.registerPage 注册，必须提供 roles、permission、serviceRoute，服务端路由与权限同时满足才允许显示。需要课程/班级的页面声明 requiresContext: 'course' 或 'class'。

当前界面：src/layout/education/AppLayout.vue；原管理页面：src/views/education/admin/；样式：src/assets/styles/education.css。旧页面 API 调用通过 src/api/education/client.js 适配统一若依 Axios；src/education/session.js 读取官方 Pinia user/permission，不再维护第二套身份令牌。
