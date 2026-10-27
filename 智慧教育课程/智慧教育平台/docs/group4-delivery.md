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
- 课件预览 URL 为占位路径，**第 3 组需提供真实预览服务**
- `CLASSWORK` 调用第 5 组占位，接口会返回「待第5组接入」
- `Idempotency-Key` 请求头尚未实现
- 绑定/资源查询为临时 SQL，待第 2/3 组 HTTP API 替换

## 修改代码后

```powershell
mvn -B -pl ruoyi-admin -am package
.\run-local.ps1
```

浏览器 **Ctrl+F5** 强制刷新。
