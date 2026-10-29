import { reactive } from 'vue'
import { api, ApiError, clearSession, getRole, getToken, setSession } from '../api/client'

export const session = reactive({
  token: getToken() || '',
  role: getRole() || '',
  user: null,
  roles: [],
  permissions: [],
  ready: false
})

export function hasPermission(permission) {
  if (!permission) return true
  if (!session.user) return false
  return session.permissions.includes('*:*:*') || session.permissions.includes(permission)
}

export function hasRole(role) {
  return session.roles.includes(role)
}

export function resetSession() {
  session.token = ''
  session.role = ''
  session.user = null
  session.roles = []
  session.permissions = []
  session.ready = false
}

/**
 * 读取当前令牌对应的身份；角色与入口不一致时抛出 roleMismatch，
 * 由路由守卫清理会话并回登录页。
 */
export async function bootstrap() {
  if (!getToken()) {
    resetSession()
    return null
  }
  const info = await api.get('/getInfo', { silent403: true })
  const roles = Array.isArray(info.roles) ? info.roles : []
  if (!session.role || !roles.includes(session.role)) {
    clearSession()
    resetSession()
    throw new ApiError('当前账号没有该登录入口的角色，请重新登录', { roleMismatch: true })
  }
  session.user = info.user || null
  session.roles = roles
  session.permissions = Array.isArray(info.permissions) ? info.permissions : []
  session.ready = true
  return info
}

export async function login({ username, password, code, uuid, role }) {
  const data = await api.post('/login', { username, password, code, uuid, role })
  if (!data.token) throw new ApiError('登录成功但未返回令牌')
  setSession(data.token, role)
  session.token = data.token
  session.role = role
  try {
    await bootstrap()
  } catch (error) {
    clearSession()
    resetSession()
    throw error
  }
  return session
}

export async function logout() {
  try {
    await api.post('/logout')
  } catch {
    // 服务端退出失败也清理本地
  }
  clearSession()
  resetSession()
}
