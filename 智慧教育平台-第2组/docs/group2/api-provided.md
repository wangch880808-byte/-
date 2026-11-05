# 第 2 组对外接口契约（v1.0-draft）

> 适用：第 3、4、5、6 组消费「学期 / 教学班 / 成员 / 课程 / 授课授权」时使用。
> 负责人：2A（后端契约）、2B（页面消费）。审核：第 1 组（身份与越权）、第 6 组（统计口径影响方）。
> 状态：`已实现` = 代码存在且本机联调过；`新增` = 本轮补齐，已编译通过但**尚未在运行库联调**；`待定` = 需要评审。

## 0. 一句话结论

跨组不要 join 我们的表，也不要自己复制一份班级名单。你们要的三件事都有唯一入口：

| 你要判断什么 | 调什么 |
|---|---|
| 这个账号在这个班能不能发/批/导 | `GET /education/scope/classes/{classId}/me` 或 Java `EduCourseAccessService.membership()` |
| 这个班现在有哪些有效学生 | `GET /education/scope/classes/{classId}/members` 或 `activeMembers()` |
| 这个班现在绑定哪门课（含课程空间 ID） | `GET /education/scope/classes/{classId}/binding` 或 `activeBinding()` |

## 1. 通用约定

### 1.1 路径与包装

- 统一前缀 `/education`，权限标识按 `education:<domain>:<operation>` 命名（待第 1 组注册，见 §9）。
- 列表沿用若依 `TableDataInfo`：`{ code, msg, rows, total }`。
- 单体沿用 `AjaxResult`：`{ code, msg, data }`。
- 失败：`{ code, msg }`，HTTP 状态恒为 200（若依约定），前端按 `code` 分支。

### 1.2 分页

`pageNum`（从 1 开始）、`pageSize`。服务端 `startPage()` 读取，不传时返回第一页。
`total` 是满足条件的总数，不是当前页条数。前端不要用 `rows.length` 当总数。

### 1.3 时间与空值

| 场景 | 约定 |
|---|---|
| 日期 | `yyyy-MM-dd` 字符串（服务端 `DATE_FORMAT` 已格式化） |
| 时间点 | `yyyy-MM-dd HH:mm:ss`；业务截止一律以服务端时间为准，客户端倒计时只用于展示 |
| 时区 | 服务端按 `Asia/Shanghai` 输出；接口不做时区换算 |
| 空值 | 单值用 `null`；列表用 `[]`，不返回 `null` 数组 |
| 布尔 | `true/false`；TINYINT(1) 由驱动转布尔，前端同时兼容 `1/0`（旧页面有兼容代码） |

### 1.4 ID 与风险项

教学表主键是 `BIGINT`，当前 JSON 里是 **number**。若后续启用雪花 ID（19 位），JS 会丢精度。

> **D-01（待定）**：是否把所有 ID 统一改为字符串输出（`@JsonSerialize(using = ToStringSerializer.class)`）。
> 在决定前，前端一律用 `String(id)` 参与比较、拼 URL，不要做 `+1` 之类的数值运算。

### 1.5 错误码

| code | 含义 | 触发 | 前端动作 |
|---|---|---|---|
| 200 | 成功 | — | — |
| 401 | 未登录 / 令牌失效 | 若依 `AuthenticationEntryPoint` | 清理令牌回登录页 |
| 403 | 已登录但**对象越权** | `EduForbiddenException` → `EduApiExceptionAdvice` | 显示「无权限」，不重试、不回登录 |
| 500 | 业务校验失败 / 服务异常 | `ServiceException` | 显示 `msg` |
| 其他 | 未定义 | — | 按失败处理 |

典型 403 文案：`只有管理员可以查看教学班`、`你不是该教学班的任课教师`、`只有课程负责人可以修改课程`、`你没有这门课程的访问权限`、`你不是该教学班的成员`。

### 1.6 幂等与重复提交

| 操作 | 当前行为 | 建议 |
|---|---|---|
| 学生入班 | 唯一约束拦截 → 500「该学生已在本班，不能重复加入」 | 前端按钮置 loading；后端可改 409（**D-02**） |
| 教师授权 | 唯一约束拦截 → 500「该教师已在本班任课」 | 同上 |
| 课程绑定班级 | 500「该课程已绑定这个教学班」/「该教学班已绑定其他课程」 | 同上 |
| 退班 / 撤权 / 解绑 | 影响行数为 0 → 500「该学生不在本班」等 | 幂等语义可改为成功（**D-03**） |
| 设为当前学期 | 事务内先清空再置位，可重复调用 | 已幂等 |

## 2. 权限模型（四层，全部在服务端判定）

```
L1 身份： SecurityUtils.getLoginUser()，前端传的 userId/role 一律不信
L2 角色： admin（user_id=1 或 role_key=admin） / teacher / student
L3 对象： 该班有效任课教师（valid_to IS NULL）
          该班有效在班学生（left_at IS NULL）
          课程负责人（owner_user_id = 当前用户）
L4 状态： 教学班 ACTIVE、课程 ACTIVE、绑定 ended_at IS NULL
```

### 2.1 撤权即时生效规则

| 变更 | 判定字段 | 生效时点 | 已验证 |
|---|---|---|---|
| 撤销任课教师 | `edu_class_teacher.valid_to` 置为当前时间 | 下一次请求 | 是 |
| 学生退班 | `edu_class_student.left_at` 置为当前时间 | 下一次请求 | 是 |
| 解绑课程 | `edu_course_class_binding.ended_at` 置为当前时间 | 下一次请求 | 是 |
| 课程撤回 | `edu_course_space.status` → `DRAFT` | 下一次请求 | 是 |
| 账号停用 | `sys_user.status` | **取决于第 1 组令牌缓存策略** | 否（见 §9.4） |

> 我们**不缓存**成员资格和课程归属。任何"为了性能"给这几个判断加缓存的改动，都会破坏 2-01 的验收条件，必须先过评审。

### 2.2 谁能看到什么

| 端点族 | 管理员 | 教师 | 学生 |
|---|---|---|---|
| 学期 CRUD | 全部（当前实现） | 无 | 无 |
| 教学班列表 | 全部 | `/mine` 仅本人授权 | 无 |
| 班级成员明细 | 全部 | 仅本班 | 无 |
| 任课教师授权 | 全部 | 仅本班成员名单（不含授权写） | 无 |
| 教师/学生名册 | 全部 | 无 | 无 |
| 课程列表 | 全部 | 本人创建 | 已发布且本班已绑定 |
| 课程详情 | 全部 | 本人创建 | 已发布且本班已绑定 |

> **注意**：当前「管理员」指若依超管。受限教务管理员（`edu_admin`，1-02）落地后，上面所有"全部"要收敛为"本组织"，见 §9.3。

## 3. 已实现端点

### 3.1 学期

| # | 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|---|
| T1 | GET | `/education/term/list` | 管理员 | 分页，`keyword` 匹配名称/编码 |
| T2 | POST | `/education/term` | 管理员 | 新建 |
| T3 | PUT | `/education/term/{id}` | 管理员 | 修改 |
| T4 | PUT | `/education/term/{id}/current` | 管理员 | 设为当前学期 |

**T1 响应行**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 学期 ID |
| `organizationId` | number | 所属组织 |
| `code` | string | 学期编码，同组织唯一 |
| `name` | string | 学期名称 |
| `startsOn` / `endsOn` | string | `yyyy-MM-dd` |
| `current` | boolean | 是否当前学期，同一时刻最多一个 |

**T2 请求体**：`{ "name": "2026-2027 学年第一学期", "code": "2026-2027-1", "startsOn": "2026-09-01", "endsOn": "2027-01-15" }`，返回 `data` = 新 ID。

**错误**：编码重复 → 500「同一学校内学期编码不能重复」；结束早于开始 → 500「结束日期不能早于开始日期」；学期不存在 → 500「学期不存在」。

### 3.2 教学班

| # | 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|---|
| C1 | GET | `/education/teaching-class/list` | 管理员 | 分页，`keyword`/`termId`/`status` |
| C2 | GET | `/education/teaching-class/mine` | 教师（管理员返回全部 ACTIVE） | 当前教师有效授权班级 |
| C3 | GET | `/education/teaching-class/{id}` | 管理员 / 本班有效教师 | 详情 |
| C4 | POST | `/education/teaching-class` | 管理员 | 新建 |
| C5 | PUT | `/education/teaching-class/{id}` | 管理员 | 修改 |

**C1/C2/C3 响应行**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 教学班 ID（跨组引用键，记作 `classId`） |
| `code` / `name` | string | 编码 / 名称 |
| `organizationId` | number | 所属组织 |
| `termId` / `semesterId` | number | 学期 ID（两个别名同值，历史原因） |
| `semesterName` / `termName` | string | 学期名（两个别名同值） |
| `officialCourseId` / `courseId` | number | 正式课程 ID（两个别名同值） |
| `courseName` | string | 正式课程名 |
| `classKind` | string | `FORMAL` / `TRIAL` |
| `status` | string | `DRAFT` / `ACTIVE` / `ARCHIVED` |
| `classStatus` | string | 与 `status` 同值；学生视角里 `status` 表示成员状态，故另给此别名 |
| `teacherName` | string | 有效任课教师（主讲优先） |
| `studentCount` | number | 有效在班人数 |
| `updatedAt` | string | `yyyy-MM-dd` |

**C4 请求体**：`{ "name": "...", "code": "...", "termId": 1, "officialCourseId": 2, "classKind": "FORMAL", "status": "DRAFT" }`，返回 `data` = 新 ID。
`status` 默认 `DRAFT`，`classKind` 默认 `FORMAL`。组织 ID 由学期自动推导，前端不传。

**错误**：同学期编码重复 → 500「同一学期内班级编码不能重复」；学期不存在 → 500「所属学期不存在」。

### 3.3 班级成员

| # | 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|---|
| M1 | GET | `/education/teaching-class/{id}/members` | 管理员 / 本班教师 | **含已退出**的完整记录 |
| M2 | POST | `/education/teaching-class/{id}/members` | 管理员 | `{ "studentId": 123 }` |
| M3 | DELETE | `/education/teaching-class/{id}/members/{studentId}?reason=` | 管理员 | 写 `left_at`，不删行 |

**M1 行**：`memberId`、`classId`、`studentId`、`userId`（与 studentId 同值）、`studentNo`、`userName`、`studentName`、`nickName`、`status`（`ACTIVE`/`LEFT`）、`joinedAt`、`leftAt`。

> 这是**管理视图**，含历史。跨组做任务目标名单请用 §4 的 `/scope/classes/{id}/members`，它只返回有效成员。

**错误**：重复入班 → 500「该学生已在本班，不能重复加入」；不在班 → 500「该学生不在本班」。

### 3.4 任课教师授权

| # | 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|---|
| R1 | GET | `/education/teaching-class/{id}/teachers` | 管理员 | 含历史授权 |
| R2 | POST | `/education/teaching-class/{id}/teachers` | 管理员 | 授权 |
| R3 | DELETE | `/education/teaching-class/{id}/teachers/{userId}` | 管理员 | 写 `valid_to` |

**R2 请求体**

```json
{
  "userId": 102,
  "teacherRole": "LEAD",
  "canPublish": true,
  "canGrade": true,
  "canExport": false,
  "validTo": "2027-01-15"
}
```

`teacherRole` ∈ `LEAD`（主讲）/`ASSIST`（助教）/`GUEST`（旁听）；`validTo` 省略 = 长期。
权限位默认：不传按 `false` 处理，页面应显式传。

> **边界**：`canPublish` / `canGrade` / `canExport` 目前只在**授权记录**里存着，尚未在资源、作业、导出接口里被强制校验。第 3、5 组接入时必须调 `membership()` 判断，而不是只看"是不是本班教师"。

### 3.5 名册与组织

| # | 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|---|
| D1 | GET | `/education/teacher/list` | 管理员 | `keyword`、`deptId`（含子部门） |
| D2 | GET | `/education/teacher/{userId}/classes` | 管理员 | 任教关系（含已失效） |
| D3 | GET | `/education/student/list` | 管理员 | `keyword` |
| D4 | GET | `/education/student/{userId}/classes` | 管理员 | 在班记录（含已退出） |
| D5 | GET | `/education/organization/tree` | 管理员 | 部门**扁平**列表，前端组树 |
| D6 | GET | `/education/official-course/list` | 登录用户 | 正式课程下拉 |

**D1 行**：`userId`、`userName`、`nickName`、`deptName`、`phone`、`phonenumber`、`classCount`（有效任教班级数）。
**D3 行**：`userId`、`userName`、`nickName`、`deptName`、`className`（在班班级名拼接）、`classCount`。
**D5 行**：`id`、`name`、`parentId`、`teacherCount`、`studentCount`。

> **越界提示**：D1/D3/D5 直接 join 了 `sys_user`、`sys_role`、`sys_dept`。若第 1 组认为教学模块不应读系统表，需要改为调用 1 组的名册接口（见依赖清单 R-1.5）。**这是当前最需要裁决的一条。**

### 3.6 课程空间

| # | 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|---|
| K1 | GET | `/education/course/list` | 按角色裁剪 | `keyword` |
| K2 | GET | `/education/course/{id}` | 管理员 / 负责人 / 本班学生（课程 ACTIVE） | 详情含 `units`、`knowledgePoints`、`classes` |
| K3 | POST | `/education/course` | 教师 / 管理员 | 新建，状态固定 `DRAFT` |
| K4 | PUT | `/education/course/{id}` | 负责人 / 管理员 | 改基本信息 |
| K5 | PUT | `/education/course/{id}/status` | 负责人 / 管理员 | `{ "status": "ACTIVE" }` |
| K6 | POST | `/education/course/{id}/classes` | 负责人 / 管理员 | `{ "classId": 9 }` |
| K7 | DELETE | `/education/course/{id}/classes/{bindingId}` | 负责人 / 管理员 | **参数是绑定 ID，不是班级 ID** |

**K1 行**：`id`、`name`、`title`、`status`、`summary`、`learningObjectives`、`officialCourseId`、`ownerUserId`、`officialCourseName`、`teacherName`、`updatedAt`、`className`、`semesterName`、`classCount`、`studentCount`。

**K2 附加**

```json
{
  "units": [
    { "id": 1, "parentUnitId": null, "unitKind": "CHAPTER", "title": "第一章", "sortOrder": 1,
      "children": [{ "id": 2, "parentUnitId": 1, "title": "1.1 概述", "sortOrder": 1, "children": [] }] }
  ],
  "knowledgePoints": [{ "id": 5, "unitId": 2, "code": "KP-01", "name": "需求分析" }],
  "classes": [{ "id": 31, "classId": 9, "name": "软工 1 班", "className": "软工 1 班",
                "termName": "2026-2027-1", "publishedAt": "2026-10-28", "studentCount": 42 }]
}
```

> `classes[].id` 是**绑定 ID**，解绑用它。`classes[].classId` 是教学班 ID，跨组引用用它。两个别混。

**K3 请求体**：`{ "name": "...", "officialCourseId": 2, "summary": "...", "learningObjectives": "..." }`，返回 `data` = 课程 ID。
**K6 错误**：重复绑定 → 500「该课程已绑定这个教学班」；班级已被别的课程占用 → 500「该教学班已绑定其他课程，请先解绑」；教师绑非本人任教班级 → 403「只能把课程绑定到自己任教的教学班」。

## 4. 新增端点：跨组作用域（`/education/scope`）

> 这一组是给第 3～6 组用的**只读最小视图**，不接受任何 `userId` 入参（除管理员专用的一条），身份一律取会话。
> 代码已提交并编译通过，**尚未在运行库联调**，联调由 2A 在 G0 关口完成。

| # | 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|---|
| S1 | GET | `/education/scope/term/current` | 登录用户 | 当前学期，未设置为 `null` |
| S2 | GET | `/education/scope/my/classes` | 登录用户 | 我可教/可学的班级 + 权限位 |
| S3 | GET | `/education/scope/classes/{classId}/me` | 登录用户（非成员 403） | 我在此班的身份与权限 |
| S4 | GET | `/education/scope/classes/{classId}/members` | 管理员 / 本班教师 | **仅有效成员** |
| S5 | GET | `/education/scope/classes/{classId}/binding` | 管理员 / 本班教师 | 当前绑定，未绑定为 `null` |
| S6 | GET | `/education/scope/courses/{courseId}/classes` | 管理员 / 负责人 | 课程绑定的班级与人数 |
| S7 | GET | `/education/scope/users/{userId}/classes` | **仅管理员** | 只读查看他人班级 |

**S1 `data`**

```json
{ "termId": 1, "name": "2026-2027 学年第一学期", "code": "2026-2027-1",
  "startsOn": "2026-09-01", "endsOn": "2027-01-15", "current": true }
```

**S2 `data`（数组）**

```json
[{
  "classId": 9, "name": "软工 1 班", "code": "SE-2026-01",
  "termId": 1, "termName": "2026-2027-1", "organizationId": 100,
  "status": "ACTIVE", "classKind": "FORMAL",
  "teacher": true, "student": false, "teacherRole": "LEAD",
  "canPublish": true, "canGrade": true, "canExport": false
}]
```

`teacher`/`student` 可同时为 `true`（一人既任教又在班）。`status` 是**班级状态**，不是成员状态。

**S3 `data`**

```json
{ "classId": 9, "teacher": false, "student": true, "teacherRole": null,
  "canPublish": false, "canGrade": false, "canExport": false, "validTo": null }
```

**S4 `data`（数组，仅有效成员）**

```json
[{ "classId": 9, "memberId": 501, "userId": 301, "userName": "2023001",
   "nickName": "张三", "joinedAt": "2026-09-05" }]
```

**S5 `data`**：`{ "bindingId": 31, "classId": 9, "courseSpaceId": 77, "publishedAt": "2026-10-28" }`，未绑定为 `null`。

**S6 `data`**：与 K2 的 `classes` 同结构。

### 4.1 各组最小消费集

| 组 | 场景 | 调这些 |
|---|---|---|
| 第 3 组 | 发布资源到某班前 | S3（`canPublish`）+ S5（`courseSpaceId` 与绑定未结束）+ S4（目标名单） |
| 第 3 组 | 学生看资料前 | S3（`student=true`）+ S5 |
| 第 4 组 | 教师开课 / 学生加入 / 断线重连 | S3 + S5；每次重连都要重调，不能只信首次 |
| 第 4 组 | 投影公开页只放公开内容 | 与 S3 无关，但**班级范围**必须来自 S5 的 `courseSpaceId` |
| 第 5 组 | 布置作业冻结目标 | S4（把 `userId` 列表写进目标表，保留 `memberId` 便于追溯） |
| 第 5 组 | 学生提交前 | S3（`student=true`）；退班后立即 403 |
| 第 6 组 | 发班级公告 / 通知接收范围 | S4；公告发布后再校验一次（防止发布瞬间退班） |
| 第 6 组 | 总览数字 | S2 + S6 + S1，**不要自己重算人数** |

## 5. Java 内部契约（同模块跨领域调用）

> 第 3～6 组与我们在同一个 `ruoyi-education` 模块里，走 Java 接口比走 HTTP 更省事，也避免了"自己造一个 `/education/xxx/members` 再包一层"。
> 位置：`com.ruoyi.education.course.api`

```java
public interface EduCourseAccessService {

    EduClassRef requireClass(Long classId);
    EduClassRef requireActiveClass(Long classId);

    boolean isActiveTeacher(Long classId, Long userId);
    boolean isActiveStudent(Long classId, Long userId);
    EduClassMembership membership(Long classId, Long userId);

    List<EduMemberRef> activeMembers(Long classId);
    List<EduClassRef> classesOfTeacher(Long userId);
    List<EduClassRef> classesOfStudent(Long userId);

    EduClassBindingRef activeBinding(Long classId);
    EduCourseRef requireCourseReadable(Long courseSpaceId, Long userId);
    EduCourseRef requireCourseOwned(Long courseSpaceId, Long userId);

    EduTermRef currentTerm();
}
```

**异常语义**（不要自己 catch 后重造）：

| 情况 | 抛出 | 最终 HTTP |
|---|---|---|
| 对象不存在 | `ServiceException("教学班不存在")` | 200 + code 500 |
| 对象存在、无权 | `EduForbiddenException("...")` | 200 + code 403 |
| 非法参数 | `ServiceException` | 200 + code 500 |

**正确用法**

```java
// 布置作业：先确认教师有发布权，再冻结成员名单
EduClassMembership me = courseAccess.membership(classId, SecurityUtils.getUserId());
if (!me.canPublish()) {
    throw new EduForbiddenException("你在这个班没有发布权");
}
List<EduMemberRef> targets = courseAccess.activeMembers(classId);

// 学生提交：先确认仍是有效成员
courseAccess.membership(classId, SecurityUtils.getUserId());
```

**错误用法（评审会打回）**

```java
// ✗ 直接 join 我们的表
SELECT * FROM edu_class_student WHERE ...
// ✗ 用 sys_user_role 判断"是不是这个班的老师"
// ✗ 把 membership 结果缓存到 Redis 当作长期授权
```

## 6. 数据快照与字段字典

跨组落库时请保存这些字段，不要只存名称：

| 字段 | 来源 | 含义 | 是否可变 |
|---|---|---|---|
| `classId` | `edu_teaching_class.id` | 教学班 | 不变 |
| `courseSpaceId` | `edu_course_space.id` | 课程空间 | 不变 |
| `bindingId` | `edu_course_class_binding.id` | 一次绑定 | 解绑后仍保留 |
| `memberId` | `edu_class_student.id` | 一次入班 | 退班后仍保留 |
| `termId` | `edu_term.id` | 学期 | 不变 |
| `organizationId` | `edu_teaching_class.organization_id` | 组织 | 不变 |
| `teacherUserId` | `edu_class_teacher.user_id` | 授权教师 | 撤权后保留历史 |

**快照 ≠ 授权。** 作业目标名单、公告接收范围可以在发布时冻结成快照（便于追溯"当时发给了谁"），但**每次访问仍要重新判断当前资格**：退班学生能查到历史任务，但不能提交；解绑后课程资料不再对新请求开放。

## 7. 四类验收用例（各组联调请照这个走）

| # | 类型 | 步骤 | 期望 |
|---|---|---|---|
| A1 | 正常 | 管理员建学期 → 建班 → 加 2 名学生 → 授权 1 名教师 | 全部 200；列表能看到教师名与学生数 |
| A2 | 正常 | 教师建课 → 发布 → 绑定自己的班；学生查课程 | 学生 `/course/list` 出现该课，详情 200 |
| A3 | 空数据 | 新教师登录查 `/course/list`、`/scope/my/classes` | `rows=[]`、`total=0`，不报错，页面显示空状态 |
| A4 | 无权限 | 教师 A 查教师 B 的班成员 `/teaching-class/{id}/members` | code 403「你不是该教学班的任课教师」 |
| A5 | 无权限 | 学生直连 `/education/term/list`、`/education/student/list` | code 403 |
| A6 | 无权限 | 外班学生查 `/course/{id}` | code 403「你没有这门课程的访问权限」 |
| A7 | 撤权 | 学生退班后立刻查 `/course/list`、`/scope/classes/{id}/me` | 课程消失；`me` 返回 403 |
| A8 | 撤权 | 撤销教师授权后立刻查 `/teaching-class/mine`、`/scope/my/classes` | 该班消失；再次查成员 403 |
| A9 | 失败 | 重复入班 / 重复绑定 / 编码重复 | 500 + 明确文案，数据不变 |
| A10 | 失败 | 结束日期早于开始日期 | 500「结束日期不能早于开始日期」 |
| A11 | 失败 | 课程 `status` 传 `PUBLISHED` 之类非法值 | 500「不支持的课程状态」 |

## 8. 契约变更流程

1. 提 Issue，写清：影响端点、影响消费者（第几组/哪个页面）、字段增减、兼容期。
2. 至少一名消费者确认（第 3～6 组之一）+ 第 1 组复核权限语义。
3. 文档先改，再改代码；破坏性变更需保留旧字段一个迭代。
4. 任何人不得在未通知消费者的情况下改字段名、默认值、错误码。

## 9. 依赖第 1 组的部分（摘要，详表见《第2组依赖接口清单》）

9.1 可信身份：`LoginUser` 的 `userId`、`nickName`、`deptId`、`roles[].roleKey`、`isAdmin()`；`/getInfo` 的 `roles`/`permissions`。
9.2 角色键：`admin` / `teacher` / `student` 已用于判定；教务管理员键名待定（建议 `edu_admin`）。
9.3 数据范围：受限教务管理员应只看到本组织数据；当前实现是"管理员看全部"。
9.4 账号停用：若依令牌缓存在 Redis，停用账号后旧令牌是否立刻失效由第 1 组策略决定——这直接影响 §2.1 最后一行。
9.5 前端 403：公共 `request.js` 目前把 403 归入"其他错误"并 `reject('error')`，页面拿不到语义；2B 已在 `api/education/http.js` 里自带一套，长期应统一。
