/**
 * 6-03 阶段1回归测试（仓库内可移植脚本）。
 *
 * 用法：
 *   PLAYWRIGHT_MODULE=<playwright 模块路径或包名> \
 *   OVERVIEW_TEST_BASE=http://127.0.0.1:8125 \
 *   node frontend/tests/overview-stage1.js
 *
 * 可选：
 *   PLAYWRIGHT_CHANNEL=msedge|chrome|...（默认 msedge）
 *
 * 不创建 package.json/锁文件；Playwright 由外部环境提供。
 * 测试页使用假 EduHttp，不发送真实请求、不校验真实登录。
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

function item(code, value, unit, source, sampleCount) {
  return {
    metricCode: code,
    value: value,
    unit: unit || 'COUNT',
    status: 'OK',
    source: source || 'course',
    sampleCount: sampleCount === undefined ? 3 : sampleCount,
    actionPath: '/candidate'
  };
}

function response(items) {
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
    }
  };
}

function teacherItems() {
  return [
    item('teacher.class.count', 3, 'COUNT', 'course'),
    item('teacher.student.count', 86, 'COUNT', 'course'),
    item('teacher.session.running', 1, 'COUNT', 'classroom'),
    item('teacher.assignment.pending', 12, 'COUNT', 'assignment'),
    item('teacher.grade.pending', 5, 'COUNT', 'grade'),
    item('teacher.notice.unread', 4, 'COUNT', 'notice')
  ];
}

async function open(page) {
  await page.goto(BASE + '/tests/overview-stage1.html', { waitUntil: 'load' });
  await page.waitForTimeout(50);
}

async function setResponse(page, value) {
  await page.evaluate((r) => window.__setResponse(r), value);
}

async function mount(page, role) {
  await page.evaluate((r) => window.__mount(r), role || 'teacher');
}

async function resolveAuth(page, value) {
  await page.evaluate((v) => window.__resolveAuth(v), value);
}

async function calls(page) {
  return page.evaluate(() => window.__calls.slice());
}

async function authCalls(page) {
  return page.evaluate(() => window.__authCalls);
}

async function waitCalls(page, count) {
  await page.waitForFunction((n) => window.__calls.length >= n, count, { timeout: 3000 });
}

async function waitCards(page, count) {
  await page.waitForFunction((n) => {
    return document.querySelectorAll('#viewRoot .ov-card').length === n
      && !document.querySelector('#viewRoot .ov-skeleton');
  }, count, { timeout: 3000 });
}

async function firstCardText(page) {
  return page.locator('#viewRoot .ov-card').first().innerText();
}

async function okBadgeCount(page) {
  return page.locator('#viewRoot .ov-badge-ok').count();
}

(async () => {
  const browser = await chromium.launch({ channel: CHANNEL, headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  const consoleErrors = [];
  page.on('pageerror', (err) => consoleErrors.push('pageerror: ' + err.message));
  page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push('console: ' + msg.text()); });

  // 1. 登录 pending 时筛选不得请求。
  await open(page);
  await setResponse(page, response(teacherItems()));
  await mount(page);
  check(await authCalls(page) === 1, '首次挂载应只发起一次登录核验');
  check((await calls(page)).length === 0, '登录 pending 时不得请求总览');
  await page.fill('#ovFrom', '2026-11-04T08:00');
  await page.fill('#ovTo', '2026-11-05T08:00');
  await page.click('[data-action="apply-time"]');
  await page.waitForTimeout(80);
  check((await calls(page)).length === 0, '登录 pending 时点击筛选不得请求 API');
  await resolveAuth(page, true);
  await waitCalls(page, 1);
  const pendingCalls = await calls(page);
  check(pendingCalls.length === 1, '登录通过后应只按最新筛选请求一次');
  check(pendingCalls[0].query.from === '2026-11-04T00:00:00.000Z', '登录通过后应使用最新开始时间');
  check(await authCalls(page) === 1, 'pending 期间操作不应重复发起登录核验');

  // 2. pending 期间连续操作不堆积请求。
  await open(page);
  await setResponse(page, response(teacherItems()));
  await mount(page);
  await page.fill('#ovFrom', '2026-11-03T08:00');
  await page.fill('#ovTo', '2026-11-04T08:00');
  await page.click('[data-action="apply-time"]');
  await page.fill('#ovFrom', '2026-11-04T08:00');
  await page.fill('#ovTo', '2026-11-05T08:00');
  await page.click('[data-action="apply-time"]');
  await page.waitForTimeout(80);
  check((await calls(page)).length === 0, 'pending 连续操作不得堆积请求');
  await resolveAuth(page, true);
  await waitCalls(page, 1);
  const consecutiveCalls = await calls(page);
  check(consecutiveCalls.length === 1, 'pending 连续操作后只允许一次请求');
  check(consecutiveCalls[0].query.from === '2026-11-04T00:00:00.000Z', '应按最新筛选请求');

  // 3. 核验 false/超时后不能绕过等待。
  await open(page);
  await setResponse(page, response(teacherItems()));
  await mount(page);
  await resolveAuth(page, false);
  await page.waitForTimeout(80);
  check((await calls(page)).length === 0, '登录核验失败时不得请求 API');
  await page.fill('#ovFrom', '2026-11-04T08:00');
  await page.fill('#ovTo', '2026-11-05T08:00');
  await page.click('[data-action="apply-time"]');
  await page.waitForTimeout(80);
  check((await calls(page)).length === 0, '登录失败后点击筛选不得直接请求');
  check((await page.locator('#viewRoot').innerText()).includes('登录状态核验超时'), '登录失败后应保留登录错误态');
  await page.click('[data-action="retry-auth"]');
  await page.waitForTimeout(80);
  check(await authCalls(page) === 2, '重试应重新发起登录核验');
  check((await calls(page)).length === 0, '重试登录核验未完成前不得请求 API');
  await resolveAuth(page, true);
  await waitCalls(page, 1);
  check((await calls(page)).length === 1, '重试核验通过后应只请求一次');

  // 4. BFCache 恢复重新核验，核验完成前零调用。
  await open(page);
  await setResponse(page, response(teacherItems()));
  await mount(page);
  await resolveAuth(page, true);
  await waitCalls(page, 1);
  const beforeBfcache = (await calls(page)).length;
  const beforeAuth = await authCalls(page);
  await page.evaluate(() => {
    window.dispatchEvent(new Event('pagehide'));
    const event = new Event('pageshow');
    Object.defineProperty(event, 'persisted', { value: true });
    window.dispatchEvent(event);
  });
  await page.waitForTimeout(80);
  check(await authCalls(page) === beforeAuth + 1, 'BFCache 恢复应重新核验登录');
  check((await calls(page)).length === beforeBfcache, 'BFCache 核验完成前不得请求 API');
  await resolveAuth(page, true);
  await waitCalls(page, beforeBfcache + 1);
  check((await calls(page)).length === beforeBfcache + 1, 'BFCache 核验通过后应只请求一次');

  // 5. cleanup 后登录核验才完成，不得请求或写页面。
  await open(page);
  await setResponse(page, response(teacherItems()));
  await mount(page);
  await page.fill('#ovFrom', '2026-11-04T08:00');
  await page.fill('#ovTo', '2026-11-05T08:00');
  await page.click('[data-action="apply-time"]');
  await page.evaluate(() => window.__unmount());
  await resolveAuth(page, true);
  await page.waitForTimeout(100);
  check((await calls(page)).length === 0, 'cleanup 后迟到的登录结果不得请求 API');
  check((await page.locator('#viewRoot .ov-card').count()) === 0, 'cleanup 后不得写入卡片');

  // 6. 重复 metricCode：相同数据。
  await open(page);
  const dupSame = teacherItems();
  dupSame.splice(1, 0, item('teacher.class.count', 3, 'COUNT', 'course'));
  await setResponse(page, response(dupSame));
  await mount(page);
  await resolveAuth(page, true);
  await waitCards(page, 6);
  check((await firstCardText(page)).includes('数据异常'), '重复相同 metricCode 应降级首卡');
  check((await firstCardText(page)).includes('暂无数据') === false, '重复 metricCode 不能当正常数据');
  check(await okBadgeCount(page) === 5, '重复 metricCode 不应影响其他 5 张有效卡');

  // 7. 重复 metricCode：冲突数据，交换顺序。
  const conflictA = teacherItems();
  conflictA.splice(1, 0, item('teacher.class.count', 9, 'COUNT', 'course'));
  await open(page);
  await setResponse(page, response(conflictA));
  await mount(page);
  await resolveAuth(page, true);
  await waitCards(page, 6);
  check((await firstCardText(page)).includes('数据异常'), '冲突重复 metricCode（3→9）应降级');
  check(await okBadgeCount(page) === 5, '冲突重复 metricCode 不应影响其他卡');

  const conflictB = teacherItems();
  conflictB[0] = item('teacher.class.count', 9, 'COUNT', 'course');
  conflictB.splice(1, 0, item('teacher.class.count', 3, 'COUNT', 'course'));
  await open(page);
  await setResponse(page, response(conflictB));
  await mount(page);
  await resolveAuth(page, true);
  await waitCards(page, 6);
  check((await firstCardText(page)).includes('数据异常'), '冲突重复 metricCode（9→3）应降级');

  // 8. unit 非 COUNT 降级。
  const badUnit = teacherItems();
  badUnit[0] = item('teacher.class.count', 3, 'PERCENT', 'course');
  await open(page);
  await setResponse(page, response(badUnit));
  await mount(page);
  await resolveAuth(page, true);
  await waitCards(page, 6);
  check((await firstCardText(page)).includes('数据异常'), 'unit=PERCENT 应降级');
  check(await okBadgeCount(page) === 5, 'unit 违约不应影响其他卡');

  // 9. source 与配置不一致降级。
  const badSource = teacherItems();
  badSource[0] = item('teacher.class.count', 3, 'COUNT', 'grade');
  await open(page);
  await setResponse(page, response(badSource));
  await mount(page);
  await resolveAuth(page, true);
  await waitCards(page, 6);
  check((await firstCardText(page)).includes('数据异常'), 'source 不一致应降级');
  check(await okBadgeCount(page) === 5, 'source 违约不应影响其他卡');

  // 10. 正常 COUNT + 合法 source 不回归。
  await open(page);
  await setResponse(page, response(teacherItems()));
  await mount(page);
  await resolveAuth(page, true);
  await waitCards(page, 6);
  check(await okBadgeCount(page) === 6, '正常 COUNT/source 应保持 6 张 OK 卡');
  check((await firstCardText(page)).includes('数据异常') === false, '正常数据不应被误降级');

  check(consoleErrors.length === 0, '测试页不应有 console/page 错误: ' + consoleErrors.join(' | '));

  console.log(JSON.stringify({ passed: passed.length, failures, consoleErrors }, null, 2));
  await browser.close();
  process.exit(failures.length ? 1 : 0);
})().catch((error) => {
  console.error(error);
  process.exit(2);
});
