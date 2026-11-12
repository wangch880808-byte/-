import Cookies from 'js-cookie'
const TokenKey = 'Admin-Token'
const RoleKey = 'Edu-Role'
export function getToken() { return Cookies.get(TokenKey) }
export function setToken(token) { return Cookies.set(TokenKey, token, { sameSite: 'Lax' }) }
export function removeToken() { Cookies.remove(RoleKey); return Cookies.remove(TokenKey) }
export function getRole() { return Cookies.get(RoleKey) || 'admin' }
export function setRole(role) { Cookies.set(RoleKey, role, { sameSite: 'Lax' }) }
