import { reactive } from 'vue'
import { api, ApiError, clearSession, getRole, getToken, setSession } from '../api/client'
import { canAccess } from '../core/access'
export const session = reactive({ role: getRole() || '', user: null, roles: [], permissions: [], routes: [], ready: false })
let inflight = null
export function hasPermission(permission) {
  return session.ready && !!session.user && (!permission || session.permissions.includes('*:*:*') || session.permissions.includes(permission))
}
export function hasRole(role) { return session.ready && session.roles.includes(role) }
export function canOpen(page) { return canAccess(page, session) }
export function resetSession() {
  Object.assign(session, { role: '', user: null, roles: [], permissions: [], routes: [], ready: false })
  inflight = null
}
export async function bootstrap() {
  const token = getToken()
  if (!token) { resetSession(); throw new ApiError('请先登录', { unauthorized: true }) }
  if (inflight?.token === token) return inflight.promise
  session.ready = false
  const promise = (async () => {
    const options = { silent403: true, scope: 'session' }
    const [info, routes] = await Promise.all([api.get('/getInfo', options), api.get('/getRouters', options)])
    if (getToken() !== token) throw new ApiError('会话已变化', { cancelled: true })
    const role = getRole()
    if (!['admin', 'teacher', 'student'].includes(role) || !info.roles?.includes(role) || !info.user?.userId || !Array.isArray(routes.data)) {
      throw new ApiError('当前账号没有该登录入口的权限', { roleMismatch: true })
    }
    Object.assign(session, { role, user: info.user, roles: info.roles, permissions: Array.isArray(info.permissions) ? info.permissions : [], routes: routes.data, ready: true })
    return info
  })()
  inflight = { token, promise }
  try { return await promise } catch (error) {
    if (getToken() === token && !error.cancelled) { clearSession(); resetSession() }
    throw error
  } finally { if (inflight?.promise === promise) inflight = null }
}
export async function login(payload) {
  const data = await api.post('/login', payload, { scope: 'session', silent403: true })
  if (!data.token) throw new ApiError('登录成功但未返回令牌')
  setSession(data.token, payload.role); resetSession()
  try { await bootstrap() } catch (error) { clearSession(); resetSession(); throw error }
  return session
}
export async function logout() {
  try { await api.post('/logout', undefined, { scope: 'session' }) } finally { clearSession(); resetSession() }
}
