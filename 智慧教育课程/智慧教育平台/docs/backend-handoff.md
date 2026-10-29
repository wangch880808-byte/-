# 后端交接说明

| 项 | 内容 |
|---|---|
| 交接日期 | 2026-10-28 |
| 交接方 | 第 1 组前端（1B） |
| 接收方 | 第 1 组后端（1A）及后续接手后端开发的同学 |
| 工程根 | `智慧教育课程/智慧教育平台/` |
| 技术栈 | RuoYi 3.9.2（Spring Boot 3.5.16 / Java 17）+ 原生静态页 + Vue 3（Vite，托管在 `/vue/`） |
| 当前状态 | 登录、权限菜单、管理员五个页面（用户/角色/菜单/组织架构/审计日志）均已在 Vue 版实现；教学业务与课程上下文接口未实现 |

---

## 1. 先跑起来

### 1.1 环境

| 依赖 | 要求 |
|---|---|
| JDK | 17（本机 `C:\Users\Asus\.jdks\temurin-17`，项目通过 `.env` 的 `RUOYI_JAVA_HOME` 指定） |
| Maven | 3.9+ |
| MySQL | 8.0.16+，库 `zhijiao_m0` |
| Redis | 6379，后端使用 DB 15 |

### 1.2 配置

复制 `backend/.env.example` 为 `backend/.env`，填写：

```
RUOYI_DB_URL / RUOYI_DB_USERNAME / RUOYI_DB_PASSWORD
RUOYI_TOKEN_SECRET（随机长字符串）
RUOYI_REDIS_HOST/PORT/DATABASE/PASSWORD
RUOYI_JAVA_HOME（可选，指定 JDK 17）
```

`.env` 已被 gitignore，**不要提交**。

### 1.3 初始化数据库

在**空库**按 `sql/README.md` 顺序执行：

1. `sql/upstream/ruoyi/ry_20260417.sql`（若依系统表）
2. `sql/upstream/ruoyi/quartz.sql`
3. `sql/mysql-v3-m0-core-schema.sql`
4. `sql/mysql-v3-m0-teaching-schema.sql`
5. `sql/demo-v3-m0-seed.sql`（演示数据）
6. `sql/demo-v3-m0-ruoyi-login-seed.sql`（三端演示账号）

> 上游脚本含 `DROP TABLE`，只能用于空测试库。

### 1.4 构建与启动

```powershell
cd '智慧教育平台/frontend/vue'
$env:VITE_BASE = '/vue/'
pnpm install        # 首次或清理后
pnpm build          # 产物 dist/ 会被后端打包

cd ../../backend
$env:JAVA_HOME = 'C:\Users\Asus\.jdks\temurin-17'
mvn.cmd -B -pl ruoyi-admin -am package -DskipTests
.\run-local.ps1
```

访问：

- Vue：<http://127.0.0.1:8080/vue/>
- 原静态页：<http://127.0.0.1:8080/login.html>
- 账号：`admin / admin123`、`teacher / teacher123`、`student / student123`（见 `docs/demo-login-accounts.txt`）

---

## 2. 前端是怎么接进后端的

| 文件 | 作用 |
|---|---|
| `backend/ruoyi-admin/pom.xml` | 资源目录 `frontend/vue/dist` → `static/vue`；`frontend/shell/**`、`pages/**` → `static` |
| `backend/.../config/SecurityConfig.java` | 放行 `GET /vue`、`/vue/**`、`/shell/**`、`/pages/**` |
| `backend/.../controller/common/VueUiController.java` | `/vue`、`/vue/` 转发到 `/vue/index.html` |
| `frontend/vue/vite.config.js` | `VITE_BASE=/vue/` 构建；开发时把 `/login`、`/system`、`/monitor`、`/education` 代理到 8080 |
| `frontend/vue/src/router/index.js` | hash 路由，避免后端为 `/vue/**` 做 SPA 回退 |

**注意**：Vue 的 `dist/` 默认被 gitignore。换机器或清理后必须先 `pnpm build`，否则 jar 里没有 Vue 页面（静态页不受影响）。

---

## 3. 1A 现在要做的后端工作

### 3.1 1-01 身份、权限、统一拒绝

| 改动 | 文件 |
|---|---|
| 新增 `ForbiddenException`（统一 `code=403`） | `ruoyi-common/.../exception/ForbiddenException.java` |
| 新增 `ScopeAssert`（`require/requireOwner`） | `ruoyi-common/.../utils/ScopeAssert.java` |
| 补 `getRoles()`、`hasAnyRole()` | `ruoyi-common/.../utils/SecurityUtils.java` |
| 500 脱敏（不要把 `e.getMessage()` 返回前端） | `ruoyi-framework/.../web/exception/GlobalExceptionHandler.java` |
| 停用/删除/改密/改角色后踢掉旧令牌 | `ruoyi-framework/.../web/service/TokenService.java` 新增 `deleteByUserId`，并在 `SysUserController` 调用 |
| 角色停用后刷新在线权限 | `SysRoleController`（`edit` 已调用 `refreshPermissionByRoleId`，`changeStatus` 需补） |

**验收**：业务越权返回 403；停用账号后旧 token 立即 401；SQL 异常不泄露表名。

### 3.2 1-02 受限教务管理员

| 事项 | 位置 |
|---|---|
| 建 `edu_admin` 角色（`data_scope=3` 或自定义）、授用户/角色查询/部门/审计菜单 | 新增 `sql/demo-v3-m0-edu-admin-seed.sql` |
| **禁止非超管分配 `admin` 角色**（当前最大提权漏洞） | `SysUserServiceImpl.insertUserRole`、`SysUserController.insertAuthRole` |
| 禁止非超管修改 admin 用户、修改自己的状态/角色/部门 | `SysUserController.edit`、`SysUserServiceImpl` |
| 非超管禁止操作 `role_key='admin'`；如开放角色编辑，需校验菜单子集 | `SysRoleServiceImpl.insertRole/updateRole/authDataScope` |
| 登录入口支持 `edu_admin` | `SysLoginService`（`LOGIN_ROLES` + 入口判断） |
| 前端配合：`edu_admin` 视为管理员端 | `frontend/role-home.js`（1B）或 `/getInfo` 增加 `entryRole` |

**验收**：`edu_admin` 只能看本部门用户；不能授 admin、不能改 admin 用户；操作写审计。

### 3.3 审计

- 现有 `@Log` 已覆盖用户/角色/部门/菜单的主要变更；
- 新增接口必须补 `@Log(title, businessType)`；
- 审计查询只给 `monitor:operlog:*`，受限管理员可查不可删；
- 在 `docs/` 写明“哪些业务操作必须审计”，供 2～6 组执行。

### 3.4 审查 2～5 组越权案例

每个接口检查：身份只从 `SecurityUtils` 取；对象归属校验；越权返回 403 且不泄露对象存在性；列表走 `@DataScope`；下载/导出/实时接口同样校验。

---

## 4. 尚未完成 / 已知风险

| 项 | 状态 | 说明 |
|---|---|---|
| `/education/context/options`、`/check` | ❌ 未实现 | 前端 `frontend/shell/context.js` 已按草案适配，接口缺失时显示“服务尚未就绪”；需求见 `docs/group2-context-api-requirements.md` |
| 教学业务九领域 | ❌ 未开始 | `ruoyi-education` 只有 README，无 `pom.xml`、无 `src` |
| 受限管理员与提权防护 | ❌ 未实现 | 见 3.2，是当前最高优先级安全问题 |
| 旧令牌立即失效 | ❌ 未实现 | 停用/撤权后最多还可访问 30 分钟 |
| 后端测试 | ❌ 无 | 无 `spring-boot-starter-test`、无 `src/test`；建议先用 HTTP 用例脚本 |
| 授权菜单 | ❌ 静态 | 三端菜单仍是前端配置 + 权限过滤，未接 `getRouters` |
| Git 仓库 | ❌ 无 | 当前目录没有 `.git`，PR 流程无法执行 |
| Vue 测试 | ⚠️ 仅前端 Node 测试 | `tests/*.test.cjs` 51 项，不覆盖真实若依接口 |

---

## 5. 验证用例（建议先跑这 9 条）

1. `edu_admin` 登录管理员入口成功；
2. 用户列表只返回授权部门；
3. 给账号分配 `admin` 角色被拒（403）；
4. 修改/停用 `admin` 用户被拒（403）；
5. 停用测试账号后，用旧 token 调 `/getInfo` 返回 401；
6. 修改角色菜单后，在线用户刷新页面权限变化；
7. 外班对象越权返回 403，且不泄露对象信息；
8. 审计页能查到停用/授权/改密记录；
9. 人为触发 SQL 异常，前端看到通用提示，日志里有详情。

---

## 6. 不要做的事

- 不要把课程/班级等业务规则写进 `common` 或 `framework`；
- 不要只靠前端隐藏按钮，服务端必须兜底；
- 不要给受限管理员开放 `system:menu:*`、`system:role:add/edit/remove`；
- 不要提交 `backend/.env`、`target/`、`logs/`、`frontend/vue/node_modules`；
- 不要修改 `docs/` 之外的原始需求文档（`智慧教育课程需求分析.docx`、`功能架构优化方案.md`）。

---

## 7. 相关文档

| 文档 | 用途 |
|---|---|
| `README.md` | 项目总览 |
| `AGENTS.md` / `CONTRIBUTING.md` | 开发边界与协作规范 |
| `docs/architecture.md` | 领域与数据约束 |
| `docs/api-contracts.md` | 接口契约（含已对接系统接口） |
| `docs/group2-context-api-requirements.md` | 课程/教学班上下文接口需求 |
| `docs/frontend-1b-delivery.md` | 前端交付与验收记录 |
| `frontend/vue/README.md` | Vue 工程运行/构建/托管 |
| `sql/README.md` | 数据库脚本执行顺序 |
