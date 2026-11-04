import { createRouter, createWebHashHistory } from 'vue-router'
import pinia from '@/store'
import { useUserStore } from '@/store/user'
import { canAccess, ROLE_HOME } from '@/utils/access'
import { getToken } from '@/utils/auth'
import { useWorkspaceStore } from '@/store/workspace'
import { businessRoutes } from './routes'
const router = createRouter({
  // 保留实际入口文件路径，使 Spring Boot 的 /app/index.html 刷新仍可访问。
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/login', component: () => import('@/views/auth/Login.vue'), meta: { public: true, title: '登录' } },
    { path: '/connection-error', component: () => import('@/views/error/Status.vue'), meta: { public: true, title: '连接失败', status: 'connection' } },
    // 投影页为公开视图，不经过布局与登录守卫，仅读取服务端裁剪后的公开快照。
    { path: '/education/projection', component: () => import('@/views/education/classroom/Projection.vue'), meta: { public: true, title: '课堂投影' } },
    { path: '/', component: () => import('@/layout/index.vue'), children: [
      { path: '', component: () => import('@/views/error/Status.vue'), meta: { title: '首页' } },
      ...businessRoutes,
      { path: '/403', component: () => import('@/views/error/Status.vue'), meta: { title: '无访问权限', status: '403' } },
      { path: '/:pathMatch(.*)*', component: () => import('@/views/error/Status.vue'), meta: { title: '页面不存在', status: '404' } }
    ] }
  ]
})
router.beforeEach(async to => {
  const user = useUserStore(pinia)
  if (to.meta.public && to.path !== '/login') return true
  if (!getToken()) return to.meta.public ? true : { path: '/login', query: { redirect: to.fullPath } }
  if (!user.loaded) {
    try { await user.loadIdentity() } catch (error) {
      if (error.kind === 'network' || error.kind === 'business') {
        return { path: '/connection-error', query: { next: to.fullPath } }
      }
      if (error.kind === 'forbidden') return '/403'
      // 只有确认未授权时才清会话；其它异常留在连接失败页，避免误踢登录
      if (error.kind === 'unauthorized') {
        user.reset()
        return { path: '/login', query: { reason: 'expired', redirect: to.fullPath } }
      }
      return { path: '/connection-error', query: { next: to.fullPath } }
    }
  }
  if (to.path === '/' || to.path === '/login') return ROLE_HOME[user.role]
  return canAccess(to.meta, user.roles, user.permissions) ? true : '/403'
})
router.afterEach(to => {
  document.title = `${to.meta.title || '知教'} · 智慧教育平台`
  useWorkspaceStore(pinia).open(to)
})

