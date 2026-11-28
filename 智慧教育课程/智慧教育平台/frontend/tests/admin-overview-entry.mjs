import { createApp, h } from 'vue'
import { createPinia } from 'pinia'
import '@/assets/styles/education.css'
import OverviewBoard from '@/components/education/OverviewBoard.vue'
import AdminOverview from '@/views/education/admin/AdminOverview.vue'
import AdminTodos from '@/views/education/admin/AdminTodos.vue'

/**
 * 管理员总览/待办的隔离测试入口。
 *
 * 只挂载组件，不安装生产路由、权限守卫或管理员菜单。生产构建入口仍是
 * src/main.js；本文件仅由 tests/admin-overview.html 通过 Vite 开发服务器加载。
 */
const view = new URLSearchParams(location.search).get('view') || 'overview'
const components = {
  overview: AdminOverview,
  todos: AdminTodos,
  unknown: {
    render: () => h(OverviewBoard, {
      config: {
        endpoint: 'unknown',
        timeFilter: true,
        classFilter: false,
        groups: [{ key: 'unknown', label: '未知' }],
        metrics: [{
          code: 'unknown.metric',
          group: 'unknown',
          title: '未知指标',
          note: '未知端点测试',
          source: 'course',
          unitLabel: '',
          windowed: false
        }]
      }
    })
  }
}
const component = components[view]
if (!component) throw new Error('未知测试视图：' + view)
const app = createApp({ render: () => h(component) })
app.use(createPinia())
app.mount('#app')
// 仅测试入口暴露卸载句柄，用于验证卸载后迟到响应；生产入口不加载本文件。
window.__adminTestApp = app
