import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    base: '/app/', publicDir: false,
    plugins: [vue()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      port: 5173, strictPort: true,
      proxy: {
        '/dev-api': {
          target: env.VITE_BACKEND_URL || 'http://127.0.0.1:8080', changeOrigin: true,
          rewrite: path => path.replace(/^\/dev-api/, '')
        }
      }
    },
    preview: { port: 4173, strictPort: true },
    // 产物目录取 app/：后端把整个 frontend/ 复制进 static/，构建完即由 /app/ 提供，
    // 与 base 保持一致，无需改动 ruoyi-admin 的打包配置。
    build: { outDir: 'app', emptyOutDir: true, sourcemap: false }
  }
})
