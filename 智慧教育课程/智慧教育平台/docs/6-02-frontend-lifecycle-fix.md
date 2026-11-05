# 6-02 前端生命周期修复记录（#32）

> 日期：2026-11-06｜分支：`six`｜状态：Draft，未 Ready、未合并
> 范围：`teacher/announcements.js`、`student/announcements.js`、`student/notices.js`，以及 `view-host.js`、`ui-kit.js` 的最小兼容修改。

## 修复前证据（#32@0624225，静态快照 8126）

- 同一视图挂载三次后点击刷新：列表 API 调用 3 次（应为 1）。
- 打开公告详情后重新挂载：`.edu-overlay` 仍为 1，旧弹窗未关闭。
- `/getInfo` 未返回时教师公告 API 已发起（annCalls=1）。
- `mock-preview` 的 `EduMock.enable()` 写入 `zhijiao.mock=1` 和演示 binding；同 tab 打开 `teacher.html` 后 Mock 仍启用。
- 全部已读在 binding A 页点击时，实际标记 A+B 全部 3 条（A 2、B 1）。

## 本轮修复

### 登录门卫

`view-host.js` 新增向后兼容的 `EduViews.createAuthGate(options)`：

- 首次加载、筛选、分页、刷新、重试、详情和写操作统一经 `request(action)`；
- `pending` 只保存最新 action，不发业务请求；
- 核验失败不执行 action；
- `pagehide` 使旧核验和排队 action 失效；
- `cleanup` 后迟到的登录结果不执行；
- 显式 Mock 预览直接就绪，真实模式仍调用 `whenAuthorized`。

三个视图均使用该门卫，不复制总览组件，不依赖 #34。

### 请求代际与派发前检查

- 教师公告：列表、详情、表单各自持有代际；`load()` 在 `Promise.resolve().then` 内、真正调用 API 前检查 `disposed/ticket`。
- 学生公告：列表和详情代际；同一模式。
- 学生通知：列表、未读数、详情分别持有代际；页签切换使旧列表/未读/详情响应失效。
- cleanup/pagehide 后排队微任务不派发；已发出的写请求可能已在服务端执行，cleanup 只阻止旧视图继续派发和写 UI。

### 弹窗与监听器所有权

- 三个视图移除根监听、binding 输入监听，并关闭自有的详情、编辑、新建弹窗。
- `UI.confirm` 返回的 Promise 增加向后兼容的 `.close()`；确认框由视图跟踪，cleanup 关闭，旧确认回调不会触发发布/归档。
- 只关闭本视图登记的弹窗，不触碰新视图或其他组件的弹窗。

### 全部已读范围

- 保持后端全量接口和请求参数不变；
- 确认文案改为：
  “将标记本人所有教学班当前可见的未读通知，包括其他教学班，不受当前筛选和分页限制。”
- 成功后重拉列表和未读数，不本地减数。

## 测试

仓库内脚本：`frontend/tests/announcement-lifecycle.html` + `announcement-lifecycle.js`。

运行：

```bash
cd frontend
python -m http.server 8130 --bind 127.0.0.1

PLAYWRIGHT_MODULE="<playwright 模块路径>" \
PLAYWRIGHT_CHANNEL=msedge \
SIX_TEST_BASE=http://127.0.0.1:8130 \
node frontend/tests/announcement-lifecycle.js
```

结果：**30 passed，0 failures，0 console/page errors**（修复后）。

覆盖：

- pending/失败时各入口零业务调用；
- 核验通过后最新筛选；
- 三次挂载一次刷新只请求一次；
- cleanup 关闭弹窗、旧确认不写；
- A/B binding、状态、页签乱序不覆盖；
- 旧详情不写新弹窗；
- cleanup 后排队微任务不派发；
- 全部已读文案与全量范围一致；
- 13 个接口方法/路径回归。

## 未关闭

- 创建公告仍无服务端幂等键；按钮禁用不构成服务端幂等。
- Mock 持久化 `zhijiao.mock`/binding、`mock.js` 随 `src/**` 进入生产、Mock ID 生成使用超安全整数 Number。
- Mock 参数解析与后端 `Long.parseLong`/`Integer.parseInt` 仍有差异（前导零不应一概判错）。
- 文档残留：`frontend/README.md` 旧“待实现/后端待实现”、`docs/6-02-frontend-integration.md` 503 旧表述、46 项断言声明不可复现。
- `role-home.js` HTTP 401 空正文/HTML 缺口不在本轮修改范围，需第 1 组/负责人处理。
