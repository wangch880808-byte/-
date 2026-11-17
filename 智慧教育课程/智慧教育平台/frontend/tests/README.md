# 6-04 学生通知 Vue 夹具

本目录是 6-04 学生通知、学生公告与教师公告草稿新建/编辑前端原型的浏览器夹具，不连接后端、不写数据库。

## 运行

```bash
cd frontend
npm run build
node tests/serve-dist.mjs
# 另开终端
NOTICE_TEST_BASE=http://127.0.0.1:4174 node tests/notice-vue.spec.mjs
```

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
- 通知与公告详情弹窗打开聚焦、Esc 关闭、关闭后焦点归还触发按钮。

## 说明

夹具通过 Playwright 路由提供 `/getInfo`、`/getRouters`、`/education/context/*`、
`/education/student/notices*`、`/education/student/announcements*` 与
`/education/teacher/announcements*` 响应。教师写请求由夹具分别记录 GET 列表、
GET 详情、POST 创建和 PUT 编辑。生产环境仍需 `education:notice:list`、
`education:announcement:list/add/edit` 权限和对应服务端路由；当前 `sys_menu`
尚未配置。
