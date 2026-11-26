# 后端架构索引

## 1. 模块划分

`backend/` 是若依 3.9.2 的 Maven 多模块工程，reactor 包含：父 POM、`ruoyi-common`、`ruoyi-system`、`ruoyi-framework`、`ruoyi-education`、`ruoyi-quartz`、`ruoyi-generator`、`ruoyi-admin`。

- `ruoyi-admin`：唯一 Spring Boot 启动模块，入口 `com.ruoyi.RuoYiApplication`；第二组教务/课程 Controller 位于 `com.ruoyi.web.controller.education`。
- `ruoyi-education`：教学业务模块。第二组课程访问契约位于 `com.ruoyi.education.course.api`；第三组业务位于 `com.ruoyi.education.group3`；历史公告相关类位于 `com.ruoyi.education.course.*`（见该模块 README）。
- 基础模块不得反向依赖教学业务；业务模块之间只通过明确的只读契约协作。

## 2. 第三组包结构

```text
com.ruoyi.education.group3
├── config/      异步转换线程池、定时清理开关
├── contract/    对下游组暴露的只读/提交契约
├── controller/  /education/group3 REST 入口
├── mapper/      Group3Mapper 与 Group3Mapper.xml
├── service/     访问控制、资源、备课、讨论、转档、存储、保留策略
└── support/     请求记录、常量、时间与角色工具
```

- `Group3AccessService` 通过 `EduCourseAccessService` 判断教师任课、学生在班和课程绑定，不直读第二组私有表。
- `Group3FileStorageService` 将文件写到 `${ruoyi.profile}/education`，并在独立线程池中生成预览页；状态为 PENDING/READY/FAILED。
- `Group3RetentionService` 每天 03:00 清理超过保留期且仍为 FAILED 的版本及其文件。
- `PublishedResourceProvider`、`PreviewTaskProvider`、`PreviewSubmissionHandler` 是给课堂、作业、AI 等下游组消费的接口；提交持久化默认由本地实现写入共享作业表，可由下游 `@Primary` 实现替换。
- `LearningAnalyticsProvider` 向统计/AI 组暴露全班完成率、作答记录与讨论参与度，本地实现 `Group3LearningAnalyticsService` 只读第三组数据，成员范围经第二组 `EduCourseAccessService` 获取，答案/解析仍受 `answer_opens_at` 约束。
- 写操作审计：`Group3Controller` 的教师端管理类写接口标注若依 `@Log(title, businessType)`，由日志切面写入 `sys_oper_log`；查询接口及学生观看、笔记、提交、讨论回复、收藏等轻量操作不记录，管理员在「系统监控 → 操作日志」查看。

## 3. 数据范围与权限

所有接口都在服务端校验当前登录人的角色、任课/在班关系和课程绑定；学生只能看到已发布、在开放窗口内、处理状态为 READY 的版本。资料发布按 `release_source` 分为 STUDIO 与 PREPARE，两条发布线互不影响。文件下载和预览接口复用同一套版本可见性校验。

## 4. 构建与验证

模块入口与构建命令见根目录 `运行说明.md` 和 `backend/README.md`。当前交付已在本机 MySQL 8.0 + Redis 环境启动后端并完成三组联调，第三组功能通过真实接口验证，前端页面已做浏览器检查。
