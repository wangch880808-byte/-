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
    build: { outDir: 'dist', sourcemap: false }
  }
})
