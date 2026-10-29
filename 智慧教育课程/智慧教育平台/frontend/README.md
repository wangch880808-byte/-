# 前端工作区

当前使用原生 HTML/CSS/JavaScript，由若依后端同源提供。已实现管理员用户、角色、菜单、部门和审计日志页面；课程/教学班上下文已有前端适配，待第 2 组接口确认和上线后完成真实联调。尚未选定 Vue 工程，不引入额外前端依赖。

运行方式见 [后端说明](../backend/README.md)，任务进度见 [1B 交付与验收记录](../docs/frontend-1b-delivery.md)。原型只作导航参考，正式导航仅显示已注册、当前角色获授权、且具备对应服务端路由的页面。

当前用户负责第 1 组前端 1B，仅维护 1-03 公共外壳与上下文、1-04 系统管理页面。第 2～6 组各自提供业务页面和接口；具体导航位置、统一卸载信号和第 6 组顶部通知接入见 [1B 接入与职责清单](../docs/frontend-1b-integration.md)。AI 不在本组范围内。

## 文件组织

- `login.html / login.js`：三入口登录、验证码、令牌和身份核验。
- `admin.html / teacher.html / student.html`：角色与稳定菜单位置配置。
- `role-home.js`：加载身份和授权路由后开放页面；返回、刷新和浏览器历史恢复重新核验。
- `shell/auth.js`：角色、操作权限及服务端路由的前端投影。
- `shell/context.js`：课程/教学班选项、校验、持久化和上下文切换。
- `shell/shell.js / shell.css`：公共布局、授权导航、页签、移动端导航和页面生命周期。
- `shell/api.js`：同源请求、令牌、超时、取消、401/403、二进制响应。
- `pages/admin/admin-ui.js / admin.css`：弹窗、确认、树选择器及管理端样式。
- `pages/admin/user-list.js / role-list.js / oper-log.js`：用户、角色及审计查询。
- `pages/admin/tree-page.js / menu-list.js / dept-list.js`：菜单和部门树表、筛选与增删改。
- `src/`：后续正式框架的预留目录，不与当前静态页面并行生成重复业务。

所有新增文件仍在现有 `shell/**` 和 `pages/**` 资源范围内，本次无需修改后端打包或安全白名单。

## 页面注册与权限

页面脚本放在 `shell.js` 之前加载：

```js
window.EDU_PAGES = window.EDU_PAGES || [];
window.EDU_PAGES.push({
  g: 1, i: 4, // 原型菜单位置；过滤导航不会改变位置
  page: {
    roles: ['teacher'],
    permission: 'education:classroom:list', // 示例；实际值以提供方契约为准
    route: 'education/classroom', // 对应 getRouters 返回的 path 或 component
    requiresContext: 'class', // 'course' 只要求课程，'class' 同时要求已校验教学班；不填则不要求上下文
  mount(host, ctx, lifecycle) { /* ctx 是只读快照；请求传 lifecycle.signal，回写前检查 lifecycle.isCurrent() */ },
    unmount() { /* 中止本页请求，移除订阅、定时器，使旧回调失效 */ }
  }
});
```

示例不是现有教育接口或权限承诺。静态且不读取私有数据的入口可显式声明 `allowAuthenticated: true`。其他页面必须声明 permission。页面也可在外壳加载后调用 `EduShell.registerPage(g, i, page)` 注册。

同一位置不能被另一个模块覆盖。延后注册的页面仍按当前角色、服务端权限和路由过滤。通知页面可声明 `headerEntry` 由外壳显示顶部入口；没有对应业务页面和真实接口时不显示模拟入口。

`getInfo` 与 `getRouters` 校验完成前不挂载业务页。权限不足、未注册或未获得路由的菜单不会显示；直达链接仍被前端拦截。按钮按操作权限显示，服务端继续负责每次请求、对象、组织和教学班范围。

> 注意：**注册页面不等于菜单可见**。非管理员账号还必须在 `sys_menu` 建立对应路由、在 `sys_role_menu` 给角色授权，`getRouters` 才会返回该路由；否则页面注册了也不会出现在导航里。新增业务页时请与 1A/2A 一起确认权限标识和菜单授权。

管理员槽位：`g=6` 的 `i=0/1/2/5` 分别是用户/角色/菜单/审计；`g=1&i=0` 为若依部门树的组织架构入口。2B 继续实现班级、学期、师生及任课关系页面，复用部门和账号编辑能力。

## 请求和页面清理

统一调用 `EduApi.get/post/put/del/request`。HTTP 或业务码 401 清会话并回登录页；403 卸载当前页、关闭管理弹窗、清除私有内容。自行处理 403 时传 `silent403: true`。

- 默认 15 秒超时，抛出 `error.timeout`。
- 可传 `signal: controller.signal`；主动取消抛出 `error.cancelled`，不会显示为超时。
- `EduApi.cancelAll()` 取消当前全部请求，不能撤销服务端已经完成的写入。
- `raw: true` 返回未消费的 Response；JSON 响应仍先检查业务错误码。
- 请求必须同源，避免将 Bearer 令牌发送给第三方。
- 弹窗字符串按纯文本显示；使用 Node 作为复杂内容。切页统一调用 `AdminUI.closeAll()`，页面仍须自行清理生命周期。
- 用户编辑保留既有岗位关联；角色数据权限通过独立接口保存，自定义部门只提交显式勾选项。

## 课程与教学班上下文

默认适配草案中的 `GET /education/context/options` 和 `GET /education/context/check`。这两条教学接口尚不存在，路径和字段仍须 2A 确认。接口缺失时显示服务未就绪，不加载假数据，不把未校验对象交给业务页。可在加载 context.js 前通过 `window.EDU_CONTEXT_ENDPOINTS` 替换同源路径。

- 选择器顺序为课程 → 教学班，学期使用 options 中的真实值。
- ID 内部统一成字符串，拒绝不安全的 JSON 数字；支持完整的大整数字符串。
- URL 同时保存 courseSpaceId 和 classId；仅 classId 且对应多个课程时要求重新选择。
- check 结果必须与所选课程、教学班、学期和成员角色一致；绑定标识存在时一并核对。
- 仅保存所选 ID，按账号和角色隔离 sessionStorage；恢复时重新读取 options 并校验。
- `EduContext.get/set/clear/onChange/refresh` 提供统一状态；`EduShell.getContext()` 返回当前快照。
- 切换前先将旧上下文置空、中止在途请求、清理页签并卸载旧页面；校验成功才重新挂载。
- 撤权、校验失败、接口异常均不复用旧上下文；上下文 403 不清除登录态。
- 系统管理页面不请求教学上下文接口。业务组页面尚未接入时，师生导航保持空状态。

一个班多课程绑定、无绑定课程、当前学期及教务管理员的上下文行为，仍按 [2A 待确认项](../docs/group2-context-api-requirements.md) 评审。前端适配和模拟测试不构成接口定稿或 G0 验收。

## 验证

在工程根运行：

```powershell
node --test tests/frontend-regressions.test.cjs tests/context-permissions.test.cjs tests/shell-integration.test.cjs
```

测试不新增依赖，覆盖权限、请求、迟到响应、上下文恢复/撤权/并发、树选择和弹窗。模拟响应和最小 DOM 模型不替代真实登录、受限管理员、各业务组接口和移动设备联调。

前端资源打入 JAR；修改后先停止本项目后端，再离线打包并重启。不可把旧 JAR 的浏览器表现作为新代码的验证结果。

