/**
 * 6-02 离线预览 Mock 数据层（第 6 组 6B）。
 *
 * 用途：只让 mock-preview.html 在不连后端、不登录的情况下离线自测。
 * 本文件位于 frontend/preview/，不在打包范围 frontend/src/** 内，运行页不引用。
 *
 * 隔离纪律：
 * 1. 开关只存在当前预览页内存，不读写 sessionStorage；运行页的 ?mock=1 或旧
 *    zhijiao.mock=1 不会启用本文件；
 * 2. 字段、业务码、状态机严格对齐 main 分支的 Controller / DTO，
 *    不允许出现"文档里有、后端没有"的字段；
 * 3. 真实请求失败时绝不回退到 Mock，否则会把 500/403 伪装成成功；
 * 4. 预览页切换或清空教学班只改页面内上下文，不影响同标签页真实会话。
 */
(function (global) {
  'use strict';

  var DEMO_BINDING = '910000000001';
  var DENIED_BINDING = '999999999999';
  var UNAVAILABLE_BINDING = '999999999998';

  // 只在本预览页内存生效；不持久化、不随 URL 自动开启。
  var enabled = false;

  var LATENCY_MIN = 120;
  var LATENCY_MAX = 320;

  // ------------------------------------------------------------------
  // 时间与响应封装
  // ------------------------------------------------------------------

  function nowIso() {
    return new Date().toISOString();
  }

  function isoFromNow(minutes) {
    return new Date(Date.now() + minutes * 60 * 1000).toISOString();
  }

  function millis(value) {
    return value ? Date.parse(value) : null;
  }

  function ok(data) {
    return { code: 200, msg: '操作成功', data: data };
  }

  function okPage(rows, total) {
    return { code: 200, msg: '查询成功', total: total, rows: rows };
  }

  function apiError(code, message, method, path) {
    if (global.EduHttp && global.EduHttp.ApiError) {
      return new global.EduHttp.ApiError({
        code: code,
        httpStatus: 200,
        message: message,
        method: method,
        url: path
      });
    }
    var fallback = new Error(message);
    fallback.code = code;
    return fallback;
  }

  function delayed(fn) {
    var wait = LATENCY_MIN + Math.floor(Math.random() * (LATENCY_MAX - LATENCY_MIN));
    return new Promise(function (resolve, reject) {
      global.setTimeout(function () {
        try {
          resolve(fn());
        } catch (error) {
          reject(error);
        }
      }, wait);
    });
  }

  // ------------------------------------------------------------------
  // 内置数据（与后端 DTO 字段一一对应）
  // ------------------------------------------------------------------

  // 草稿公告 ID 不能用 Number 自增：内置 ID 是 18 位十进制字符串，
  // 超过 Number.MAX_SAFE_INTEGER（2^53 - 1）后 +1 不再改变数值，连续创建会拿到同一 ID。
  // 这里用字符串逐位进位，保证越过边界后仍精确递增。
  var announcementSeq = '820000000000000100';
  var noticeSeq = 830000000000000100;

  // TODO(6-02-mock-notice-id): noticeSeq 目前没有通知写入路由，仍是 Number 占位。
  // 原因：本轮只修草稿公告 ID，通知写入/fan-out 不在范围；
  // 解除条件：Mock 新增通知创建接口或需要分配通知 ID 时，改用 incrementDecimalId。

  /**
   * 对十进制字符串 ID 精确加一，不经过 Number。
   *
   * @param {string} value 仅由十进制数字组成的正整数
   * @returns {string} value + 1 的十进制字符串；输入为空或非数字时按 0 处理并返回 '1'
   */
  function incrementDecimalId(value) {
    var digits = String(value || '').trim();
    if (!/^\d+$/.test(digits)) {
      return '1';
    }
    var chars = digits.split('');
    var carry = 1;
    for (var i = chars.length - 1; i >= 0 && carry === 1; i--) {
      var digit = chars[i].charCodeAt(0) - 48 + carry;
      carry = digit >= 10 ? 1 : 0;
      chars[i] = String(digit % 10);
    }
    if (carry === 1) {
      chars.unshift('1');
    }
    return chars.join('');
  }

  /**
   * 分配下一个草稿公告 ID。
   *
   * @returns {string} 递增后的十进制字符串 ID，不经过 Number，越过安全整数边界仍精确
   */
  function nextAnnouncementId() {
    announcementSeq = incrementDecimalId(announcementSeq);
    return announcementSeq;
  }

  var announcements = [
    {
      id: '820000000000000001',
      bindingId: DEMO_BINDING,
      title: '第 5 周随堂测验安排',
      body: '本周四第 3 节在实验楼 A302 进行随堂测验，范围是第 3～4 章。\n请提前 10 分钟到场，携带学生证。',
      status: 'PUBLISHED',
      opensAt: isoFromNow(-3 * 24 * 60),
      closesAt: null,
      createdByUserId: '102',
      publishedByUserId: '102',
      createdAt: isoFromNow(-3 * 24 * 60 - 30)
    },
    {
      id: '820000000000000002',
      bindingId: DEMO_BINDING,
      title: '（草稿）国庆后补课安排',
      body: '拟定于第 6 周周六上午补一次课，具体教室待定。',
      status: 'DRAFT',
      opensAt: null,
      closesAt: null,
      createdByUserId: '102',
      publishedByUserId: null,
      createdAt: isoFromNow(-2 * 24 * 60)
    },
    {
      id: '820000000000000003',
      bindingId: DEMO_BINDING,
      title: '期中项目提交说明（未到开放时间）',
      body: '期中项目以小组为单位提交，截止时间见课程日历。',
      status: 'PUBLISHED',
      opensAt: isoFromNow(2 * 24 * 60),
      closesAt: isoFromNow(10 * 24 * 60),
      createdByUserId: '102',
      publishedByUserId: '102',
      createdAt: isoFromNow(-60)
    },
    {
      id: '820000000000000004',
      bindingId: DEMO_BINDING,
      title: '开学第一课教室调整（已归档）',
      body: '原定 B201 调整至 B305。',
      status: 'ARCHIVED',
      opensAt: isoFromNow(-40 * 24 * 60),
      closesAt: isoFromNow(-20 * 24 * 60),
      createdByUserId: '102',
      publishedByUserId: '102',
      createdAt: isoFromNow(-41 * 24 * 60)
    },
    {
      id: '820000000000000005',
      bindingId: '910000000002',
      title: '实验报告回收提醒（已关闭）',
      body: '实验报告已截止回收，逾期不再受理。',
      status: 'PUBLISHED',
      opensAt: isoFromNow(-10 * 24 * 60),
      closesAt: isoFromNow(-2 * 24 * 60),
      createdByUserId: '102',
      publishedByUserId: '102',
      createdAt: isoFromNow(-11 * 24 * 60)
    },
    {
      id: '820000000000000006',
      bindingId: '910000000002',
      title: '（草稿）课程问卷',
      body: '课程中期问卷，待发布。',
      status: 'DRAFT',
      opensAt: null,
      closesAt: null,
      createdByUserId: '102',
      publishedByUserId: null,
      createdAt: isoFromNow(-90)
    },
    {
      id: '820000000000000007',
      bindingId: '910000000002',
      title: '数据库原理第 6 周上机安排',
      body: '第 6 周上机在机房 C401，按座位表就坐。',
      status: 'PUBLISHED',
      opensAt: isoFromNow(-24 * 60),
      closesAt: null,
      createdByUserId: '102',
      publishedByUserId: '102',
      createdAt: isoFromNow(-25 * 60)
    }
  ];

  var notices = [
    {
      id: '830000000000000001',
      kind: 'ANNOUNCEMENT',
      bindingId: DEMO_BINDING,
      sourceId: '820000000000000001',
      title: '第 5 周随堂测验安排',
      visibleAt: isoFromNow(-3 * 24 * 60),
      readAt: null,
      createdAt: isoFromNow(-3 * 24 * 60)
    },
    {
      id: '830000000000000002',
      kind: 'GRADE',
      bindingId: DEMO_BINDING,
      sourceId: '840000000000000001',
      title: '课后作业成绩已发布',
      visibleAt: isoFromNow(-26 * 60),
      readAt: null,
      createdAt: isoFromNow(-26 * 60)
    },
    {
      id: '830000000000000003',
      kind: 'ASSIGNMENT',
      bindingId: DEMO_BINDING,
      sourceId: '840000000000000002',
      title: '第 5 章练习已布置，截止本周日',
      visibleAt: isoFromNow(-8 * 60),
      readAt: isoFromNow(-5 * 60),
      createdAt: isoFromNow(-8 * 60)
    },
    {
      id: '830000000000000004',
      kind: 'ACTIVITY',
      bindingId: '910000000002',
      sourceId: '840000000000000003',
      title: '课堂投票已开启：选择小组课题',
      visibleAt: isoFromNow(-4 * 60),
      readAt: null,
      createdAt: isoFromNow(-4 * 60)
    },
    {
      id: '830000000000000005',
      kind: 'PREVIEW',
      bindingId: DEMO_BINDING,
      sourceId: '840000000000000004',
      title: '下节课预习材料已上传',
      visibleAt: isoFromNow(-2 * 60),
      readAt: isoFromNow(-1 * 60),
      createdAt: isoFromNow(-2 * 60)
    },
    {
      id: '830000000000000006',
      kind: 'GRADE',
      bindingId: DEMO_BINDING,
      sourceId: '840000000000000005',
      title: '（未来可见）阶段测验成绩',
      visibleAt: isoFromNow(3 * 24 * 60),
      readAt: null,
      createdAt: isoFromNow(-30)
    }
  ];

  // ------------------------------------------------------------------
  // 规则（对齐 AnnouncementStatePolicy / CourseAnnouncementService）
  // ------------------------------------------------------------------

  function visibilityOf(announcement) {
    if (announcement.status === 'DRAFT') {
      return 'DRAFT';
    }
    if (announcement.status === 'ARCHIVED') {
      return 'ARCHIVED';
    }
    var now = Date.now();
    var opensAt = millis(announcement.opensAt);
    if (opensAt === null || now < opensAt) {
      return 'NOT_YET_OPEN';
    }
    var closesAt = millis(announcement.closesAt);
    if (closesAt !== null && now >= closesAt) {
      return 'CLOSED';
    }
    return 'OPEN';
  }

  function toTeacherRow(item, withBody) {
    var row = {
      id: item.id,
      bindingId: item.bindingId,
      title: item.title,
      status: item.status,
      visibilityState: visibilityOf(item),
      opensAt: item.opensAt,
      closesAt: item.closesAt,
      createdByUserId: item.createdByUserId,
      publishedByUserId: item.publishedByUserId,
      createdAt: item.createdAt
    };
    if (withBody) {
      row.body = item.body;
    }
    return row;
  }

  function toStudentRow(item, withBody) {
    var row = {
      id: item.id,
      bindingId: item.bindingId,
      title: item.title,
      opensAt: item.opensAt,
      closesAt: item.closesAt,
      createdAt: item.createdAt
    };
    if (withBody) {
      row.body = item.body;
    }
    return row;
  }

  function toNoticeRow(item) {
    return {
      id: item.id,
      kind: item.kind,
      bindingId: item.bindingId,
      sourceId: item.sourceId,
      title: item.title,
      visibleAt: item.visibleAt,
      readAt: item.readAt,
      isRead: item.readAt !== null,
      createdAt: item.createdAt
    };
  }

  var LONG_MAX_DECIMAL = '9223372036854775807';
  var INT_MAX = 2147483647;
  var STATUS_VALUES = ['DRAFT', 'PUBLISHED', 'ARCHIVED'];
  var KIND_VALUES = ['ANNOUNCEMENT', 'PREVIEW', 'ACTIVITY', 'ASSIGNMENT', 'GRADE'];

  /**
   * 按后端 Long 语义解析正整数 ID，返回规范十进制字符串。
   *
   * 不能用 Number 处理整串 ID：超过 2^53 - 1 后 Number 会丢失低位，
   * 前导零和正号也会在转成数字后失真。这里只去空白、检查正负号/数字，
   * 去前导零后按字符串比较 Long 上界，全程不把整串 ID 转成 Number。
   *
   * @param {string|number} value 原始 ID
   * @param {string} fieldName 字段名，用于错误信息
   * @param {string} method 请求方法
   * @param {string} path 请求路径
   * @param {string} [blankMessage] 空值错误文案
   * @returns {string} 去前导零和正号后的十进制 ID
   */
  function parsePositiveLongId(value, fieldName, method, path, blankMessage) {
    if (value === undefined || value === null || String(value).trim() === '') {
      throw apiError(400, blankMessage || (fieldName + ' 不能为空'), method, path);
    }
    var text = String(value).trim();
    var negative = false;
    if (text.charAt(0) === '+' || text.charAt(0) === '-') {
      negative = text.charAt(0) === '-';
      text = text.slice(1);
    }
    if (!/^\d+$/.test(text)) {
      throw apiError(400, fieldName + ' 必须是正整数', method, path);
    }
    var normalized = text.replace(/^0+(?=\d)/, '');
    if (negative || normalized === '0') {
      throw apiError(400, fieldName + ' 必须是正整数', method, path);
    }
    if (normalized.length > LONG_MAX_DECIMAL.length
      || (normalized.length === LONG_MAX_DECIMAL.length && normalized > LONG_MAX_DECIMAL)) {
      throw apiError(400, fieldName + ' 必须是正整数', method, path);
    }
    return normalized;
  }

  /**
   * 可选正整数 ID：省略/undefined/null 返回 null，显式空串/空白返回 400。
   *
   * 学生接口用这个区分「不传 bindingId」（本人全部教学班）与「显式传空」
   * （后端 optionalPositiveId 直接 400）。
   *
   * @param {string|number|undefined|null} value 原始 ID
   * @param {string} fieldName 字段名
   * @param {string} method 请求方法
   * @param {string} path 请求路径
   * @returns {string|null} 规范十进制 ID 或 null
   */
  function parseOptionalPositiveLongId(value, fieldName, method, path) {
    if (value === undefined || value === null) {
      return null;
    }
    return parsePositiveLongId(value, fieldName, method, path, fieldName + ' 不能为空字符串');
  }

  /**
   * 解码单个路径段并按正 Long 解析，只解码一次。
   *
   * Spring 只把路径变量解码一次；重复解码会把 %252B 变成正号，
   * 与后端行为不一致。解码失败按业务 400 处理。
   *
   * @param {string} encoded 路由捕获的原始路径段
   * @param {string} fieldName 字段名
   * @param {string} method 请求方法
   * @param {string} path 请求路径
   * @returns {string} 规范十进制路径 ID
   */
  function parsePathId(encoded, fieldName, method, path) {
    var decoded;
    try {
      decoded = decodeURIComponent(String(encoded));
    } catch (e) {
      throw apiError(400, fieldName + ' 格式非法', method, path);
    }
    return parsePositiveLongId(decoded, fieldName, method, path);
  }

  /**
   * 按 Integer.parseInt 语义解析分页参数。
   *
   * 缺失/空串/纯空白使用默认值；小数、指数、十六进制和超出 int 的输入返回 400。
   * pageNum 范围为 1..Integer.MAX_VALUE，pageSize 为 1..100。
   *
   * @param {string|number|undefined|null} value 原始参数
   * @param {string} fieldName 字段名
   * @param {number} defaultValue 缺省值
   * @param {number} min 最小值
   * @param {number} max 最大值
   * @param {string} method 请求方法
   * @param {string} path 请求路径
   * @returns {number} 解析后的整数
   */
  function parseIntegerParam(value, fieldName, defaultValue, min, max, method, path) {
    if (value === undefined || value === null || String(value).trim() === '') {
      return defaultValue;
    }
    var text = String(value).trim();
    if (!/^[+-]?\d+$/.test(text)) {
      throw apiError(400, fieldName + ' 必须是整数', method, path);
    }
    var digits = text.replace(/^[+-]/, '').replace(/^0+(?=\d)/, '');
    if (digits.length > 10 || (digits.length === 10 && digits > String(INT_MAX))) {
      throw apiError(400, fieldName + ' 必须是整数', method, path);
    }
    var parsed = Number(text);
    if (parsed < min || parsed > max) {
      if (max === INT_MAX) {
        throw apiError(400, fieldName + ' 不能小于 ' + min, method, path);
      }
      throw apiError(400, fieldName + ' 必须在 ' + min + ' 到 ' + max + ' 之间', method, path);
    }
    return parsed;
  }

  /** 教师 status：null/空白表示不过滤，允许大小写，非法值 400。 */
  function parseOptionalStatus(value, method, path) {
    if (value === undefined || value === null) {
      return null;
    }
    var text = String(value).trim();
    if (text === '') {
      return null;
    }
    var status = text.toUpperCase();
    if (STATUS_VALUES.indexOf(status) < 0) {
      throw apiError(400, 'status 必须是 DRAFT、PUBLISHED 或 ARCHIVED', method, path);
    }
    return status;
  }

  /** 学生通知 kind：null 表示不过滤，显式空值 400，允许大小写。 */
  function parseOptionalKind(value, method, path) {
    if (value === undefined || value === null) {
      return null;
    }
    var text = String(value).trim();
    if (text === '') {
      throw apiError(400, 'kind 不能为空字符串', method, path);
    }
    var kind = text.toUpperCase();
    if (KIND_VALUES.indexOf(kind) < 0) {
      throw apiError(400, 'kind 必须是 ANNOUNCEMENT、PREVIEW、ACTIVITY、ASSIGNMENT 或 GRADE', method, path);
    }
    return kind;
  }

  /** unreadOnly：null 用默认值 false，显式空值 400，true/false 大小写不敏感。 */
  function parseOptionalBoolean(value, fieldName, defaultValue, method, path) {
    if (value === undefined || value === null) {
      return defaultValue;
    }
    var text = String(value).trim();
    if (text === '') {
      throw apiError(400, fieldName + ' 不能为空字符串', method, path);
    }
    if (text.toLowerCase() === 'true') {
      return true;
    }
    if (text.toLowerCase() === 'false') {
      return false;
    }
    throw apiError(400, fieldName + ' 必须是 true 或 false', method, path);
  }

  function ensureAuthorizedBinding(bindingId, method, path) {
    if (bindingId === UNAVAILABLE_BINDING) {
      throw apiError(503, '教学班授权服务暂不可用，请稍后重试', method, path);
    }
    if (bindingId !== DEMO_BINDING && bindingId !== '910000000002') {
      throw apiError(403, '没有该教学班公告的操作权限', method, path);
    }
  }

  function pageParams(query, method, path) {
    var pageNum = parseIntegerParam(query.pageNum, 'pageNum', 1, 1, INT_MAX, method, path);
    var pageSize = parseIntegerParam(query.pageSize, 'pageSize', 10, 1, 100, method, path);
    return { pageNum: pageNum, pageSize: pageSize };
  }

  function paginate(rows, pageNum, pageSize) {
    var start = (pageNum - 1) * pageSize;
    return { total: rows.length, rows: rows.slice(start, start + pageSize) };
  }

  function findAnnouncement(rawId, method, path) {
    var id = parsePathId(rawId, 'announcementId', method, path);
    for (var i = 0; i < announcements.length; i++) {
      if (announcements[i].id === id) {
        return announcements[i];
      }
    }
    throw apiError(404, '公告不存在或不可访问', method, path);
  }

  function validateDraftFields(title, body, opensAt, closesAt, method, path) {
    if (typeof title !== 'string' || title.trim() === '') {
      throw apiError(400, '标题不能为空', method, path);
    }
    if (Array.from(title).length > 200) {
      throw apiError(400, '标题最多 200 字', method, path);
    }
    if (typeof body !== 'string' || body.trim() === '') {
      throw apiError(400, '正文不能为空', method, path);
    }
    if (Array.from(body).length > 20000) {
      throw apiError(400, '正文超过 20000 字', method, path);
    }
    if (opensAt && closesAt && millis(closesAt) <= millis(opensAt)) {
      throw apiError(400, '关闭时间必须晚于开放时间', method, path);
    }
  }

  // ------------------------------------------------------------------
  // 路由
  // ------------------------------------------------------------------

  var routes = [];

  function route(method, pattern, handler) {
    routes.push({ method: method, pattern: pattern, handler: handler });
  }

  // --- 教师公告 ---

  route('GET', /^\/education\/teacher\/announcements$/, function (ctx) {
    var bindingId = parsePositiveLongId(ctx.query.bindingId, 'bindingId', ctx.method, ctx.path);
    var status = parseOptionalStatus(ctx.query.status, ctx.method, ctx.path);
    var page = pageParams(ctx.query, ctx.method, ctx.path);
    ensureAuthorizedBinding(bindingId, ctx.method, ctx.path);
    var rows = announcements
      .filter(function (item) {
        return item.bindingId === bindingId && (!status || item.status === status);
      })
      .sort(function (a, b) {
        return millis(b.createdAt) - millis(a.createdAt);
      })
      .map(function (item) {
        return toTeacherRow(item, false);
      });
    var sliced = paginate(rows, page.pageNum, page.pageSize);
    return okPage(sliced.rows, sliced.total);
  });

  route('GET', /^\/education\/teacher\/announcements\/([^/]+)$/, function (ctx) {
    var item = findAnnouncement(ctx.params[0], ctx.method, ctx.path);
    ensureAuthorizedBinding(item.bindingId, ctx.method, ctx.path);
    return ok(toTeacherRow(item, true));
  });

  route('POST', /^\/education\/teacher\/announcements$/, function (ctx) {
    var body = ctx.body || {};
    var bindingId = parsePositiveLongId(body.bindingId, 'bindingId', ctx.method, ctx.path);
    ensureAuthorizedBinding(bindingId, ctx.method, ctx.path);
    validateDraftFields(body.title, body.body, body.opensAt, body.closesAt, ctx.method, ctx.path);
    var item = {
      id: nextAnnouncementId(),
      bindingId: bindingId,
      title: body.title.trim(),
      body: body.body,
      status: 'DRAFT',
      opensAt: body.opensAt || null,
      closesAt: body.closesAt || null,
      createdByUserId: '102',
      publishedByUserId: null,
      createdAt: nowIso()
    };
    announcements.unshift(item);
    return ok(toTeacherRow(item, true));
  });

  route('PUT', /^\/education\/teacher\/announcements\/([^/]+)$/, function (ctx) {
    var item = findAnnouncement(ctx.params[0], ctx.method, ctx.path);
    ensureAuthorizedBinding(item.bindingId, ctx.method, ctx.path);
    if (item.status !== 'DRAFT') {
      throw apiError(409, '只有草稿可以编辑，请刷新后重试', ctx.method, ctx.path);
    }
    var body = ctx.body || {};
    validateDraftFields(body.title, body.body, body.opensAt, body.closesAt, ctx.method, ctx.path);
    item.title = body.title.trim();
    item.body = body.body;
    item.opensAt = body.opensAt || null;
    item.closesAt = body.closesAt || null;
    return ok(toTeacherRow(item, true));
  });

  route('POST', /^\/education\/teacher\/announcements\/([^/]+)\/publish$/, function (ctx) {
    var item = findAnnouncement(ctx.params[0], ctx.method, ctx.path);
    ensureAuthorizedBinding(item.bindingId, ctx.method, ctx.path);
    if (item.status === 'PUBLISHED') {
      return ok(toTeacherRow(item, true));
    }
    if (item.status !== 'DRAFT') {
      throw apiError(409, '只有草稿可以发布；已归档公告不能再次发布', ctx.method, ctx.path);
    }
    var effectiveOpensAt = item.opensAt || nowIso();
    if (item.closesAt && millis(item.closesAt) <= millis(effectiveOpensAt)) {
      throw apiError(400, '关闭时间必须晚于开始时间', ctx.method, ctx.path);
    }
    item.status = 'PUBLISHED';
    item.opensAt = effectiveOpensAt;
    item.publishedByUserId = '102';
    return ok(toTeacherRow(item, true));
  });

  route('POST', /^\/education\/teacher\/announcements\/([^/]+)\/archive$/, function (ctx) {
    var item = findAnnouncement(ctx.params[0], ctx.method, ctx.path);
    ensureAuthorizedBinding(item.bindingId, ctx.method, ctx.path);
    if (item.status === 'ARCHIVED') {
      return ok(toTeacherRow(item, true));
    }
    if (item.status !== 'PUBLISHED') {
      throw apiError(409, '只有已发布公告可以归档', ctx.method, ctx.path);
    }
    item.status = 'ARCHIVED';
    return ok(toTeacherRow(item, true));
  });

  // --- 学生公告（只返回 OPEN） ---

  route('GET', /^\/education\/student\/announcements$/, function (ctx) {
    // 与后端一致：只有"完全不传"（undefined/null）才表示本人全部班级；
    // 空串/纯空白是显式空值，requiredId 返回 400。
    var rawBinding = ctx.query.bindingId;
    var bindingId = rawBinding === undefined || rawBinding === null
      ? null
      : parsePositiveLongId(rawBinding, 'bindingId', ctx.method, ctx.path);
    var page = pageParams(ctx.query, ctx.method, ctx.path);
    if (bindingId) {
      ensureAuthorizedBinding(bindingId, ctx.method, ctx.path);
    }
    var rows = announcements
      .filter(function (item) {
        return visibilityOf(item) === 'OPEN' && (!bindingId || item.bindingId === bindingId);
      })
      .sort(function (a, b) {
        return millis(b.opensAt) - millis(a.opensAt);
      })
      .map(function (item) {
        return toStudentRow(item, false);
      });
    var sliced = paginate(rows, page.pageNum, page.pageSize);
    return okPage(sliced.rows, sliced.total);
  });

  route('GET', /^\/education\/student\/announcements\/([^/]+)$/, function (ctx) {
    var item = findAnnouncement(ctx.params[0], ctx.method, ctx.path);
    if (visibilityOf(item) !== 'OPEN') {
      throw apiError(404, '公告不存在或不可访问', ctx.method, ctx.path);
    }
    return ok(toStudentRow(item, true));
  });

  // --- 学生通知 ---

  function visibleNotices() {
    var now = Date.now();
    return notices.filter(function (item) {
      return millis(item.visibleAt) <= now;
    });
  }

  route('GET', /^\/education\/student\/notices$/, function (ctx) {
    var bindingId = parseOptionalPositiveLongId(ctx.query.bindingId, 'bindingId', ctx.method, ctx.path);
    var kind = parseOptionalKind(ctx.query.kind, ctx.method, ctx.path);
    var unreadOnly = parseOptionalBoolean(ctx.query.unreadOnly, 'unreadOnly', false, ctx.method, ctx.path);
    var page = pageParams(ctx.query, ctx.method, ctx.path);
    if (bindingId) {
      ensureAuthorizedBinding(bindingId, ctx.method, ctx.path);
    }
    var rows = visibleNotices()
      .filter(function (item) {
        return (!kind || item.kind === kind)
          && (!bindingId || item.bindingId === bindingId)
          && (!unreadOnly || item.readAt === null);
      })
      .sort(function (a, b) {
        return millis(b.visibleAt) - millis(a.visibleAt);
      })
      .map(toNoticeRow);
    var sliced = paginate(rows, page.pageNum, page.pageSize);
    return okPage(sliced.rows, sliced.total);
  });

  route('GET', /^\/education\/student\/notices\/unread-count$/, function (ctx) {
    var bindingId = parseOptionalPositiveLongId(ctx.query.bindingId, 'bindingId', ctx.method, ctx.path);
    if (bindingId) {
      ensureAuthorizedBinding(bindingId, ctx.method, ctx.path);
    }
    var count = visibleNotices().filter(function (item) {
      return item.readAt === null && (!bindingId || item.bindingId === bindingId);
    }).length;
    return ok({ unreadCount: count, asOf: nowIso() });
  });

  route('GET', /^\/education\/student\/notices\/([^/]+)$/, function (ctx) {
    var id = parsePathId(ctx.params[0], 'noticeId', ctx.method, ctx.path);
    for (var i = 0; i < notices.length; i++) {
      if (notices[i].id === id && millis(notices[i].visibleAt) <= Date.now()) {
        return ok(toNoticeRow(notices[i]));
      }
    }
    throw apiError(404, '通知不存在或当前不可访问', ctx.method, ctx.path);
  });

  route('POST', /^\/education\/student\/notices\/([^/]+)\/read$/, function (ctx) {
    var id = parsePathId(ctx.params[0], 'noticeId', ctx.method, ctx.path);
    for (var i = 0; i < notices.length; i++) {
      if (notices[i].id === id && millis(notices[i].visibleAt) <= Date.now()) {
        var item = notices[i];
        var alreadyRead = item.readAt !== null;
        if (!alreadyRead) {
          item.readAt = nowIso();
        }
        return ok({ id: item.id, readAt: item.readAt, alreadyRead: alreadyRead });
      }
    }
    throw apiError(404, '通知不存在或当前不可访问', ctx.method, ctx.path);
  });

  route('PUT', /^\/education\/student\/notices\/read-all$/, function (ctx) {
    var operatedAt = nowIso();
    var markedCount = 0;
    visibleNotices().forEach(function (item) {
      if (item.readAt === null) {
        item.readAt = operatedAt;
        markedCount += 1;
      }
    });
    var unread = visibleNotices().filter(function (item) {
      return item.readAt === null;
    }).length;
    return ok({ markedCount: markedCount, unreadCount: unread, operatedAt: operatedAt });
  });

  // --- 6-03 总览（显式 Mock；字段与 #24 DTO 对齐） ---

  var overviewScenario = 'normal';
  var overviewDelayMs = null;

  var OVERVIEW_DEFS = {
    teacher: [
      { code: 'teacher.class.count', source: 'course', actionPath: '/teacher/classes' },
      { code: 'teacher.student.count', source: 'course', actionPath: '/teacher/classes' },
      { code: 'teacher.session.running', source: 'classroom', actionPath: '/teacher/classes' },
      { code: 'teacher.assignment.pending', source: 'assignment', actionPath: '/teacher/assignments' },
      { code: 'teacher.grade.pending', source: 'grade', actionPath: '/teacher/grades' },
      { code: 'teacher.notice.unread', source: 'notice', actionPath: '/teacher/notices' }
    ],
    student: [
      { code: 'student.class.count', source: 'course', actionPath: '/student/classes' },
      { code: 'student.session.active', source: 'classroom', actionPath: '/student/todos' },
      { code: 'student.assignment.due', source: 'assignment', actionPath: '/student/todos' },
      { code: 'student.notice.unread', source: 'notice', actionPath: '/student/notices' }
    ],
    admin: [
      { code: 'admin.class.count', source: 'course', actionPath: '/admin/classes' },
      { code: 'admin.student.count', source: 'course', actionPath: '/admin/classes' },
      { code: 'admin.session.running', source: 'classroom', actionPath: '/admin/classes' },
      { code: 'admin.assignment.pending', source: 'assignment', actionPath: '/admin/assignments' },
      { code: 'admin.grade.pending', source: 'grade', actionPath: '/admin/grades' }
    ],
    adminTodos: [
      { code: 'admin.todo.pending', source: 'pending_product_confirmation', actionPath: null }
    ]
  };

  var OVERVIEW_SAMPLES = {
    'teacher.class.count': 3,
    'teacher.student.count': 3,
    'teacher.session.running': 3,
    'teacher.assignment.pending': 4,
    'teacher.grade.pending': 3,
    'teacher.notice.unread': null,
    'student.class.count': 2,
    'student.session.active': 2,
    'student.assignment.due': 3,
    'student.notice.unread': null,
    'admin.class.count': 12,
    'admin.student.count': 12,
    'admin.session.running': 12,
    'admin.assignment.pending': 12,
    'admin.grade.pending': 12,
    'admin.todo.pending': null
  };

  function overviewNormalValues(role, classId) {
    if (role === 'teacher') {
      return {
        'teacher.class.count': classId ? 9 : 3,
        'teacher.student.count': classId ? 40 : 86,
        'teacher.session.running': classId ? 2 : 1,
        'teacher.assignment.pending': classId ? 7 : 12,
        'teacher.grade.pending': classId ? 3 : 5,
        'teacher.notice.unread': classId ? 1 : 4
      };
    }
    if (role === 'student') {
      return {
        'student.class.count': 2,
        'student.session.active': 1,
        'student.assignment.due': 3,
        'student.notice.unread': 0
      };
    }
    if (role === 'admin') {
      return {
        'admin.class.count': 12,
        'admin.student.count': 420,
        'admin.session.running': 2,
        'admin.assignment.pending': 37,
        'admin.grade.pending': 18
      };
    }
    return { 'admin.todo.pending': null };
  }

  function overviewZeroValues(role) {
    if (role === 'teacher') {
      return {
        'teacher.class.count': 3,
        'teacher.student.count': 0,
        'teacher.session.running': 0,
        'teacher.assignment.pending': 0,
        'teacher.grade.pending': 0,
        'teacher.notice.unread': 0
      };
    }
    if (role === 'student') {
      return {
        'student.class.count': 2,
        'student.session.active': 0,
        // 学生 assignment.due 空列表是 NO_DATA，没有 OK 0。
        'student.assignment.due': null,
        'student.notice.unread': 0
      };
    }
    if (role === 'admin') {
      return {
        'admin.class.count': 12,
        'admin.student.count': 0,
        'admin.session.running': 0,
        'admin.assignment.pending': 0,
        'admin.grade.pending': 0
      };
    }
    return { 'admin.todo.pending': null };
  }

  function overviewMixedValues(role) {
    if (role === 'teacher') {
      return {
        'teacher.class.count': ['OK', 3],
        'teacher.student.count': ['UNAVAILABLE', null],
        'teacher.session.running': ['OK', 0],
        'teacher.assignment.pending': ['NO_DATA', null],
        'teacher.grade.pending': ['UNAVAILABLE', null],
        'teacher.notice.unread': ['OK', 4]
      };
    }
    if (role === 'student') {
      return {
        'student.class.count': ['OK', 2],
        'student.session.active': ['UNAVAILABLE', null],
        'student.assignment.due': ['OK', 3],
        'student.notice.unread': ['NO_DATA', null]
      };
    }
    if (role === 'admin') {
      return {
        'admin.class.count': ['OK', 12],
        'admin.student.count': ['NO_DATA', null],
        'admin.session.running': ['UNAVAILABLE', null],
        'admin.assignment.pending': ['OK', 37],
        'admin.grade.pending': ['OK', 0]
      };
    }
    return { 'admin.todo.pending': ['NO_DATA', null] };
  }

  function overviewWindow(ctx) {
    var from = ctx.query.from;
    var to = ctx.query.to;
    if ((from && !to) || (!from && to)) {
      throw apiError(400, 'from 和 to 必须同时提供', ctx.method, ctx.path);
    }
    if (from && to) {
      var fromMs = Date.parse(from);
      var toMs = Date.parse(to);
      if (isNaN(fromMs) || isNaN(toMs) || fromMs >= toMs) {
        throw apiError(400, '时间窗不合法：必须为 RFC 3339，且 to 晚于 from', ctx.method, ctx.path);
      }
      return { startsAt: new Date(fromMs).toISOString(), endsAt: new Date(toMs).toISOString() };
    }
    var now = new Date();
    var start = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
    return {
      startsAt: new Date(start).toISOString(),
      endsAt: new Date(start + 24 * 60 * 60 * 1000).toISOString()
    };
  }

  function overviewItem(def, status, value, sample) {
    return {
      metricCode: def.code,
      value: status === 'OK' ? value : null,
      unit: 'COUNT',
      status: status,
      source: def.source,
      sampleCount: status === 'OK' && sample !== undefined ? sample : null,
      actionPath: def.actionPath
    };
  }

  function buildOverviewResponse(role, ctx) {
    if (overviewScenario === 'error401') {
      throw apiError(401, '登录状态已失效（Mock）', ctx.method, ctx.path);
    }
    if (overviewScenario === 'error403') {
      throw apiError(403, '当前账号无权查看该总览范围', ctx.method, ctx.path);
    }
    if (overviewScenario === 'error400') {
      throw apiError(400, '筛选参数不合法（Mock）', ctx.method, ctx.path);
    }
    if (overviewScenario === 'error404') {
      throw apiError(404, '总览接口未部署（Mock）', ctx.method, ctx.path);
    }
    if (overviewScenario === 'error500') {
      throw apiError(500, '服务端异常（Mock）', ctx.method, ctx.path);
    }
    if (overviewScenario === 'network') {
      throw apiError(0, '网络异常（Mock）', ctx.method, ctx.path);
    }
    if (overviewScenario === 'adminTodosError' && role === 'adminTodos') {
      throw apiError(500, '管理员待办读取失败（Mock）', ctx.method, ctx.path);
    }
    if (overviewScenario === 'missing-data') {
      return { code: 200, msg: '操作成功', data: null };
    }

    var window = overviewWindow(ctx);
    var defs = OVERVIEW_DEFS[role];
    var normal = role === 'adminTodos' ? { 'admin.todo.pending': null }
      : overviewNormalValues(role, ctx.query.classId);
    var items = [];

    if (overviewScenario === 'empty-items') {
      items = [];
    } else if (role === 'adminTodos') {
      items = overviewScenario === 'adminTodoOkZero'
        ? [overviewItem(defs[0], 'OK', 0, 0)]
        : [overviewItem(defs[0], 'NO_DATA', null, null)];
    } else if (overviewScenario === 'nodata') {
      items = defs.map(function (def) { return overviewItem(def, 'NO_DATA', null, null); });
    } else if (overviewScenario === 'unavailable') {
      items = defs.map(function (def) { return overviewItem(def, 'UNAVAILABLE', null, null); });
    } else if (overviewScenario === 'mixed') {
      var mixed = overviewMixedValues(role);
      items = defs.map(function (def) {
        var state = mixed[def.code] || ['NO_DATA', null];
        return overviewItem(def, state[0], state[1], OVERVIEW_SAMPLES[def.code]);
      });
    } else if (overviewScenario === 'zero') {
      var zero = overviewZeroValues(role);
      items = defs.map(function (def) {
        if (role === 'student' && def.code === 'student.assignment.due') {
          return overviewItem(def, 'NO_DATA', null, null);
        }
        return overviewItem(def, 'OK', zero[def.code], OVERVIEW_SAMPLES[def.code]);
      });
    } else if (overviewScenario === 'missing') {
      var omit = role === 'teacher' ? 'teacher.grade.pending'
        : role === 'student' ? 'student.assignment.due' : 'admin.grade.pending';
      items = defs.filter(function (def) { return def.code !== omit; }).map(function (def) {
        return overviewItem(def, 'OK', normal[def.code], OVERVIEW_SAMPLES[def.code]);
      });
    } else if (overviewScenario === 'invalid') {
      items = defs.map(function (def, index) {
        if (index === 0) {
          return {
            metricCode: def.code,
            value: null,
            unit: 'COUNT',
            status: 'OK',
            source: def.source,
            sampleCount: null,
            actionPath: def.actionPath
          };
        }
        if (index === 1) {
          return {
            metricCode: def.code,
            value: 5,
            unit: 'COUNT',
            status: 'NO_DATA',
            source: def.source,
            sampleCount: null,
            actionPath: def.actionPath
          };
        }
        return overviewItem(def, 'OK', normal[def.code], OVERVIEW_SAMPLES[def.code]);
      });
    } else {
      items = defs.map(function (def) {
        return overviewItem(def, 'OK', normal[def.code], OVERVIEW_SAMPLES[def.code]);
      });
    }

    return ok({ asOf: nowIso(), window: window, items: items });
  }

  function overviewDelayed(fn) {
    var wait = overviewDelayMs === null
      ? LATENCY_MIN + Math.floor(Math.random() * (LATENCY_MAX - LATENCY_MIN))
      : overviewDelayMs;
    return new Promise(function (resolve, reject) {
      global.setTimeout(function () {
        try {
          resolve(fn());
        } catch (error) {
          reject(error);
        }
      }, wait);
    });
  }

  function overviewHandler(role, ctx) {
    // 在请求进入时构建响应，保证并发乱序测试看到的是各自请求时刻的数据。
    var response = buildOverviewResponse(role, ctx);
    return overviewDelayed(function () { return response; });
  }

  route('GET', /^\/education\/teacher\/overview$/, function (ctx) {
    return overviewHandler('teacher', ctx);
  });

  route('GET', /^\/education\/student\/today-todos$/, function (ctx) {
    return overviewHandler('student', ctx);
  });

  route('GET', /^\/education\/admin\/overview$/, function (ctx) {
    return overviewHandler('admin', ctx);
  });

  route('GET', /^\/education\/admin\/todos$/, function (ctx) {
    return overviewHandler('adminTodos', ctx);
  });

  // --- 课程—教学班上下文（C-1，2 组未交付，Mock 先给演示数据） ---

  route('GET', /^\/education\/(teacher|student)\/bindings$/, function () {
    return ok([
      { bindingId: DEMO_BINDING, courseName: '数据结构', className: '2025级计算机1班', term: '2026-2027-1' },
      { bindingId: '910000000002', courseName: '数据库原理', className: '2025级软件2班', term: '2026-2027-1' }
    ]);
  });

  // ------------------------------------------------------------------
  // 对外接口
  // ------------------------------------------------------------------

  function match(method, path) {
    for (var i = 0; i < routes.length; i++) {
      var item = routes[i];
      if (item.method !== method) {
        continue;
      }
      var found = item.pattern.exec(path);
      if (found) {
        return { handler: item.handler, params: found.slice(1) };
      }
    }
    return null;
  }

  /**
   * 返回当前预览页内存中的 Mock 开关状态。
   *
   * @returns {boolean} 当前页面已显式开启 Mock 时返回 true；页面刷新后默认 false
   */
  function isEnabled() {
    return enabled;
  }

  /**
   * 在当前预览页内存中开启 Mock，并挂载预览横幅。
   *
   * 不写 sessionStorage，也不读取 URL/旧存储开关；因此同标签页转去运行页时，
   * 预览开关不会残留或改变真实登录态与教学班上下文。
   *
   * @returns {void}
   */
  function enable() {
    enabled = true;
    mountBadge();
  }

  /**
   * 在当前预览页内关闭 Mock，并移除预览横幅。
   *
   * @returns {void}
   */
  function disable() {
    enabled = false;
    var banner = document.getElementById('eduMockBanner');
    if (banner && banner.parentNode) {
      banner.parentNode.removeChild(banner);
    }
  }

  function handles(method, path) {
    return isEnabled() && match(method, path) !== null;
  }

  /**
   * 处理已开启 Mock 的预览请求。
   *
   * @param {{method?:string,path:string,query?:object,body?:object}} options 请求选项
   * @returns {Promise<object>} 命中内置路由时返回与后端同形的响应；
   *   未覆盖路由返回拒绝的 404，不回退真实请求
   */
  function request(options) {
    var opts = options || {};
    var method = (opts.method || 'GET').toUpperCase();
    var path = opts.path || '';
    var found = match(method, path);
    if (!found) {
      return Promise.reject(apiError(404, 'Mock 未覆盖该接口：' + method + ' ' + path, method, path));
    }
    var ctx = {
      method: method,
      path: path,
      query: opts.query || {},
      body: opts.body || {},
      params: found.params
    };
    // 总览请求由 handler 自己控制延迟；这样并发乱序测试能在请求进入时
    // 固定各自的数据快照，不被外层随机延迟改变顺序。
    if (/^\/education\/(teacher\/overview|student\/today-todos|admin\/overview|admin\/todos)$/.test(path)) {
      try {
        return Promise.resolve(found.handler(ctx));
      } catch (error) {
        return Promise.reject(error);
      }
    }
    return delayed(function () {
      return found.handler(ctx);
    });
  }

  function mountBadge() {
    if (!isEnabled() || typeof document === 'undefined') {
      return;
    }
    if (document.getElementById('eduMockBanner')) {
      return;
    }
    var banner = document.createElement('div');
    banner.id = 'eduMockBanner';
    banner.className = 'edu-mock-banner';

    var label = document.createElement('span');
    label.textContent = '离线预览 · 未连接后端';
    var hint = document.createElement('span');
    hint.className = 'edu-mock-hint';
    hint.textContent = '数据仅在本页内存';
    var resetButton = document.createElement('button');
    resetButton.type = 'button';
    resetButton.textContent = '重置预览数据';
    resetButton.addEventListener('click', function () {
      reset();
      global.location.reload();
    });

    banner.appendChild(label);
    banner.appendChild(hint);
    banner.appendChild(resetButton);
    document.body.appendChild(banner);
  }

  /**
   * 把预览数据恢复到初始演示集，只影响当前页面内存。
   *
   * @returns {void}
   */
  function reset() {
    announcements = announcements.slice(0, 7);
    notices = notices.slice(0, 6);
    notices.forEach(function (item) {
      if (item.id === '830000000000000001' || item.id === '830000000000000002'
        || item.id === '830000000000000004') {
        item.readAt = null;
      }
    });
    overviewScenario = 'normal';
    overviewDelayMs = null;
  }

  /**
   * 切换总览 Mock 场景。
   *
   * @param {string} scenario normal|zero|nodata|unavailable|mixed|missing|invalid|
   *   empty-items|missing-data|error401|error403|error400|error404|error500|network|
   *   adminTodoOkZero|adminTodosError
   */
  function setOverviewScenario(scenario) {
    overviewScenario = scenario || 'normal';
  }

  /**
   * 设置总览 Mock 延迟，仅用于预览/并发测试；null 恢复随机延迟。
   *
   * @param {number|null} ms 毫秒
   */
  function setOverviewDelay(ms) {
    overviewDelayMs = (typeof ms === 'number' && ms >= 0) ? ms : null;
  }

  if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', mountBadge);
    } else {
      mountBadge();
    }
  }

  global.EduMock = {
    enabled: isEnabled,
    enable: enable,
    disable: disable,
    handles: handles,
    request: request,
    reset: reset,
    setOverviewScenario: setOverviewScenario,
    setOverviewDelay: setOverviewDelay,
    bindings: [DEMO_BINDING, '910000000002'],
    deniedBinding: DENIED_BINDING,
    unavailableBinding: UNAVAILABLE_BINDING
  };
})(window);
