/**
 * 6-02 离线 Mock 草稿 ID 边界回归。
 *
 * 用法：
 *   PLAYWRIGHT_MODULE=<playwright 模块路径> \
 *   MOCK_ID_TEST_BASE=http://127.0.0.1:8134 \
 *   node frontend/tests/mock-draft-id.js
 *
 * 测试把预览 Mock 的公告序号临时推进到 Number.MAX_SAFE_INTEGER 附近再加载，
 * 用连续创建草稿证明旧 Number 自增会碰撞、新字符串递增不会碰撞。
 * 不连接真实后端、不创建真实登录。
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const BASE = process.env.MOCK_ID_TEST_BASE || 'http://127.0.0.1:8134';
const CHANNEL = process.env.PLAYWRIGHT_CHANNEL || 'msedge';
const SEED = '9007199254740991';
const failures = [];
const passed = [];

function check(condition, message) {
  if (condition) {
    passed.push(message);
  } else {
    failures.push(message);
  }
}

const sourcePath = path.join(__dirname, '..', 'preview', 'education-mock.js');
const source = fs.readFileSync(sourcePath, 'utf8');
const seededSource = source.replace(
  /var announcementSeq = ['"]?820000000000000100['"]?;/,
  "var announcementSeq = '" + SEED + "';"
);
if (seededSource === source) {
  console.error('未能定位 announcementSeq 初始值，测试无法把序号推进到安全整数边界');
  process.exit(2);
}

(async () => {
  const browser = await chromium.launch({ channel: CHANNEL, headless: true });
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  const errors = [];
  page.on('pageerror', (err) => errors.push('pageerror: ' + err.message));
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      errors.push('console: ' + msg.text());
    }
  });
  await page.route('**/preview/education-mock.js', (route) => route.fulfill({
    status: 200,
    contentType: 'application/javascript',
    body: seededSource
  }));

  await page.goto(BASE + '/mock-preview.html', { waitUntil: 'load' });
  await page.waitForSelector('#eduMockBanner', { timeout: 5000 });

  const result = await page.evaluate(async () => {
    const api = window.EduAnnouncementApi.teacher;
    const first = await api.create({
      bindingId: '910000000001',
      title: '边界草稿一',
      body: '正文一'
    });
    const second = await api.create({
      bindingId: '910000000001',
      title: '边界草稿二',
      body: '正文二'
    });
    const firstDetail = await api.detail(first.data.id);
    const secondDetail = await api.detail(second.data.id);
    await api.update(first.data.id, {
      title: '边界草稿一（已编辑）',
      body: '正文一已编辑'
    });
    const firstEdited = await api.detail(first.data.id);
    const secondAfterEdit = await api.detail(second.data.id);
    return {
      id1: first.data.id,
      id2: second.data.id,
      firstTitle: firstDetail.data.title,
      secondTitle: secondDetail.data.title,
      firstEditedTitle: firstEdited.data.title,
      firstEditedBody: firstEdited.data.body,
      secondAfterTitle: secondAfterEdit.data.title,
      secondAfterBody: secondAfterEdit.data.body,
      firstEditedId: firstEdited.data.id,
      secondAfterId: secondAfterEdit.data.id
    };
  });

  const builtIn = [
    '820000000000000001',
    '820000000000000002',
    '820000000000000003',
    '820000000000000004',
    '820000000000000005',
    '820000000000000006',
    '820000000000000007'
  ];
  check(typeof result.id1 === 'string' && typeof result.id2 === 'string', '草稿 ID 应为字符串');
  check(result.id1 !== result.id2, '连续创建的两条草稿应获得不同 ID');
  check(BigInt(result.id1) > BigInt(SEED) && BigInt(result.id2) > BigInt(SEED), '草稿 ID 应越过安全整数边界继续精确递增');
  check(builtIn.indexOf(result.id1) < 0 && builtIn.indexOf(result.id2) < 0, '新草稿 ID 不得与内置公告冲突');
  check(result.firstTitle === '边界草稿一' && result.secondTitle === '边界草稿二', '按各自 ID 应查到正确详情');
  check(result.firstEditedTitle === '边界草稿一（已编辑）'
    && result.firstEditedBody === '正文一已编辑', '编辑草稿一应更新其详情');
  check(result.secondAfterTitle === '边界草稿二'
    && result.secondAfterBody === '正文二', '编辑草稿一不得影响草稿二');
  check(result.firstEditedId === result.id1 && result.secondAfterId === result.id2, '编辑后草稿 ID 应保持不变');
  check(errors.length === 0, '测试页不应有浏览器错误: ' + errors.join(' | '));

  console.log(JSON.stringify({
    seed: SEED,
    ids: [result.id1, result.id2],
    passed: passed.length,
    failures: failures,
    consoleErrors: errors
  }, null, 2));
  await browser.close();
  process.exit(failures.length ? 1 : 0);
})().catch((error) => {
  console.error(error);
  process.exit(2);
});
