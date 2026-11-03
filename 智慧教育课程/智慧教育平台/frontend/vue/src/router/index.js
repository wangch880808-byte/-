import { createRouter, createWebHashHistory } from 'vue-router'
import { bootstrap, canOpen, session } from '../stores/session'
import { clearSession, getToken } from '../api/client'
import { pages, onPageRegistered } from '../config/pages'
import { prepareContext, resetView } from '../stores/workspace'
const pageRoute = page => ({ path: page.path, name: page.id, component: page.component, meta: { page } })
const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    { path: '/', redirect: () => getToken() ? '/' + (session.role || 'admin') : '/login' },
    { path: '/:role(admin|teacher|student)', name: 'workspace', component: () => import('../layout/AppLayout.vue'),
      children: [{ path: '', name: 'home', component: () => import('../views/HomeView.vue') }, ...pages.map(pageRoute),
        { path: ':unknown(.*)*', name: 'unavailable', component: () => import('../views/UnavailableView.vue') }] },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})
onPageRegistered(page => {
  router.addRoute('workspace', pageRoute(page))
  if (router.currentRoute.value.name === 'unavailable') router.replace(router.currentRoute.value.fullPath)
})
router.beforeEach(async to => {
  resetView()
  if (!getToken()) return to.meta.public ? true : { path: '/login', query: { redirect: to.fullPath } }
  try { await bootstrap() } catch {
    clearSession()
    return to.meta.public ? true : { path: '/login', query: { logout: 'expired' } }
  }
  if (to.meta.public) return '/' + session.role
  if (to.params.role !== session.role) return '/' + session.role
  if (to.meta.page && !canOpen(to.meta.page)) return { path: '/' + session.role, query: { denied: '1' } }
  await prepareContext()
  return true
})
router.afterEach(to => { if (to.query.denied) resetView('该页面未开放或没有访问权限') })
export default router
