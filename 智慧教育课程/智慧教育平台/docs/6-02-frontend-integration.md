# 6-02 前端接口接入说明（6-04 交付）

> 版本：0.1｜日期：2026-10-28｜负责人：6B｜分支：`six`
>
> 状态：教师公告（6 个接口）与学生公告（2 个接口）已按后端 `main` 的 Controller 接入；
> 学生通知（5 个接口）后端尚未实现，前端已按契约接好调用并显示"接口未就绪"；
> **尚未完成真实联调**，本机无 JDK／Maven／MySQL／Redis 运行环境。

## 1. 范围

本次只覆盖 `docs/分工.md` 的 6-04：教师发布班级公告、学生查看本班公告与本人通知。6-01 三端总览、6-03 总览页面、6-05/6-06 留待后续。

事实基线（对照 `main` 分支代码，不照抄文档草案）：

| 能力 | 后端状态 | 前端状态 |
| --- | --- | --- |
| 教师公告增删改查、发布、归档 | 已实现 `TeacherAnnouncementController` | 已接入 |
| 学生本班公告列表/详情 | 已实现 `StudentAnnouncementController` | 已接入 |
| 学生通知列表/详情/未读数/单条已读/全部已读 | **未实现**（无 `StudentNoticeController`） | 已按契约接入，失败显式提示 |
| 教学班授权 | `TeachingClassAuthorizationPort`、`StudentAnnouncementAuthorizationPort` 仍只有测试用 Fake，**无生产实现** | 依赖第 2 组 2-01 |
| 教师公告权限串 | Controller 要求 `education:announcement:*`，`sql/` 无对应 `sys_menu` 种子 | 403 时页面直接提示缺失的权限串 |

## 2. 文件与职责

| 文件 | 职责 |
| --- | --- |
| `frontend/src/api/education/http.js` | 统一请求：附加 `Bearer` 令牌、按 `code` 判成败、401 清会话跳登录、错误归一为 `ApiError` |
| `frontend/src/api/education/announcement.js` | 教师 6 个 + 学生 2 个公告接口 |
| `frontend/src/api/education/notice.js` | 学生 5 个通知接口（后端待实现） |
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
| 未登录 / 令牌失效（401） | 清 `sessionStorage` 登录态并跳 `/login.html`，不再渲染业务内容 |
| 角色或无班级权限（403） | 显示"无权限"并清空区域，教师端额外提示缺失的权限串 |
| 对象不可见 / 路径未注册（404） | 统一提示"不存在或当前不可查看"；路径未注册时提示先同步 `main` |
| 状态冲突（409） | 提示"状态已变化，请刷新后重试"并刷新列表 |
| 服务端异常（500/503） | 展示通用失败与重试，不显示上一次的数据 |
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
4. 学生账号进入"学习通知 / 课程公告"：公告页签应看到本班 `OPEN` 公告；通知页签当前预期显示"接口未就绪"。
5. 越权用例：用未选课的 `student4`（`930000000104`，属 B 班）访问 A 班公告，应返回 404/403 而非内容。

## 7. 未完成与阻塞

1. **学生通知后端未实现**：`edu_user_notice` 仍只有 DDL，需要 6A 补 `StudentNoticeController` 与 `NoticeService`。
2. **教学班授权无生产实现**：公告 Service 在缺少授权适配时按"授权不可用"拒绝，真实请求会失败，需第 2 组 2-01 提供成员与任课查询。
3. **教师菜单权限缺失**：`education:announcement:*` 未写入 `sys_menu`，也未分配给教师角色，所有教师公告接口会返回 403。这是 6B 联调的第一阻塞项。
4. **`bindingId` 来源缺失**：2-04 课程列表接口未就绪，当前只能手工填写；上下文切换后需要清理旧数据，等 1-03 公共外壳提供选择器后再替换。
5. 未做：6-01 总览聚合、6-03 总览页面、6-05/6-06（资讯、留言、系统通知、运行概况、过程分析）。

## 8. Mock 自测（2026-10-28 新增）

后端通知接口未就绪期间，页面可用内置 Mock 自测，不依赖登录与后端：

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
（A 已合并 8 个、B 待实现通知 5 个、C 缺失的上下文接口、D 需确认的 4 件事）。
6A 可直接据此实现与自测，无需再向我要字段。
