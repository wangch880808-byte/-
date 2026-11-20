> 当前第一组交付以 [group1-delivery.md](group1-delivery.md) 为准。本文保留早期记录；受限管理员已取消，旧待办不代表当前实现。

# 第 1 组后端（1A）交付说明 — 版本二

- 任务：1-01 身份权限统一拒绝；1-02 受限教务管理员与审计（受限管理员角色已按 1A 团队决定移除，保留提权防护与审计）
- 基线：`智慧教育平台-后端交接-20261028-v2`（ruoyi-education 等 v2 前端保持不变）
- 数据库：本机 `zhijiao_m0`（复用，已含全部演示数据）

## 一、代码改动清单

### 新增
1. `ruoyi-common/.../exception/ForbiddenException.java`：统一 403 拒绝异常（code=403）。
2. `ruoyi-common/.../utils/ScopeAssert.java`：`require` / `requireOwner`，统一"无权限/非本人"拒绝。

### 修改
3. `ruoyi-common/.../utils/SecurityUtils.java`：新增 `getRoles()`、`hasAnyRole(String...)`。
4. `ruoyi-framework/.../web/exception/GlobalExceptionHandler.java`：
   - ForbiddenException → 403；
   - UserException（验证码错误/失效、密码错误等业务异常）→ 返回原始业务提示；
   - 其余 RuntimeException/Exception → 前端只见"系统繁忙，请稍后再试"，堆栈详情只进日志。
5. `ruoyi-framework/.../web/service/TokenService.java`：新增 `deleteByUserId`，角色/状态变更后立即清掉该用户旧令牌。
6. `ruoyi-system/.../service/impl/SysUserServiceImpl.java`：
   - `checkSelfMutation`：非超管不得改自己的状态/角色/部门；
   - `checkGrantAdminRole`：非超管不得授予 admin 角色；
   - 覆盖新增/编辑/授权/改状态/重置密码等全部入口。
7. `ruoyi-admin/.../controller/system/SysUserController.java`：编辑/删除/改密/改状态/授权后按条件踢旧令牌。
8. `ruoyi-admin/.../controller/system/SysRoleController.java`：
   - 禁止非超管新增/编辑/停用 admin 角色；
   - 新增/编辑角色时菜单必须是本人菜单子集（防越权拿菜单）；
   - 角色变更后在线权限刷新；解除/批量授权用户后踢旧令牌。
9. `ruoyi-system/.../service/impl/SysRoleServiceImpl.java`：`checkRoleDataScope` 对超管/空入参提前返回，修复 null 入参 NPE。

> 登录入口保持若依三端模型 `admin / teacher / student`，`SysLoginService` 不做四端扩展。

### 文档
- `docs/demo-login-accounts.txt`：三端联调账号说明（含 Vue 入口）。
- `sql/README.md`：建表/种子脚本执行顺序。

## 二、角色说明（移除 edu_admin 后）

当前 `sys_role` 中与登录/业务相关的角色：

| 角色 key | 角色名 | 来源 | 登录端 |
|---|---|---|---|
| `admin` | 超级管理员 | 若依内置 | 管理员端 |
| `teacher` | 教师 | 登录种子脚本 | 教师端 |
| `student` | 学生 | 登录种子脚本 | 学生端 |
| `common` | 普通角色 | 若依内置 | 无（通用兜底，可忽略） |

变更记录：
- **已移除受限教务管理员 `edu_admin`**：其角色、账号（`edu_admin`）、授权及专用种子脚本 `demo-v3-m0-edu-admin-seed.sql` 均已删除（2026-11-01，按 1A 团队决定"教务管理员即管理员，不单设角色"）。
- **角色名标准化**：`演示教师 → 教师`、`演示学生 → 学生`。
- 保留的服务端安全加固（提权防护、踢令牌、审计）不依赖 edu_admin，继续生效。

## 三、端到端验证结果（已通过）

| 验证项 | 结果 |
|---|---|
| 后端 8080、Redis 6379、前端托管 | 正常 |
| `http://127.0.0.1:8080/vue/` | 200，正常加载 |
| `http://127.0.0.1:8080/login.html` | 200，正常加载 |
| 验证码开关 | 已开启，错误/失效/密码错误均返回对应业务提示 |
| admin / teacher / student 登录 | code=200 |
| 错误登录入口 | "此账号没有所选登录入口的权限" |
| 非超管授予 admin 角色 | 被拒："没有权限访问角色数据" |
| 非超管编辑 admin 用户 | 被拒："不允许操作超级管理员用户" |
| 非超管创建 admin 角色 | 403："没有权限，请联系管理员授权" |
| 停用账号后旧 token 调 /getInfo | 立即 401（旧令牌已失效） |
| 审计日志查询 | code=200，可查 |

## 四、运行方式

1. 启动 MySQL（服务 MySQL80，库 `zhijiao_m0`）。
2. 启动 Redis：`C:\Users\wpweu\tools\redis\redis-server.exe`（手动进程，重启电脑后需重新启动）。
3. 在 `backend/` 目录执行 `.\run-local.ps1`（.env 已配置好，JDK17 已固定）。
4. 浏览器访问：
   - Vue 版：http://127.0.0.1:8080/vue/
   - 静态登录页：http://127.0.0.1:8080/login.html

## 五、备注

- 登录页维持若依三端（管理员/教师/学生），无第四入口；前端无需为此改动。
- 提权防护（非超管不得授/改/建 `admin` 角色、不得改自己）、踢令牌、500 脱敏、业务提示正常返回，为本次 1A 交付的核心安全成果。
- 审计日志基于若依操作日志能力，关键启停/授权/改密操作可查。
