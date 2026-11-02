# 前端工程

业务页面都在 Vue 3 工程里。源码只保留挂载入口 `index.html`，构建后由 `/app/` 提供。

| 形态 | 位置 | 说明 |
|---|---|---|
| 挂载入口 | `index.html` | Vite 入口，挂载 `#app` |
| Vue 3 工程 | `src/`、`vite.config.js` | 三端菜单、页签、课程上下文和业务页，构建到 `app/`，由 `/app/` 访问 |

## Vue 工程

技术栈与若依前端一致：Vue 3.5 + Vite 7 + Element Plus + Pinia + Vue Router（hash 模式）。

```powershell
cd frontend
npm install                       # 首次
npm run dev                       # 开发，http://127.0.0.1:5173/app/
npm run build                     # 产出 app/，后端打包时复制到 static/app
```

- `base` 与产物目录都取 `app/`，配合 `ruoyi-admin/pom.xml` 的 `app/**` 资源条目，构建后直接由 `http://127.0.0.1:8080/app/` 提供，无需改动打包流程。
- 开发态通过 `/dev-api` 代理到 `http://127.0.0.1:8080`（见 `vite.config.js`，`VITE_BACKEND_URL` 可覆盖）。
- 当前路由只有第 4 组的四个页面加一个公开投影页；其他组的业务路由由各自分支追加到 `src/router/routes.js`。

## 三端页面和公共能力

```text
frontend/
├── README.md
├── index.html、login.js、logo.png
├── role-home.js
└── src/
    ├── views/education/
    │   ├── teacher/
    │   ├── student/
    │   └── admin/
    ├── api/education/
    └── components/education/
```

| 路径 | 规划职责 |
|---|---|
| src/views/education/teacher | 教学工作台、课程空间、备课预习、课堂控制台、作业批改、学情反馈 |
| src/views/education/student | 学习待办、在修课程、预习、同步课堂、作业提交、错题补学及答疑 |
| src/views/education/admin | 组织人员、正式课程和教学班、授权、资源治理、教学运行与系统配置入口 |
| src/api/education | 按 course/resource/prepare/classroom/activity/assignment/learning/analysis/ai 组织业务请求；同一接口由各端复用 |
| src/components/education | 课程/班级上下文选择、资源预览、题目作答、任务状态、课堂公共展示等确有复用价值的组件 |

不提前生成这些页面或 API 文件；具体文件扩展名、状态管理方式、路由组织和组件命名以最终若依前端版本为准。

## 一套基础设施

当前联调页调用若依认证、用户信息和动态菜单接口。选中管理员、教师或学生入口后，页面将 `role` 传给 `/login`；服务端在发令牌前核对账号角色，页面进入对应角色页时再次核验令牌和角色。原型导航目前只负责展示，不表示账号已经获得相应业务权限；未来正式前端工程应复用若依的 Token 生命周期、请求客户端、错误处理、布局与权限能力。三端是同一工程下的角色页面工作区，不是三个独立应用；不建立三套登录、课程、权限或请求基础设施。

业务 API 按领域组织，角色差异通过明确的操作权限、后端数据裁剪和少量角色专属交互表达。管理员页复用既有系统管理入口，教学班和课程空间归教育业务，不复制账号和角色管理页面。

## 课堂体验边界

- 教师控制台同屏容纳 PPT、活动入口和私有反馈，避免签到/答题/讲课反复跳页。
- 投影画面作为公开展示模式，复用受控展示组件；不新增第四套登录或权限体系，不接收备注、未公布答案和学生成绩明细。
- 学生课堂以手机访问和任务完成为主，断线后获取服务端最新快照，恢复页码、活动状态和本人提交结果。
- 一个统一课程/教学班上下文贯穿业务；切换上下文时清理旧订阅与过期数据，不能只替换标题。
- 前端控制可见性改善操作体验，服务端仍必须校验每个资源、请求和实时通道的数据范围。

接入后再决定实时传输方式、响应适配和路由配置，保持 [接口约定](../docs/api-contracts.md) 与实际若依能力一致。
