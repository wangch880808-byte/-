/**
 * 6-02 前端 Mock 数据层（第 6 组 6B）。
 *
 * 用途：后端通知接口未就绪时，让页面能在不连后端的情况下自测。
 *
 * 三条纪律：
 * 1. 只有显式开启才生效（URL 加 ?mock=1 或调用 EduMock.enable()），默认关闭；
 * 2. 字段、业务码、状态机严格对齐 main 分支的 Controller / DTO，
 *    不允许出现"文档里有、后端没有"的字段；
 * 3. 真实请求失败时绝不回退到 Mock，否则会把 500/403 伪装成成功。
 */
(function (global) {
  'use strict';

  var FLAG_KEY = 'zhijiao.mock';
  var BINDING_KEY = 'zhijiao.bindingId';
  var DEMO_BINDING = '910000000001';
  var DENIED_BINDING = '999999999999';
  var UNAVAILABLE_BINDING = '999999999998';

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

  var announcementSeq = 820000000000000100;
  var noticeSeq = 830000000000000100;

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

  function requireBindingId(value, method, path) {
    if (value === undefined || value === null || String(value).trim() === '') {
      throw apiError(400, 'bindingId 不能为空', method, path);
    }
    if (!/^\d+$/.test(String(value).trim())) {
      throw apiError(400, 'bindingId 必须是正整数', method, path);
    }
    return String(value).trim();
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
    var pageNum = query.pageNum === undefined || query.pageNum === '' ? 1 : Number(query.pageNum);
    var pageSize = query.pageSize === undefined || query.pageSize === '' ? 10 : Number(query.pageSize);
    if (!Number.isInteger(pageNum) || pageNum < 1) {
      throw apiError(400, 'pageNum 不能小于 1', method, path);
    }
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100) {
      throw apiError(400, 'pageSize 必须在 1 到 100 之间', method, path);
    }
    return { pageNum: pageNum, pageSize: pageSize };
  }

  function paginate(rows, pageNum, pageSize) {
    var start = (pageNum - 1) * pageSize;
    return { total: rows.length, rows: rows.slice(start, start + pageSize) };
  }

  function findAnnouncement(id, method, path) {
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
    var bindingId = requireBindingId(ctx.query.bindingId, ctx.method, ctx.path);
    ensureAuthorizedBinding(bindingId, ctx.method, ctx.path);
    var page = pageParams(ctx.query, ctx.method, ctx.path);
    var status = ctx.query.status ? String(ctx.query.status).toUpperCase() : '';
    if (status && ['DRAFT', 'PUBLISHED', 'ARCHIVED'].indexOf(status) < 0) {
      throw apiError(400, 'status 必须是 DRAFT、PUBLISHED 或 ARCHIVED', ctx.method, ctx.path);
    }
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
    var bindingId = requireBindingId(body.bindingId, ctx.method, ctx.path);
    ensureAuthorizedBinding(bindingId, ctx.method, ctx.path);
    validateDraftFields(body.title, body.body, body.opensAt, body.closesAt, ctx.method, ctx.path);
    var item = {
      id: String(++announcementSeq),
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
    var page = pageParams(ctx.query, ctx.method, ctx.path);
    var bindingId = ctx.query.bindingId ? String(ctx.query.bindingId) : '';
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
    var page = pageParams(ctx.query, ctx.method, ctx.path);
    var kind = ctx.query.kind ? String(ctx.query.kind) : '';
    if (kind && ['ANNOUNCEMENT', 'PREVIEW', 'ACTIVITY', 'ASSIGNMENT', 'GRADE'].indexOf(kind) < 0) {
      throw apiError(400, 'kind 不在允许的枚举内', ctx.method, ctx.path);
    }
    var bindingId = ctx.query.bindingId ? String(ctx.query.bindingId) : '';
    if (bindingId) {
      ensureAuthorizedBinding(bindingId, ctx.method, ctx.path);
    }
    var unreadOnly = String(ctx.query.unreadOnly) === 'true';
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
    var bindingId = ctx.query.bindingId ? String(ctx.query.bindingId) : '';
    if (bindingId) {
      ensureAuthorizedBinding(bindingId, ctx.method, ctx.path);
    }
    var count = visibleNotices().filter(function (item) {
      return item.readAt === null && (!bindingId || item.bindingId === bindingId);
    }).length;
    return ok({ unreadCount: count, asOf: nowIso() });
  });

  route('GET', /^\/education\/student\/notices\/([^/]+)$/, function (ctx) {
    var id = ctx.params[0];
    for (var i = 0; i < notices.length; i++) {
      if (notices[i].id === id && millis(notices[i].visibleAt) <= Date.now()) {
        return ok(toNoticeRow(notices[i]));
      }
    }
    throw apiError(404, '通知不存在或当前不可访问', ctx.method, ctx.path);
  });

  route('POST', /^\/education\/student\/notices\/([^/]+)\/read$/, function (ctx) {
    var id = ctx.params[0];
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

  function isEnabled() {
    try {
      return global.sessionStorage.getItem(FLAG_KEY) === '1';
    } catch (e) {
      return false;
    }
  }

  function enable() {
    try {
      global.sessionStorage.setItem(FLAG_KEY, '1');
      if (!global.sessionStorage.getItem(BINDING_KEY)) {
        global.sessionStorage.setItem(BINDING_KEY, DEMO_BINDING);
      }
    } catch (e) {
      /* 存储不可用时只能依赖于页面内已开启 */
    }
    mountBadge();
  }

  function disable() {
    try {
      global.sessionStorage.removeItem(FLAG_KEY);
    } catch (e) {
      /* 忽略 */
    }
  }

  function handles(method, path) {
    return isEnabled() && match(method, path) !== null;
  }

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
    label.textContent = 'Mock 数据 · 未连接后端';
    var hint = document.createElement('span');
    hint.className = 'edu-mock-hint';
    hint.textContent = '演示上下文 ' + DEMO_BINDING;
    var exit = document.createElement('button');
    exit.type = 'button';
    exit.textContent = '退出 Mock';
    exit.addEventListener('click', function () {
      disable();
      global.location.replace(global.location.pathname);
    });

    banner.appendChild(label);
    banner.appendChild(hint);
    banner.appendChild(exit);
    document.body.appendChild(banner);
  }

  function reset() {
    announcements = announcements.slice(0, 6);
    notices = notices.slice(0, 6);
    notices.forEach(function (item) {
      if (item.id === '830000000000000001' || item.id === '830000000000000002'
        || item.id === '830000000000000004') {
        item.readAt = null;
      }
    });
  }

  if (typeof global.location !== 'undefined' && global.location.search) {
    var search = global.location.search;
    if (/[?&]mock=1(&|$)/.test(search)) {
      enable();
    } else if (/[?&]mock=0(&|$)/.test(search)) {
      disable();
    }
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
    bindings: [DEMO_BINDING, '910000000002'],
    deniedBinding: DENIED_BINDING,
    unavailableBinding: UNAVAILABLE_BINDING
  };
})(window);
