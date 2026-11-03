import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles.css'
import { setAuthHandlers, getToken } from './api/client'
import { bootstrap, resetSession } from './stores/session'
import { prepareContext, forbidView, suspendWorkspace } from './stores/workspace'
import { registerBusinessPage } from './config/pages'
setAuthHandlers({
  unauthorized() {
    resetSession(); suspendWorkspace()
    if (router.currentRoute.value.path !== '/login') router.replace({ path: '/login', query: { logout: 'expired' } })
  },
  forbidden(message) { forbidView(message) }
})
window.addEventListener('pagehide', () => { resetSession(); suspendWorkspace() })
window.addEventListener('pageshow', async event => {
  if (!event.persisted) return
  if (!getToken()) { router.replace('/login'); return }
  try { await bootstrap(); await prepareContext(true); await router.replace(router.currentRoute.value.fullPath) }
  catch { router.replace({ path: '/login', query: { logout: 'expired' } }) }
})
window.EduVue = Object.freeze({ registerPage: registerBusinessPage,
  refreshHeader: () => window.dispatchEvent(new Event('edu:refresh-header')) })
createApp(App).use(router).mount('#app')
