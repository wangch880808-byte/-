# 第 4 组 · 跨组交接清单（占位接口说明）

> 本文列出第四组 M0 依赖其它小组的字段与接口。当前实现中，第 4 组用 **直连数据库的 Port 占位** 或 **临时 REST 接口** 代替，待对方正式 API 就绪后替换。

## 1. 第 2 组（课程 / 教学班 / 成员）

| 需求 | 用途 | 当前占位 | 待替换为 |
|------|------|----------|----------|
| `bindingId` 与教学班摘要 | 教师选课、学生选课列表 | `GET /education/course/bindings/mine` | 第 2 组 `course` 契约中的绑定列表 API |
| 任课教师校验 | 开课、翻页、发起活动 | `CourseAccessPort` → `edu_class_teacher` | `GET /education/course/bindings/{id}/my-role` 或等价 |
| 本班学生校验 | 加入课堂、签到、作答 | `CourseAccessPort` → `edu_class_student` | 第 2 组成员校验 API |
| 撤权 / 退班 | G2 验收：外班 403 | SQL `left_at` / `valid_to` 判断 | 第 2 组撤权事件或实时校验 API |

**替换位置（代码）：**

- `backend/ruoyi-education/.../port/CourseAccessPort.java`
- `backend/ruoyi-education/.../port/impl/CourseAccessPortImpl.java`
- `backend/ruoyi-education/.../controller/EducationCourseController.java`（临时 `/mine`）

---

## 2. 第 3 组（资源 / 课件 / 预览）

| 需求 | 用途 | 当前占位 | 待替换为 |
|------|------|----------|----------|
| 已发布 `releaseId` + `resourceVersionId` | 开课选课件 | `GET /education/course/bindings/{id}/classroom-prep` | 第 3 组按班发布列表 API |
| 页码范围 / 总页数 | 翻页边界 | `ResourceAccessPort.countPages` | 第 3 组资源版本元数据 |
| 页预览 URL | 投影 / 学生跟页 | 路径 `/education/resource/versions/{id}/pages/{n}/preview`（**无实际 Controller**） | 第 3 组预览流或签名 URL API |
| 题目公开快照（无答案） | 随堂题展示 | `ResourceAccessPort.getQuestionPublicSnapshot` | 第 3 组题库只读 API |

**替换位置：**

- `backend/ruoyi-education/.../port/ResourceAccessPort.java`
- `backend/ruoyi-education/.../port/impl/ResourceAccessPortImpl.java`
- `EducationCourseServiceImpl.getClassroomPrep`

---

## 3. 第 5 组（统一作业 / 提交）

| 需求 | 用途 | 当前占位 | 待替换为 |
|------|------|----------|----------|
| 课堂小作业 `CLASSWORK` 创建与提交 | 4-02 小作业 | `AssignmentBridgePort` **抛出**「待第5组接入」 | 第 5 组 `5-01` `/education/assignment/*` |
| 预习 / 课后作业与课堂活动隔离 | 数据不串班 | 未实现 | 第 5 组任务目标与 `bindingId` 规则 |

**替换位置：**

- `backend/ruoyi-education/.../port/AssignmentBridgePort.java`
- `backend/ruoyi-education/.../port/impl/AssignmentBridgePortImpl.java`
- `ActivityController` → `POST .../classwork`

---

## 4. 第 1 组（身份 / 网关）

| 需求 | 状态 |
|------|------|
| Bearer Token 登录 | ✅ 已用若依现有登录 |
| 401 / 403 统一响应 | ✅ 沿用 `AjaxResult` |
| WebSocket / SSE 推送 | ❌ M0 用轮询；待 1 组或 4 组二期选型 |

---

## 5. 第 6 组（总览）

| 需求 | 说明 |
|------|------|
| 教师「今日课程 / 快捷上课」入口数据 | 6-03 可消费 `GET /education/classroom/sessions/active` |
| 学生「正在上课」入口 | 同上，按 `bindingId` 查活跃会话 |

---

## 6. 联调前请对方提供

1. **Swagger 或 Markdown 契约**（最终 URL、请求/响应示例）
2. **演示账号**与 `bindingId` 对照表
3. **错误码表**（403 外班、409 冲突、422 业务校验）
4. **联调环境地址**（或约定本地共用 `zhijiao_m0` 种子数据）

---

## 7. G2 端到端验收分工

| 步骤 | 负责组 |
|------|--------|
| 教师建课、发布课件 | 第 2 + 3 组 |
| 教师开课、翻页、投影 | **第 4 组** |
| 学生加入、跟页、签到、答题 | **第 4 组** + 第 2 成员校验 |
| 课堂小作业提交 | 第 4 + **第 5 组** |

当前可在本地用演示库 **独立演示第 4 组页面与 API**；完整 G2 需上表接口替换占位后联调。
