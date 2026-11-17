/**
 * 本地静态服务器：把 dist 挂到 /vue/，供浏览器夹具使用。
 *
 * Vite preview 在本机把资源按根路径提供，而生产构建 index.html 使用 /vue/ 前缀。
 * 本服务器只做前缀剥离和 SPA fallback，不改变构建产物。
 */
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'dist')
const port = Number(process.env.PORT || 4174)
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2'
}

function send(res, status, body, type = 'text/plain; charset=utf-8') {
  res.writeHead(status, { 'Content-Type': type, 'Cache-Control': 'no-store' })
  res.end(body)
}

const server = http.createServer((req, res) => {
  let pathname
  try {
    pathname = decodeURIComponent(new URL(req.url, `http://${req.headers.host}`).pathname)
  } catch {
    return send(res, 400, 'Bad Request')
  }
  if (pathname === '/vue' || pathname === '/vue/') {
    pathname = '/vue/index.html'
  }
  if (!pathname.startsWith('/vue/')) {
    return send(res, 404, 'Not Found')
  }
  const relative = pathname.slice('/vue/'.length)
  const target = path.resolve(root, relative)
  if (!target.startsWith(root + path.sep) && target !== root) {
    return send(res, 403, 'Forbidden')
  }
  fs.readFile(target, (error, data) => {
    if (error) {
      if (!path.extname(relative)) {
        return fs.readFile(path.join(root, 'index.html'), (indexError, indexData) => {
          if (indexError) return send(res, 404, 'Not Found')
          send(res, 200, indexData, MIME['.html'])
        })
      }
      return send(res, 404, 'Not Found')
    }
    send(res, 200, data, MIME[path.extname(target).toLowerCase()] || 'application/octet-stream')
  })
})

server.listen(port, '127.0.0.1', () => {
  console.log(`serving ${root} at http://127.0.0.1:${port}/vue/`)
})
