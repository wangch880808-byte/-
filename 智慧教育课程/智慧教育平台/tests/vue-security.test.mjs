import test from 'node:test'
import assert from 'node:assert/strict'
import { canAccess, collectRoutes } from '../frontend/vue/src/core/access.js'
import { createContextController, objectId } from '../frontend/vue/src/core/context.js'
const identity = { ready: true, role: 'teacher', user: { userId: 9 }, permissions: ['education:course:list'], routes: [{ path: 'education', children: [{ path: 'course', component: 'education/course/index' }] }] }
const page = { roles: ['teacher'], permission: 'education:course:list', serviceRoute: 'education/course' }
test('业务入口必须同时满足角色、操作权限及服务端路由', () => {
  assert.equal(canAccess(page, identity), true)
  for (const change of [{ ready: false }, { role: 'student' }, { permissions: [] }, { routes: [] }, { user: null }]) assert.equal(canAccess(page, { ...identity, ...change }), false)
  assert.equal(canAccess(page, { ...identity, permissions: ['*:*:*'], routes: [] }), false)
})
test('隐藏的服务端路由不能开放业务入口', () => {
  assert.deepEqual([...collectRoutes([{ path: 'education', hidden: true, children: [{ path: 'course' }] }])], [])
})
test('上下文 ID 保留大整数精度并拒绝非法值', () => {
  assert.equal(objectId('9007199254740993'), '9007199254740993')
  for (const value of [0, -1, null, '', 9007199254740992, '1 OR 1']) assert.throws(() => objectId(value))
})
const options = { term: { termId: '3', termName: '测试学期' }, courses: [{ courseSpaceId: '11', title: '测试课程', classes: [{ classId: '12', termId: '3', bindingId: '13', className: '测试教学班', memberRole: 'teacher', canGrade: true }] }] }
const checked = { courseSpaceId: '11', classId: '12', termId: '3', bindingId: '13', memberRole: 'teacher', canGrade: true, canPublish: true }
function harness(request, selection = null) {
  const state = { current: null, courses: [], term: null, status: 'idle' }
  const values = new Map()
  let changes = 0
  const control = createContextController({ state, request, storage: { getItem: key => values.get(key), setItem: (key, val) => values.set(key, val), removeItem: key => values.delete(key) }, readSelection: () => selection, onChange: () => changes++ })
  return { state, control, values, changes: () => changes }
}
test('正常选择由 check 确认绑定与操作权限并只保存 ID', async () => {
  const h = harness(async path => ({ data: path.includes('/options') ? options : checked }))
  await h.control.initialize({ userId: '9', role: 'teacher' }); await h.control.select('11', '12')
  assert.equal(h.state.current.canGrade, true)
  assert.equal(h.state.current.bindingId, '13')
  assert.equal(Object.isFrozen(h.state.current), true)
  assert.equal([...h.values.values()][0], '{"courseSpaceId":"11","classId":"12","bindingId":"13"}')
})
test('缺少 check 授权字段时不沿用 options 的旧授权', async () => {
  const h = harness(async path => ({ data: path.includes('/options') ? options : { ...checked, canGrade: undefined, canPublish: undefined } }))
  await h.control.initialize({ userId: '9', role: 'teacher' }); await h.control.select('11', '12')
  assert.equal(h.state.current.canGrade, false); assert.equal(h.state.current.canPublish, false)
})
test('外班、错误绑定及校验对象不一致均关闭上下文', async () => {
  for (const response of [{ ...checked, classId: '99' }, { ...checked, bindingId: '99' }, { ...checked, memberRole: 'student' }]) {
    const h = harness(async path => ({ data: path.includes('/options') ? options : response }))
    await h.control.initialize({ userId: '9', role: 'teacher' }); await h.control.select('11', '12')
    assert.equal(h.state.current, null)
  }
})
test('旧深链接绑定不自动切换至新绑定', async () => {
  const h = harness(async () => ({ data: options }), { courseSpaceId: '11', classId: '12', bindingId: '99' })
  await h.control.initialize({ userId: '9', role: 'teacher' })
  assert.equal(h.state.current, null)
})
test('重复选项作为错误处理', async () => {
  const h = harness(async () => ({ data: { courses: [...options.courses, ...options.courses] } }))
  await h.control.initialize({ userId: '9', role: 'teacher' })
  assert.equal(h.state.status, 'error'); assert.equal(h.state.current, null)
})
test('空数据、接口未就绪及普通失败分别显示', async () => {
  for (const [request, expected] of [
    [async () => ({ data: { courses: [] } }), 'empty'],
    [async () => { throw { status: 404 } }, 'unavailable'],
    [async () => { throw { status: 500, message: 'No static resource private/path' } }, 'unavailable'],
    [async () => { throw new Error('network') }, 'error']
  ]) {
    const h = harness(request); await h.control.initialize({ userId: '9', role: 'teacher' })
    assert.equal(h.state.status, expected); assert.equal(h.state.message.includes('private/path'), false)
  }
})
test('管理员不调用教学上下文接口', async () => {
  let calls = 0
  const h = harness(async () => { calls++; return { data: options } })
  await h.control.initialize({ userId: '1', role: 'admin' })
  assert.equal(calls, 0); assert.equal(h.state.status, 'disabled')
})
test('切换时先清旧上下文，迟到响应不能恢复旧数据', async () => {
  const replies = []
  const h = harness(async path => path.includes('/options') ? { data: options } : new Promise(resolve => replies.push(resolve)))
  await h.control.initialize({ userId: '9', role: 'teacher' })
  const first = h.control.select('11', '12')
  const second = h.control.select('11', '12')
  replies[1]({ data: checked }); await second
  replies[0]({ data: { ...checked, canGrade: false } }); await first
  assert.equal(h.state.current.canGrade, true)
  h.control.clear(); assert.equal(h.state.current, null)
  assert.ok(h.changes() >= 4)
})
test('账号与角色隔离上下文持久化', async () => {
  const h = harness(async path => ({ data: path.includes('/options') ? options : checked }))
  await h.control.initialize({ userId: '9', role: 'teacher' }); await h.control.select('11', '12')
  await h.control.initialize({ userId: '10', role: 'teacher' })
  assert.equal(h.state.current, null)
})
let serial = 0
async function client() {
  const values = new Map()
  globalThis.location = { origin: 'http://localhost' }
  globalThis.sessionStorage = { getItem: key => values.get(key) || null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
  return import('../frontend/vue/src/api/client.js?fixture=' + (++serial))
}
function response(code, data = {}) { return { ok: true, status: 200, json: async () => ({ code, ...data }) } }
test('HTTP 与业务 401 均清理身份', async () => {
  for (const reply of [{ ok: false, status: 401 }, response(401)]) {
    const api = await client(); api.setSession('fixture-token', 'teacher')
    let denied = 0; api.setAuthHandlers({ unauthorized: () => denied++ })
    globalThis.fetch = async () => reply
    await assert.rejects(api.api.get('/fixture'), error => error.unauthorized)
    assert.equal(api.getToken(), null); assert.equal(denied, 1)
  }
})
test('HTTP 与业务 403 触发全局内容清理，静默模式交给调用方处理', async () => {
  for (const reply of [{ ok: false, status: 403 }, response(403)]) {
    const api = await client(); let cleaned = 0
    api.setAuthHandlers({ forbidden: () => cleaned++ }); globalThis.fetch = async () => reply
    await assert.rejects(api.api.get('/fixture'), error => error.forbidden)
    assert.equal(cleaned, 1)
    await assert.rejects(api.api.get('/fixture', { silent403: true }), error => error.forbidden)
    assert.equal(cleaned, 1)
  }
})
test('旧会话的迟到 401 不得清除新账号', async () => {
  const api = await client(); let reply
  api.setSession('old', 'teacher')
  globalThis.fetch = () => new Promise(resolve => { reply = resolve })
  const pending = api.api.get('/fixture')
  api.setSession('new', 'student')
  reply(response(401))
  await assert.rejects(pending, error => error.cancelled)
  assert.equal(api.getToken(), 'new')
})
test('令牌不发送到外部站点', async () => {
  const api = await client(); let calls = 0
  globalThis.fetch = async () => { calls++; return response(200) }
  await assert.rejects(api.api.get('https://external.example/fixture'))
  assert.equal(calls, 0)
})
test('取消页面请求不会取消上下文请求', async () => {
  const api = await client()
  globalThis.fetch = async (_url, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(Object.assign(new Error('abort'), { name: 'AbortError' })))
    if (_url.includes('context')) resolve(response(200))
  })
  const pageRequest = api.api.get('/fixture')
  const contextRequest = api.api.get('/context', { scope: 'context' })
  api.cancelRequests('page')
  await assert.rejects(pageRequest, error => error.cancelled)
  assert.equal((await contextRequest).code, 200)
})
