# 6-03 阶段1回归测试

本目录只放测试页和可移植脚本，不提交 Playwright、浏览器、node_modules、截图或临时输出，不创建产品 `package.json`/锁文件。

## 前置

- Node.js
- 已安装 Playwright 及其浏览器（或可用的本机 Chrome/Edge）
- 静态服务器能访问 `frontend/` 根目录

## 运行

在 `frontend/` 根目录启动静态服务器：

```bash
cd frontend
python -m http.server 8125 --bind 127.0.0.1
```

另开终端，在项目根目录运行：

```bash
PLAYWRIGHT_MODULE="<playwright 模块路径或包名>" \
PLAYWRIGHT_CHANNEL=msedge \
OVERVIEW_TEST_BASE=http://127.0.0.1:8125 \
node frontend/tests/overview-stage1.js
```

- `PLAYWRIGHT_MODULE`：Playwright 模块路径/包名，由本机环境提供；不写入仓库。
- `PLAYWRIGHT_CHANNEL`：默认 `msedge`，也可设为 `chrome` 等。
- `OVERVIEW_TEST_BASE`：静态服务器地址，默认 `http://127.0.0.1:8125`。
- 测试页：`overview-stage1.html`，使用假 `EduHttp`，不连接后端、不校验真实登录、不发送真实请求。
- 测试 context 固定 `timezoneId: 'Asia/Shanghai'`，避免依赖执行机器时区。
- 阶段1脚本只输出 JSON 到 stdout；阶段2可用 `OVERVIEW_SCREENSHOT_DIR` 输出到仓库外独立目录。测试不提交截图/临时文件。

## 覆盖

- 真实模式登录 pending：筛选/连续操作/刷新零 API；核验通过后按最新筛选只请求一次。
- 核验 false/超时：筛选不能绕过；重试先重新核验。
- BFCache 恢复重新核验，核验完成前零调用。
- cleanup 后迟到核验不请求、不写页面。
- 登录核验 resolve 后、API 微任务派发前 cleanup/pagehide/新筛选失效：不调用或只调用最新有效请求。
- 重复 metricCode（相同/冲突、交换顺序）、unit 非 COUNT、source 与配置不一致：单卡降级，其他卡保留。
- 正常 COUNT/source 不回归。


## 阶段2角色页回归

在项目根目录运行（同一静态服务器）：

```bash
PLAYWRIGHT_MODULE="<playwright 模块路径或包名>" PLAYWRIGHT_CHANNEL=msedge OVERVIEW_TEST_BASE=http://127.0.0.1:8125 OVERVIEW_SCREENSHOT_DIR="<仓库外截图目录>" node frontend/tests/overview-stage2.js
```

- 加载真实 `teacher.html`、`student.html`、`admin.html`。
- 通过 Playwright route 夹具拦截 `/getInfo`、`/getRouters`、`/logout`、`/education/**`；不连接真实后端，不创建真实登录，不冒充真实联调。
- 覆盖四页挂载、刷新/筛选、登录等待、核验失败重试、错角色、401/403、公告/通知/角标回归、非总览占位、迟到响应、学生通知入口、390px 总览和样式恢复、退出登录。
- `OVERVIEW_SCREENSHOT_DIR` 不设置时不写截图；输出目录与源代码分离。
