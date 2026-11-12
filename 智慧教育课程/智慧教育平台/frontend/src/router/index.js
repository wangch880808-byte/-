import { createRouter, createWebHashHistory } from 'vue-router'
import { bootstrap, canOpen, session } from '@/education/session.js'
import { clearSession, getToken } from '@/api/education/client.js'
import { pages, onPageRegistered } from '@/education/ui-pages.js'
import { adminMenuItems } from '@/education/ui-menu.js'
import { prepareContext, resetView } from '@/education/workspace.js'
const pageRoute = page => ({ path: page.path, name: page.id, component: page.component, meta: { page } })
const placeholderName = item => 'admin-placeholder-' + item.id
const placeholderRoutes = adminMenuItems
  .filter(item => !pages.some(page => page.roles.includes('admin') && page.path === item.path))
  .map(item => ({ path: item.path, name: placeholderName(item),
    component: () => import('@/views/education/admin/BlankAdminView.vue'), meta: { adminPlaceholder: item } }))
const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/education/LoginView.vue'), meta: { public: true } },
    { path: '/', redirect: () => getToken() ? '/' + (session.role || 'admin') : '/login' },
    { path: '/:role(admin|teacher|student)', name: 'workspace', component: () => import('@/layout/education/AppLayout.vue'),
      children: [{ path: '', name: 'home', component: () => import('@/views/education/HomeView.vue') }, ...pages.map(pageRoute), ...placeholderRoutes,
        {path:'courses/:courseId(\\d+)',name:'course-detail',component:()=>import('@/views/education/CourseDetailPage.vue'),meta:{page:{id:'course-detail',title:'课程详情',roles:['teacher','student'],permission:'education:course:list',serviceRoute:'education/course'}}},
        { path: ':unknown(.*)*', name: 'unavailable', component: () => import('@/views/education/UnavailableView.vue') }] },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})
onPageRegistered(page => {
  const item = adminMenuItems.find(item => item.path === page.path)
  if (item && page.roles.includes('admin')) router.removeRoute(placeholderName(item))
  router.addRoute('workspace', pageRoute(page))
  if (router.currentRoute.value.name === 'unavailable' || router.currentRoute.value.meta.adminPlaceholder) router.replace(router.currentRoute.value.fullPath)
})
export default router

// Public route metadata consumed by RuoYi's shared navigation utilities.
export const constantRoutes = router.options.routes
export const dynamicRoutes = pages.map(pageRoute)
