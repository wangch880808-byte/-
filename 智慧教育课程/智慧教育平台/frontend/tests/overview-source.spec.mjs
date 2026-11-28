/**
 * 6-01 已接通四项指标来源入口测试。
 *
 * 覆盖：学生未读通知进入全部范围、教师/学生范围明细、真实零、缺权限/
 * 缺路由、旧选班上下文、离开页面后的迟到响应。只使用 Playwright 路由夹具，
 * 不连接真实后端、数据库或登录。
 *
 *   node tests/serve-dist.mjs
 *   OVERVIEW_SOURCE_TEST_BASE=http://127.0.0.1:4174 \
 *     node tests/overview-source.spec.mjs
 */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'

const BASE = process.env.OVERVIEW_SOURCE_TEST_BASE || 'http://127.0.0.1:4174'
const TOKEN_COOKIE = 'Admin-Token'
const ROLE_COOKIE = 'Edu-Role'
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const AS_OF = '2026-11-18T06:00:00.000Z'
const WINDOW = {
  startsAt: '2026-11-18T00:00:00.000Z',
  endsAt: '2026-11-19T00:00:00.000Z'
}

const TEACHER_PERMISSIONS = ['education:overview:teacher', 'education:scope:read']
const STUDENT_PERMISSIONS = ['education:overview:student', 'education:scope:read', 'education:notice:list']

const TEACHER_CODES = [
  'teacher.class.count', 'teacher.student.count', 'teacher.session.running',
  'teacher.assignment.pending', 'teacher.grade.pending', 'teacher.notice.unread'
]
const STUDENT_CODES = [
  'student.class.count', 'student.session.active', 'student.assignment.due',
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

function json(route, body, status = 200) {
  return route.fulfill({
    status,
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
      userName: role + '-source',
      nickName: role === 'teacher' ? '教师来源' : '学生来源',
      avatar: ''
    },
    roles: [role],
    permissions
  }
}

function routers(role, includeOverview, includeNoticeRoute) {
  const overview = role === 'teacher'
    ? { path: 'teacher/overview', component: 'education/teacher/overview' }
    : { path: 'student/today-todos', component: 'education/student/today-todos' }
  const children = [
    ...(includeOverview ? [overview] : []),
    { path: 'course', component: 'education/CourseListPage' }
  ]
  if (role === 'student' && includeNoticeRoute) {
    children.push({ path: 'notices', component: 'education/notices' })
  }
  return { code: 200, msg: '操作成功', data: [{ path: 'education', component: 'Layout', children }] }
}

function contextOptions() {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      term: { termId: '1', name: '2026-2027-1' },
      courses: [{
        courseSpaceId: '101',
        title: '演示课程',
        classes: [{
          classId: '201',
          className: '演示教学班',
          termId: '1',
          bindingId: '9001',
          memberRole: 'student'
        }]
      }]
    }
  }
}

function contextCheck(url) {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      courseSpaceId: url.searchParams.get('courseSpaceId') || '101',
      classId: url.searchParams.get('classId') || '201',
      termId: '1',
      bindingId: '9001',
      memberRole: 'student',
      canPublish: false,
      canGrade: false
    }
  }
}

function item(code, value, status = 'OK') {
  return {
    metricCode: code,
    value: status === 'OK' ? value : null,
    unit: 'COUNT',
    status,
    source: SOURCE_BY_CODE[code],
    sampleCount: null,
    actionPath: '/should-not-be-used'
  }
}

function overviewBody(role, values = {}) {
  const codes = role === 'teacher' ? TEACHER_CODES : STUDENT_CODES
  const defaults = role === 'teacher'
    ? {
        'teacher.class.count': 2,
        'teacher.student.count': 4,
        'teacher.session.running': null,
        'teacher.assignment.pending': null,
        'teacher.grade.pending': null,
        'teacher.notice.unread': null
      }
    : {
        'student.class.count': 1,
        'student.session.active': null,
        'student.assignment.due': null,
        'student.notice.unread': 0
      }
  const items = codes.map(code => {
    const value = values[code] === undefined ? defaults[code] : values[code]
    return value === null ? item(code, null, 'UNAVAILABLE') : item(code, value)
  })
  return { code: 200, msg: '操作成功', data: { asOf: AS_OF, window: WINDOW, items } }
}

function teacherScopeBody(classId) {
  const ranges = classId === '11'
    ? [{ teachingClassId: '11', bindingId: '100', courseSpaceId: '200', className: '教学班 A', courseName: '课程 A', studentCount: 3 }]
    : [
        { teachingClassId: '11', bindingId: '100', courseSpaceId: '200', className: '教学班 A', courseName: '课程 A', studentCount: 3 },
        { teachingClassId: '12', bindingId: '101', courseSpaceId: '200', className: '教学班 B', courseName: '课程 A', studentCount: 1 }
      ]
  return {
    code: 200,
    msg: '操作成功',
    data: {
      asOf: AS_OF,
      status: 'OK',
      classCount: ranges.length,
      studentCount: ranges.reduce((sum, range) => sum + range.studentCount, 0),
      ranges
    }
  }
}

function studentScopeBody() {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      asOf: AS_OF,
      status: 'OK',
      classCount: 1,
      studentCount: null,
      ranges: [{
        teachingClassId: '12', bindingId: '101', courseSpaceId: '200',
        className: '教学班 B', courseName: '课程 A', studentCount: null
      }]
    }
  }
}

async function installFixture(page, options = {}) {
  const opts = {
    role: 'teacher',
    permissions: options.role === 'student' ? STUDENT_PERMISSIONS : TEACHER_PERMISSIONS,
    includeOverview: true,
    includeNoticeRoute: true,
    overviewValues: {},
    scopeDelay: 0,
    scopeBody: null,
    ...options
  }
  const requests = []
  let read = false

  await page.route('**/getInfo', route => json(route, info(opts.role, opts.permissions)))
  await page.route('**/getRouters', route => json(route,
    routers(opts.role, opts.includeOverview, opts.includeNoticeRoute)))
  await page.route('**/education/context/options*', route => json(route, contextOptions()))
  await page.route('**/education/context/check*', route => {
    const url = new URL(route.request().url())
    return json(route, contextCheck(url))
  })
  await page.route('**/education/scope/term/current', route => json(route, {
    code: 200, msg: '操作成功',
    data: { termId: '1', name: '2026-2027-1', code: '2026-2027-1' }
  }))

  const record = (kind, route) => {
    const url = new URL(route.request().url())
    requests.push({ kind, path: url.pathname, query: Object.fromEntries(url.searchParams.entries()) })
    return url
  }

  await page.route(/\/education\/teacher\/overview\/scopes(?:\?|$)/, async route => {
    const url = record('teacher-scope', route)
    await sleep(opts.scopeDelay)
    return json(route, opts.scopeBody || teacherScopeBody(url.searchParams.get('classId')))
  })
  await page.route(/\/education\/student\/today-todos\/scopes(?:\?|$)/, async route => {
    record('student-scope', route)
    await sleep(opts.scopeDelay)
    return json(route, opts.scopeBody || studentScopeBody())
  })
  await page.route(/\/education\/teacher\/overview(?:\?|$)/, route => {
    const url = record('teacher-overview', route)
    const classId = url.searchParams.get('classId')
    const values = classId
      ? { ...opts.overviewValues, 'teacher.class.count': 1, 'teacher.student.count': 3 }
      : opts.overviewValues
    return json(route, overviewBody('teacher', values))
  })
  await page.route(/\/education\/student\/today-todos(?:\?|$)/, route => {
    record('student-overview', route)
    return json(route, overviewBody('student', opts.overviewValues))
  })

  await page.route(/\/education\/student\/notices(?:\/.*)?(?:\?|$)/, async route => {
    const url = record('notice', route)
    const method = route.request().method()
    if (url.pathname.endsWith('/unread-count')) {
      return json(route, {
        code: 200, msg: '操作成功',
        data: { unreadCount: read ? 0 : 1, asOf: AS_OF }
      })
    }
    if (url.pathname.endsWith('/read') && method === 'POST') {
      read = true
      return json(route, {
        code: 200, msg: '操作成功',
        data: { id: '501', readAt: '2026-11-18T06:01:00.000Z', alreadyRead: false }
      })
    }
    if (/\/education\/student\/notices\/\d+$/.test(url.pathname)) {
      return json(route, {
        code: 200, msg: '操作成功',
        data: {
          id: '501', kind: 'ANNOUNCEMENT', bindingId: '9001', sourceId: '601',
          title: '演示公告', visibleAt: '2026-11-18T05:00:00.000Z',
          readAt: read ? '2026-11-18T06:01:00.000Z' : null, isRead: read,
          createdAt: '2026-11-18T04:00:00.000Z'
        }
      })
    }
    return json(route, {
      code: 200, msg: '查询成功', total: 1,
      rows: [{
        id: '501', kind: 'ANNOUNCEMENT', bindingId: '9001', sourceId: '601',
        title: '演示公告', visibleAt: '2026-11-18T05:00:00.000Z',
        readAt: read ? '2026-11-18T06:01:00.000Z' : null, isRead: read
      }]
    })
  })

  return requests
}

async function newContext(browser, role) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, timezoneId: 'Asia/Shanghai' })
  await context.addCookies([
    { name: TOKEN_COOKIE, value: 'fixture-token', url: BASE },
    { name: ROLE_COOKIE, value: role, url: BASE }
  ])
  return context
}

async function open(page, hash) {
  await page.goto(`${BASE}/vue/#${hash}`, { waitUntil: 'domcontentloaded' })
}

async function board(page) {
  await page.waitForSelector('.overview-board', { timeout: 15000 })
  await page.waitForSelector('.ov-card', { timeout: 15000 })
}

function card(page, code) {
  return page.locator(`.ov-card[data-metric="${code}"]`)
}

const results = []
async function run(name, fn) {
  try {
    await fn()
    results.push({ name, ok: true })
    console.log(`PASS ${name}`)
  } catch (error) {
    results.push({ name, ok: false, error: error.message })
    console.log(`FAIL ${name}: ${error.message}`)
  }
}

const browser = await chromium.launch({ channel: 'msedge', headless: true })

await run('学生未读通知入口进入全部范围，旧选班不窄化，详情和已读保留', async () => {
  const context = await newContext(browser, 'student')
  const page = await context.newPage()
  const requests = await installFixture(page, { role: 'student' })
  await open(page, '/student/today-todos?courseSpaceId=101&classId=201&bindingId=9001')
  await board(page)
  assert.match(await card(page, 'student.notice.unread').innerText(), /0/)
  const requestCountBeforeSource = requests.length
  await card(page, 'student.notice.unread').getByRole('button', { name: '查看通知' }).click()
  await page.waitForFunction(() => location.hash.includes('/student/notices'), null, { timeout: 10000 })
  assert.match(await page.evaluate(() => location.hash), /scope=all/)
  await page.waitForSelector('.all-scope-note', { timeout: 10000 })
  assert.equal(await page.locator('.teaching-context').count(), 0)
  await page.waitForSelector('.notice-list', { timeout: 10000 })
  const afterSource = requests.slice(requestCountBeforeSource)
  const listRequest = afterSource.filter(request => request.kind === 'notice'
    && !request.path.endsWith('/unread-count') && !/\/\d+(?:\/read)?$/.test(request.path)).at(-1)
  const listIndex = afterSource.indexOf(listRequest)
  const unreadRequests = afterSource.filter((request, index) => index > listIndex
    && request.kind === 'notice' && request.path.endsWith('/unread-count'))
  assert.equal(Object.hasOwn(listRequest.query, 'bindingId'), false)
  assert.equal(Object.hasOwn(listRequest.query, 'courseSpaceId'), false)
  assert.equal(Object.hasOwn(listRequest.query, 'classId'), false)
  assert.ok(unreadRequests.length >= 1)
  for (const request of unreadRequests) {
    assert.equal(Object.hasOwn(request.query, 'bindingId'), false)
  }
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-dialog .detail-read').click()
  await page.waitForFunction(() => /已读状态\s*已读/.test(
    document.querySelector('.detail-dialog')?.innerText || ''), null, { timeout: 10000 })
  await context.close()
})

await run('学生缺通知权限或缺服务端路由时来源入口不显示', async () => {
  for (const options of [
    { permissions: ['education:overview:student', 'education:scope:read'], includeNoticeRoute: true },
    { permissions: STUDENT_PERMISSIONS, includeNoticeRoute: false }
  ]) {
    const context = await newContext(browser, 'student')
    const page = await context.newPage()
    await installFixture(page, { role: 'student', ...options })
    await open(page, '/student/today-todos')
    await board(page)
    assert.equal(await card(page, 'student.notice.unread').getByRole('button', { name: '查看通知' }).count(), 0)
    await context.close()
  }
})

await run('教师班级数和学生数范围明细与卡片同口径，classId 筛选同步', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, { role: 'teacher' })
  await open(page, '/teacher/teaching-overview')
  await board(page)
  await card(page, 'teacher.student.count').getByRole('button', { name: '查看范围明细' }).click()
  await page.waitForSelector('.ov-dialog', { timeout: 10000 })
  assert.match(await page.locator('.ov-dialog').innerText(), /范围 2 条/)
  assert.match(await page.locator('.ov-dialog').innerText(), /学生合计 4 人/)
  assert.equal(await page.locator('.ov-scope-table tbody tr').count(), 2)
  await page.locator('.ov-dialog .ov-btn').click()
  const allScope = requests.filter(request => request.kind === 'teacher-scope').at(-1)
  assert.deepEqual(allScope.query, {})

  await page.locator('#overview-class-id').fill('11')
  await page.getByRole('button', { name: '应用筛选' }).click()
  await page.waitForFunction(() => [...document.querySelectorAll('.ov-card[data-metric="teacher.class.count"]')]
    .some(card => card.innerText.includes('1')), null, { timeout: 10000 })
  await card(page, 'teacher.class.count').getByRole('button', { name: '查看范围明细' }).click()
  await page.waitForSelector('.ov-dialog', { timeout: 10000 })
  assert.match(await page.locator('.ov-dialog').innerText(), /范围 1 条/)
  assert.match(await page.locator('.ov-dialog').innerText(), /学生合计 3 人/)
  const filtered = requests.filter(request => request.kind === 'teacher-scope').at(-1)
  assert.equal(filtered.query.classId, '11')
  await context.close()
})

await run('学生班级范围明细使用服务端范围，不携带身份或班级参数', async () => {
  const context = await newContext(browser, 'student')
  const page = await context.newPage()
  const requests = await installFixture(page, { role: 'student' })
  await open(page, '/student/today-todos')
  await board(page)
  await card(page, 'student.class.count').getByRole('button', { name: '查看范围明细' }).click()
  await page.waitForSelector('.ov-dialog', { timeout: 10000 })
  assert.match(await page.locator('.ov-dialog').innerText(), /范围 1 条/)
  assert.equal(await page.locator('.ov-scope-table th', { hasText: '学生数' }).count(), 0)
  const request = requests.filter(item => item.kind === 'student-scope').at(-1)
  assert.deepEqual(request.query, {})
  await context.close()
})

await run('真实零：教师学生数为 0 时仍可查看明细并显示 0', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, {
    role: 'teacher',
    overviewValues: { 'teacher.class.count': 1, 'teacher.student.count': 0 },
    scopeBody: {
      code: 200,
      msg: '操作成功',
      data: {
        asOf: AS_OF, status: 'OK', classCount: 1, studentCount: 0,
        ranges: [{
          teachingClassId: '11', bindingId: '100', courseSpaceId: '200',
          className: '教学班 A', courseName: '课程 A', studentCount: 0
        }]
      }
    }
  })
  await open(page, '/teacher/teaching-overview')
  await board(page)
  assert.match(await card(page, 'teacher.student.count').innerText(), /0/)
  await card(page, 'teacher.student.count').getByRole('button', { name: '查看范围明细' }).click()
  await page.waitForSelector('.ov-dialog', { timeout: 10000 })
  assert.match(await page.locator('.ov-dialog').innerText(), /学生合计 0 人/)
  await context.close()
})

await run('UNAVAILABLE 指标不显示来源或范围入口', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, { role: 'teacher' })
  await open(page, '/teacher/teaching-overview')
  await board(page)
  for (const code of ['teacher.session.running', 'teacher.assignment.pending',
    'teacher.grade.pending', 'teacher.notice.unread']) {
    assert.equal(await card(page, code).locator('.ov-card-actions button').count(), 0, code)
  }
  await context.close()
})

await run('范围明细 403 显示无权限，不显示数字', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, {
    role: 'teacher',
    scopeBody: { code: 403, msg: '无权查看范围明细' }
  })
  await open(page, '/teacher/teaching-overview')
  await board(page)
  await card(page, 'teacher.class.count').getByRole('button', { name: '查看范围明细' }).click()
  await page.waitForFunction(() => document.querySelector('.ov-dialog')?.innerText.includes('无权查看范围明细'),
    null, { timeout: 10000 })
  assert.match(await page.locator('.ov-dialog').innerText(), /无权查看范围明细/)
  await context.close()
})

await run('离开页面后范围明细迟到响应不重新打开弹窗', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, { role: 'teacher', scopeDelay: 1200 })
  await open(page, '/teacher/teaching-overview')
  await board(page)
  await card(page, 'teacher.class.count').getByRole('button', { name: '查看范围明细' }).click()
  await page.waitForSelector('.ov-dialog', { timeout: 10000 })
  await open(page, '/teacher')
  await page.waitForSelector('.workspace-shortcuts', { timeout: 10000 })
  await sleep(1600)
  assert.equal(await page.locator('.ov-dialog').count(), 0)
  await context.close()
})

await browser.close()
console.log(JSON.stringify({ results }, null, 2))
if (results.some(result => !result.ok)) process.exit(1)
