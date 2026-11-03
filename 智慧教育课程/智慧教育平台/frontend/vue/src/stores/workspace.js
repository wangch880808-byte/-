import { reactive } from 'vue'
import { api, cancelRequests } from '../api/client'
import { createContextController } from '../core/context'
import { session } from './session'
export const workspace = reactive({ blocked: '', epoch: 0, tabs: [], notifications: [] })
export const context = reactive({ current: null, pending: null, courses: [], term: null, status: 'idle', message: '' })
export function resetView(message = '') {
  cancelRequests('page'); cancelRequests('header')
  workspace.epoch++; workspace.blocked = message; workspace.notifications = []
}
export function forbidView(message) {
  contexts.suspend()
  resetView(message)
}
function readSelection() {
  const params = new URLSearchParams((window.location.hash.split('?')[1] || ''))
  return params.has('courseSpaceId') || params.has('classId')
    ? Object.fromEntries(['courseSpaceId', 'classId', 'bindingId'].map(key => [key, params.get(key)])) : null
}
function writeSelection(selection) {
  const [path, query = ''] = window.location.hash.slice(1).split('?')
  const params = new URLSearchParams(query)
  for (const key of ['courseSpaceId', 'classId', 'bindingId']) {
    if (selection?.[key]) params.set(key, selection[key]); else params.delete(key)
  }
  const suffix = params.toString()
  window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search + '#' + (path || '/') + (suffix ? '?' + suffix : ''))
}
export const contexts = createContextController({ state: context, storage: sessionStorage,
  request: (path, options) => api.get(path, { ...options, scope: 'context' }),
  readSelection, writeSelection, onChange: () => resetView() })
let contextIdentity = ''
export async function prepareContext(force = false) {
  const key = session.user?.userId + ':' + session.role
  if (force || key !== contextIdentity || context.status === 'idle') {
    contextIdentity = key
    await contexts.initialize({ userId: String(session.user.userId), role: session.role })
  }
}
export function suspendWorkspace() { resetView(); contexts.suspend(); workspace.tabs = []; contextIdentity = '' }
