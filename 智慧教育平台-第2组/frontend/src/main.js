import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import pinia from './store'
import router from './router'
import { useUserStore } from './store/user'
import modal from './plugins/modal'
import Pagination from './components/Pagination/index.vue'
import './styles/index.css'
import 'element-plus/dist/index.css'
const app = createApp(App)
app.use(pinia)
app.use(ElementPlus, { locale: zhCn })
app.component('Pagination', Pagination)
app.config.globalProperties.$modal = modal
app.directive('hasPermi', (el, binding) => {
  const permissions = useUserStore(pinia).permissions
  el.hidden = !permissions.includes('*:*:*') && !(binding.value || []).some(item => permissions.includes(item))
})
window.addEventListener('zhijiao:session-expired', () => {
  const redirect = router.currentRoute.value.fullPath
  useUserStore(pinia).reset()
  if (router.currentRoute.value.path !== '/login') router.replace({ path: '/login', query: { reason: 'expired', redirect } })
})
router.onError(() => { ElMessage.error('页面加载失败，请刷新后重试') })
app.use(router)
app.mount('#app')
