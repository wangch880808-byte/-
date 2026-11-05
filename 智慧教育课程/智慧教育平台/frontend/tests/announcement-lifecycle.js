/**
 * 6-02 公告/通知生命周期回归。
 *
 * 用法：
 *   PLAYWRIGHT_MODULE=<playwright 模块路径或包名> \
 *   SIX_TEST_BASE=http://127.0.0.1:8130 \
 *   node frontend/tests/announcement-lifecycle.js
 *
 * 测试页使用假 EduHttp + 假 whenAuthorized；不连接真实后端，不校验真实登录。
 */
'use strict';

const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const BASE = process.env.SIX_TEST_BASE || 'http://127.0.0.1:8130';
const CHANNEL = process.env.PLAYWRIGHT_CHANNEL || 'msedge';
const failures = [];
const passed = [];

function check(condition, message) {
  if (condition) passed.push(message);
  else failures.push(message);
}

function teacherRows(title, status) {
  return [{
    id: '820000000000000001',
    bindingId: '910000000001',
    title: title,
    status: status || 'DRAFT',
    visibilityState: status === 'PUBLISHED' ? 'OPEN' : 'DRAFT',
    opensAt: null,
    closesAt: null,
    createdByUserId: '102',
    publishedByUserId: status === 'PUBLISHED' ? '102' : null,
    createdAt: '2026-11-05T00:00:00.000Z'
  }];
}

function teacherListBody(title, status) {
  return { code: 200, msg: '查询成功', total: 1, rows: teacherRows(title, status) };
}

function teacherDetailBody(title) {
  return { code: 200, msg: '操作成功', data: teacherRows(title, 'DRAFT')[0] };
}

function noticeRows(title) {
  return [{
    id: '830000000000000001',
    kind: 'ASSIGNMENT',
    bindingId: '910000000001',
    sourceId: '820000000000000001',
    title: title,
    visibleAt: '2026-11-05T00:00:00.000Z',
    readAt: null,
    isRead: false,
    createdAt: '2026-11-05T00:00:00.000Z'
  }];
}

async function open(page) {
  await page.goto(BASE + '/tests/announcement-lifecycle.html', { waitUntil: 'load' });
  await page.evaluate(() => {
    window.__clearCalls();
    sessionStorage.setItem('zhijiao.bindingId', '910000000001');
  });
}

async function mount(page, role, g, i) {
  await page.evaluate(({ role, g, i }) => window.__mount(role, g, i), { role, g, i });
}

async function resolveAuth(page, value) {
  await page.evaluate((value) => window.__resolveAuth(value), value);
}

async function pendingCount(page) {
  return page.evaluate(() => window.__pending.length);
}

async function calls(page) {
  return page.evaluate(() => window.__calls.map((c) => ({ method: c.method, url: c.url, query: c.query, body: c.body })));
}

async function resolvePending(page, index, body) {
  await page.evaluate(({ index, body }) => window.__resolveCall(index, body), { index, body });
}

async function findPending(page, part) {
  return page.evaluate((part) => window.__findPending(part), part);
}

async function findPendingExact(page, url) {
  return page.evaluate((url) => window.__findPendingExact(url), url);
}

async function waitForText(page, text) {
  await page.waitForFunction((t) => document.querySelector('#viewRoot').innerText.includes(t), text, { timeout: 3000 });
}

(async () => {
  const browser = await chromium.launch({ channel: CHANNEL, headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, timezoneId: 'Asia/Shanghai' });
  const page = await context.newPage();
  const consoleErrors = [];
  page.on('pageerror', (err) => consoleErrors.push('pageerror: ' + err.message));
  page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push('console: ' + msg.text()); });

  // 1. pending 时所有入口零 API；核验完成后只按最新筛选请求一次。
  await open(page);
  await mount(page, 'teacher', 0, 3);
  check((await pendingCount(page)) === 0, '登录 pending 时不得请求公告 API');
  await page.click('[data-action="refresh"]');
  await page.selectOption('#eduStatusFilter', 'DRAFT');
  await page.click('[data-action="refresh"]');
  await page.waitForTimeout(100);
  check((await pendingCount(page)) === 0, 'pending 连续操作不得堆积公告请求');
  await resolveAuth(page, true);
  await page.waitForTimeout(200);
  check((await pendingCount(page)) === 1, '核验通过后只应有一个列表请求');
  const pendingCalls = await calls(page);
  check(pendingCalls[0].query.status === 'DRAFT', '核验通过后应使用最新状态筛选');

  // 2. 核验失败不能通过筛选/刷新绕过。
  await open(page);
  await mount(page, 'teacher', 0, 3);
  await resolveAuth(page, false);
  await page.waitForTimeout(100);
  await page.click('[data-action="refresh"]');
  await page.selectOption('#eduStatusFilter', 'PUBLISHED');
  await page.waitForTimeout(100);
  check((await pendingCount(page)) === 0, '登录失败后不得请求公告 API');
  await waitForText(page, '登录状态核验失败');

  // 3. 同一视图挂载三次，一次刷新只产生一次列表请求。
  await open(page);
  for (let i = 0; i < 3; i++) {
    await mount(page, 'teacher', 0, 3);
    await resolveAuth(page, true);
    await page.waitForTimeout(100);
    const listIndex = await findPending(page, '/education/teacher/announcements');
    if (listIndex >= 0) await resolvePending(page, listIndex, teacherListBody('挂载-' + i, 'DRAFT'));
  }
  await page.waitForTimeout(100);
  await page.evaluate(() => window.__clearCalls());
  await page.click('[data-action="refresh"]');
  await page.waitForTimeout(150);
  check((await pendingCount(page)) === 1, '三次挂载后一次刷新只应产生一次请求');

  // 4. cleanup 关闭详情弹窗。
  await open(page);
  await mount(page, 'teacher', 0, 3);
  await resolveAuth(page, true);
  await page.waitForTimeout(100);
  let idx = await findPending(page, '/education/teacher/announcements');
  await resolvePending(page, idx, teacherListBody('详情公告', 'DRAFT'));
  await page.locator('[data-action="detail"]').first().click();
  await page.waitForTimeout(100);
  idx = await findPending(page, '/education/teacher/announcements/');
  await resolvePending(page, idx, teacherDetailBody('详情公告'));
  await page.waitForTimeout(80);
  check((await page.locator('.edu-overlay').count()) === 1, '详情弹窗应已打开');
  await page.evaluate(() => window.__unmount());
  await page.waitForTimeout(80);
  check((await page.locator('.edu-overlay').count()) === 0, 'cleanup 应关闭详情弹窗');

  // 5. cleanup 后旧确认不能触发发布。
  await open(page);
  await mount(page, 'teacher', 0, 3);
  await resolveAuth(page, true);
  await page.waitForTimeout(100);
  idx = await findPending(page, '/education/teacher/announcements');
  await resolvePending(page, idx, teacherListBody('待发布公告', 'DRAFT'));
  await page.locator('[data-action="publish"]').first().click();
  await page.waitForTimeout(80);
  check((await page.locator('.edu-overlay').count()) === 1, '发布确认框应出现');
  await page.evaluate(() => window.__unmount());
  await page.waitForTimeout(80);
  check((await page.locator('.edu-overlay').count()) === 0, 'cleanup 应关闭确认框');
  check((await pendingCount(page)) === 0, 'cleanup 后确认结果不得发起发布请求');

  // 6. A/B 状态响应乱序：后发请求先返回，旧响应不能覆盖。
  await open(page);
  await mount(page, 'teacher', 0, 3);
  await resolveAuth(page, true);
  await page.waitForTimeout(100);
  check((await pendingCount(page)) === 1, '初次列表请求应存在');
  await page.selectOption('#eduStatusFilter', 'PUBLISHED');
  await page.waitForTimeout(100);
  check((await pendingCount(page)) === 2, '切换状态后应有两个 pending 列表请求');
  const firstIndex = await findPending(page, '/education/teacher/announcements');
  await resolvePending(page, firstIndex + 1, teacherListBody('最新结果', 'PUBLISHED'));
  await page.waitForTimeout(80);
  await resolvePending(page, firstIndex, teacherListBody('旧结果', 'DRAFT'));
  await page.waitForTimeout(100);
  const listText = await page.locator('#viewRoot').innerText();
  check(listText.includes('最新结果') && !listText.includes('旧结果'), '旧列表响应不得覆盖新筛选结果');

  // 6b. A/B 教学班响应乱序。
  await open(page);
  await mount(page, 'teacher', 0, 3);
  await resolveAuth(page, true);
  await page.waitForTimeout(100);
  idx = await findPending(page, '/education/teacher/announcements');
  await resolvePending(page, idx, teacherListBody('初始班级', 'DRAFT'));
  await page.waitForTimeout(80);
  await page.evaluate(() => window.__clearCalls());
  await page.fill('#eduBindingInput', '910000000002');
  await page.click('[data-action="apply-binding"]');
  await page.waitForTimeout(80);
  await page.fill('#eduBindingInput', '910000000003');
  await page.click('[data-action="apply-binding"]');
  await page.waitForTimeout(100);
  check((await pendingCount(page)) === 2, 'A/B binding 快速切换应有两个 pending 列表请求');
  const bindingFirst = await findPending(page, '/education/teacher/announcements');
  await resolvePending(page, bindingFirst + 1, teacherListBody('B 班最新', 'DRAFT'));
  await page.waitForTimeout(80);
  await resolvePending(page, bindingFirst, teacherListBody('A 班旧', 'DRAFT'));
  await page.waitForTimeout(100);
  const bindingText = await page.locator('#viewRoot').innerText();
  check(bindingText.includes('B 班最新') && !bindingText.includes('A 班旧'), '旧 binding 响应不得覆盖新结果');

  // 7. cleanup 后排队微任务不派发。
  await open(page);
  await mount(page, 'teacher', 0, 3);
  await resolveAuth(page, true);
  await page.waitForTimeout(100);
  idx = await findPending(page, '/education/teacher/announcements');
  await resolvePending(page, idx, teacherListBody('初始', 'DRAFT'));
  await page.evaluate(() => window.__clearCalls());
  await page.evaluate(() => {
    document.querySelector('[data-action="refresh"]').click();
    window.__unmount();
  });
  await page.waitForTimeout(120);
  check((await pendingCount(page)) === 0, 'cleanup 后排队微任务不得派发 API');

  // 7b. 旧详情响应不得写入新视图的新弹窗。
  await open(page);
  await mount(page, 'teacher', 0, 3);
  await resolveAuth(page, true);
  await page.waitForTimeout(100);
  idx = await findPending(page, '/education/teacher/announcements');
  await resolvePending(page, idx, teacherListBody('旧详情', 'DRAFT'));
  await page.locator('[data-action="detail"]').first().click();
  await page.waitForTimeout(80);
  const oldDetailIndex = await findPending(page, '/education/teacher/announcements/');
  await page.evaluate(() => window.__unmount());
  await mount(page, 'teacher', 0, 3);
  await resolveAuth(page, true);
  await page.waitForTimeout(100);
  idx = await findPendingExact(page, '/education/teacher/announcements');
  await resolvePending(page, idx, teacherListBody('新详情', 'DRAFT'));
  await page.locator('[data-action="detail"]').first().click();
  await page.waitForTimeout(80);
  await resolvePending(page, oldDetailIndex, teacherDetailBody('旧详情标题'));
  await page.waitForTimeout(100);
  const oldDialogText = await page.locator('.edu-overlay .edu-dialog').innerText().catch(() => '');
  check(!oldDialogText.includes('旧详情标题'), '旧详情响应不得写入新弹窗');

  // 7c. 页签切换后旧通知列表响应不得写入新页签。
  await open(page);
  await mount(page, 'student', 0, 3);
  await resolveAuth(page, true);
  await page.waitForTimeout(100);
  let tabList = await findPendingExact(page, '/education/student/notices');
  await resolvePending(page, tabList, { code: 200, msg: '查询成功', total: 1, rows: noticeRows('旧页签通知') });
  const tabUnread = await findPending(page, '/education/student/notices/unread-count');
  if (tabUnread >= 0) await resolvePending(page, tabUnread, { code: 200, msg: '成功', data: { unreadCount: 1 } });
  await page.waitForTimeout(80);
  await page.evaluate(() => window.__clearCalls());
  await page.click('[data-action="refresh"]');
  await page.waitForTimeout(80);
  await page.click('[data-tab="announcements"]');
  await page.waitForTimeout(80);
  tabList = await findPendingExact(page, '/education/student/notices');
  await resolvePending(page, tabList, { code: 200, msg: '查询成功', total: 1, rows: noticeRows('旧页签通知覆盖') });
  await page.waitForTimeout(100);
  check((await page.locator('#viewRoot').innerText()).indexOf('旧页签通知覆盖') < 0, '切换页签后旧通知响应不得写入新页签');

  // 8. 全部已读文案和全量作用范围。
  await open(page);
  await mount(page, 'student', 0, 3);
  await resolveAuth(page, true);
  await page.waitForTimeout(100);
  const noticeIdx = await findPending(page, '/education/student/notices');
  const unreadIdx = await findPending(page, '/education/student/notices/unread-count');
  if (noticeIdx >= 0) await resolvePending(page, noticeIdx, { code: 200, msg: '查询成功', total: 1, rows: noticeRows('待读通知') });
  const unreadNow = await findPending(page, '/education/student/notices/unread-count');
  if (unreadNow >= 0) await resolvePending(page, unreadNow, { code: 200, msg: '成功', data: { unreadCount: 1 } });
  await page.waitForTimeout(100);
  await page.click('[data-action="mark-all"]');
  await page.waitForTimeout(80);
  const dialogText = await page.locator('.edu-dialog-text').innerText();
  check(dialogText === '将标记本人所有教学班当前可见的未读通知，包括其他教学班，不受当前筛选和分页限制。', '全部已读确认文案应明确全量范围');
  await page.locator('.edu-dialog [data-role="ok"]').click();
  await page.waitForTimeout(120);
  const markAllIndex = await findPending(page, '/education/student/notices/read-all');
  check(markAllIndex >= 0, '全部已读应调用 read-all');
  if (markAllIndex >= 0) {
    const markAllCalls = await calls(page);
    const readAll = markAllCalls.find((c) => c.url.indexOf('/education/student/notices/read-all') >= 0);
    check(readAll && readAll.method === 'PUT', '全部已读应使用 PUT 且不带 binding 参数');
  }

  // 9. 13 个接口方法/路径不回归。
  await open(page);
  await page.evaluate(() => {
    const A = window.EduAnnouncementApi;
    const N = window.EduNoticeApi;
    // 不 await：假客户端刻意保持 pending，路径检查只看发出的请求。
    A.teacher.list({ bindingId: '1', status: 'DRAFT', pageNum: 1, pageSize: 10 });
    A.teacher.detail('1');
    A.teacher.create({ bindingId: '1', title: 't', body: 'b' });
    A.teacher.update('1', { title: 't', body: 'b' });
    A.teacher.publish('1');
    A.teacher.archive('1');
    A.student.list({ bindingId: '1', pageNum: 1, pageSize: 10 });
    A.student.detail('1');
    N.list({ kind: 'ASSIGNMENT', bindingId: '1', unreadOnly: true, pageNum: 1, pageSize: 10 });
    N.detail('1');
    N.unreadCount('1');
    N.markRead('1');
    N.markAllRead();
  });
  await page.waitForTimeout(50);
  const endpointCalls = await calls(page);
  check(endpointCalls.length === 13, '13 个接口应全部发出');
  check(endpointCalls[0].method === 'GET' && endpointCalls[0].url === '/education/teacher/announcements', '教师列表方法/路径错误');
  check(endpointCalls[2].method === 'POST' && endpointCalls[2].url === '/education/teacher/announcements', '教师创建方法/路径错误');
  check(endpointCalls[4].method === 'POST' && endpointCalls[4].url === '/education/teacher/announcements/1/publish', '教师发布方法/路径错误');
  check(endpointCalls[8].method === 'GET' && endpointCalls[8].url === '/education/student/notices', '通知列表方法/路径错误');
  check(endpointCalls[11].method === 'POST' && endpointCalls[11].url === '/education/student/notices/1/read', '单条已读方法/路径错误');
  check(endpointCalls[12].method === 'PUT' && endpointCalls[12].url === '/education/student/notices/read-all', '全部已读方法/路径错误');

  check(consoleErrors.length === 0, '测试页不应有 console/page 错误: ' + consoleErrors.join(' | '));
  console.log(JSON.stringify({ passed: passed.length, failures, consoleErrors }, null, 2));
  await browser.close();
  process.exit(failures.length ? 1 : 0);
})().catch((error) => {
  console.error(error);
  process.exit(2);
});
