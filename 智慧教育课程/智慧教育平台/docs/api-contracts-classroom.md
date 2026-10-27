# 课堂与活动接口契约（第 4 组 · 草案 v0.1）

> 状态：**M0 已实现（轮询同步）** · 跨组占位见 [group4-handoff-requests.md](group4-handoff-requests.md)  
> 负责人：第 4 组  
> 关联任务：4-01（课堂会话）、4-02（签到与随堂题）、4-03/4-04（三端页面）  
> 数据库表：见 `sql/mysql-v3-m0-teaching-schema.sql` 中 `edu_class_session`、`edu_session_projection`、`edu_session_event`、`edu_activity_instance` 等

本文是第 4 组首周交付的接口草案，供第 2、3、5、6 组评审字段与权限边界。实现前须与 [接口协作约定](api-contracts.md) 一并阅读。

## 1. 通用约定

- **命名空间**：`/education/classroom`、`/education/activity`
- **认证**：沿用若依 `Authorization: Bearer <token>`；身份从服务端上下文读取，不信任前端传入的 `userId`。
- **响应包装**：沿用若依 `AjaxResult`（`code`、`msg`、`data`）；列表分页沿用 `TableDataInfo`。
- **时间**：ISO-8601 字符串，UTC 或带时区偏移；客户端倒计时仅展示，截止以服务端为准。
- **幂等**：写操作支持请求头 `Idempotency-Key`（UUID，24h 内同键同载荷返回相同结果）。
- **对象范围**：所有接口须校验 `binding_id`（课程-教学班绑定）及成员资格（第 2 组 `course` 契约）。

### 1.1 会话状态（`edu_class_session.status`）

| 值 | 含义 |
|---|---|
| `PLANNED` | 已创建未开课 |
| `RUNNING` | 进行中 |
| `PAUSED` | 暂停（仍占用「每班唯一进行中会话」槽位） |
| `ENDED` | 已结束 |

同一 `binding_id` 在 `RUNNING`/`PAUSED` 时最多一条会话（由 `uq_edu_session_active_binding` 保证）。

### 1.2 公开载荷裁剪

| 视图 | 可见内容 |
|---|---|
| 教师控制台 | 完整快照、进行中活动、学生名单统计、未公布答案 |
| 公开投影 | 课件页码、公开活动题干（无答案）、倒计时、已公布统计 |
| 学生跟课 | 同投影 + 本人签到/作答状态 |

`edu_session_event.visibility = TEACHER` 的事件不得进入公开/学生接口。

---

## 2. 课堂会话（4-01）

### 2.1 创建并开始课堂

`POST /education/classroom/sessions`

| 角色 | 教师（须为 `binding_id` 任课教师） |
|---|---|

**请求体**

```json
{
  "bindingId": 1001,
  "lessonId": null,
  "planId": null,
  "releaseId": 2001,
  "resourceVersionId": 3001,
  "initialPageNo": 1
}
```

**成功 `data`**

```json
{
  "sessionId": 5001,
  "bindingId": 1001,
  "status": "RUNNING",
  "stateSeq": 1,
  "startedAt": "2026-10-27T09:00:00.000Z"
}
```

**错误**：`403` 无任课权限；`409` 该班已有进行中会话；`422` 资源未发布或版本无效。

---

### 2.2 查询会话（教师）

`GET /education/classroom/sessions/{sessionId}`

返回会话元数据、当前投影、进行中活动摘要（含 `activityId`、`activityKind`、`status`）。

---

### 2.3 暂停 / 恢复 / 结束

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/education/classroom/sessions/{sessionId}/pause` | `RUNNING` → `PAUSED` |
| `POST` | `/education/classroom/sessions/{sessionId}/resume` | `PAUSED` → `RUNNING` |
| `POST` | `/education/classroom/sessions/{sessionId}/end` | → `ENDED`，幂等 |

结束请求可重复提交；已 `ENDED` 时返回当前终态，不报错。

---

### 2.4 翻页

`POST /education/classroom/sessions/{sessionId}/page`

```json
{
  "pageNo": 3,
  "studentCanBrowse": false
}
```

递增 `edu_session_projection.state_seq`，写入 `edu_session_event`（`event_kind = PAGE_CHANGED`，`visibility = PUBLIC`）。

---

### 2.5 快照（重连入口）

`GET /education/classroom/sessions/{sessionId}/snapshot`

| 查询参数 | 说明 |
|---|---|
| `view` | `teacher` \| `public` \| `student`（默认按角色推断） |

**公开/学生 `data` 示例**

```json
{
  "sessionId": 5001,
  "stateSeq": 12,
  "status": "RUNNING",
  "pageNo": 3,
  "resourceVersionId": 3001,
  "previewUrl": "/education/resource/versions/3001/pages/3/preview",
  "serverTime": "2026-10-27T09:15:00.000Z",
  "openActivity": {
    "activityId": 6001,
    "activityKind": "ATTENDANCE",
    "status": "OPEN",
    "closesAt": "2026-10-27T09:20:00.000Z"
  },
  "myParticipation": {
    "checkedIn": true,
    "lastResponseId": null
  }
}
```

`myParticipation` 仅 `view=student` 时返回。`openActivity` 未公布答案时不含 `correctOption` 等字段。

---

### 2.6 事件补查

`GET /education/classroom/sessions/{sessionId}/events?afterSeq={n}&view=public`

按 `seq_no` 升序返回 `afterSeq` 之后的事件；客户端丢弃 `seq_no` 小于本地已应用值的更新。

---

### 2.7 学生加入校验

`POST /education/classroom/sessions/{sessionId}/join`

学生进入跟课页前调用。成功返回与 2.5 相同结构快照；`403` 非本班或已退班；`404` 会话不存在或已结束（可配置为返回终态快照）。

---

## 3. 活动（4-02）

### 3.1 发起签到

`POST /education/activity/instances`

```json
{
  "sessionId": 5001,
  "bindingId": 1001,
  "activityKind": "ATTENDANCE",
  "title": "课堂签到",
  "opensAt": "2026-10-27T09:05:00.000Z",
  "closesAt": "2026-10-27T09:15:00.000Z"
}
```

同一 `binding_id` 同时仅允许一个 `OPEN` 的 `ATTENDANCE`（`uq_edu_activity_active_kind`）。

### 3.2 发起随堂题

`activityKind`: `QUIZ`；须带 `questionId`、`questionVersionId`。公开载荷仅含题干与选项，不含答案。

### 3.3 学生签到

`POST /education/activity/instances/{activityId}/checkin`

幂等：同一学生重复签到返回已有记录，不新增有效签到。

### 3.4 学生作答

`POST /education/activity/instances/{activityId}/responses`

```json
{
  "answers": [{ "itemKey": "q1", "value": "B" }]
}
```

截止后 `422`；重复提交按契约返回当前有效版本或冲突码。

### 3.5 关闭与公布

| 方法 | 路径 |
|---|---|
| `POST` | `/education/activity/instances/{activityId}/close` |
| `POST` | `/education/activity/instances/{activityId}/publish-result` |

未调用 `publish-result` 前，统计与答案不得出现在 `view=public|student` 快照中。

### 3.6 课堂小作业

通过 `activityKind = CLASSWORK` 创建，提交走第 5 组 `/education/assignment/*` 统一引擎（单独评审关联字段）。

---

### 2.8 查询教学班当前活跃会话

`GET /education/classroom/sessions/active?bindingId={bindingId}`

教师或本班学生可调用。无活跃会话时 `data` 为 `null`。

---

### 2.9 开课准备（临时，待第 3 组替换）

`GET /education/course/bindings/{bindingId}/classroom-prep`

仅任课教师。返回默认 `releaseId`、`resourceVersionId`、`totalPages`、可选 `lessonId`/`planId`/默认题目 ID。

---

## 4. 与上游组的依赖

| 提供方 | 第 4 组需要 | 用途 |
|---|---|---|
| 第 2 组 `course` | `bindingId`、教学班成员校验 API | 加入课堂、活动目标人数 |
| 第 3 组 `resource` | 已发布 `releaseId` + `resourceVersionId` + 页预览 URL | 翻页与投影 |
| 第 5 组 `assignment` | 课堂小作业提交契约 | CLASSWORK 活动 |
| 第 1 组 | 统一 403/401 响应 | 前端错误处理 |

---

## 5. 验收场景（G2 最小集）

1. 教师开课后翻页，投影与学生快照 `pageNo` 一致。  
2. 学生迟加入，拉快照恢复到最新页。  
3. 外班学生 `join` 返回 403。  
4. 重复签到不增加有效记录。  
5. 未公布答案时，公开快照无 `correctOption`。  
6. 断网重连：先 `snapshot` 再 `events?afterSeq=`，不丢失已提交作答。

---

## 6. 实现顺序建议（第 2 周起）

1. `sessions` CRUD + 快照只读（可先轮询，后换 WebSocket/SSE）  
2. 翻页 + `state_seq`  
3. 签到活动  
4. 随堂题 + 公布结果  
5. 与第 5 组课堂小作业对接  

实现与交付说明见 [group4-delivery.md](group4-delivery.md)。
