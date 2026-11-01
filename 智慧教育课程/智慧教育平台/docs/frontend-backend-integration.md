# 前后端联调说明（2B 页面）

本文件记录 `frontend/` 三端静态页面与 `backend/` 教学接口的实际对接方式、字段约定和已验证范围。
接口清单以本文件为准；[接口协作约定](api-contracts.md) 中的领域边界与权限原则仍然有效。

## 1. 范围

已接通的是第 2 组 2B 负责的页面：

| 端 | 页面 | 调用方文件 |
|---|---|---|
| 管理员 | 学期管理、班级管理（含成员、任课教师授权）、教师管理、学生管理、组织架构管理 | `frontend/pages-admin.js` |
| 教师 | 我的课程、课程详情（新建 / 编辑 / 发布 / 撤回 / 绑定教学班） | `frontend/pages-teacher.js` |
| 学生 | 在修课程、课程详情（只读） | `frontend/pages-student.js` |

其余菜单仍由 `frontend/edu-boot.js` 的 `OWNER` 表显示归属，不返回伪造数据。
演示数据层 `frontend/edu-mock.js` 保留，只在地址栏带 `?mock=1` 时接管请求，正式联调默认走真实后端。

教师端与学生端的外壳已改为学习通风格（**左侧导航** + 右侧内容区 + 课程卡片墙 + 课程空间页签），与管理员端侧栏保持同一套视觉：

- `teacher.html` / `student.html`：左侧边栏（首页 / 我的课程 / 消息 / 我的）、侧栏底部用户与退出登录，右侧顶部条放面包屑与课程搜索；窄屏（≤900px）时侧栏收成 72px 图标轨道；
- `xt.css`：外壳与课程组件样式（侧栏、顶栏面包屑、卡片、封面、课程空间、页签、表格）；
- `xt-shell.js`：导航选中态、面包屑同步、详情页返回列表（`history.pushState` + 重新挂载）、搜索接线；
- `pages-teacher.js` / `pages-student.js`：课程中心与课程空间页面，接口调用不变。

> 布局改动只动外壳，接口调用与页面渲染逻辑未变；「我的课程」与「首页」当前指向同一个课程中心视图。
>
> **外壳约束**：`#pageTitle` 必须放在 `.content` 外面。业务页面渲染时会整体替换 `.content`，标题放在里面第一次渲染就被清掉，之后切换左侧导航再次挂载时取不到标题，会落到「这一页还没有实现」的兜底页。

管理员端仍是原来的控制台外壳（`admin.html` + 内联样式 + `edu.css`）。「消息」「我的」两个入口还没有业务，点开只给归属说明，不放假数据。

## 2. 接口清单

统一前缀 `/education`，沿用若依 `AjaxResult` / `TableDataInfo` 包装：

- 列表：`{ code, msg, rows, total }`
- 单体：`{ code, msg, data }`
- 失败：`code=500` + `msg`；无权限：`code=403`；登录失效：HTTP 200 + `code=401`（若依 `AuthenticationEntryPoint` 的行为）

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| GET | `/education/term/list` | 管理员 | 学期分页，含 `current` 标记 |
| POST | `/education/term` | 管理员 | 新建学期 |
| PUT | `/education/term/{id}` | 管理员 | 修改学期 |
| PUT | `/education/term/{id}/current` | 管理员 | 设为当前学期（先清空再置位） |
| GET | `/education/teaching-class/list` | 管理员 | 教学班分页，含学期名、课程名、任课教师、学生数 |
| GET | `/education/teaching-class/mine` | 教师 | 当前教师被授权的教学班 |
| GET | `/education/teaching-class/{id}` | 管理员 / 本班教师 | 教学班详情 |
| POST | `/education/teaching-class` | 管理员 | 新建教学班 |
| PUT | `/education/teaching-class/{id}` | 管理员 | 修改教学班 |
| GET | `/education/teaching-class/{id}/members` | 管理员 / 本班教师 | 成员（含已退出记录） |
| POST | `/education/teaching-class/{id}/members` | 管理员 | 加入学生，body `studentId` |
| DELETE | `/education/teaching-class/{id}/members/{studentId}` | 管理员 | 移出学生（标记 `left_at`，不删除） |
| GET | `/education/teaching-class/{id}/teachers` | 管理员 | 任课教师授权 |
| POST | `/education/teaching-class/{id}/teachers` | 管理员 | 授权，body `userId/teacherRole/canPublish/canGrade/canExport/validTo` |
| DELETE | `/education/teaching-class/{id}/teachers/{userId}` | 管理员 | 撤权（标记 `valid_to`） |
| GET | `/education/teacher/list` | 管理员 | 教师分页，支持 `keyword`、`deptId`（含子部门） |
| GET | `/education/teacher/{userId}/classes` | 管理员 | 教师任教关系 |
| GET | `/education/student/list` | 管理员 | 学生分页，含在班班级名与班级数 |
| GET | `/education/student/{userId}/classes` | 管理员 | 学生在班记录 |
| GET | `/education/organization/tree` | 管理员 | 部门扁平列表（前端自行组树） |
| GET | `/education/official-course/list` | 登录用户 | 正式课程下拉 |
| GET | `/education/course/list` | 按角色裁剪 | 管理员全部；教师本人创建；学生已发布且本班已绑定 |
| GET | `/education/course/{id}` | 同上 + 学生须在班 | 课程详情，含 `units`、`knowledgePoints`、`classes` |
| POST | `/education/course` | 教师 / 管理员 | 新建课程（DRAFT） |
| PUT | `/education/course/{id}` | 课程负责人 / 管理员 | 修改课程基本信息 |
| PUT | `/education/course/{id}/status` | 课程负责人 / 管理员 | 发布 / 撤回（`DRAFT` / `ACTIVE` / `ARCHIVED`） |
| POST | `/education/course/{id}/classes` | 课程负责人 / 管理员 | 绑定教学班，body `classId` |
| DELETE | `/education/course/{id}/classes/{bindingId}` | 课程负责人 / 管理员 | 解绑（`bindingId` 是绑定记录 id，不是班级 id） |

## 3. 数据范围与权限

- 身份只从服务端登录态取（`SecurityUtils.getLoginUser()`），不接受前端传入的 `studentId` 冒充。
- 管理员 = `sys_user.user_id = 1` 或拥有 `admin` 角色；教师、学生按 `sys_role.role_key` 判断。
- 教师只能看自己创建（`owner_user_id`）的课程、自己被授权的教学班。
- 学生只能看：课程 `status=ACTIVE` **且** 自己所在教学班有未结束的绑定。
- 越权统一返回 `code=403`，由 `EduApiExceptionAdvice` 转换，前端显示「无权限」状态而不是重试。
- 组织归属（`organization_id`）优先取被引用对象（学期、正式课程）的值，建课时回落到当前用户部门树的顶层部门。

## 4. 字段映射要点

列表行是跨表拼接的展示模型，SQL 别名直接使用前端字段名，避免为每个页面造一次性 VO：

| 页面字段 | 来源 |
|---|---|
| `semesterName` / `termName` | `edu_term.name` |
| `courseName` | `edu_official_course.name` |
| `teacherName` | 有效任课教师（`valid_to IS NULL`，主讲优先）的 `nick_name` |
| `studentCount` | 有效在班人数 / 课程所有绑定班级去重人数 |
| `classCount` | 有效绑定数 |
| `className` | 绑定教学班名拼接（学生端为所在班） |
| `current` | `edu_term.is_current`（TINYINT(1) → JSON boolean） |
| `canPublish` / `canGrade` / `canExport` | TINYINT(1) → JSON boolean，前端两种写法都兼容 |
| `updatedAt` / `joinedAt` / `validFrom` 等 | `DATE_FORMAT(..., '%Y-%m-%d')`，前端 `slice(0,10)` 可直接用 |

## 5. 数据库变更

`edu_term` 增加当前学期标记（脚本见 [migration-20261028-term-current.sql](../sql/migration-20261028-term-current.sql)）：

```sql
is_current TINYINT(1) NOT NULL DEFAULT 0,
current_flag TINYINT GENERATED ALWAYS AS (IF(is_current = 1, 1, NULL)) STORED,
UNIQUE KEY uq_edu_term_single_current (current_flag)
```

生成列 + 唯一索引保证同一时刻只有一个当前学期，`0` 在生成列里为 `NULL`，不参与唯一约束。

## 6. 本地运行

前提：MySQL 8 的 `zhijiao_m0`、`localhost:6379` 的 Redis、JDK 17+。

```powershell
cd backend
mvn -B -pl ruoyi-admin -am package
.\run-local.ps1
```

Windows 下 `logback.xml` 默认写 `/home/ruoyi/logs`，可用 `-Druoyi.log.path=D:/ruoyi/logs` 覆盖。

打开 <http://127.0.0.1:8080/login.html>，按 [演示账号](demo-login-accounts.txt) 登录后进入三端页面。
带 `?mock=1` 可切回演示数据，用于对比页面效果。

### 静态页面怎么进 classpath

`ruoyi-admin/pom.xml` 把 `../../frontend` 整个目录复制到 `classpath:/static`：

```xml
<resource>
    <directory>${project.basedir}/../../frontend</directory>
    <targetPath>static</targetPath>
    <includes><include>**/*</include></includes>
</resource>
```

早先这里只列了 7 个文件（三个 html、login.js、role-home.js、logo.png），新增的 `edu-api.js`、`edu-boot.js`、`edu-ui.js`、`edu-mock.js`、`edu.css`、`pages-*.js` 都不会进构建产物。**症状**：页面框架正常，内容区一直停在「内容区（待实现）」。

自查方法：浏览器直接访问 `http://127.0.0.1:8080/pages-admin.js`。

- 返回 40KB 左右 JS → 正常；
- 返回 `{"msg":"No static resource pages-admin.js.","code":500}` → 静态资源没打进构建产物。

修改 pom 后需要重新构建（`mvn package` 或在 IDEA 里 Reload Maven + Rebuild），只重启旧进程不会生效。

### 业务页样式来自 edu.css

`admin.html`、`teacher.html`、`student.html` 的 `<head>` 必须有一行：

```html
<link rel="stylesheet" href="/edu.css">
```

外壳（顶栏、侧边菜单、页签）用页面内联 `<style>`，业务内容区（`.edu-table`、`.edu-btn`、`.edu-page` 等）的样式全在 `edu.css`。漏掉这一行时页面能取到数据，但表格没有边框、按钮是浏览器默认样式、字体变成宋体。

## 7. 已验证范围（2026-10-28，本机 zhijiao_m0）

用真实 HTTP 请求分别以 admin / teacher / student 登录（验证码从 Redis 取），逐项调用上表接口，结果：

- 管理员 10 项读取全部 200，学期 `current=true`，班级行含学期名/课程名/教师/人数；
- 教师课程列表、`mine`、建课、改名、发布、绑定、详情、解绑全部成功；
- 学生登录后能看到刚发布并绑定的课程与其详情；
- 越权（教师查教师列表、学生查学期/班级成员）返回 `code=403`；
- 未登录访问返回 `code=401`，前端据此跳登录页；
- 15 个静态资源（html/js/css/png）全部由后端同源返回 200。

验证后已清理测试课程与绑定记录，演示库回到原有数据。

## 8. 未实现 / 待评审

- 课程章节与知识点的**编辑**：详情只读展示 `units` / `knowledgePoints`，写入归第 3 组。
- 发布课程时未校验“至少绑定一个教学班”，也未校验课程内容非空；这是产品规则，需评审后补。
- 绑定教学班的 `publishedAt` 以服务端时间为准，前端传值暂不落库。
- 成员备注（`note`）没有对应列，暂不保存。
- 教学班状态为 `ARCHIVED` 时仍可编辑，归档后的只读约束未实现。
- 实时课堂、资源、作业、成绩等接口不在本次范围。
