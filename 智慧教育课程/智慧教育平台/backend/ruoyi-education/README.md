# 教育业务模块

本模块已接入父工程构建，当前包含 6-02 教师公告的 Entity、状态规则、Mapper 读写和业务 Service；不包含 Controller、前端 DTO、学生通知或公告通知 fan-out。

模块职责、跨域调用方向与对象约束见 [后端架构规划](../README.md) 与 [架构文档](../../docs/architecture.md)。

## 当前内容

```text
ruoyi-education/
├── pom.xml
└── src/
    ├── main/java/com/ruoyi/education/course/
    │   ├── authorization/
    │   │   ├── TeacherAnnouncementAccess.java
    │   │   └── TeachingClassAuthorizationPort.java
    │   ├── domain/
    │   │   ├── AnnouncementStatus.java
    │   │   ├── AnnouncementVisibility.java
    │   │   ├── CourseAnnouncement.java
    │   │   └── TeacherAnnouncementAuthority.java
    │   ├── exception/
    │   │   ├── AnnouncementException.java
    │   │   ├── AnnouncementAccessDeniedException.java
    │   │   ├── AnnouncementAuthorizationUnavailableException.java
    │   │   ├── AnnouncementConcurrentUpdateException.java
    │   │   ├── AnnouncementNotFoundException.java
    │   │   ├── AnnouncementPersistenceException.java
    │   │   ├── AnnouncementStateConflictException.java
    │   │   └── AnnouncementValidationException.java
    │   ├── mapper/
    │   │   └── CourseAnnouncementMapper.java
    │   ├── policy/
    │   │   └── AnnouncementStatePolicy.java
    │   └── service/
    │       └── CourseAnnouncementService.java
    ├── main/resources/mapper/education/
    │   └── CourseAnnouncementMapper.xml
    └── test/java/com/ruoyi/education/course/
        ├── mapper/CourseAnnouncementMapperTest.java
        ├── policy/AnnouncementStatePolicyTest.java
        ├── security/CurrentUserAccessTest.java
        └── service/
            ├── CourseAnnouncementServiceTest.java
            └── CourseAnnouncementServiceWiringTest.java
```

- `CourseAnnouncement` 只映射现有 `edu_course_announcement` 表字段，不包含 `visibilityState`、发布人姓名等派生或未确认响应字段。
- `CourseAnnouncementMapper` 提供教师/学生读取查询，以及创建草稿、更新草稿、发布、归档的条件写操作。
- `CourseAnnouncementService` 实现教师创建、编辑、发布、归档和教师读取；写操作使用事务和影响行数检查，发布/归档不写通知。
- 重复发布/归档在身份、绑定和权限校验通过后幂等成功；已归档后再发布、草稿归档等非法转换仍为状态冲突。
- 条件更新影响 0 行时，Service 使用 `selectByIdForUpdate` 当前读重新加载，并按“已处于目标状态、公告不存在/跨绑定、非法状态、真实并发冲突”分类，避免 REPEATABLE READ 快照读返回旧状态。
- 草稿权限：创建者可读取/编辑自己的 `DRAFT`；具有本班公告管理权限（当前为 `canPublish`）的教师可读取/编辑；发布/归档必须有 `canPublish`。
- `AnnouncementStatePolicy` 是纯规则：草稿可编辑/发布，已发布可归档，学生只读当前 `OPEN` 公告；不查询数据库。
- `TeachingClassAuthorizationPort` 是教学班授权适配端口；当前没有生产实现，缺失授权适配时公告操作按授权不可用拒绝，不会放行。

## 登录用户身份

业务接口不得信任前端传入的用户 ID。当前登录用户 ID 统一从若依认证上下文读取：

```java
Long userId = SecurityUtils.getUserId();
```

无认证上下文时该方法抛出 `ServiceException`（401 语义）。本模块不另行创建用户会话或令牌体系。

## 构建与测试

在 `backend/` 目录执行：

```bash
mvn -B -pl ruoyi-education -am test
```

当前测试使用 JUnit 5 和 H2 内存数据库，验证 Mapper XML、状态规则、认证上下文、Service 权限/幂等/0 行分类/事务回滚和授权缺失失败关闭；**不是 MySQL 联调，也不代表数据库已部署**。

## 尚未实现

- 教师公告 Controller、前端请求/响应 DTO。
- 学生公告/通知接口、未读数、单条标记已读、全部标记已读（契约已确认，代码未实现）。
- 公告发布后的 `ANNOUNCEMENT` 通知 fan-out。
- 6-01 总览、6-05 校园资讯/留言/系统通知、AI 功能。
- 教学班授权端口的生产实现；当前没有真实任课/绑定联调。
- 异常到 HTTP 响应码的 Controller/Advice 映射。
- 数据库变更；当前没有新增表、字段或迁移脚本。

## 扫描方式

- 组件扫描：`RuoYiApplication` 在 `com.ruoyi` 包，覆盖 `com.ruoyi.education`。
- Mapper 扫描：`@MapperScan("com.ruoyi.**.mapper")`。
- Mapper XML：`classpath*:mapper/**/*Mapper.xml`。
- 类型别名：`com.ruoyi.**.domain`。
