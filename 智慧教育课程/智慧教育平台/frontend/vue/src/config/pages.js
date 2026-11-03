import { shallowReactive } from 'vue'
export const pages = shallowReactive([
  { id: 'users', title: '用户管理', group: '系统管理', roles: ['admin'], path: 'users', permission: 'system:user:list', serviceRoute: 'system/user', component: () => import('../views/admin/UserListView.vue') },
  { id: 'roles', title: '角色管理', group: '系统管理', roles: ['admin'], path: 'roles', permission: 'system:role:list', serviceRoute: 'system/role', component: () => import('../views/admin/RoleListView.vue') },
  { id: 'menus', title: '菜单管理', group: '系统管理', roles: ['admin'], path: 'menus', permission: 'system:menu:list', serviceRoute: 'system/menu', component: () => import('../views/admin/MenuListView.vue') },
  { id: 'depts', title: '组织架构管理', group: '教务组织', roles: ['admin'], path: 'depts', permission: 'system:dept:list', serviceRoute: 'system/dept', component: () => import('../views/admin/DeptListView.vue') },
  { id: 'logs', title: '审计日志', group: '系统管理', roles: ['admin'], path: 'logs', permission: 'monitor:operlog:list', serviceRoute: 'monitor/operlog', component: () => import('../views/admin/OperLogView.vue') }
])
let install = null
export function onPageRegistered(handler) { install = handler }
export function registerBusinessPage(page) {
  if (!page || !/^[a-z][a-z0-9-]*$/.test(page.id || '') || !/^[a-z][a-z0-9-]*$/.test(page.path || '') ||
      !page.title || !page.permission || !page.serviceRoute || typeof page.component !== 'function' ||
      !Array.isArray(page.roles) || !page.roles.length || page.roles.some(role => !['admin', 'teacher', 'student'].includes(role)) ||
      (page.requiresContext && !['course', 'class'].includes(page.requiresContext))) throw new Error('业务页面注册信息不完整')
  if (pages.some(item => item.id === page.id || item.path === page.path)) throw new Error('业务页面位置重复')
  if (page.headerEntry && (!page.headerEntry.label || (page.headerEntry.getUnreadCount && typeof page.headerEntry.getUnreadCount !== 'function'))) throw new Error('通知入口配置不正确')
  const entry = Object.freeze({ ...page })
  pages.push(entry); install?.(entry)
}
