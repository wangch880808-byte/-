import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles.css'
import { setAuthHandlers } from './api/client'
import { resetSession } from './stores/session'

setAuthHandlers({
  unauthorized() {
    resetSession()
    if (router.currentRoute.value.path !== '/login') {
      router.replace({ path: '/login', query: { logout: 'expired' } })
    }
  },
  forbidden(message) {
    window.dispatchEvent(new CustomEvent('app:forbidden', { detail: message }))
  }
})

createApp(App).use(router).mount('#app')
