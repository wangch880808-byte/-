# 6-04 通知 / 公告与 6-03 总览 Vue 夹具

本目录是 6-04 学生通知、学生公告、教师公告，以及 6-03 教师/学生/管理员总览前端原型的浏览器夹具，不连接后端、不写数据库。

## 运行

```bash
cd frontend
npm run build
node tests/serve-dist.mjs
# 另开终端
NOTICE_TEST_BASE=http://127.0.0.1:4174 node tests/notice-vue.spec.mjs
OVERVIEW_TEST_BASE=http://127.0.0.1:4174 node tests/overview-vue.spec.mjs
```

管理员总览/待办组件隔离入口，不经过生产路由或管理员菜单：

```bash
cd frontend
npx vite --host 127.0.0.1 --port 4175
# 另开终端
ADMIN_OVERVIEW_BASE=http://127.0.0.1:4175/tests/admin-overview.html   node tests/admin-overview.spec.mjs
```

管理员实际入口回归使用生产构建和 `serve-dist.mjs`，驱动实际 Vue Router、公共
`permission.js` 守卫、canOpen 和管理员侧边栏；接口只由 Playwright 夹具提供：

```bash
cd frontend
corepack pnpm install --frozen-lockfile
corepack pnpm build
node tests/serve-dist.mjs
# 另开终端
ADMIN_ENTRY_TEST_BASE=http://127.0.0.1:4174 node tests/admin-entry.spec.mjs
```

管理员截图默认跳过；设置仓库外目录后输出：

```bash
ADMIN_OVERVIEW_SCREENSHOT_DIR="$HOME/admin-overview-shots" ADMIN_OVERVIEW_BASE=http://127.0.0.1:4175/tests/admin-overview.html   node tests/admin-overview.spec.mjs
```

总览截图默认跳过。需要留档时设置仓库外目录：

```bash
OVERVIEW_SCREENSHOT_DIR="$HOME/overview-shots" OVERVIEW_TEST_BASE=http://127.0.0.1:4174 node tests/overview-vue.spec.mjs
```

截图目录由 `OVERVIEW_SCREENSHOT_DIR` / `ADMIN_OVERVIEW_SCREENSHOT_DIR` 决定，不依赖固定盘符。浏览器上下文固定
`timezoneId: 'Asia/Shanghai'`；输入和显示按本机时区，请求中的 `from/to` 转为 UTC。

`playwright` 使用本机 Edge 通道；未写入 `package.json`，按需临时安装：

```bash
npm install --no-save --no-package-lock playwright@1.65.1
```

## 覆盖

- 授权后工作台入口进入通知列表，顶栏角标显示未读数；
- 工作台、顶栏、直达入口使用同一角色/权限/服务端路由检查；
- 缺权限或缺服务端路由时直达也被拦截；
- 切班后旧请求被 abort 或迟到响应被序号丢弃；
- 正常未读数、未读数 503“暂不可用”的不同展示；
- 教学班授权业务 503、通用业务 503、网关 HTML 503 的文案区分；
- 空列表、列表 403、列表 503 三态互不混同；
- 通知详情正常字段、大整数 ID 路径编码、404、授权/通用 503；
- 关闭、切换通知、切班、离开页面后旧详情迟到响应不重新打开或覆盖；
- 打开详情零写请求、单次标为已读 POST、列表与角标同步、alreadyRead=true；
- 标为已读 404/503、刷新失败分项提示、关闭/切换/切班/离开后的迟到写响应；
- 全部已读跨班确认文案、取消零 PUT、单次无参数 PUT、markedCount=0、成功同步；
- 全部已读切班/离开后的迟到响应、错误 403/503；
- 单条已读成功后立即刷新顶栏，列表未完成时离开不晚发请求；
- 学生课程公告列表/详情、无班级省略 bindingId、按 sourceId 从通知打开公告；
- 公告不可见 404、切班后公告列表迟到响应、切页签后公告详情迟到响应；
- 公告页签点顶栏通知回「学习通知」，保留当前班级和显式 tab 参数；
- 通知 kind / unreadOnly 筛选、翻页重置、空筛选省略、筛选下全部已读范围；
- 学生从通知打开公告后关闭并切班，旧 announcementId 不重发、不重开；
- 教师未选班零请求、缺权限/路由拦截、按班查询、状态筛选与 visibilityState 详情；
- 教师切班后旧公告列表迟到响应不覆盖新班级；
- 教师新建入口同时要求有效班级、`education:announcement:add` 和当前班级 canPublish；
- 新建草稿提交当前班级字符串 bindingId、UTC 时间，成功关闭表单并重拉当前列表；
- 标题 200/201 Unicode 码点边界、正文字符数、正文 UTF-8 字节上限、关闭时间窗口校验，校验失败零写请求；
- 提交中禁用保存，重复点击只发一次创建请求；
- 新建 403、授权 503、超时/网络中断的分类提示，超时引导先查列表再重试；
- 编辑入口按 `education:announcement:edit`、DRAFT、创建者或 canPublish 显示；
- 编辑先读详情，PUT 只提交 title/body/opensAt/closesAt，不提交 bindingId；未修改时间保留原毫秒；
- 详情已非草稿时关闭表单并刷新列表；编辑保存 403/409/503 保留提示；
- 切班、关闭表单或离开页面后，迟到的编辑详情和新建响应不重开旧表单、不覆盖新列表；
- 发布入口要求 DRAFT、`education:announcement:publish` 和 canPublish；归档入口要求 PUBLISHED、`education:announcement:archive` 和 canPublish；
- 发布/归档确认文案分别说明开放时间生效和归档后学生不可见；
- 取消确认、确认前切班或离开页面零写请求；提交中禁用确认，重复点击只发一次；
- 发布/归档成功重拉列表并刷新仍打开的详情；重复发布/归档成功按幂等成功处理；
- 发布失败区分 403/404/409/授权 503/通用 503，发布 503 保留失败提示并重读公告状态；
- 发布 503 状态重读期间重试成功时，旧详情迟到响应不覆盖新状态；
- 操作发出后切班或离开页面，迟到响应不覆盖新班级列表、详情或成功提示；
- 大整数公告 ID 发布路径保持字符串；
- 通知与公告详情弹窗打开聚焦、Esc 关闭、关闭后焦点归还触发按钮；
- 6-03 教师 6 指标、学生 4 指标按 metricCode 渲染，真实零、NO_DATA、UNAVAILABLE 和混合三态分开；
- 缺项、重复、非法 status/value、unit 非 COUNT、source 不匹配只降级对应卡片；
- 顶层 data/items/asOf/window 异常显示响应异常，不伪装空数据；
- 工作台快捷入口和独立入口均按角色/权限/服务端路由门控，登录核验前零总览请求；
- 教师仅发送 from/to/可选字符串 classId，学生仅发送 from/to，不携带身份或班级上下文；
- 默认服务端窗口、from/to 成对/顺序校验、UTC 转换、筛选乱序和排队后失效；
- 401 登录跳转，403 清除旧数据并提示无权限，网络/业务错误和刷新失败不保留旧值；
- classId 以 Long 字符串边界校验，Long.MAX_VALUE 合法，MAX+1/超长/0/负数/非法格式零请求；
- asOf、window.startsAt、window.endsAt 校验真实日历日期，非法日期不因 Date.parse 归一化而显示；
- 合法闰日、Z、偏移和不同小数秒精度可展示；单卡违约不影响其余合法卡片；
- 390px 总览单列无横向溢出，离开总览后页面恢复；
- 管理员总览 5 指标、管理员待办固定 NO_DATA、两个端点只发送 from/to；
- 管理员实际入口：侧边栏和直达 `/admin/teaching-metrics`、`/admin/todos`，`/admin/todos` 不再重定向到第一组组织待办；
- 管理员第一组 `/admin/overview` 仍调用 `/education/overview`，不混用第 6 组管理员指标；
- 管理员缺权限、缺目标 serviceRoute、登录后撤权稳定落到 `/admin/forbidden`，无跳转循环、不调用对应总览；
- 教师/学生直达管理员页面被公共守卫拒绝且不发管理员请求；教师教学总览仍可注册和打开；
- 管理员待办 NO_DATA、503 和 390px 布局保留原表现；
- 管理员实际入口每次导航/点击只断言本次新增的精确 GET 端点，历史请求不能满足断言；
- 管理员待办非法来源/value/actionPath/状态/sampleCount 不落入固定空态；
- 管理员顶层异常、401/403/503/网络失败、刷新失败、筛选乱序、卸载迟到响应和 390px 布局；
- 未知 endpoint 报错并停止请求。

## 说明

夹具通过 Playwright 路由提供 `/getInfo`、`/getRouters`、`/education/context/*`、
`/education/scope/term/current`、`/education/student/notices*`、
`/education/student/announcements*` 与 `/education/teacher/announcements*` 响应。教师写请求由夹具分别记录 GET 列表、
GET 详情、POST 创建、PUT 编辑、POST 发布和 POST 归档。`overview-vue.spec.mjs`
另行提供 `/education/teacher/overview`、`/education/student/today-todos` 的夹具。
生产环境仍需 `education:notice:list`、`education:announcement:list/add/edit/publish/archive`
以及 `education:overview:teacher/student/admin` 的菜单、角色授权和对应服务端路由。
第 1 组 #41@549ef8e 已提供四个总览锚点脚本并报告其环境真实账号验证；本轮组合分支
包含这些脚本，但未在本轮目标库执行 SQL，也未做真实账号联调。管理员组件隔离入口
不进入生产构建；`admin-entry.spec.mjs` 加载实际生产构建，但所有接口仍是 Playwright
夹具，不能证明真实登录、HTTP、服务端授权、审计或数据库联调通过。
