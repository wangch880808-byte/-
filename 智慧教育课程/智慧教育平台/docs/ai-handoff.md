# 交接说明（给接手的 Agent / 开发同学）

> ⚠️ **本文是 2026-10-25 的接手基线，部分内容已过期。** 此后前端已增加 `shell/auth.js`、`shell/context.js`、菜单管理、部门管理、37 项 Node 测试和权限/路由过滤；最新状态以 [`frontend-1b-delivery.md`](frontend-1b-delivery.md) 为准，历史接手过程见 [`frontend-takeover.md`](frontend-takeover.md)。本文第 4～9 节请对照阅读。

| 项 | 内容 |
|---|---|
| 更新时间 | 2026-10-25（历史基线） |
| 工程根 | `D:\Project\--main\--main\智慧教育课程\智慧教育平台` |
| 交接范围 | 第 1 组前端（1B）已完成的公共外壳、请求客户端、登录页修复、管理员端三个页面，以及为跑通环境所做的后端配置改动 |
| 项目阶段 | 架构准备 + 登录联调；教学业务代码为零 |
| 服务现状 | 后端**已停止**；Redis 6379 **运行中**（登录自启）；MySQL `zhijiao_m0` 可用 |

> 先读仓库根 `AGENTS.md` 与 `CONTRIBUTING.md`，它们对本项目的开发边界有硬性要求（不伪造构建产物、不擅自初始化数据库、保护原始需求文档等）。本文只补充"当前实际状态 + 已做的改动 + 坑"。

---

## 1. 一句话现状

后端是若依（RuoYi 3.9.2 / Spring Boot 3.5.16 / Java 17）原样 + 一处登录角色校验；前端已从"三份复制粘贴的静态页"重构为"公共外壳 + 业务页注册"，管理员端已有用户、角色、审计日志三个可用的真实页面；**教师端/学生端仍只有导航外壳，没有业务页面**。

---

## 2. 运行环境与启动

### 2.1 依赖

| 项 | 位置 / 版本 | 备注 |
|---|---|---|
| JDK | `C:\Users\Asus\.jdks\temurin-17`（17.0.20.1） | 机器全局 `JAVA_HOME` 仍是 `D:\IDE\JDK`（25），项目通过 `.env` 固定用 17 |
| Maven | `D:\tools\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin\mvn.cmd` | 本地仓库 `D:\mvnrep`；settings.xml 有一个无效 mirror 警告，不影响构建 |
| MySQL | 8.0.44，`localhost:3306` | 库 `zhijiao_m0`：75 张表（18 sys + 11 quartz + 2 gen + 44 edu），7 用户 / 4 角色 |
| Redis | `D:\tools\Redis\Redis-8.6.1`，`127.0.0.1:6379` | 有密码，配置在 `backend/.env`；后端使用 **DB 15** |
| Chrome | `C:\Program Files\Google\Chrome\Application\chrome.exe` | 只用于本机回归探针 |

数据库连接串、账号、Redis 密码、令牌密钥都在 **`backend/.env`**（本机私有、已 gitignore）。文档中不复制密码，接手时直接读该文件。
三端演示账号见 `docs/demo-login-accounts.txt`。

### 2.2 启动后端

```powershell
cd 'D:\Project\--main\--main\智慧教育课程\智慧教育平台\backend'
Set-ExecutionPolicy -Scope Process Bypass -Force
$env:JAVA_HOME = 'C:\Users\Asus\.jdks\temurin-17'
D:\tools\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin\mvn.cmd -B -pl ruoyi-admin -am clean package -DskipTests
.\run-local.ps1
```

- **构建前必须先停后端**，否则 `clean` 删不掉被占用的 `ruoyi-admin.jar`。
- `run-local.ps1` 从 `.env` 读 `RUOYI_JAVA_HOME`，所以不必全局改 JDK。
- 启动约 10 秒。访问 <http://127.0.0.1:8080/login.html>。
- 前端资源被打进 jar 的 `static/`，**改前端必须重新打包并重启**，没有热更新。

### 2.3 Redis

正常开机登录会自动启动（启动文件夹里的 `Redis-6379.vbs`）。手工启动：

```powershell
wscript.exe "D:\tools\Redis\Redis-8.6.1\start-redis.vbs"
```

- Windows 服务 `Redis` 仍指向已删除的旧路径 `D:\redis-windows-8.6.1\...`，**服务本身是坏的**；修它需要管理员权限（`sc.exe config Redis binPath= "D:\tools\Redis\Redis-8.6.1\RedisService.exe"`）。
- 该 Redis 是 Cygwin 版：**不能给它传绝对路径参数**（会被转成 `/Redis-8.6.1/D:\...`），必须先 `cd` 到目录再用相对配置名启动，VBS 里已经这样处理。

---

## 3. 我做过什么

### 3.1 后端（只动了 3 个文件）

| 文件 | 改动 | 原因 |
|---|---|---|
| `backend/ruoyi-admin/pom.xml` | 静态资源 `<includes>` 增加 `shell/**`、`pages/**` | 原白名单只列了 7 个文件，新目录不会被打进 jar |
| `backend/ruoyi-framework/.../config/SecurityConfig.java` | GET 匿名放行增加 `/shell/**`、`/pages/**` | 否则新目录的 css/js 返回 401 |
| `backend/run-local.ps1` | 支持 `.env` 的 `RUOYI_JAVA_HOME` | 固定用 JDK 17，不影响机器默认 JDK 25 |

另外：
- 新增 `backend/.env`（私有，gitignore）与 `.env.example` 的 `RUOYI_JAVA_HOME` 说明；
- 新建数据库 `zhijiao_m0` 并按 `sql/README.md` 顺序导入（若依系统表、Quartz、44 张教育表、演示数据、登录账号）。

> 这两处后端配置改动属于 1A / 框架范围，合并前建议让 1A 过一眼。

### 3.2 前端（重构 + 新增）

| 文件 | 状态 | 说明 |
|---|---|---|
| `frontend/shell/shell.css` | 新增 | 三份内联样式合并（admin 少 `.menu-subhead`，取并集） |
| `frontend/shell/shell.js` | 新增 | 顶部栏、菜单、面包屑、页签、内容区、403 视图、业务页注册 |
| `frontend/shell/api.js` | 新增 | 统一请求客户端 |
| `frontend/pages/admin/admin.css` | 新增 | 管理页公共样式（表格 / 表单 / 弹窗 / 菜单树） |
| `frontend/pages/admin/admin-ui.js` | 新增 | 公共交互：确认框、表单弹窗、菜单树、部门下拉、轻提示 |
| `frontend/pages/admin/user-list.js` | 新增 | 用户管理：增删改查 + 停用 + 重置密码 |
| `frontend/pages/admin/role-list.js` | 新增 | 角色管理：增删改查 + 菜单权限树 + 数据范围 |
| `frontend/pages/admin/oper-log.js` | 新增 | 审计日志：筛选 + 详情 |
| `frontend/admin.html` | 重写 | 10.4 KB / 256 行 → 约 2 KB / 40 行，只保留角色与菜单配置 |
| `frontend/teacher.html` | 重写 | 10.3 KB → 约 1.1 KB |
| `frontend/student.html` | 重写 | 10.1 KB → 约 1.2 KB |
| `frontend/login.html` | 修复 | 角色单选点 `<span>` 加 `display:block`（原圆点压字） |
| `frontend/login.js` | 修复 | 超时、防重复提交、验证码竞态、账号/密码保留策略、失败后聚焦 |
| `frontend/role-home.js` | 小改 | 401 跳转带 `?logout=expired` |
| `frontend/README.md` | 更新 | 目录树、请求约定、业务页注册协议、上下文需求链接 |

三个原始 HTML 备份在 `D:\Project\--main\_shell_backup\`（仓库外）。

### 3.3 文档

| 文件 | 说明 |
|---|---|
| `frontend/README.md` | 已更新为真实结构 + 注册协议 + 请求约定 |
| `docs/group2-context-api-requirements.md` | 给第 2 组的课程/教学班上下文接口需求（1-03 的阻塞项） |
| `docs/ai-handoff.md` | 本文 |

---

## 4. 代码地图

### 4.1 `frontend/shell/shell.js`

| 函数 / 导出 | 作用 |
|---|---|
| `init()` | 消费 `window.EDU_PAGES` 队列 → 渲染框架与菜单 → 解析 `?g=&i=` → 挂载页面 |
| `renderFrame()` | 追加 header/layout 到 body（必须是 body 直接子元素，才能命中 `body.auth-pending > ...` 隐藏规则） |
| `renderMenu()` | 渲染两级菜单，支持 `group.subhead` 小标题 |
| `select(gi, ii)` / `mountPage()` | 高亮、面包屑，并调用业务页 `mount(host, ctx)` / `unmount()` |
| `renderTabs()` / `addTab()` / `closeTab()` | 页签，存 `localStorage`，key 为 `zhijiao_tabs_<role>` |
| `renderForbidden(message)` | 403 时整体替换内容区，不保留业务数据 |
| `window.EduShell` | `registerPage(g,i,page)`、`getCurrent()`、`getContext()`（当前返回 `null`）、`showForbidden()`、`menu`、`role` |

### 4.2 `frontend/shell/api.js`

`EduApi.get/post/put/del/request`：

- 自动带 `Authorization: Bearer <sessionStorage zhijiao.login.token>`；
- 默认 15 秒超时（`options.timeout` 可覆盖）；
- `401`（HTTP 或业务码）→ 清会话 → `login.html?logout=expired`；
- `403` → 派发 `edu:forbidden` 事件（外壳渲染无权限视图）→ 抛 `error.forbidden`；
- 其他非 200 → 抛 `Error`，带 `status` / `code`，message 用服务端 `msg`；
- `silent403: true` 可让页面自行处理 403。

### 4.3 `frontend/pages/admin/`

| 文件 | 注册位置 | 依赖接口 |
|---|---|---|
| `user-list.js` | 系统管理（g=6）→ 用户管理（i=0） | `/system/user/list`、`/system/user`、`/system/user/{id}`、`/system/user/changeStatus`、`/system/user/resetPwd`、`/system/dept/list` |
| `role-list.js` | g=6 → 角色管理（i=1） | `/system/role/list`、`/system/role`、`/system/role/{id}`、`/system/role/changeStatus`、`/system/menu/treeselect`、`/system/menu/roleMenuTreeselect/{roleId}` |
| `oper-log.js` | g=6 → 审计日志（i=5） | `/monitor/operlog/list` |

`admin-ui.js` 暴露 `AdminUI.confirm / modal / toast / menuTree / deptOptions / businessType`，全部用 DOM API 构造，不拼 innerHTML 写用户数据。

---

## 5. 关键约定（改代码前必读）

### 5.1 业务页注册协议

页面脚本必须在 `shell.js` **之前**加载，用队列登记：

```js
window.EDU_PAGES = window.EDU_PAGES || [];
window.EDU_PAGES.push({
  g: 6, i: 0,                       // 菜单分组下标 / 菜单项下标
  page: {
    mount(host, ctx) { /* 渲染到 host */ },
    unmount() { /* 清定时器/订阅/在途请求 */ }
  }
});
```

`shell.js` 加载后也可以 `EduShell.registerPage(g, i, page)` 补注册（注册到当前页会立即挂载）。
`ctx` 目前恒为 `null`（上下文未接入）。

### 5.2 页面结构约定

- 页面自己负责渲染 `host` 内部，**不要**重建顶部栏/菜单；
- 样式类统一 `adm-` 前缀，放在 `pages/admin/admin.css` 或页面自己的 css；
- 接口一律走 `EduApi`，不要直接 `fetch`；
- 页面 `unmount()` 必须让后续异步回调失效（现有页面用 `disposed` 标志）。

### 5.3 菜单下标（管理员端 g=6）

`0 用户管理 / 1 角色管理 / 2 菜单管理 / 3 字典管理 / 4 通知中心 / 5 审计日志 / 6 第三方登录`

教师端 g=0/1，学生端 g=0/1，见各自 HTML 里的 `window.EDU_SHELL.menu`。

---

## 6. 已完成的功能（可演示）

| 功能 | 入口 | 状态 |
|---|---|---|
| 三端登录（含入口角色校验） | `/login.html` | ✅ 三账号联调通过 |
| 管理员用户管理 | `admin.html?g=6&i=0` | ✅ 列表/搜索/分页/新增/编辑/停用/启用/重置密码/删除 |
| 管理员角色管理 | `admin.html?g=6&i=1` | ✅ 列表/搜索/新增（菜单树）/编辑/停用/删除 |
| 管理员审计日志 | `admin.html?g=6&i=5` | ✅ 筛选/分页/详情（请求参数、返回结果） |
| 403 无权限视图 | 全站 | ✅ 调用无权限接口时内容区替换为提示 |
| 401 会话失效 | 全站 | ✅ 清会话 → 登录页并提示「登录状态已失效」 |
| 教师/学生端 | `teacher.html` / `student.html` | 🟡 只有外壳与导航，内容区仍是占位 |

---

## 7. 验证方法与最近结果

回归探针（本机专用，**不在仓库内**）：`D:\Project\--main\_shell_backup\`

| 脚本 | 覆盖 |
|---|---|
| `probe_shell.js` | 三端渲染：菜单数、面包屑、页签、头像标题、布局尺寸、CSS 生效 |
| `probe_interact.js` | 深链接 `?g=&i=`、二级小标题、页签增删、折叠展开 |
| `probe_login.js` | 验证码加载、正常登录、错误验证码自动刷新、错角色拒绝、JS 异常 |
| `probe_api.js` | 正常请求、403 视图、401 跳登录并提示 |
| `probe_user_page.js` | 用户页只读版本（历史） |
| `probe_admin_pages.js` | 用户 CRUD、角色 CRUD、审计查询/详情/筛选 |

运行方式：

```powershell
& 'C:\Users\Asus\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe' 'D:\Project\--main\_shell_backup\probe_admin_pages.js'
```

前置条件：后端已启动、Redis 可用、Chrome 存在。`probe_shell.js` 额外需要 `_tokens.json`（已删除，需重新生成）；其余脚本自己登录取令牌。
最近一次全部为 `ALL_PASS`，截图在 `_shell_backup/shots/`。

---

## 8. 已知的坑（按踩中概率排序）

1. **新增前端目录要改两处**：`ruoyi-admin/pom.xml` 的 `<includes>` + `SecurityConfig` 的 GET 放行，否则页面 404 或 401。
2. **`.ps1` 必须带 UTF-8 BOM**：PowerShell 5.1 按 GBK 读无 BOM 文件，中文会变乱码并导致语法错误。编辑 `run-local.ps1` 后请确认前 3 字节是 `EF BB BF`。
3. **执行策略**：直接 `.\run-local.ps1` 会被拦，需要 `Set-ExecutionPolicy -Scope Process Bypass`。
4. **若依的 401/403 是 HTTP 200 + `{code:401/403}`**，fetch 不会抛错，必须看 `code`。
5. **RuoYi 删除是逻辑删除**（`del_flag=2`），数据库里会留行。
6. **Redis 用 DB 15**，验证码键是 `captcha_codes:<uuid>`；用 redis-cli 调试必须加 `-n 15`。
7. **前端改动必须重新打包**（资源在 jar 内），没有热更新。
8. **构建前停后端**，否则 `jar` 被占用导致 `clean` 失败。
9. **管理员角色**（`roleKey=admin`）不能被编辑/删除，服务端会拒绝。
10. **菜单树提交要带祖先节点**，`AdminUI.menuTree().getCheckedIds()` 已自动补齐。
11. **ID 类型**：现有演示 ID 约 9e11，JS 数字安全；若后续用雪花 ID，需要和后端约定返回字符串。
12. **Cygwin Redis 的路径转换**：给 `redis-server.exe` 传绝对路径会被改写，必须 `cd` + 相对路径。
13. **数据库是演示库**，不是正式迁移基线；`sql/upstream/ruoyi/*.sql` 含 `DROP TABLE`，只能对空库执行。

---

## 9. 未完成 / 阻塞

| 项 | 状态 | 阻塞点 |
|---|---|---|
| 1-03 课程/教学班上下文 | ❌ 未开始 | 等第 2 组接口，需求见 `docs/group2-context-api-requirements.md` |
| 1-03 业务页入口挂载 | ❌ | 等业务页出现 |
| 1-04 菜单管理、部门管理 | ❌ | 部门管理与 2-03 重叠，需先定边界；菜单管理可直接做 |
| 角色编辑回显（菜单树）回归 | ⚠️ 未测 | 只测了新增/删除 |
| 移动端适配 | ⚠️ 未验证 | 学生端未来要手机优先 |
| 管理页并发/重复提交 | ⚠️ 未测 | 依赖 modal 的 busy 状态 |
| 1-01 / 1-02（1A） | ❌ | 统一 403 响应、受限教务管理员、审计埋点范围 |
| 95% 的教学业务（九领域） | ❌ | 第 2～6 组，`ruoyi-education` 仍为空 |
| 授权菜单 | ❌ | 三端菜单仍是硬编码，`getRouters` 只用于校验令牌 |
| Git 仓库 / PR 流程 | ❌ | 仓库无 `.git`，分工文档第 7 节无法执行 |
| Vue 技术选型 | ❌ | 决定 1-04 剩余页面与后续业务页写法 |
| CI / 自动化测试 | ❌ | 探针在本机，未入库 |

---

## 10. 接手建议顺序

1. **先跑起来**：按第 2 节构建启动，用 `admin / teacher / student` 三个账号各登一次；
2. **读代码**：`shell/shell.js` → `shell/api.js` → `pages/admin/user-list.js`（最完整的示例）；
3. **补回归**：先跑 `probe_admin_pages.js` 确认现状；
4. **优先级建议**：
   - P0：等 2 组接口 → 做上下文（1-03 收尾）；
   - P1：菜单管理页、角色编辑回显回归；
   - P2：部门管理（先谈边界）、移动端；
5. 每加一个新目录/页面，记得同步 `pom.xml` 与 `SecurityConfig`，并更新 `frontend/README.md`。

---

## 11. 禁止事项（项目规则，摘自 AGENTS.md）

- 不伪造 `pom.xml`、`package.json`、启动类、测试结果或"已可运行"的结论；
- 不擅自初始化/清空数据库，不执行 `sql/upstream` 里的 `DROP TABLE` 脚本到非空库；
- 不修改 `智慧教育课程需求分析.docx`、`功能架构优化方案.md` 等来源文档；
- 不给原型/页面写假数据冒充业务实现；未实现的入口不在正式菜单开放；
- 不把密码、令牌、学生个人信息写进代码、示例、日志或提交物；
- 删除历史文件前必须有清单、备份与用户确认。

---

## 12. 回滚

| 对象 | 方式 |
|---|---|
| 三个原始 HTML | 从 `D:\Project\--main\_shell_backup\` 拷回 `frontend/`，重新打包 |
| 后端配置 | 还原 `pom.xml` 的 `<includes>`、`SecurityConfig` 的放行行、`run-local.ps1` |
| 数据库 | `DROP DATABASE zhijiao_m0`（或重新按 `sql/README.md` 导入） |
| 工作区 ACL | `D:\Project\--main-dsh-acl-recovery\` 内的回滚脚本 |

---

## 13. 文档索引

| 文档 | 用途 |
|---|---|
| `README.md` | 项目总览与目录 |
| `AGENTS.md` | 开发边界与代理规范 |
| `CONTRIBUTING.md` | 分支、提交、PR 规则 |
| `docs/architecture.md` | 领域与数据约束 |
| `docs/api-contracts.md` | 正式接口约定（上下文接口定稿后并入此文件） |
| `docs/group2-context-api-requirements.md` | 第 2 组接口需求（当前阻塞项） |
| `docs/demo-login-accounts.txt` | 三端演示账号 |
| `docs/database-design.md` | 44 张 M0 业务表设计 |
| `docs/development-plan.md` | 五阶段路线与 G0～G4 关口 |
