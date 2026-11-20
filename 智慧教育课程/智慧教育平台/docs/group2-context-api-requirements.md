# 课程 / 教学班上下文：第 2 组接口需求

| 项 | 内容 |
|---|---|
| 版本 | 0.1（需求草案，未定稿） |
| 日期 | 2026-10-23 |
| 提出方 | 第 1 组（前端） |
| 接收方 | 第 2 组（2A 后端 / 2B 前端） |
| 状态 | 待 2A 确认字段与路径 |
| 关联任务 | 1-03（三端公共外壳与课程/教学班上下文）、G0 关口 |

> 本文是**接口需求清单**，不是最终契约。字段确认后由 2A 并入 [`api-contracts.md`](api-contracts.md)，以那份为准。本文只描述第 1 组前端为完成 1-03 所必需的最小集合。

---

## 1. 背景与现状

第 1 组前端已完成：

- 三端公共外壳 `frontend/shell/shell.js`（顶部栏、菜单、页签、面包屑、403 视图）；
- 统一请求客户端 `frontend/shell/api.js`（Bearer、15 秒超时、401 跳登录、403 派发无权限事件）；
- 业务页注册协议 `window.EDU_PAGES.push({ g, i, page })`，页面通过 `mount(host, ctx)` / `unmount()` 挂载；
- 管理员端三个页面（用户、角色、审计日志）作为协议的第一个真实使用者。

**唯一缺口**：`EduShell.getContext()` 目前返回 `null`，`ctx` 参数已透传给每个页面但没有数据。没有它，教师端「课程建设 / 课前准备 / 课堂教学」和学生端「课程资料 / 同步课堂 / 课后学习」都无法确定操作对象。

第 2 组负责对象：学期、教学班、成员、课程与授课授权（任务 2-01、2-02）。现有建表脚本中已经具备完整结构，接口直接在其上实现即可，**不需要新增表**。

| 表 | 关键字段 | 用途 |
|---|---|---|
| `edu_term` | `id`, `organization_id`, `code`, `name`, `starts_on`, `ends_on` | 当前学期 |
| `edu_course_space` | `id`, `organization_id`, `owner_user_id`, `title`, `status`（`DRAFT`/`ACTIVE`/`ARCHIVED`） | 课程空间（“我的课程”的课程） |
| `edu_teaching_class` | `id`, `organization_id`, `term_id`, `code`, `name`, `class_kind`（`FORMAL`/`TRIAL`）, `status` | 教学班 |
| `edu_course_class_binding` | `teaching_class_id`, `course_space_id`, `published_at`, `ended_at` | 课程空间 ↔ 教学班，`ended_at IS NULL` 为有效绑定 |
| `edu_class_teacher` | `teaching_class_id`, `user_id`, `teacher_role`（`LEAD`/`CO_TEACHER`）, `can_publish`, `can_grade`, `valid_from`, `valid_to` | 教师任课与授权 |
| `edu_class_student` | `teaching_class_id`, `user_id`, `joined_at`, `left_at` | 学生入班，`left_at IS NULL` 为在班 |

**关系要点**：一个课程空间可以绑定多个教学班；一个教学班通过有效绑定关联到一个课程空间。因此上下文选择器必须是「课程 → 教学班」两级，不能只给班级列表。

---

## 2. 前端接入点

| 位置 | 现状 | 接口到位后 |
|---|---|---|
| `EduShell.getContext()` | 返回 `null` | 返回当前 `{ courseSpaceId, classId, ... }` |
| `page.mount(host, ctx)` | `ctx` 为 `null` | `ctx` 即上下文对象 |
| 顶栏 | 只有写死的「当前学期：2026-2027-1」 | 改为课程 / 教学班选择器 + 真实学期 |
| `EduApi` | 已可用 | 上下文接口直接走它 |

---

## 3. 接口 A（最小可用）：上下文选项

**`GET /education/context/options`**

用途：渲染「课程 → 教学班」选择器，并确定当前学期。

### 请求

无参数（身份从令牌解析）。不分页，一次返回全量。

### 响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "term": { "termId": 1, "termCode": "2026-2027-1", "termName": "2026-2027-1" },
    "courses": [
      {
        "courseSpaceId": 1001,
        "title": "数据结构",
        "status": "ACTIVE",
        "classes": [
          {
            "classId": 2001,
            "className": "数据结构-A班",
            "classCode": "DS-2026-A",
            "termId": 1,
            "memberRole": "teacher",
            "teacherRole": "LEAD",
            "canPublish": true,
            "canGrade": true
          }
        ]
      }
    ]
  }
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `term` | object | 是 | 当前学期；无当前学期时为 `null` |
| `courses[].courseSpaceId` | number/string | 是 | 课程空间 ID（见第 5 节 ID 约定） |
| `courses[].title` | string | 是 | 课程名称 |
| `courses[].status` | string | 是 | `DRAFT`/`ACTIVE`/`ARCHIVED` |
| `courses[].classes[]` | array | 是 | 该课程下当前用户可见的教学班，无则为 `[]` |
| `classes[].classId` | number/string | 是 | 教学班 ID |
| `classes[].className` | string | 是 | 教学班名称 |
| `classes[].classCode` | string | 是 | 教学班编码 |
| `classes[].termId` | number/string | 是 | 所属学期 |
| `classes[].memberRole` | string | 是 | `teacher` / `student` |
| `classes[].teacherRole` | string | 否 | `LEAD`/`CO_TEACHER`，仅教师 |
| `classes[].canPublish` | boolean | 否 | 仅教师；前端据此隐藏/禁用发布类按钮（**服务端仍须校验**） |
| `classes[].canGrade` | boolean | 否 | 仅教师 |

### 必须遵守的过滤语义

| 场景 | 要求 |
|---|---|
| 教师 | 仅返回 `valid_from <= now` 且（`valid_to IS NULL` 或 `valid_to > now`）的班级 |
| 学生 | 仅返回 `left_at IS NULL` 的班级 |
| 教学班 | 仅 `status = 'ACTIVE'`（`DRAFT` / `ARCHIVED` 不出现） |
| 绑定 | 仅 `edu_course_class_binding.ended_at IS NULL` |
| 组织 | 仅本组织范围，按若依数据权限裁剪 |
| 无数据 | `200` + `courses: []`，**不得返回 404/500** |
| 缓存 | `Cache-Control: no-store`，权限变化后立即生效 |

### 错误

| 场景 | 期望 |
|---|---|
| 未登录 / 令牌失效 | `401`（若依返回 HTTP 200 + `code: 401` 亦可，与现有约定一致） |
| 服务端异常 | `500` + `msg` |

---

## 4. 接口 B：深链接上下文校验

**`GET /education/context/check?classId={classId}`**

用途：页面 URL 带 `classId`（刷新、收藏、从别处跳转）时，确认该班当前仍有效，避免用过期上下文渲染页面。

### 响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "courseSpaceId": 1001,
    "classId": 2001,
    "termId": 1,
    "memberRole": "teacher",
    "teacherRole": "LEAD"
  }
}
```

### 失败语义

| 场景 | 期望 |
|---|---|
| 已撤权 / 非成员 / 班级归档 / 绑定结束 | `403` |
| 班级不存在 | `403` 或 `404`，请统一并写入契约；**建议统一 403，避免被枚举** |
| 未登录 | `401` |

前端收到 403 后的行为：清除本地上下文 → 回到「未选择课程」状态 → 提示「该课程/班级已不可用」，**不清除登录态**。

---

## 5. 需要明确的约定

| # | 问题 | 需要的答案 |
|---|---|---|
| 1 | “我的课程”是 `course_space` 还是 `teaching_class`？ | 确认选择器为「课程 → 班级」两级；确认一个课程空间可绑定多个班 |
| 2 | ID 类型 | 统一 JSON number 或统一 string。若用 number，需保证不超过 JS 安全整数 `2^53`；不要混用 |
| 3 | 时间格式 | 表内为 UTC `DATETIME(3)`，若依默认 `yyyy-MM-dd HH:mm:ss`（GMT+8）。契约中明确采用哪种、是否带时区 |
| 4 | 枚举 | `status` / `class_kind` / `teacher_role` 均为大写英文；请提供中文映射或确认由前端内置 |
| 5 | 一个班绑定多个课程空间 | 现有唯一键允许多个有效绑定；业务上是否允许？上下文如何呈现？ |
| 6 | 撤权后的在途请求 | 服务端每次请求实时校验，不依赖登录时快照 |
| 7 | 当前学期判定 | `starts_on <= today <= ends_on`？多个学期重叠或都过期时怎么办？ |
| 8 | 教务管理员 | 受限教务管理员访问该接口时返回什么（空列表 / 全部 / 403）？ |

---

## 6. 权限规则（请写入接口文档，供 1-01 越权审查）

1. 教师仅能看到 `edu_class_teacher` 中本人有效记录对应的班级；
2. 学生仅能看到 `edu_class_student` 中本人 `left_at IS NULL` 的班级；
3. 班级必须 `status = 'ACTIVE'`，绑定必须 `ended_at IS NULL`；
4. 跨组织不可见；
5. 撤权、调班、归档后，下一次请求立即拒绝，不能等令牌过期；
6. 403 返回体不得包含班级名称、课程名称等对象信息（避免通过错误信息泄露存在性）。

---

## 7. 验收用例

| # | 场景 | 期望 |
|---|---|---|
| 1 | 教师 A 登录 | 只返回 A 任教的班级 |
| 2 | 教师 B 登录 | 看不到 A 的班 |
| 3 | 学生甲在 A 班 | 返回 A 班，不返回 B 班 |
| 4 | 学生甲被移出 A 班（`left_at` 有值） | 立即不再返回；`check?classId=A` 返回 403 |
| 5 | 教师 A 的任课记录 `valid_to` 已过 | 不返回该班 |
| 6 | 班级 `status='ARCHIVED'` | 不返回 |
| 7 | 绑定 `ended_at` 有值 | 不返回 |
| 8 | 无任何课程的新账号 | `200` + `courses: []` |
| 9 | 未登录访问 | 401 |
| 10 | 伪造 `classId` 调 check（他人的班） | 403，且不泄露班级是否存在 |

以上每条都需要「正常 / 空数据 / 无权限 / 失败」四类验证，与项目验收要求一致。

---

## 8. 接口未就绪时的替代方案

| 方案 | 说明 | 推荐度 |
|---|---|---|
| A. 只读种子数据接口 | 直接读 `zhijiao_m0` 现有表，权限逻辑后补 | 推荐：能顺带验证权限规则 |
| B. 静态 JSON（`frontend/mock/context.json`） | 仅开发环境读取，接口上线后删除 | 备选：只能验证 UI |

不建议为了让页面“看起来能用”而写死假数据进正式代码路径。

---

## 9. 接口到位后第 1 组会做的事

1. 新增 `frontend/shell/context.js`：选择器、`sessionStorage` 持久化、`EduContext.get/set/onChange`；
2. `EduShell.getContext()` 返回真实上下文，`ctx` 透传给所有业务页；
3. 切换上下文时：`EduApi.cancelAll()` 中止在途请求 → 清理页签与页面缓存 → 通知当前页重载 → URL 同步 `classId`；
4. 深链接校验失败回退到选择页；
5. 覆盖第 7 节 10 条用例的浏览器回归；
6. 同步更新 `frontend/README.md` 的上下文说明。

---

## 10. 对 2B（第 2 组前端）的配合要求

1. **不要另建上下文选择器**：选择器归第 1 组外壳，2B 的「我的课程」页面通过 `EduShell.getContext()` 取当前课程/班级，避免两套状态；
2. **页面跳转带 `classId`**：从课程列表进入具体教学班时把 `classId` 写进 URL，否则刷新后上下文丢失；
3. **课程/班级列表页与选择器复用同一接口**：2-04 的「我的课程」可以直接复用 `/education/context/options`，避免两套查询口径不一致；
4. 如果 2B 需要「课程详情」接口，另行提出，不要塞进上下文接口。

---

## 11. 待确认清单（2A 回复时逐条勾选）

- [ ] 接口路径与命名是否按本文（`/education/context/options`、`/education/context/check`）
- [ ] 是否采用「课程 → 教学班」两级结构
- [ ] ID 统一为 number 还是 string
- [ ] 时间格式
- [ ] 枚举中文映射由谁提供
- [ ] 一个班多绑定的业务规则
- [ ] 当前学期判定规则
- [ ] 教务管理员在该接口下的行为
- [ ] 403 统一语义（不区分不存在 / 无权）
- [ ] 预计可联调时间（G0 前置）

---

## 12. 附：对 G0 关口的影响

按分工文档，G0 要求「1-03 接入受保护的业务页入口；2-01 确定课程、教学班、成员 ID 与撤权规则」。本文即 1-03 对 2-01 的依赖清单：**接口 A 到位即可解除 1-03 的主要阻塞**，接口 B 影响深链接与刷新体验。
