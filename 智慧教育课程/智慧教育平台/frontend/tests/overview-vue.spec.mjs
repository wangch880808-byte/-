/**
 * 6-03 教师教学总览 / 学生学习总览 Vue 夹具。
 *
 * 只使用 Playwright 路由夹具，不连接后端、数据库或真实登录。运行：
 *   npm run build
 *   node tests/serve-dist.mjs
 *   OVERVIEW_TEST_BASE=http://127.0.0.1:4174 node tests/overview-vue.spec.mjs
 */
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { chromium } from 'playwright'

const BASE = process.env.OVERVIEW_TEST_BASE || 'http://127.0.0.1:4174'
const SCREENSHOT_DIR = process.env.OVERVIEW_SCREENSHOT_DIR || ''
const ROLE_COOKIE = 'Edu-Role'
const TOKEN_COOKIE = 'Admin-Token'
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

const TEACHER_PERMISSIONS = ['education:overview:teacher', 'education:scope:read']
const STUDENT_PERMISSIONS = ['education:overview:student', 'education:scope:read']

const TEACHER_CODES = [
  'teacher.class.count',
  'teacher.student.count',
  'teacher.session.running',
  'teacher.assignment.pending',
  'teacher.grade.pending',
  'teacher.notice.unread'
]
const STUDENT_CODES = [
  'student.class.count',
  'student.session.active',
  'student.assignment.due',
  'student.notice.unread'
]
const SOURCE_BY_CODE = {
  'teacher.class.count': 'course',
  'teacher.student.count': 'course',
  'teacher.session.running': 'classroom',
  'teacher.assignment.pending': 'assignment',
  'teacher.grade.pending': 'grade',
  'teacher.notice.unread': 'notice',
  'student.class.count': 'course',
  'student.session.active': 'classroom',
  'student.assignment.due': 'assignment',
  'student.notice.unread': 'notice'
}

const WINDOW = {
  startsAt: '2026-11-18T00:00:00.000Z',
  endsAt: '2026-11-19T00:00:00.000Z'
}
const AS_OF = '2026-11-18T06:00:00.000Z'

function json(route, body) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body)
  })
}

function info(role, permissions) {
  return {
    code: 200,
    msg: '操作成功',
    user: {
      userId: role === 'teacher' ? '9102' : '9101',
      userName: role === 'teacher' ? 'teacher-overview' : 'student-overview',
      nickName: role === 'teacher' ? '教师总览' : '学生总览',
      avatar: ''
    },
    roles: [role],
    permissions
  }
}

function routers(role, includeOverview) {
  const route = role === 'teacher'
    ? { path: 'teacher/overview', component: 'education/teacher/TeacherOverview' }
    : { path: 'student/today-todos', component: 'education/student/StudentOverview' }
  return {
    code: 200,
    msg: '操作成功',
    data: [{
      path: 'education',
      component: 'Layout',
      children: [
        ...(includeOverview ? [route] : []),
        { path: 'course', component: 'education/CourseListPage' }
      ]
    }]
  }
}

function item(code, value, status = 'OK', overrides = {}) {
  return {
    metricCode: code,
    value: status === 'OK' ? value : null,
    unit: 'COUNT',
    status,
    source: SOURCE_BY_CODE[code] || 'course',
    sampleCount: null,
    actionPath: '/candidate/path',
    ...overrides
  }
}

function teacherItems(values = {}) {
  const defaults = {
    'teacher.class.count': 3,
    'teacher.student.count': 86,
    'teacher.session.running': 1,
    'teacher.assignment.pending': 12,
    'teacher.grade.pending': 5,
    'teacher.notice.unread': 4
  }
  return TEACHER_CODES.map(code => item(code, values[code] === undefined ? defaults[code] : values[code]))
}

function studentItems(values = {}) {
  const defaults = {
    'student.class.count': 2,
    'student.session.active': 1,
    'student.assignment.due': 3,
    'student.notice.unread': 0
  }
  return STUDENT_CODES.map(code => item(code, values[code] === undefined ? defaults[code] : values[code]))
}

function response(items = teacherItems()) {
  return {
    code: 200,
    msg: '操作成功',
    data: { asOf: AS_OF, window: WINDOW, items }
  }
}

function studentResponse(items = studentItems()) {
  return {
    code: 200,
    msg: '操作成功',
    data: { asOf: AS_OF, window: WINDOW, items }
  }
}

async function installFixture(page, options = {}) {
  const opts = {
    role: 'teacher',
    permissions: options.role === 'student' ? STUDENT_PERMISSIONS : TEACHER_PERMISSIONS,
    includeOverviewRoute: true,
    infoDelay: 0,
    routersDelay: 0,
    teacherResponse: response(),
    teacherDelay: 0,
    teacherAbort: null,
    studentResponse: studentResponse(),
    studentDelay: 0,
    studentAbort: null,
    ...options
  }
  const requests = []
  let teacherCalls = 0
  let studentCalls = 0

  await page.route('**/getInfo', async route => {
    await sleep(opts.infoDelay)
    return json(route, info(opts.role, opts.permissions))
  })
  await page.route('**/getRouters', async route => {
    await sleep(opts.routersDelay)
    return json(route, routers(opts.role, opts.includeOverviewRoute))
  })
  await page.route('**/education/context/options*', route => json(route, {
    code: 200,
    msg: '操作成功',
    data: { term: null, courses: [] }
  }))
  await page.route('**/education/context/check*', route => json(route, {
    code: 200,
    msg: '操作成功',
    data: { courseSpaceId: '101', classId: '201', termId: '1', bindingId: '9001', memberRole: opts.role, canPublish: false, canGrade: false }
  }))

  const register = async (kind, pattern, getResponse, getDelay, abort) => {
    await page.route(pattern, async route => {
      const request = route.request()
      const url = new URL(request.url())
      requests.push({
        kind,
        path: url.pathname,
        query: Object.fromEntries(url.searchParams.entries()),
        rawQuery: url.search
      })
      const call = kind === 'teacher' ? ++teacherCalls : ++studentCalls
      try {
        const delay = typeof getDelay === 'function' ? getDelay(url, call) : getDelay
        await sleep(delay)
        if (abort) return route.abort(abort)
        const spec = typeof getResponse === 'function' ? getResponse(url, call) : getResponse
        if (spec?.httpStatus) {
          return route.fulfill({
            status: spec.httpStatus,
            contentType: 'application/json',
            body: JSON.stringify(spec.body)
          })
        }
        return json(route, spec)
      } catch {
        return undefined
      }
    })
  }
  await register('teacher', '**/education/teacher/overview*', opts.teacherResponse, opts.teacherDelay, opts.teacherAbort)
  await register('student', '**/education/student/today-todos*', opts.studentResponse, opts.studentDelay, opts.studentAbort)
  return requests
}

async function newContext(browser, role) {
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    timezoneId: 'Asia/Shanghai'
  })
  await context.addCookies([
    { name: TOKEN_COOKIE, value: 'fixture-token', url: BASE },
    { name: ROLE_COOKIE, value: role, url: BASE }
  ])
  return context
}

async function openHash(page, hash) {
  await page.goto(`${BASE}/vue/#${hash}`, { waitUntil: 'domcontentloaded' })
}

async function takeScreenshot(page, filename) {
  if (!SCREENSHOT_DIR) return
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true })
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, filename), fullPage: true })
}

async function board(page) {
  await page.waitForSelector('.overview-board', { timeout: 15000 })
}

function card(page, code) {
  return page.locator(`.ov-card[data-metric="${code}"]`)
}

async function waitForRecorded(requests, predicate) {
  const deadline = Date.now() + 3000
  while (Date.now() < deadline) {
    if (requests.some(predicate)) return
    await sleep(20)
  }
  throw new Error('等待夹具记录请求超时')
}

const results = []
async function run(name, fn) {
  try {
    await fn()
    results.push({ name, ok: true })
    console.log(`PASS ${name}`)
  } catch (error) {
    results.push({ name, ok: false, error })
    console.log(`FAIL ${name}: ${error.message}`)
  }
}

const browser = await chromium.launch({ channel: 'msedge', headless: true })

await run('教师工作台入口显示 6 个指标且不发送班级或身份参数', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openHash(page, '/teacher?section=overview')
  await board(page)

  assert.equal(await page.locator('.ov-card').count(), 6)
  assert.equal(await page.locator('.ov-card[data-metric="teacher.class.count"]').count(), 1)
  assert.match(await page.locator('.overview-board').innerText(), /待提交人次/)
  assert.equal(requests.length, 1)
  assert.equal(requests[0].kind, 'teacher')
  assert.deepEqual(requests[0].query, {})
  assert.equal(await page.locator('.overview-board a').count(), 0)
  assert.match(await page.locator('.ov-window').innerText(), /统计窗口/)
  assert.match(await page.locator('.ov-window').innerText(), /课堂、作业指标/)
  assert.match(await page.locator('.ov-meta').innerText(), /响应时间/)
  await page.setViewportSize({ width: 1280, height: 1800 })
  await takeScreenshot(page, '6-03-overview-teacher.png')
  await context.close()
})

await run('学生工作台入口显示 4 个指标且只发送空查询', async () => {
  const context = await newContext(browser, 'student')
  const page = await context.newPage()
  const requests = await installFixture(page, { role: 'student' })
  await openHash(page, '/student?section=overview')
  await board(page)

  assert.equal(await page.locator('.ov-card').count(), 4)
  assert.equal(requests.length, 1)
  assert.equal(requests[0].kind, 'student')
  assert.deepEqual(requests[0].query, {})
  assert.equal(Object.hasOwn(requests[0].query, 'classId'), false)
  assert.equal(Object.hasOwn(requests[0].query, 'bindingId'), false)
  await page.setViewportSize({ width: 1280, height: 1600 })
  await takeScreenshot(page, '6-03-overview-student.png')
  await context.close()
})

await run('教师和学生独立入口可用', async () => {
  const teacherContext = await newContext(browser, 'teacher')
  const teacherPage = await teacherContext.newPage()
  const teacherRequests = await installFixture(teacherPage)
  await openHash(teacherPage, '/teacher/teaching-overview')
  await board(teacherPage)
  assert.equal(await teacherPage.locator('.ov-card').count(), 6)
  assert.equal(teacherRequests.length, 1)
  await teacherContext.close()

  const studentContext = await newContext(browser, 'student')
  const studentPage = await studentContext.newPage()
  const studentRequests = await installFixture(studentPage, { role: 'student' })
  await openHash(studentPage, '/student/today-todos')
  await board(studentPage)
  assert.equal(await studentPage.locator('.ov-card').count(), 4)
  assert.equal(studentRequests.length, 1)
  await studentContext.close()
})

await run('缺权限或缺服务端路由时关闭入口且直达零请求', async () => {
  const cases = [
    { role: 'teacher', permissions: ['education:scope:read'], includeOverviewRoute: true, path: '/teacher?section=overview', direct: '/teacher/teaching-overview' },
    { role: 'teacher', permissions: TEACHER_PERMISSIONS, includeOverviewRoute: false, path: '/teacher?section=overview', direct: '/teacher/teaching-overview' },
    { role: 'student', permissions: ['education:scope:read'], includeOverviewRoute: true, path: '/student?section=overview', direct: '/student/today-todos' },
    { role: 'student', permissions: STUDENT_PERMISSIONS, includeOverviewRoute: false, path: '/student?section=overview', direct: '/student/today-todos' }
  ]
  for (const item of cases) {
    const context = await newContext(browser, item.role)
    const page = await context.newPage()
    const requests = await installFixture(page, {
      role: item.role,
      permissions: item.permissions,
      includeOverviewRoute: item.includeOverviewRoute
    })
    await openHash(page, item.path)
    await page.waitForTimeout(250)
    assert.equal(await page.locator('.overview-board').count(), 0, item.role)
    assert.equal(requests.length, 0, item.role)
    assert.match(await page.locator('body').innerText(), /总览未开放|没有访问权限/)
    await context.close()

    const directContext = await newContext(browser, item.role)
    const directPage = await directContext.newPage()
    const directRequests = await installFixture(directPage, {
      role: item.role,
      permissions: item.permissions,
      includeOverviewRoute: item.includeOverviewRoute
    })
    await openHash(directPage, item.direct)
    await directPage.waitForFunction(path => !location.hash.includes(path), item.direct, { timeout: 10000 })
    assert.equal(await directPage.locator('.overview-board').count(), 0, item.role + ' direct')
    assert.equal(directRequests.length, 0, item.role + ' direct')
    await directContext.close()
  }
})

await run('角色不匹配的总览入口不请求错误端点', async () => {
  const cases = [
    { role: 'teacher', path: '/teacher/today-todos', wrongMetric: 'student.', wrongKind: 'student' },
    { role: 'student', path: '/student/teaching-overview', wrongMetric: 'teacher.', wrongKind: 'teacher' }
  ]
  for (const item of cases) {
    const context = await newContext(browser, item.role)
    const page = await context.newPage()
    const requests = await installFixture(page, { role: item.role })
    await openHash(page, item.path)
    await page.waitForFunction(path => !location.hash.includes(path), item.path, { timeout: 10000 })
    await page.waitForTimeout(200)
    assert.equal(await page.locator(`.ov-card[data-metric^="${item.wrongMetric}"]`).count(), 0, item.role)
    assert.equal(requests.filter(request => request.kind === item.wrongKind).length, 0, item.role)
    await context.close()
  }
})

await run('登录核验完成前零总览请求', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, { infoDelay: 350, routersDelay: 300 })
  await openHash(page, '/teacher?section=overview')
  await page.waitForTimeout(120)
  assert.equal(requests.length, 0)
  await board(page)
  assert.equal(requests.length, 1)
  await context.close()
})

await run('教师 classId 保持字符串和大整数，不发送 bindingId', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openHash(page, '/teacher?section=overview')
  await board(page)
  const largeId = '820000000000000001'
  await page.locator('#overview-class-id').fill(largeId)
  await page.getByRole('button', { name: '应用筛选' }).click()
  await page.waitForTimeout(250)
  const filtered = requests.filter(item => item.kind === 'teacher').at(-1)
  assert.equal(filtered.query.classId, largeId)
  assert.equal(typeof filtered.query.classId, 'string')
  assert.equal(Object.hasOwn(filtered.query, 'bindingId'), false)
  assert.equal(Object.hasOwn(filtered.query, 'from'), false)
  await context.close()
})

await run('教师 classId Long 上界、溢出和格式边界按字符串校验', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openHash(page, '/teacher?section=overview')
  await board(page)
  const input = page.locator('#overview-class-id')
  const apply = page.getByRole('button', { name: '应用筛选' })

  const validMax = '9223372036854775807'
  const maxRequest = page.waitForRequest(request => request.url().includes('classId=' + validMax), { timeout: 10000 })
  await input.fill(validMax)
  await apply.click()
  await maxRequest
  await waitForRecorded(requests, item => item.kind === 'teacher' && item.query.classId === validMax)
  assert.equal(requests.filter(item => item.kind === 'teacher').at(-1).query.classId, validMax)

  const afterMax = requests.length
  for (const value of ['9223372036854775808', '9'.repeat(30), '0', '-1', 'abc']) {
    await input.fill(value)
    await apply.click()
    await page.waitForFunction(() => document.querySelector('.ov-filter-error')?.textContent.includes('Long'), null, { timeout: 2000 })
    await page.waitForTimeout(100)
    assert.equal(requests.length, afterMax, value)
  }

  const normalLarge = '820000000000000001'
  const largeRequest = page.waitForRequest(request => request.url().includes('classId=' + normalLarge), { timeout: 10000 })
  await input.fill(normalLarge)
  await apply.click()
  await largeRequest
  await waitForRecorded(requests, item => item.kind === 'teacher' && item.query.classId === normalLarge)
  assert.equal(requests.filter(item => item.kind === 'teacher').at(-1).query.classId, normalLarge)
  await context.close()
})

await run('学生筛选只发送 from/to，不携带班级或身份参数', async () => {
  const context = await newContext(browser, 'student')
  const page = await context.newPage()
  const requests = await installFixture(page, { role: 'student' })
  await openHash(page, '/student?section=overview')
  await board(page)
  await page.locator('#overview-from').fill('2026-11-19T09:00')
  await page.locator('#overview-to').fill('2026-11-19T11:00')
  await page.getByRole('button', { name: '应用筛选' }).click()
  await page.waitForTimeout(250)
  const filtered = requests.filter(item => item.kind === 'student').at(-1)
  assert.deepEqual(Object.keys(filtered.query).sort(), ['from', 'to'])
  assert.equal(filtered.query.from, '2026-11-19T01:00:00.000Z')
  assert.equal(filtered.query.to, '2026-11-19T03:00:00.000Z')
  await context.close()
})

await run('时间筛选成对校验、顺序校验和 UTC 转换，非法输入零请求', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openHash(page, '/teacher?section=overview')
  await board(page)
  const baseline = requests.length
  await page.locator('#overview-from').fill('2026-11-19T09:00')
  await page.getByRole('button', { name: '应用筛选' }).click()
  await page.waitForFunction(() => document.querySelector('.ov-filter-error')?.textContent.includes('必须同时填写'))
  assert.equal(requests.length, baseline)

  await page.locator('#overview-to').fill('2026-11-19T09:00')
  await page.getByRole('button', { name: '应用筛选' }).click()
  await page.waitForFunction(() => document.querySelector('.ov-filter-error')?.textContent.includes('必须晚于'))
  assert.equal(requests.length, baseline)

  await page.locator('#overview-to').fill('2026-11-19T11:00')
  await page.getByRole('button', { name: '应用筛选' }).click()
  await page.waitForTimeout(250)
  const filtered = requests.filter(item => item.kind === 'teacher').at(-1)
  assert.equal(filtered.query.from, '2026-11-19T01:00:00.000Z')
  assert.equal(filtered.query.to, '2026-11-19T03:00:00.000Z')
  await context.close()
})

await run('真实零、NO_DATA、UNAVAILABLE 与混合三态分别展示', async () => {
  const items = [
    item('teacher.class.count', 0),
    item('teacher.student.count', null, 'NO_DATA'),
    item('teacher.session.running', null, 'UNAVAILABLE'),
    item('teacher.assignment.pending', 0),
    item('teacher.grade.pending', 5),
    item('teacher.notice.unread', 0)
  ]
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, { teacherResponse: response(items) })
  await openHash(page, '/teacher?section=overview')
  await board(page)
  assert.equal(await card(page, 'teacher.class.count').locator('.ov-value strong').innerText(), '0')
  assert.match(await card(page, 'teacher.student.count').innerText(), /暂无数据/)
  assert.match(await card(page, 'teacher.session.running').innerText(), /暂不可用/)
  assert.equal(await card(page, 'teacher.assignment.pending').locator('.ov-value strong').innerText(), '0')
  assert.match(await card(page, 'teacher.grade.pending').innerText(), /正常/)
  assert.match(await card(page, 'teacher.notice.unread').innerText(), /正常/)
  await context.close()
})

await run('单卡缺项、重复、非法值、单位或来源违约只降级对应卡片', async () => {
  const items = [
    item('teacher.student.count', 86),
    item('teacher.student.count', 87),
    item('teacher.session.running', '1'),
    item('teacher.assignment.pending', 0, 'NO_DATA', { value: 0 }),
    item('teacher.grade.pending', 5, 'OK', { unit: 'PERCENT' }),
    item('teacher.notice.unread', 4, 'OK', { source: 'course' })
  ]
  // class.count 缺失；student.count 重复；其余分别违反 value/unit/source。
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, { teacherResponse: response(items) })
  await openHash(page, '/teacher?section=overview')
  await board(page)
  assert.equal(await page.locator('.ov-card').count(), 6)
  assert.equal(await page.locator('.ov-card .ov-state-text.error').count(), 6)
  assert.match(await page.locator('.ov-summary').innerText(), /6 数据异常/)
  await context.close()
})

await run('混合响应仅一张卡片违约时其余合法卡片保持正常', async () => {
  const items = teacherItems()
  const grade = items.find(item => item.metricCode === 'teacher.grade.pending')
  grade.unit = 'PERCENT'
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, { teacherResponse: response(items) })
  await openHash(page, '/teacher?section=overview')
  await board(page)
  assert.equal(await page.locator('.ov-card').count(), 6)
  assert.equal(await page.locator('.ov-card .ov-state-text.error').count(), 1)
  assert.match(await card(page, 'teacher.grade.pending').innerText(), /数据异常/)
  assert.equal(await card(page, 'teacher.class.count').locator('.ov-value strong').innerText(), '3')
  assert.match(await card(page, 'teacher.student.count').innerText(), /正常/)
  assert.match(await page.locator('.ov-summary').innerText(), /5 正常/)
  await context.close()
})

await run('顶层响应 data、items、asOf 或 window 异常显示响应异常', async () => {
  const cases = [
    { code: 200, msg: '操作成功', data: null },
    { code: 200, msg: '操作成功', data: { asOf: AS_OF, window: WINDOW, items: [] } },
    { code: 200, msg: '操作成功', data: { asOf: 'bad', window: WINDOW, items: teacherItems() } },
    { code: 200, msg: '操作成功', data: { asOf: AS_OF, window: { startsAt: WINDOW.endsAt, endsAt: WINDOW.startsAt }, items: teacherItems() } }
  ]
  for (const body of cases) {
    const context = await newContext(browser, 'teacher')
    const page = await context.newPage()
    await installFixture(page, { teacherResponse: body })
    await openHash(page, '/teacher?section=overview')
    await page.waitForSelector('.ov-state.error', { timeout: 15000 })
    assert.match(await page.locator('.ov-state.error').innerText(), /总览响应异常/)
    assert.equal(await page.locator('.ov-card').count(), 0)
    await context.close()
  }
})

await run('非法日历 asOf 和窗口字段分别显示响应异常，不自动归一化', async () => {
  const cases = [
    { asOf: '2026-02-30T06:00:00Z', window: WINDOW },
    { asOf: AS_OF, window: { startsAt: '2026-02-30T00:00:00Z', endsAt: WINDOW.endsAt } },
    { asOf: AS_OF, window: { startsAt: WINDOW.startsAt, endsAt: '2026-02-30T00:00:00Z' } }
  ]
  for (const item of cases) {
    const context = await newContext(browser, 'teacher')
    const page = await context.newPage()
    await installFixture(page, {
      teacherResponse: {
        code: 200,
        msg: '操作成功',
        data: { asOf: item.asOf, window: item.window, items: teacherItems() }
      }
    })
    await openHash(page, '/teacher?section=overview')
    await page.waitForSelector('.ov-state.error', { timeout: 2000 })
    assert.match(await page.locator('.ov-state.error').innerText(), /总览响应异常/)
    assert.equal(await page.locator('.ov-card').count(), 0)
    assert.doesNotMatch(await page.locator('body').innerText(), /2026-03-02/)
    await context.close()
  }
})

await run('合法闰日、Z/偏移和不同小数秒精度仍可展示', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, {
    teacherResponse: {
      code: 200,
      msg: '操作成功',
      data: {
        asOf: '2028-02-29T23:59:59.123456+08:00',
        window: {
          startsAt: '2028-02-29T00:00:00.123Z',
          endsAt: '2028-03-01T00:00:00.123456789Z'
        },
        items: teacherItems()
      }
    }
  })
  await openHash(page, '/teacher?section=overview')
  await board(page)
  assert.equal(await page.locator('.ov-card').count(), 6)
  assert.match(await page.locator('.ov-meta').innerText(), /响应时间/)
  assert.match(await page.locator('.ov-window').innerText(), /统计窗口/)
  assert.equal(await page.locator('.ov-state.error').count(), 0)
  await context.close()
})

await run('403 清除旧数据并展示无权限', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, { teacherResponse: { code: 403, msg: '没有该总览的访问权限' } })
  await openHash(page, '/teacher?section=overview')
  await page.waitForSelector('.ov-state.error', { timeout: 15000 })
  assert.match(await page.locator('.ov-state.error').innerText(), /无权查看/)
  assert.equal(await page.locator('.ov-card').count(), 0)
  await context.close()
})

await run('刷新失败不保留旧值和旧 asOf', async () => {
  let failing = false
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, {
    teacherResponse: () => failing
      ? { code: 500, msg: '总览查询失败' }
      : response(teacherItems({ 'teacher.class.count': 7 }))
  })
  await openHash(page, '/teacher?section=overview')
  await board(page)
  assert.equal(await card(page, 'teacher.class.count').locator('.ov-value strong').innerText(), '7')
  failing = true
  await page.locator('.ov-head .ov-btn').click()
  await page.waitForSelector('.ov-state.error', { timeout: 15000 })
  assert.equal(await page.locator('.ov-card').count(), 0)
  assert.equal(await page.locator('.overview-board').innerText().then(text => text.includes('响应时间')), false)
  await context.close()
})

await run('网络失败显示可重试错误，401 沿用登录跳转', async () => {
  const networkContext = await newContext(browser, 'teacher')
  const networkPage = await networkContext.newPage()
  await installFixture(networkPage, { teacherAbort: 'failed' })
  await openHash(networkPage, '/teacher?section=overview')
  await networkPage.waitForSelector('.ov-state.error', { timeout: 15000 })
  assert.match(await networkPage.locator('.ov-state.error').innerText(), /网络异常/)
  await networkContext.close()

  const authContext = await newContext(browser, 'teacher')
  const authPage = await authContext.newPage()
  await installFixture(authPage, {
    teacherResponse: { code: 401, msg: '登录已过期' }
  })
  await openHash(authPage, '/teacher?section=overview')
  await authPage.waitForFunction(() => location.hash.includes('/login'), null, { timeout: 15000 })
  assert.equal(await authPage.locator('.overview-board').count(), 0)
  await authContext.close()
})

await run('业务 400 与 HTTP 500 分别展示参数错误和服务异常', async () => {
  const cases = [
    { body: { code: 400, msg: '查询参数不合法' }, expected: /筛选参数不合法/ },
    { body: { httpStatus: 500, body: { code: 500, msg: '服务器内部错误' } }, expected: /总览读取失败/ }
  ]
  for (const item of cases) {
    const context = await newContext(browser, 'teacher')
    const page = await context.newPage()
    await installFixture(page, { teacherResponse: item.body })
    await openHash(page, '/teacher?section=overview')
    await page.waitForSelector('.ov-state.error', { timeout: 15000 })
    assert.match(await page.locator('.ov-state.error').innerText(), item.expected)
    assert.equal(await page.locator('.ov-card').count(), 0)
    await context.close()
  }
})

await run('筛选乱序时旧响应不覆盖新筛选', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    teacherDelay: url => url.searchParams.get('classId') === '9001' ? 500 : 50,
    teacherResponse: url => {
      const classId = url.searchParams.get('classId')
      return response(teacherItems({ 'teacher.class.count': classId === '9001' ? 11 : classId === '9002' ? 22 : 0 }))
    }
  })
  await openHash(page, '/teacher?section=overview')
  await board(page)
  await page.locator('#overview-class-id').fill('9001')
  const firstRequest = page.waitForRequest(request => request.url().includes('classId=9001'), { timeout: 10000 })
  await page.getByRole('button', { name: '应用筛选' }).click()
  await firstRequest
  await page.locator('#overview-class-id').fill('9002')
  const secondRequest = page.waitForRequest(request => request.url().includes('classId=9002'), { timeout: 10000 })
  await page.getByRole('button', { name: '应用筛选' }).click()
  await secondRequest
  await page.waitForFunction(() => document.querySelector('[data-metric="teacher.class.count"] .ov-value strong')?.textContent === '22', null, { timeout: 15000 })
  await page.waitForTimeout(700)
  assert.equal(await card(page, 'teacher.class.count').locator('.ov-value strong').innerText(), '22')
  assert.equal(requests.filter(item => item.kind === 'teacher').length, 3)
  await context.close()
})

await run('已排队请求被后续筛选作废，不产生旧请求', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openHash(page, '/teacher?section=overview')
  await board(page)
  const baseline = requests.length
  const queuedRequest = page.waitForRequest(request => request.url().includes('classId=9002'), { timeout: 10000 })
  await page.evaluate(() => {
    const input = document.querySelector('#overview-class-id')
    const apply = [...document.querySelectorAll('.ov-filters button')]
      .find(button => button.textContent.includes('应用筛选'))
    input.value = '9001'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    apply.click()
    input.value = '9002'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    apply.click()
  })
  await queuedRequest
  await page.waitForTimeout(250)
  const filtered = requests.filter(item => item.kind === 'teacher')
  assert.equal(filtered.some(item => item.query.classId === '9001'), false)
  assert.equal(filtered.some(item => item.query.classId === '9002'), true)
  assert.equal(requests.length, baseline + 1)
  await context.close()
})

await run('卸载后迟到响应不更新页面', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error))
  await installFixture(page, { teacherDelay: 700 })
  await openHash(page, '/teacher?section=overview')
  await page.waitForTimeout(100)
  await page.evaluate(() => { location.hash = '#/teacher/profile' })
  await page.waitForFunction(() => !location.hash.includes('/teacher?section=overview'), null, { timeout: 10000 })
  await page.waitForTimeout(900)
  assert.equal(await page.locator('.overview-board').count(), 0)
  assert.deepEqual(pageErrors, [])
  await context.close()
})

await run('390px 总览单列无横向溢出，离开后页面恢复', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await page.setViewportSize({ width: 390, height: 844 })
  await installFixture(page)
  await openHash(page, '/teacher?section=overview')
  await board(page)
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
  await takeScreenshot(page, '6-03-overview-teacher-390.png')
  await page.evaluate(() => { location.hash = '#/teacher/profile' })
  await page.waitForFunction(() => document.querySelector('.overview-board') === null, null, { timeout: 10000 })
  assert.equal(await page.locator('.app-shell').count(), 1)
  await context.close()
})

await browser.close()

const failed = results.filter(item => !item.ok)
console.log(`\n${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length > 0) {
  for (const item of failed) console.error(item.name, item.error)
  process.exitCode = 1
}
