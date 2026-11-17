/**
 * 6-04 学生通知 Vue 最小接入浏览器夹具。
 *
 * 目的：不依赖后端和数据库，验证工作台/顶栏/直达入口的三项检查
 * （角色、权限、服务端路由）、通知列表与未读数、空列表/403/503 区分、
 * 切班旧响应丢弃。
 *
 * 运行前置：
 *   npm run build
 *   node tests/serve-dist.mjs
 *   PLAYWRIGHT_MODULE 可用（本机临时安装 playwright，使用 Edge 通道）
 *
 * 运行：
 *   NOTICE_TEST_BASE=http://127.0.0.1:4174 node tests/notice-vue.spec.mjs
 */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'

const BASE = process.env.NOTICE_TEST_BASE || 'http://127.0.0.1:4174'
const ROLE_COOKIE = 'Edu-Role'
const TOKEN_COOKIE = 'Admin-Token'

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

function json(route, body) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body)
  })
}

function contextOptions(role = 'student', canPublish = role === 'teacher') {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      term: { termId: '1', termName: '2026-2027-1' },
      courses: [{
        courseSpaceId: '101',
        title: '课程一',
        classes: [
          {
            classId: '201', className: '一班', termId: '1', bindingId: '9001',
            memberRole: role, canPublish
          },
          {
            classId: '202', className: '二班', termId: '1', bindingId: '9002',
            memberRole: role, canPublish
          }
        ]
      }]
    }
  }
}

function contextCheck(url, canPublish) {
  const bindingId = url.searchParams.get('bindingId')
  const classId = url.searchParams.get('classId')
  const role = url.searchParams.get('role') || 'student'
  const publish = canPublish === undefined ? role === 'teacher' : canPublish === true
  return {
    code: 200,
    msg: '操作成功',
    data: {
      courseSpaceId: '101',
      classId,
      termId: '1',
      bindingId,
      memberRole: role,
      canPublish: publish,
      canGrade: false
    }
  }
}

function routers(includeNoticeRoute, includeTeacherAnnouncementRoute = true) {
  const notices = includeNoticeRoute
    ? [{ path: 'notices', component: 'education/student/StudentNotices' }]
    : []
  const teacherAnnouncements = includeTeacherAnnouncementRoute
    ? [{ path: 'announcements', component: 'education/teacher/TeacherAnnouncements' }]
    : []
  return {
    code: 200,
    msg: '操作成功',
    data: [{
      path: 'education',
      component: 'Layout',
      children: [
        ...notices,
        ...teacherAnnouncements,
        { path: 'course', component: 'education/CourseListPage' }
      ]
    }]
  }
}

function info(role, permissions) {
  return {
    code: 200,
    msg: '操作成功',
    user: {
      userId: role === 'teacher' ? '9002' : '9001',
      userName: role === 'teacher' ? 'teacher01' : 'student01',
      nickName: role === 'teacher' ? '教师甲' : '学生甲',
      avatar: ''
    },
    roles: [role],
    permissions
  }
}

const NOTICE_ID = '30001'
const LARGE_NOTICE_ID = '820000000000000001'
const ANNOUNCEMENT_ID = '70001'
const TEACHER_ANNOUNCEMENT_ID = '80001'
const READ_AT = '2026-11-17T03:00:00.000Z'
const TEACHER_WRITE_PERMISSIONS = [
  'education:announcement:list',
  'education:announcement:add',
  'education:announcement:edit',
  'education:scope:read'
]

function teacherListRequests(requests) {
  return requests.filter(item => item.teacherAnnouncementBindingId !== undefined)
}

function teacherCreateRequests(requests) {
  return requests.filter(item => item.method === 'POST' && item.teacherAnnouncementCreate)
}

function teacherUpdateRequests(requests) {
  return requests.filter(item => item.method === 'PUT' && item.teacherAnnouncementUpdateId)
}

function noticeRow(id, title, bindingId = '9001') {
  return {
    id,
    kind: 'ANNOUNCEMENT',
    bindingId,
    sourceId: ANNOUNCEMENT_ID,
    title,
    visibleAt: '2026-11-17T02:00:00.000Z',
    readAt: null,
    isRead: false,
    createdAt: '2026-11-17T01:00:00.000Z'
  }
}

function listBody(bindingId, noticeId = NOTICE_ID) {
  const suffix = bindingId || 'ALL'
  return {
    code: 200,
    msg: '查询成功',
    total: 1,
    rows: [noticeRow(noticeId, `通知-${suffix}`, bindingId || '9001')]
  }
}

function detailBody(noticeId, overrides = {}) {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      ...noticeRow(noticeId, `详情-${noticeId}`),
      readAt: null,
      isRead: false,
      ...overrides
    }
  }
}

function announcementRow(id = ANNOUNCEMENT_ID, bindingId = '9001', title = `公告-${id}`) {
  return {
    id,
    bindingId,
    title,
    opensAt: '2026-11-17T02:00:00.000Z',
    closesAt: '2026-11-18T02:00:00.000Z',
    createdAt: '2026-11-17T01:00:00.000Z'
  }
}

function announcementDetailBody(id = ANNOUNCEMENT_ID, overrides = {}) {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      id,
      bindingId: '9001',
      title: `公告-${id}`,
      body: `公告正文-${id}`,
      opensAt: '2026-11-17T02:00:00.000Z',
      closesAt: '2026-11-18T02:00:00.000Z',
      createdAt: '2026-11-17T01:00:00.000Z',
      ...overrides
    }
  }
}

function teacherAnnouncementRow(
  id = TEACHER_ANNOUNCEMENT_ID,
  bindingId = '9001',
  visibilityState = 'OPEN',
  title = `教师公告-${id}`
) {
  return {
    id,
    bindingId,
    title,
    status: visibilityState === 'DRAFT' ? 'DRAFT' : 'PUBLISHED',
    visibilityState,
    opensAt: '2026-11-17T02:00:00.000Z',
    closesAt: '2026-11-18T02:00:00.000Z',
    createdByUserId: '9002',
    publishedByUserId: visibilityState === 'DRAFT' ? null : '9002',
    createdAt: '2026-11-17T01:00:00.000Z'
  }
}

function teacherAnnouncementDetailBody(id = TEACHER_ANNOUNCEMENT_ID, overrides = {}) {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      ...teacherAnnouncementRow(id),
      body: `教师公告正文-${id}`,
      ...overrides
    }
  }
}

/**
 * 让列表响应反映夹具内存中的已读状态。
 *
 * 只改夹具响应，不代表前端本地减一；前端仍必须重新请求服务端。
 */
function applyReadState(body, readState, allRead) {
  if (!body || !Array.isArray(body.rows)) return body
  return {
    ...body,
    rows: body.rows.map(row => ((allRead || readState.has(row.id))
      ? { ...row, isRead: true, readAt: row.readAt || READ_AT }
      : row))
  }
}

/**
 * 安装浏览器夹具。
 *
 * 所有 /getInfo、/getRouters、/education/context/*、/education/student/notices*
 * 请求都由本函数接管；未覆盖的请求会继续打到 Vite preview，可能得到 index.html，
 * 因此测试中不让未覆盖业务请求出现。
 */
async function installFixture(page, options = {}) {
  const opts = {
    role: 'student',
    permissions: ['education:notice:list', 'education:scope:read'],
    includeNoticeRoute: true,
    includeTeacherAnnouncementRoute: true,
    list: null,
    listAfterRead: null,
    listAfterReadDelay: 0,
    unread: null,
    unreadAfterRead: null,
    detail: null,
    detailDelays: {},
    read: null,
    readDelays: {},
    readAll: null,
    readAllDelay: 0,
    announcementList: null,
    announcementListDelays: {},
    announcementDetail: null,
    announcementDetailDelays: {},
    teacherAnnouncementList: null,
    teacherAnnouncementListDelays: {},
    teacherAnnouncementDetail: null,
    teacherAnnouncementDetailDelays: {},
    teacherAnnouncementCreate: null,
    teacherAnnouncementCreateDelay: 0,
    teacherAnnouncementUpdate: null,
    teacherAnnouncementUpdateDelay: 0,
    abortTeacherAnnouncementCreate: null,
    abortTeacherAnnouncementUpdate: null,
    contextCheck: null,
    delayByBinding: {},
    ...options
  }
  const requests = []
  const readState = new Set()
  let allRead = false

  await page.route('**/getInfo', route => json(route, info(opts.role, opts.permissions)))
  await page.route('**/getRouters', route => json(route,
    routers(opts.includeNoticeRoute, opts.includeTeacherAnnouncementRoute)))
  await page.route('**/education/context/options*', route => json(route,
    opts.contextOptions || contextOptions(opts.role)))
  await page.route('**/education/context/check*', route => {
    const url = new URL(route.request().url())
    return json(route, typeof opts.contextCheck === 'function'
      ? opts.contextCheck(url)
      : (opts.contextCheck || contextCheck(url)))
  })
  await page.route('**/education/teacher/announcements/*', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const announcementId = decodeURIComponent(url.pathname.split('/').pop())
    if (request.method() === 'PUT') {
      const payload = JSON.parse(request.postData() || '{}')
      requests.push({
        method: 'PUT',
        path: url.pathname,
        query: url.search,
        teacherAnnouncementUpdateId: announcementId,
        payload,
        body: request.postData()
      })
      try {
        await sleep(opts.teacherAnnouncementUpdateDelay || 0)
        if (opts.abortTeacherAnnouncementUpdate) {
          return route.abort(opts.abortTeacherAnnouncementUpdate)
        }
        const body = typeof opts.teacherAnnouncementUpdate === 'function'
          ? opts.teacherAnnouncementUpdate(announcementId, payload)
          : (opts.teacherAnnouncementUpdate || {
            code: 200,
            msg: '操作成功',
            data: {
              ...teacherAnnouncementRow(announcementId),
              title: payload?.title ?? `教师公告-${announcementId}`,
              body: payload?.body ?? `教师公告正文-${announcementId}`,
              opensAt: payload?.opensAt ?? null,
              closesAt: payload?.closesAt ?? null,
              status: 'DRAFT',
              visibilityState: 'DRAFT',
              publishedByUserId: null
            }
          })
        return json(route, body)
      } catch {
        return undefined
      }
    }
    requests.push({ path: url.pathname, query: url.search, teacherAnnouncementDetailId: announcementId })
    try {
      await sleep(opts.teacherAnnouncementDetailDelays[announcementId] || 0)
      const body = typeof opts.teacherAnnouncementDetail === 'function'
        ? opts.teacherAnnouncementDetail(announcementId)
        : (opts.teacherAnnouncementDetail || teacherAnnouncementDetailBody(announcementId))
      return json(route, body)
    } catch {
      return undefined
    }
  })
  await page.route('**/education/teacher/announcements*', async route => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'POST') {
      const payload = JSON.parse(request.postData() || '{}')
      requests.push({
        method: 'POST',
        path: url.pathname,
        query: url.search,
        teacherAnnouncementCreate: true,
        payload,
        body: request.postData()
      })
      try {
        await sleep(opts.teacherAnnouncementCreateDelay || 0)
        if (opts.abortTeacherAnnouncementCreate) {
          return route.abort(opts.abortTeacherAnnouncementCreate)
        }
        const body = typeof opts.teacherAnnouncementCreate === 'function'
          ? opts.teacherAnnouncementCreate(payload)
          : (opts.teacherAnnouncementCreate || {
            code: 200,
            msg: '操作成功',
            data: {
              ...teacherAnnouncementRow('81001', payload?.bindingId || '9001', 'DRAFT', payload?.title || '新草稿'),
              body: payload?.body || '新草稿正文',
              opensAt: payload?.opensAt ?? null,
              closesAt: payload?.closesAt ?? null,
              createdAt: '2026-11-17T04:00:00.000Z'
            }
          })
        return json(route, body)
      } catch {
        return undefined
      }
    }
    const bindingId = url.searchParams.get('bindingId') || ''
    requests.push({
      path: url.pathname,
      query: url.search,
      teacherAnnouncementBindingId: bindingId,
      teacherAnnouncementStatus: url.searchParams.get('status') || ''
    })
    try {
      await sleep(opts.teacherAnnouncementListDelays[bindingId] || 0)
      const body = typeof opts.teacherAnnouncementList === 'function'
        ? opts.teacherAnnouncementList(bindingId)
        : (opts.teacherAnnouncementList || {
          code: 200,
          msg: '查询成功',
          total: 1,
          rows: [teacherAnnouncementRow(TEACHER_ANNOUNCEMENT_ID, bindingId || '9001')]
        })
      return json(route, body)
    } catch {
      return undefined
    }
  })
  await page.route('**/education/student/announcements/*', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const announcementId = decodeURIComponent(url.pathname.split('/').pop())
    requests.push({ path: url.pathname, query: url.search, announcementDetailId: announcementId })
    try {
      await sleep(opts.announcementDetailDelays[announcementId] || 0)
      const body = typeof opts.announcementDetail === 'function'
        ? opts.announcementDetail(announcementId)
        : (opts.announcementDetail || announcementDetailBody(announcementId))
      return json(route, body)
    } catch {
      return undefined
    }
  })
  await page.route('**/education/student/announcements*', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const bindingId = url.searchParams.get('bindingId') || ''
    requests.push({ path: url.pathname, query: url.search, announcementBindingId: bindingId })
    try {
      await sleep(opts.announcementListDelays[bindingId] || 0)
      const body = typeof opts.announcementList === 'function'
        ? opts.announcementList(bindingId)
        : (opts.announcementList || {
          code: 200,
          msg: '查询成功',
          total: 1,
          rows: [announcementRow(ANNOUNCEMENT_ID, bindingId || '9001')]
        })
      return json(route, body)
    } catch {
      return undefined
    }
  })
  await page.route('**/education/student/notices/*/read', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const noticeId = decodeURIComponent(url.pathname.split('/').slice(-2)[0])
    requests.push({ method: 'POST', path: url.pathname, readId: noticeId })
    try {
      await sleep(opts.readDelays[noticeId] ?? opts.readDelay ?? 0)
      if (opts.rawRead) {
        return route.fulfill(opts.rawRead)
      }
      const body = typeof opts.read === 'function'
        ? opts.read(noticeId, readState)
        : (opts.read || {
          code: 200,
          msg: '操作成功',
          data: { id: noticeId, readAt: READ_AT, alreadyRead: false }
        })
      if (body?.code === 200 && body.data) {
        readState.add(noticeId)
      }
      return json(route, body)
    } catch {
      return undefined
    }
  })
  await page.route('**/education/student/notices/*', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const noticeId = decodeURIComponent(url.pathname.split('/').pop())
    requests.push({ path: url.pathname, query: url.search, detailId: noticeId })
    try {
      await sleep(opts.detailDelays[noticeId] ?? opts.detailDelay ?? 0)
      if (opts.rawDetail) {
        return route.fulfill(opts.rawDetail)
      }
      let body = typeof opts.detail === 'function'
        ? opts.detail(noticeId)
        : (opts.detail || detailBody(noticeId))
      if (body?.code === 200 && body.data && (allRead || readState.has(noticeId))) {
        body = {
          ...body,
          data: { ...body.data, isRead: true, readAt: body.data.readAt || READ_AT }
        }
      }
      return json(route, body)
    } catch {
      return undefined
    }
  })
  await page.route('**/education/student/notices/read-all', async route => {
    const request = route.request()
    const url = new URL(request.url())
    requests.push({
      method: request.method(),
      path: url.pathname,
      query: url.search,
      body: request.postData()
    })
    try {
      await sleep(opts.readAllDelay || 0)
      if (opts.rawReadAll) {
        return route.fulfill(opts.rawReadAll)
      }
      const body = typeof opts.readAll === 'function'
        ? opts.readAll()
        : (opts.readAll || {
          code: 200,
          msg: '操作成功',
          data: { markedCount: 3, unreadCount: 0, operatedAt: '2026-11-17T03:00:00.000Z' }
        })
      if (body?.code === 200 && body.data) {
        allRead = true
      }
      return json(route, body)
    } catch {
      return undefined
    }
  })
  await page.route('**/education/student/notices/unread-count*', async route => {
    const request = route.request()
    const url = new URL(request.url())
    requests.push({ path: url.pathname, query: url.search })
    try {
      const unread = (allRead ? { code: 200, msg: '操作成功', data: { unreadCount: 0, asOf: '2026-11-17T02:00:00.000Z' } } : null)
        || (readState.size > 0 && opts.unreadAfterRead) || opts.unread || {
        code: 200,
        msg: '操作成功',
        data: { unreadCount: Math.max(0, 3 - readState.size), asOf: '2026-11-17T02:00:00.000Z' }
      }
      await sleep(opts.unreadDelay || 0)
      return json(route, unread)
    } catch {
      return undefined
    }
  })

  await page.route('**/education/student/notices*', async route => {
    const request = route.request()
    const url = new URL(request.url())
    requests.push({ path: url.pathname, query: url.search })
    try {
      if (url.pathname.endsWith('/unread-count')) {
        const unread = (allRead ? { code: 200, msg: '操作成功', data: { unreadCount: 0, asOf: '2026-11-17T02:00:00.000Z' } } : null)
          || (readState.size > 0 && opts.unreadAfterRead) || opts.unread || {
          code: 200,
          msg: '操作成功',
          data: { unreadCount: Math.max(0, 3 - readState.size), asOf: '2026-11-17T02:00:00.000Z' }
        }
        await sleep(opts.unreadDelay || 0)
        return json(route, unread)
      }
      const bindingId = url.searchParams.get('bindingId') || ''
      const afterRead = readState.size > 0 || allRead
      const delay = (opts.delayByBinding[bindingId] || 0) + (afterRead ? opts.listAfterReadDelay : 0)
      await sleep(delay)
      if (opts.rawList) {
        return route.fulfill(opts.rawList)
      }
      const body = applyReadState((afterRead && opts.listAfterRead)
        || opts.list || (opts.listRows
          ? { code: 200, msg: '查询成功', total: opts.listRows.length, rows: opts.listRows }
          : listBody(bindingId)), readState, allRead)
      return json(route, body)
    } catch {
      // 请求已被切班 abort 时，Playwright 可能拒绝 fulfill；测试以 DOM 代际为准。
      return undefined
    }
  })

  return requests
}

async function newContext(browser, role = 'student') {
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } })
  await context.addCookies([
    { name: TOKEN_COOKIE, value: 'fixture-token', url: BASE },
    { name: ROLE_COOKIE, value: role, url: BASE }
  ])
  return context
}

async function openHash(page, hash) {
  await page.goto(`${BASE}/vue/#${hash}`, { waitUntil: 'domcontentloaded' })
}

async function text(page) {
  return page.locator('body').innerText()
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

await run('授权后工作台入口进入通知列表，顶栏角标显示未读数', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error))
  const requests = await installFixture(page)
  await openHash(page, '/student?section=notice')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  const body = await text(page)
  assert.match(body, /学习通知/)
  assert.match(body, /通知-ALL/)
  assert.match(body, /未读：3/)
  await page.waitForSelector('.header-right .notice-count', { timeout: 10000 })
  assert.equal((await page.locator('.header-right .notice-count').innerText()).trim(), '3')
  const listRequest = requests.find(item => item.path.endsWith('/student/notices'))
  assert.ok(listRequest, '应请求通知列表')
  assert.equal(new URLSearchParams(listRequest.query).has('bindingId'), false, '无班级上下文时列表应省略 bindingId')
  const unreadRequests = requests.filter(item => item.path.endsWith('/unread-count'))
  assert.ok(unreadRequests.length > 0, '应请求未读数')
  assert.ok(unreadRequests.every(item => !new URLSearchParams(item.query).has('bindingId')), '无班级上下文时未读数应省略 bindingId')
  assert.equal(pageErrors.length, 0, `页面异常：${pageErrors.map(item => item.message).join('; ')}`)
  await context.close()
})

await run('授权后直达入口和顶栏入口可打开同一通知页', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page)
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.header-right button', { hasText: '通知' }).first().click()
  await page.waitForFunction(() => location.hash.includes('/student/notices'), null, { timeout: 10000 })
  assert.match(await text(page), /学习通知/)
  await context.close()
})

await run('缺权限时直达和工作台入口均被拦截', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, { permissions: ['education:scope:read'] })
  await openHash(page, '/student/notices')
  await page.waitForFunction(() => !location.hash.includes('/student/notices'), null, { timeout: 10000 })
  assert.equal(await page.locator('.student-notices').count(), 0)
  assert.match(await text(page), /没有访问权限|未开放/)
  await openHash(page, '/student?section=notice')
  await page.waitForTimeout(500)
  assert.equal(await page.locator('.student-notices').count(), 0)
  assert.match(await text(page), /未开放|没有访问权限/)
  await context.close()
})

await run('缺服务端路由时直达也按同一规则拦截', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, { includeNoticeRoute: false })
  await openHash(page, '/student/notices')
  await page.waitForFunction(() => !location.hash.includes('/student/notices'), null, { timeout: 10000 })
  assert.equal(await page.locator('.student-notices').count(), 0)
  assert.match(await text(page), /没有访问权限|未开放/)
  await context.close()
})

await run('角色不匹配时学生直达入口被拦截', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, { role: 'teacher', permissions: ['education:notice:list'] })
  await openHash(page, '/student/notices')
  await page.waitForFunction(() => location.hash.includes('/teacher'), null, { timeout: 10000 })
  assert.equal(await page.locator('.student-notices').count(), 0)
  await context.close()
})

await run('切班后旧班迟到响应不覆盖新班', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, { delayByBinding: { 9001: 1500, 9002: 50 } })
  await openHash(page, '/student/notices?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForRequest(request => {
    const url = new URL(request.url())
    return url.pathname.endsWith('/education/student/notices')
      && url.searchParams.get('bindingId') === '9001'
  }, { timeout: 15000 })
  await page.evaluate(() => {
    location.hash = '#/student/notices?courseSpaceId=101&classId=202&bindingId=9002'
  })
  await page.waitForFunction(() => document.body.innerText.includes('通知-9002'), null, { timeout: 15000 })
  await page.waitForTimeout(1800)
  const body = await text(page)
  assert.match(body, /通知-9002/)
  assert.doesNotMatch(body, /通知-9001/)
  await context.close()
})

await run('未读数 503 显示暂不可用，正常 200 显示数字', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, {
    unread: { code: 503, msg: '学生教学班授权服务尚未接入', data: null }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  assert.match(await text(page), /未读：暂不可用/)
  await page.waitForFunction(() => document.body.innerText.includes('通知（暂不可用）'), null, { timeout: 10000 })
  await context.close()

  const normalContext = await newContext(browser)
  const normalPage = await normalContext.newPage()
  await installFixture(normalPage)
  await openHash(normalPage, '/student/notices')
  await normalPage.waitForSelector('.header-right .notice-count', { timeout: 15000 })
  assert.equal((await normalPage.locator('.header-right .notice-count').innerText()).trim(), '3')
  await normalContext.close()
})

await run('空列表、403、503 三种状态互不混同', async () => {
  const emptyContext = await newContext(browser)
  const emptyPage = await emptyContext.newPage()
  await installFixture(emptyPage, {
    list: { code: 200, msg: '查询成功', total: 0, rows: [] },
    unread: { code: 200, msg: '操作成功', data: { unreadCount: 0, asOf: '2026-11-17T02:00:00.000Z' } }
  })
  await openHash(emptyPage, '/student/notices')
  await emptyPage.waitForSelector('.student-notices', { timeout: 15000 })
  assert.match(await text(emptyPage), /暂无通知/)
  assert.equal(await emptyPage.locator('.header-right .notice-count').count(), 0)
  await emptyContext.close()

  const forbiddenContext = await newContext(browser)
  const forbiddenPage = await forbiddenContext.newPage()
  await installFixture(forbiddenPage, {
    list: { code: 403, msg: '没有该教学班通知的访问权限' }
  })
  await openHash(forbiddenPage, '/student/notices')
  await forbiddenPage.waitForSelector('.student-notices', { timeout: 15000 })
  assert.match(await text(forbiddenPage), /没有该教学班通知的访问权限/)
  assert.doesNotMatch(await text(forbiddenPage), /暂无通知/)
  await forbiddenContext.close()

  const unavailableContext = await newContext(browser)
  const unavailablePage = await unavailableContext.newPage()
  await installFixture(unavailablePage, {
    list: { code: 503, msg: '学生教学班授权服务尚未接入' },
    unread: { code: 503, msg: '学生教学班授权服务尚未接入', data: null }
  })
  await openHash(unavailablePage, '/student/notices')
  await unavailablePage.waitForSelector('.student-notices', { timeout: 15000 })
  assert.match(await text(unavailablePage), /教学班授权服务暂不可用/)
  assert.match(await text(unavailablePage), /学生教学班授权服务尚未接入/)
  assert.doesNotMatch(await text(unavailablePage), /暂无通知/)
  await unavailableContext.close()
})

await run('通用业务 503 显示服务暂不可用并保留服务端消息', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, {
    list: { code: 503, msg: '服务端暂时不可用' }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  const body = await text(page)
  assert.match(body, /服务暂不可用/)
  assert.match(body, /服务端暂时不可用/)
  assert.doesNotMatch(body, /教学班授权服务暂不可用/)
  await context.close()
})

await run('网关 HTML 503 显示服务暂不可用并补充 HTTP 状态', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, {
    rawList: {
      status: 503,
      contentType: 'text/html',
      body: '<html><body><h1>503 Service Unavailable</h1></body></html>'
    }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  const body = await text(page)
  assert.match(body, /服务暂不可用/)
  assert.match(body, /请求失败（HTTP 503）/)
  assert.doesNotMatch(body, /教学班授权服务暂不可用/)
  await context.close()
})

await run('点击通知打开详情并展示服务端字段', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page)
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  const detailText = await page.locator('.detail-dialog').innerText()
  assert.match(detailText, /通知详情/)
  assert.match(detailText, new RegExp(`详情-${NOTICE_ID}`))
  assert.match(detailText, /类型\s*公告/)
  assert.match(detailText, /可见时间/)
  assert.match(detailText, /已读状态\s*未读/)
  await page.locator('.detail-dialog .detail-close').first().click()
  await page.waitForFunction(() => !document.querySelector('.detail-dialog'), null, { timeout: 10000 })
  await context.close()
})

await run('大整数通知 ID 按字符串编码并原样请求', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page, {
    listRows: [noticeRow(LARGE_NOTICE_ID, '大整数通知')]
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  const request = requests.find(item => item.detailId)
  assert.ok(request, '应请求通知详情')
  assert.equal(request.detailId, LARGE_NOTICE_ID)
  assert.ok(request.path.endsWith('/' + LARGE_NOTICE_ID), `详情路径应保留完整 ID：${request.path}`)
  const detailText = await page.locator('.detail-dialog').innerText()
  assert.match(detailText, new RegExp(`详情-${LARGE_NOTICE_ID}`))
  await context.close()
})

await run('详情 404 显示通知不存在或不可访问', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, {
    detail: { code: 404, msg: '通知不存在或当前不可访问' }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  assert.match(await page.locator('.detail-dialog').innerText(), /通知不存在或不可访问/)
  await context.close()
})

await run('详情 503 区分授权与通用故障', async () => {
  const authContext = await newContext(browser)
  const authPage = await authContext.newPage()
  await installFixture(authPage, {
    detail: { code: 503, msg: '学生教学班授权服务尚未接入' }
  })
  await openHash(authPage, '/student/notices')
  await authPage.waitForSelector('.student-notices', { timeout: 15000 })
  await authPage.locator('.notice-main').first().click()
  await authPage.waitForSelector('.detail-dialog', { timeout: 10000 })
  const authText = await authPage.locator('.detail-dialog').innerText()
  assert.match(authText, /教学班授权服务暂不可用/)
  assert.match(authText, /学生教学班授权服务尚未接入/)
  await authContext.close()

  const genericContext = await newContext(browser)
  const genericPage = await genericContext.newPage()
  await installFixture(genericPage, {
    detail: { code: 503, msg: '服务端暂时不可用' }
  })
  await openHash(genericPage, '/student/notices')
  await genericPage.waitForSelector('.student-notices', { timeout: 15000 })
  await genericPage.locator('.notice-main').first().click()
  await genericPage.waitForSelector('.detail-dialog', { timeout: 10000 })
  const genericText = await genericPage.locator('.detail-dialog').innerText()
  assert.match(genericText, /服务暂不可用/)
  assert.match(genericText, /服务端暂时不可用/)
  assert.doesNotMatch(genericText, /教学班授权服务暂不可用/)
  await genericContext.close()
})

await run('关闭详情后迟到响应不重新打开', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, { detailDelays: { [NOTICE_ID]: 1200 } })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-dialog .detail-close').first().click()
  await page.waitForFunction(() => !document.querySelector('.detail-dialog'), null, { timeout: 10000 })
  await page.waitForTimeout(1500)
  assert.equal(await page.locator('.detail-dialog').count(), 0)
  await context.close()
})

await run('切换通知后旧详情迟到响应不覆盖新详情', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, {
    listRows: [noticeRow('30001', '通知-A'), noticeRow('30002', '通知-B')],
    detailDelays: { 30001: 1200, 30002: 50 }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-dialog .detail-close').first().click()
  await page.locator('.notice-main').nth(1).click()
  await page.waitForFunction(() => document.querySelector('.detail-dialog')?.innerText.includes('详情-30002'), null, { timeout: 10000 })
  await page.waitForTimeout(1500)
  assert.match(await page.locator('.detail-dialog').innerText(), /详情-30002/)
  assert.doesNotMatch(await page.locator('.detail-dialog').innerText(), /详情-30001/)
  await context.close()
})

await run('切班后旧详情迟到响应不重新打开', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, { detailDelays: { [NOTICE_ID]: 1200 } })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.evaluate(() => {
    location.hash = '#/student/notices?courseSpaceId=101&classId=202&bindingId=9002'
  })
  await page.waitForFunction(() => !document.querySelector('.detail-dialog'), null, { timeout: 10000 })
  await page.waitForTimeout(1500)
  assert.equal(await page.locator('.detail-dialog').count(), 0)
  await context.close()
})

await run('离开通知页后迟到详情不重新打开', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, { detailDelays: { [NOTICE_ID]: 1200 } })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.evaluate(() => { location.hash = '#/student' })
  await page.waitForFunction(() => !document.querySelector('.student-notices'), null, { timeout: 10000 })
  await page.waitForTimeout(1500)
  assert.equal(await page.locator('.detail-dialog').count(), 0)
  await context.close()
})

await run('打开详情零写请求，标为已读单次 POST 并同步列表和角标', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page, { readDelays: { [NOTICE_ID]: 250 } })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  assert.equal(requests.filter(item => item.readId).length, 0, '打开详情不应产生写请求')
  const readButton = page.locator('.detail-dialog .detail-read')
  assert.equal(await readButton.innerText(), '标为已读')
  const postRequest = page.waitForRequest(request => request.method() === 'POST', { timeout: 10000 })
  await readButton.click()
  await postRequest
  assert.equal(await readButton.isDisabled(), true, '提交中按钮应禁用')
  await page.waitForFunction(() => document.body.innerText.includes('已标记为已读'), null, { timeout: 10000 })
  await page.waitForFunction(() => document.querySelector('.header-right .notice-count')?.textContent.trim() === '2', null, { timeout: 10000 })
  assert.equal(requests.filter(item => item.readId).length, 1, '只应发送一次 POST')
  const detailText = await page.locator('.detail-dialog').innerText()
  assert.match(detailText, /已标记为已读/)
  assert.match(detailText, /已读/)
  assert.match(await page.locator('.notice-item').first().innerText(), /已读/)
  await context.close()
})

await run('alreadyRead=true 按成功处理', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page, {
    read: {
      code: 200,
      msg: '操作成功',
      data: { id: NOTICE_ID, readAt: READ_AT, alreadyRead: true }
    }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-dialog .detail-read').click()
  await page.waitForFunction(() => document.body.innerText.includes('已标记为已读'), null, { timeout: 10000 })
  assert.equal(requests.filter(item => item.readId).length, 1)
  assert.match(await page.locator('.detail-dialog').innerText(), /已读/)
  await page.waitForFunction(() => document.querySelector('.header-right .notice-count')?.textContent.trim() === '2', null, { timeout: 10000 })
  await context.close()
})

await run('标为已读 404 提示不可访问并清除旧详情、刷新列表和角标', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page, {
    read: { code: 404, msg: '通知不存在或当前不可访问' }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  const listRequestsBefore = requests.filter(item => item.path.endsWith('/student/notices')).length
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-dialog .detail-read').click()
  await page.waitForFunction(() => document.querySelector('.detail-dialog')?.innerText.includes('通知不存在或不可访问'), null, { timeout: 10000 })
  const detailText = await page.locator('.detail-dialog').innerText()
  assert.doesNotMatch(detailText, /详情-30001/)
  assert.equal(requests.filter(item => item.readId).length, 1)
  await page.waitForFunction(
    count => document.querySelectorAll('.notice-item').length > 0
      && document.querySelector('.header-right .notice-count')?.textContent.trim() === '3',
    listRequestsBefore,
    { timeout: 10000 }
  )
  await context.close()
})

await run('标为已读 503 区分授权与通用故障', async () => {
  const authContext = await newContext(browser)
  const authPage = await authContext.newPage()
  await installFixture(authPage, {
    read: { code: 503, msg: '学生教学班授权服务尚未接入' }
  })
  await openHash(authPage, '/student/notices')
  await authPage.waitForSelector('.student-notices', { timeout: 15000 })
  await authPage.locator('.notice-main').first().click()
  await authPage.waitForSelector('.detail-dialog', { timeout: 10000 })
  await authPage.locator('.detail-dialog .detail-read').click()
  await authPage.waitForFunction(() => document.querySelector('.detail-dialog')?.innerText.includes('教学班授权服务暂不可用'), null, { timeout: 10000 })
  await authContext.close()

  const genericContext = await newContext(browser)
  const genericPage = await genericContext.newPage()
  await installFixture(genericPage, {
    read: { code: 503, msg: '服务端暂时不可用' }
  })
  await openHash(genericPage, '/student/notices')
  await genericPage.waitForSelector('.student-notices', { timeout: 15000 })
  await genericPage.locator('.notice-main').first().click()
  await genericPage.waitForSelector('.detail-dialog', { timeout: 10000 })
  await genericPage.locator('.detail-dialog .detail-read').click()
  await genericPage.waitForFunction(() => document.querySelector('.detail-dialog')?.innerText.includes('服务暂不可用'), null, { timeout: 10000 })
  assert.doesNotMatch(await genericPage.locator('.detail-dialog').innerText(), /教学班授权服务暂不可用/)
  await genericContext.close()
})

await run('写入成功后列表与未读数刷新失败分别提示', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, {
    listAfterRead: { code: 503, msg: '服务端暂时不可用' },
    unreadAfterRead: { code: 503, msg: '服务端暂时不可用' }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-dialog .detail-read').click()
  await page.waitForFunction(() => document.querySelector('.detail-dialog')?.innerText.includes('通知列表刷新失败'), null, { timeout: 10000 })
  const detailText = await page.locator('.detail-dialog').innerText()
  assert.match(detailText, /通知列表刷新失败/)
  assert.match(detailText, /未读数刷新失败/)
  assert.match(detailText, /已标记为已读/)
  await context.close()
})

await run('关闭并切换通知后迟到写响应不覆盖新详情', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page, {
    listRows: [noticeRow('30001', '通知-A'), noticeRow('30002', '通知-B')],
    readDelays: { 30001: 1200, 30002: 50 }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-dialog .detail-read').click()
  await page.locator('.detail-dialog .detail-close').first().click()
  await page.waitForFunction(() => !document.querySelector('.detail-dialog'), null, { timeout: 10000 })
  await page.locator('.notice-main').nth(1).click()
  await page.waitForFunction(() => document.querySelector('.detail-dialog')?.innerText.includes('详情-30002'), null, { timeout: 10000 })
  await page.waitForTimeout(1500)
  const detailText = await page.locator('.detail-dialog').innerText()
  assert.match(detailText, /详情-30002/)
  assert.doesNotMatch(detailText, /详情-30001/)
  assert.doesNotMatch(detailText, /已标记为已读/)
  assert.equal(requests.filter(item => item.readId).length, 1)
  await context.close()
})

await run('切班后迟到写响应不更新新班级状态', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, { readDelays: { [NOTICE_ID]: 1200 } })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-dialog .detail-read').click()
  await page.evaluate(() => {
    location.hash = '#/student/notices?courseSpaceId=101&classId=202&bindingId=9002'
  })
  await page.waitForFunction(() => !document.querySelector('.detail-dialog'), null, { timeout: 10000 })
  await page.waitForTimeout(1500)
  assert.equal(await page.locator('.detail-dialog').count(), 0)
  assert.doesNotMatch(await text(page), /已标记为已读/)
  await context.close()
})

await run('全部已读确认文案覆盖跨班范围，取消零写请求', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-head-actions button').click()
  await page.waitForSelector('.modal', { timeout: 10000 })
  const copy = await page.locator('.modal .confirm-text').innerText()
  assert.match(copy, /所有教学班/)
  assert.match(copy, /其他教学班/)
  assert.match(copy, /不受当前班级和分页限制/)
  await page.locator('.modal-foot button', { hasText: '取消' }).click()
  await page.waitForFunction(() => !document.querySelector('.modal'), null, { timeout: 10000 })
  assert.equal(requests.filter(item => item.method === 'PUT').length, 0, '取消不应发送 PUT')
  await context.close()
})

await run('全部已读确认后单次 PUT 且不带筛选参数，成功后列表和角标同步', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openHash(page, '/student/notices?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-head-actions button').click()
  await page.waitForSelector('.modal', { timeout: 10000 })
  const putRequest = page.waitForRequest(request => request.method() === 'PUT', { timeout: 10000 })
  await page.locator('.modal-foot button', { hasText: '确认' }).click()
  await putRequest
  await page.waitForFunction(() => document.body.innerText.includes('已标记 3 条为已读'), null, { timeout: 10000 })
  await page.waitForFunction(() => document.querySelector('.student-notices')?.innerText.includes('未读：0'), null, { timeout: 10000 })
  await page.waitForFunction(() => !document.querySelector('.header-right .notice-count'), null, { timeout: 10000 })
  assert.match(await page.locator('.notice-item').first().innerText(), /已读/)
  const puts = requests.filter(item => item.method === 'PUT')
  assert.equal(puts.length, 1, '确认后只应发送一次 PUT')
  assert.equal(puts[0].path, '/education/student/notices/read-all')
  assert.equal(puts[0].query, '')
  assert.ok(puts[0].body === null || puts[0].body === '', 'PUT 不应带请求体')
  assert.ok(requests.some(item => item.path.endsWith('/unread-count') && item.query.includes('bindingId=9001')),
    '刷新角标应使用当前班级范围')
  await context.close()
})

await run('全部已读 markedCount=0 按成功处理', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page, {
    readAll: {
      code: 200,
      msg: '操作成功',
      data: { markedCount: 0, unreadCount: 0, operatedAt: '2026-11-17T03:00:00.000Z' }
    }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-head-actions button').click()
  await page.waitForSelector('.modal', { timeout: 10000 })
  await page.locator('.modal-foot button', { hasText: '确认' }).click()
  await page.waitForFunction(() => document.body.innerText.includes('没有需要标记的通知'), null, { timeout: 10000 })
  assert.equal(requests.filter(item => item.method === 'PUT').length, 1)
  await page.waitForFunction(() => !document.querySelector('.header-right .notice-count'), null, { timeout: 10000 })
  await context.close()
})

await run('全部已读确认前切班或离开页面不发请求', async () => {
  const switchContext = await newContext(browser)
  const switchPage = await switchContext.newPage()
  const switchRequests = await installFixture(switchPage)
  await openHash(switchPage, '/student/notices')
  await switchPage.waitForSelector('.student-notices', { timeout: 15000 })
  await switchPage.locator('.notice-head-actions button').click()
  await switchPage.waitForSelector('.modal', { timeout: 10000 })
  await switchPage.evaluate(() => {
    location.hash = '#/student/notices?courseSpaceId=101&classId=202&bindingId=9002'
  })
  await switchPage.waitForFunction(() => !document.querySelector('.modal'), null, { timeout: 10000 })
  await switchPage.waitForTimeout(300)
  assert.equal(switchRequests.filter(item => item.method === 'PUT').length, 0, '确认前切班不应发送 PUT')
  await switchContext.close()

  const leaveContext = await newContext(browser)
  const leavePage = await leaveContext.newPage()
  const leaveRequests = await installFixture(leavePage)
  await openHash(leavePage, '/student/notices')
  await leavePage.waitForSelector('.student-notices', { timeout: 15000 })
  await leavePage.locator('.notice-head-actions button').click()
  await leavePage.waitForSelector('.modal', { timeout: 10000 })
  await leavePage.evaluate(() => { location.hash = '#/student' })
  await leavePage.waitForFunction(() => !document.querySelector('.student-notices'), null, { timeout: 10000 })
  await leavePage.waitForTimeout(300)
  assert.equal(leaveRequests.filter(item => item.method === 'PUT').length, 0, '确认前离开页面不应发送 PUT')
  await leaveContext.close()
})

await run('全部已读错误沿用 403/503 展示', async () => {
  const forbiddenContext = await newContext(browser)
  const forbiddenPage = await forbiddenContext.newPage()
  await installFixture(forbiddenPage, {
    readAll: { code: 403, msg: '没有权限' }
  })
  await openHash(forbiddenPage, '/student/notices')
  await forbiddenPage.waitForSelector('.student-notices', { timeout: 15000 })
  await forbiddenPage.locator('.notice-head-actions button').click()
  await forbiddenPage.waitForSelector('.modal', { timeout: 10000 })
  await forbiddenPage.locator('.modal-foot button', { hasText: '确认' }).click()
  await forbiddenPage.waitForFunction(() => document.querySelector('.modal-error')?.textContent.includes('没有权限执行全部已读'), null, { timeout: 10000 })
  await forbiddenContext.close()

  const authContext = await newContext(browser)
  const authPage = await authContext.newPage()
  await installFixture(authPage, {
    readAll: { code: 503, msg: '学生教学班授权服务尚未接入' }
  })
  await openHash(authPage, '/student/notices')
  await authPage.waitForSelector('.student-notices', { timeout: 15000 })
  await authPage.locator('.notice-head-actions button').click()
  await authPage.waitForSelector('.modal', { timeout: 10000 })
  await authPage.locator('.modal-foot button', { hasText: '确认' }).click()
  await authPage.waitForFunction(() => document.querySelector('.modal-error')?.textContent.includes('教学班授权服务暂不可用'), null, { timeout: 10000 })
  await authContext.close()
})

await run('全部已读切班或离开后迟到响应不更新新状态', async () => {
  const switchContext = await newContext(browser)
  const switchPage = await switchContext.newPage()
  await installFixture(switchPage, { readAllDelay: 1200 })
  await openHash(switchPage, '/student/notices')
  await switchPage.waitForSelector('.student-notices', { timeout: 15000 })
  await switchPage.locator('.notice-head-actions button').click()
  await switchPage.waitForSelector('.modal', { timeout: 10000 })
  await switchPage.locator('.modal-foot button', { hasText: '确认' }).click()
  await switchPage.evaluate(() => {
    location.hash = '#/student/notices?courseSpaceId=101&classId=202&bindingId=9002'
  })
  await switchPage.waitForFunction(() => !document.querySelector('.modal'), null, { timeout: 10000 })
  await switchPage.waitForTimeout(1500)
  assert.doesNotMatch(await text(switchPage), /已标记 .* 条为已读/)
  await switchContext.close()

  const leaveContext = await newContext(browser)
  const leavePage = await leaveContext.newPage()
  await installFixture(leavePage, { readAllDelay: 1200 })
  await openHash(leavePage, '/student/notices')
  await leavePage.waitForSelector('.student-notices', { timeout: 15000 })
  await leavePage.locator('.notice-head-actions button').click()
  await leavePage.waitForSelector('.modal', { timeout: 10000 })
  await leavePage.locator('.modal-foot button', { hasText: '确认' }).click()
  await leavePage.evaluate(() => { location.hash = '#/student' })
  await leavePage.waitForFunction(() => !document.querySelector('.student-notices'), null, { timeout: 10000 })
  await leavePage.waitForTimeout(1500)
  assert.doesNotMatch(await text(leavePage), /已标记 .* 条为已读/)
  await leaveContext.close()
})

await run('单条已读成功后立即刷新顶栏，列表未完成时离开不晚发请求', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, { listAfterReadDelay: 1200 })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.evaluate(() => {
    window.__noticeHeaderRefreshCount = 0
    window.addEventListener('edu:refresh-header', () => { window.__noticeHeaderRefreshCount++ })
  })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-dialog .detail-read').click()
  await page.waitForFunction(() => window.__noticeHeaderRefreshCount >= 1, null, { timeout: 800 })
  const countBeforeLeave = await page.evaluate(() => window.__noticeHeaderRefreshCount)
  await page.evaluate(() => { location.hash = '#/student' })
  await page.waitForFunction(() => !document.querySelector('.student-notices'), null, { timeout: 10000 })
  await page.waitForTimeout(1500)
  assert.ok(countBeforeLeave >= 1, '列表刷新完成前应已触发顶栏刷新')
  assert.equal(await page.locator('.detail-dialog').count(), 0)
  assert.doesNotMatch(await text(page), /已标记为已读/)
  await context.close()
})

await run('离开通知页后迟到写响应不更新新页面', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, { readDelays: { [NOTICE_ID]: 1200 } })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-dialog .detail-read').click()
  await page.evaluate(() => { location.hash = '#/student' })
  await page.waitForFunction(() => !document.querySelector('.student-notices'), null, { timeout: 10000 })
  await page.waitForTimeout(1500)
  assert.equal(await page.locator('.detail-dialog').count(), 0)
  assert.doesNotMatch(await text(page), /已标记为已读/)
  await context.close()
})

await run('详情弹窗打开聚焦、Esc 关闭并归还焦点', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page)
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  const focusedInside = await page.evaluate(() => Boolean(document.activeElement?.closest('.detail-dialog')))
  assert.equal(focusedInside, true, '打开后焦点应进入弹窗')
  await page.keyboard.press('Escape')
  await page.waitForFunction(() => !document.querySelector('.detail-dialog'), null, { timeout: 10000 })
  const focusedId = await page.evaluate(() => document.activeElement?.getAttribute('data-notice-id'))
  assert.equal(focusedId, NOTICE_ID, '关闭后焦点应回到触发按钮')
  await context.close()
})

await run('课程公告列表按当前班级查询并展示详情', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openHash(page, '/student/notices?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.getByRole('tab', { name: '课程公告' }).click()
  await page.waitForSelector('.ann-main', { timeout: 15000 })
  const listRequest = requests.find(item => item.announcementBindingId === '9001')
  assert.ok(listRequest, '应请求当前班级公告列表')
  await page.locator('.ann-main').first().click()
  await page.waitForSelector('.ann-dialog', { timeout: 10000 })
  const detailText = await page.locator('.ann-dialog').innerText()
  assert.match(detailText, new RegExp(`公告-${ANNOUNCEMENT_ID}`))
  assert.match(detailText, new RegExp(`公告正文-${ANNOUNCEMENT_ID}`))
  assert.match(detailText, /开放：/)
  const detailRequest = requests.find(item => item.announcementDetailId === ANNOUNCEMENT_ID)
  assert.ok(detailRequest, '应请求公告详情')
  assert.ok(detailRequest.path.endsWith('/' + ANNOUNCEMENT_ID))
  await context.close()
})

await run('无班级上下文时公告列表省略 bindingId', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.getByRole('tab', { name: '课程公告' }).click()
  await page.waitForSelector('.ann-main', { timeout: 15000 })
  const listRequest = requests.find(item => item.announcementBindingId === '')
  assert.ok(listRequest, '应请求全部班级公告列表')
  assert.equal(new URLSearchParams(listRequest.query).has('bindingId'), false, '无班级时应省略 bindingId')
  await context.close()
})

await run('从 ANNOUNCEMENT 通知打开对应公告', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-link', { hasText: '查看公告' }).click()
  await page.waitForSelector('.ann-dialog', { timeout: 15000 })
  assert.equal(await page.getByRole('tab', { name: '课程公告' }).getAttribute('aria-selected'), 'true')
  const detailText = await page.locator('.ann-dialog').innerText()
  assert.match(detailText, new RegExp(`公告-${ANNOUNCEMENT_ID}`))
  assert.match(detailText, new RegExp(`公告正文-${ANNOUNCEMENT_ID}`))
  const detailRequest = requests.find(item => item.announcementDetailId === ANNOUNCEMENT_ID)
  assert.ok(detailRequest, '应按 sourceId 请求公告详情')
  assert.ok(detailRequest.path.endsWith('/' + ANNOUNCEMENT_ID))
  await context.close()
})

await run('公告不可见返回 404 显示公告不存在或不可访问', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, {
    announcementDetail: { code: 404, msg: '公告不存在或当前不可访问' }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.getByRole('tab', { name: '课程公告' }).click()
  await page.waitForSelector('.ann-main', { timeout: 15000 })
  await page.locator('.ann-main').first().click()
  await page.waitForSelector('.ann-dialog', { timeout: 10000 })
  assert.match(await page.locator('.ann-dialog').innerText(), /公告不存在或不可访问/)
  await context.close()
})

await run('切班后迟到公告列表响应不覆盖新班级', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, {
    announcementListDelays: { 9001: 1200, 9002: 50 },
    announcementList: bindingId => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [announcementRow(ANNOUNCEMENT_ID, bindingId || '9001', `公告-${bindingId || 'ALL'}`)]
    })
  })
  await openHash(page, '/student/notices?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  const listRequest = page.waitForRequest(request => request.url().includes('/education/student/announcements')
    && request.url().includes('bindingId=9001'), { timeout: 10000 })
  await page.getByRole('tab', { name: '课程公告' }).click()
  await listRequest
  await page.evaluate(() => {
    location.hash = '#/student/notices?courseSpaceId=101&classId=202&bindingId=9002&tab=announcements'
  })
  await page.waitForFunction(() => document.querySelector('.ann-main')?.innerText.includes('公告-9002'), null, { timeout: 15000 })
  await page.waitForTimeout(1500)
  const listText = await page.locator('.student-announcements').innerText()
  assert.match(listText, /公告-9002/)
  assert.doesNotMatch(listText, /公告-9001/)
  await context.close()
})

await run('切页签后迟到公告详情不重新打开', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, {
    announcementDetailDelays: { [ANNOUNCEMENT_ID]: 1200 }
  })
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.getByRole('tab', { name: '课程公告' }).click()
  await page.waitForSelector('.ann-main', { timeout: 15000 })
  await page.locator('.ann-main').first().click()
  await page.waitForSelector('.ann-dialog', { timeout: 10000 })
  await page.getByRole('tab', { name: '学习通知' }).click()
  await page.waitForFunction(() => !document.querySelector('.ann-dialog'), null, { timeout: 10000 })
  await page.waitForTimeout(1500)
  assert.equal(await page.locator('.ann-dialog').count(), 0)
  assert.match(await text(page), /学习通知/)
  await context.close()
})

await run('从公告页签点顶栏通知回学习通知并保留当前班级', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page)
  await openHash(page, '/student/notices?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.getByRole('tab', { name: '课程公告' }).click()
  await page.waitForSelector('.ann-main', { timeout: 15000 })
  assert.match(await page.evaluate(() => location.hash), /tab=announcements/)
  const headerNotice = page.locator('.header-right button', { hasText: '通知' }).first()
  await headerNotice.click()
  await page.waitForFunction(() => location.hash.includes('tab=notices'), null, { timeout: 10000 })
  await page.waitForSelector('.notice-main', { timeout: 15000 })
  const hash = await page.evaluate(() => location.hash)
  assert.match(hash, /bindingId=9001/)
  assert.equal(await page.getByRole('tab', { name: '学习通知' }).getAttribute('aria-selected'), 'true')
  assert.equal(await page.locator('.student-announcements').count(), 0)
  await headerNotice.click()
  await page.waitForTimeout(300)
  assert.equal(await page.getByRole('tab', { name: '学习通知' }).getAttribute('aria-selected'), 'true')
  await context.close()
})

await run('公告详情弹窗聚焦、Esc 关闭并归还焦点', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page)
  await openHash(page, '/student/notices')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.getByRole('tab', { name: '课程公告' }).click()
  await page.waitForSelector('.ann-main', { timeout: 15000 })
  await page.locator('.ann-main').first().click()
  await page.waitForSelector('.ann-dialog', { timeout: 10000 })
  const focusedInside = await page.evaluate(() => Boolean(document.activeElement?.closest('.ann-dialog')))
  assert.equal(focusedInside, true, '打开公告详情后焦点应进入弹窗')
  await page.keyboard.press('Escape')
  await page.waitForFunction(() => !document.querySelector('.ann-dialog'), null, { timeout: 10000 })
  const focusedId = await page.evaluate(() => document.activeElement?.getAttribute('data-announcement-id'))
  assert.equal(focusedId, ANNOUNCEMENT_ID, '关闭后焦点应回到公告触发按钮')
  await context.close()
})

await run('通知筛选发送 kind/unreadOnly 且回到第一页，过滤空值省略', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const listRows = Array.from({ length: 10 }, (_, index) =>
    noticeRow(String(30001 + index), `通知-${index}`))
  const requests = await installFixture(page, {
    list: { code: 200, msg: '查询成功', total: 25, rows: listRows }
  })
  await openHash(page, '/student/notices?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.notice-main', { timeout: 15000 })
  const initialList = requests.find(item => item.path.endsWith('/student/notices'))
  assert.ok(initialList)
  const initialQuery = new URLSearchParams(initialList.query)
  assert.equal(initialQuery.has('kind'), false)
  assert.equal(initialQuery.has('unreadOnly'), false)

  const pageTwo = page.waitForRequest(request => {
    const url = new URL(request.url())
    return url.pathname.endsWith('/education/student/notices')
      && url.searchParams.get('pageNum') === '2'
  }, { timeout: 10000 })
  await page.locator('.pager button', { hasText: '下一页' }).click()
  await pageTwo

  await page.selectOption('.notice-kind-filter', 'ASSIGNMENT')
  const filtered = page.waitForRequest(request => {
    const url = new URL(request.url())
    return url.pathname.endsWith('/education/student/notices')
      && url.searchParams.get('kind') === 'ASSIGNMENT'
      && url.searchParams.get('unreadOnly') === 'true'
      && url.searchParams.get('pageNum') === '1'
  }, { timeout: 10000 })
  await page.locator('.notice-unread-filter input').check()
  const filteredRequest = await filtered
  const filteredQuery = new URL(filteredRequest.url()).searchParams
  assert.equal(filteredQuery.get('bindingId'), '9001')
  assert.equal(filteredQuery.get('pageNum'), '1')
  const unreadRequests = requests.filter(item => item.path.endsWith('/unread-count'))
  assert.ok(unreadRequests.length > 0)
  assert.ok(unreadRequests.every(item => {
    const query = new URLSearchParams(item.query)
    return !query.has('kind') && !query.has('unreadOnly')
  }), '未读角标不应带列表筛选参数')
  await context.close()
})

await run('筛选状态下全部已读仍为无参数全教学班范围', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openHash(page, '/student/notices')
  await page.waitForSelector('.notice-main', { timeout: 15000 })
  await page.selectOption('.notice-kind-filter', 'GRADE')
  await page.locator('.notice-unread-filter input').check()
  await page.waitForTimeout(200)
  await page.locator('.notice-head-actions button').click()
  await page.waitForSelector('.modal', { timeout: 10000 })
  const copy = await page.locator('.modal .confirm-text').innerText()
  assert.match(copy, /所有教学班/)
  assert.match(copy, /其他教学班/)
  assert.match(copy, /不受当前班级和分页限制/)
  const putRequest = page.waitForRequest(request => request.method() === 'PUT', { timeout: 10000 })
  await page.locator('.modal-foot button', { hasText: '确认' }).click()
  await putRequest
  const put = requests.find(item => item.method === 'PUT')
  assert.ok(put)
  assert.equal(put.query, '')
  assert.ok(put.body === null || put.body === '')
  await context.close()
})

await run('学生从通知打开公告后关闭并切班，不重发不重开', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page, {
    announcementList: bindingId => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [announcementRow(ANNOUNCEMENT_ID, bindingId || '9001', `公告-${bindingId || 'ALL'}`)]
    })
  })
  await openHash(page, '/student/notices?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.student-notices', { timeout: 15000 })
  await page.locator('.notice-main').first().click()
  await page.waitForSelector('.detail-dialog', { timeout: 10000 })
  await page.locator('.detail-link', { hasText: '查看公告' }).click()
  await page.waitForSelector('.ann-dialog', { timeout: 15000 })
  const detailRequestsBefore = requests.filter(item => item.announcementDetailId === ANNOUNCEMENT_ID).length
  assert.equal(detailRequestsBefore, 1)
  await page.keyboard.press('Escape')
  await page.waitForFunction(() => !document.querySelector('.ann-dialog'), null, { timeout: 10000 })
  await page.locator('.workspace-context select').selectOption('101:202')
  await page.waitForFunction(() => document.querySelector('.ann-main')?.innerText.includes('公告-9002'), null, { timeout: 15000 })
  await page.waitForTimeout(1200)
  assert.equal(await page.locator('.ann-dialog').count(), 0, '切班后不得重开旧公告')
  assert.equal(requests.filter(item => item.announcementDetailId === ANNOUNCEMENT_ID).length,
    detailRequestsBefore, '切班后不得重发旧公告详情')
  assert.equal(await page.getByRole('tab', { name: '课程公告' }).getAttribute('aria-selected'), 'true')
  assert.ok(requests.some(item => item.announcementBindingId === '9002'))
  await context.close()
})

await run('教师未选教学班时提示选择班级且零列表请求', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: ['education:announcement:list', 'education:scope:read']
  })
  await openHash(page, '/teacher?section=notice')
  await page.waitForSelector('.teacher-announcements', { timeout: 15000 })
  assert.match(await text(page), /请先选择有效教学班/)
  assert.equal(await page.locator('.teacher-announcements .ann-create').count(), 0)
  assert.equal(requests.filter(item => item.teacherAnnouncementBindingId !== undefined).length, 0)
  await context.close()
})

await run('教师缺公告权限或缺服务端路由时直达被拦截', async () => {
  const permissionContext = await newContext(browser, 'teacher')
  const permissionPage = await permissionContext.newPage()
  await installFixture(permissionPage, {
    role: 'teacher',
    permissions: ['education:scope:read']
  })
  await openHash(permissionPage, '/teacher/announcements')
  await permissionPage.waitForFunction(() => !location.hash.includes('/teacher/announcements'), null, { timeout: 10000 })
  assert.equal(await permissionPage.locator('.teacher-announcements').count(), 0)
  assert.match(await text(permissionPage), /没有访问权限|未开放/)
  await permissionContext.close()

  const routeContext = await newContext(browser, 'teacher')
  const routePage = await routeContext.newPage()
  await installFixture(routePage, {
    role: 'teacher',
    permissions: ['education:announcement:list', 'education:scope:read'],
    includeTeacherAnnouncementRoute: false
  })
  await openHash(routePage, '/teacher/announcements')
  await routePage.waitForFunction(() => !location.hash.includes('/teacher/announcements'), null, { timeout: 10000 })
  assert.equal(await routePage.locator('.teacher-announcements').count(), 0)
  assert.match(await text(routePage), /没有访问权限|未开放/)
  await routeContext.close()
})

await run('教师按班级查询公告、状态筛选并展示 visibilityState 详情', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: ['education:announcement:list', 'education:scope:read'],
    teacherAnnouncementList: bindingId => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [teacherAnnouncementRow(TEACHER_ANNOUNCEMENT_ID, bindingId || '9001', 'OPEN', '教师公告-OPEN')]
    })
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements', { timeout: 15000 })
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  assert.ok(requests.some(item => item.teacherAnnouncementBindingId === '9001'))
  const statusRequest = page.waitForRequest(request => {
    const url = new URL(request.url())
    return url.pathname.endsWith('/education/teacher/announcements')
      && url.searchParams.get('status') === 'PUBLISHED'
      && url.searchParams.get('pageNum') === '1'
  }, { timeout: 10000 })
  await page.locator('.teacher-announcements .status-filter').selectOption('PUBLISHED')
  await statusRequest
  await page.locator('.teacher-announcements .ann-main').first().click()
  await page.waitForSelector('.teacher-announcements .ann-dialog', { timeout: 10000 })
  const detailText = await page.locator('.teacher-announcements .ann-dialog').innerText()
  assert.match(detailText, new RegExp(`教师公告-${TEACHER_ANNOUNCEMENT_ID}`))
  assert.match(detailText, new RegExp(`教师公告正文-${TEACHER_ANNOUNCEMENT_ID}`))
  assert.match(detailText, /展示中/)
  assert.ok(requests.some(item => item.teacherAnnouncementDetailId === TEACHER_ANNOUNCEMENT_ID))
  await context.close()
})

await run('教师切班后旧公告列表迟到响应不覆盖新班级', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, {
    role: 'teacher',
    permissions: ['education:announcement:list', 'education:scope:read'],
    teacherAnnouncementListDelays: { 9001: 1200, 9002: 50 },
    teacherAnnouncementList: bindingId => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [teacherAnnouncementRow(TEACHER_ANNOUNCEMENT_ID, bindingId || '9001', 'OPEN',
        `教师公告-${bindingId || 'ALL'}`)]
    })
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements', { timeout: 15000 })
  await page.evaluate(() => {
    location.hash = '#/teacher/announcements?courseSpaceId=101&classId=202&bindingId=9002'
  })
  await page.waitForFunction(() => document.querySelector('.teacher-announcements .ann-main')?.innerText.includes('教师公告-9002'), null, { timeout: 15000 })
  await page.waitForTimeout(1500)
  const body = await page.locator('.teacher-announcements').innerText()
  assert.match(body, /教师公告-9002/)
  assert.doesNotMatch(body, /教师公告-9001/)
  await context.close()
})

await run('教师新建入口需要有效班级、add 权限和当前班级发布权限', async () => {
  const cases = [
    { label: '全部满足', permissions: TEACHER_WRITE_PERMISSIONS, canPublish: true, expected: 1 },
    {
      label: '缺 add 权限',
      permissions: ['education:announcement:list', 'education:announcement:edit', 'education:scope:read'],
      canPublish: true,
      expected: 0
    },
    { label: '班级不允许发布', permissions: TEACHER_WRITE_PERMISSIONS, canPublish: false, expected: 0 }
  ]
  for (const item of cases) {
    const context = await newContext(browser, 'teacher')
    const page = await context.newPage()
    await installFixture(page, {
      role: 'teacher',
      permissions: item.permissions,
      contextOptions: contextOptions('teacher', item.canPublish),
      contextCheck: url => contextCheck(url, item.canPublish)
    })
    await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
    await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
    assert.equal(await page.locator('.teacher-announcements .ann-create').count(), item.expected, item.label)
    await context.close()
  }
})

await run('教师新建草稿提交当前班级 bindingId、UTC 时间并重拉列表', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementCreateDelay: 350,
    teacherAnnouncementCreate: payload => ({
      code: 200,
      msg: '操作成功',
      data: {
        ...teacherAnnouncementRow('81001', payload.bindingId, 'DRAFT', payload.title),
        body: payload.body,
        opensAt: payload.opensAt,
        closesAt: payload.closesAt
      }
    }),
    teacherAnnouncementList: bindingId => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [teacherAnnouncementRow('80001', bindingId, 'DRAFT', `列表-${bindingId}`)]
    })
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  const listBefore = teacherListRequests(requests).length

  await page.locator('.teacher-announcements .ann-create').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  await page.locator('#ann-form-title').fill('新建草稿标题')
  await page.locator('#ann-form-body').fill('新建草稿正文')
  await page.locator('#ann-form-opens').fill('2026-11-18T09:30')
  await page.locator('#ann-form-closes').fill('2026-11-18T11:45')
  await page.locator('.ann-save').click()
  await page.waitForFunction(() => document.querySelector('.ann-save')?.disabled === true)
  await page.waitForSelector('.ann-form-dialog', { state: 'detached', timeout: 10000 })

  const createRequests = teacherCreateRequests(requests)
  assert.equal(createRequests.length, 1)
  const payload = createRequests[0].payload
  assert.equal(payload.bindingId, '9001')
  assert.equal(typeof payload.bindingId, 'string')
  assert.equal(payload.title, '新建草稿标题')
  assert.equal(payload.body, '新建草稿正文')
  assert.equal(payload.opensAt, new Date('2026-11-18T09:30').toISOString())
  assert.equal(payload.closesAt, new Date('2026-11-18T11:45').toISOString())

  await page.waitForFunction(() => document.querySelector('.ann-notice')?.textContent.includes('公告草稿已创建'), null, { timeout: 10000 })
  await page.waitForFunction(() => document.querySelector('.ann-item')?.innerText.includes('列表-9001'), null, { timeout: 10000 })
  assert.ok(teacherListRequests(requests).length > listBefore, '成功后应重拉当前列表')
  await context.close()
})

await run('教师草稿表单校验标题、正文码点、UTF-8 字节和关闭时间', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  await page.locator('.teacher-announcements .ann-create').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })

  await page.locator('.ann-save').click()
  await page.waitForFunction(() => document.querySelector('.ann-form-error')?.textContent.includes('标题不能为空'))

  await page.locator('#ann-form-title').fill('标题')
  await page.locator('#ann-form-body').fill('😀'.repeat(16384))
  await page.waitForFunction(() => document.querySelector('#ann-form-body + .ann-count')?.textContent.includes('65536 / 65535'))
  await page.locator('.ann-save').click()
  await page.waitForFunction(() => document.querySelector('.ann-form-error')?.textContent.includes('UTF-8 编码后超过 65535 字节'))

  await page.locator('#ann-form-body').fill('字'.repeat(20001))
  await page.waitForFunction(() => document.querySelector('#ann-form-body + .ann-count')?.textContent.includes('20001 / 20000'))
  await page.locator('.ann-save').click()
  await page.waitForFunction(() => document.querySelector('.ann-form-error')?.textContent.includes('正文超过 20000 字'))

  await page.locator('#ann-form-body').fill('有效正文')
  await page.locator('#ann-form-opens').fill('2026-11-18T10:00')
  await page.locator('#ann-form-closes').fill('2026-11-18T10:00')
  await page.locator('.ann-save').click()
  await page.waitForFunction(() => document.querySelector('.ann-form-error')?.textContent.includes('关闭时间必须晚于开放时间'))

  assert.equal(teacherCreateRequests(requests).length, 0, '校验失败不得发送创建请求')
  await context.close()
})

await run('教师标题按 Unicode 码点限制 200 字', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })

  await page.locator('.teacher-announcements .ann-create').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  const maxTitle = '😀'.repeat(200)
  await page.locator('#ann-form-title').fill(maxTitle)
  await page.locator('#ann-form-body').fill('正文')
  await page.waitForFunction(() => document.querySelector('#ann-form-title + .ann-count')?.textContent.includes('200 / 200'))
  await page.locator('.ann-save').click()
  await page.waitForSelector('.ann-form-dialog', { state: 'detached', timeout: 10000 })
  const createRequests = teacherCreateRequests(requests)
  assert.equal(createRequests.length, 1)
  assert.equal(Array.from(createRequests[0].payload.title).length, 200)

  await page.locator('.teacher-announcements .ann-create').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  await page.locator('#ann-form-title').fill('😀'.repeat(201))
  await page.locator('#ann-form-body').fill('正文')
  await page.locator('.ann-save').click()
  await page.waitForFunction(() => document.querySelector('.ann-form-error')?.textContent.includes('标题最多 200 字'))
  assert.equal(teacherCreateRequests(requests).length, 1, '201 码点标题不得发送请求')
  await context.close()
})

await run('教师成功响应格式不正确时保留表单', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementCreate: { code: 200, msg: '操作成功', data: null }
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  await page.locator('.teacher-announcements .ann-create').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  await page.locator('#ann-form-title').fill('响应格式')
  await page.locator('#ann-form-body').fill('成功响应缺少公告对象。')
  await page.locator('.ann-save').click()

  await page.waitForFunction(() => document.querySelector('.ann-form-error')?.textContent.includes('公告保存响应格式不正确'))
  assert.equal(await page.locator('.ann-form-dialog').count(), 1)
  assert.equal(await page.locator('.ann-save').isDisabled(), false)
  assert.equal(teacherCreateRequests(requests).length, 1)
  await context.close()
})

await run('教师重复点击保存只发送一次创建请求', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementCreateDelay: 650
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  await page.locator('.teacher-announcements .ann-create').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  await page.locator('#ann-form-title').fill('只提交一次')
  await page.locator('#ann-form-body').fill('重复点击不得重复创建。')

  await page.locator('.ann-save').evaluate(button => {
    button.click()
    button.click()
  })
  await page.waitForFunction(() => document.querySelector('.ann-save')?.disabled === true)
  await page.waitForSelector('.ann-form-dialog', { state: 'detached', timeout: 10000 })
  assert.equal(teacherCreateRequests(requests).length, 1)
  await context.close()
})

await run('教师新建 403 保留表单和服务端权限提示', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementCreate: { code: 403, msg: '无该教学班发布权限' }
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  await page.locator('.teacher-announcements .ann-create').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  await page.locator('#ann-form-title').fill('无权限草稿')
  await page.locator('#ann-form-body').fill('服务端应再次拒绝。')
  await page.locator('.ann-save').click()

  await page.waitForFunction(() => document.querySelector('.ann-form-error')?.textContent.includes('没有该公告操作的权限'))
  const errorText = await page.locator('.ann-form-error').innerText()
  assert.match(errorText, /无该教学班发布权限/)
  assert.equal(await page.locator('.ann-form-dialog').count(), 1, '失败后应保留表单和输入')
  assert.equal(await page.locator('.ann-save').isDisabled(), false)
  await context.close()
})

await run('教师新建 503 区分教学班授权不可用', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementCreate: { code: 503, msg: '教师教学班授权服务尚未接入' }
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  await page.locator('.teacher-announcements .ann-create').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  await page.locator('#ann-form-title').fill('授权不可用')
  await page.locator('#ann-form-body').fill('应显示授权服务提示。')
  await page.locator('.ann-save').click()

  await page.waitForFunction(() => document.querySelector('.ann-form-error')?.textContent.includes('教学班授权服务暂不可用'))
  assert.match(await page.locator('.ann-form-error').innerText(), /教师教学班授权服务尚未接入/)
  await context.close()
})

await run('教师新建超时引导先查列表再决定重试', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    abortTeacherAnnouncementCreate: 'timedout'
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  const listBefore = teacherListRequests(requests).length
  await page.locator('.teacher-announcements .ann-create').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  await page.locator('#ann-form-title').fill('超时草稿')
  await page.locator('#ann-form-body').fill('创建接口没有幂等键。')
  await page.locator('.ann-save').click()

  await page.waitForFunction(() => document.querySelector('.ann-form-error')?.textContent.includes('先刷新列表确认'))
  assert.equal(await page.locator('.ann-check-list').count(), 1)
  assert.equal(await page.locator('.ann-save').isDisabled(), false)
  assert.equal(teacherCreateRequests(requests).length, 1, '网络失败后不得自动重发创建请求')
  await page.locator('.ann-check-list').click()
  await page.waitForSelector('.ann-form-dialog', { state: 'detached', timeout: 10000 })
  assert.ok(teacherListRequests(requests).length > listBefore, '确认按钮应重拉列表')
  await context.close()
})

await run('教师编辑入口按 edit 权限、草稿状态和创建者或发布权限显示', async () => {
  const rows = [
    teacherAnnouncementRow('80001', '9001', 'DRAFT', '我创建的草稿'),
    { ...teacherAnnouncementRow('80002', '9001', 'DRAFT', '他人创建的草稿'), createdByUserId: '9003' },
    teacherAnnouncementRow('80003', '9001', 'OPEN', '已发布公告')
  ]
  const cases = [
    {
      label: '无发布权限仅能编辑本人草稿',
      permissions: ['education:announcement:list', 'education:announcement:edit', 'education:scope:read'],
      canPublish: false,
      expectedIds: ['80001']
    },
    {
      label: '有发布权限可编辑本班草稿',
      permissions: ['education:announcement:list', 'education:announcement:edit', 'education:scope:read'],
      canPublish: true,
      expectedIds: ['80001', '80002']
    },
    {
      label: '缺 edit 权限不显示编辑入口',
      permissions: ['education:announcement:list', 'education:scope:read'],
      canPublish: true,
      expectedIds: []
    }
  ]
  for (const item of cases) {
    const context = await newContext(browser, 'teacher')
    const page = await context.newPage()
    await installFixture(page, {
      role: 'teacher',
      permissions: item.permissions,
      contextOptions: contextOptions('teacher', item.canPublish),
      contextCheck: url => contextCheck(url, item.canPublish),
      teacherAnnouncementList: () => ({ code: 200, msg: '查询成功', total: rows.length, rows })
    })
    await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
    await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
    const ids = await page.locator('.teacher-announcements .ann-edit').evaluateAll(nodes =>
      nodes.map(node => node.getAttribute('data-id')))
    assert.deepEqual(ids, item.expectedIds, item.label)
    await context.close()
  }
})

await run('教师编辑草稿只提交四个字段并重拉列表', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementList: bindingId => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [teacherAnnouncementRow('80001', bindingId, 'DRAFT', '列表标题')]
    }),
    teacherAnnouncementDetailDelays: { 80001: 350 },
    teacherAnnouncementDetail: id => teacherAnnouncementDetailBody(id, {
      title: '服务端标题',
      body: '服务端正文',
      opensAt: '2026-11-17T02:00:00.000Z',
      closesAt: '2026-11-18T02:00:00.000Z',
      status: 'DRAFT',
      visibilityState: 'DRAFT',
      publishedByUserId: null
    })
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  const listBefore = teacherListRequests(requests).length

  await page.locator('.teacher-announcements .ann-edit[data-id="80001"]').click()
  await page.waitForSelector('.ann-form-dialog', { timeout: 10000 })
  assert.equal(await page.locator('.ann-save').isDisabled(), true, '详情未加载前不得保存')
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  assert.equal(await page.locator('#ann-form-title').inputValue(), '服务端标题')
  assert.equal(await page.locator('#ann-form-body').inputValue(), '服务端正文')

  await page.locator('#ann-form-title').fill('修改后标题')
  await page.locator('#ann-form-body').fill('修改后正文')
  await page.locator('#ann-form-opens').fill('2026-11-18T09:15')
  await page.locator('#ann-form-closes').fill('')
  await page.locator('.ann-save').click()
  await page.waitForSelector('.ann-form-dialog', { state: 'detached', timeout: 10000 })

  const updates = teacherUpdateRequests(requests)
  assert.equal(updates.length, 1)
  const update = updates[0]
  assert.equal(update.teacherAnnouncementUpdateId, '80001')
  assert.deepEqual(Object.keys(update.payload).sort(), ['body', 'closesAt', 'opensAt', 'title'])
  assert.equal(update.payload.bindingId, undefined)
  assert.equal(update.payload.title, '修改后标题')
  assert.equal(update.payload.body, '修改后正文')
  assert.equal(update.payload.opensAt, new Date('2026-11-18T09:15').toISOString())
  assert.equal(update.payload.closesAt, null)

  await page.waitForFunction(() => document.querySelector('.ann-notice')?.textContent.includes('公告草稿已保存'), null, { timeout: 10000 })
  assert.ok(teacherListRequests(requests).length > listBefore, '成功后应重拉当前列表')
  await context.close()
})

await run('教师编辑未修改时间时保留原时间精度', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementList: bindingId => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [teacherAnnouncementRow('80001', bindingId, 'DRAFT', '列表标题')]
    }),
    teacherAnnouncementDetail: id => teacherAnnouncementDetailBody(id, {
      status: 'DRAFT',
      visibilityState: 'DRAFT',
      publishedByUserId: null,
      opensAt: '2026-11-17T02:00:45.123Z',
      closesAt: '2026-11-17T03:00:50.456Z'
    })
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  await page.locator('.teacher-announcements .ann-edit[data-id="80001"]').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  await page.locator('.ann-save').click()
  await page.waitForSelector('.ann-form-dialog', { state: 'detached', timeout: 10000 })

  const updates = teacherUpdateRequests(requests)
  assert.equal(updates.length, 1)
  assert.equal(updates[0].payload.opensAt, '2026-11-17T02:00:45.123Z')
  assert.equal(updates[0].payload.closesAt, '2026-11-17T03:00:50.456Z')
  await context.close()
})

await run('教师编辑详情已非草稿时关闭表单并刷新列表', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementList: () => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [teacherAnnouncementRow('80001', '9001', 'DRAFT', '列表标题')]
    }),
    teacherAnnouncementDetail: id => teacherAnnouncementDetailBody(id, {
      status: 'PUBLISHED',
      visibilityState: 'OPEN',
      publishedByUserId: '9002'
    })
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  const listBefore = teacherListRequests(requests).length

  await page.locator('.teacher-announcements .ann-edit[data-id="80001"]').click()
  await page.waitForFunction(() => document.querySelector('.ann-notice')?.textContent.includes('不能编辑'), null, { timeout: 10000 })
  assert.equal(await page.locator('.ann-form-dialog').count(), 0)
  await page.waitForTimeout(150)
  assert.ok(teacherListRequests(requests).length > listBefore, '非草稿详情应刷新列表')
  await context.close()
})

await run('教师编辑保存 409 保留状态冲突提示并刷新列表', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementList: () => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [teacherAnnouncementRow('80001', '9001', 'DRAFT', '列表标题')]
    }),
    teacherAnnouncementDetail: id => teacherAnnouncementDetailBody(id, {
      status: 'DRAFT',
      visibilityState: 'DRAFT',
      publishedByUserId: null
    }),
    teacherAnnouncementUpdate: { code: 409, msg: '只有草稿可以编辑' }
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  const listBefore = teacherListRequests(requests).length

  await page.locator('.teacher-announcements .ann-edit[data-id="80001"]').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  await page.locator('.ann-save').click()
  await page.waitForFunction(() => document.querySelector('.ann-form-error')?.textContent.includes('公告状态已变化'), null, { timeout: 10000 })
  assert.match(await page.locator('.ann-form-error').innerText(), /只有草稿可以编辑/)
  assert.equal(await page.locator('.ann-form-dialog').count(), 1, '冲突提示应保留表单输入')
  await page.waitForTimeout(150)
  assert.ok(teacherListRequests(requests).length > listBefore, '409 后应刷新列表')
  await context.close()
})

await run('教师编辑保存 403/503 保留权限与授权提示', async () => {
  const cases = [
    {
      label: '403',
      response: { code: 403, msg: '非创建者且无公告管理权限' },
      expected: /非创建者且无公告管理权限/
    },
    {
      label: '503',
      response: { code: 503, msg: '教师教学班授权服务尚未接入' },
      expected: /教学班授权服务暂不可用/
    }
  ]
  for (const item of cases) {
    const context = await newContext(browser, 'teacher')
    const page = await context.newPage()
    await installFixture(page, {
      role: 'teacher',
      permissions: TEACHER_WRITE_PERMISSIONS,
      teacherAnnouncementList: () => ({
        code: 200,
        msg: '查询成功',
        total: 1,
        rows: [teacherAnnouncementRow('80001', '9001', 'DRAFT', '列表标题')]
      }),
      teacherAnnouncementDetail: id => teacherAnnouncementDetailBody(id, {
        status: 'DRAFT',
        visibilityState: 'DRAFT',
        publishedByUserId: null
      }),
      teacherAnnouncementUpdate: item.response
    })
    await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
    await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
    await page.locator('.teacher-announcements .ann-edit[data-id="80001"]').click()
    await page.waitForSelector('#ann-form-title', { timeout: 10000 })
    await page.locator('.ann-save').click()
    await page.waitForFunction(() => document.querySelector('.ann-form-error')?.textContent.length > 0, null, { timeout: 10000 })
    assert.match(await page.locator('.ann-form-error').innerText(), item.expected, item.label)
    assert.equal(await page.locator('.ann-form-dialog').count(), 1, item.label)
    await context.close()
  }
})

await run('教师切班后迟到的编辑详情不重开旧表单也不覆盖新列表', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementList: bindingId => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [teacherAnnouncementRow(TEACHER_ANNOUNCEMENT_ID, bindingId, 'DRAFT', `教师公告-${bindingId}`)]
    }),
    teacherAnnouncementDetail: id => teacherAnnouncementDetailBody(id, {
      status: 'DRAFT',
      visibilityState: 'DRAFT',
      publishedByUserId: null
    }),
    teacherAnnouncementDetailDelays: { [TEACHER_ANNOUNCEMENT_ID]: 1000 }
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  await page.locator('.teacher-announcements .ann-edit').first().click()
  await page.waitForSelector('.ann-form-dialog', { timeout: 10000 })

  await page.evaluate(() => {
    location.hash = '#/teacher/announcements?courseSpaceId=101&classId=202&bindingId=9002'
  })
  await page.waitForFunction(() => document.querySelector('.teacher-announcements .ann-main')?.innerText.includes('教师公告-9002'), null, { timeout: 15000 })
  assert.equal(await page.locator('.ann-form-dialog').count(), 0, '切班应立即关闭旧表单')
  await page.waitForTimeout(1200)
  assert.equal(await page.locator('.ann-form-dialog').count(), 0, '迟到详情不得重开旧表单')
  const body = await page.locator('.teacher-announcements').innerText()
  assert.match(body, /教师公告-9002/)
  assert.doesNotMatch(body, /教师公告-9001|教师公告正文-80001/)
  await context.close()
})

await run('教师关闭编辑表单后迟到详情不重新打开', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementList: () => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [teacherAnnouncementRow(TEACHER_ANNOUNCEMENT_ID, '9001', 'DRAFT', '列表标题')]
    }),
    teacherAnnouncementDetail: id => teacherAnnouncementDetailBody(id, {
      status: 'DRAFT',
      visibilityState: 'DRAFT',
      publishedByUserId: null
    }),
    teacherAnnouncementDetailDelays: { [TEACHER_ANNOUNCEMENT_ID]: 900 }
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  await page.locator('.teacher-announcements .ann-edit').first().click()
  await page.waitForSelector('.ann-form-dialog', { timeout: 10000 })
  await page.locator('.ann-form-dialog .ann-dialog-head .ann-close').click()
  await page.waitForSelector('.ann-form-dialog', { state: 'detached', timeout: 10000 })
  await page.waitForTimeout(1100)
  assert.equal(await page.locator('.ann-form-dialog').count(), 0)
  await context.close()
})

await run('教师新建提交中切班，旧响应不覆盖新班级列表', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  const requests = await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementCreateDelay: 1000,
    teacherAnnouncementList: bindingId => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [teacherAnnouncementRow('80001', bindingId, 'DRAFT', `列表-${bindingId}`)]
    })
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  await page.locator('.teacher-announcements .ann-create').click()
  await page.waitForSelector('#ann-form-title', { timeout: 10000 })
  await page.locator('#ann-form-title').fill('切班前草稿')
  await page.locator('#ann-form-body').fill('切班后旧响应不得生效。')

  const createRequest = page.waitForRequest(request => request.method() === 'POST'
    && new URL(request.url()).pathname.endsWith('/education/teacher/announcements'), { timeout: 10000 })
  await page.locator('.ann-save').click()
  await createRequest
  await page.evaluate(() => {
    location.hash = '#/teacher/announcements?courseSpaceId=101&classId=202&bindingId=9002'
  })
  await page.waitForFunction(() => document.querySelector('.teacher-announcements .ann-main')?.innerText.includes('列表-9002'), null, { timeout: 15000 })
  await page.waitForTimeout(1200)
  assert.equal(await page.locator('.ann-form-dialog').count(), 0)
  const body = await page.locator('.teacher-announcements').innerText()
  assert.match(body, /列表-9002/)
  assert.doesNotMatch(body, /列表-9001/)
  assert.doesNotMatch(body, /公告草稿已创建/)
  assert.equal(teacherCreateRequests(requests).length, 1, '旧创建响应不得触发重发')
  await context.close()
})

await run('教师离开公告页后迟到编辑详情不重开', async () => {
  const context = await newContext(browser, 'teacher')
  const page = await context.newPage()
  await installFixture(page, {
    role: 'teacher',
    permissions: TEACHER_WRITE_PERMISSIONS,
    teacherAnnouncementList: () => ({
      code: 200,
      msg: '查询成功',
      total: 1,
      rows: [teacherAnnouncementRow(TEACHER_ANNOUNCEMENT_ID, '9001', 'DRAFT', '列表标题')]
    }),
    teacherAnnouncementDetail: id => teacherAnnouncementDetailBody(id, {
      status: 'DRAFT',
      visibilityState: 'DRAFT',
      publishedByUserId: null
    }),
    teacherAnnouncementDetailDelays: { [TEACHER_ANNOUNCEMENT_ID]: 900 }
  })
  await openHash(page, '/teacher/announcements?courseSpaceId=101&classId=201&bindingId=9001')
  await page.waitForSelector('.teacher-announcements .ann-main', { timeout: 15000 })
  await page.locator('.teacher-announcements .ann-edit').first().click()
  await page.waitForSelector('.ann-form-dialog', { timeout: 10000 })
  await page.evaluate(() => { location.hash = '#/teacher' })
  await page.waitForFunction(() => !location.hash.includes('/teacher/announcements'), null, { timeout: 10000 })
  await page.waitForTimeout(1100)
  assert.equal(await page.locator('.ann-form-dialog').count(), 0)
  assert.equal(await page.locator('.teacher-announcements').count(), 0)
  await context.close()
})

await browser.close()

const failed = results.filter(item => !item.ok)
console.log(`\n${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length > 0) {
  for (const item of failed) {
    console.error(item.name, item.error)
  }
  process.exitCode = 1
}
