# 6-02 生命周期与离线 Mock 回归测试

本目录只放测试页和可移植脚本，不提交 Playwright、浏览器、node_modules、截图或临时输出，不创建产品 `package.json`/锁文件。

## 前置

- Node.js
- 已安装 Playwright 及其浏览器（或可用的本机 Chrome/Edge）
- 静态服务器能访问 `frontend/` 根目录

## 运行

在 `frontend/` 根目录启动静态服务器：

```bash
cd frontend
python -m http.server 8130 --bind 127.0.0.1
```

另开终端，在项目根目录运行：

```bash
PLAYWRIGHT_MODULE="<playwright 模块路径或包名>" \
PLAYWRIGHT_CHANNEL=msedge \
SIX_TEST_BASE=http://127.0.0.1:8130 \
node frontend/tests/announcement-lifecycle.js
```

- `PLAYWRIGHT_MODULE`：Playwright 模块路径/包名，由本机环境提供；不写入仓库。
- `PLAYWRIGHT_CHANNEL`：默认 `msedge`，也可设为 `chrome` 等。
- `SIX_TEST_BASE`：静态服务器地址，默认 `http://127.0.0.1:8130`。
- 测试页：`announcement-lifecycle.html`，使用假 `EduHttp` 和假 `EduViews.whenAuthorized`；不连接后端、不校验真实登录、不发送真实请求。
- 页面离开/恢复用合成 `PageTransitionEvent` 模拟 `pagehide` / `pageshow(persisted=true)`，不等同于真实浏览器 bfcache 恢复。
- 脚本只输出 JSON 到 stdout，不写截图或临时文件。

### 离线 Mock 隔离回归

```bash
PLAYWRIGHT_MODULE="<playwright 模块路径或包名>" \
PLAYWRIGHT_CHANNEL=msedge \
MOCK_TEST_BASE=http://127.0.0.1:8132 \
node frontend/tests/mock-isolation.js
```

- `MOCK_TEST_BASE`：静态服务器地址，默认 `http://127.0.0.1:8132`，可复用同一 `frontend/` 静态根。
- 直接驱动真实 `mock-preview.html`、`teacher.html`、`student.html`；
  `/getInfo`、`/getRouters`、`/logout`、`/education/**` 由 Playwright route 夹具返回。
- 不连接真实后端、不创建真实登录；预览脚本位于 `frontend/preview/education-mock.js`，
  不在打包范围 `frontend/src/**` 内。

### 草稿 ID 边界回归

```bash
PLAYWRIGHT_MODULE="<playwright 模块路径或包名>" \
PLAYWRIGHT_CHANNEL=msedge \
MOCK_ID_TEST_BASE=http://127.0.0.1:8134 \
node frontend/tests/mock-draft-id.js
```

- `MOCK_ID_TEST_BASE`：静态服务器地址，默认 `http://127.0.0.1:8134`。
- 通过 Playwright route 把预览 Mock 的公告序号推进到 `Number.MAX_SAFE_INTEGER` 附近，
  验证连续创建草稿的字符串 ID 不同、按 ID 查详情、编辑互不影响。
- 不连接真实后端、不创建真实登录。

### ID、分页与筛选解析回归

```bash
PLAYWRIGHT_MODULE="<playwright 模块路径或包名>" \
PLAYWRIGHT_CHANNEL=msedge \
MOCK_PARAM_TEST_BASE=http://127.0.0.1:8135 \
node frontend/tests/mock-params.js
```

- `MOCK_PARAM_TEST_BASE`：静态服务器地址，默认 `http://127.0.0.1:8135`。
- 直接调用 `EduMock.request`，覆盖显式空查询值（沿用 `EduHttp` 省略空串的规则）。
- 覆盖 ID 前导零/正号/Long 上界/溢出/路径单次解码、学生 bindingId 省略与显式空、
  分页默认值和范围、status/kind/unreadOnly 空值/大小写/非法值。
- 不连接真实后端、不创建真实登录。

## 覆盖

- 登录 pending/失败时首个加载、筛选、刷新零业务请求；
- 核验通过后只按最新筛选请求一次；
- 核验期间修改筛选后补齐首次未读数请求；
- 核验期间切到课程公告后只加载公告页签，旧通知任务不发请求、不覆盖公告区域；
- 公告页签子视图的刷新等 `data-action` 不被父通知视图截获；
- 同一视图挂载三次后一次刷新只产生一次列表请求；
- 教师公告清空 binding 后立即作废旧列表代际：已排队请求不派发，旧成功/旧失败响应不覆盖未选择提示；
- 教师公告 A 班 → 清空 → B 班的乱序场景中，A 班旧响应不覆盖 B 班结果；
- cleanup 关闭详情/编辑/确认弹窗；
- cleanup 后旧确认不发起发布/归档；
- 状态、A/B binding、页签切换的旧响应不覆盖新结果；
- 旧详情响应不写入新弹窗；
- cleanup/pagehide 后排队微任务不派发 API；
- pagehide 后迟到列表、未读数、详情、发布响应不更新旧页面/弹窗/角标，不弹旧提示；
- pageshow persisted 恢复核验期间零业务调用，通过后只重读当前视图；
- 恢复核验期间再改筛选仍按最终条件补未读数；
- 切到公告页签后迟到的全部已读响应不弹提示、不加载通知、不覆盖公告区域；
- 恢复不重放旧发布、全部已读操作；
- 教师公告、学生通知（两个页签）、学生公告三视图的离开/恢复路径；
- 全部已读确认文案为全量范围，PUT 不带 binding；
- 13 个接口方法/路径回归。

### Mock 隔离

- 预览教师公告、学生通知、课程公告均可用；
- 预览开关、教学班切换/清空不读写真实 sessionStorage；
- 同标签页从预览转运行页沿用原教学班与登录态，走真实请求且不显示 Mock 横幅；
- 教师/学生运行页的旧 `zhijiao.mock=1` 与 `?mock=1` 均无效；
- 登录核验前零业务请求；真实 500 显示错误状态，不退化为 Mock 成功。

### 草稿 ID

- 连续创建两条草稿得到不同字符串 ID，越过 `Number.MAX_SAFE_INTEGER` 后仍精确递增；
- 按各自 ID 查到正确详情；编辑一条不影响另一条；新 ID 不与内置公告冲突。

### ID、分页与筛选解析

- ID 接受前导零/正号并规范成十进制字符串；Long 上界合法但不存在的路径 ID 返回 404，
  零、负数、非法格式和溢出返回 400；路径 ID 只解码一次。
- 学生接口区分省略 bindingId 与显式空值；显式空值和纯空白返回 400。
- 分页缺失/空串/纯空白用默认值；整数范围与小数、指数、十六进制、溢出处理对齐 Controller。
- status/kind/unreadOnly 的空值、大小写和非法值处理对齐 Controller。
