/**
 * 6-03 管理员管理总览 / 管理员待办隔离回归。
 *
 * 页面由 tests/admin-overview.html 直接挂载组件，不经过生产路由、管理员
 * 菜单或权限守卫。Playwright 路由夹具提供总览响应；不连接后端。
 *
 * 运行：
 *   npx vite --host 127.0.0.1 --port 4175
 *   ADMIN_OVERVIEW_BASE=http://127.0.0.1:4175/tests/admin-overview.html \
 *     node tests/admin-overview.spec.mjs
 */
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { chromium } from 'playwright'

const BASE = process.env.ADMIN_OVERVIEW_BASE || 'http://127.0.0.1:4175/tests/admin-overview.html'
const ORIGIN = new URL(BASE).origin
const SCREENSHOT_DIR = process.env.ADMIN_OVERVIEW_SCREENSHOT_DIR || ''
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

const ADMIN_CODES = [
  'admin.class.count',
  'admin.student.count',
  'admin.session.running',
  'admin.assignment.pending',
  'admin.grade.pending'
]
const SOURCE_BY_CODE = {
  'admin.class.count': 'course',
  'admin.student.count': 'course',
  'admin.session.running': 'classroom',
  'admin.assignment.pending': 'assignment',
  'admin.grade.pending': 'grade'
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

function item(code, value, status = 'OK', overrides = {}) {
  return {
    metricCode: code,
    value: status === 'OK' ? value : null,
    unit: 'COUNT',
    status,
    source: SOURCE_BY_CODE[code] || 'course',
    sampleCount: null,
    actionPath: null,
    ...overrides
  }
}

function adminItems(values = {}) {
  const defaults = {
    'admin.class.count': 12,
    'admin.student.count': 420,
    'admin.session.running': 2,
    'admin.assignment.pending': 37,
    'admin.grade.pending': 18
  }
  return ADMIN_CODES.map(code => item(code, values[code] === undefined ? defaults[code] : values[code]))
}

function adminResponse(items = adminItems()) {
  return {
    code: 200,
    msg: '操作成功',
    data: { asOf: AS_OF, window: WINDOW, items }
  }
}

function todoItem(overrides = {}) {
  return {
    metricCode: 'admin.todo.pending',
    value: null,
    unit: 'COUNT',
    status: 'NO_DATA',
    source: 'pending_product_confirmation',
    sampleCount: null,
    actionPath: null,
    ...overrides
  }
}

function todoResponse(itemOverrides = {}) {
  return {
    code: 200,
    msg: '操作成功',
    data: { asOf: AS_OF, window: WINDOW, items: [todoItem(itemOverrides)] }
  }
}

async function installFixture(page, options = {}) {
  const opts = {
    overviewResponse: adminResponse(),
    overviewDelay: 0,
    overviewAbort: null,
    todosResponse: todoResponse(),
    todosDelay: 0,
    todosAbort: null,
    ...options
  }
  const requests = []
  const register = async (kind, pattern, getResponse, getDelay, abort) => {
    await page.route(pattern, async route => {
      const request = route.request()
      const url = new URL(request.url())
      requests.push({
        kind,
        path: url.pathname,
        query: Object.fromEntries(url.searchParams.entries())
      })
      try {
        const delay = typeof getDelay === 'function' ? getDelay(url) : getDelay
        await sleep(delay)
        if (abort) return route.abort(abort)
        const spec = typeof getResponse === 'function' ? getResponse(url) : getResponse
        return json(route, spec)
      } catch {
        return undefined
      }
    })
  }
  await register('overview', '**/education/admin/overview*', opts.overviewResponse, opts.overviewDelay, opts.overviewAbort)
  await register('todos', '**/education/admin/todos*', opts.todosResponse, opts.todosDelay, opts.todosAbort)
  return requests
}

async function newContext(browser) {
  const context = await browser.newContext({
    viewport: { width: 1280, height: 900 },
    timezoneId: 'Asia/Shanghai'
  })
  await context.addCookies([{ name: 'Edu-Role', value: 'admin', url: ORIGIN }])
  return context
}

async function openView(page, view = 'overview') {
  await page.goto(`${BASE}?view=${view}`, { waitUntil: 'domcontentloaded' })
}

async function board(page) {
  await page.waitForSelector('.overview-board', { timeout: 15000 })
}

function card(page, code) {
  return page.locator(`.ov-card[data-metric="${code}"]`)
}

async function takeScreenshot(page, filename) {
  if (!SCREENSHOT_DIR) return
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true })
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, filename), fullPage: true })
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

await run('管理员管理总览显示 5 个指标且只发送 from/to', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openView(page, 'overview')
  await board(page)
  assert.equal(await page.locator('.ov-card').count(), 5)
  assert.equal(await page.locator('.ov-card[data-metric="admin.class.count"]').count(), 1)
  assert.match(await page.locator('.overview-board').innerText(), /待提交人次/)
  assert.match(await page.locator('.overview-board').innerText(), /未跨班去重/)
  assert.equal(requests.length, 1)
  assert.equal(requests[0].kind, 'overview')
  assert.deepEqual(requests[0].query, {})
  assert.equal(await page.locator('.overview-board a').count(), 0)
  assert.match(await page.locator('.ov-window').innerText(), /统计窗口/)
  await page.setViewportSize({ width: 1280, height: 1500 })
  await takeScreenshot(page, '6-03-admin-overview.png')
  await context.close()
})

await run('管理员待办合法 NO_DATA 显示待办口径待确认', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openView(page, 'todos')
  await board(page)
  assert.equal(await page.locator('.ov-card').count(), 1)
  const text = await page.locator('.overview-board').innerText()
  assert.match(text, /待办口径待确认/)
  assert.doesNotMatch(text, /0 条|全部完成/)
  assert.match(text, /产品待确认/)
  assert.equal(requests.length, 1)
  assert.equal(requests[0].kind, 'todos')
  assert.deepEqual(requests[0].query, {})
  await takeScreenshot(page, '6-03-admin-todos.png')
  await context.close()
})

await run('两个管理员端点自定义 from/to 转 UTC，不带组织/身份参数', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openView(page, 'overview')
  await board(page)
  await page.locator('#overview-from').fill('2026-11-19T09:00')
  await page.locator('#overview-to').fill('2026-11-19T11:00')
  await page.getByRole('button', { name: '应用筛选' }).click()
  await page.waitForTimeout(250)
  const overview = requests.filter(request => request.kind === 'overview').at(-1)
  assert.deepEqual(Object.keys(overview.query).sort(), ['from', 'to'])
  assert.equal(overview.query.from, '2026-11-19T01:00:00.000Z')
  assert.equal(overview.query.to, '2026-11-19T03:00:00.000Z')
  for (const key of ['organizationId', 'deptId', 'userId', 'classId', 'bindingId']) {
    assert.equal(Object.hasOwn(overview.query, key), false, key)
  }
  await context.close()

  const todosContext = await newContext(browser)
  const todosPage = await todosContext.newPage()
  const todoRequests = await installFixture(todosPage)
  await openView(todosPage, 'todos')
  await board(todosPage)
  await todosPage.locator('#overview-from').fill('2026-11-19T09:00')
  await todosPage.locator('#overview-to').fill('2026-11-19T11:00')
  await todosPage.getByRole('button', { name: '应用筛选' }).click()
  await todosPage.waitForTimeout(250)
  const todos = todoRequests.filter(request => request.kind === 'todos').at(-1)
  assert.deepEqual(Object.keys(todos.query).sort(), ['from', 'to'])
  assert.equal(todos.query.from, '2026-11-19T01:00:00.000Z')
  assert.equal(todos.query.to, '2026-11-19T03:00:00.000Z')
  for (const key of ['organizationId', 'deptId', 'userId', 'classId', 'bindingId']) {
    assert.equal(Object.hasOwn(todos.query, key), false, key)
  }
  await todosContext.close()
})

await run('管理员总览真实零、混合三态和单卡违约', async () => {
  const mixed = [
    item('admin.class.count', 0),
    item('admin.student.count', null, 'NO_DATA'),
    item('admin.session.running', null, 'UNAVAILABLE'),
    item('admin.assignment.pending', 4),
    item('admin.grade.pending', 0)
  ]
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, { overviewResponse: adminResponse(mixed) })
  await openView(page, 'overview')
  await board(page)
  assert.equal(await card(page, 'admin.class.count').locator('.ov-value strong').innerText(), '0')
  assert.match(await card(page, 'admin.student.count').innerText(), /暂无数据/)
  assert.match(await card(page, 'admin.session.running').innerText(), /暂不可用/)
  assert.equal(await card(page, 'admin.assignment.pending').locator('.ov-value strong').innerText(), '4')
  assert.equal(await card(page, 'admin.grade.pending').locator('.ov-value strong').innerText(), '0')
  await context.close()

  const violated = adminItems()
  violated[0].unit = 'PERCENT'
  const violatedContext = await newContext(browser)
  const violatedPage = await violatedContext.newPage()
  await installFixture(violatedPage, { overviewResponse: adminResponse(violated) })
  await openView(violatedPage, 'overview')
  await board(violatedPage)
  assert.equal(await violatedPage.locator('.ov-card').count(), 5)
  assert.equal(await violatedPage.locator('.ov-card .ov-state-text.error').count(), 1)
  assert.match(await card(violatedPage, 'admin.class.count').innerText(), /数据异常/)
  assert.match(await card(violatedPage, 'admin.student.count').innerText(), /正常/)
  await violatedContext.close()
})

await run('管理员待办非法来源、value、actionPath、状态和 sampleCount 不落入固定空态', async () => {
  const cases = [
    { label: 'metricCode', overrides: { metricCode: 'admin.todo.other' } },
    { label: 'status OK', overrides: { status: 'OK', value: 0 } },
    { label: 'unit', overrides: { unit: 'PERCENT' } },
    { label: 'source', overrides: { source: 'course' } },
    { label: 'value', overrides: { value: 0 } },
    { label: 'actionPath', overrides: { actionPath: '/admin/todos' } },
    { label: 'sampleCount', overrides: { sampleCount: 0 } }
  ]
  for (const item of cases) {
    const context = await newContext(browser)
    const page = await context.newPage()
    await installFixture(page, { todosResponse: todoResponse(item.overrides) })
    await openView(page, 'todos')
    await board(page)
    const text = await page.locator('.overview-board').innerText()
    assert.match(await card(page, 'admin.todo.pending').innerText(), /数据异常/, item.label)
    assert.doesNotMatch(text, /待办口径待确认/, item.label)
    assert.doesNotMatch(text, /0 条|全部完成/, item.label)
    await context.close()
  }
})

await run('管理员待办顶层异常、401、403、503 和网络失败不落入固定空态', async () => {
  const cases = [
    { label: '顶层异常', response: { code: 200, msg: '操作成功', data: null } },
    { label: '401', response: { code: 401, msg: '登录已过期' } },
    { label: '403', response: { code: 403, msg: '无权查看管理员待办' } },
    { label: '503', response: { code: 503, msg: '服务暂不可用' } }
  ]
  for (const item of cases) {
    const context = await newContext(browser)
    const page = await context.newPage()
    await installFixture(page, { todosResponse: item.response })
    await openView(page, 'todos')
    await page.waitForTimeout(400)
    assert.equal(await page.locator('.ov-card').count(), 0, item.label)
    assert.doesNotMatch(await page.locator('body').innerText(), /待办口径待确认/, item.label)
    await context.close()
  }

  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, { todosAbort: 'failed' })
  await openView(page, 'todos')
  await page.waitForSelector('.ov-state.error', { timeout: 15000 })
  assert.doesNotMatch(await page.locator('body').innerText(), /待办口径待确认/)
  await context.close()
})

await run('管理员总览刷新失败不保留旧值和旧 asOf', async () => {
  let failing = false
  const context = await newContext(browser)
  const page = await context.newPage()
  await installFixture(page, {
    overviewResponse: () => failing
      ? { code: 500, msg: '管理员总览失败' }
      : adminResponse(adminItems({ 'admin.class.count': 7 }))
  })
  await openView(page, 'overview')
  await board(page)
  assert.equal(await card(page, 'admin.class.count').locator('.ov-value strong').innerText(), '7')
  failing = true
  await page.locator('.ov-head .ov-btn').click()
  await page.waitForSelector('.ov-state.error', { timeout: 15000 })
  assert.equal(await page.locator('.ov-card').count(), 0)
  assert.equal((await page.locator('.overview-board').innerText()).includes('响应时间'), false)
  await context.close()
})

await run('管理员总览筛选乱序和卸载后迟到响应不写页面', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const firstWindowStart = '2026-11-19T01:00:00.000Z'
  const secondWindowStart = '2026-11-19T02:00:00.000Z'
  const requests = await installFixture(page, {
    overviewDelay: url => url.searchParams.get('from') === firstWindowStart ? 500 : 50,
    overviewResponse: url => {
      const from = url.searchParams.get('from')
      return adminResponse(adminItems({
        'admin.class.count': from === firstWindowStart ? 11 : from === secondWindowStart ? 22 : 0
      }))
    }
  })
  await openView(page, 'overview')
  await board(page)
  await page.locator('#overview-from').fill('2026-11-19T09:00')
  await page.locator('#overview-to').fill('2026-11-19T11:00')
  const first = page.waitForRequest(request =>
    new URL(request.url()).searchParams.get('from') === firstWindowStart, { timeout: 10000 })
  await page.getByRole('button', { name: '应用筛选' }).click()
  await first
  await page.locator('#overview-from').fill('2026-11-19T10:00')
  await page.getByRole('button', { name: '应用筛选' }).click()
  await page.waitForFunction(() => document.querySelector('[data-metric="admin.class.count"] .ov-value strong')?.textContent === '22', null, { timeout: 15000 })
  await page.waitForTimeout(650)
  assert.equal(await card(page, 'admin.class.count').locator('.ov-value strong').innerText(), '22')
  assert.equal(requests.filter(request => request.kind === 'overview').length, 3)
  await context.close()

  const lateContext = await newContext(browser)
  const latePage = await lateContext.newPage()
  const pageErrors = []
  latePage.on('pageerror', error => pageErrors.push(error))
  await installFixture(latePage, { overviewDelay: 700 })
  await openView(latePage, 'overview')
  await latePage.waitForTimeout(100)
  await latePage.evaluate(() => window.__adminTestApp.unmount())
  await latePage.waitForTimeout(900)
  assert.equal(await latePage.locator('.overview-board').count(), 0)
  assert.deepEqual(pageErrors, [])
  await lateContext.close()
})

await run('未知 endpoint 报错并停止请求', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  const requests = await installFixture(page)
  await openView(page, 'unknown')
  await page.waitForSelector('.ov-state.error', { timeout: 10000 })
  assert.match(await page.locator('.ov-state.error').innerText(), /未知总览端点/)
  assert.equal(requests.length, 0)
  await context.close()
})

await run('管理员总览 390px 单列无横向溢出', async () => {
  const context = await newContext(browser)
  const page = await context.newPage()
  await page.setViewportSize({ width: 390, height: 844 })
  await installFixture(page)
  await openView(page, 'overview')
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
  await takeScreenshot(page, '6-03-admin-overview-390.png')
  await context.close()
})

await browser.close()

const failed = results.filter(item => !item.ok)
console.log(`\n${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length > 0) {
  for (const item of failed) console.error(item.name, item.error)
  process.exitCode = 1
}
