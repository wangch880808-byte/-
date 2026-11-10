# 前端架构规划

本目录是前端工程工作区。当前 `login.html`、`login.js` 以及 `admin.html`、`teacher.html`、`student.html` 由后端同源提供，使用若依真实认证接口；运行方式见 [后端说明](../backend/README.md)。三端页面以 [`prototypes/console-navigation-v1`](../prototypes/console-navigation-v1/) 的原型为基础，保留原有导航和页签交互，并增加登录态、角色校验及退出登录。原型目录的文件保持不变。页面中的菜单是静态设计稿导航，尚未关联若依授权菜单；教师「课程建设」「课前准备」与学生「课程资料」「课前学习」已由第 3 组接入真实业务接口，并按一条课前时间轴编排：环节按「课程 / 教学班 / 上课日期」分成一堂堂课，每堂课内部重新编号 `1..N`，每个环节有占满内容区的独立作答 / 编辑页，可在本堂课内跳上一 / 下一环节；「添加环节」是独立分步创建页（`#create=`），资料与题目可直接从课程资源库、题库选用已有版本，不必重复上传或录入；教师端可原地修改环节名称与开放 / 截止时间（含解析公布时间），不必删除重建，也可删除环节（后端归档软删除，见 [接口协作约定](../docs/api-contracts.md)）；教师与学生页面采用学习通式紧凑卡片，并精简了教学式说明文字，见 [课前时间轴页面编排说明](../docs/pre-class-timeline.md)。其余菜单内容仍为占位。尚未接入若依 Vue 前端、创建依赖清单或选择 Vue 版本。

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

## 课堂体验边界

- 教师控制台同屏容纳 PPT、活动入口和私有反馈，避免签到/答题/讲课反复跳页。
- 投影画面作为公开展示模式，复用受控展示组件；不新增第四套登录或权限体系，不接收备注、未公布答案和学生成绩明细。
- 学生课堂以手机访问和任务完成为主，断线后获取服务端最新快照，恢复页码、活动状态和本人提交结果。
- 一个统一课程/教学班上下文贯穿业务；切换上下文时清理旧订阅与过期数据，不能只替换标题。
- 前端控制可见性改善操作体验，服务端仍必须校验每个资源、请求和实时通道的数据范围。

接入后再决定实时传输方式、响应适配和路由配置，保持 [接口约定](../docs/api-contracts.md) 与实际若依能力一致。

## 本地静态资源与刷新

统一构建号 `20261110d`，写在四个 HTML 的引导脚本、`login.js` 的 `buildTag`、`role-home.js` 的 `buildTag` 和 `group3.js` 的 `BUILD` 常量里。任何页面只要 URL 上没有当前构建号，就会自动 `location.replace` 到带 `v=20261110d` 的地址（保留 `?g=`、`?i=`、`#stage=` 等原有参数），因此改动前缓存的 `/login.html`、`/teacher.html`、`/group3.js` 等条目不会再被命中。所有脚本与样式引用（含 `login.js`、`role-home.js`）都带同一版本号；登录成功跳转、页面内切换菜单、退出登录、进入创建页等跳转也都由 `buildUrl()` 补上 `v=`。

后端 `application.yml` 对静态资源设 `no-store / no-cache / must-revalidate / max-age=0`，并关闭 `use-last-modified`：jar 内资源时间戳固定为 1980，若服务端回应 `If-Modified-Since` 会返回 304，浏览器就会一直沿用缓存里的旧 JS；关闭条件请求后每次都返回 200 完整内容。改完 `frontend/` 必须重新执行 `mvn -B -pl ruoyi-admin -am package -DskipTests` 并重启服务，页面由 jar 内的 `static/` 提供；重新打包前先停掉占用 `ruoyi-admin.jar` 的 Java 进程。

### 在 IDEA 里打开最新页面

前端源码就在本项目的 `frontend/` 目录，IDEA 直接编辑的就是这份文件；若 IDEA 里显示旧内容，用 `File → Reload All from Disk` 刷新即可。问题通常出在 IDEA 内置浏览器：改动前打开的标签页缓存了旧 HTML，浏览器连请求都不发，服务端任何响应头都无法介入。

在 IDEA 内置浏览器地址栏粘贴一次（或在 Markdown 预览里点击）这个全新地址：[打开最新登录页](http://127.0.0.1:8081/login.html?v=20261110d)。登录后会跳到 `teacher.html?v=20261110d`，此后所有跳转都带版本号，不会再退回旧页面。若仍看到旧内容，关掉该内置浏览器标签页重开，或清除一次 IDE 的浏览器缓存。

教师端入口：`teacher.html?g=1&i=2`（课前准备）→ 点时间轴卡片上的蓝色「编辑环节设置」→ 独立编辑页顶部的「环节设置」卡片可直接改名称、开放时间、截止时间，习题还有解析公布时间，底部「保存设置」写回真实业务记录。

同一页下方的「本堂课资料」卡片可直接增删资料：「+ 添加资料」从课程资源库选用或上传新文件，逐条写入既有 `POST /education/group3/releases`；每行「移出」调用 `POST /education/group3/releases/{id}/revoke` 结束该条按班发布（资源本体保留）；勾选「替换本环节资料」可一步换掉当前资料，无需撤回或删除环节。详见 [课前时间轴页面编排说明](../docs/pre-class-timeline.md) 第 11 节。
