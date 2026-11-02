# 第 4 组 · M0 交付说明

> 课堂与同步跟课（4-01～4-04）· 后端 + 前端已接真实 API，跨组依赖见 [group4-handoff-requests.md](group4-handoff-requests.md)

## 交付范围

| 任务 | 状态 | 说明 |
|------|------|------|
| 4-01 课堂会话 | ✅ | 开课/暂停/恢复/结束、翻页、快照、事件、加入校验 |
| 4-02 课堂活动 | ✅ 部分 | 签到、随堂题、公布结果；**课堂小作业待第 5 组** |
| 4-03 教师控制台 + 投影 | ✅ | `teacher.html` 课堂菜单 + `projection.html` |
| 4-04 学生跟课 | ✅ | `student.html` 一键加入 / 同步课堂 |

## 代码结构

```
backend/ruoyi-education/          # Java 业务模块
frontend/js/classroom-api.js      # API 封装
frontend/js/classroom-pages.js    # 三端课堂页面
frontend/classroom.css
frontend/projection.html
docs/api-contracts-classroom.md   # 接口契约
docs/group4-handoff-requests.md   # 跨组交接
```

## 本地运行

```powershell
cd backend
mvn -B -pl ruoyi-admin -am package
.\run-local.ps1
```

浏览器：`http://127.0.0.1:8080/login.html`

| 角色 | 账号 | 密码 |
|------|------|------|
| 教师 | teacher | teacher123 |
| 学生 | student | student123 |

## 演示路径

### 教师

1. 登录 → **今日课程 / 快捷上课** 或 **课堂教学**
2. 若库中已有进行中会话（种子数据），直接进入控制台
3. 否则点击 **开始上课**（需已发布课件）
4. **上一页 / 下一页** → 打开放射页
5. **发起签到 / 发随堂题 / 公布答案**

### 学生

1. 登录 → **正在上课 / 一键加入** → **一键加入**
2. **同步课堂** → 签到、作答随堂题
3. 页码每 3 秒与教师端同步

### 投影

教师控制台点击 **投影**，或访问：

`http://127.0.0.1:8080/projection.html?sessionId=<会话ID>`

## 主要 API

| 方法 | 路径 |
|------|------|
| GET | `/education/course/bindings/mine` |
| GET | `/education/course/bindings/{bindingId}/classroom-prep` |
| POST | `/education/classroom/sessions` |
| GET | `/education/classroom/sessions/active?bindingId=` |
| GET | `/education/classroom/sessions/{id}/snapshot?view=` |
| POST | `/education/classroom/sessions/{id}/join` |
| POST | `/education/activity/instances` |
| POST | `/education/activity/instances/{id}/checkin` |

完整列表见 [api-contracts-classroom.md](api-contracts-classroom.md)。

## 已知限制（M0）

- 实时同步为 **3 秒轮询**，非 WebSocket
- 课件预览走本地目录（`ZHIJIAO_PREVIEW_ROOT`），**第 3 组仍需提供正式预览服务或签名 URL**
- `CLASSWORK` 调用第 5 组占位，接口会返回「待第5组接入」
- 绑定/资源查询为临时 SQL，待第 2/3 组 HTTP API 替换

## 本轮已补齐（M0 收尾）

| 问题 | 处理 |
|---|---|
| 传 `?view=teacher` 可绕过任课校验，读到未公布答案 | `resolveView` 先校验 `isTeacherOfBinding`，非任课教师返回 `403` |
| `publish-result` 后答案仍不下发（条件写重） | 改为「教师视图 **或** 已到 `answerOpensAt`」才下含答案快照 |
| 并发翻页可能撞同一 `state_seq` | 改成 `state_seq = state_seq + 1` 原子自增后回读，再写事件 |
| 暂停/恢复/结束不写事件 | 新增 `SESSION_STATUS_CHANGED` 公开事件并递增 `state_seq` |
| `Idempotency-Key` 未实现 | `checkin` / `responses` 接入 `edu_idempotency_record`：同键同载荷回放、同键异载荷 `409`、24h 失效 |
| 预览 URL 指向不存在的 Controller（404） | 新增 `GET /education/resource/versions/{id}/pages/{n}/preview`，带权限校验与 `503` 说明 |
| 零测试 | 新增 12 个单元测试，覆盖 G2 的 6 条验收规则 |

验证方式：

```powershell
mvn -B -pl ruoyi-education -am test        # 12 tests, 0 failures
mvn -B -pl ruoyi-admin -am -DskipTests package
```

新增 SQL 语句已用 `sql/verify-v3-m0-invariants.sql` 同库 `zhijiao_m0` 实测（事务内执行后回滚），
未新增表结构，`edu_idempotency_record` 沿用原设计。

## M0 增补（统计与控制台体验）

| 项 | 说明 |
|---|---|
| 活动统计接口 | 新增 `GET /education/activity/instances/{id}/statistics`：教师随时可读，学生仅 `publish-result` 后可见，其他账号 403；只返回聚合数字 |
| 控制台进度 | 教师控制台活动列表显示「已签到 x/y」「已提交 x/y」，随 3 秒轮询刷新 |
| 断线重连 | 监听 `online` / `offline`：断网时显示提示条，恢复后立即补拉快照并重绘，页码与本人提交状态不丢 |
| 重复点击 | 按钮处理期间置 `busy` 标记并禁用、文案改为「处理中…」，防止同一次操作提交两遍 |

验证：`mvn -pl ruoyi-education -am test` → **167 tests, 0 failures**；
新增统计 SQL 已在 `zhijiao_m0` 实库执行验证（目标 3 / 已提交 1 / 答对 0）。

## 修改代码后

```powershell
mvn -B -pl ruoyi-admin -am package
.\run-local.ps1
```

浏览器 **Ctrl+F5** 强制刷新。
