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

## #32 复审补修（2026-11-08）

### 复现证据（six@ee4dad9）

在原有 30 项用例之外补 31 项生命周期断言；未改产品代码时（最终测试脚本 + HEAD 产品源码）
运行结果为 **42 passed，19 failed，0 console/page errors**。失败覆盖：

- 核验期间勾选「仅未读」/改筛选后，核验通过只发列表请求，未补首次未读数；
- 核验期间切到课程公告后，旧通知任务仍发通知请求并覆盖公告区域；
- pagehide 后排队读请求仍派发；
- pagehide 后迟到列表、未读数、详情、发布响应仍更新旧页面/弹窗/角标或弹旧提示；
- pageshow(persisted) 未重新核验，恢复后不加载当前视图；
- 通知页签恢复会重放旧「全部已读」；
- 课程公告页签 pagehide 后排队读请求仍派发。

### 本轮修改

- `view-host.js`：`createAuthGate` 增加 `onHidden`、`onRestore` 和 `cancelPending()`。
  - pagehide 统一作废登录等待、排队 action，并通知视图作废请求代际、关闭临时 UI；
  - pageshow 且 persisted=true 时重新调用现有 `EduViews.whenAuthorized()`（复用角色壳核验），
    只执行视图按当前页签返回的只读 action；离开前排队的写操作不进入恢复队列；
  - Mock 预览没有角色壳，恢复后保持 ready，只执行只读 action。
- `student/notices.js`：
  - 筛选/分页保存为「列表 + 首次未读数只补一次」，核验通过后按最终条件只请求一次列表；
  - 切到课程公告时 `cancelPending()`，核验/失败回调不再写公告区域；
  - 通知页签恢复只重读列表和未读数；公告页签由子视图自己的门卫恢复；
  - 全部已读确认文案、全量范围、`PUT read-all` 不变。
- 三个视图增加请求代际 `epoch`：pagehide/恢复/cleanup 后，已经排队或在途的读写响应
  不得再更新页面、弹窗或 toast；详情/编辑/确认弹窗在 pagehide 时关闭。
- `role-home.js` 未修改；本轮无 Java、Mapper、SQL、Mock 隔离或 401 处理改动。

### 验证结果

同一测试页、同一 Edge 无头配置：

- 新增后完整脚本：**61 passed，0 failed，0 console/page errors**；
- 用 `git show HEAD:智慧教育课程/智慧教育平台/frontend/tests/announcement-lifecycle.js`
  取回修改前 30 项原脚本
  独立重跑：**30 passed，0 failed，0 console/page errors**（原断言未削弱）。

### 测试边界

- 测试夹具：假 `EduHttp`（请求进入 pending，由 runner 显式 resolve/reject）+
  假 `EduViews.whenAuthorized`（手动 resolve），不连接真实后端。
- 缓存恢复：用合成 `PageTransitionEvent('pagehide'/'pageshow', {persisted:true})`
  模拟事件顺序，不是真实浏览器 bfcache 恢复。
- 真实登录、真实 bfcache 恢复、真实后端联调本轮均未执行；角色壳重新核验仅通过
  调用现有 `whenAuthorized()` 的路径验证，`role-home.js` 的 `/getInfo`、`/getRouters`
  行为未在本轮测试范围内。

## 未关闭

- 创建公告仍无服务端幂等键；按钮禁用不构成服务端幂等。
- Mock 持久化 `zhijiao.mock`/binding、`mock.js` 随 `src/**` 进入生产、Mock ID 生成使用超安全整数 Number。
- Mock 参数解析与后端 `Long.parseLong`/`Integer.parseInt` 仍有差异（前导零不应一概判错）。
- 文档残留：`frontend/README.md` 旧“待实现/后端待实现”、`docs/6-02-frontend-integration.md` 503 旧表述、46 项断言声明不可复现。
- `role-home.js` HTTP 401 空正文/HTML 缺口不在本轮修改范围，需第 1 组/负责人处理。
