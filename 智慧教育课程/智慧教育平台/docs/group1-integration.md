# 第一组公共接口与页面接入

## 可信身份和统一拒绝

GET /system/identity/current 仅返回当前认证用户的 userId、deptId（字符串）与有效角色列表。接口没有可供调用方指定身份的请求参数。组织标识是若依部门 ID，不等于教学班成员资格。

业务服务使用：
```java
Long actorId = SecurityUtils.getUserId();
Long deptId = SecurityUtils.getDeptId();
ScopeAssert.requireOwner(authorizationService.mayRead(actorId, objectId));
```
authorizationService 由对应业务组提供，必须校验对象与组织、课程、教学班及成员有效期；不能只检查 teacher/student 角色。ScopeAssert 失败统一返回业务 code=403。身份失效为 401。HTTP 200 也可能携带业务拒绝码，客户端必须同时检查。系统错误不应携带 SQL、私有答案或对象名称。

## Vue 页面注册

在实际业务入口模块中调用 education/ui-pages.js 导出的 registerBusinessPage，并在 main.js 导入该模块。也可由已有脚本调用 window.EduVue.registerPage。仅注册实际已实现的组件。

```js
registerBusinessPage({
  id: 'course-list',
  path: 'courses',
  title: '我的课程',
  group: '课程',
  roles: ['teacher'],
  permission: 'education:course:list',
  serviceRoute: 'education/course',
  requiresContext: 'course',
  component: () => import('../views/education/CourseList.vue')
})
```

示例中的业务组件由第 2 组提供，示例不表示它在本包中存在。id/path 必须唯一；重复注册拒绝。requiresContext 可省略，或为 course/class；缺少所需上下文时不挂载组件。serviceRoute 必须匹配 getRouters 的路径或组件（允许末尾 /index）。roles 约束当前登录入口，即使多角色账号也不会混入另一端页面。

业务组件可使用 composables/useBusinessPage.js 的 useBusinessPage()：
- getContext() 取得只读快照；context 提供只读响应状态。
- get(path, options) 自动带当前页面取消信号；signal 可交给文件请求或订阅。
- 异步写入视图前检查 isCurrent()；unmount 时关闭业务自建连接和定时器。
- 每次业务请求仍由服务端重新校验对象权限，不能依赖前端 canPublish/canGrade。

## 教学上下文契约

默认 GET /education/context/options 和 GET /education/context/check 由 2A 实现，可通过 contexts.configure 修改为团队确认的路径。以下是前端适配所消费的字段，不代表 2A 已冻结协议。

options 的 data：
```json
{
  "term": {"termId":"3","termName":"当前学期"},
  "courses":[{"courseSpaceId":"11","title":"课程名称","classes":[
    {"classId":"12","termId":"3","bindingId":"13","className":"教学班名称","memberRole":"teacher"}
  ]}]
}
```

check 查询包含 courseSpaceId、classId，以及 options 已给出的 bindingId。响应 data 应返回同一 courseSpaceId/classId/termId/bindingId/memberRole，以及最新 canPublish/canGrade 布尔值。学生端操作标志始终关闭；字段缺失不能沿用旧授权。绑定不一致或撤权应返回 403，不能自动换成其他绑定。

ID 必须是正整数字符串或安全整数；推荐字符串，避免 JavaScript 精度丢失。重复课程和同角色重复教学班拒绝。只提供 classId 且存在多课程绑定时，前端要求用户重新选择。上下文持久化仅保存 ID，并且恢复时再次向服务端校验。

空选项显示空状态；接口缺失显示服务未就绪；普通失败显示重试；403 清空上下文。管理员不请求教学上下文接口。

## 顶部通知协作

第 6 组在已经注册并获授权的通知页面增加：
```js
headerEntry: {
  label: '通知',
  async getUnreadCount(context, { signal, request }) {
    const response = await request('/团队确认的通知接口', { signal })
    return response.data.unreadCount
  }
}
```

request 自动使用 header 请求域和取消信号。返回非负安全整数；可以省略 getUnreadCount，仅显示入口。0 不显示角标，超过 99 显示 99+。失败显示不可用，403 隐藏入口。切班、切页、退出和重新核验后丢弃旧响应。已读成功后调用 window.EduVue.refreshHeader()。

## 管理员角色分配（2026-11-06）

新增、编辑和授权用户的角色选项按当前操作者身份返回。系统所有者（原始超级管理员账号）可以选择并授予现有 admin 角色；其他账号仍受原有服务端授权范围限制，不能通过请求参数指定目标账号来获得更高角色选项。

新授予的有效内置管理员角色登录后获得管理员操作权限与管理菜单，数据范围使用该角色已有配置；停用角色不授予权限或完整菜单。角色变更的会话失效逻辑继续沿用。新增表单每次打开重新获取角色列表，使用服务器返回的实际roleId提交，不硬编码或伪造管理员选项。

本次验证：8项管理员授予/权限/菜单/数据范围测试，以及27项浏览器回归通过。测试使用隔离数据，没有在真实数据库创建测试管理员。

## 6-02 公告/通知权限与菜单回传（2026-11-25）

### 取值规则

- `getInfo.permissions` 来自当前账号有效角色在 `sys_role_menu` 中已分配的 `sys_menu.perms`；`userId = 1` 的超级管理员直接返回 `["*:*:*"]`。
- `getRouters` 只取 `menu_type in ('M','C')` 且状态正常的菜单，按父子关系输出 `path`、`component` 和 `meta`；`F` 类型按钮权限只进入 `getInfo.permissions`，不进入路由。
- 前端 `collectRoutes` 把父路径与子路径拼成完整路径，并同时收录 `component`；比较前去掉首尾 `/` 和末尾 `/index`。

### 补齐前的实际配置（脚本级）

| 角色 | `getInfo.roles` | 与教学相关的 `permissions` | `getRouters` 教学菜单 |
| --- | --- | --- | --- |
| 管理员 | `["admin"]` | `["*:*:*"]` | 全部启用菜单 |
| 教师 | `["teacher"]` | `education:course:list`、`education:course:add/edit/publish/bind`、`education:scope:read` | `education/course` → `education/CourseListPage` |
| 学生 | `["student"]` | `education:course:list`、`education:scope:read` | `education/course` → `education/CourseListPage` |

教师和学生的 `permissions` 还可能包含一个空字符串，来源是父目录菜单的空 `perms`；前端的 `includes` 判断不受影响。

结论：补齐前，`education:announcement:*`、`education:notice:*` 没有写入任何角色，`getRouters` 中也没有 `education/announcements` 或 `education/notices`。第六组填写的 `serviceRoute: 'education/announcements'` 会匹配失败，公告入口会被前端隐藏；教师发布接口即使有教学班权限，也会因缺少 `@PreAuthorize` 权限码返回 403。这不是权限配错，而是 6-02 的权限与菜单尚未落库。

### 本次补齐

新增可重复执行的 [group1-announcement-menu-permissions.sql](../sql/group1-announcement-menu-permissions.sql)，在已执行 `group12-menu-permissions.sql` 的库上运行：

- 菜单 `班级公告`：`path = announcements`、`component = education/announcements`、`perms = education:announcement:list`，分配给教师和学生；完整路径为 `education/announcements`，与前端 `serviceRoute` 对齐。
- 菜单 `通知`：`path = notices`、`component = education/notices`、`perms = education:notice:list`，只分配给学生；完整路径为 `education/notices`。
- 教师按钮权限：`education:announcement:add/edit/publish/archive`。
- 学生按钮权限：`education:notice:read`。
- 管理员不写 `sys_role_menu`，继续由账号级 `*:*:*` 覆盖。

执行后，`getInfo` 预期新增：

```text
教师：education:announcement:list, education:announcement:add,
      education:announcement:edit, education:announcement:publish,
      education:announcement:archive
学生：education:announcement:list, education:notice:list, education:notice:read
```

`getRouters` 的 `education` 目录下预期新增：

```text
教师：{ "name": "Announcements", "path": "announcements",
        "component": "education/announcements" }
学生：{ "name": "Announcements", "path": "announcements",
        "component": "education/announcements" }
      { "name": "Notices", "path": "notices",
        "component": "education/notices" }
```

### 运行实例核对（2026-11-25）

已在本地 `smart_edu_g12` 执行补种脚本，启动后端后分别用 `admin`、`teacher`、`student` 登录实测，结果与上节预期一致：

| 角色 | `getInfo.roles` | 与教学相关的 `permissions` |
| --- | --- | --- |
| 管理员 | `["admin"]` | `["*:*:*"]` |
| 教师 | `["teacher"]` | `education:announcement:add/archive/edit/list/publish`、`education:course:add/bind/edit/list/publish`、`education:scope:read` |
| 学生 | `["student"]` | `education:announcement:list`、`education:course:list`、`education:notice:list/read`、`education:scope:read` |

`getRouters` 的 `education` 子项实测：

- 管理员：`term`、`teaching-class`、`teacher`、`student`、`course`、`announcements`、`notices`
- 教师：`course`、`announcements`
- 学生：`course`、`announcements`、`notices`

公告为 `path=announcements`、`component=education/announcements`；通知为 `path=notices`、`component=education/notices`。

其他环境仍需按下面的 SQL 和 F12 步骤复核，避免目标库存在人工添加的同名菜单或不同 `path`/`component`。

```sql
SELECT r.role_key, m.menu_type, m.path, m.component, m.perms
FROM sys_role r
JOIN sys_role_menu rm ON rm.role_id = r.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE r.role_key IN ('teacher', 'student')
  AND r.del_flag = '0'
  AND m.status = '0'
ORDER BY r.role_key, m.menu_type, m.order_num;
```

管理员的 `getInfo` 由 `userId = 1` 直接返回 `*:*:*` 和全部启用菜单，不依赖 `sys_role_menu`，因此无需为上表补管理员行。

## 6-01/6-03 总览入口与权限（2026-11-26）

### 两套总览的边界

| 入口 | 归属 | 后端 | 权限 | 说明 |
| --- | --- | --- | --- | --- |
| `/admin/overview` | 第一组 | `/education/overview` | `education:overview:admin` 作为页面门槛；超级管理员由 `*:*:*` 覆盖 | 组织管理统计：学校、学生、教师、学院、行政班，以及未分班学生/未归属学院教师；组织待办仍在页面 `#overview-tasks` |
| `/admin/teaching-overview` | 6-01 | `/education/admin/overview` | `education:overview:admin` | 6-01 管理员教学指标；`serviceRoute` 为 `education/admin/overview` |
| `/admin/todos` | 6-01 | `/education/admin/todos` | `education:overview:admin` | 6-01 管理员待办；`serviceRoute` 为 `education/admin/todos`。`#37` 注册生产页面前，当前路由仍重定向到 `/admin/overview#overview-tasks`；注册页与移除重定向必须同批完成 |
| `/teacher/teaching-overview` | 6-01/6-03 | 6-01 教师总览接口 | `education:overview:teacher` | `serviceRoute` 为 `education/teacher/overview` |
| `/student/today-todos` | 6-01/6-03 | 6-01 学生总览接口 | `education:overview:student` | 学习总览/今日待办，`serviceRoute` 为 `education/student/today-todos` |

6-01 的管理员指标接口 `/education/admin/overview` 与第一组 `/education/overview` 分别取值，不合并口径，也不互相覆盖页面。第一组保留 `/admin/overview` 作为管理总览；6-01 保留 `/education/admin/overview`、`/education/admin/todos` 和 `education:overview:admin` 权限要求。

### 已补菜单与权限

新增可重复执行的 [group1-overview-menu-permissions.sql](../sql/group1-overview-menu-permissions.sql)：

- 菜单 `教学总览`：`path=teacher/overview`、`component=education/teacher/overview`、`perms=education:overview:teacher`，分配给教师。
- 菜单 `学习总览`：`path=student/today-todos`、`component=education/student/today-todos`、`perms=education:overview:student`，分配给学生。
- 管理员菜单锚点 `教学指标`：`path=admin/overview`、`component=education/admin/overview`、`perms=education:overview:admin`，分配给 `admin` 角色；完整路径为 `education/admin/overview`，对应前端 `/admin/teaching-overview`。
- 管理员菜单锚点 `管理员待办`：`path=admin/todos`、`component=education/admin/todos`、`perms=education:overview:admin`，分配给 `admin` 角色；完整路径为 `education/admin/todos`，对应前端 `/admin/todos`。
- 按钮权限 `education:overview:admin`，分配给 `admin` 角色；`管理总览` 页面同步声明该权限。
- 超级管理员仍由 `*:*:*` 覆盖，不依赖 `sys_role_menu`。

本地 `smart_edu_g12` 实测（2026-11-26）：

- 超级管理员：`roles=["admin"]`、`permissions=["*:*:*"]`；`education` 子项包含 `admin/overview`、`admin/todos`、`teacher/overview`、`student/today-todos`。
- 普通管理员（临时 `role_key=admin`、`role_id != 1`，验证后已删除）：`roles=["admin"]`，`permissions=["education:overview:admin"]`，不含 `*:*:*`；`education` 子项为 `admin/overview`、`admin/todos`。
- 教师：新增 `education:overview:teacher`；`education` 子项为 `course`、`announcements`、`teacher/overview`。
- 学生：新增 `education:overview:student`；`education` 子项为 `course`、`announcements`、`notices`、`student/today-todos`。

### 前端入口

- 管理员的 `/admin/overview` 继续使用第一组页面，不渲染 6-01 指标；6-01 指标在 `/admin/teaching-overview`，调用 `/education/admin/overview`。
- `/admin/todos` 的重定向是临时兼容；6-01 注册生产页时，页面注册与移除该重定向放在同一批，避免页面注册后仍被旧重定向带走。第一组不占用该路径。
- 教师/学生侧边栏现在会渲染所有已授权业务页；6-01/6-03 注册 `TeacherOverview`、`StudentOverview` 后会自动出现入口，不需要再改第一组菜单。
- 默认登录落地仍是 `/teacher/overview`、`/student/overview` 占位；6-01 页面合并后可将落地页改为 `teaching-overview`/`today-todos`，或在工作台补快捷入口。
