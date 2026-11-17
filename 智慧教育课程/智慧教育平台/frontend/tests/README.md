# 6-04 学生通知 Vue 夹具

本目录是 6-04 学生通知只读前端的最小浏览器夹具，不连接后端、不写数据库。

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
- 弹窗打开聚焦、Esc 关闭、关闭后焦点归还触发按钮。

## 说明

夹具通过 Playwright 路由提供 `/getInfo`、`/getRouters`、`/education/context/*`、
`/education/student/notices*` 响应。生产环境仍需 `education:notice:list` 权限和
`education/notices` 服务端路由；当前 `sys_menu` 尚未配置。
