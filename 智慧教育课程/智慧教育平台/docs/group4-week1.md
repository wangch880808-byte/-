# 第 4 组 · 第一周交付说明

> 课堂与同步跟课 · 首周目标：接口契约 + 前端占位页 + 联调准备

## 本周已完成

| 交付物 | 路径 | 说明 |
|---|---|---|
| 课堂接口契约草案 | [api-contracts-classroom.md](api-contracts-classroom.md) | 4-01/4-02 API 字段与验收场景 |
| 公共请求封装 | [../frontend/js/api-client.js](../frontend/js/api-client.js) | Bearer Token `fetch` 封装 |
| 课堂占位页逻辑 | [../frontend/js/classroom-pages.js](../frontend/js/classroom-pages.js) | 教师/学生课堂菜单占位 UI |
| 课堂样式 | [../frontend/classroom.css](../frontend/classroom.css) | 控制台、跟课页样式 |
| 投影公开页 | [../frontend/projection.html](../frontend/projection.html) | 独立全屏公开视图壳子 |
| 三端接入 | `teacher.html`、`student.html` | 点击课堂相关菜单显示占位页 |

**未包含（第 2 周起）：** 后端 Java 接口、数据库联调、WebSocket/SSE。

## 本地查看占位页

### 方式 A：仅看页面布局（无需后端）

用浏览器直接打开仓库内 `frontend/teacher.html` 或 `frontend/student.html` 时，登录校验会失败。请使用方式 B。

### 方式 B：完整三端（需后端）

1. 安装 Java 17、Maven、MySQL 8、Redis  
2. 复制 `backend/.env.example` → `backend/.env` 并填写数据库配置  
3. 按 [sql/README.md](../sql/README.md) 导入演示库（或向负责人索取已配置环境）  
4. 构建并启动：

```powershell
cd backend
mvn -B -pl ruoyi-admin -am package
.\run-local.ps1
```

5. 打开 http://127.0.0.1:8080/login.html ，教师账号 `teacher` / `teacher123`  
6. 菜单进入「今日课程 / 快捷上课」或「课堂教学」  
7. 学生账号 `student` / `student123` 进入「一键加入」或「同步课堂」  
8. 教师控制台点击「投影」可打开 `projection.html`

### 占位演示说明

- 课堂状态保存在浏览器 `sessionStorage`（键 `zhijiao.classroom.demoSession`）  
- 「开始上课」「翻页」「签到」等按钮可本地演示状态变化  
- 接口实现后将改为调用 `ZhijiaoApi` 请求服务端快照

## 学妹第一周练习清单

- [ ] 克隆仓库，用 Cursor 打开项目  
- [ ] 浏览器打开 `prototypes/console-navigation-v1/teacher.html` 和 `student.html`，找到课堂相关菜单  
- [ ] （可选）按方式 B 跑通登录  
- [ ] 教师端：开始上课 → 翻页 → 打开放射页  
- [ ] 学生端：一键加入 → 同步课堂 → 签到  
- [ ] 填写下方测试用例表并截图发给组长

## 测试用例表（首周 · 占位页）

| 编号 | 场景 | 操作 | 期望结果 | 实际 | 通过 |
|---|---|---|---|---|---|
| T1 | 正常 | 教师「开始上课」 | 状态变为进行中，按钮状态更新 | | |
| T2 | 正常 | 教师翻页 | 页码增减，投影页同步 | | |
| T3 | 正常 | 学生「一键加入」 | 跳转到同步课堂页 | | |
| T4 | 正常 | 学生签到 | 按钮变为已签到 | | |
| T5 | 空数据 | 未开课时学生加入 | 加入按钮禁用 | | |
| T6 | 边界 | 第 1 页点上一页 | 按钮禁用 | | |
| T7 | 边界 | 最后一页点下一页 | 按钮禁用 | | |

## 第 2 周计划（预览）

1. 在 `ruoyi-education/classroom` 实现 `POST /education/classroom/sessions` 与快照只读  
2. 占位页 `classroom-pages.js` 改为调用真实 API  
3. 与第 2 组确认 `bindingId` 与成员校验  
4. 与第 3 组确认课件 `resourceVersionId` 与预览 URL  
