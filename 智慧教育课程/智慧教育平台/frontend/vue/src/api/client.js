const TOKEN_KEY = 'zhijiao.login.token'
const ROLE_KEY = 'zhijiao.login.role'
let handlers = {}
let generation = 0
const pending = new Map()
export function setAuthHandlers(next) { handlers = { ...handlers, ...next } }
export function getToken() { return sessionStorage.getItem(TOKEN_KEY) }
export function getRole() { return sessionStorage.getItem(ROLE_KEY) }
export function cancelRequests(scope) {
  for (const [controller, kind] of pending) if (!scope || kind === scope) controller.abort()
}
export function setSession(token, role) {
  generation++; cancelRequests()
  sessionStorage.setItem(TOKEN_KEY, token); sessionStorage.setItem(ROLE_KEY, role)
}
export function clearSession() {
  generation++; cancelRequests()
  sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(ROLE_KEY)
}
export class ApiError extends Error {
  constructor(message, extra = {}) { super(message); Object.assign(this, extra) }
}
export async function request(path, options = {}) {
  const origin = globalThis.location?.origin || 'http://localhost'
  const url = new URL(path, origin)
  if (url.origin !== origin || !['http:', 'https:'].includes(url.protocol)) throw new ApiError('仅允许请求本站接口')
  const controller = new AbortController()
  const run = generation
  const token = getToken()
  const stale = () => run !== generation || token !== getToken() || controller.signal.aborted
  const assertCurrent = () => { if (stale()) throw new ApiError('请求已取消', { cancelled: true }) }
  let timedOut = false
  const timeout = options.timeout ?? 15000
  pending.set(controller, options.scope || 'page')
  const timer = setTimeout(() => { timedOut = true; controller.abort() }, timeout)
  const abort = () => controller.abort()
  if (options.signal?.aborted) abort()
  else options.signal?.addEventListener('abort', abort, { once: true })
  const headers = { Accept: 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = 'Bearer ' + token
  let body = options.body
  if (body && typeof body === 'object' && !(body instanceof FormData)) {
    headers['Content-Type'] = headers['Content-Type'] || 'application/json'
    body = JSON.stringify(body)
  }
  try {
    assertCurrent()
    const response = await fetch(url.href, { method: options.method || 'GET', headers, body, cache: 'no-store', signal: controller.signal })
    assertCurrent()
    let data
    if (response.status !== 401 && response.status !== 403) {
      try { data = await response.json() } catch { throw new ApiError('服务器响应无法解析', { status: response.status }) }
    }
    assertCurrent()
    const code = Number(data?.code ?? response.status)
    if (response.status === 401 || code === 401) {
      clearSession(); handlers.unauthorized?.()
      throw new ApiError('登录状态已失效，请重新登录', { unauthorized: true, status: 401 })
    }
    if (response.status === 403 || code === 403) {
      if (!options.silent403) { cancelRequests('page'); handlers.forbidden?.('没有访问该资源的权限') }
      throw new ApiError('没有访问该资源的权限', { forbidden: true, status: 403 })
    }
    if (!response.ok || code !== 200) throw new ApiError(data?.msg || '请求失败，请稍后重试', { code, status: response.status })
    return data
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (controller.signal.aborted) throw new ApiError(timedOut ? '请求超时，请稍后重试' : '请求已取消', timedOut ? { timeout: true } : { cancelled: true })
    if (error instanceof TypeError) throw new ApiError('网络连接异常，请稍后重试', { network: true })
    throw error
  } finally {
    clearTimeout(timer); pending.delete(controller); options.signal?.removeEventListener('abort', abort)
  }
}
export const api = {
  get: (path, options) => request(path, { ...options, method: 'GET' }),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
  del: (path, options) => request(path, { ...options, method: 'DELETE' })
}
