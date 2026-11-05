# 6-02 前端接口接入说明（6-04 交付）

> 版本：0.2｜日期：2026-11-03｜负责人：6B｜分支：`six`
>
> 状态：13 个接口（教师公告 6 + 学生公告 2 + 学生通知 5）**均已合并到 `main@f21f51d`**，
> 前端按源码逐字段核对一致；`AnnouncementNoticeFanoutService`（PR #20）已合并，公告会生成通知。
> **源码核对一致 ≠ 真实联调通过**：本机无 JDK／Maven／MySQL／Redis 运行环境，
> 且生产授权契约与 `bindingId` 来源仍是外部阻塞。

## 1. 范围

本次只覆盖 `docs/分工.md` 的 6-04：教师发布班级公告、学生查看本班公告与本人通知。6-01 三端总览、6-03 总览页面、6-05/6-06 留待后续。

事实基线（对照 `main@f21f51d` 代码，不照抄文档草案）：

| 能力 | 后端状态 | 前端状态 |
| --- | --- | --- |
| 教师公告增删改查、发布、归档 | 已实现 `TeacherAnnouncementController` | 已接入 |
| 学生本班公告列表/详情 | 已实现 `StudentAnnouncementController` | 已接入 |
| 学生通知列表/详情/未读数/单条已读/全部已读 | **已实现** `StudentNoticeController`（PR #18 已合并） | 已接入，字段与 DTO 一致 |
| 公告发布 fan-out 通知 | **已实现** `AnnouncementNoticeFanoutService`（PR #20 已合并，唯一约束防重） | 通知列表可显示公告类型，按 `sourceId` 跳公告页签 |
| 教学班授权 | 端口仍只有测试用 Fake，**无生产实现**（生产接入前返回 503） | 依赖第 2 组公开契约，记为外部阻塞 |
| 教师公告权限串 | Controller 要求 `education:announcement:*`，`sql/` 无对应 `sys_menu` 种子 | 403 时页面直接提示缺失的权限串 |

## 2. 文件与职责

| 文件 | 职责 |
| --- | --- |
| `frontend/src/api/education/http.js` | 统一请求：附加 `Bearer` 令牌、按 `code` 判成败、401 清会话跳登录、错误归一为 `ApiError` |
| `frontend/src/api/education/announcement.js` | 教师 6 个 + 学生 2 个公告接口 |
| `frontend/src/api/education/notice.js` | 学生 5 个通知接口（已合并，字段与 `StudentNoticeResponseMapper` 一致） |
| `frontend/src/views/education/ui-kit.js` | 转义、本地时区时间格式化、状态标签、失败/空/加载态、分页、确认框、轻提示 |
| `frontend/src/views/education/view-host.js` | 菜单项到视图的注册与挂载、课程—教学班上下文、切换菜单时清理上一个视图 |
| `frontend/src/views/education/teacher/announcements.js` | 教师"班级消息 / 公告"：列表、筛选、分页、新建、编辑、发布、归档、详情 |
| `frontend/src/views/education/student/announcements.js` | 学生"课程公告"列表与详情（由通知页页签挂载） |
| `frontend/src/views/education/student/notices.js` | 学生"学习通知 / 课程公告"页：两个页签、通知筛选、标为已读、全部已读、未读数、顶栏角标 |
| `frontend/src/views/education/education-views.css` | 上述页面样式（浅色，主色 `#1890ff`，与三端运行页一致） |

页面挂载点（菜单数组下标）：教师 `(0,3)` 班级消息 / 公告；学生 `(0,3)` 学习通知 / 课程公告。未注册的菜单仍显示原"内容区（待实现）"占位，不伪造页面。

## 3. 接口对照

| 方法 | 路径 | 权限/角色 | 前端调用点 |
| --- | --- | --- | --- |
| GET | `/education/teacher/announcements?bindingId&status&pageNum&pageSize` | `education:announcement:list` | 列表、分页、筛选 |
| GET | `/education/teacher/announcements/{id}` | `education:announcement:list` | 详情、编辑前取正文 |
| POST | `/education/teacher/announcements` | `education:announcement:add` | 新建草稿 |
| PUT | `/education/teacher/announcements/{id}` | `education:announcement:edit` | 保存草稿 |
| POST | `/education/teacher/announcements/{id}/publish` | `education:announcement:publish` | 发布（二次确认） |
| POST | `/education/teacher/announcements/{id}/archive` | `education:announcement:archive` | 归档（二次确认） |
| GET | `/education/student/announcements?bindingId&pageNum&pageSize` | `student` 角色 | 学生公告列表 |
| GET | `/education/student/announcements/{id}` | `student` 角色 | 学生公告详情 |
| GET | `/education/student/notices?kind&bindingId&unreadOnly&pageNum&pageSize` | 契约 5.3 | 通知列表 |
| GET | `/education/student/notices/{id}` | 契约 5.4 | 通知详情 |
| GET | `/education/student/notices/unread-count?bindingId` | 契约 5.5 | 页内未读与顶栏角标 |
| POST | `/education/student/notices/{id}/read` | 契约 5.6 | 单条标为已读 |
| PUT | `/education/student/notices/read-all` | 契约 5.7 | 全部标为已读 |

字段约定：ID 全部按字符串处理，不做 `Number()` 转换；时间由后端输出 `yyyy-MM-ddTHH:mm:ss.SSSZ`，页面转本地时区展示；不传任何排序参数（排序由后端固定）。

## 4. 状态处理

| 场景 | 前端行为 |
| --- | --- |
| 未登录 / 令牌失效（401） | HTTP 401（含空正文、HTML 响应）与业务 `code=401` 统一处理：清 `sessionStorage` 登录态并跳 `/login.html`，不再渲染业务内容；并发 401 由跳转去重标记保证只跳一次 |
| 角色或无班级权限（403） | 显示"无权限"并清空区域，教师端额外提示缺失的权限串 |
| 对象不可见（404） | 统一提示"不存在或当前不可查看"（已退班、未开放、已归档等），**不从 404 推断路径未注册** |
| 状态冲突（409） | 提示"状态已变化，请刷新后重试"并刷新列表 |
| 服务端异常（500） | 展示通用失败与重试，不显示上一次的数据 |
| 服务不可用（503） | 分级提示：`msg` 含"授权"→"教学班授权服务暂不可用"（保留服务端原始 `msg`）；`msg` 明确含"尚未接入"→额外说明契约未落地；无授权证据→通用服务不可用 |
| 网络异常（code 0） | 提示网络错误并可重试 |
| 空数据（`total=0`、`rows=[]`） | 显示明确的空状态文案，不写成 0 值指标 |

此外：新建/编辑使用本地长度校验（标题 ≤ 200 字；正文 ≤ 20000 字且 UTF-8 ≤ 65535 字节），提交期间禁用按钮防重复；发布/归档必须二次确认；`closesAt` 留空显示"不关闭"。

## 5. 对其他组代码的改动（需评审）

| 文件 | 改动 | 原因 | 归属 |
| --- | --- | --- | --- |
| `backend/ruoyi-admin/pom.xml` | 资源 include 增加 `src/**` | `frontend/src` 原本不参与打包，新增脚本与样式无法随 JAR 发布 | 构建配置，需项目负责人确认 |
| `backend/ruoyi-framework/.../SecurityConfig.java` | GET `/src/**` 匿名放行 | 静态脚本/样式不应走鉴权 | 第 1 组公共权限，需 review |
| `frontend/teacher.html`、`student.html` | 引入样式与脚本、新增 `#viewRoot` 挂载点、菜单切换时挂载视图、学生顶栏未读角标 | 6-04 页面落地 | 第 1 组公共外壳，需 review |

未修改 `role-home.js`：登录态核验仍由原逻辑负责，视图通过轮询 `auth-pending` 类等待其完成。

## 6. 本地验证步骤（需先具备运行环境）

1. `mvn -B -pl ruoyi-admin -am package`，准备 `backend/.env`（MySQL 8 + Redis）。
2. 启动后以教师账号登录，进入"班级消息 / 公告"，填写演示库的 `bindingId`（`910000000001` 或 `910000000002`）后点"应用"。
3. 依次验证：新建草稿 → 编辑 → 发布 → 学生端可见 → 归档 → 学生端不可见。
4. 学生账号进入"学习通知 / 课程公告"：公告页签应看到本班 `OPEN` 公告；通知页签应看到本人可见通知、未读数与角标，无数据时显示空状态（不再提示"接口未就绪"）。
5. 越权用例：用未选课的 `student4`（`930000000104`，属 B 班）访问 A 班公告，应返回 404/403 而非内容。

## 7. 外部阻塞（2026-11-03 更新）

已解除：学生通知 5 个接口（PR #18）与公告 fan-out（PR #20）均已合并到 `main@f21f51d`，
`edu_user_notice` 已有完整读写与唯一约束防重，不再是阻塞项。

仍阻塞（均为外部依赖，前端不硬编码、不绕过权限）：

1. **教学班授权无生产实现**：公告/通知 Service 在缺少授权适配时按"授权不可用"拒绝（503，`msg` 含"授权"），需第 2 组公开契约。
2. **教师菜单权限缺失**：`education:announcement:*` 未写入 `sys_menu`，也未分配给教师角色，教师公告接口会返回 403。这是 6B 联调的第一阻塞项。
3. **`bindingId` 来源缺失**：2-04 课程列表接口未就绪，当前只能手工填写（不内置默认值）；等 1-03 公共外壳提供选择器后再替换。
4. 未做：6-01 总览聚合、6-03 总览页面、6-05/6-06（资讯、留言、系统通知、运行概况、过程分析）。

## 8. Mock 自测（2026-10-28 新增）

> **历史记录**：本节写作时"学生通知接口未就绪"，Mock 当时的用途是替代缺失接口做页面自测。
> 该状态已结束：通知 5 接口已随 PR #18 合并进 `main@f21f51d`。Mock 现仅保留两个用途——
> 失败态演练（403/503 分支）与前端回归自测，**不替代真实联调**，也不会在生产路径静默兜底。

后端接口未就绪期间，页面可用内置 Mock 自测，不依赖登录与后端：

- **预览页**：`frontend/mock-preview.html`，浏览器直接打开即可（无需服务）。
  - 默认教师公告页；`?role=student` 看学生通知/公告页；`?mock=0` 退出。
- **运行页开启**：`teacher.html` / `student.html` 加 `?mock=1`。Mock 标记写在
  `sessionStorage`，仅当前标签页有效，右下角有"Mock 数据"提示条与退出按钮。
- **实现**：`src/api/education/mock.js`。`http.js` 在 Mock 开启时把 `/education/*`
  业务请求全部路由到 Mock；未覆盖的路径直接报错，**绝不回落真实请求**，
  真实失败也不会被 Mock 兜底。
- **对齐范围**：字段、业务码（HTTP 200 + code）、状态机（发布补 `opensAt`、
  幂等归档、409 冲突）、分页与过滤规则均按 main 分支实现。
  特别 binding：`999999999999` → 403、`999999999998` → 503，用于测试失败态。
- **验证**：46 项行为断言全部通过（列表/详情/状态机/幂等/错误分支/开关），
  并用无头浏览器渲染教师端与学生端确认视觉正常。

Mock 只用于页面自测，不进入交付逻辑；联调仍以真实接口为准。

## 9. 给 6A 的接口需求单

`docs/6-02-6B-http-interface-request.md`：前端实际发出的全部 HTTP 请求清单
（A 教师/学生公告 8 个、B 学生通知 5 个均已合并；C 为外部阻塞；D 为 4 个问题的确认结论；E 为字段使用方式）。
6A 可直接据此核对，无需再向我要字段。

## 10. 学长反馈落实（2026-11-02）

> **历史记录**：下表是 2026-11-02 那一轮的处理快照，仅作追溯。其中"授权 503"一行的
> 文案（"第 2 组公开契约未接入"）已被本轮收口修订取代：当前 503 按证据分级提示
> （见第 4 节），不再凭 `msg` 含"授权"断定未接入。以第 4 节和第 11 节为准。

依据：学长 2026-10-28 反馈（通知 5 接口已在 PR #18 实现，4 个问题已答复，4 条语义修正）。

代码修改：

| 反馈项 | 修改 |
| --- | --- |
| `visibilityState` 缺失不得用 `status` 推算 | `ui-kit.statusBadge` 改为只认 `visibilityState`：缺失显示"未知状态"（warn 色）并 `console.error` 记录，不再回退 `status` |
| 授权 503 是预期安全拒绝 | `ui-kit.stateError` 为 `code=503` 增加独立文案："教学班授权服务暂不可用（第 2 组公开契约未接入）"，不再显示为"服务端异常，交给 6A 排查" |
| 学生公告 `bindingId` 空串语义 | Mock 与文档对齐：完全不传 = 本人全部班级；空串/纯空白 400。`http.js` 在 Mock 分支同样剔除空值参数，保证 Mock 与真实请求入参一致 |
| 重复发布/归档幂等 | Mock 对齐后端：重复发布/归档返回 200 与当前对象；非法转换（已归档再发布、草稿归档）才 409 |

文档修改：`6-02-6B-http-interface-request.md` 的 A-5/A-6（幂等）、A-7（bindingId）、A-8（404 统一语义）、
B（PR #18 状态）、D（4 个问题答复结论）、E（前端适配约定）均已更新。

验证：新增 16 项反馈回归断言全部通过；原有 46 项 Mock 行为断言全部通过；教师端页面无头渲染正常。

## 11. 与学长 PR #31 的收口对齐（2026-11-03）

基线：后端 `main@f21f51d`（含 PR #18 通知接口、#20 fan-out、#21 upstream-event-notice、#22 MySQL hardening）；
学长文档版本 `2765eb8`（PR #31）；`six` 已 merge `origin/main`，双方改动均保留（15 个提交领先）。

### 11.1 按 5 项确认逐条落实

| # | 学长要求 | 落实 |
| --- | --- | --- |
| 1 | 更新"通知接口未实现、fan-out 未实现"的旧说明 | `6-02-6B-http-interface-request.md` B 段与 D-3、本文档第 1/7/9 节、`notice.js`/`notices.js`/`mock.js` 注释、`frontend/README.md` 全部改为"已合并到 main@f21f51d" |
| 2 | 404 不统一提示 Controller 未部署；503 无明确授权证据时按通用服务不可用 | `ui-kit.stateError`：404 统一为"对象不存在或当前不可见"；503 仅当 `msg` 含"授权"才提示授权未接入，否则通用服务不可用；两种都保留服务端 `msg`、`code`、`method + url` |
| 3 | 单条已读仅在通知仍可见时返回原 `readAt`，退班/不可见可能 404 | `notices.js` 的 `markRead` 对 404 单独处理：提示"该通知已不可见"并刷新列表与未读数；文档 B-4 记录该语义 |
| 4 | `asOf`/`alreadyRead`/`operatedAt` 不要求展示，确认使用方式 | 三项均不展示、不做本地推算；标记成功后统一重拉列表与未读数，仅 `markedCount` 用于提示文案（见文档 E-1） |
| 5 | `bindingId` 来源与生产授权记为外部阻塞，不硬编码不绕过 | 文档 C-1/C-2 明确"不硬编码教学班 ID、不绕过权限、不内置免校验入口"；第 7 节改为外部阻塞清单 |

### 11.2 状态区分（按学长要求，勿混用）

| 结论 | 依据 |
| --- | --- |
| **源码核对一致**：13 个接口路径/参数/字段与 `main@f21f51d` 对应 | 逐文件比对 Controller、RequestParser、ResponseMapper |
| **真实联调未通过**：未在运行环境发起过任何一次真实请求 | 本机无 JDK/Maven/MySQL/Redis；授权契约未接入 |
| **未确认项**：教师菜单权限种子、生产授权契约、`bindingId` 来源 | 均为外部依赖，见第 7 节 |

本轮只改前端展示语义与文档，未改动任何后端代码；`ruoyi-admin/pom.xml` 与 `SecurityConfig` 的 `/src/**` 放行仍属第 1 组公共配置范围，需 review。
