# 第 3 组交付说明

> 状态：M0 第 3 组前后端完成，已通过隔离 MySQL 真实接口与 Playwright 页面验证。  
> 范围：课程资源、题库、备课和预习编排；不包含 AI、课堂同步、统一评分或学情聚合。

## 1. 完成内容

- 教师课程资源上传、文件版本、PPTX/PDF/图片/文档处理和页码记录。
- 文件私有存储、受控预览、受控下载；每次请求重新检查教师任课授权或学生有效成员资格与发布窗口。
- 按教学班发布、替换和撤回资源版本；学生只获得当前开放版本，撤回后旧页面/下载地址返回无权限。
- 题库单选、多选、判断、简答，题目答案/解析以不可变版本保存；学生答案字段按公布时间裁剪。
- 备课方案、材料顺序、活动模板预设。
- 预习任务发布、目标学生快照、开放/截止/解析公布时间、学生提交与重复提交幂等。
- 教师“课程建设”“课前准备”和学生“课程资料”“课前学习”页面，复用原登录、页面外壳、导航、页签和退出逻辑。

代码入口：

- 后端：`backend/ruoyi-education/src/main/java/com/ruoyi/education/group3/`
- Mapper：`backend/ruoyi-education/src/main/resources/mapper/education/Group3Mapper.xml`
- 前端：`frontend/group3.js`、`frontend/group3.css`
- HTTP 接口：`/education/group3/**`
- Java 契约：`PublishedResourceProvider`、`PreviewTaskProvider`

## 2. 第 3 组提供给其他组的内容

| 使用方 | 交付物 | 使用方式与边界 |
|---|---|---|
| 第 4 组 | 已授权、已发布资源版本；正确版本号、媒体类型、页数、页码 URL | 通过 `PublishedResourceProvider` 或 `/contracts/published-resources/{bindingId}` 读取。教师视图可拿到本班当前发布；学生视图只拿开放中版本。课堂投影不得广播 `storage_key`。 |
| 第 5 组 | 预习任务、题目版本快照、目标学生、开放/截止/解析公布时间、本人提交状态；统一 `edu_assignment` 表上的提交入口 | 通过 `PreviewTaskProvider`、`/contracts/preview-tasks/{bindingId}` 和学生预习接口读取。当前第 3 组提交适配器已使用共享作业表，随后续统一作业服务接管提交/评分而不复制表。 |
| 第 6 组 | 预习任务数量、截止时间、完成状态和课程资源数量来源 | 只消费第 3 组授权接口，不直接查本组 Mapper 或表。 |
| 全组 | 资源版本与发布契约、答题答案公开时点、撤权后旧地址失效规则 | 以 [接口协作约定](api-contracts.md) 和本文为准。 |

### 跨组读取字段

发布资源契约主要返回：

- `bindingId`, `resourceId`, `versionId`, `versionNo`, `releaseId`
- `title`, `mimeType`, `mediaKind`, `pageCount`
- `opensAt`, `closesAt`, `downloadUrl`, `pages[]`
- 每个页面只返回 `pageNo`, `width`, `height`, `url`，不返回私有存储键

预习契约主要返回：

- `assignmentId`, `bindingId`, `title`, `instructions`, `state`
- `opensAt`, `closesAt`, `answerOpensAt`
- 题目 `assignmentItemId`, `questionId`, `questionVersionId`, `questionKind`, `options`
- `answer`、`explanation` 仅在解析公布时间到达后返回
- 学生本人 `submissionId`, `submittedVersion`, `submittedAt`

## 3. 第 3 组需要其他组提供的内容

| 提供方 | 必需输入 | 当前处理 |
|---|---|---|
| 第 1 组 | 可信 `user_id`、角色、统一 401/403 语义、后续业务菜单授权 | 已复用现有 `SecurityUtils` 与若依令牌，不另建登录。后续接入正式菜单时需给教师/学生开放对应入口。 |
| 第 2 组 | 课程空间、教学班、课程按班绑定、任课授权、有效学生成员、课次、章节、知识点及撤权即时生效契约 | 当前在 `Group3Mapper` 中以只读 SQL 消费现有 `edu_*` 表，并用演示数据验证；正式联调时应替换为第 2 组提供的服务契约，避免长期读私有表。 |
| 第 5 组 | 统一预习提交、改交、评分/反馈发布服务；正式成绩口径 | 当前使用共享 `edu_assignment*` 表提供独立可演示的预习提交回退实现；第 5 组交付后由统一作业服务接管，第 3 组保留任务与内容快照。 |
| 第 4 组 | 课堂公开页消费确认、页面版本/页码兼容性反馈 | 第 3 组已输出版本与逐页 URL；第 4 组接入时需确认投影页面缓存和撤权清理。 |
| 第 6 组 | 总览/待办入口与统一通知的消费确认 | 第 3 组提供预习数量、截止时间和本人完成状态；第 6 组不得自行复制资源或任务表。 |

## 4. 对象权限与状态

- 教师必须对教学班存在有效 `edu_class_teacher` 记录且 `can_publish=1`。
- 学生必须存在有效 `edu_class_student` 记录，教学班与课程绑定均未结束。
- 学生只能打开当前未结束、已到开放时间、未过截止时间的发布版本。
- 资源发布只接受 `READY` 版本；旧版本继续保留，不会被新上传覆盖。
- 同一资源在同一教学班只有一个未结束发布；新发布替换旧发布，撤回写入 `REVOKED` 和操作者。
- 预习提交按 `assignment_id + student_user_id` 定位目标，客户端不能传入其他学生 ID。
- 重复提交使用相同有效目标和提交版本返回幂等结果，不生成第二份有效提交。
- 学生未到解析公布时间时，接口根本不查询答案/解析字段。

## 5. 验证记录

- `mvn -B -pl ruoyi-admin -am -DskipTests package`：通过。
- 隔离 MySQL 8.0.44：按现有 M0 建表脚本、演示数据、若依系统表和登录种子导入成功。
- 真实接口：教师/学生登录、上下文、资源列表、题库、备课、预习发布和提交通过。
- PPTX 3 页转换为 3 张 PNG；PDF 2 页转换为 2 张 PNG。
- 损坏 PDF 返回 `FAILED` 并在列表中保留失败记录。
- 外班学生访问返回 403；学生上传教师资源返回 403。
- 学生提前读取答案返回空；重复提交返回幂等结果。
- 同一资源上传 V2 后替换按班发布，V1 旧地址立即 403，V2 页面可访问；题目 V2 与 V1 均保留。
- 撤回后学生旧版本地址和旧下载地址返回 403，学生跨组资源契约不再返回该版本。
- Playwright 使用真实后端完成教师资源/题库/备课、学生资料/预习桌面截图，以及 390px 手机视口检查；浏览器无脚本错误。

## 6. 已知限制

- 当前文件转换在请求线程内同步执行，适合 M0 演示；正式大文件应迁移到受控异步处理队列，并把处理中状态通过轮询/事件返回。
- 本地文件存储在 `ruoyi.profile/education`，正式环境需评估对象存储、加密、病毒扫描、备份和保留策略。
- PPTX 采用静态页渲染，不还原复杂动画、音视频或切换效果。
- 仍保留内部只读课程/成员 SQL 作为第 2 组契约未交付前的适配层。
- 预习提交回退实现依赖第 5 组的共享表结构；正式联调应改为调用第 5 组统一提交服务。
- 当前未开放外部链接资源，因为无法保证撤权后外部地址同步失效。
