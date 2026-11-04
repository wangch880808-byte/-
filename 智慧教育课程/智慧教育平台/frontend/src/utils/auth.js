export const TOKEN_KEY = 'zhijiao.login.token'
export const ROLE_KEY = 'zhijiao.login.role'
// 本机联调用 localStorage，关标签后仍保持登录；不持久化密码。
const store = window.localStorage
export function getToken() { return store.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || '' }
export function setToken(token) {
  store.setItem(TOKEN_KEY, token)
  sessionStorage.removeItem(TOKEN_KEY)
}
export function removeToken() {
  store.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
}
export function getRole() { return store.getItem(ROLE_KEY) || sessionStorage.getItem(ROLE_KEY) || '' }
export function setRole(role) {
  store.setItem(ROLE_KEY, role)
  sessionStorage.removeItem(ROLE_KEY)
}
export function clearSession() { removeToken(); store.removeItem(ROLE_KEY); sessionStorage.removeItem(ROLE_KEY) }
