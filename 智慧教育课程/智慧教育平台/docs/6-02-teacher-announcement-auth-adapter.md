# 6-02 教师公告授权适配器接入说明

日期：2026-11-27

状态：**本地实现与隔离验证完成；未推送、未创建或修改 PR。**

基线：origin/main `eee6883ee461bcfc957129e540d2a4ea70bfade4`；#42 契约修复版本 `99f50ad495021dbe47b29502ba5516e043979d6c` 已合入 main。

## 1. 本轮范围

新增：

- `backend/ruoyi-education/src/main/java/com/ruoyi/education/course/authorization/MembershipTimelineTeachingClassAuthorizationAdapter.java`
- `backend/ruoyi-education/src/test/java/com/ruoyi/education/course/authorization/MembershipTimelineTeachingClassAuthorizationAdapterTest.java`
- `backend/ruoyi-education/src/test/java/com/ruoyi/education/course/service/TeacherAnnouncementAuthorizationIntegrationTest.java`
- 本说明文档

未修改 Controller、公告状态策略、权限码、错误码、`CourseAnnouncementService`、Mapper、SQL、生产配置、前端或依赖版本；未合入 #40 或任何前端分支。

## 2. 装配边界

适配器 `MembershipTimelineTeachingClassAuthorizationAdapter` 实现既有
`TeachingClassAuthorizationPort`，标注 `@Component`，通过构造器可选注入
`ObjectProvider<EduMembershipTimelineService>`。

- 只调用第 2 组公开契约 `EduMembershipTimelineService.teacherInBinding(bindingId, userId, Instant at)`。
- 不访问第 2 组 Mapper、私有表，不扫描教师全部班级，不新增 HTTP 端点。
- 缺少来源时 Bean 仍可创建；调用 `resolve` 抛运行时异常，由既有
  `CourseAnnouncementService` 授权不可用链映射为 503，不返回假授权。
- 有来源时上下文只注册本适配器一个生产 `TeachingClassAuthorizationPort` Bean。

## 3. 字段映射

| 来源 `EduTeacherBindingAccess` | 公告 `TeacherAnnouncementAuthority` | 说明 |
|---|---|---|
| `bindingId`、`teachingClassId` | 同名快照字段 | 必须与请求一致、为正数且非空 |
| `currentTeacher` | `currentTeacher` | 独立保留，不因绑定结束而清除 |
| `canPublish` | `canPublish` | 只取契约值；非任课教师的 null 归一为 false |
| `bindingActive` | `bindingActive` | 独立保留，不与其他标志做蕴含推导 |
| `classOpen` | `teachingClassActive` | 教学班当前状态 |
| `courseSpaceId` | 不作为公告授权字段 | 仅校验存在且为正数 |
| `teacherRole`、姓名等 | 不参与授权 | 不按角色名或展示字段自行赋权 |

权限组合：

- `currentTeacher=false、canPublish=null`：合法非任课教师结果，返回无发布权限快照，由公告服务按现有规则返回 403，不误判为 503。
- `currentTeacher=true、canPublish=false`：合法受限教师，保留现有列表、详情和“创建者编辑本人草稿”规则。
- `currentTeacher=true、canPublish=null`：不完整结果，失败关闭。
- `bindingActive=false` 或 `classOpen=false`：合法拒绝快照，保留标志，由公告服务返回 403。
- 团队 fallback 的 `canPublish=false` 与直接任课 CO_TEACHER 的 `canPublish=true` 均按契约值保留，不按角色名称赋权。

## 4. 时间与校验

- 传入的 `at` 是服务端 UTC 墙钟时间；先按 `DatabaseTimePrecision` 四舍五入到毫秒，再显式按 `ZoneOffset.UTC` 转 `Instant`。
- 不重新读取当前时间，不使用客户端时间。
- 返回的 `bindingPublishedAt` 必须非空；`bindingEndedAt` 允许为真正 null，存在时必须晚于发布时间。
- 适配器按半开区间 `[publishedAt, endedAt)` 独立复核 `bindingActive`，与来源标志矛盾时失败关闭。
- 返回 `bindingId`/`userId` 必须与请求一致且为正数；`teachingClassId`、`courseSpaceId` 必须存在且为正数。
- 空结果、跨用户、跨绑定、非法标识、缺失发布时间、错误区间、权限组合矛盾均抛运行时异常，不返回假授权。

## 5. 失败语义

| 场景 | 适配器行为 | 公告服务结果 |
|---|---|---|
| 正常任课教师/受限教师/团队 fallback | 返回快照 | 进入既有业务规则 |
| 非任课教师、绑定结束、教学班关闭 | 返回快照标志 | 403 |
| 输入非法、结果为空/不完整/矛盾 | 抛 `IllegalArgumentException` / `IllegalStateException` | 503 |
| 来源缺失 | 抛 `IllegalStateException` | 503 |
| 来源 `ServiceException`、数据库故障、重叠记录 | 原样抛出 | 503 |

不按异常文案猜 403/404，不为创建草稿新增 404/409。

## 6. 测试与验证

测试替身说明：适配器测试与集成测试中的第 2 组来源服务使用手工测试替身；集成测试的公告 Mapper 使用 H2 内存库。未连接真实 MySQL、真实登录、HTTP 或第 2 组 Mapper。

- `MembershipTimelineTeachingClassAuthorizationAdapterTest`：22 项，覆盖普通/受限/团队/非教师、CO_TEACHER 发布权限、绑定结束、班级关闭、空结果、跨用户、跨绑定、非法标识、缺失字段、错误区间、不完整权限、来源异常、重叠记录、UTC 毫秒传递、缺源启动及单一生产 Bean。
- `TeacherAnnouncementAuthorizationIntegrationTest`：6 项，覆盖真实适配器与公告服务共同装配后的正常创建、非教师 403、受限教师 403、绑定结束 403、来源故障 503、创建者编辑本人草稿，以及拒绝时无业务写入。
- `ruoyi-education` 全量 `clean test`：345 项，0 失败，5 跳过。跳过为既有真实 MySQL 条件测试：`StudentNoticeMySqlConcurrencyTest` 1 项、`NoticeMySqlHardeningTest` 4 项，均因未配置 `notice.mysql.url`。
- `ruoyi-admin` 打包（Java 17，跳过测试）：BUILD SUCCESS。
- `git diff --check`：通过。

以上均为单元/隔离测试结果，不代表真实登录、HTTP、服务端授权、审计或目标数据库联调通过。

## 7. 未验证边界

- 真实 MySQL、真实登录、HTTP 权限拦截、审计和目标库联调未执行。
- #40 学生授权适配器仍为 Draft；本轮只接通教师公告授权，教师发布到学生通知的完整生产链路仍未端到端联调。
- 生产 `sys_menu`、角色授权、部署配置未变更。
- 本轮未推送、未创建或修改 PR。
