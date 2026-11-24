# 关于公告通知授权接入「成员时间契约」的答复

- 发件：第 2 组（course 领域）
- 收件：第 6 组
- 日期：2026-11-21
- 关联文档：`docs/2-03-member-time-contract.md`（第 2 组成员与访问契约现状）

---

## 0. 结论先行

1. **本工程不存在 Git 仓库**（`d:\Group\--main\--main` 与 `智慧教育课程\智慧教育平台` 两层均无 `.git`，`git rev-parse` 返回 `fatal: not a git repository`）。因此**无法提供分支 / PR / head**。权威依据仅以工作区文件路径 + 行号为准。
2. 第 2 组现有契约**不满足**通知可见性所需的精确区间：`EduMemberRef` 只给 `joinedAt`（`yyyy-MM-dd`），**没有 `leftAt`**，无毫秒、无时区。
3. 现有契约**没有 `at` 参数、没有历史区间查询**，只能回答"此刻是否在班"。
4. 通知侧的生产适配器**尚未存在**：`StudentAnnouncementAuthorizationPort` 只有测试替身，无生产实现。
5. **第 6 组继续按 503 失败关闭是正确的**，不应补假授权。缺口未补齐前没有安全的放行路径。

---

## 1. 权威接口清单（文件 + 行号）

| 契约 | 文件 | 关键内容 |
|------|------|----------|
| 成员快照 | `backend/ruoyi-education/src/main/java/com/ruoyi/education/course/api/EduMemberRef.java` | 字段 `classId, memberId, userId, userName, nickName, joinedAt` |
| 组间唯一只读契约 | `backend/ruoyi-education/src/main/java/com/ruoyi/education/course/api/EduCourseAccessService.java` | `activeMembers`、`isActiveStudent`、`membership`、`activeBinding(s)` |
| 实现 | `backend/ruoyi-education/src/main/java/com/ruoyi/education/course/api/impl/EduCourseAccessServiceImpl.java` | 第 140–154 行 `activeMembers` 仅保留 `status=ACTIVE` |
| 成员 SQL | `backend/ruoyi-education/src/main/resources/mapper/education/EduPortalMapper.xml` | 第 206–222 行 `selectClassMembers`；第 664–672 行 `countStudentInCourse` |
| 通知授权端口 | `backend/ruoyi-education/src/main/java/com/ruoyi/education/course/authorization/StudentAnnouncementAuthorizationPort.java` | `resolve(bindingId,userId,at)`、`listActiveStudents(bindingId,at)` |
| 通知成员快照 | `backend/ruoyi-education/src/main/java/com/ruoyi/education/course/authorization/StudentAnnouncementMember.java` | `joinedAt` / `leftAt` 为 `LocalDateTime` |
| 发布 fan-out | `backend/ruoyi-education/src/main/java/com/ruoyi/education/course/service/AnnouncementNoticeFanoutService.java` | 第 147–175 行 `[joinedAt, leftAt)` 过滤；第 118–141 行缺适配器即 503 |
| 表结构 | `sql/mysql-v3-m0-core-schema.sql` | 第 150–169 行 `edu_class_student` |
| 契约文档 | `docs/6-02-announcement-notice-contract.md` | 第 163、267、538 行区间过滤约定 |

---

## 2. 逐条答复

### Q1：`joinedAt` / `leftAt` 是否提供完整时间、时区、毫秒精度？

**分两层，答案不同：**

| 层 | `joinedAt` | `leftAt` | 精度 | 时区 |
|----|-----------|----------|------|------|
| 数据库 `edu_class_student` | 提供，`DATETIME(3) NOT NULL` | 提供，`DATETIME(3) NULL` | **毫秒** | 列无时区，按 **UTC** 约定；JDBC 默认 `serverTimezone=UTC` |
| 第 2 组契约 `EduMemberRef` | 仅 `yyyy-MM-dd` | **完全没有** | 仅到**日** | 无时区标记 |

结论：**底层数据有毫秒级 UTC 时间，但对外契约没有暴露，`EduMemberRef` 不满足精确区间需求。**

补充：通知侧自身的 `StudentAnnouncementMember` 已按 `LocalDateTime` + 毫秒设计，契约形状是对的，缺的是生产实现与数据来源。

### Q2：多次退班、重新入班时返回哪个区间？有 memberId / 历史查询能力吗？

- **存储层保留历史**：每次入班新增一行，`id` 独立；退班只写 `left_at`，不删行；唯一键保证同一时刻最多一条有效。
- **契约层只返回当前段**：`activeMembers(classId)` 按 `status = ACTIVE` 过滤，即 `left_at IS NULL` 这一段。**返回的一定是当前有效区间，不是历史区间。**
- **`memberId` 有**：即 `edu_class_student.id`，可标识单条入班记录。
- **历史区间查询：没有。** 无"按用户返回全部历史段"，也无"按历史时点返回当时区间"的方法。

结论：**当前契约给不了历史段；重新入班后只能拿到新段。**

### Q3：查询参数 `at` 能否按指定时点判断成员、绑定和教学班状态？

- **第 2 组契约：不能。** 全部方法签名无 `at`，SQL 统一 `NOW(3)`（`selectClassMembers` 第 215/221 行、`countStudentInCourse` 第 670–671 行）。语义是"此刻"。
- **第 6 组端口：签名支持。** `resolve(..., at)` 与 `listActiveStudents(..., at)` 明确要求实现方以 `at` 判断有效期（端口注释第 23–24、47–48 行）。
- **但端口无生产实现**，`at` 语义目前无人兑现。

结论：**这是缺口。要"历史时点判定"，第 2 组需新增契约能力。**

### Q4：在读 / 退班 / 重新入班 / 绑定结束 的示例与字段含义

**字段含义（`edu_class_student`）**

| 字段 | 含义 |
|------|------|
| `id` | 入班记录 ID（契约里的 `memberId`），多段历史各不相同 |
| `teaching_class_id` | 教学班 |
| `user_id` | 学生账号 |
| `joined_at` | 本段入班时间，UTC，毫秒 |
| `left_at` | 本段退班时间（**不含**），`NULL` = 未退班 |
| `active_user_id` | 生成列：`left_at IS NULL ? user_id : NULL` |
| `left_by_user_id` / `leave_reason` | 退班操作人与原因 |

**场景示例（学生 S，教学班 100）**

| 场景 | 表内形态 | 当前契约返回的区间 |
|------|----------|-------------------|
| 在读 | `id=1, joined_at=2026-09-01T00:00:00.000Z, left_at=NULL` | `[2026-09-01T00:00:00.000Z, ∞)` |
| 退班 | `id=1, joined_at=09-01, left_at=2026-10-01T00:00:00.000Z` | **不再返回**（非 ACTIVE） |
| 重新入班 | `id=1` 保持 `left_at=10-01`；新增 `id=2, joined_at=2026-11-01T00:00:00.000Z, left_at=NULL` | 只返回 `[2026-11-01T00:00:00.000Z, ∞)`，**第 1 段拿不到** |
| 绑定结束 | `edu_course_class_binding.ended_at` 写入，`active_space_id` 变 `NULL` | `activeBinding(s)` 不再返回该绑定 |

### Q5：重新入班后旧通知会不会恢复可见？

**不会自动全部恢复。** 机制上是：

- 通知查询把 `visibleAt` 与返回区间做 `[joinedAt, leftAt)` 判定；
- 重新入班后返回的是**新段**，`joinedAt` = 重新入班时间；
- 重新入班前的旧通知 `visibleAt < 新 joinedAt` → 判定不可见。

**若要"恢复历史"，必须逐段用各自的历史区间判定**，而第 2 组当前不提供历史区间查询。因此第 6 组的判断正确：**不能假定旧通知会恢复，必须先明确返回哪一段。**

退班后是否允许历史访问，属产品/负责人决策。本次只确认数据与接口事实，未做访问政策决定。

---

## 3. 缺口清单（需第 2 组补齐后方可解除 503）

1. `EduMemberRef` 增加 `leftAt`；`joinedAt` / `leftAt` 改为**毫秒级 UTC**（`LocalDateTime` 或 `Instant`），去掉 `yyyy-MM-dd` 截断。
2. 新增带 `at` 参数的契约：`membershipAt(classId, userId, at)`、`activeMembersAt(classId, at)`。
3. 新增历史区间查询：`memberIntervals(userId, classId)` 或 `memberIntervalsByBinding(bindingId, at)`，返回全部 `[joinedAt, leftAt)` 段及 `memberId`。
4. 提供**生产版** `StudentAnnouncementAuthorizationPort` 实现，把上述契约桥接到 `StudentAnnouncementMember`。

在第 1–4 项落地并通过联调前，第 6 组按 503 失败关闭是正确且唯一安全的选择，第 2 组不会要求放松该约束。

---

## 4. 待第 6 组确认

- 是否需要"历史时点"能力（对应缺口 2、3），还是只需"当前时点 + 当前区间"（对应缺口 1、4）即可。
- 若需历史能力，请明确 `at` 的时区与精度要求（建议 RFC3339 UTC 毫秒），以便第 2 组设计契约。
