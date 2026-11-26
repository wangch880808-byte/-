/**
 * 6-03 管理员教学指标 / 管理员待办生产入口回归。
 *
 * 通过 `serve-dist.mjs` 加载实际 Vite 构建，驱动实际 Vue Router、公共
 * permission 守卫、canOpen 和 AppLayout 侧边栏；接口只使用 Playwright
 * 路由夹具，不连接后端、数据库或真实登录。
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

function routeTargets(role, targets) {
  const prefix = role === 'admin' ? 'admin/' : role + '/'
  return {
    code: 200,
    msg: '操作成功',
    data: [{
      path: 'education',
      component: 'Layout',
      children: [
        ...targets.map(target => ({
          path: prefix + target,
          component: 'education/' + (role === 'admin' ? 'overview/Admin' : 'overview/Entry')
        })),
        { path: 'course', component: 'education/CourseListPage' }
      ]
    }]
  }
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
    const url = new URL(route.request().url())
    requests.push({
      kind,
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
    // 第一组管理总览入口；不要与 /education/admin/overview 混淆。
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

async function state() {
  return {
    role: 'admin',
    userId: '9302',
    permissions: [...ADMIN_PERMISSIONS],
    targets: ['overview', 'todos'],
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

await run('admin 侧边栏和两个页面实际可进入，/admin/todos 不再重定向', async () => {
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

  await openHash(page, '/admin')
  await waitHash(page, '/admin/overview')
  await page.waitForSelector('.admin-overview', { timeout: 15000 })
  assert.ok(requests.some(request => request.kind === 'first-overview'),
    '管理员首页应调用第一组 /education/overview')
  assert.equal(requests.some(request => request.kind === 'admin-overview'), false,
    '管理员首页不应调用第 6 组管理员指标')

  // 管理员侧边栏分组默认折叠；按真实交互先展开“工作台”，再点击菜单锚点。
  await page.locator('.menu-group .menu-title', { hasText: '工作台' }).first().click()
  const metrics = page.locator('.menu-group .menu-item', { hasText: '管理员教学指标' }).first()
  await metrics.waitFor({ state: 'visible', timeout: 10000 })
  await metrics.click()
  await waitHash(page, '/admin/teaching-metrics')
  await board(page)
  assert.equal(await page.locator('.ov-card').count(), 5)
  assert.ok(requests.some(request => request.kind === 'admin-overview'))
  assert.equal(requests.some(request => request.kind === 'admin-todos'), false)

  const todos = page.locator('.menu-group .menu-item', { hasText: '管理员待办' }).first()
  await todos.waitFor({ state: 'visible', timeout: 10000 })
  await todos.click()
  await waitHash(page, '/admin/todos')
  await board(page)
  assert.match(await page.locator('.overview-board').innerText(), /待办口径待确认/)
  assert.equal(locationHash(await page.evaluate(() => location.hash)).includes('overview-tasks'), false)
  assert.equal(requests.some(request => request.kind === 'admin-todos'), true)
  assert.deepEqual(pageErrors, [])
  await context.close()
})

await run('管理员两页直接访问，/admin/overview 保持第一组页面', async () => {
  const s = await state()
  const context = await newContext(browser, 'admin', ADMIN_PERMISSIONS)
  const page = await context.newPage()
  const requests = await installFixture(page, s)

  await openHash(page, '/admin/teaching-metrics')
  await waitHash(page, '/admin/teaching-metrics')
  await board(page)
  assert.equal(await page.locator('.ov-card').count(), 5)
  assert.equal(requests.some(request => request.kind === 'first-overview'), false)

  await openHash(page, '/admin/todos')
  await waitHash(page, '/admin/todos')
  await board(page)
  assert.match(await page.locator('.overview-board').innerText(), /待办口径待确认/)
  assert.equal(requests.some(request => request.kind === 'admin-overview'), true)

  await openHash(page, '/admin/overview')
  await waitHash(page, '/admin/overview')
  await page.waitForSelector('.admin-overview', { timeout: 15000 })
  assert.ok(requests.some(request => request.kind === 'first-overview'))
  assert.equal(requests.some(request => request.kind === 'admin-overview'), true)
  await context.close()
})

await run('缺权限、缺 serviceRoute、登录后撤权均稳定拒绝且不调用管理员接口', async () => {
  const cases = [
    {
      label: '缺权限',
      prepare: async s => { s.permissions = [] }
    },
    {
      label: '缺 serviceRoute',
      prepare: async s => { s.permissions = [...ADMIN_PERMISSIONS]; s.targets = ['todos'] }
    }
  ]
  for (const item of cases) {
    const s = await state()
    await item.prepare(s)
    const context = await newContext(browser, 'admin', s.permissions)
    const page = await context.newPage()
    const requests = await installFixture(page, s)
    await openHash(page, '/admin/teaching-metrics')
    await waitHash(page, '/admin/forbidden')
    await sleep(900)
    assert.equal(locationHash(await page.evaluate(() => location.hash)).includes('/admin/forbidden'), true,
      item.label + ' 不应跳转循环')
    assert.equal(requests.some(request => request.kind === 'admin-overview'), false,
      item.label + ' 不应发出管理员总览请求')
    await context.close()
  }

  const s = await state()
  const context = await newContext(browser, 'admin', ADMIN_PERMISSIONS)
  const page = await context.newPage()
  const requests = await installFixture(page, s)
  await openHash(page, '/admin')
  await waitHash(page, '/admin/overview')
  await page.waitForSelector('.admin-overview', { timeout: 15000 })
  requests.length = 0
  s.permissions = []
  await openHash(page, '/admin/teaching-metrics')
  await waitHash(page, '/admin/forbidden')
  await sleep(900)
  assert.equal(requests.some(request => request.kind === 'admin-overview'), false,
    '撤权后不应调用管理员总览')
  await context.close()
})

await run('教师、学生直达管理员两页被拒绝且不发管理员总览请求', async () => {
  for (const role of ['teacher', 'student']) {
    const s = await state()
    s.role = role
    s.permissions = role === 'teacher' ? [...TEACHER_PERMISSIONS] : [...STUDENT_PERMISSIONS]
    s.targets = role === 'teacher' ? ['overview'] : ['today-todos']
    const context = await newContext(browser, role, s.permissions)
    const page = await context.newPage()
    const requests = await installFixture(page, s)
    await openHash(page, '/admin/teaching-metrics')
    await page.waitForFunction(expected => location.hash.includes(expected), '/' + role,
      { timeout: 12000 })
    assert.equal(requests.some(request => request.kind === 'admin-overview'), false,
      role + ' 不应发出管理员总览请求')
    assert.equal(locationHash(await page.evaluate(() => location.hash)).includes('/admin'), false)
    await context.close()
  }
})

await run('教师教学总览仍可注册、打开，未产生重复注册错误', async () => {
  const s = await state()
  s.role = 'teacher'
  s.permissions = [...TEACHER_PERMISSIONS]
  s.targets = ['overview']
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
  await openHash(page, '/teacher/teaching-overview')
  await waitHash(page, '/teacher/teaching-overview')
  await board(page)
  assert.equal(await page.locator('.ov-card').count(), 6)
  assert.ok(requests.some(request => request.kind === 'teacher-overview'))
  assert.deepEqual(pageErrors, [])
  await context.close()
})

await run('管理员待办 NO_DATA、503 和 390px 布局保留原表现', async () => {
  const s = await state()
  const context = await newContext(browser, 'admin', ADMIN_PERMISSIONS)
  const page = await context.newPage()
  await installFixture(page, s)
  await openHash(page, '/admin/todos')
  await waitHash(page, '/admin/todos')
  await board(page)
  const text = await page.locator('.overview-board').innerText()
  assert.match(text, /待办口径待确认/)
  assert.doesNotMatch(text, /0 条|全部完成/)
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
  await installFixture(errorPage, errorState)
  await openHash(errorPage, '/admin/todos')
  await waitHash(errorPage, '/admin/todos')
  await errorPage.waitForSelector('.ov-state.warn', { timeout: 15000 })
  assert.match(await errorPage.locator('.ov-state.warn').innerText(), /总览暂不可用/)
  assert.doesNotMatch(await errorPage.locator('body').innerText(), /待办口径待确认/)
  await errorContext.close()
})

await browser.close()
const failed = results.filter(result => !result.ok)
console.log(`\n${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length > 0) {
  for (const item of failed) console.error(item.name, item.error)
  process.exitCode = 1
}

function locationHash(hash) {
  return String(hash || '')
}
