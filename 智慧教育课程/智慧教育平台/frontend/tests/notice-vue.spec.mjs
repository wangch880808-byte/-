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

const BASE = process.env.NOTICE_TEST_BASE || 'http://127.0.0.1:4173'
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

function contextOptions() {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      term: { termId: '1', termName: '2026-2027-1' },
      courses: [{
        courseSpaceId: '101',
        title: '课程一',
        classes: [
          { classId: '201', className: '一班', termId: '1', bindingId: '9001', memberRole: 'student' },
          { classId: '202', className: '二班', termId: '1', bindingId: '9002', memberRole: 'student' }
        ]
      }]
    }
  }
}

function contextCheck(url) {
  const bindingId = url.searchParams.get('bindingId')
  const classId = url.searchParams.get('classId')
  return {
    code: 200,
    msg: '操作成功',
    data: {
      courseSpaceId: '101',
      classId,
      termId: '1',
      bindingId,
      memberRole: 'student',
      canPublish: false,
      canGrade: false
    }
  }
}

function routers(includeNoticeRoute) {
  const notices = includeNoticeRoute
    ? [{ path: 'notices', component: 'education/student/StudentNotices' }]
    : []
  return {
    code: 200,
    msg: '操作成功',
    data: [{
      path: 'education',
      component: 'Layout',
      children: [...notices, { path: 'course', component: 'education/CourseListPage' }]
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
const READ_AT = '2026-11-17T03:00:00.000Z'

function noticeRow(id, title, bindingId = '9001') {
  return {
    id,
    kind: 'ANNOUNCEMENT',
    bindingId,
    sourceId: 'announcement-1',
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
    delayByBinding: {},
    ...options
  }
  const requests = []
  const readState = new Set()
  let allRead = false

  await page.route('**/getInfo', route => json(route, info(opts.role, opts.permissions)))
  await page.route('**/getRouters', route => json(route, routers(opts.includeNoticeRoute)))
  await page.route('**/education/context/options*', route => json(route, opts.contextOptions || contextOptions()))
  await page.route('**/education/context/check*', route => json(route, contextCheck(new URL(route.request().url()))))
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

await browser.close()

const failed = results.filter(item => !item.ok)
console.log(`\n${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length > 0) {
  for (const item of failed) {
    console.error(item.name, item.error)
  }
  process.exitCode = 1
}
