# 6-02 教师公告与学生通知全后端联调记录

日期：2026-11-28
状态：**完整后端真实环境验证完成；发布与审阅状态以对应 PR 记录为准。**

## 1. 基线与运行版本

- 远端 main：`b794a7fc204df1267d392a3d80742003fb7bd06f`
- main TREE：`a71a6a07b2c9ab3de0eff0160f304b208ef1afa7`
- 本地工作区分支：`proto/6-02-full-backend-auth-validation`
- 本地起点：`f83afd5af8224c1a7ecec40d0fd05dbecebe9334`（该提交的 TREE 与 main 相同；本轮 `git fetch` 网络不可用，未覆盖任何线上内容）
- 运行 jar：由上述 TREE 构建的 `ruoyi-admin.jar`
- Java：17.0.14；MySQL：8.0.43；Redis：5.0.14

## 2. 完整装配与隔离环境

启动的是完整 `ruoyi-admin`：

- Druid 数据源、MyBatis Mapper/XML、Spring Security 过滤器、真实 `/login`、Captcha 校验、Redis Token、Controller、Service、事务代理。
- 一次性 MySQL：`127.0.0.1:56353/smart_edu_it`，独立数据目录；未连接或修改既有 3306 库。
- 一次性 Redis：`127.0.0.1:6380` DB 0，独立目录；未清空 6379 共享实例。
- 应用端口：`127.0.0.1:18080`。
- 登录验证码保持仓库默认启用；脚本按真实 `/captchaImage` 的 uuid 从隔离 Redis 读取答案后调用 `/login`，未关闭验证码，也未使用 MockMvc、伪造 SecurityContext 或接口响应夹具。

数据库由 `sql/install-fresh.sql` 初始化，再加载本目录的 `scripts/full-backend-auth-validation/fixture.sql`。夹具只用于一次性库：

- 为教师角色补充 5 个公告按钮权限菜单（`education:announcement:add/edit/publish/archive/list`）。
- 新增 `assistant`（无公告权限）、受限教师、其他班教师、团队教师、非任课教师、联调学生甲/乙等合成账号。
- 新增专用教学班 C 及其绑定，避免演示公告/通知影响精确计数。
- 未宣称生产菜单或角色授权已完成；正式环境仍需第一组/管理员配置。

## 3. 复现步骤

1. 在一次性 MySQL 中新建空库，执行 `sql/install-fresh.sql` 和 `scripts/full-backend-auth-validation/fixture.sql`。
2. 启动独立 Redis，例如：`redis-server --port 6380 --bind 127.0.0.1 --dir <隔离目录> --save "" --appendonly no`。
3. 启动完整后端，关键环境变量：

```bash
RUOYI_DB_URL='jdbc:mysql://127.0.0.1:56353/smart_edu_it?...'
RUOYI_DB_USERNAME=root
RUOYI_DB_PASSWORD=
RUOYI_REDIS_HOST=127.0.0.1
RUOYI_REDIS_PORT=6380
RUOYI_REDIS_DATABASE=0
RUOYI_TOKEN_SECRET=<一次性随机值>
java -jar ruoyi-admin/target/ruoyi-admin.jar --server.port=18080
```

4. 用演示账号的实际联调密码通过 `TEST_PASSWORD` 环境变量运行：

```bash
TEST_PASSWORD='<一次性库演示密码>' \
MYSQL_BIN='<mysql>' REDIS_CLI='<redis-cli>' \
REPORT_PATH=full-auth-report.json \
python scripts/full-backend-auth-validation/verify.py
```

脚本不写入密码、Token、Cookie 或完整响应体；Token 仅保存在进程内存。

负例自检（每个用例预期退出码 1，不连接后端）：

```bash
for case in missing-publish already-read db-count audit-history request-exception; do
  REPORT_PATH=self-test-$case.json \
    python scripts/full-backend-auth-validation/verify.py --self-test "$case"
done
```

真实链路退出码 0 才表示全部断言通过；任一检查失败或脚本异常时写出报告并返回非零。

## 4. 验证结果

执行结果：**104 项检查通过，0 项失败；脚本退出码 0。**

### 4.0 负例自检

修订后的脚本提供受控负例自检，证明断言失败会被检测并以非零退出码结束：

| 自检用例 | 结果 |
|---|---|
| 缺少发布权限 | 检出，退出码 1 |
| `alreadyRead` 返回错误 | 检出，退出码 1 |
| 数据库数量错误 | 检出，退出码 1 |
| 审计只有历史记录 | 检出，退出码 1 |
| 请求异常 | 检出，退出码 1 |

自检不连接后端、不写入真实数据；真实链路随后独立重跑，退出码 0。观察项与断言分开记录，不参与通过数量。

### 4.1 HTTP 状态与业务 code

脚本记录全部 63 个 HTTP 请求（含 12 次 `/captchaImage`、12 次 `/login`、39 次业务请求），HTTP 状态均为 `200`：

- 业务 code 200：42 次（含验证码、登录及成功业务响应）
- 业务 code 401：2 次（未登录教师/学生接口）
- 业务 code 403：14 次（缺按钮权限、非任课、受限、团队 fallback、跨班、角色不符）
- 业务 code 404：4 次（跨班/归档公告、跨用户通知、归档通知）
- 业务 code 409：1 次（归档后重复发布）

本项目既有契约是 HTTP 200 + JSON 业务 `code`；本轮只记录该事实，不把 HTTP 200 写成鉴权成功，也不改变公共异常映射。

### 4.2 成功链路

- 验证码实际启用；12 个账号均通过真实 `/captchaImage` + Redis 答案 + `/login` 登录。
- 教师 `teacher` 真实登录，`getInfo` 返回 `teacher` 角色和 `education:announcement:publish`。
- 教师创建草稿、编辑标题、发布：状态依次 `DRAFT`、`DRAFT`、`PUBLISHED`，`published_by_user_id=930000000001`。
- 教学班 C 发布后生成 2 条通知，接收人为 `930000000301`、`930000000302`；重复发布返回 200，通知数仍为 2。
- 学生通知列表 total=2，未读数=2，标题集合和详情标题均与编辑后的公告一致。
- 单条已读：业务 code 200，`alreadyRead=false`；返回 `readAt` 与数据库 `read_at` 按 UTC 毫秒归一化后一致。重复已读：`alreadyRead=true`，返回 `readAt` 仍与数据库一致。
- 全部已读：`markedCount=1`，未读数 0；数据库未读数为 0。
- 归档公告一后：学生公告列表 total=1，通知列表 total=1；归档公告学生详情 404，归档通知详情 404；教师仍可查看归档公告；再次发布返回 409。
- 数据库最终关键行：公告一 `ARCHIVED`、公告二 `PUBLISHED`、拒绝场景草稿 `DRAFT`；通知 6 条（含演示库 2 条），测试通知的 `read_at` 与预期一致。
- 最终公告数为基线 +3、通知数为基线 +4；拒绝请求未产生额外公告或通知写入。

### 4.3 拒绝与幂等

- 未登录访问：业务 code 401。
- 无公告按钮权限的 `assistant`、学生调用教师接口：403。
- 非任课教师、`can_publish=0` 受限教师、团队 fallback：创建/发布均 403；拒绝后草稿仍为 `DRAFT`，通知数 0。
- 跨班教师创建/列表/发布 403，按公告 ID 读取 404；跨班学生通知 403。
- 学生甲标记学生乙通知：404，学生乙 `read_at` 保持 NULL。
- 教师、管理员访问学生专属通知接口：403。
- 重复发布不重复 fan-out；重复已读保持 `read_at`；归档后重复发布 409。
- 未发现公告状态、通知数量或已读时间被拒绝请求破坏。

### 4.4 审计

请求前记录 `sys_oper_log` 最大 ID 作为基线；`@Log` 异步落库最多等待 20 秒，只匹配基线之后的新增记录。按 `oper_name + request_method + oper_url + title + business_type + status` 做多重集匹配，一条日志只能满足一个预期。

- 本轮期望 21 条，实际新增 21 条，缺失 0、多余 0；成功 status=0 共 11 条，失败 status=1 共 10 条。
- 成功含创建、编辑、发布、重复发布、归档、单条已读、重复已读、全部已读。
- 失败含非任课/受限/团队/跨班教师拒绝、归档后重复发布 409、跨用户已读 404。
- 权限拒绝在 Spring Security 方法调用前发生，因此不产生 `@Log` 记录；这与现有切面边界一致，未把任意一条日志当作全部操作已审计。
- `sys_logininfor` 有 12 条登录记录；本轮未宣称审计已接入外部系统。

## 5. 未验证与边界

- 重复投递：仓库没有面向外部的上游事件投递 HTTP 端点，`UpstreamNoticeEventService` 是内部端口；本轮只验证重复发布和重复已读，未通过 HTTP 触发重复投递。
- 未验证 MySQL 8.0.46、真实外部 Redis、真实浏览器页面、真实第三方上游、生产菜单/角色授权、审计外部落库。
- 固定演示账号、精简菜单、专用教学班 C 和一次性库均为测试夹具，不代表生产权限完成。
- 未修改生产实现、SQL 初始化脚本、配置、依赖或前端；本轮验证脚本和夹具仅在 `scripts/full-backend-auth-validation/` 与本文件。

## 6. 清理

一次性 MySQL 库、Redis DB 0 和应用进程仅用于本轮；验证结束后停止并删除本轮库数据。数据目录如因本机清理策略保留，应在确认无其他用途后由负责人删除。
