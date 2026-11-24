# 第 2 组（course 领域）成员与访问契约

- 文档编号：2-03
- 责任方：第 2 组（course 领域）
- 状态：现状说明（描述已存在实现，不含未实现承诺）
- 依据：工作区源码与 SQL（本工程**无 Git 仓库**，无法提供分支 / PR / commit；权威性以文件路径与行号为准）

---

## 1. 契约定位与边界

`EduCourseAccessService` 是第 2 组对其它领域唯一对外只读契约。

- 资源、备课、课堂、活动、作业、学情、总览等领域需要判断"某账号现在能否对某教学班/课程做某事"时，一律调用本契约。
- 调用方**不得直接 join** `edu_teaching_class`、`edu_class_student`、`edu_class_teacher`、`edu_course_class_binding`。
- 每次调用都查当前库，不做缓存；退班、撤权后下一次请求即失效。
- 返回的是快照记录，落库仅用于追溯，**权限判定必须重新调用**。

代码位置：

- 接口：`backend/ruoyi-education/src/main/java/com/ruoyi/education/course/api/EduCourseAccessService.java`
- 实现：`backend/ruoyi-education/src/main/java/com/ruoyi/education/course/api/impl/EduCourseAccessServiceImpl.java`

---

## 2. 接口清单

| 方法 | 语义 | 时间口径 | 异常 |
|------|------|----------|------|
| `requireClass(classId)` | 取教学班快照，不校验状态 | 当前库 | 不存在 → `ServiceException` |
| `requireActiveClass(classId)` | 取快照并要求 `status = ACTIVE` | 当前库 | 不存在/未开放 → `ServiceException` |
| `isActiveTeacher(classId, userId)` | 是否有效任课教师（`valid_to IS NULL`） | `NOW(3)` | 无 |
| `isActiveStudent(classId, userId)` | 是否有效在班学生（`left_at IS NULL`） | `NOW(3)` | 无 |
| `membership(classId, userId)` | 当前账号在该班的身份与权限位 | `NOW(3)` | 非成员 → `EduForbiddenException` |
| `activeMembers(classId)` | 该班**当前有效**学生名单，按入班时间排序 | `NOW(3)` | 无 |
| `activeTeachers(classId)` | 当前有效任课教师公开信息 | `NOW(3)` | 非成员 → `EduForbiddenException` |
| `classesOfTeacher(userId)` | 教师当前被授权的班级 | `valid_to IS NULL` | 无 |
| `classesOfStudent(userId)` | 学生当前在班的班级 | `left_at IS NULL` | 无 |
| `activeBinding(classId)` | 当前有效课程绑定；无则 `null` | `ended_at IS NULL` | 多绑定 → `ServiceException` |
| `activeBindings(classId)` | 该班全部当前有效绑定 | `ended_at IS NULL` | 无 |
| `requireCourseReadable(courseSpaceId, userId)` | 校验能否读课程 | 当前库 | 无权限 → `EduForbiddenException` |
| `requireCourseOwned(courseSpaceId, userId)` | 校验能否改课程 | 当前库 | 非负责人 → `EduForbiddenException` |
| `currentTerm()` | 当前学期；未设置返回 `null` | 当前库 | 无 |

**重要共性：本契约全部方法都没有 `at` 参数，一律以 `NOW(3)` 判定"此刻"状态，不提供历史时点查询。**

---

## 3. 数据结构

### 3.1 `EduMemberRef`（教学班有效成员快照）

| 字段 | 类型 | 含义 |
|------|------|------|
| `classId` | `Long` | 教学班 ID |
| `memberId` | `Long` | 入班记录 ID（`edu_class_student.id`），同一学生多次入班有多条历史记录 |
| `userId` | `Long` | 学生账号 ID |
| `userName` | `String` | 学号 / 登录名 |
| `nickName` | `String` | 姓名 |
| `joinedAt` | `String` | 入班**日期**，格式 `yyyy-MM-dd` |

**限制：无 `leftAt` 字段；`joinedAt` 仅到日，无时分秒、无毫秒、无时区标记。**

### 3.2 `EduClassMembership`（当前登录人在某班的身份与权限位）

| 字段 | 类型 | 含义 |
|------|------|------|
| `classId` | `Long` | 教学班 ID |
| `userId` | `Long` | 当前登录人 ID |
| `teacher` | `boolean` | 是否为有效任课教师 |
| `student` | `boolean` | 是否为有效在班学生 |
| `teacherRole` | `String` | `LEAD` / `ASSIST` / `GUEST`，非教师为 `null` |
| `canPublish` | `boolean` | 是否可发布资源/任务 |
| `canGrade` | `boolean` | 是否可批改与确认成绩 |
| `canExport` | `boolean` | 是否可导出 |
| `validTo` | `String` | 授权截止日期 `yyyy-MM-dd`，长期有效为 `null` |

### 3.3 `EduClassRef`（教学班只读快照）

| 字段 | 类型 | 含义 |
|------|------|------|
| `classId` | `Long` | 教学班 ID |
| `name` | `String` | 教学班名称 |
| `code` | `String` | 教学班编码（同学期唯一） |
| `termId` | `Long` | 所属学期 ID |
| `termName` | `String` | 学期名称（展示用，可空） |
| `organizationId` | `Long` | 所属组织 ID |
| `status` | `String` | `DRAFT` / `ACTIVE` / `ARCHIVED` |
| `classKind` | `String` | `FORMAL` / `TRIAL` |

### 3.4 `EduClassBindingRef`（教学班当前有效课程绑定）

| 字段 | 类型 | 含义 |
|------|------|------|
| `bindingId` | `Long` | 绑定记录 ID，解绑时使用 |
| `classId` | `Long` | 教学班 ID |
| `courseSpaceId` | `Long` | 课程空间 ID |
| `publishedAt` | `String` | 绑定/发布**日期** `yyyy-MM-dd` |

### 3.5 `EduTeacherRef`（教学班有效任课教师快照）

| 字段 | 类型 | 含义 |
|------|------|------|
| `classId` | `Long` | 教学班 ID |
| `id` | `Long` | 任课记录 ID（`edu_class_teacher.id`） |
| `userId` | `Long` | 教师账号 ID |
| `userName` | `String` | 登录名 |
| `nickName` | `String` | 姓名 |
| `teacherRole` | `String` | `LEAD` / `ASSIST` / `GUEST` |
| `canPublish` | `boolean` | 是否可发布资源/任务 |
| `canGrade` | `boolean` | 是否可批改与确认成绩 |
| `canExport` | `boolean` | 是否可导出 |
| `validTo` | `String` | 授权截止日期 `yyyy-MM-dd`，长期有效为 `null` |

---

## 4. 时间与精度语义

### 4.1 数据库层（有毫秒、按 UTC）

`edu_class_student`（`sql/mysql-v3-m0-core-schema.sql` 第 150–169 行）：

| 列 | 定义 | 说明 |
|----|------|------|
| `joined_at` | `DATETIME(3) NOT NULL` | 本段入班时间，**毫秒精度** |
| `left_at` | `DATETIME(3) NULL` | 本段退班时间（**不含**），`NULL` = 未退班 |
| `active_user_id` | 生成列 | `left_at IS NULL ? user_id : NULL`，用于唯一约束 |
| `left_by_user_id` / `leave_reason` | 可空 | 退班操作人与原因 |

- 唯一键 `uq_edu_class_student_active (teaching_class_id, active_user_id)`：同一时刻每个学生最多一条有效记录。
- 索引 `idx_edu_class_student_user_dates (user_id, joined_at, left_at)`：支持按用户查历史区间。
- 约束 `chk_edu_class_student_dates`：`left_at IS NULL OR left_at > joined_at`。
- JDBC 默认 `serverTimezone=UTC`（`backend/ruoyi-admin/src/main/resources/application-druid.yml`），可被环境变量 `RUOYI_DB_URL` 覆盖。`DATETIME` 列本身不存时区，语义按 UTC 约定。

### 4.2 契约层（精度被截断）

- `EduMemberRef.joinedAt` 由 SQL `DATE_FORMAT(cs.joined_at, '%Y-%m-%d')` 生成，**丢失时分秒与毫秒**。
- `EduMemberRef` **不暴露 `leftAt`**。
- `activeMembers` 只保留 `status = ACTIVE` 的行（即 `left_at IS NULL`），历史段不返回。

对应位置：

- `backend/ruoyi-education/src/main/resources/mapper/education/EduPortalMapper.xml` 第 206–222 行
- `backend/ruoyi-education/src/main/java/com/ruoyi/education/course/api/impl/EduCourseAccessServiceImpl.java` 第 140–154 行

---

## 5. 多段入班的存储与返回

数据库支持一个学生在一门教学班内的多段历史：

| 场景 | 表内形态 |
|------|----------|
| 在读 | 1 行：`joined_at = T1`，`left_at = NULL` |
| 退班 | 同一行写入 `left_at = T2` |
| 重新入班 | **新增一行**：`joined_at = T3`，`left_at = NULL`；旧行保持不变 |
| 绑定结束 | `edu_course_class_binding.ended_at` 写入，`active_space_id` 生成列变为 `NULL` |

返回规则：

- `activeMembers(classId)` 返回**当前有效段**（`left_at IS NULL`），`memberId` 为该段记录 ID。
- 契约**不提供**"按历史时点返回区间"，也**不提供**"按学生返回全部历史区间"。

---

## 6. 明确的能力缺口（供跨组评审）

以下为现状事实，非已实现能力：

1. `EduMemberRef` 缺 `leftAt`，`joinedAt` 精度仅到日。
2. 全部方法无 `at` 参数，无法按历史时点判定成员 / 绑定 / 教学班状态。
3. 无历史区间查询能力（无法一次取得某学生在某班的全部 `[joinedAt, leftAt)` 段）。
4. 成员相关 SQL 统一使用 `NOW(3)`，无参数化时点。

上述能力如需补齐，属契约变更，需走评审与跨组联调流程，并同步更新本文档。

---

## 7. 变更记录

| 日期 | 变更 | 说明 |
|------|------|------|
| 2026-11-21 | 建立文档 | 依据现有源码梳理成员与访问契约现状 |
