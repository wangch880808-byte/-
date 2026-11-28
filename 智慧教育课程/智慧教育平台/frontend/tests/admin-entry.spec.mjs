/**
 * 6-03 管理员教学指标 / 管理员待办生产入口回归。
 *
 * 通过 `serve-dist.mjs` 加载实际 Vite 构建，驱动实际 Vue Router、公共
 * permission 守卫、canOpen 和 AppLayout 自动侧边栏；接口只使用 Playwright
 * 路由夹具，不连接后端、数据库或真实登录。
 *
 * 每个导航/点击操作前记录 `requests.length`，只断言本次操作新增的 GET
 * 请求，避免历史请求满足断言。管理员两个页面分别覆盖缺权限、缺自身
 * serviceRoute、登录后撤权、教师直达和学生直达。
 *
 * 运行：
 *   pnpm build
 *   node tests/serve-dist.mjs
 *   ADMIN_ENTRY_TEST_BASE=http://127.0.0.1:4174 node tests/admin-entry.spec.mjs
 */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'

const BASE = process.env.ADMIN_ENTRY_TEST_BASE || 'http://127.0.0.1:4174'
const APP = new URL('/vue/', BASE).toString()
const ROLE_COOKIE = 'Edu-Role'
const TOKEN_COOKIE = 'Admin-Token'
const ADMIN_PERMISSIONS = ['education:overview:admin']
const TEACHER_PERMISSIONS = ['education:overview:teacher']
const STUDENT_PERMISSIONS = ['education:overview:student']
const ADMIN_OVERVIEW_PATH = '/education/admin/overview'
const ADMIN_TODOS_PATH = '/education/admin/todos'
const FIRST_OVERVIEW_PATH = '/education/overview'
const TEACHER_OVERVIEW_PATH = '/education/teacher/overview'
const WINDOW = { startsAt: '2026-11-18T00:00:00.000Z', endsAt: '2026-11-19T00:00:00.000Z' }
const AS_OF = '2026-11-18T06:00:00.000Z'
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

const ADMIN_CODES = [
  'admin.class.count',
  'admin.student.count',
  'admin.session.running',
  'admin.assignment.pending',
  'admin.grade.pending'
]
const TEACHER_CODES = [
  'teacher.class.count',
  'teacher.student.count',
  'teacher.session.running',
  'teacher.assignment.pending',
  'teacher.grade.pending',
  'teacher.notice.unread'
]
const SOURCE_BY_CODE = {
  'admin.class.count': 'course',
  'admin.student.count': 'course',
  'admin.session.running': 'classroom',
  'admin.assignment.pending': 'assignment',
  'admin.grade.pending': 'grade',
  'teacher.class.count': 'course',
  'teacher.student.count': 'course',
  'teacher.session.running': 'classroom',
  'teacher.assignment.pending': 'assignment',
  'teacher.grade.pending': 'grade',
  'teacher.notice.unread': 'notice'
}

function json(route, body) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body)
  })
}

function info(role, permissions, userId = '9302') {
  return {
    code: 200,
    msg: '操作成功',
    user: {
      userId,
      userName: role + '-entry',
      nickName: role + '入口夹具',
      avatar: ''
    },
    roles: [role],
    permissions
  }
}

/**
 * 第一组实际提供的 getRouters 子项：
 * admin/overview -> education/admin/overview
 * admin/todos -> education/admin/todos
 * teacher/overview -> education/teacher/overview
 * student/today-todos -> education/student/today-todos
 */
function routeTargets(role, targets) {
  return {
    code: 200,
    msg: '操作成功',
    data: [{
      path: 'education',
      component: 'Layout',
      children: [
        ...targets,
        { path: 'course', component: 'education/CourseListPage' }
      ]
    }]
  }
}

function adminTargets({ metrics = true, todos = true } = {}) {
  const targets = []
  if (metrics) targets.push({ path: 'admin/overview', component: 'education/admin/overview' })
  if (todos) targets.push({ path: 'admin/todos', component: 'education/admin/todos' })
  return targets
}

function roleTargets(role) {
  if (role === 'teacher') return [{ path: 'teacher/overview', component: 'education/teacher/overview' }]
  return [{ path: 'student/today-todos', component: 'education/student/today-todos' }]
}

function metric(code, value, overrides = {}) {
  return {
    metricCode: code,
    value,
    unit: 'COUNT',
    status: 'OK',
    source: SOURCE_BY_CODE[code] || 'course',
    sampleCount: null,
    actionPath: null,
    ...overrides
  }
}

function adminResponse() {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      asOf: AS_OF,
      window: WINDOW,
      items: ADMIN_CODES.map((code, index) => metric(code, index + 1))
    }
  }
}

function teacherResponse() {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      asOf: AS_OF,
      window: WINDOW,
      items: TEACHER_CODES.map((code, index) => metric(code, index + 1))
    }
  }
}

function todosResponse(overrides = {}) {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      asOf: AS_OF,
      window: WINDOW,
      items: [{
        metricCode: 'admin.todo.pending',
        value: null,
        unit: 'COUNT',
        status: 'NO_DATA',
        source: 'pending_product_confirmation',
        sampleCount: null,
        actionPath: null,
        ...overrides
      }]
    }
  }
}

function firstOverviewResponse() {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      schoolId: null,
      schools: [],
      totals: {
        studentCount: 1,
        teacherCount: 1,
        collegeCount: 1,
        administrativeClassCount: 1,
        unassignedStudentCount: 0,
        unassignedTeacherCount: 0
      },
      currentTerm: null
    }
  }
}

async function installFixture(page, state) {
  const requests = []
  const record = (kind, route) => {
    const request = route.request()
    const url = new URL(request.url())
    requests.push({
      kind,
      method: request.method(),
      path: url.pathname,
      query: Object.fromEntries(url.searchParams.entries())
    })
  }

  await page.route('**/getInfo', route => json(route, info(
    state.role, state.permissions, state.userId)))
  await page.route('**/getRouters', route => json(route,
    routeTargets(state.role, state.targets)))
  await page.route('**/education/context/options*', route => json(route, {
    code: 200, msg: '操作成功', data: { term: null, courses: [] }
  }))
  await page.route('**/education/context/check*', route => json(route, {
    code: 200,
    msg: '操作成功',
    data: {
      courseSpaceId: '101', classId: '201', termId: '1', bindingId: '9001',
      memberRole: state.role, canPublish: false, canGrade: false
    }
  }))
  await page.route('**/education/scope/term/current', route => json(route, {
    code: 200, msg: '操作成功',
    data: { termId: '1', name: '2026-2027-1', code: '2026-2027-1' }
  }))
  await page.route('**/education/overview*', route => {
    record('first-overview', route)
    return json(route, firstOverviewResponse())
  })
  await page.route('**/education/admin/overview*', route => {
    record('admin-overview', route)
    return json(route, state.adminResponse)
  })
  await page.route('**/education/admin/todos*', route => {
    record('admin-todos', route)
    return json(route, state.todosResponse)
  })
  await page.route('**/education/teacher/overview*', route => {
    record('teacher-overview', route)
    return json(route, state.teacherResponse)
  })
  await page.route('**/education/student/today-todos*', route => {
    record('student-overview', route)
    return json(route, state.studentResponse)
  })
  return requests
}

async function newContext(browser, role, permissions) {
  const context = await browser.newContext({
    viewport: { width: 1280, height: 900 },
    timezoneId: 'Asia/Shanghai'
  })
  await context.addCookies([
    { name: TOKEN_COOKIE, value: 'fixture-token', url: BASE },
    { name: ROLE_COOKIE, value: role, url: BASE }
  ])
  return context
}

async function openHash(page, hash) {
  await page.goto(APP + '#' + hash, { waitUntil: 'domcontentloaded' })
}

async function waitHash(page, fragment) {
  await page.waitForFunction(expected => location.hash.includes(expected), fragment,
    { timeout: 12000 })
}

async function board(page) {
  await page.waitForSelector('.overview-board', { timeout: 15000 })
}

async function expandWorkbench(page) {
  await page.locator('.menu-group .menu-title', { hasText: '工作台' }).first().click()
}

function newRequests(requests, start) {
  return requests.slice(start)
}

function assertGet(requests, start, path, label) {
  const calls = newRequests(requests, start)
    .filter(request => request.method === 'GET' && request.path === path)
  assert.ok(calls.length >= 1, `${label} 应新增 GET ${path}，实际：`
    + JSON.stringify(newRequests(requests, start)))
}

function assertNoAdminGets(requests, start, label) {
  const calls = newRequests(requests, start).filter(request =>
    request.method === 'GET'
    && (request.path === ADMIN_OVERVIEW_PATH || request.path === ADMIN_TODOS_PATH))
  assert.deepEqual(calls, [], `${label} 不应新增管理员总览请求`)
}

function assertNoAdminGetsExcept(requests, start, allowedPath, label) {
  const calls = newRequests(requests, start).filter(request =>
    request.method === 'GET'
    && (request.path === ADMIN_OVERVIEW_PATH || request.path === ADMIN_TODOS_PATH)
    && request.path !== allowedPath)
  assert.deepEqual(calls, [], `${label} 不应新增其他管理员总览请求`)
}

async function state() {
  return {
    role: 'admin',
    userId: '9302',
    permissions: [...ADMIN_PERMISSIONS],
    targets: adminTargets(),
    adminResponse: adminResponse(),
    todosResponse: todosResponse(),
    teacherResponse: teacherResponse(),
    studentResponse: teacherResponse()
  }
}

const results = []
async function run(name, fn) {
  try {
    await fn()
    results.push({ name, ok: true })
  } catch (error) {
    results.push({ name, ok: false, error })
  }
}

const browser = await chromium.launch({ channel: 'msedge', headless: true })

await run('普通管理员通过自动侧边栏进入两页，并保持第一组总览端点独立', async () => {
  const s = await state()
  const context = await newContext(browser, 'admin', ADMIN_PERMISSIONS)
  const page = await context.newPage()
  const requests = await installFixture(page, s)
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(String(error)))
  page.on('console', message => {
    if (message.type() === 'error' && /业务页面位置重复/.test(message.text())) {
      pageErrors.push(message.text())
    }
  })

  let start = requests.length
  await openHash(page, '/admin')
  await waitHash(page, '/admin/overview')
  await page.waitForSelector('.admin-overview', { timeout: 15000 })
  assertGet(requests, start, FIRST_OVERVIEW_PATH, '管理员首页')
  assertNoAdminGets(requests, start, '管理员首页')

  start = requests.length
  await expandWorkbench(page)
  const metrics = page.locator('.menu-group .menu-item', { hasText: '管理员教学指标' }).first()
  await metrics.waitFor({ state: 'visible', timeout: 10000 })
  await metrics.click()
  await waitHash(page, '/admin/teaching-metrics')
  await board(page)
  assert.equal(await page.locator('.ov-card').count(), 5)
  assertGet(requests, start, ADMIN_OVERVIEW_PATH, '侧边栏进入教学指标')
  assertNoAdminGetsExcept(requests, start, ADMIN_OVERVIEW_PATH, '侧边栏进入教学指标')

  start = requests.length
  const todos = page.locator('.menu-group .menu-item', { hasText: '管理员待办' }).first()
  await todos.waitFor({ state: 'visible', timeout: 10000 })
  await todos.click()
  await waitHash(page, '/admin/todos')
  await board(page)
  assert.match(await page.locator('.overview-board').innerText(), /待办口径待确认/)
  assertGet(requests, start, ADMIN_TODOS_PATH, '侧边栏进入管理员待办')
  assertNoAdminGetsExcept(requests, start, ADMIN_TODOS_PATH, '侧边栏进入管理员待办')
  assert.equal((await page.evaluate(() => location.hash)).includes('overview-tasks'), false)
  assert.deepEqual(pageErrors, [])
  await context.close()
})

await run('两页可直接访问，第一组 /admin/overview 仍使用自己的端点', async () => {
  const s = await state()
  const context = await newContext(browser, 'admin', ADMIN_PERMISSIONS)
  const page = await context.newPage()
  const requests = await installFixture(page, s)

  let start = requests.length
  await openHash(page, '/admin/teaching-metrics')
  await waitHash(page, '/admin/teaching-metrics')
  await board(page)
  assert.equal(await page.locator('.ov-card').count(), 5)
  assertGet(requests, start, ADMIN_OVERVIEW_PATH, '直接访问教学指标')
  assertNoAdminGetsExcept(requests, start, ADMIN_OVERVIEW_PATH, '直接访问教学指标')

  start = requests.length
  await openHash(page, '/admin/todos')
  await waitHash(page, '/admin/todos')
  await board(page)
  assertGet(requests, start, ADMIN_TODOS_PATH, '直接访问管理员待办')
  assertNoAdminGetsExcept(requests, start, ADMIN_TODOS_PATH, '直接访问管理员待办')

  start = requests.length
  await openHash(page, '/admin/overview')
  await waitHash(page, '/admin/overview')
  await page.waitForSelector('.admin-overview', { timeout: 15000 })
  assertGet(requests, start, FIRST_OVERVIEW_PATH, '直接访问第一组管理总览')
  assertNoAdminGets(requests, start, '直接访问第一组管理总览')
  await context.close()
})

await run('两页缺权限或缺自身 serviceRoute 时拒绝，且不新增管理员 API', async () => {
  const targets = [
    { path: '/admin/teaching-metrics', own: ADMIN_OVERVIEW_PATH, other: ADMIN_TODOS_PATH },
    { path: '/admin/todos', own: ADMIN_TODOS_PATH, other: ADMIN_OVERVIEW_PATH }
  ]
  for (const target of targets) {
    for (const mode of ['permission', 'route']) {
      const s = await state()
      if (mode === 'permission') {
        s.permissions = []
      } else {
        s.targets = target.other === ADMIN_TODOS_PATH
          ? adminTargets({ metrics: false, todos: true })
          : adminTargets({ metrics: true, todos: false })
      }
      const context = await newContext(browser, 'admin', s.permissions)
      const page = await context.newPage()
      const requests = await installFixture(page, s)
      const start = requests.length
      await openHash(page, target.path)
      await waitHash(page, '/admin/forbidden')
      await sleep(900)
      assert.equal((await page.evaluate(() => location.hash)).includes('/admin/forbidden'), true,
        `${target.path} ${mode} 不应跳转循环`)
      assertNoAdminGets(requests, start, `${target.path} ${mode}`)
      await context.close()
    }
  }
})

await run('缺 serviceRoute 时另一页面锚点仍在，且拒绝不新增管理员 API', async () => {
  const cases = [
    {
      missing: 'metrics',
      target: '/admin/teaching-metrics',
      visible: '管理员待办',
      targets: adminTargets({ metrics: false, todos: true })
    },
    {
      missing: 'todos',
      target: '/admin/todos',
      visible: '管理员教学指标',
      targets: adminTargets({ metrics: true, todos: false })
    }
  ]
  for (const item of cases) {
    const s = await state()
    s.targets = item.targets
    const context = await newContext(browser, 'admin', ADMIN_PERMISSIONS)
    const page = await context.newPage()
    const requests = await installFixture(page, s)
    let start = requests.length
    await openHash(page, '/admin')
    await waitHash(page, '/admin/overview')
    await page.waitForSelector('.admin-overview', { timeout: 15000 })
    assertGet(requests, start, FIRST_OVERVIEW_PATH, '缺路由首页')
    await expandWorkbench(page)
    const visible = page.locator('.menu-group .menu-item', { hasText: item.visible }).first()
    await visible.waitFor({ state: 'visible', timeout: 10000 })
    assert.equal(await page.locator('.menu-group .menu-item', { hasText: '管理员教学指标' }).count()
      + await page.locator('.menu-group .menu-item', { hasText: '管理员待办' }).count(), 1,
      item.missing + ' 缺路由时应只保留另一页面锚点')

    start = requests.length
    await openHash(page, item.target)
    await waitHash(page, '/admin/forbidden')
    await sleep(900)
    assert.equal((await page.evaluate(() => location.hash)).includes('/admin/forbidden'), true)
    assertNoAdminGets(requests, start, item.missing + ' 缺路由直达')
    await context.close()
  }
})

await run('登录后撤权通过 SPA 链接导航重新核验，两页均稳定拒绝', async () => {
  for (const item of [
    { label: '管理员教学指标', path: '/admin/teaching-metrics' },
    { label: '管理员待办', path: '/admin/todos' }
  ]) {
    const s = await state()
    const context = await newContext(browser, 'admin', ADMIN_PERMISSIONS)
    const page = await context.newPage()
    const requests = await installFixture(page, s)
    const start0 = requests.length
    await openHash(page, '/admin')
    await waitHash(page, '/admin/overview')
    await page.waitForSelector('.admin-overview', { timeout: 15000 })
    assertGet(requests, start0, FIRST_OVERVIEW_PATH, '撤权前首页')
    await expandWorkbench(page)
    const link = page.locator('.menu-group .menu-item', { hasText: item.label }).first()
    await link.waitFor({ state: 'visible', timeout: 10000 })

    // 点击已渲染的 SPA 链接，撤权后由现有守卫重新 bootstrap；不整页刷新。
    const start = requests.length
    s.permissions = []
    await link.click()
    await waitHash(page, '/admin/forbidden')
    await sleep(900)
    assert.equal((await page.evaluate(() => location.hash)).includes('/admin/forbidden'), true,
      item.label + ' 撤权后不应跳转循环')
    assertNoAdminGets(requests, start, item.label + ' 撤权')
    await context.close()
  }
})

await run('教师、学生直达两页均被拒绝，教师总览仍可注册和打开', async () => {
  for (const role of ['teacher', 'student']) {
    for (const target of ['/admin/teaching-metrics', '/admin/todos']) {
      const s = await state()
      s.role = role
      s.permissions = role === 'teacher' ? [...TEACHER_PERMISSIONS] : [...STUDENT_PERMISSIONS]
      s.targets = roleTargets(role)
      const context = await newContext(browser, role, s.permissions)
      const page = await context.newPage()
      const requests = await installFixture(page, s)
      const start = requests.length
      await openHash(page, target)
      await page.waitForFunction(expected => location.hash.includes(expected), '/' + role,
        { timeout: 12000 })
      await sleep(700)
      assert.equal((await page.evaluate(() => location.hash)).includes('/admin'), false,
        role + ' 不应停留在管理员路径')
      assertNoAdminGets(requests, start, role + ' 直达 ' + target)
      await context.close()
    }
  }

  const s = await state()
  s.role = 'teacher'
  s.permissions = [...TEACHER_PERMISSIONS]
  s.targets = roleTargets('teacher')
  const context = await newContext(browser, 'teacher', s.permissions)
  const page = await context.newPage()
  const requests = await installFixture(page, s)
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(String(error)))
  page.on('console', message => {
    if (message.type() === 'error' && /业务页面位置重复/.test(message.text())) {
      pageErrors.push(message.text())
    }
  })
  const start = requests.length
  await openHash(page, '/teacher/teaching-overview')
  await waitHash(page, '/teacher/teaching-overview')
  await board(page)
  assert.equal(await page.locator('.ov-card').count(), 6)
  assertGet(requests, start, TEACHER_OVERVIEW_PATH, '教师教学总览')
  assert.deepEqual(pageErrors, [])
  await context.close()
})

await run('管理员待办 NO_DATA、503 和 390px 布局保留原表现', async () => {
  const s = await state()
  const context = await newContext(browser, 'admin', ADMIN_PERMISSIONS)
  const page = await context.newPage()
  const requests = await installFixture(page, s)
  const start = requests.length
  await openHash(page, '/admin/todos')
  await waitHash(page, '/admin/todos')
  await board(page)
  const text = await page.locator('.overview-board').innerText()
  assert.match(text, /待办口径待确认/)
  assert.doesNotMatch(text, /0 条|全部完成/)
  assertGet(requests, start, ADMIN_TODOS_PATH, '待办 NO_DATA')
  await page.setViewportSize({ width: 390, height: 844 })
  const overflow = await page.evaluate(() => {
    const board = document.querySelector('.overview-board')
    return {
      boardScrollWidth: board.scrollWidth,
      boardClientWidth: board.clientWidth,
      documentScrollWidth: document.documentElement.scrollWidth,
      documentClientWidth: document.documentElement.clientWidth
    }
  })
  assert.ok(overflow.boardScrollWidth <= overflow.boardClientWidth + 1, JSON.stringify(overflow))
  assert.ok(overflow.documentScrollWidth <= overflow.documentClientWidth + 1, JSON.stringify(overflow))
  await context.close()

  const errorState = await state()
  errorState.todosResponse = { code: 503, msg: '服务暂不可用' }
  const errorContext = await newContext(browser, 'admin', ADMIN_PERMISSIONS)
  const errorPage = await errorContext.newPage()
  const errorRequests = await installFixture(errorPage, errorState)
  const errorStart = errorRequests.length
  await openHash(errorPage, '/admin/todos')
  await waitHash(errorPage, '/admin/todos')
  await errorPage.waitForSelector('.ov-state.warn', { timeout: 15000 })
  assert.match(await errorPage.locator('.ov-state.warn').innerText(), /总览暂不可用/)
  assert.doesNotMatch(await errorPage.locator('body').innerText(), /待办口径待确认/)
  assertGet(errorRequests, errorStart, ADMIN_TODOS_PATH, '待办 503')
  await errorContext.close()
})

await browser.close()
const failed = results.filter(result => !result.ok)
console.log(`\n${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length > 0) {
  for (const item of failed) console.error(item.name, item.error)
  process.exitCode = 1
}
