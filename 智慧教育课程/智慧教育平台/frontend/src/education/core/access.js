export function normalizeRoute(value) {
  return String(value || '').replace(/^\/+|\/+$/g, '').replace(/\/index$/, '')
}

export function collectRoutes(items, parent = '', result = new Set()) {
  for (const item of items || []) {
    if (item.hidden) continue
    const path = String(item.path || '')
    const full = path.startsWith('/') ? path : [parent, path].filter(Boolean).join('/')
    result.add(normalizeRoute(full))
    if (item.component) result.add(normalizeRoute(item.component))
    collectRoutes(item.children, full, result)
  }
  return result
}

export function canAccess(page, identity) {
  if (!page || !identity.ready || !identity.user) return false
  if (!page.roles?.includes(identity.role)) return false
  const permissions = identity.permissions || []
  let routes
  const allows = ({ permission, serviceRoute }) =>
    (!permission || permissions.includes('*:*:*') || permissions.includes(permission)) &&
    (!serviceRoute || (routes ??= collectRoutes(identity.routes)).has(normalizeRoute(serviceRoute)))
  return allows(page) && (!page.accessAny || page.accessAny.some(allows))
}
