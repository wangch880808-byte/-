# 数据库脚本与维护手册

适用于智慧教育平台第一、二组对接版。本文按源码中的 SQL 文件和实际 DDL 整理，覆盖 `sql/` 下的 13 个脚本及 `docs/` 下的 1 个索引草案。环境配置见 [项目运行说明](../运行说明.md)，教学模型设计背景见 [数据库设计](../docs/database-design.md)。

## 1. 先确定要执行哪个脚本

| 场景 | 执行顺序 | 执行前确认 |
| --- | --- | --- |
| 新建本地联调或演示库 | [install-fresh.sql](install-fresh.sql) | 目标库已创建且为空。该文件会执行若依和 Quartz 的 `DROP TABLE`，还会初始化虚构演示数据。 |
| 升级已经接入若依和教学表的对接库 | [upgrade-group12.sql](upgrade-group12.sql) → [upgrade-course-content.sql](upgrade-course-content.sql) | 先备份并核对源库版本。前者补学期、身份视图和菜单权限，后者补课程内容字段与附件表。 |
| 已在用的对接库，增加课程教师团队 | [upgrade-course-team.sql](upgrade-course-team.sql) | 新建 edu_course_teacher，记录课程协作教师及学生管理、名单导出权限。可重复执行。 |
| 已在用的对接库，只升级学校、学院和班级导入 | [upgrade-organization-types.sql](upgrade-organization-types.sql) | 在已有项目库运行。脚本添加组织类型列并更新组织视图。 |
| 已在用的对接库，更新行政班列表、组织导入和花名册 | [upgrade-administrative-class.sql](upgrade-administrative-class.sql) | 在原有项目库运行，前置脚本为组织类型升级。 |
| 单独维护身份视图或补齐教学菜单 | [group12-system-directory.sql](group12-system-directory.sql) 或 [group12-menu-permissions.sql](group12-menu-permissions.sql) | 身份视图依赖 `sys_dept.dept_type`，旧库先执行组织类型升级。菜单脚本只补缺失项。 |
| 为现有若依工程从零接入教学表 | [mysql-v3-m0-core-schema.sql](mysql-v3-m0-core-schema.sql) → [mysql-v3-m0-teaching-schema.sql](mysql-v3-m0-teaching-schema.sql) → 适用的两个升级脚本 | 目标库已具备兼容的若依系统表，且尚无这些教学表。核对用户、组织、角色映射，不导入演示种子。 |
| 评估学生通知查询索引 | [6-02-notice-index-draft.sql](../docs/6-02-notice-index-draft.sql) | 待审核。只在隔离环境评估，评审通过后另行发布正式迁移。 |

项目约定 MySQL **8.0.16+**、InnoDB、`utf8mb4`。教学表使用 `utf8mb4_0900_ai_ci`、生成列、复合外键和 `CHECK` 约束。应与共享联调环境核对实际 MySQL 小版本。脚本使用当前选中的库，库名可由各组自行确定。全量安装和本次增量脚本设置会话时区为 `+08:00`，连接配置与业务时间口径应保持一致。

## 2. 安装脚本和若依基础表

### install-fresh.sql

全量安装入口，包含 **76 张基础表和 4 个身份视图**。按文件中的顺序依次执行若依系统表、Quartz 表、教学核心表、教学扩展表、教学演示种子、若依演示账号、身份视图、教学菜单权限。随后把演示学期设为当前学期，并补齐课程正文、逻辑删除和附件结构。76 张表由 20 张若依基础表、11 张 Quartz 表、44 张教学基线表和 1 张章节附件表组成。

其中 `-- SOURCE ...` 是分段注释，各段 SQL 已嵌入这个文件。完整运行后，表、视图和基础演示数据已齐备。真实业务库沿用升级脚本，数据交接文件独立导入。

### upstream/ruoyi/ry_20260417.sql

若依基础库快照。它重建以下 20 张表，并写入内置部门、用户、角色、菜单、字典、配置和任务等初始记录。由第一组维护与教学模块共享的账号和权限契约。

| 表 | 业务用途 |
| --- | --- |
| `sys_dept` | 部门树、父部门、祖先路径、排序和启停状态。当前对接从账号的部门树推导教学组织 ID，子部门账号按祖先路径归入所属组织。 |
| `sys_user` | 登录账号、个人资料、所属部门、密码哈希及状态。 |
| `sys_post` | 岗位定义与状态。 |
| `sys_role` | 角色标识、显示顺序、数据范围和状态。 |
| `sys_menu` | 菜单目录、路由、页面组件和按钮权限码。 |
| `sys_user_role` | 用户与角色的多对多关系。 |
| `sys_role_menu` | 角色可用的菜单与操作权限。 |
| `sys_role_dept` | 角色选择自定义数据范围时关联的部门。 |
| `sys_user_post` | 用户与岗位的多对多关系。 |
| `sys_oper_log` | 后台操作审计，包括操作类型、结果及时间。 |
| `sys_dict_type` | 系统字典分类。 |
| `sys_dict_data` | 字典分类下的选项、显示文本与值。 |
| `sys_config` | 系统配置键值及内置标记。 |
| `sys_logininfor` | 登录成功、失败等认证日志。 |
| `sys_job` | 若依任务管理中的任务定义、调用目标、Cron 表达式和启停状态。 |
| `sys_job_log` | 任务执行日志和结果。 |
| `sys_notice` | 系统级通知公告。课程公告使用教学表。 |
| `sys_notice_read` | 系统公告的用户已读关系。 |
| `gen_table` | 若依代码生成器保存的表级生成配置。 |
| `gen_table_column` | 代码生成器保存的字段映射、校验和展示配置。 |

### upstream/ruoyi/quartz.sql

Quartz JDBC 持久化表快照。它重建以下 11 张 `QRTZ_*` 表，负责调度器内部状态。具体是否使用 JDBC 持久化取决于部署配置。若依后台的任务定义和执行日志仍查 `sys_job`、`sys_job_log`。

| 表 | 用途 |
| --- | --- |
| `QRTZ_JOB_DETAILS` | Quartz 任务标识、执行类、配置与数据。 |
| `QRTZ_TRIGGERS` | 触发器主记录、任务关联、触发时间和状态。 |
| `QRTZ_SIMPLE_TRIGGERS` | 简单触发器的重复次数、间隔及已触发次数。 |
| `QRTZ_CRON_TRIGGERS` | Cron 触发器的表达式和时区。 |
| `QRTZ_BLOB_TRIGGERS` | 以 BLOB 序列化存储的触发器数据。 |
| `QRTZ_SIMPROP_TRIGGERS` | 以字符串、数字和布尔属性保存的触发器扩展数据。 |
| `QRTZ_CALENDARS` | 用于排除或限定触发日期的日历。 |
| `QRTZ_PAUSED_TRIGGER_GRPS` | 已暂停的触发器组。 |
| `QRTZ_FIRED_TRIGGERS` | 已获取或已触发的执行记录，用于运行协调和恢复。 |
| `QRTZ_SCHEDULER_STATE` | 调度器实例状态和集群检入信息。 |
| `QRTZ_LOCKS` | 调度器协调所需的数据库锁记录。 |

## 3. 教学建表脚本

### mysql-v3-m0-core-schema.sql

教学基线第一段，共 **12 张表**。仅定义结构，执行前应已选中目标库并核对若依身份映射。后续的教学扩展表依赖这里的课程空间、教学班、资源及发布关系。

| 表 | 数据结构与维护要点 |
| --- | --- |
| `edu_term` | 学期编号、起止日期与当前标志。组织内编号唯一，生成列 `current_flag` 的唯一索引约束**全库最多一个**当前学期。 |
| `edu_official_course` | 组织内的正式课程目录。课程代码按组织唯一，可被教师课程空间或教学班关联。 |
| `edu_course_space` | 教师自建的课程空间，保存所属组织、所有者、可选正式课程、标题、简介和发布状态。可绑定多个班。逻辑删除字段由课程内容升级补齐。 |
| `edu_course_unit` | 章节、节及排序。父子单元必须在同一课程空间，循环层级由应用校验。正文由课程内容升级补齐。 |
| `edu_knowledge_point` | 课程知识点及可选所属单元，知识点编码在课程空间内唯一。正文由课程内容升级补齐。 |
| `edu_teaching_class` | 教学班的组织、学期、可选正式课程、班级编码与状态。同组织约束由复合外键保证。 |
| `edu_class_teacher` | 主讲或协同教师的班级任课授权。保存发布、批改、导出权限及授权起止。一个教师在同班最多一条未结束授权。 |
| `edu_class_student` | 学生入班、退班历史。一个学生在同班最多一条当前在班记录，活动和作业目标引用具体入班记录。 |
| `edu_course_class_binding` | 把某个课程空间发布到某个教学班，保存发布人和结束时间。班级和空间必须同组织，同一对组合最多一条有效绑定。 |
| `edu_resource` | 课程空间中的资源主记录，保存标题、文件或链接类型与状态。 |
| `edu_resource_version` | 资源版本、存储键或外链、文件元信息及处理状态。资源内版本号唯一。文件型资源的字节存于配套存储。 |
| `edu_resource_class_release` | 资源按课程班级绑定发布，固定到指定资源版本，记录开放期和结束原因。同一绑定中同一资源最多一条未结束发布。 |

课程空间与教学班的关系通过 `edu_course_class_binding` 建立。任课、在班和资源发布都要看有效期及状态；对已有历史的业务，应由对应服务结束关系并保留原记录。教学表里的 `user_id`、`owner_user_id`、`organization_id` 等是跨模块逻辑引用，导入时仍需核对账号、组织、角色和成员范围。

### mysql-v3-m0-teaching-schema.sql

教学基线第二段，共 **32 张表**。在核心表之后执行，覆盖备课、课堂、作业、评价、学习证据、AI 引用和通知等结构。建表表示数据库预留了这些结构，具体接口和页面进度以各组交付为准。

| 领域 | 表 | 数据结构与维护要点 |
| --- | --- | --- |
| 课次 | `edu_lesson` | 教学班课程绑定下的课次，可引用同一课程空间内的章节。 |
| 资源 | `edu_resource_page` | 文件版本的预览页，按版本和页码定位；记录预览存储键。 |
| 题库 | `edu_question` | 课程内的题目主记录及状态。 |
| 题库 | `edu_question_version` | 指定题目的版本，保存题干、答案、解析、评分量规等。下游引用固定版本。 |
| 题库 | `edu_question_knowledge_point` | 题目与知识点的多对多关系，双方必须属于同一课程空间。 |
| 备课 | `edu_lesson_plan` | 课次的备课方案及版本，保留状态和教师私有备课内容。 |
| 备课 | `edu_plan_material` | 备课方案中的材料顺序及指定资源版本。 |
| 备课 | `edu_activity_template` | 签到、随堂题、课堂小作业的配置模板；随堂题可绑定题目版本。 |
| 同步课堂 | `edu_class_session` | 绑定下的课堂会话，关联课次、方案及开课状态，同一绑定最多一个活动会话。 |
| 同步课堂 | `edu_session_projection` | 每个会话的当前投影快照，关联班级发布、资源版本、页码和状态序号。 |
| 同步课堂 | `edu_session_event` | 会话中的有序事件，区分公开和教师可见，`(session_id, seq_no)` 唯一。 |
| 课堂活动 | `edu_activity_instance` | 实际发起的签到或随堂题，可关联会话、模板和题目版本。按课程班级绑定和活动种类限制同时开放。 |
| 课堂活动 | `edu_activity_target` | 活动面向的学生快照，引用同班的入班记录。 |
| 课堂活动 | `edu_activity_response` | 某个目标的响应主记录，保存当前响应状态和修订号。 |
| 课堂活动 | `edu_activity_response_revision` | 响应的历史修订版本，保留作答或判定结果。 |
| 作业 | `edu_assignment` | 预习、课堂小作业、课后作业共用的发布记录，保存截止时间、迟交规则和提交上限。 |
| 作业 | `edu_assignment_item` | 作业题项、顺序、分值；题库题引用指定版本，另支持文本和文件题。 |
| 作业 | `edu_assignment_target` | 发布时确定的学生名单和目标状态，关联同班入班记录。 |
| 作业 | `edu_assignment_submission` | 某个作业目标的提交主记录，一个目标至多一条。 |
| 作业 | `edu_submission_version` | 历次提交、迟交标记与当前有效版本，同一提交最多一个有效版本。 |
| 作业 | `edu_submission_answer` | 指定提交版本对各题项的 JSON 作答内容或附件存储键。 |
| 评价 | `edu_grade_revision` | 针对提交版本的评分修订，保留分数、评语、草稿和确认状态。 |
| 评价 | `edu_grade_publication` | 成绩发布、撤回的历史，正式发布必须引用同一提交版本的已确认评分。 |
| 防重复 | `edu_idempotency_record` | 按操作者、操作、作用域和键摘要记录请求处理状态、结果及到期时间。 |
| 学习 | `edu_learning_event` | 学习证据事件，记录课程绑定、学生、来源及事件时间，按学生和客户端事件键去重。 |
| 学习 | `edu_video_watch_interval` | 指定班级发布和视频版本的观看起止区间，聚合时需要合并重叠区间。 |
| 学习 | `edu_wrong_question` | 错题证据，关联学生、作业项、题目版本和原提交版本。 |
| 统计 | `edu_metric_snapshot` | 指标快照，保留主体、窗口、定义版本、样本和统计口径。 |
| AI | `edu_ai_task` | 教学问答任务的请求角色、模型、提示词模板版本、运行状态及问答内容存储键。 |
| AI | `edu_ai_citation` | 问答结果引用的班级资源发布、资源版本与可选页码，用于溯源。 |
| 通知 | `edu_course_announcement` | 课程按班发布的公告正文、状态和可见时间。 |
| 通知 | `edu_user_notice` | 面向接收人的公告、预习、活动、作业和成绩通知，保存展示和已读时间。按接收人、类别、来源去重。 |

跨组维护重点如下。

- 学生能否访问资源或活动，要结合班级成员、课程绑定、发布范围、时间窗口和状态。外键保证归属关系，应用服务负责授权。
- 题目、资源、提交和成绩使用独立版本或发布记录。变更当前内容时应保留已经发布或使用的历史版本，避免旧作答、成绩或引用改变含义。
- `edu_resource_version` 和 `edu_resource_page` 保存文件引用。备份和恢复这部分功能时，需同时保留相关文件存储。章节、知识点附件走独立的 `edu_outline_attachment`，其文件字节在库内。

## 4. 已有库升级和跨组契约

### upgrade-group12.sql

适用于已有兼容 `sys_*`、`edu_*` 表的对接库。脚本创建并调用迁移过程，检测并按需添加 `edu_term.is_current`、生成列 `current_flag` 和唯一索引 `uq_edu_term_single_current`，之后删除过程。新补的 `is_current` 默认是 0，业务需要时由管理员在学期管理中设置当前学期。如果旧库已经存在多条 `is_current=1` 的记录，添加唯一索引前应先核定哪条保留。

脚本同时按需添加 `sys_dept.dept_type`，随后重建四个身份视图并补齐教学菜单授权。脚本沿用原有用户和密码。可在同一兼容库串行重跑，但 MySQL 的 DDL 会隐式提交，失败后要逐项检查字段、索引、视图和菜单绑定的实际落库状态。 [MySQL 隐式提交说明](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html)

### upgrade-organization-types.sql

对已在使用的对接库单独补齐组织类型。脚本向 `sys_dept` 添加可空的 `dept_type`，并让 `v_identity_dept` 暴露该字段。组织类型分别是 `SCHOOL` 学校、`COLLEGE` 学院、`MAJOR` 专业、`CLASS` 行政班、`OTHER` 其他。已存在的记录保持原有归属，管理员可在组织架构管理里补填类型。本次仍使用已有组织树存储行政班，学期和课程的教学班继续存储在 `edu_teaching_class`。脚本可以重复执行。

### upgrade-administrative-class.sql

为已有的 `sys_dept` 追加四个可空字段。`grade_year` 存储所属年级对应的四位入学年份，`education_level` 存储教育层次，`study_years` 存储学制年数，`class_remark` 存储班级备注。组织 Excel 的四列可选，更新时空白列保留原值。行政班名单按 `sys_user.dept_id` 关联班级组织，存档和备份时应同时保留这两张表。脚本可重复执行，全新安装已包含上述字段。

### group12-system-directory.sql

第一组维护身份目录契约，教学模块通过这些视图查询账号、角色和组织。视图用 `CREATE OR REPLACE SQL SECURITY INVOKER` 定义，应用按查询用途筛选启用且未删除的记录。视图不设行过滤，执行账户需具备视图查询及调用者权限模式所需的底层访问权限。教学模块按只读契约使用视图。

| 视图 | 来源 | 暴露范围 |
| --- | --- | --- |
| `v_identity_user` | `sys_user` | 用户 ID、账号、昵称、部门 ID、手机号、启停和删除状态。 |
| `v_identity_user_role` | `sys_user_role` | 用户 ID 到角色 ID 的映射。 |
| `v_identity_role` | `sys_role` | 角色 ID、角色标识及启停和删除状态。 |
| `v_identity_dept` | `sys_dept` | 部门树的 ID、父 ID、祖先路径、名称、排序、状态和组织类型。 |

用户视图包含手机号，应按已有目录接口的数据范围使用。密码哈希、邮箱、令牌和认证信息不在视图字段内。改视图时应和调用方一起核对字段名、类型及过滤条件。

### group12-menu-permissions.sql

读取 `sys_role`，写入 `sys_menu`、`sys_role_menu`。通过目录路径、页面组件或权限码判断记录是否已存在，只补缺失的菜单、按钮和授权关系。依赖目标库已存在相应角色标识。

| 角色 | 脚本补齐的范围 |
| --- | --- |
| `admin` | 教学业务目录、学期、班级、教师和学生管理页，以及学期维护、班级成员和任课授权操作。 |
| `teacher` | 教学业务目录、我的课程、课程上下文以及创建、编辑、发布和绑班操作。 |
| `student` | 教学业务目录、我的课程和课程上下文。 |

迁移页面或变更操作权限时，核对前端路由组件路径、`sys_menu.perms` 与后端权限校验。数据库菜单授权和课程归属、任课授权、学生在班等数据范围分别核对。

### upgrade-course-content.sql

适用于已建立课程空间、章节和知识点表的库。字段按存在性检查，附件表使用 `CREATE TABLE IF NOT EXISTS`。重复执行可补齐缺失对象，若同名列、表或约束定义已经不同，需单独核对和修正。

| 表或字段 | 用途 |
| --- | --- |
| `edu_course_space.deleted_at` | 课程逻辑删除时间。查询可用课程时按 `deleted_at IS NULL` 筛选，原课程和关联历史仍保留。 |
| `edu_course_unit.content_text` | 章或节的正文。 |
| `edu_knowledge_point.content_text` | 知识点正文。 |
| `edu_outline_attachment` | 章节或知识点附件。保存课程空间、单元 ID 或知识点 ID、文件名、字节数和 `MEDIUMBLOB` 内容。 |

一条附件只能归属一个单元或一个知识点，两个归属字段必须恰好一个有值。复合外键校验其归属同一课程，父单元或知识点被物理删除时，该附件随外键级联删除。课程逻辑删除只标记课程时间。

## 5. 演示数据与草案

### demo-v3-m0-seed.sql

为 44 张教学基线表写入虚构的联调数据，包含学期、正式课程、教师空间、两个教学班、资源发布、课堂活动、作业、学习证据与通知。演示账号在下一脚本补齐。这里使用预留高位 ID 对齐用户、组织和课程引用，并按执行时刻生成演示时间窗。单独执行时要求核心表和扩展表已建好，并使用没有业务数据的教学库。脚本会在检测到已有学期时拒绝继续，成功的数据写入在事务内提交。

### demo-v3-m0-ruoyi-login-seed.sql

要求若依基础数据和上一份教学种子都已导入。它写入 `sys_dept`、`sys_role`、`sys_user` 和 `sys_user_role`，创建演示学校、教师和学生角色、1 个演示教师和 4 个演示学生，并调整演示环境内置管理员的密码和部门。固定 ID 用于连接上一份脚本里的任课与在班记录。只用于独立演示库，执行前检查内置管理员、演示 ID 和角色是否符合脚本前置条件。

### docs/6-02-notice-index-draft.sql

待审核的索引草案，只拟为 `edu_user_notice` 新增 `idx_edu_notice_recipient_visible (recipient_user_id, visible_at, id)`，支持按接收人查找并按可见时间、ID 倒序排列。当前正式建表脚本保留原有去重唯一键、未读查询索引和绑定索引。评审时检查通知列表的 `EXPLAIN`、通知写入开销，以及批量标记已读与并发插入的锁行为。草案未被全量安装或任何升级脚本纳入。

## 6. 其他组执行更新的步骤

1. **核对环境。** 确认目标库名、MySQL 版本、业务分支和已执行的迁移。在 DataGrip 的目标查询控制台执行 `SELECT DATABASE(), VERSION(), @@session.time_zone;`。确认执行账号具备对应的表、视图、例程或数据写入权限。
2. **备份并预演。** 对已有库备份表结构、数据、视图和实际使用的例程。业务依赖外部资源文件时同时保留文件存储。先恢复到隔离库，按相同顺序执行脚本，记录备份位置、脚本版本与第一条错误。
3. **执行完整 SQL 文件。** 在 DataGrip 数据库资源管理器中右键目标库，选择 **SQL Scripts → Run SQL Script…**，确认目标 schema 后执行。含 `DELIMITER` 的脚本须完整执行，`SOURCE` 命令用于 MySQL 命令行客户端。 [DataGrip 运行 SQL 文件](https://www.jetbrains.com/help/datagrip/run-sql-files.html) · [MySQL 客户端命令](https://dev.mysql.com/doc/refman/8.0/en/mysql-commands.html)
4. **按变更核对结果。** 通过下面的只读查询确认表、字段、索引和视图。涉及权限时分别用管理员、教师和学生验证菜单与数据范围；涉及内容和附件时验证课程、章节、附件下载及班级可见性。
5. **处理失败和回退。** 在第一处错误停止，记录成功和失败的语句。DDL 可能已提交，先确认实际结构。涉及业务写入时用已演练的修复迁移或备份恢复方案处理。已有业务库的回退应以备份和迁移记录为依据。

```sql
-- 先确认查询窗口连接到预期的库。
SELECT DATABASE(), VERSION(), @@session.time_zone;

-- 核对当前学期。结果应为 0 或 1。
SELECT COUNT(*) AS current_term_count
FROM edu_term
WHERE is_current = 1;
SHOW INDEX FROM edu_term WHERE Key_name = 'uq_edu_term_single_current';

-- 核对课程内容升级。
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'edu_course_space' AND COLUMN_NAME = 'deleted_at')
    OR (TABLE_NAME IN ('edu_course_unit', 'edu_knowledge_point') AND COLUMN_NAME = 'content_text')
    OR (TABLE_NAME = 'edu_outline_attachment' AND COLUMN_NAME = 'file_data'));

-- 核对身份目录视图。
SELECT TABLE_NAME, SECURITY_TYPE
FROM information_schema.VIEWS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('v_identity_user', 'v_identity_user_role', 'v_identity_role', 'v_identity_dept');

-- 核对教学授权。结果按角色和权限码列出。
SELECT r.role_key, m.menu_type, m.menu_name, m.perms
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE r.role_key IN ('admin', 'teacher', 'student')
  AND r.del_flag = '0'
  AND (m.path = 'education' OR m.perms LIKE 'education:%')
ORDER BY r.role_key, m.order_num, m.menu_id;
```

升级后如果找不到 `deleted_at`、`content_text` 或附件表，先检查是否对后端配置指向的库执行了课程内容升级。报重复键时，区分本次数据已经导入、主键被其他记录占用、有效关系冲突三种情况。报外键错误时，核对目标库的同版演示记录和组织、课程、教学班引用。遇到大附件导致的包大小错误，检查客户端和服务端的 `max_allowed_packet`。 [MySQL 包大小说明](https://dev.mysql.com/doc/refman/8.0/en/packet-too-large.html)

## 7. 提交后续迁移时记录什么

- **迁移文件**写明负责人、适用起始版本、依赖脚本、影响表与字段、可否重跑、验证查询和回退办法。已交付的共享迁移应保留原版本，后续更正以新迁移追踪。
- **同时维护新装和升级路径。** 新增结构或权限时核对模块脚本、`install-fresh.sql` 的嵌入段以及对应升级入口。涉及视图或菜单时，同时核对 `upgrade-group12.sql` 中的内嵌定义，防止新库和升级库得到不同的结构或权限。
- **按依赖写入。** 先父记录后子记录，生成列由数据库计算。业务关系冲突应先确认归属和状态，再按业务服务或明确的迁移规则修正；保留原有外键、去重键和历史证据。
- **区分交付数据。** 基线种子使用虚构数据。联调增量、真实数据库备份和包含附件的业务导出按授权范围交接，源码库保留可共享的结构和迁移脚本。
- **登记执行结果。** 在团队交接记录中保留目标库、执行时间、脚本版本、执行人、备份位置及核验结果。修订数据库契约时同步通知相关模块的维护组。
