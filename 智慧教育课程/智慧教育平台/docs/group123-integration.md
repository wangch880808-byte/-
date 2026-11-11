# 第一、二、三组对接记录

交付日期：2026-11-10。本轮在第一、二组对接版（smart-edu-g12）上接入第三组交付的课程资源、题库、备课与预习能力，形成统一的一套前后端工程。本文只记录本次实际整合范围与验证状态，不代表六组全部需求完成。

## 需求依据与冲突处理

1. docs/sources/分工文档.docx：v1.1，2026-10-23，第 3 组任务 3-01～3-04，课程资源、题库、备课和预习发布。
2. docs/sources/功能架构优化方案.md：V3.0，教师“课程建设／课前准备”、学生“课程资料／课前学习”入口。
3. docs/sources/group3/group3-delivery.md：第三组原交接说明，记录其接口、权限、验证与已知限制。
4. 第三组原工程 `D:\Huangzhitong\zhijiao-group3`：作为本轮的移植来源，只读取，不修改、不删除。

第三组原工程基于同一套 M0 表结构与若依 Vue3 外壳开发，但早于本次第一、二组对接。整合时以当前 g12 工程为准，保留 g12 较新的课程编辑、课程删除、章节正文附件和学生名单导入导出，不反向覆盖；只移植第三组独有的后端包、Mapper、前端页面与接口封装。

## 任务与落点

| 任务 | 本次整合结果 | 主要落点 |
|---|---|---|
| 3-01 资源上传、版本、转换、受控预览与按班发布 | 接入资源上传、文件版本、PPTX／PDF／图片／文档转换与页码记录；按教学班发布、替换、撤回；旧版本旧地址即时失效 | backend/ruoyi-education/.../education/group3/；Group3Mapper.xml；TeacherStudio.vue；StudentMaterials.vue |
| 3-02 题库、备课方案与预习发布契约 | 接入单选、多选、判断、简答题库与不可变题目版本；备课方案、材料顺序、活动模板预设；预习任务、目标学生快照、开放／截止／解析公布时间 | Group3Controller；Group3PrepareService；Group3ResourceService；TeacherPrepare.vue |
| 3-03 教师课程建设／课前准备页面 | 教师通过“课程建设”“课前准备”完成资源上传与状态查看、版本发布、题库维护、备课包与预习发布 | frontend/src/views/education/group3/TeacherStudio.vue、TeacherPrepare.vue |
| 3-04 学生课程资料／课前学习页面 | 学生按当前教学班查看已发布资源、阅读与下载、作答预习、查看允许公开的反馈与时间线 | StudentMaterials.vue、StudentPrestudy.vue、StudentTimeline.vue |

页面入口按分工文档与 V3.0 导航树嵌入课程详情，而不是在左侧栏新增一级菜单：教师课程详情为“课程概览／课程建设／课前准备／课堂教学／课后作业与反馈／学情分析与教学改进／AI教学助手”，学生课程详情为“课程概览／课程资料／课前学习／同步课堂／课后学习／我的学情／AI学习助手”。课程建设、课前准备、课程资料、课前学习分别挂载第三组页面组件；其余标签保留未开放占位。第三组页面通过 `contextBindingId` 接收当前课程详情选中的课程—教学班绑定，隐藏自身的课程选择器，随顶部课程／班级切换重新挂载。

## 对接落点与兼容处理

- 后端新增 `com.ruoyi.education.group3` 包（控制器、服务、访问校验、契约、文件存储、文档转换）与 `mapper/education/Group3Mapper.xml`；HTTP 前缀 `/education/group3/**`，文件保存在 `ruoyi.profile/education`。
- `ruoyi-education/pom.xml` 增加 PDFBox 3.0.3；PPTX 解析复用 `ruoyi-common` 已有的 Apache POI 5.5.1，不重复锁定版本。
- `application.yml` 单文件上传上限由 10 MB 调整为 200 MB、请求总上限 220 MB，以支持课件与 PDF；第二组课程大纲附件仍在 `EduOutlineContentController` 内单独限制 10 MB，语义不变。
- 前端新增 `src/api/education/group3.js` 与 `src/views/education/group3/`；页面不再注册为侧栏路由，由教师／学生课程详情按标签页异步加载，不新增若依菜单权限标识。
- `AppLayout.vue` 非管理员导航维持“工作台＋我的课程”两条固定入口，第三组页面不再作为侧栏菜单；课程详情内通过标签页进入，顶部课程／教学班选择器继续负责上下文切换。
- 第三组依赖的 `edu_resource*`、`edu_question*`、`edu_lesson_plan`、`edu_assignment*` 等 16 张表已包含在 M0 基础表和新库总脚本中，本版没有表结构变更，也没有新增迁移脚本。
- 第三组读写教学班与成员关系时仍使用 `Group3Mapper` 只读 SQL。这是第三组在第二组服务契约冻结前的适配层；本次不擅自改动其已验证逻辑，后续正式联调应按第三组交接说明替换为第二组服务契约，避免长期直读其他领域私有表。
- 预习提交继续复用共享 `edu_assignment`、`edu_assignment_submission`、`edu_submission_version` 表，作为第 5 组统一作业服务接管前的回退实现；本次不新建第二套提交表。
- 登录、令牌、401/403、请求客户端、页签与退出均沿用第一组能力；第三组未另建登录态。

## 对象权限与状态

与第三组原交付一致：教师须对教学班存在有效任课记录且具备发布权限；学生须有有效在班关系、课程绑定未结束；只开放当前未结束、已到开放时间、未过截止时间的资源版本；资源发布只接受处理成功的版本；同一资源在同一教学班只有一个未结束发布；撤回写入 `REVOKED` 并保留旧版本；学生解析公布前接口不查询答案与解析字段；预习提交按 `assignment_id + student_user_id` 定位，重复提交幂等。

## 验证记录

- `npm run build`（frontend）：通过，产物包含 TeacherStudio、TeacherPrepare、StudentMaterials、StudentPrestudy、Group3ContextBar 等页面分块。
- `mvn -B -f backend/pom.xml -pl ruoyi-admin -am -DskipTests clean package`：八个模块全部 SUCCESS，`ruoyi-admin.jar` 重新打包成功；包内 `ruoyi-education-3.9.2.jar` 含 26 个 group3 类与 Group3Mapper.xml，并打入 pdfbox 3.0.3 与最新前端产物。
- 未启动应用，未连接 MySQL／Redis，未执行接口测试、权限回归或浏览器验收；第三组原工程的真实接口与 Playwright 记录见其原交接说明，不能等同于本整合版的运行结论。

## 保留范围与后续工作

- 第三组范围是资源、题库、备课和预习；课堂同步、统一作业评分、学情聚合与 AI 仍未接入，对应菜单继续空白，不伪造接口或数据。
- 第三组课件转换在请求线程内同步执行，适合演示；大文件异步队列、对象存储、病毒扫描与保留策略属于后续工作。
- 第三组 `PublishedResourceProvider`、`PreviewTaskProvider` 契约原样保留，供第 4、5、6 组后续消费；本次没有为未接入的组创建前端页面。
- 预习的评分、改交与正式成绩仍应由第 5 组统一作业服务接管；本版保留第三组的可演示回退实现。
- 本轮没有修改 `D:\Huangzhitong\zhijiao-group3` 原工程，也没有覆盖第一、二组已交付文件；新增文件以第三组原交付为准，共用文件仅做最小接入改动。

## 交付方式

源码仍以 smart-edu-g12 为工程根，运行步骤见根目录 `运行说明.md`。本轮验证仅为前端生产构建与后端打包；数据库导入、服务启动与业务验收需按运行说明由使用者执行。
