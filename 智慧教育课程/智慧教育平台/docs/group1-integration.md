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
