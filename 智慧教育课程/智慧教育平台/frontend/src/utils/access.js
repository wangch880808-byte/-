export const ROLES = ['admin', 'teacher', 'student']
export const ROLE_LABELS = { admin: '管理员', teacher: '教师', student: '学生' }
export const ROLE_HOME = {
  admin: '/system/user', teacher: '/education/teacher/quick-start', student: '/education/student/join'
}
export function pickRole(roles = [], preferred = '') {
  return ROLES.includes(preferred) && roles.includes(preferred)
    ? preferred : ROLES.find(role => roles.includes(role)) || ''
}
export function hasPermission(permissions = [], required) {
  return !required || permissions.includes('*:*:*') || permissions.includes(required)
}
export function canAccess(meta = {}, roles = [], permissions = []) {
  return (!meta.roles || meta.roles.some(role => roles.includes(role))) && hasPermission(permissions, meta.permission)
}
// 登录跳转只接受本站业务路径，拒绝外部 URL 和登录页循环。
export function safeRedirect(value, fallback) {
  return typeof value === 'string' && /^\/(education|system)\//.test(value) && !/[\\\r\n]/.test(value) ? value : fallback
}
