# 第一、二、三组对接说明

本文件说明当前 `group3/` 目录中实际存在的代码与数据边界，作为源码交付的内部索引。部署与启动步骤以根目录 `运行说明.md` 为准。

## 1. 工程边界

- 唯一前端：`frontend/`，若依 Vue3 + Vite 工程，哈希路由。第一组登录/布局/系统页面保留；第二组教务与课程页面、第三组课程建设、课前准备与预习页面接入其中。
- 唯一后端入口：`backend/ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java`。Maven reactor 共 8 个项目：父 POM、common、system、framework、education、quartz、generator、admin。
- 教学业务主体：`backend/ruoyi-education/`。第二组教务/课程接口位于 `com.ruoyi.web.controller.education`（admin 模块），第三组业务位于 `com.ruoyi.education.group3`（education 模块）。
- 数据库脚本：`sql/`。全新空库使用 `install-fresh.sql`；已有库按 `sql/README.md` 的说明逐个执行增量脚本。

## 2. 第三组已实现能力

| 领域 | 主要入口 | 说明 |
|---|---|---|
| 课程建设 | `TeacherStudio.vue`、`/education/group3` | 资料多版本、分页预览、发布/撤回、资料文件夹（新建/改名/上架/下架/打包下载/批量移动）、章节与知识点（内容概览、学生进度、详情、正文）、ZIP 导入导出、题库 Excel 导入导出、题库文件夹（含归档）、题目资源库 |
| 课前准备 | `TeacherPrepare.vue`、`/education/group3` | 时间轴编排、备课方案、资料阅读、预习任务（支持整卷多题一次发布）、课堂讨论环节 |
| 学生端 | `StudentMaterials.vue`、`StudentPrestudy.vue`、`StudentDiscussion.vue` | 课程资料（文件夹层级导航）、课前学习/整卷作答、课堂讨论；均通过服务端按班级、时间窗和角色过滤 |
| 学生学习看板 | `StudentProgressBoard.vue`、`/education/group3/student/dashboard`、`/education/group3/student/answer-review` | 汇总视频任务点、预习提交与计分讨论的完成状态；提示新发布/即将截止/逾期任务；解析公布后提供作答回看、错题本与知识点归集；支持个人笔记读取与保存 |
| 公共教学资源库 | 教师端课程建设中的 `PublicLibrary.vue`、`/education/group3/public/*` | 分类浏览、文件或外链详情、分页预览、收藏、我的提交，并可把公共资源加入当前课程资料；使用独立的 `edu_public_resource_*` 表，不复用课程私有资源表 |
| 外部契约 | `PublishedResourceProvider`、`PreviewTaskProvider`、`PreviewSubmissionHandler`、`LearningAnalyticsProvider` | 供课堂、作业、统计/AI 等下游组读取已发布资源、预习任务与学情数据；提交持久化默认走本地实现，可由 `@Primary` 实现替换 |
| 学情数据契约 | `LearningAnalyticsProvider`、`Group3LearningAnalyticsService`、`/education/group3/contracts/learning-analytics/{bindingId}` | 面向统计/AI 组提供全班任务完成率、逐题作答记录、讨论参与度；仅本班具备发布权限的任课教师可读，解析公布前不返回答案/解析 |

第三组用第一组的身份入口与有效角色判定，通过第二组的课程、教学班和任课契约校验每个绑定。教师需要对教学班有发布权限，批量导出及讨论评分另需对应权限。课程建设中的章节、知识点写入复用第二组的大纲服务，课程负责人操作后，两处课程页面读取同一份数据。

### 2.1 近期新增接口

以下接口均挂在 `/education/group3` 下，沿用 `Group3Security` 的教师绑定与学生绑定校验。

| 能力 | 接口 | 说明 |
| --- | --- | --- |
| 题目资源库发布 | `POST /questions/library/share` | 请求体为 `{bindingId, questionIds}`；按题目当前版本生成共享快照，重复共享会被跳过 |
| 题目资源库撤回 | `POST /questions/library/withdraw` | 请求体为 `{bindingId, shareIds}`；只允许撤回本人课程发布的共享 |
| 题目资源库检索 | `GET /questions/library` | 可选参数 `keyword`、`questionKind`、`difficulty`、`collegeId`；只返回 `PUBLISHED` 记录 |
| 题目资源库学院 | `GET /questions/library/colleges` | 按共享题目的来源课程归集学院，用于筛选下拉 |
| 题目资源库详情 | `GET /questions/library/{shareId}` | 返回共享题目的题干、答案与解析 |
| 题目资源库引入 | `POST /questions/library/import` | 请求体为 `{bindingId, folderId, shareIds}`；在当前课程生成题目副本并写入 `origin_share_id`，同一共享记录在同一课程只引入一次 |
| 题库文件夹 | `POST/PATCH/DELETE /folders`、`/folders/{folderId}` | `kind=QUESTION` 时操作题库文件夹；删除只解除文件夹归属，不删除题目 |
| 题库文件夹归档 | `POST /folders/{folderId}/archive` | 请求体 `{bindingId, kind:"QUESTION", archived}`；仅题库文件夹支持 |
| 资料文件夹发布 | `POST /folders/{folderId}/release`、`/revoke` | 文件夹整体上架或下架，内部按来源独立生效 |
| 文件夹打包下载 | `GET /folders/{folderId}/download` | 以 ZIP 返回文件夹内文件型资料 |
| 批量移动 | `POST /folders/move` | 请求体 `{bindingId, kind, folderId, ids}`；`folderId` 为空表示移到根目录 |
| 章节详情 | `GET /outline/units/{unitId}/detail` | 返回章节信息、该章及子节的知识点、未归档资料与题目 |
| 知识点详情 | `GET /outline/knowledge-points/{pointId}/detail` | 返回知识点信息与关联题目 |
| 整卷发布 | `POST /preview-tasks` | 请求体在原有字段上增加 `questionVersionIds`；多题一次发布为同一份预习任务，学生统一作答、统一提交 |
| 学生笔记 | `GET/PUT /student/note` | 按 `bindingId` 读写本人笔记，请求体 `{bindingId, content}` |
| 公共资源库 | `GET /public/categories`、`GET /public/resources`、`GET /public/resources/{id}`、`POST /public/resources/{id}/import` 等 | 分类、列表、详情、上传、外链、预览页、下载、收藏、我的提交与加入课程资料 |

题目创建接口 `POST /questions` 支持可选的 `folderId`；题库列表返回 `folderId`、`shareId`、`shareStatus`，供前端区分根目录、文件夹内与已共享题目。

## 3. 关键数据约定

- 资料归属：`edu_resource.origin` 区分 `STUDIO`（课程建设）与 `PREPARE`（课前准备）。
- 发布来源：`edu_resource_class_release.release_source` 按来源独立生效，课程建设与课前准备的发布、撤回互不影响。
- 章节归属：`edu_resource.unit_id` 关联 `edu_course_unit(id, course_space_id)`，上传时传入的 `unitId` 会落库。
- 章节与知识点正文：`edu_course_unit.content_text`、`edu_knowledge_point.content_text` 由第二组大纲服务统一保存，单条上限 4000 字；教师端详情接口按章节聚合子节、知识点、未归档资料、题目与学习进度。
- 题目资源库：`edu_question_share` 以题目版本（`question_version_id`）为单位保存共享快照，`status` 为 `PUBLISHED/WITHDRAWN`，同一版本最多一条共享记录；引入时在当前课程生成题目副本，`edu_question.origin_share_id` 指向来源共享记录，不复制原课程的知识点关联。
- 题库文件夹：`edu_question_folder.archived_at` 标记归档，NULL 表示未归档；归档文件夹及其题目不进入根目录的常规展示。
- 学生笔记：`edu_student_note` 按 `(binding_id, user_id)` 唯一，读写均取当前登录用户，只能访问本人笔记。
- 公共资源库：`edu_public_resource_category/resource/version/page/favorite` 五张表独立于课程资源；加入课程时复制为当前课程的 `edu_resource`、版本、预览页与班级发布记录，之后与原公共资源互不影响。
- 时间：Java 侧 `Group3Time` 以 UTC 墙钟写入，SQL 侧以 `UTC_TIMESTAMP(3)` 判断开放窗口；JDBC 连接按 `运行说明.md` 的示例配置。
- 文件存储：`${ruoyi.profile}/education`，支持的文件类型见 `Group3FileStorageService`；转换在独立异步线程池执行，状态为 PENDING/READY/FAILED。
- 学习进度：看板只统计视频任务点、预习提交和开启评分的讨论话题；未开放任务不计完成率。作答回看严格按 `answer_opens_at` 过滤，简答题只展示参考答案，不自动判分。
- 学情数据契约口径：
  - `courseProgress`：完成率分母为「有效在班学生数 × 任务点数」，任务点包括开启任务化的已发布视频、`PREVIEW` 已发布预习任务和开启评分的讨论话题；每个任务点的完成人数不超过在班人数。草稿资料、草稿任务与不评分话题不计入。
  - `submissionRecords`：每人每题一条最新有效提交（`superseded_at IS NULL`），最多 500 条，`total` 为真实总数、`truncated` 标记是否被截断。`answerVisible=false` 时只有提交事实，不含 `correctAnswer / studentAnswer / explanation / correct`；简答题不判分。
  - `discussionParticipation`：仅统计未删除话题下的 `NORMAL` 帖；匿名帖计入参与次数但不返回正文或作者名。`records` 按学生汇总，`topicBreakdown` 为话题×学生明细。
  - 学生属于第二组课程数据，分母来自 `EduCourseAccessService.activeMembers`，第三组不直接读课程私有表。

## 4. 验证状态

已用独立 MySQL 8.0、Redis 环境验证单脚本空库初始化、增量脚本重跑、三端登录和课程详情。浏览器检查了课程建设、课前准备、学生资料、课前学习和讨论标签。联调覆盖课程大纲同步、资料上传和预览、资料 ZIP 和题库 Excel 导入导出、备课、预习提交、讨论回复、学习进度及跨班访问拦截。

本轮通过真实接口补充验证了题目资源库的发布、重复发布跳过、引入、重复引入跳过、撤回与按学院筛选，题库批量发布，章节与知识点详情及正文保存回读，以及学生笔记读写。整卷发布沿用既有预习任务接口，通过 `questionVersionIds` 一次提交多题。
