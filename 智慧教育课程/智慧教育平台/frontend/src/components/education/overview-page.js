/**
 * 6-03 三端总览共享页面组件（第 6 组 6A/6B）。
 *
 * 职责：
 * - 按配置渲染教师/学生/管理员总览卡片；
 * - 校验 #24 响应结构、三态、value 与 status 组合；
 * - 处理时间窗、教师 classId、刷新、错误态和单卡降级；
 * - 用 requestSeq/disposed 防止旧响应覆盖新筛选，cleanup 后不再写页面。
 *
 * 不在本文件：
 * - 不补算指标、不修改服务端 window/asOf、不直读 actionPath 跳转；
 * - 不保存登录态，不伪造身份，不把 Mock 结果写入真实页面。
 *
 * 配置结构：
 * {
 *   endpoint: 'teacher'|'student'|'admin'|'adminTodos',
 *   timeFilter: boolean,
 *   classFilter: boolean,
 *   showWindow: boolean,
 *   groups: [{ key, label, customLabel? }],
 *   metrics: [{
 *     code, group, title, note, source,
 *     unitLabel, windowed, noDataText?, navigation?
 *   }]
 * }
 */
(function (global) {
  'use strict';

  var UI = global.EduUI;
  var Views = global.EduViews;

  var STATUS_OK = 'OK';
  var STATUS_NO_DATA = 'NO_DATA';
  var STATUS_UNAVAILABLE = 'UNAVAILABLE';

  var SOURCE_LABEL = {
    course: '课程范围',
    classroom: '课堂',
    assignment: '任务',
    grade: '成绩',
    notice: '通知',
    pending_product_confirmation: '产品待确认'
  };

  function esc(value) {
    return UI.esc(value);
  }

  function isNonNegativeInteger(value) {
    return typeof value === 'number' && isFinite(value)
      && Math.floor(value) === value && value >= 0;
  }

  function isInstant(value) {
    return typeof value === 'string' && value !== '' && !isNaN(Date.parse(value));
  }

  function formatTime(value) {
    return UI.formatTime(value);
  }

  /**
   * 创建一个可注册到 EduViews 的总览视图工厂。
   *
   * @param {object} config 页面配置
   * @returns {Function} view-host 工厂：接收 {root, bindingId, label}
   */
  function create(config) {
    if (!config || !config.endpoint || !Array.isArray(config.metrics)) {
      throw new Error('总览页面配置缺少 endpoint 或 metrics');
    }
    return function (ctx) {
      return mountView(ctx, config);
    };
  }

  function mountView(ctx, config) {
    var root = ctx && ctx.root;
    if (!root) {
      throw new Error('总览视图缺少根节点');
    }
    var api = global.EduOverviewApi && global.EduOverviewApi[config.endpoint];
    if (typeof api !== 'function') {
      throw new Error('总览 API 未加载：' + config.endpoint);
    }

    var state = {
      from: '',
      to: '',
      classId: '',
      seq: 0,
      disposed: false,
      pageHidden: false,
      loading: false,
      authState: 'idle',
      authTicket: 0,
      pendingReload: false
    };

    var groups = Array.isArray(config.groups) ? config.groups : [];
    var metrics = config.metrics;
    var els = {};

    document.body.classList.add('edu-overview-active');
    root.innerHTML = shellHtml(config);
    els.meta = root.querySelector('#ovMeta');
    els.filterHost = root.querySelector('#ovFilterHost');
    els.windowHost = root.querySelector('#ovWindowHost');
    els.bannerHost = root.querySelector('#ovBannerHost');
    els.summary = root.querySelector('#ovSummary');
    els.body = root.querySelector('#ovBody');

    renderFilters();
    renderSkeleton();
    setLoading(true);

    root.addEventListener('click', onClick);
    global.addEventListener('pagehide', onPageHide);
    global.addEventListener('pageshow', onPageShow);

    // Mock 预览不校验登录；真实页面必须等 role-home 完成令牌/角色核验。
    // 所有请求入口都经过 authState/requestLoad，避免筛选、刷新、pageshow
    // 绕过登录等待直接调用 API。
    var mockMode = !!(global.EduMock && global.EduMock.enabled());
    if (mockMode) {
      state.authState = 'ready';
      load();
    } else {
      beginAuth(false);
    }

    /**
     * 构建 shell；正式卡片不显示 metricCode/端口/PR 等工程信息。
     */
    function shellHtml() {
      return '<div class="ov-page">'
        + '<div class="ov-head">'
        + '<div class="ov-head-main"><div class="ov-sub" id="ovMeta">正在校验登录状态…</div></div>'
        + '<div class="ov-head-actions"><button type="button" class="ov-btn" data-action="refresh" disabled>刷新</button></div>'
        + '</div>'
        + '<div id="ovFilterHost"></div>'
        + '<div id="ovWindowHost"></div>'
        + '<div id="ovBannerHost"></div>'
        + '<div id="ovSummary" class="ov-summary"></div>'
        + '<div id="ovBody" class="ov-region"></div>'
        + '</div>';
    }

    function renderFilters() {
      if (!config.timeFilter && !config.classFilter) {
        els.filterHost.innerHTML = '';
        return;
      }
      var html = '<div class="ov-filter">';
      if (config.timeFilter) {
        html += '<label class="ov-field">开始时间（可选）'
          + '<input type="datetime-local" id="ovFrom" value="' + esc(state.from) + '"></label>'
          + '<label class="ov-field">结束时间（可选）'
          + '<input type="datetime-local" id="ovTo" value="' + esc(state.to) + '"></label>'
          + '<button type="button" class="ov-btn ov-btn-primary" data-action="apply-time">应用</button>'
          + '<button type="button" class="ov-btn" data-action="reset-filters">恢复默认</button>';
      }
      if (config.classFilter) {
        html += '<label class="ov-field">教学班 classId（可选，不是 bindingId）'
          + '<input type="text" inputmode="numeric" id="ovClassId" value="' + esc(state.classId)
          + '" placeholder="留空 = 全部授权范围"></label>'
          + '<button type="button" class="ov-btn" data-action="apply-class">应用班级</button>'
          + '<button type="button" class="ov-btn" data-action="clear-class">清除</button>';
      }
      html += '<span class="ov-hint">'
        + (config.timeFilter ? '留空使用服务端默认窗口；自定义时需同时填写开始和结束。' : '')
        + (config.classFilter ? ' classId 只用于教师筛选，与 bindingId 不同。' : '')
        + '</span></div>';
      els.filterHost.innerHTML = html;
    }

    function onClick(event) {
      var target = event.target;
      if (!target || typeof target.closest !== 'function') {
        return;
      }
      var trigger = target.closest('[data-action]');
      if (!trigger || !root.contains(trigger)) {
        return;
      }
      var action = trigger.getAttribute('data-action');
      if (action === 'refresh' || action === 'retry') {
        requestLoad();
      } else if (action === 'retry-auth') {
        beginAuth(true);
      } else if (action === 'reset-filters') {
        state.from = '';
        state.to = '';
        state.classId = '';
        syncFilterInputs();
        requestLoad();
      } else if (action === 'apply-time') {
        applyTime();
      } else if (action === 'apply-class') {
        applyClass();
      } else if (action === 'clear-class') {
        state.classId = '';
        var input = root.querySelector('#ovClassId');
        if (input) {
          input.value = '';
        }
        requestLoad();
      }
    }

    function syncFilterInputs() {
      var fromInput = root.querySelector('#ovFrom');
      var toInput = root.querySelector('#ovTo');
      var classInput = root.querySelector('#ovClassId');
      if (fromInput) {
        fromInput.value = state.from;
      }
      if (toInput) {
        toInput.value = state.to;
      }
      if (classInput) {
        classInput.value = state.classId;
      }
    }

    function applyTime() {
      var fromInput = root.querySelector('#ovFrom');
      var toInput = root.querySelector('#ovTo');
      var from = fromInput ? fromInput.value : '';
      var to = toInput ? toInput.value : '';
      var problem = validateTimeInputs(from, to);
      if (problem) {
        showParamError(problem);
        return;
      }
      state.from = from;
      state.to = to;
      requestLoad();
    }

    function applyClass() {
      var input = root.querySelector('#ovClassId');
      var value = input ? input.value.trim() : '';
      if (value && !/^[1-9][0-9]*$/.test(value)) {
        showParamError('classId 必须是正整数；前端按字符串传递，不做 Number 转换。');
        return;
      }
      state.classId = value;
      requestLoad();
    }

    function validateTimeInputs(from, to) {
      if ((from && !to) || (!from && to)) {
        return '开始时间和结束时间必须同时填写，或都留空使用服务端默认窗口。';
      }
      if (from && to) {
        var fromMs = Date.parse(from);
        var toMs = Date.parse(to);
        if (isNaN(fromMs) || isNaN(toMs)) {
          return '时间格式不合法，请重新选择。';
        }
        if (fromMs >= toMs) {
          return '结束时间必须晚于开始时间。';
        }
      }
      return '';
    }

    function buildQuery() {
      var query = {};
      if (config.timeFilter) {
        var problem = validateTimeInputs(state.from, state.to);
        if (problem) {
          return { error: problem };
        }
        if (state.from && state.to) {
          query.from = new Date(Date.parse(state.from)).toISOString();
          query.to = new Date(Date.parse(state.to)).toISOString();
        }
      }
      if (config.classFilter && state.classId) {
        if (!/^[1-9][0-9]*$/.test(state.classId)) {
          return { error: 'classId 必须是正整数。' };
        }
        // 保留字符串，避免超过 Number.MAX_SAFE_INTEGER 的 Long 精度损失。
        query.classId = state.classId;
      }
      return { query: query };
    }

    /**
     * 发起或复用公共登录核验。
     *
     * 所有真实请求入口都先经过这里；pending 时只记录“需要重新加载”，
     * 不调用 API。BFCache 恢复和显式重新核验使用 force=true。
     *
     * @param {boolean} force 是否强制重新核验
     */
    function beginAuth(force) {
      if (state.disposed || state.pageHidden) {
        return;
      }
      if (!force && (state.authState === 'pending' || state.authState === 'ready')) {
        return;
      }
      var ticket = ++state.authTicket;
      state.seq++;
      state.authState = 'pending';
      state.pendingReload = true;
      setLoading(true);
      els.meta.textContent = '正在核验登录状态…';
      els.windowHost.innerHTML = '';
      els.bannerHost.innerHTML = '';
      els.summary.textContent = '';
      renderSkeleton();
      notifyDebug({ status: 'auth-pending', endpoint: config.endpoint });

      var promise;
      try {
        promise = Views.whenAuthorized();
      } catch (error) {
        promise = Promise.resolve(false);
      }
      Promise.resolve(promise).then(function (ok) {
        // cleanup、pagehide 或新的核验已经使本次结果失效。
        if (state.disposed || ticket !== state.authTicket) {
          return;
        }
        if (!ok) {
          state.authState = 'failed';
          state.pendingReload = false;
          setLoading(false);
          renderPageState('auth-timeout');
          return;
        }
        state.authState = 'ready';
        if (state.pendingReload) {
          state.pendingReload = false;
          load();
        }
      }).catch(function () {
        if (state.disposed || ticket !== state.authTicket) {
          return;
        }
        state.authState = 'failed';
        state.pendingReload = false;
        setLoading(false);
        renderPageState('auth-timeout');
      });
    }

    /**
     * 统一请求入口：真实模式必须先登录就绪。
     * pending 期间只保留最新筛选，不重复派发请求；failed 状态不允许绕过。
     */
    function requestLoad() {
      if (state.disposed || state.pageHidden) {
        return;
      }
      if (mockMode || state.authState === 'ready') {
        load();
        return;
      }
      if (state.authState === 'pending') {
        state.pendingReload = true;
        els.meta.textContent = '正在核验登录状态，完成后按最新筛选读取…';
        renderSkeleton();
        return;
      }
      if (state.authState === 'failed' || state.authState === 'stale') {
        renderPageState('auth-timeout');
        return;
      }
      beginAuth(false);
    }

    function load() {
      if (state.disposed || state.pageHidden) {
        return;
      }
      if (!mockMode && state.authState !== 'ready') {
        requestLoad();
        return;
      }
      var built = buildQuery();
      if (built.error) {
        showParamError(built.error);
        return;
      }
      var ticket = ++state.seq;
      state.loading = true;
      setLoading(true);
      els.meta.textContent = '正在读取总览…';
      els.windowHost.innerHTML = '<div class="ov-window"><b>统计窗口</b><span>读取中…</span></div>';
      els.bannerHost.innerHTML = '';
      els.summary.textContent = '';
      renderSkeleton();
      notifyDebug({ status: 'loading', endpoint: config.endpoint, query: built.query });

      // 请求先进入微任务队列；在真正调用 API 前再核验一次。
      // cleanup/pagehide/新筛选/登录状态回退都可能发生在“已排队但未派发”的窗口，
      // 此时必须直接跳过，不能调用 API，也不能把跳过值送入 handleSuccess 制造契约错误。
      var skipped = false;
      Promise.resolve().then(function () {
        if (isStale(ticket) || state.pageHidden || (!mockMode && state.authState !== 'ready')) {
          skipped = true;
          return null;
        }
        return api(built.query);
      }).then(function (result) {
        if (skipped || isStale(ticket) || state.pageHidden
          || (!mockMode && state.authState !== 'ready')) {
          return;
        }
        state.loading = false;
        setLoading(false);
        handleSuccess(result, built.query);
      }).catch(function (error) {
        if (skipped || isStale(ticket) || state.pageHidden
          || (!mockMode && state.authState !== 'ready')) {
          return;
        }
        state.loading = false;
        setLoading(false);
        handleError(error, built.query);
      });
    }

    function isStale(ticket) {
      return state.disposed || ticket !== state.seq;
    }

    function handleSuccess(result, query) {
      if (!result || typeof result !== 'object' || result.code !== 200) {
        notifyDebug({ status: 'contract-error', endpoint: config.endpoint, query: query, response: result });
        renderContractError('响应缺少成功业务码，无法确认总览数据。');
        return;
      }
      var data = result.data;
      if (!data || typeof data !== 'object') {
        notifyDebug({ status: 'contract-error', endpoint: config.endpoint, query: query, response: result });
        renderContractError('响应缺少 data，页面不会显示 0 或示例数据。');
        return;
      }
      if (!Array.isArray(data.items) || data.items.length === 0) {
        notifyDebug({ status: 'contract-error', endpoint: config.endpoint, query: query, response: result });
        renderContractError('响应 items 为空；总览契约要求至少返回一个指标项。');
        return;
      }
      if (!isInstant(data.asOf)) {
        notifyDebug({ status: 'contract-error', endpoint: config.endpoint, query: query, response: result });
        renderContractError('响应缺少合法的 asOf 时间。');
        return;
      }
      var window = data.window;
      if (!window || !isInstant(window.startsAt) || !isInstant(window.endsAt)
        || Date.parse(window.endsAt) <= Date.parse(window.startsAt)) {
        notifyDebug({ status: 'contract-error', endpoint: config.endpoint, query: query, response: result });
        renderContractError('响应时间窗不合法，无法展示统计范围。');
        return;
      }

      var byCode = {};
      var duplicateCodes = {};
      data.items.forEach(function (item) {
        if (item && typeof item.metricCode === 'string') {
          if (Object.prototype.hasOwnProperty.call(byCode, item.metricCode)
            || Object.prototype.hasOwnProperty.call(duplicateCodes, item.metricCode)) {
            duplicateCodes[item.metricCode] = true;
            if (global.console && global.console.warn) {
              global.console.warn('[EduOverview] 重复 metricCode，按契约违约降级', item.metricCode);
            }
          } else {
            byCode[item.metricCode] = item;
          }
        }
      });

      var rows = metrics.map(function (metric) {
        if (duplicateCodes[metric.code]) {
          return contractRow(metric, 'metricCode 重复，不能确定唯一指标值');
        }
        var raw = byCode[metric.code];
        if (!raw) {
          return contractRow(metric, '接口未返回该指标');
        }
        return normalizeItem(metric, raw);
      });

      renderMeta(data.asOf, window);
      renderWindow(window);
      renderSummary(rows);
      renderBanner(rows);
      renderGroups(rows);
      notifyDebug({
        status: 'success',
        endpoint: config.endpoint,
        query: query,
        response: result
      });
    }

    /**
     * 构造单卡契约违约结果；不保留服务端数值，避免任选一个重复值。
     *
     * @param {object} metric 页面指标配置
     * @param {string} message 违约原因（仅用于卡片提示，不展示原始数值）
     * @returns {object} 卡片行
     */
    function contractRow(metric, message) {
      return {
        config: metric,
        status: STATUS_UNAVAILABLE,
        contract: true,
        message: message,
        value: null,
        unit: '',
        source: metric.source
      };
    }

    /**
     * 严格校验单指标：状态/值必须成对，数值必须为非负整数，unit 必须为 COUNT，
     * source 必须与页面配置一致。违约只降级当前卡片，不抛异常拖垮整页。
     */
    function normalizeItem(metric, raw) {
      var invalid = function (message) {
        return {
          config: metric,
          status: STATUS_UNAVAILABLE,
          contract: true,
          message: message,
          value: null,
          unit: '',
          source: metric.source
        };
      };
      if (!raw || typeof raw !== 'object') {
        return invalid('指标数据不是对象');
      }
      if (raw.status !== STATUS_OK && raw.status !== STATUS_NO_DATA
        && raw.status !== STATUS_UNAVAILABLE) {
        return invalid('status 不在允许枚举内');
      }
      if (raw.status === STATUS_OK) {
        if (!isNonNegativeInteger(raw.value)) {
          return invalid('OK 状态必须有非负整数 value');
        }
      } else if (raw.value !== null) {
        return invalid('NO_DATA/UNAVAILABLE 状态不得有 value');
      }
      if (typeof raw.unit !== 'string' || raw.unit === '') {
        return invalid('unit 缺失');
      }
      if (raw.unit !== 'COUNT') {
        return invalid('unit 不是 COUNT');
      }
      if (typeof raw.source !== 'string' || raw.source === '') {
        return invalid('source 缺失');
      }
      if (metric.source && raw.source !== metric.source) {
        return invalid('source 与页面配置不一致');
      }
      if (config.requireNoData && raw.status !== STATUS_NO_DATA) {
        return invalid('管理员待办当前只接受 NO_DATA');
      }
      if (config.expectedSource && raw.source !== config.expectedSource) {
        return invalid('来源不符合当前契约');
      }
      if (config.requireNullActionPath && raw.actionPath !== null) {
        return invalid('管理员待办 actionPath 必须为 null');
      }
      if (raw.sampleCount !== null && raw.sampleCount !== undefined
        && !isNonNegativeInteger(raw.sampleCount)) {
        if (global.console && global.console.warn) {
          global.console.warn('[EduOverview] sampleCount 非法，忽略', metric.code, raw.sampleCount);
        }
      }
      return {
        config: metric,
        status: raw.status,
        contract: false,
        message: '',
        value: raw.status === STATUS_OK ? raw.value : null,
        unit: raw.unit,
        source: raw.source
      };
    }

    function renderMeta(asOf, window) {
      var html = '响应时间 <b title="' + esc(String(asOf)) + '">' + esc(formatTime(asOf)) + '</b>';
      if (config.showWindow !== false) {
        html += ' · 统计窗口 <b title="' + esc(String(window.startsAt)) + ' — ' + esc(String(window.endsAt)) + '">'
          + esc(formatTime(window.startsAt)) + ' — ' + esc(formatTime(window.endsAt)) + '</b>';
      }
      els.meta.innerHTML = html;
    }

    function renderWindow(window) {
      if (config.showWindow === false) {
        els.windowHost.innerHTML = '<div class="ov-window"><b>时间来源</b><span>本页无时间来源</span></div>';
        return;
      }
      els.windowHost.innerHTML = '<div class="ov-window"><b>统计窗口（服务端返回）</b>'
        + '<span title="' + esc(String(window.startsAt)) + ' — ' + esc(String(window.endsAt)) + '">'
        + esc(formatTime(window.startsAt)) + ' — ' + esc(formatTime(window.endsAt)) + '</span>'
        + '<span class="snapshot">课堂/任务按窗口；其他指标为当前快照</span></div>';
    }

    function renderSummary(rows) {
      var ok = rows.filter(function (row) { return row.status === STATUS_OK; }).length;
      var noData = rows.filter(function (row) { return row.status === STATUS_NO_DATA; }).length;
      var unavailable = rows.filter(function (row) {
        return row.status === STATUS_UNAVAILABLE && !row.contract;
      }).length;
      var contract = rows.filter(function (row) { return row.contract; }).length;
      els.summary.innerHTML = '共 <b>' + rows.length + '</b> 项指标 · <b>' + ok + '</b> 正常 · <b>'
        + noData + '</b> 暂无数据 · <b>' + unavailable + '</b> 暂不可用'
        + (contract > 0 ? ' · <b>' + contract + '</b> 数据异常' : '');
    }

    function renderBanner(rows) {
      var unavailable = rows.filter(function (row) {
        return row.status === STATUS_UNAVAILABLE && !row.contract;
      }).length;
      var contract = rows.filter(function (row) { return row.contract; }).length;
      if (unavailable === 0 && contract === 0) {
        els.bannerHost.innerHTML = '';
        return;
      }
      els.bannerHost.innerHTML = '<div class="ov-banner">部分指标暂不可用'
        + (unavailable > 0 ? '（' + unavailable + '）' : '')
        + (contract > 0 ? '，数据异常（' + contract + '）' : '')
        + '。其他卡片继续显示；不可用项不显示 0 或旧值。</div>';
    }

    function renderGroups(rows) {
      var html = '';
      groups.forEach(function (group) {
        var groupRows = rows.filter(function (row) {
          return row.config.group === group.key;
        });
        if (groupRows.length === 0) {
          return;
        }
        var label = groupLabel(group);
        var windowed = groupRows.some(function (row) { return row.config.windowed; });
        html += '<section class="ov-group" data-group="' + esc(group.key) + '">'
          + '<div class="ov-group-head"><h3>' + esc(label) + '</h3>'
          + '<span>' + (windowed ? '按时间窗' : '当前快照') + '</span></div>'
          + '<div class="ov-grid">';
        groupRows.forEach(function (row) {
          html += cardHtml(row);
        });
        html += '</div></section>';
      });
      els.body.innerHTML = html;
    }

    function hasCustomWindow() {
      return Boolean(state.from || state.to);
    }

    function groupLabel(group) {
      return (hasCustomWindow() && group.customLabel) ? group.customLabel : group.label;
    }

    function cardHtml(row) {
      var metric = row.config;
      var status = row.status;
      var badgeHtml;
      var valueHtml;
      if (status === STATUS_OK) {
        badgeHtml = '<span class="ov-badge ov-badge-ok">正常</span>';
        valueHtml = '<div class="ov-value"><span class="ov-number">' + esc(row.value) + '</span>'
          + '<span class="ov-unit">' + esc(metric.unitLabel || '') + '</span></div>';
      } else if (status === STATUS_NO_DATA) {
        badgeHtml = '<span class="ov-badge ov-badge-empty">暂无数据</span>';
        valueHtml = '<div class="ov-value"><span class="ov-state-text">'
          + esc(metric.noDataText || '暂无数据') + '</span></div>';
      } else if (row.contract) {
        badgeHtml = '<span class="ov-badge ov-badge-unavailable">数据异常</span>';
        valueHtml = '<div class="ov-value"><span class="ov-state-text unavailable">数据异常</span></div>';
      } else {
        badgeHtml = '<span class="ov-badge ov-badge-unavailable">暂不可用</span>';
        valueHtml = '<div class="ov-value"><span class="ov-state-text unavailable">暂不可用</span></div>';
      }

      var note = metric.note || '';
      if (row.contract) {
        note += row.message === '接口未返回该指标'
          ? '（接口未返回该指标）'
          : '（该指标响应异常，已降级）';
      }

      var sourceLabel = SOURCE_LABEL[row.source] || SOURCE_LABEL[metric.source] || '来源未知';
      var foot = '<div class="ov-card-foot"><span>来源：' + esc(sourceLabel) + '</span>'
        + '<span>· ' + (metric.windowed ? '按窗口' : '快照') + '</span>';
      // sampleCount 属于契约元数据，正式卡片不展示；需要时在预览调试区查看。
      if (metric.navigation) {
        // 阶段1只声明已核验映射，不开放真实跳转；阶段2验证菜单后再接。
        foot += '<span class="spacer"></span><span class="ov-nav-note">'
          + esc(metric.navigation.label) + '（阶段2开放）</span>';
      }
      foot += '</div>';

      return '<div class="ov-card">'
        + '<div class="ov-card-top"><div class="ov-card-title">' + esc(metric.title) + '</div>'
        + badgeHtml + '</div>'
        + valueHtml
        + '<div class="ov-card-note">' + esc(note) + '</div>'
        + foot + '</div>';
    }

    function renderSkeleton() {
      var html = '';
      groups.forEach(function (group) {
        var groupMetrics = metrics.filter(function (metric) { return metric.group === group.key; });
        if (groupMetrics.length === 0) {
          return;
        }
        html += '<section class="ov-group"><div class="ov-group-head"><h3>' + esc(groupLabel(group))
          + '</h3><span>读取中</span></div><div class="ov-grid ov-skeleton">';
        groupMetrics.forEach(function () {
          html += '<div class="ov-card"><div class="sk-line title"></div>'
            + '<div class="sk-line value"></div><div class="sk-line note"></div></div>';
        });
        html += '</div></section>';
      });
      els.body.innerHTML = html;
    }

    function showParamError(message) {
      // 参数错误不发请求；递增序号使任何在途旧响应失效。
      state.seq++;
      state.pendingReload = false;
      state.loading = false;
      setLoading(false);
      els.meta.textContent = '筛选参数不合法';
      els.windowHost.innerHTML = '';
      els.bannerHost.innerHTML = '';
      els.summary.textContent = '';
      els.body.innerHTML = pageStateHtml('400', message, '未发送请求');
      notifyDebug({ status: 'param-error', endpoint: config.endpoint, message: message });
    }

    function renderContractError(message) {
      state.loading = false;
      setLoading(false);
      els.meta.textContent = '响应结构不符合总览契约';
      els.windowHost.innerHTML = '';
      els.bannerHost.innerHTML = '';
      els.summary.textContent = '';
      els.body.innerHTML = pageStateHtml('contract', message, '响应契约');
    }

    function renderPageState(kind, message) {
      state.loading = false;
      setLoading(false);
      els.meta.textContent = '';
      els.windowHost.innerHTML = '';
      els.bannerHost.innerHTML = '';
      els.summary.textContent = '';
      els.body.innerHTML = pageStateHtml(kind, message, kind === 'auth-timeout' ? '登录状态' : '');
      notifyDebug({ status: kind, endpoint: config.endpoint, message: message || '' });
    }

    function pageStateHtml(kind, message, codeText) {
      var title = '服务暂不可用';
      var text = message || '请稍后重试；持续失败时请联系管理员。';
      var icon = '!';
      var actions = '';
      if (kind === 'auth-timeout') {
        title = '登录状态核验超时';
        text = '页面未读取总览数据；请重新核验登录，或刷新页面后重试。';
        actions = '<div class="ov-state-actions"><button type="button" class="ov-btn ov-btn-primary" data-action="retry-auth">重新核验登录</button></div>';
      } else if (kind === '403') {
        title = '无权限查看总览';
        text = message || '当前账号无权访问该总览范围，页面不显示旧数据。';
      } else if (kind === '400') {
        title = '筛选参数不合法';
        text = message || '请修正开始/结束时间或 classId 后重试。';
        actions = '<div class="ov-state-actions"><button type="button" class="ov-btn ov-btn-primary" data-action="reset-filters">恢复默认筛选</button></div>';
      } else if (kind === '404') {
        title = '总览接口暂不可用';
        text = message || '当前环境可能未部署 6-01 后端，或接口路径不可用；这不是 0 条数据。';
      } else if (kind === '500') {
        title = '服务暂时不可用';
        text = message || '服务端处理总览请求失败；页面不保留旧数据。';
      } else if (kind === 'contract') {
        title = '总览响应异常';
        text = message || '响应结构不符合契约，页面不显示 0 或示例数据。';
      } else if (kind === '401') {
        title = '登录状态已失效';
        text = '请重新登录后再查看总览。';
      }
      if (kind === '404' || kind === '500' || kind === 'contract') {
        actions = '<div class="ov-state-actions"><button type="button" class="ov-btn ov-btn-primary" data-action="retry">重试</button></div>';
      }
      return '<div class="ov-page-state"><div class="icon">' + esc(icon) + '</div>'
        + '<h2>' + esc(title) + '</h2><p>' + esc(text) + '</p>'
        + (codeText ? '<div class="code">' + esc(codeText) + '</div>' : '')
        + actions + '</div>';
    }

    function handleError(error, query) {
      var code = error && typeof error.code === 'number' ? error.code : 0;
      var message = error && error.message ? error.message : '请求失败';
      notifyDebug({ status: 'error', endpoint: config.endpoint, query: query, error: { code: code, message: message } });

      if (error && (error.requiresLogin || error.isAuth && error.isAuth())) {
        if (global.EduMock && global.EduMock.enabled()) {
          renderPageState('401', message);
        } else if (global.EduHttp && typeof global.EduHttp.backToLogin === 'function') {
          global.EduHttp.backToLogin();
        }
        return;
      }
      if (error && error.isPermission && error.isPermission()) {
        renderPageState('403', message);
      } else if (code === 400) {
        renderPageState('400', message);
      } else if (code === 404) {
        renderPageState('404', message);
      } else if (error && error.isNetwork && error.isNetwork()) {
        renderPageState('500', '网络异常，未能连接到服务端。');
      } else {
        renderPageState('500', message);
      }
    }

    function setLoading(loading) {
      var refresh = root.querySelector('[data-action="refresh"]');
      if (refresh) {
        refresh.disabled = loading;
        refresh.textContent = loading ? '读取中…' : '刷新';
      }
      root.setAttribute('aria-busy', loading ? 'true' : 'false');
    }

    function notifyDebug(snapshot) {
      var page = global.EduOverviewPage;
      if (page && typeof page.onDebug === 'function') {
        try {
          page.onDebug(snapshot);
        } catch (e) {
          /* 调试钩子不能影响正式页面 */
        }
      }
    }

    function onPageHide() {
      // 页面离开或进入 bfcache：使在途响应和旧登录等待同时失效。
      state.pageHidden = true;
      state.seq++;
      state.authTicket++;
      state.authState = 'stale';
      state.pendingReload = false;
    }

    function onPageShow(event) {
      if (event && event.persisted && !state.disposed) {
        state.pageHidden = false;
        state.seq++;
        // BFCache 恢复后必须重新等待公共登录/角色核验，不能直接 load。
        beginAuth(true);
      }
    }

    return {
      cleanup: function () {
        state.disposed = true;
        state.pageHidden = true;
        state.seq++;
        state.authTicket++;
        state.authState = 'stale';
        state.pendingReload = false;
        root.removeEventListener('click', onClick);
        global.removeEventListener('pagehide', onPageHide);
        global.removeEventListener('pageshow', onPageShow);
        document.body.classList.remove('edu-overview-active');
        root.innerHTML = '';
      }
    };
  }

  global.EduOverviewPage = {
    create: create,
    onDebug: null
  };
})(window);
