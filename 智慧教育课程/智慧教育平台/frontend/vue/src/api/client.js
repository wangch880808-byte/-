/**
 * 统一请求客户端（Vue 版，语义与静态页 shell/api.js 保持一致）
 * - Bearer 令牌
 * - 默认 15 秒超时
 * - 401 清会话并通知上层跳登录
 * - 403 通知上层显示无权限
 */
const TOKEN_KEY = 'zhijiao.login.token'
const ROLE_KEY = 'zhijiao.login.role'
const DEFAULT_TIMEOUT = 15000

let handlers = { unauthorized: null, forbidden: null }

export function setAuthHandlers(next) {
  handlers = { ...handlers, ...next }
}

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function getRole() {
  return sessionStorage.getItem(ROLE_KEY)
}

export function setSession(token, role) {
  sessionStorage.setItem(TOKEN_KEY, token)
  sessionStorage.setItem(ROLE_KEY, role)
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(ROLE_KEY)
}

export class ApiError extends Error {
  constructor(message, extra = {}) {
    super(message)
    Object.assign(this, extra)
  }
}

async function parseBody(response) {
  try {
    return await response.json()
  } catch {
    throw new ApiError(`服务器响应无法解析（HTTP ${response.status}）`, { status: response.status })
  }
}

export async function request(path, options = {}) {
  const controller = typeof AbortController !== 'undefined' ? new AbortController() : null
  let timedOut = false
  const timeout = options.timeout || DEFAULT_TIMEOUT
  const timer = controller
    ? setTimeout(() => {
        timedOut = true
        controller.abort()
      }, timeout)
    : null

  const abortFromCaller = () => controller && controller.abort()
  if (controller && options.signal) {
    if (options.signal.aborted) controller.abort()
    else options.signal.addEventListener('abort', abortFromCaller, { once: true })
  }

  const headers = { Accept: 'application/json', ...(options.headers || {}) }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let body = options.body
  if (body && typeof body === 'object' && !(body instanceof FormData)) {
    headers['Content-Type'] = headers['Content-Type'] || 'application/json'
    body = JSON.stringify(body)
  }

  try {
    const response = await fetch(path, {
      method: options.method || 'GET',
      headers,
      body,
      cache: 'no-store',
      signal: controller ? controller.signal : options.signal
    })

    if (response.status === 401) {
      clearSession()
      handlers.unauthorized?.()
      throw new ApiError('登录状态已失效，请重新登录', { status: 401, unauthorized: true })
    }
    if (response.status === 403) {
      const message = '没有访问该资源的权限'
      if (!options.silent403) handlers.forbidden?.(message)
      throw new ApiError(message, { status: 403, forbidden: true })
    }

    const data = await parseBody(response)
    const code = data && typeof data.code !== 'undefined' ? data.code : response.status

    if (code === 401) {
      clearSession()
      handlers.unauthorized?.()
      throw new ApiError('登录状态已失效，请重新登录', { status: 401, unauthorized: true })
    }
    if (code === 403) {
      const message = data?.msg || '没有访问该资源的权限'
      if (!options.silent403) handlers.forbidden?.(message)
      throw new ApiError(message, { status: 403, forbidden: true })
    }
    if (!response.ok || code !== 200) {
      throw new ApiError(data?.msg || `请求失败（${response.status}）`, { status: response.status, code })
    }
    return data
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error?.name === 'AbortError' || controller?.signal.aborted) {
      if (timedOut) throw new ApiError(`请求超时（${timeout}ms），请稍后重试`, { timeout: true })
      throw new ApiError('请求已取消', { cancelled: true })
    }
    if (error instanceof TypeError) throw new ApiError('网络连接异常，请检查网络后重试', { network: true })
    throw error
  } finally {
    if (timer) clearTimeout(timer)
    if (controller && options.signal) options.signal.removeEventListener('abort', abortFromCaller)
  }
}

export const api = {
  get: (path, options) => request(path, { ...options, method: 'GET' }),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
  del: (path, options) => request(path, { ...options, method: 'DELETE' })
}
