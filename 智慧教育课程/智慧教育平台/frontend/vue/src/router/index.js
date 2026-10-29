import { createRouter, createWebHashHistory } from 'vue-router'
import { bootstrap, hasPermission, session } from '../stores/session'
import { getToken } from '../api/client'

const LoginView = () => import('../views/LoginView.vue')
const AppLayout = () => import('../layout/AppLayout.vue')
const HomeView = () => import('../views/HomeView.vue')
const UserListView = () => import('../views/admin/UserListView.vue')
const RoleListView = () => import('../views/admin/RoleListView.vue')
const MenuListView = () => import('../views/admin/MenuListView.vue')
const DeptListView = () => import('../views/admin/DeptListView.vue')
const OperLogView = () => import('../views/admin/OperLogView.vue')

const routes = [
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  { path: '/', redirect: () => `/${session.role || 'login'}` },
  {
    path: '/:role(admin|teacher|student)',
    component: AppLayout,
    children: [
      { path: '', name: 'home', component: HomeView },
      {
        path: 'users',
        name: 'users',
        component: UserListView,
        meta: { permission: 'system:user:list', roles: ['admin'] }
      },
      {
        path: 'roles',
        name: 'roles',
        component: RoleListView,
        meta: { permission: 'system:role:list', roles: ['admin'] }
      },
      {
        path: 'menus',
        name: 'menus',
        component: MenuListView,
        meta: { permission: 'system:menu:list', roles: ['admin'] }
      },
      {
        path: 'depts',
        name: 'depts',
        component: DeptListView,
        meta: { permission: 'system:dept:list', roles: ['admin'] }
      },
      {
        path: 'logs',
        name: 'logs',
        component: OperLogView,
        meta: { permission: 'monitor:operlog:list', roles: ['admin'] }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  // 后端静态托管时用 hash 路由，避免服务器为 /vue/** 做 SPA 回退
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    // 已登录用户访问登录页时回到自己的工作台
    if (getToken() && session.role) return `/${session.role}`
    return true
  }
  if (!getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!session.ready) {
    try {
      await bootstrap()
    } catch {
      return { path: '/login', query: { logout: 'expired' } }
    }
  }
  const role = to.params.role
  if (role && role !== session.role) {
    return `/${session.role}`
  }
  if (to.meta.roles && !to.meta.roles.includes(session.role)) {
    return `/${session.role}`
  }
  if (to.meta.permission && !hasPermission(to.meta.permission)) {
    return `/${session.role}`
  }
  return true
})

export default router
