/**
 * 6-02 离线 Mock ID、分页与列表筛选解析回归。
 *
 * 用法：
 *   PLAYWRIGHT_MODULE=<playwright 模块路径> \
 *   MOCK_PARAM_TEST_BASE=http://127.0.0.1:8135 \
 *   node frontend/tests/mock-params.js
 *
 * 直接调用预览页的 EduMock.request，覆盖显式空查询值；
 * 不连接真实后端、不创建真实登录。
 */
'use strict';

const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const BASE = process.env.MOCK_PARAM_TEST_BASE || 'http://127.0.0.1:8135';
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

  await page.goto(BASE + '/mock-preview.html', { waitUntil: 'load' });
  await page.waitForSelector('#eduMockBanner', { timeout: 5000 });

  const result = await page.evaluate(async () => {
    const mock = window.EduMock;
    async function call(method, path, query, body) {
      try {
        const response = await mock.request({ method: method, path: path, query: query, body: body });
        return { ok: true, code: response.code, data: response.data, rows: response.rows };
      } catch (error) {
        return { ok: false, code: error.code, message: error.message };
      }
    }
    const teacherList = '/education/teacher/announcements';
    const teacherDetailPath = '/education/teacher/announcements/';
    const plusEncoded = teacherDetailPath + encodeURIComponent('+820000000000000001');
    const spaceEncoded = teacherDetailPath + encodeURIComponent(' 820000000000000001');
    const doubleEncoded = teacherDetailPath + encodeURIComponent(encodeURIComponent('+820000000000000001'));
    return {
      teacherLeadingZero: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '1', pageSize: '10' }),
      teacherPlus: await call('GET', teacherList, { bindingId: '+910000000001', pageNum: '1', pageSize: '10' }),
      teacherMaxBinding: await call('GET', teacherList, { bindingId: '9223372036854775807', pageNum: '1', pageSize: '10' }),
      teacherOverflowBinding: await call('GET', teacherList, { bindingId: '9223372036854775808', pageNum: '1', pageSize: '10' }),
      teacherZeroBinding: await call('GET', teacherList, { bindingId: '0', pageNum: '1', pageSize: '10' }),
      teacherNegativeBinding: await call('GET', teacherList, { bindingId: '-1', pageNum: '1', pageSize: '10' }),
      teacherExponentBinding: await call('GET', teacherList, { bindingId: '1e2', pageNum: '1', pageSize: '10' }),
      teacherMissingBinding: await call('GET', teacherList, { pageNum: '1', pageSize: '10' }),
      studentAnnOmitted: await call('GET', '/education/student/announcements', { pageNum: '1', pageSize: '10' }),
      studentAnnEmpty: await call('GET', '/education/student/announcements', { bindingId: '', pageNum: '1', pageSize: '10' }),
      studentAnnBlank: await call('GET', '/education/student/announcements', { bindingId: '   ', pageNum: '1', pageSize: '10' }),
      studentAnnLeadingZero: await call('GET', '/education/student/announcements', { bindingId: '000910000000001', pageNum: '1', pageSize: '10' }),
      studentNoticeOmitted: await call('GET', '/education/student/notices', { pageNum: '1', pageSize: '10' }),
      studentNoticeEmpty: await call('GET', '/education/student/notices', { bindingId: '', pageNum: '1', pageSize: '10' }),
      studentNoticeBlank: await call('GET', '/education/student/notices', { bindingId: '  ', pageNum: '1', pageSize: '10' }),
      teacherDetailLeadingZero: await call('GET', '/education/teacher/announcements/000820000000000000001'),
      teacherDetailPlusEncoded: await call('GET', plusEncoded),
      teacherDetailSpaceEncoded: await call('GET', spaceEncoded),
      teacherDetailDoubleEncoded: await call('GET', doubleEncoded),
      teacherDetailEncodedSlash: await call('GET', '/education/teacher/announcements/%2F'),
      teacherDetailOverflow: await call('GET', '/education/teacher/announcements/9223372036854775808'),
      teacherDetailMaxNonexistent: await call('GET', '/education/teacher/announcements/9223372036854775807'),
      teacherDetailZero: await call('GET', '/education/teacher/announcements/0'),
      teacherDetailNegative: await call('GET', '/education/teacher/announcements/-1'),
      teacherDetailDecimal: await call('GET', '/education/teacher/announcements/1.0'),
      noticeDetailLeadingZero: await call('GET', '/education/student/notices/000830000000000000001'),
      studentAnnDetailLeadingZero: await call('GET', '/education/student/announcements/000820000000000000001'),
      pageNumExponent: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '1e2', pageSize: '10' }),
      pageNumHex: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '0x10', pageSize: '10' }),
      pageNumDecimal: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '1.5', pageSize: '10' }),
      pageNumZero: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '0', pageSize: '10' }),
      pageNumMax: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '2147483647', pageSize: '10' }),
      pageNumOverflow: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '2147483648', pageSize: '10' }),
      pageNumBlank: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '   ', pageSize: '10' }),
      pageNumEmpty: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '', pageSize: '10' }),
      pageNumPlus: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '+1', pageSize: '10' }),
      pageNumLeadingZero: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '0001', pageSize: '10' }),
      pageSizeZero: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '1', pageSize: '0' }),
      pageSize101: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '1', pageSize: '101' }),
      pageSize100: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '1', pageSize: '100' }),
      pageSizeExponent: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '1', pageSize: '1e2' }),
      pageSizeHex: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '1', pageSize: '0x10' }),
      pageSizeBlank: await call('GET', teacherList, { bindingId: '000910000000001', pageNum: '1', pageSize: '  ' }),
      statusBlank: await call('GET', teacherList, { bindingId: '000910000000001', status: '   ', pageNum: '1', pageSize: '10' }),
      statusEmpty: await call('GET', teacherList, { bindingId: '000910000000001', status: '', pageNum: '1', pageSize: '10' }),
      statusLower: await call('GET', teacherList, { bindingId: '000910000000001', status: 'draft', pageNum: '1', pageSize: '10' }),
      statusInvalid: await call('GET', teacherList, { bindingId: '000910000000001', status: 'BOGUS', pageNum: '1', pageSize: '10' }),
      kindBlank: await call('GET', '/education/student/notices', { kind: '', pageNum: '1', pageSize: '10' }),
      kindWhitespace: await call('GET', '/education/student/notices', { kind: '  ', pageNum: '1', pageSize: '10' }),
      kindLower: await call('GET', '/education/student/notices', { kind: 'assignment', pageNum: '1', pageSize: '10' }),
      kindInvalid: await call('GET', '/education/student/notices', { kind: 'BOGUS', pageNum: '1', pageSize: '10' }),
      unreadEmpty: await call('GET', '/education/student/notices', { unreadOnly: '', pageNum: '1', pageSize: '10' }),
      unreadBlank: await call('GET', '/education/student/notices', { unreadOnly: '  ', pageNum: '1', pageSize: '10' }),
      unreadUpper: await call('GET', '/education/student/notices', { unreadOnly: 'TRUE', pageNum: '1', pageSize: '10' }),
      unreadInvalid: await call('GET', '/education/student/notices', { unreadOnly: 'yes', pageNum: '1', pageSize: '10' }),
      createPlusBinding: await call('POST', teacherList, undefined, {
        bindingId: '+000910000000001',
        title: '参数测试草稿',
        body: '正文'
      })
    };
  });

  check(result.teacherLeadingZero.code === 200, '教师列表 bindingId 前导零应接受');
  check(result.teacherPlus.code === 200, '教师列表 bindingId 正号应接受');
  check(result.teacherMaxBinding.code === 403, 'Long 上界 bindingId 应通过格式校验后按授权拒绝');
  check(result.teacherOverflowBinding.code === 400, 'bindingId 超过 Long 上界应 400');
  check(result.teacherZeroBinding.code === 400, 'bindingId 为 0 应 400');
  check(result.teacherNegativeBinding.code === 400, 'bindingId 为负数应 400');
  check(result.teacherExponentBinding.code === 400, 'bindingId 指数格式应 400');
  check(result.teacherMissingBinding.code === 400, '教师列表缺少 bindingId 应 400');

  check(result.studentAnnOmitted.code === 200, '学生公告省略 bindingId 应返回全部');
  check(result.studentAnnEmpty.code === 400, '学生公告显式空 bindingId 应 400');
  check(result.studentAnnBlank.code === 400, '学生公告纯空白 bindingId 应 400');
  check(result.studentAnnLeadingZero.code === 200, '学生公告 bindingId 前导零应接受');
  check(result.studentNoticeOmitted.code === 200, '学生通知省略 bindingId 应返回全部');
  check(result.studentNoticeEmpty.code === 400, '学生通知显式空 bindingId 应 400');
  check(result.studentNoticeBlank.code === 400, '学生通知纯空白 bindingId 应 400');

  check(result.teacherDetailLeadingZero.code === 200
    && result.teacherDetailLeadingZero.data.id === '820000000000000001', '路径 ID 前导零应规范化后命中');
  check(result.teacherDetailPlusEncoded.code === 200
    && result.teacherDetailPlusEncoded.data.id === '820000000000000001', '路径 ID 编码正号应接受并命中');
  check(result.teacherDetailSpaceEncoded.code === 200
    && result.teacherDetailSpaceEncoded.data.id === '820000000000000001', '路径 ID 编码前导空白应 trim 后命中');
  check(result.teacherDetailDoubleEncoded.code === 400, '路径 ID 只能解码一次，双重编码应 400');
  check(result.teacherDetailEncodedSlash.code === 400, '路径 ID 编码斜杠解码后应 400');
  check(result.teacherDetailOverflow.code === 400, '路径 ID 超过 Long 上界应 400');
  check(result.teacherDetailMaxNonexistent.code === 404, 'Long 上界合法但不存在的路径 ID 应 404');
  check(result.teacherDetailZero.code === 400, '路径 ID 为 0 应 400');
  check(result.teacherDetailNegative.code === 400, '路径 ID 为负数应 400');
  check(result.teacherDetailDecimal.code === 400, '路径 ID 小数应 400');
  check(result.noticeDetailLeadingZero.code === 200
    && result.noticeDetailLeadingZero.data.id === '830000000000000001', '通知路径 ID 前导零应命中原通知');
  check(result.studentAnnDetailLeadingZero.code === 200
    && result.studentAnnDetailLeadingZero.data.id === '820000000000000001', '学生公告路径 ID 前导零应命中原公告');

  check(result.pageNumExponent.code === 400, 'pageNum 指数应 400');
  check(result.pageNumHex.code === 400, 'pageNum 十六进制应 400');
  check(result.pageNumDecimal.code === 400, 'pageNum 小数应 400');
  check(result.pageNumZero.code === 400, 'pageNum 0 应 400');
  check(result.pageNumMax.code === 200, 'pageNum Integer.MAX_VALUE 应接受');
  check(result.pageNumOverflow.code === 400, 'pageNum 超过 Integer.MAX_VALUE 应 400');
  check(result.pageNumBlank.code === 200, 'pageNum 纯空白应使用默认值');
  check(result.pageNumEmpty.code === 200, 'pageNum 空串应使用默认值');
  check(result.pageNumPlus.code === 200, 'pageNum 正号应接受');
  check(result.pageNumLeadingZero.code === 200, 'pageNum 前导零应接受');
  check(result.pageSizeZero.code === 400, 'pageSize 0 应 400');
  check(result.pageSize101.code === 400, 'pageSize 101 应 400');
  check(result.pageSize100.code === 200, 'pageSize 100 应接受');
  check(result.pageSizeExponent.code === 400, 'pageSize 指数应 400');
  check(result.pageSizeHex.code === 400, 'pageSize 十六进制应 400');
  check(result.pageSizeBlank.code === 200, 'pageSize 纯空白应使用默认值');

  check(result.statusBlank.code === 200 && result.statusEmpty.code === 200, 'status 空值应按无筛选处理');
  check(result.statusLower.code === 200, 'status 小写应接受');
  check(result.statusInvalid.code === 400, 'status 非法值应 400');
  check(result.kindBlank.code === 400 && result.kindWhitespace.code === 400, 'kind 显式空值应 400');
  check(result.kindLower.code === 200, 'kind 小写应接受');
  check(result.kindInvalid.code === 400, 'kind 非法值应 400');
  check(result.unreadEmpty.code === 400 && result.unreadBlank.code === 400, 'unreadOnly 显式空值应 400');
  check(result.unreadUpper.code === 200, 'unreadOnly 大小写不敏感');
  check(result.unreadInvalid.code === 400, 'unreadOnly 非法值应 400');
  check(result.createPlusBinding.code === 200
    && result.createPlusBinding.data.bindingId === '910000000001', '创建公告 bindingId 正号/前导零应规范化');

  check(errors.length === 0, '测试页不应有浏览器错误: ' + errors.join(' | '));

  console.log(JSON.stringify({
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
