/**
 * 6-02 离线 Mock 隔离回归。
 *
 * 用法：
 *   PLAYWRIGHT_MODULE=<playwright 模块路径或包名> \
 *   MOCK_TEST_BASE=http://127.0.0.1:8132 \
 *   node frontend/tests/mock-isolation.js
 *
 * 直接驱动真实 mock-preview.html、teacher.html、student.html；
 * /getInfo、/getRouters、/logout、/education/** 全部由 Playwright route 夹具返回。
 * 不连接真实后端、不校验真实登录、不发送真实业务请求。
 */
'use strict';

const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const BASE = process.env.MOCK_TEST_BASE || 'http://127.0.0.1:8132';
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

async function waitUntil(predicate, timeoutMs) {
  const deadline = Date.now() + (timeoutMs || 5000);
  while (Date.now() < deadline) {
    if (predicate()) {
      return true;
    }
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  return false;
}

async function waitForViewText(page, text) {
  try {
    await page.waitForFunction((expected) => document.querySelector('#viewRoot').innerText.includes(expected), text, { timeout: 5000 });
    return true;
  } catch (e) {
    return false;
  }
}

function teacherRow(title) {
  return {
    id: '920000000000000001',
    bindingId: '910000000002',
    title: title,
    status: 'DRAFT',
    visibilityState: 'DRAFT',
    opensAt: null,
    closesAt: null,
    createdByUserId: '102',
    publishedByUserId: null,
    createdAt: '2026-11-08T00:00:00.000Z'
  };
}

function teacherList(title) {
  return { code: 200, msg: '查询成功', total: 1, rows: [teacherRow(title)] };
}

function noticeList(title) {
  return {
    code: 200,
    msg: '查询成功',
    total: 1,
    rows: [{
      id: '930000000000000001',
      kind: 'ASSIGNMENT',
      bindingId: '910000000002',
      sourceId: '920000000000000001',
      title: title,
      visibleAt: '2026-11-08T00:00:00.000Z',
      readAt: null,
      isRead: false,
      createdAt: '2026-11-08T00:00:00.000Z'
    }]
  };
}

function adminOverviewBody() {
  return {
    code: 200,
    msg: '操作成功',
    data: {
      asOf: '2026-11-08T00:00:00.000Z',
      window: {
        startsAt: '2026-11-08T00:00:00.000Z',
        endsAt: '2026-11-09T00:00:00.000Z'
      },
      items: [
        { metricCode: 'admin.class.count', value: 12, unit: 'COUNT', status: 'OK', source: 'course', sampleCount: 12, actionPath: '/admin/classes' },
        { metricCode: 'admin.student.count', value: 420, unit: 'COUNT', status: 'OK', source: 'course', sampleCount: 12, actionPath: '/admin/classes' },
        { metricCode: 'admin.session.running', value: 2, unit: 'COUNT', status: 'OK', source: 'classroom', sampleCount: 12, actionPath: '/admin/classes' },
        { metricCode: 'admin.assignment.pending', value: 37, unit: 'COUNT', status: 'OK', source: 'assignment', sampleCount: 12, actionPath: '/admin/assignments' },
        { metricCode: 'admin.grade.pending', value: 18, unit: 'COUNT', status: 'OK', source: 'grade', sampleCount: 12, actionPath: '/admin/grades' }
      ]
    }
  };
}

async function makePage(browser, role, storage, options) {
  const opts = options || {};
  const context = await browser.newContext({
    viewport: { width: 1280, height: 900 },
    timezoneId: 'Asia/Shanghai'
  });
  // 只在首次落页写入标记，后续导航不重写，才能观察预览是否污染真实会话。
  await context.addInitScript(({ pageRole, seed }) => {
    if (sessionStorage.getItem('__mock_test_seeded') === '1') {
      return;
    }
    sessionStorage.setItem('zhijiao.login.token', seed.token);
    sessionStorage.setItem('zhijiao.login.role', pageRole);
    sessionStorage.setItem('zhijiao.bindingId', seed.binding);
    sessionStorage.setItem('zhijiao.mock', seed.mock);
    sessionStorage.setItem('__mock_test_seeded', '1');
  }, { pageRole: role, seed: storage });

  const page = await context.newPage();
  const state = {
    infoCalls: 0,
    infoGate: null,
    announcementsStatus: opts.announcementsStatus || 200,
    educationCalls: [],
    errors: []
  };
  page.on('pageerror', (err) => state.errors.push('pageerror: ' + err.message));
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      state.errors.push('console: ' + msg.text());
    }
  });

  await page.route('**/getInfo', async (route) => {
    state.infoCalls += 1;
    if (state.infoGate) {
      await state.infoGate;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        msg: '操作成功',
        user: { nickName: '测试用户', userName: 'test' },
        roles: [role]
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
    if (url.pathname.startsWith('/src/')) {
      await route.continue();
      return;
    }
    state.educationCalls.push(url.pathname + url.search);
    if (url.pathname === '/education/teacher/announcements') {
      if (state.announcementsStatus >= 400) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: state.announcementsStatus, msg: '模拟服务失败' })
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(teacherList('真实路由公告')) });
      return;
    }
    if (url.pathname === '/education/admin/overview') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(adminOverviewBody()) });
      return;
    }
    if (url.pathname === '/education/student/notices') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(noticeList('真实路由通知')) });
      return;
    }
    if (url.pathname === '/education/student/notices/unread-count') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: '成功', data: { unreadCount: 1 } }) });
      return;
    }
    if (url.pathname === '/education/student/announcements') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: '查询成功', total: 0, rows: [] }) });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 404, msg: '模拟未覆盖：' + url.pathname }) });
  });

  return { context, page, state };
}

async function sessionState(page) {
  return page.evaluate(() => ({
    token: sessionStorage.getItem('zhijiao.login.token'),
    role: sessionStorage.getItem('zhijiao.login.role'),
    binding: sessionStorage.getItem('zhijiao.bindingId'),
    mock: sessionStorage.getItem('zhijiao.mock')
  }));
}

function sameStorage(actual, expected) {
  return actual.token === expected.token
    && actual.role === expected.role
    && actual.binding === expected.binding
    && actual.mock === expected.mock;
}

(async () => {
  const browser = await chromium.launch({ channel: CHANNEL, headless: true });
  const seeded = {
    token: 'preview-test-token',
    role: 'teacher',
    binding: '910000000002',
    mock: 'legacy'
  };

  // 1. 预览可用；切换、清空教学班不读写真实会话。
  const preview = await makePage(browser, 'teacher', seeded);
  await preview.page.goto(BASE + '/mock-preview.html', { waitUntil: 'load' });
  await preview.page.waitForSelector('.edu-table tbody tr', { timeout: 5000 });
  check(await preview.page.locator('#eduMockBanner').count() === 1, '离线预览应显示 Mock 横幅');
  check((await preview.page.locator('#eduBindingInput').inputValue()) === '910000000001', '预览应使用页面内演示教学班');
  check(sameStorage(await sessionState(preview.page), seeded), '预览首屏不得改写真实会话');

  await preview.page.fill('#eduBindingInput', '910000000002');
  await preview.page.click('[data-action="apply-binding"]');
  await preview.page.waitForTimeout(250);
  check(sameStorage(await sessionState(preview.page), seeded), '预览切换教学班不得改写真实会话');
  await preview.page.fill('#eduBindingInput', '');
  await preview.page.click('[data-action="apply-binding"]');
  await preview.page.waitForTimeout(80);
  check((await preview.page.locator('#viewRoot').innerText()).includes('尚未选择课程—教学班上下文'),
    '预览清空教学班应显示未选择提示');
  check(sameStorage(await sessionState(preview.page), seeded), '预览清空教学班不得改写真实会话');

  await preview.page.click('[data-role-tab="student"]');
  await preview.page.waitForSelector('.edu-notice-item', { timeout: 5000 });
  check((await preview.page.locator('#eduNoticeRegion').innerText()).includes('第 5 周随堂测验安排'), '学生通知预览应可用');
  await preview.page.click('[data-tab="announcements"]');
  await preview.page.waitForSelector('.edu-timeline-item', { timeout: 5000 });
  check((await preview.page.locator('#eduNoticeRegion').innerText()).includes('第 5 周随堂测验安排'), '课程公告预览应可用');
  check(sameStorage(await sessionState(preview.page), seeded), '预览切换角色不得改写真实会话');
  check(preview.state.errors.length === 0, '预览页不应有浏览器错误: ' + preview.state.errors.join(' | '));

  // 2. 同标签页从预览转运行页：沿用原教学班和登录态，不得继承预览状态。
  await preview.page.goto(BASE + '/teacher.html?g=0&i=3', { waitUntil: 'load' });
  const runtimeRequested = await waitUntil(() => preview.state.educationCalls
    .some((call) => call.startsWith('/education/teacher/announcements') && call.includes('bindingId=910000000002')));
  check(runtimeRequested, '同标签页切回运行页应沿用原教学班上下文: ' + JSON.stringify(preview.state.educationCalls));
  await waitForViewText(preview.page, '真实路由公告');
  check((await preview.page.locator('#viewRoot').innerText()).includes('真实路由公告'), '运行页应使用真实请求链路');
  check(await preview.page.locator('#eduMockBanner').count() === 0, '运行页不得显示预览横幅');
  check((await sessionState(preview.page)).token === seeded.token, '预览不得改变原登录态');
  check(preview.state.errors.length === 0, '运行页不应有浏览器错误: ' + preview.state.errors.join(' | '));
  await preview.context.close();

  // 3. 旧开关和 ?mock=1 无效；登录核验前零业务请求；核验后走真实列表。
  let releaseInfo = null;
  const legacy = await makePage(browser, 'teacher', {
    token: 'legacy-token',
    role: 'teacher',
    binding: '910000000002',
    mock: '1'
  });
  legacy.state.infoGate = new Promise((resolve) => { releaseInfo = resolve; });
  await legacy.page.goto(BASE + '/teacher.html?mock=1&g=0&i=3', { waitUntil: 'load' });
  await waitUntil(() => legacy.state.infoCalls === 1);
  await legacy.page.waitForTimeout(200);
  check(legacy.state.educationCalls.length === 0, '登录核验前不得发业务请求');
  check(await legacy.page.locator('#eduMockBanner').count() === 0, '旧开关不得让运行页进入 Mock');
  releaseInfo();
  const legacyRequested = await waitUntil(() => legacy.state.educationCalls
    .some((call) => call.startsWith('/education/teacher/announcements') && call.includes('bindingId=910000000002')));
  check(legacyRequested, '旧开关下核验后应走真实公告请求: ' + JSON.stringify(legacy.state.educationCalls));
  await waitForViewText(legacy.page, '真实路由公告');
  check((await legacy.page.locator('#viewRoot').innerText()).includes('真实路由公告'), '旧开关下应渲染真实请求结果');
  check(legacy.state.errors.length === 0, '旧开关运行页不应有浏览器错误: ' + legacy.state.errors.join(' | '));
  await legacy.context.close();

  // 4. 真实请求失败仍显示错误，不退化为 Mock 成功。
  const failing = await makePage(browser, 'teacher', {
    token: 'fail-token',
    role: 'teacher',
    binding: '910000000002',
    mock: '1'
  }, { announcementsStatus: 500 });
  await failing.page.goto(BASE + '/teacher.html?mock=1&g=0&i=3', { waitUntil: 'load' });
  const failedRequested = await waitUntil(() => failing.state.educationCalls
    .some((call) => call.startsWith('/education/teacher/announcements')));
  check(failedRequested, '运行页应发出真实公告请求以验证失败处理');
  await waitForViewText(failing.page, '公告列表读取失败');
  const failText = await failing.page.locator('#viewRoot').innerText();
  check(failText.includes('公告列表读取失败') && failText.includes('模拟服务失败'), '真实 500 应显示错误状态而不是 Mock 成功');
  check(await failing.page.locator('#eduMockBanner').count() === 0, '真实失败页不得出现 Mock 横幅');
  check(failing.state.errors.length === 0, '真实失败页不应有浏览器错误: ' + failing.state.errors.join(' | '));
  await failing.context.close();

  // 5. 学生运行页同样不接受旧开关。
  let releaseStudentInfo = null;
  const student = await makePage(browser, 'student', {
    token: 'student-token',
    role: 'student',
    binding: '910000000002',
    mock: '1'
  });
  student.state.infoGate = new Promise((resolve) => { releaseStudentInfo = resolve; });
  await student.page.goto(BASE + '/student.html?mock=1&g=0&i=3', { waitUntil: 'load' });
  await waitUntil(() => student.state.infoCalls === 1);
  await student.page.waitForTimeout(200);
  check(student.state.educationCalls.length === 0, '学生登录核验前不得发业务请求');
  check(await student.page.locator('#eduMockBanner').count() === 0, '学生运行页旧开关不得进入 Mock');
  releaseStudentInfo();
  const studentRequested = await waitUntil(() => student.state.educationCalls
    .some((call) => call.startsWith('/education/student/notices') && call.includes('bindingId=910000000002')));
  check(studentRequested, '学生运行页应按真实通知请求加载: ' + JSON.stringify(student.state.educationCalls));
  await waitForViewText(student.page, '真实路由通知');
  check((await student.page.locator('#viewRoot').innerText()).includes('真实路由通知'), '学生运行页应渲染真实通知结果');
  check(await student.page.locator('#eduMockBanner').count() === 0, '学生运行页不得显示 Mock 横幅');
  check(student.state.errors.length === 0, '学生运行页不应有浏览器错误: ' + student.state.errors.join(' | '));
  await student.context.close();

  // 6. 管理员运行页旧开关同样等待登录核验。
  let releaseAdminInfo = null;
  const admin = await makePage(browser, 'admin', {
    token: 'admin-token',
    role: 'admin',
    binding: '910000000002',
    mock: '1'
  });
  admin.state.infoGate = new Promise((resolve) => { releaseAdminInfo = resolve; });
  await admin.page.goto(BASE + '/admin.html?mock=1&g=0&i=0', { waitUntil: 'load' });
  await waitUntil(() => admin.state.infoCalls === 1);
  await admin.page.waitForTimeout(200);
  check(admin.state.educationCalls.length === 0, '管理员登录核验前不得发业务请求');
  check(await admin.page.locator('#eduMockBanner').count() === 0, '管理员运行页旧开关不得进入 Mock');
  releaseAdminInfo();
  const adminRequested = await waitUntil(() => admin.state.educationCalls
    .some((call) => call.startsWith('/education/admin/overview')));
  check(adminRequested, '管理员旧开关下核验后应走真实总览请求: ' + JSON.stringify(admin.state.educationCalls));
  check(await admin.page.locator('#eduMockBanner').count() === 0, '管理员真实请求页不得显示 Mock 横幅');
  check(admin.state.errors.length === 0, '管理员运行页不应有浏览器错误: ' + admin.state.errors.join(' | '));
  await admin.context.close();

  console.log(JSON.stringify({ passed: passed.length, failures, consoleErrors: [] }, null, 2));
  await browser.close();
  process.exit(failures.length ? 1 : 0);
})().catch((error) => {
  console.error(error);
  process.exit(2);
});
