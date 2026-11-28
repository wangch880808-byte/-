import { registerBusinessPage } from './ui-pages'

/**
 * 6-03 教师/学生/管理员总览业务页注册。
 *
 * 工作台快捷入口和独立路径使用同一组件。角色、权限和服务端路由三项
 * 同时满足才显示/可直达；服务端 @PreAuthorize 仍会逐请求复核。
 *
 * 第 1 组 #41@549ef8e 已提供四个总览菜单锚点，并在其环境报告真实账号验证；
 * serviceRoute 值已双方确认。管理员的“管理员教学指标”“管理员待办”已在
 * 本文件注册为生产业务页，分别对应 `/admin/teaching-metrics` 和
 * `/admin/todos`；目标环境的真实访问仍以 getRouters、权限数据和服务端
 * @PreAuthorize 为准，不能把业务页注册本身当作真实联调结论。
 *
 * TODO(6-03-overview-menu)：目标环境仍需执行第一组菜单脚本并复核
 * education/teacher/overview、education/student/today-todos、
 * education/admin/overview、education/admin/todos 的实际 getRouters 返回；
 * 解除条件为目标环境完成菜单/角色配置和公共外壳复审。有效浏览器回归继续
 * 保留，真实联调是另一个验证层次，不以删除夹具测试作为完成条件。
 * 管理员 /admin/todos 不再重定向到第一组组织待办。
 */
registerBusinessPage({
  id: 'teacher-overview',
  title: '教学总览',
  roles: ['teacher'],
  path: 'teaching-overview',
  permission: 'education:overview:teacher',
  serviceRoute: 'education/teacher/overview',
  component: () => import('@/views/education/teacher/TeacherOverview.vue')
})

registerBusinessPage({
  id: 'student-overview',
  title: '学习总览 / 今日待办',
  roles: ['student'],
  path: 'today-todos',
  permission: 'education:overview:student',
  serviceRoute: 'education/student/today-todos',
  component: () => import('@/views/education/student/StudentOverview.vue')
})

/**
 * 管理员教学指标页面。
 *
 * 复用管理员总览组件，端点与 #24 合同保持一致：只读取
 * `/education/admin/overview`，不发送组织或身份参数。权限和 serviceRoute
 * 必须同时满足，管理员菜单才可进入；拒绝由公共 canOpen/守卫处理。
 */
registerBusinessPage({
  id: 'admin-teaching-metrics',
  title: '管理员教学指标',
  group: '工作台',
  roles: ['admin'],
  path: 'teaching-metrics',
  permission: 'education:overview:admin',
  serviceRoute: 'education/admin/overview',
  component: () => import('@/views/education/admin/AdminOverview.vue')
})

/**
 * 管理员待办页面。
 *
 * 复用原 AdminTodos 的 NO_DATA 合同；页面仍真实请求
 * `/education/admin/todos`，不使用第一组组织待办替代。
 */
registerBusinessPage({
  id: 'admin-todos',
  title: '管理员待办',
  group: '工作台',
  roles: ['admin'],
  path: 'todos',
  permission: 'education:overview:admin',
  serviceRoute: 'education/admin/todos',
  component: () => import('@/views/education/admin/AdminTodos.vue')
})
