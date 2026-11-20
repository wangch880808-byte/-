import { shallowReactive } from 'vue'
export const pages = shallowReactive([
  { id: 'profile', title: '个人中心', sidebar: false, roles: ['admin', 'teacher', 'student'], path: 'profile', component: () => import('@/views/education/ProfileView.vue') },
  {id:'terms',title:'学期管理',group:'教务组织',roles:['admin'],path:'terms',permission:'education:term:list',serviceRoute:'education/term',component:()=>import('@/views/education/admin/TermList.vue')},
  {id:'classes',title:'班级管理',group:'教务组织',roles:['admin'],path:'classes',permission:'education:class:list',serviceRoute:'education/teaching-class',component:()=>import('@/views/education/admin/ClassList.vue')},
  {id:'teachers',title:'教师管理',group:'教务组织',roles:['admin'],path:'teachers',permission:'education:directory:list',serviceRoute:'education/teacher',component:()=>import('@/views/education/admin/TeacherList.vue')},
  {id:'students',title:'学生管理',group:'教务组织',roles:['admin'],path:'students',permission:'education:directory:list',serviceRoute:'education/student',component:()=>import('@/views/education/admin/StudentList.vue')},
  {id:'courses',title:'我的课程',group:'我的课程',roles:['teacher','student'],path:'courses',permission:'education:course:list',serviceRoute:'education/course',component:()=>import('@/views/education/CourseListPage.vue')},

  { id: 'users', title: '用户管理', group: '系统管理', roles: ['admin'], path: 'users', permission: 'system:user:list', serviceRoute: 'system/user', component: () => import('@/views/education/admin/UserListView.vue') },
  { id: 'roles', title: '角色管理', group: '系统管理', roles: ['admin'], path: 'roles', permission: 'system:role:list', serviceRoute: 'system/role', component: () => import('@/views/education/admin/RoleListView.vue') },
  { id: 'menus', title: '菜单管理', group: '系统管理', roles: ['admin'], path: 'menus', permission: 'system:menu:list', serviceRoute: 'system/menu', component: () => import('@/views/education/admin/MenuListView.vue') },
  { id: 'depts', title: '组织架构管理', group: '教务组织', roles: ['admin'], path: 'depts', permission: 'system:dept:list', serviceRoute: 'system/dept', component: () => import('@/views/education/admin/DeptListView.vue') },
  { id: 'logs', title: '审计日志', group: '系统管理', roles: ['admin'], path: 'logs', permission: 'monitor:operlog:list', serviceRoute: 'monitor/operlog', component: () => import('@/views/education/admin/OperLogView.vue') }
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
