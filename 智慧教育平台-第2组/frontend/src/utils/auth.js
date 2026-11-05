export const TOKEN_KEY = 'zhijiao.login.token'
export const ROLE_KEY = 'zhijiao.login.role'
// 与旧页面共用同源、同标签页会话，不持久化密码。
export function getToken() { return sessionStorage.getItem(TOKEN_KEY) || '' }
export function setToken(token) { sessionStorage.setItem(TOKEN_KEY, token) }
export function removeToken() { sessionStorage.removeItem(TOKEN_KEY) }
export function getRole() { return sessionStorage.getItem(ROLE_KEY) || '' }
export function setRole(role) { sessionStorage.setItem(ROLE_KEY, role) }
export function clearSession() { removeToken(); sessionStorage.removeItem(ROLE_KEY) }
