/**
 * 6-03 阶段2回归测试（角色 HTML + 隔离网络夹具）。
 *
 * 用法：
 *   PLAYWRIGHT_MODULE=<playwright 模块路径或包名> \
 *   OVERVIEW_TEST_BASE=http://127.0.0.1:8125 \
 *   node frontend/tests/overview-stage2.js
 *
 * 所有 /getInfo、/getRouters、/logout、/education/** 由 Playwright
 * route 夹具返回；不连接真实后端、不创建真实登录、不进入产品代码。
 */
'use strict';

const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const BASE = process.env.OVERVIEW_TEST_BASE || 'http://127.0.0.1:8125';
const CHANNEL = process.env.PLAYWRIGHT_CHANNEL || 'msedge';
const failures = [];
const passed = [];

function check(condition, message) {
  if (condition) {
    passed.push(message);
  } else {
    failures.push(message);
  }
}

/**
 * 判断一条请求记录是否为指定教学班的学生通知列表请求。
 *
 * 必须与未读数请求区分：/education/student/notices/unread-count 也以列表路径为前缀，
 * 只看前缀会把未读数误判为列表请求。这里解析 URL 后精确比较 pathname，并精确比较
 * bindingId，避免缺少参数或多一位数字的 bindingId 被当成同一教学班。
 *
 * @param {string} call 按 pathname+search 记录的请求路径
 * @param {string} bindingId 期望的课程—教学班 ID
 * @returns {boolean} pathname 与 bindingId 均精确匹配返回 true；URL 解析失败或任一条件不满足返回 false
 */
function isInheritedNoticeListRequest(call, bindingId) {
  let url;
  try {
    url = new URL(call, 'http://stage2.local');
  } catch (e) {
    return false;
  }
  return url.pathname === '/education/student/notices'
    && url.searchParams.get('bindingId') === bindingId;
}

/**
 * 在点击「查看通知」后新增的请求里查找学生通知列表请求。
 *
 * 只从 startIndex 开始检查，避免把点击前总览页或旧页面已经发出的请求算作本次导航结果。
 * 该函数是纯判断，异步等待由调用方负责。
 *
 * @param {string[]} calls 按发生顺序记录的 /education/** 请求
 * @param {number} startIndex 点击「查看通知」前的请求数量
 * @param {string} bindingId 期望的课程—教学班 ID
 * @returns {boolean} startIndex 之后存在精确匹配的通知列表请求返回 true；否则返回 false
 */
function hasInheritedNoticeListRequest(calls, startIndex, bindingId) {
  for (let i = startIndex; i < calls.length; i += 1) {
    if (isInheritedNoticeListRequest(calls[i], bindingId)) {
      return true;
    }
  }
  return false;
}

/**
 * 有上限地等待条件成立，避免固定延时掩盖异步时序。
 *
 * @param {Function} predicate 返回 boolean 的条件函数
 * @param {number} [timeoutMs] 超时毫秒数，默认 5000
 * @returns {Promise<boolean>} 条件成立返回 true；超时返回 false
 */
async function waitForCondition(predicate, timeoutMs) {
  const deadline = Date.now() + (timeoutMs || 5000);
  while (Date.now() < deadline) {
    if (predicate()) {
      return true;
    }
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  return false;
}

// 匹配函数的负例自检；只在显式设置环境变量时运行，不启动浏览器。
if (process.env.STAGE2_MATCHER_SELFTEST === '1') {
  const binding = '910000000001';
  const listPath = '/education/student/notices?bindingId=' + binding + '&pageNum=1&pageSize=10';
  const cases = [
    { name: '正确列表请求', calls: [listPath], startIndex: 0, expected: true },
    { name: '仅有未读数请求', calls: ['/education/student/notices/unread-count?bindingId=' + binding], startIndex: 0, expected: false },
    { name: '列表缺少 bindingId', calls: ['/education/student/notices?pageNum=1&pageSize=10'], startIndex: 0, expected: false },
    { name: '列表 bindingId 多一位', calls: ['/education/student/notices?bindingId=' + binding + '0&pageNum=1'], startIndex: 0, expected: false },
    { name: '点击前旧列表不计入', calls: [listPath], startIndex: 1, expected: false },
    { name: '点击后新列表计入', calls: ['/education/student/announcements?bindingId=' + binding, listPath], startIndex: 1, expected: true }
  ];
  const results = cases.map((entry) => {
    const actual = hasInheritedNoticeListRequest(entry.calls, entry.startIndex, binding);
    return {
      name: entry.name,
      expected: entry.expected,
      actual: actual,
      passed: actual === entry.expected
    };
  });
  const failed = results.filter((entry) => !entry.passed);
  console.log(JSON.stringify({
    matcherSelfTest: results,
    passed: results.length - failed.length,
    failures: failed.map((entry) => entry.name)
  }, null, 2));
  process.exit(failed.length ? 1 : 0);
}

function item(code, value, source, unit, sampleCount) {
  return {
    metricCode: code,
    value: value,
    unit: unit || 'COUNT',
    status: value === null ? 'NO_DATA' : 'OK',
    source: source,
    sampleCount: sampleCount === undefined ? 3 : sampleCount,
    actionPath: '/candidate'
  };
}

function response(items, extra) {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      asOf: '2026-11-05T12:00:00.000Z',
      window: {
        startsAt: '2026-11-05T00:00:00.000Z',
        endsAt: '2026-11-06T00:00:00.000Z'
      },
      items: items
    },
    ...(extra || {})
  };
}

function teacherItems() {
  return [
    item('teacher.class.count', 3, 'course'),
    item('teacher.student.count', 86, 'course'),
    item('teacher.session.running', 1, 'classroom'),
    item('teacher.assignment.pending', 12, 'assignment'),
    item('teacher.grade.pending', 5, 'grade'),
    item('teacher.notice.unread', 4, 'notice')
  ];
}

function studentItems() {
  return [
    item('student.class.count', 2, 'course'),
    item('student.session.active', 1, 'classroom'),
    item('student.assignment.due', 3, 'assignment'),
    item('student.notice.unread', 2, 'notice')
  ];
}

function adminItems() {
  return [
    item('admin.class.count', 12, 'course'),
    item('admin.student.count', 420, 'course'),
    item('admin.session.running', 2, 'classroom'),
    item('admin.assignment.pending', 37, 'assignment'),
    item('admin.grade.pending', 18, 'grade')
  ];
}

function adminTodoItems() {
  return [{
    metricCode: 'admin.todo.pending',
    value: null,
    unit: 'COUNT',
    status: 'NO_DATA',
    source: 'pending_product_confirmation',
    sampleCount: null,
    actionPath: null
  }];
}

function overviewBody(path) {
  if (path === '/education/teacher/overview') {
    return response(teacherItems());
  }
  if (path === '/education/student/today-todos') {
    return response(studentItems());
  }
  if (path === '/education/admin/overview') {
    return response(adminItems());
  }
  if (path === '/education/admin/todos') {
    return response(adminTodoItems());
  }
  return null;
}

async function makePage(browser, role, options) {
  const opts = options || {};
  const context = await browser.newContext({
    viewport: opts.viewport || { width: 1440, height: 1000 },
    timezoneId: 'Asia/Shanghai'
  });
  await context.addInitScript((pageRole) => {
    if (!location.pathname.includes('login.html')) {
      sessionStorage.setItem('zhijiao.login.token', 'test-token');
      sessionStorage.setItem('zhijiao.login.role', pageRole);
    }
  }, role);

  const page = await context.newPage();
  const state = {
    infoCalls: 0,
    overviewCalls: 0,
    educationCalls: [],
    infoFail: opts.infoFail === true,
    infoStatus: opts.infoStatus || 0,
    roles: opts.roles || [role],
    overviewMode: 'normal',
    infoGate: null,
    overviewGate: null,
    errors: []
  };
  page.on('pageerror', (err) => state.errors.push('pageerror: ' + err.message));
  page.on('console', (msg) => { if (msg.type() === 'error') state.errors.push('console: ' + msg.text()); });

  await page.route('**/getInfo', async (route) => {
    state.infoCalls += 1;
    if (state.infoGate) {
      await state.infoGate;
    }
    if (state.infoStatus === 401) {
      await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 401, msg: '登录状态已失效' }) });
      return;
    }
    if (state.infoFail) {
      await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 500, msg: '模拟核验失败' }) });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        msg: '操作成功',
        user: { nickName: '测试用户', userName: 'test' },
        roles: state.roles
      })
    });
  });

  await page.route('**/getRouters', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: '操作成功', data: [] }) });
  });

  await page.route('**/logout', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: '退出成功' }) });
  });

  await page.route('**/education/**', async (route) => {
    const url = new URL(route.request().url());
    // 静态脚本/样式也位于 /src/views/education/**；只拦截后端 /education/ API。
    if (url.pathname.startsWith('/src/')) {
      await route.continue();
      return;
    }
    state.educationCalls.push(url.pathname + url.search);
    if (/^\/education\/(teacher\/overview|student\/today-todos|admin\/overview|admin\/todos)$/.test(url.pathname)) {
      state.overviewCalls += 1;
      if (state.overviewGate) {
        await state.overviewGate;
      }
      if (state.overviewMode === '403') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 403, msg: '当前账号无权查看该总览范围' }) });
        return;
      }
      if (state.overviewMode === '401') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 401, msg: '登录状态已失效' }) });
        return;
      }
      const body = overviewBody(url.pathname);
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
      return;
    }
    if (url.pathname === '/education/student/notices/unread-count') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data: { unreadCount: 2, asOf: '2026-11-05T12:00:00.000Z' } }) });
      return;
    }
    if (url.pathname === '/education/student/notices' || url.pathname === '/education/student/announcements'
      || url.pathname === '/education/teacher/announcements') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: '查询成功', total: 0, rows: [] }) });
      return;
    }
    if (/^\/education\/student\/notices\/[^/]+$/.test(url.pathname)) {
      await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 404, msg: '通知不存在或不可访问' }) });
      return;
    }
    if (/^\/education\/teacher\/announcements\/[^/]+$/.test(url.pathname)) {
      await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 404, msg: '公告不存在或不可访问' }) });
      return;
    }
    await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 404, msg: 'Mock 未覆盖：' + url.pathname }) });
  });

  return { context, page, state };
}

async function waitOverview(page, count) {
  await page.waitForFunction((n) => {
    return document.querySelectorAll('#viewRoot .ov-card').length === n
      && !document.querySelector('#viewRoot .ov-skeleton');
  }, count, { timeout: 5000 });
}

async function openOverview(page, role, query) {
  await page.goto(BASE + '/' + role + '.html?' + (query || 'g=0&i=0'), { waitUntil: 'load' });
  await waitOverview(page, role === 'teacher' ? 6 : role === 'student' ? 4 : 5);
}

async function close(entry) {
  await entry.context.close();
}

async function screenshot(page, name) {
  if (!process.env.OVERVIEW_SCREENSHOT_DIR) {
    return;
  }
  await page.screenshot({ path: process.env.OVERVIEW_SCREENSHOT_DIR + '/' + name, fullPage: true });
}

(async () => {
  const browser = await chromium.launch({ channel: CHANNEL, headless: true });

  // 1. 四页面实际挂载。
  const teacher = await makePage(browser, 'teacher');
  await openOverview(teacher.page, 'teacher');
  check((await teacher.page.locator('.ov-card-title').allTextContents()).includes('授权班级范围'), '教师总览未挂载');
  check(await teacher.page.locator('#viewRoot .ov-card').count() === 6, '教师应有 6 卡');
  check((await teacher.page.locator('.tab.active').innerText()).includes('教学总览'), '教师页签应保持总览');
  const beforeRefresh = teacher.state.overviewCalls;
  await teacher.page.click('[data-action="refresh"]');
  await teacher.page.waitForFunction(() => document.querySelector('#viewRoot .ov-skeleton') === null && document.querySelectorAll('#viewRoot .ov-card').length === 6, null, { timeout: 5000 });
  check(teacher.state.overviewCalls > beforeRefresh, '教师总览刷新应重新请求');
  await teacher.page.fill('#ovFrom', '2026-11-04T08:00');
  await teacher.page.fill('#ovTo', '2026-11-05T08:00');
  await teacher.page.click('[data-action="apply-time"]');
  await teacher.page.waitForFunction(() => document.querySelector('#viewRoot .ov-skeleton') === null && document.querySelectorAll('#viewRoot .ov-card').length === 6, null, { timeout: 5000 });
  check(teacher.state.educationCalls[teacher.state.educationCalls.length - 1].includes('from=2026-11-04T00%3A00%3A00.000Z'), '教师筛选应发送最新时间窗');
  await screenshot(teacher.page, 'stage2-teacher.png');
  await close(teacher);

  const student = await makePage(browser, 'student');
  await openOverview(student.page, 'student');
  check(await student.page.locator('#viewRoot .ov-card').count() === 4, '学生应有 4 卡');
  const studentCardText = await student.page.locator('#viewRoot').innerText();
  check(studentCardText.includes('本人全部未读；通知页按当前班级上下文显示'), '学生通知卡应说明范围差异');
  check(await student.page.locator('a.ov-link', { hasText: '查看通知' }).count() === 1, '学生通知入口应存在');
  await student.page.waitForFunction(() => {
    const badge = document.getElementById('noticeBadge');
    return badge && !badge.hidden && badge.textContent === '2';
  }, null, { timeout: 5000 });
  check((await student.page.locator('#noticeBadge').innerText()) === '2', '学生顶栏未读角标应保留');
  await screenshot(student.page, 'stage2-student.png');
  await close(student);

  const admin = await makePage(browser, 'admin');
  await openOverview(admin.page, 'admin');
  check(await admin.page.locator('#viewRoot .ov-card').count() === 5, '管理员总览应有 5 卡');
  await screenshot(admin.page, 'stage2-admin.png');
  await admin.page.goto(BASE + '/admin.html?g=0&i=1', { waitUntil: 'load' });
  await admin.page.waitForFunction(() => document.querySelectorAll('#viewRoot .ov-card').length === 1
    && !document.querySelector('#viewRoot .ov-skeleton'), null, { timeout: 5000 });
  check((await admin.page.locator('#viewRoot').innerText()).includes('待办来源尚未确定，当前暂无数据'), '管理员待办应显示合法 NO_DATA 文案');
  await screenshot(admin.page, 'stage2-admin-todos.png');
  await close(admin);

  // 2. 登录核验完成前零总览请求。
  let releaseInfo;
  const infoGate = new Promise((resolve) => { releaseInfo = resolve; });
  const gated = await makePage(browser, 'teacher');
  gated.state.infoGate = infoGate;
  await gated.page.goto(BASE + '/teacher.html?g=0&i=0', { waitUntil: 'load' });
  await gated.page.waitForTimeout(180);
  check(gated.state.overviewCalls === 0, '登录核验完成前不得请求总览');
  releaseInfo();
  await waitOverview(gated.page, 6);
  check(gated.state.overviewCalls > 0, '登录核验完成后应请求总览');
  await close(gated);

  // 3. 核验失败后重试。
  const retryEntry = await makePage(browser, 'teacher', { infoFail: true });
  await retryEntry.page.goto(BASE + '/teacher.html?g=0&i=0', { waitUntil: 'load' });
  await retryEntry.page.waitForSelector('#authRetry:not([hidden])', { timeout: 5000 });
  check(retryEntry.state.overviewCalls === 0, '核验失败时不得请求总览');
  retryEntry.state.infoFail = false;
  await retryEntry.page.click('#authRetry');
  await waitOverview(retryEntry.page, 6);
  check(retryEntry.state.overviewCalls > 0, '重新核验成功后才请求总览');
  await close(retryEntry);

  // 4. 401 / 错角色。
  const unauthorized = await makePage(browser, 'teacher', { infoStatus: 401 });
  await unauthorized.page.goto(BASE + '/teacher.html?g=0&i=0', { waitUntil: 'load' });
  await unauthorized.page.waitForURL(/login\.html/, { timeout: 5000 });
  check(unauthorized.page.url().includes('login.html'), '登录 401 应回登录页');
  check(unauthorized.state.overviewCalls === 0, '登录 401 不应请求总览');
  await close(unauthorized);

  const wrongRole = await makePage(browser, 'admin', { roles: ['student'] });
  await wrongRole.page.goto(BASE + '/teacher.html?g=0&i=0', { waitUntil: 'load' });
  await wrongRole.page.waitForURL(/\/(student|login)\.html/, { timeout: 5000 });
  check(!wrongRole.page.url().includes('/teacher.html'), '错角色不应停留在教师页');
  check(!wrongRole.state.educationCalls.some((p) => p.startsWith('/education/teacher/overview')), '错角色不得请求教师总览');
  await close(wrongRole);

  const noRole = await makePage(browser, 'admin', { roles: [] });
  await noRole.page.goto(BASE + '/teacher.html?g=0&i=0', { waitUntil: 'load' });
  await noRole.page.waitForURL(/login\.html/, { timeout: 5000 });
  check(noRole.page.url().includes('login.html'), '无匹配角色应回登录页');
  await close(noRole);

  // 5. 总览 403 与 401。
  const forbidden = await makePage(browser, 'teacher');
  forbidden.state.overviewMode = '403';
  await forbidden.page.goto(BASE + '/teacher.html?g=0&i=0', { waitUntil: 'load' });
  await forbidden.page.waitForSelector('#viewRoot .ov-page-state', { timeout: 5000 });
  check((await forbidden.page.locator('#viewRoot').innerText()).includes('无权限查看总览'), '总览 403 应显示无权限');
  await close(forbidden);

  const overview401 = await makePage(browser, 'teacher');
  overview401.state.overviewMode = '401';
  await overview401.page.goto(BASE + '/teacher.html?g=0&i=0', { waitUntil: 'load' });
  await overview401.page.waitForURL(/login\.html/, { timeout: 5000 });
  check(overview401.page.url().includes('login.html'), '总览 401 应回登录页');
  await close(overview401);

  // 6. 教师公告菜单回归 + 管理员非总览占位。
  const teacherRegression = await makePage(browser, 'teacher');
  await teacherRegression.page.goto(BASE + '/teacher.html?g=0&i=3', { waitUntil: 'load' });
  await teacherRegression.page.waitForSelector('#viewRoot .edu-page', { timeout: 5000 });
  check(await teacherRegression.page.locator('#viewRoot .ov-card').count() === 0, '教师公告菜单不应挂载总览');
  check(teacherRegression.state.errors.length === 0, '教师公告菜单脚本错误: ' + teacherRegression.state.errors.join(' | '));
  await close(teacherRegression);

  const adminNonOverview = await makePage(browser, 'admin');
  await adminNonOverview.page.goto(BASE + '/admin.html?g=0&i=2', { waitUntil: 'load' });
  await adminNonOverview.page.waitForSelector('.content .hint', { state: 'visible', timeout: 5000 });
  check(await adminNonOverview.page.locator('#viewRoot').isHidden(), '管理员非总览菜单不应显示 viewRoot');
  check((await adminNonOverview.page.locator('.content .hint').innerText()).includes('内容区'), '管理员非总览菜单应保留占位');
  await close(adminNonOverview);

  // 7. 菜单/视图切换时迟到响应不写入。
  let releaseOverview;
  const overviewGate = new Promise((resolve) => { releaseOverview = resolve; });
  const late = await makePage(browser, 'teacher');
  late.state.overviewGate = overviewGate;
  await late.page.goto(BASE + '/teacher.html?g=0&i=0', { waitUntil: 'load' });
  await late.page.waitForFunction(() => document.querySelector('#viewRoot .ov-skeleton') !== null, null, { timeout: 5000 });
  await late.page.evaluate(() => {
    window.EduViews.mount('teacher', 0, 3, '班级消息 / 公告');
  });
  releaseOverview();
  await late.page.waitForTimeout(250);
  check(await late.page.locator('#viewRoot .ov-card').count() === 0, '迟到总览响应不得写回公告视图');
  check(await late.page.locator('#viewRoot .edu-page').count() === 1, '切换后应挂载公告视图');
  await close(late);

  // 8. 学生通知入口与范围提示。
  const studentNotice = await makePage(browser, 'student');
  await openOverview(studentNotice.page, 'student');
  const noticeLink = studentNotice.page.locator('a.ov-link', { hasText: '查看通知' });
  // 目标通知页继承课程—教学班上下文；卡片仍显示本人全部未读，范围差异由文案说明。
  await studentNotice.page.evaluate(() => sessionStorage.setItem('zhijiao.bindingId', '910000000001'));
  const noticeCallStart = studentNotice.state.educationCalls.length;
  await noticeLink.click();
  await studentNotice.page.waitForURL(/student\.html\?g=0&i=3/, { timeout: 5000 });
  await studentNotice.page.waitForSelector('#viewRoot .edu-seg', { timeout: 5000 });
  check(await studentNotice.page.locator('#viewRoot .ov-card').count() === 0, '学生通知页不应保留总览卡片');
  check((await studentNotice.page.locator('#viewRoot').innerText()).includes('学习通知'), '学生通知页应挂载既有通知视图');
  // 只认点击「查看通知」后新增的请求；未读数请求和旧页面请求都不算列表继承。
  const inheritedBinding = await waitForCondition(
    () => hasInheritedNoticeListRequest(studentNotice.state.educationCalls, noticeCallStart, '910000000001')
  );
  check(inheritedBinding, '学生通知页应继承当前教学班上下文: '
    + JSON.stringify(studentNotice.state.educationCalls.slice(noticeCallStart)));
  await screenshot(studentNotice.page, 'stage2-student-notices.png');
  await studentNotice.page.click('[data-tab="announcements"]');
  await studentNotice.page.waitForTimeout(150);
  check(await studentNotice.page.locator('#viewRoot .edu-page').count() > 0, '学生课程公告页签应可挂载');
  check(studentNotice.state.errors.length === 0, '学生课程公告页签脚本错误: ' + studentNotice.state.errors.join(' | '));
  await screenshot(studentNotice.page, 'stage2-student-announcements.png');
  check(studentNotice.state.errors.length === 0, '学生通知页脚本错误: ' + studentNotice.state.errors.join(' | '));
  await close(studentNotice);

  // 9. 390px 实际视口与离开总览后的样式恢复。
  const mobile = await makePage(browser, 'student', { viewport: { width: 390, height: 844 } });
  await openOverview(mobile.page, 'student');
  const columns = await mobile.page.locator('#viewRoot .ov-grid').first().evaluate((el) => getComputedStyle(el).gridTemplateColumns.split(' ').length);
  check(columns === 1, '390px 总览应为单列');
  check(!(await mobile.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)), '390px 总览不应水平溢出');
  check(await mobile.page.evaluate(() => document.body.classList.contains('edu-overview-active')), '学生总览应挂载局部样式类');
  const mobileHeader = await mobile.page.evaluate(() => {
    const rect = (el) => {
      const r = el.getBoundingClientRect();
      return { x: r.x, y: r.y, width: r.width, height: r.height, right: r.right, bottom: r.bottom };
    };
    const overlap = (a, b) => !(a.right <= b.x || b.right <= a.x || a.bottom <= b.y || b.bottom <= a.y);
    const logo = document.querySelector('.logo');
    const right = document.querySelector('.header-right');
    const notice = document.querySelectorAll('.header-right .item')[1];
    const logout = document.getElementById('logoutBtn');
    const logoText = document.querySelector('.logo b');
    const logoutRect = rect(logout);
    const hit = document.elementFromPoint(logoutRect.x + logoutRect.width / 2, logoutRect.y + logoutRect.height / 2);
    return {
      logo: rect(logo),
      right: rect(right),
      notice: rect(notice),
      logout: logoutRect,
      logoutWhiteSpace: getComputedStyle(logout).whiteSpace,
      logoutHeight: logoutRect.height,
      logoWhiteSpace: getComputedStyle(logoText).whiteSpace,
      logoHeight: rect(logoText).height,
      logoLineHeight: parseFloat(getComputedStyle(logoText).lineHeight) || 20,
      hitIsLogout: hit === logout || logout.contains(hit),
      viewportWidth: window.innerWidth,
      overlapLogoRight: overlap(rect(logo), rect(right)),
      overlapNoticeLogout: overlap(rect(notice), logoutRect)
    };
  });
  check(mobileHeader.logout.height > 0 && mobileHeader.logout.right <= mobileHeader.viewportWidth + 1, '手机退出按钮应完整可见');
  check(mobileHeader.logoutWhiteSpace === 'nowrap' && mobileHeader.logoutHeight <= 40, '手机退出按钮应单行且高度合理');
  check(mobileHeader.hitIsLogout, '手机退出按钮不应被遮挡');
  check(!mobileHeader.overlapLogoRight, '手机品牌区与右侧控件不应重叠');
  check(!mobileHeader.overlapNoticeLogout, '手机通知与退出按钮不应重叠');
  check(mobileHeader.logoWhiteSpace === 'nowrap' && mobileHeader.logoHeight <= mobileHeader.logoLineHeight * 2 + 2, '手机品牌名最多两行');
  await screenshot(mobile.page, 'stage2-student-mobile.png');
  const mobileNoticeMenu = mobile.page.locator('.menu-item', { hasText: '学习通知 / 课程公告' });
  await mobileNoticeMenu.scrollIntoViewIfNeeded();
  await mobileNoticeMenu.click();
  await mobile.page.waitForURL(/student\.html\?g=0&i=3/, { timeout: 5000 });
  check(!(await mobile.page.evaluate(() => document.body.classList.contains('edu-overview-active'))), '离开总览后应移除局部样式类');
  check(!(await mobile.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)), '离开总览后原外壳不应水平溢出');
  const restoredHeaderHeight = await mobile.page.evaluate(() => document.querySelector('.app-header').getBoundingClientRect().height);
  check(restoredHeaderHeight <= 60, '离开总览后顶栏应恢复原高度');
  await screenshot(mobile.page, 'stage2-student-notices-390.png');
  await close(mobile);

  // 10. 退出登录仍可用。
  const logout = await makePage(browser, 'teacher');
  await openOverview(logout.page, 'teacher');
  await logout.page.click('#logoutBtn');
  await logout.page.waitForURL(/login\.html/, { timeout: 5000 });
  check(logout.page.url().includes('login.html'), '退出登录应回登录页');
  await close(logout);

  check(failures.length === 0, '阶段2断言失败');
  console.log(JSON.stringify({ passed: passed.length, failures }, null, 2));
  await browser.close();
  process.exit(failures.length ? 1 : 0);
})().catch((error) => {
  console.error(error);
  process.exit(2);
});
