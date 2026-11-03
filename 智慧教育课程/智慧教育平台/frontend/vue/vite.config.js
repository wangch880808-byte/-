import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const target = process.env.VITE_API_TARGET || 'http://127.0.0.1:8080'

// /login 和 /logout 既是后端 POST 接口，也是前端路由：
// 浏览器的 GET 导航交给 SPA（后端这两个地址只接受 POST），POST 才代理到后端。
const htmlBypass = (req) => {
  if (req.method === 'GET') return '/index.html'
}

// 开发时把若依接口代理到本地后端，浏览器看到同源，避免跨域和令牌问题。
// 生产部署时可用 VITE_BASE 指定子路径（例如 /vue/），再由 nginx 或后端静态目录托管。
export default defineConfig({
  plugins: [vue()],
  base: process.env.VITE_BASE || '/',
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/captchaImage': target,
      '/login': { target, bypass: htmlBypass },
      '/logout': { target, bypass: htmlBypass },
      '/getInfo': target,
      '/getRouters': target,
      '/system': target,
      '/monitor': target,
      '/education': target,
      '/logo.png': target
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: false
  }
})
