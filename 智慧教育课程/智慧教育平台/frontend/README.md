# 前端架构规划

本目录是前端工程工作区。当前 `login.html`、`login.js` 以及 `admin.html`、`teacher.html`、`student.html` 由后端同源提供，使用若依真实认证接口；运行方式见 [后端说明](../backend/README.md)。三端页面以 [`prototypes/console-navigation-v1`](../prototypes/console-navigation-v1/) 的原型为基础，保留原有导航和页签交互，并增加登录态、角色校验及退出登录。原型目录的文件保持不变。页面中的菜单是静态设计稿导航，尚未关联若依授权菜单或教学业务接口；内容区仍待实现。尚未接入若依 Vue 前端、创建依赖清单或选择 Vue 版本。

## 三端页面和公共能力

```text
frontend/
├── README.md
├── login.html、login.js、logo.png
├── admin.html、teacher.html、student.html
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

## 6-02 教学业务页（已接入，M0 6-04）

`src/api/education` 与 `src/views/education` 下已落地教师公告与学生公告/通知页面，其余目录仍为规划占位。

```text
frontend/src/
├── api/education/
│   ├── http.js            统一请求与错误归一
│   ├── announcement.js    教师 6 个 + 学生 2 个公告接口
│   └── notice.js          学生 5 个通知接口（后端待实现）
└── views/education/
    ├── education-views.css
    ├── ui-kit.js          转义、时间、状态卡片、分页、确认框
    ├── view-host.js       菜单挂载、课程—教学班上下文
    ├── teacher/announcements.js
    └── student/{announcements,notices}.js
```

- 页面由 `teacher.html`、`student.html` 通过 `#viewRoot` 挂载，未注册的菜单仍显示占位。
- 教师端公告必须带 `bindingId`；2-04 课程列表接口就绪前只能手工填写。
- 学生通知接口已在 `main@f21f51d` 合并（列表/详情/未读数/单条已读/全部已读）；失败时不使用本地假数据，也不把失败渲染成 0 或空列表。
- 离线自测：`mock-preview.html`（免登录免后端），或在运行页加 `?mock=1`。
- 详细接口对照、状态处理与阻塞项见 [6-02 前端接口接入说明](../docs/6-02-frontend-integration.md)。

## 6-03 三端总览（阶段1/2）

`src/api/education/overview.js`、`src/components/education/overview-page.js`、三端 `overview.js`、管理员 `todos.js` 和 `overview.css` 已落地；教师/学生/管理员角色 HTML 已按最小接入挂载总览，教师公告与学生通知/公告原注册项保留。

- 页面自测：`overview-preview.html`（显式内存 Mock，不连后端、不校验登录、不写 sessionStorage/bindingId）。
- 预设场景：正常、真实零、NO_DATA、UNAVAILABLE、混合、缺指标、非法值、空 items、缺 data、401/403/400/404/500/网络、管理员待办失败/非法 OK 0。
- 总览只消费 #24 当前 16 个指标；不补算、不读其他组表、不把失败写成 0。
- 阶段1回归：`tests/overview-stage1.html` + `tests/overview-stage1.js`；阶段2角色页回归：`tests/overview-stage2.js`；不提交 Playwright，不建产品 package.json。
- 学生通知卡跳既有学生通知菜单；卡片为本人全部未读，通知页按当前班级上下文显示，范围差异已在卡片说明。
- 详细设计与验证见 [6-03 总览前端设计](../docs/6-03-overview-frontend-design.md)。

## 课堂体验边界

- 教师控制台同屏容纳 PPT、活动入口和私有反馈，避免签到/答题/讲课反复跳页。
- 投影画面作为公开展示模式，复用受控展示组件；不新增第四套登录或权限体系，不接收备注、未公布答案和学生成绩明细。
- 学生课堂以手机访问和任务完成为主，断线后获取服务端最新快照，恢复页码、活动状态和本人提交结果。
- 一个统一课程/教学班上下文贯穿业务；切换上下文时清理旧订阅与过期数据，不能只替换标题。
- 前端控制可见性改善操作体验，服务端仍必须校验每个资源、请求和实时通道的数据范围。

接入后再决定实时传输方式、响应适配和路由配置，保持 [接口约定](../docs/api-contracts.md) 与实际若依能力一致。
