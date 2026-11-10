(() => {
  'use strict';

  // 第 3 组页面挂载层。三端 HTML 外壳、导航、页签、登录态与 Group3App.render(gi, ii)
  // 契约保持不变；本文件只负责把四个业务页面渲染进 #group3Content。
  // 前端构建号：所有页面内跳转都带上它，避免浏览器/IDE 内置浏览器复用改动前的缓存页面。
  const BUILD = '20261110d';
  function buildUrl(url) {
    const hashAt = url.indexOf('#');
    const path = hashAt < 0 ? url : url.slice(0, hashAt);
    const hash = hashAt < 0 ? '' : url.slice(hashAt);
    return `${path}${path.includes('?') ? '&' : '?'}v=${BUILD}${hash}`;
  }

  const tokenKey = 'zhijiao.login.token';
  const role = document.body.dataset.role;
  const root = document.getElementById('group3Content');
  if (!root || !['teacher', 'student'].includes(role)) return;

  const state = {
    contexts: null,
    bindingId: localStorage.getItem(`zhijiao.group3.binding.${role}`) || '',
    studioTab: 'resources',
    prepareView: 'timeline',
    prepareFilter: 'all',
    studentFilter: 'all',
    selectedKey: '',
    overview: null,
    overviewBinding: '',
    nodes: [],
    nodeIndex: {},
    teacherTasks: [],
    plans: [],
    studentTasks: [],
    materials: [],
    selectedPreviewId: null,
    modalUrls: [],
    route: '',
    // 独立环节页：与总览共用同一份节点数据，作答草稿按环节隔离。
    stageKey: '',
    stageAnswers: {},
    stageUrls: [],
    stageOrder: [],
    // 一堂课 = 某课程 / 教学班在某一天上的课；每堂课内部单独从 1 编号。
    stageGroups: [],
    // 独立分步创建页：与总览共用 #group3Content 和 ?g=&i= 路由，只用 #create= 区分视图。
    createFlow: '',
    createDraft: {}
  };

  const routes = role === 'teacher'
    ? { studio: { g: 1, i: 1 }, prepare: { g: 1, i: 2 } }
    : { materials: { g: 1, i: 1 }, prestudy: { g: 1, i: 2 } };

  const labels = {
    SINGLE: '单选题', MULTIPLE: '多选题', TRUE_FALSE: '判断题', SHORT_TEXT: '简答题',
    READY: '可用', PENDING: '处理中', FAILED: '处理失败',
    PUBLISHED: '已发布', CLOSED: '已截止', ARCHIVED: '已归档',
    UPCOMING: '未开放', OPEN: '进行中', DRAFT: '待编排', PRIVATE: '仅教师可见',
    ACTIVE: '启用'
  };

  // 时间轴环节类型。每个类型都映射到既有 /education/group3/** 返回的真实对象。
  const KINDS = {
    MATERIAL: { label: '资料阅读', short: '资料', tone: 'blue', icon: '▤' },
    QUIZ: { label: '预习习题', short: '习题', tone: 'teal', icon: '✎' },
    PLAN: { label: '备课包', short: '备课', tone: 'amber', icon: '◈' },
    DRAFT: { label: '待编排资料', short: '草稿', tone: 'slate', icon: '◌' }
  };

  const STATE_LABELS = {
    DRAFT: '待编排', FAILED: '处理失败', UPCOMING: '未开放', OPEN: '进行中',
    CLOSED: '已截止', ARCHIVED: '已归档', PRIVATE: '仅教师可见'
  };

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function authHeaders(extra = {}) {
    const token = sessionStorage.getItem(tokenKey);
    return token ? { ...extra, Authorization: `Bearer ${token}` } : extra;
  }

  async function parseResponse(response) {
    let result = null;
    try { result = await response.json(); }
    catch { throw Object.assign(new Error(`服务器响应无法解析（${response.status}）`), { status: response.status }); }
    if (!response.ok || result.code !== 200) {
      const error = new Error(result.msg || `请求失败（${response.status}）`);
      error.status = response.status;
      error.code = result.code;
      throw error;
    }
    return result.data;
  }

  async function api(path, options = {}) {
    const headers = authHeaders(options.headers || {});
    const request = { cache: 'no-store', ...options, headers };
    if (request.body && !(request.body instanceof FormData) && typeof request.body !== 'string') {
      headers['Content-Type'] = 'application/json';
      request.body = JSON.stringify(request.body);
    }
    const response = await fetch(path, request);
    try { return await parseResponse(response); }
    catch (error) {
      if (error.code === 401 || error.status === 401) {
        sessionStorage.removeItem(tokenKey);
        sessionStorage.removeItem('zhijiao.login.role');
        location.replace(buildUrl('/login.html?logout=expired'));
      }
      throw error;
    }
  }

  // 失败提示必须可执行：告诉用户下一步做什么，而不是只回显服务端原文。
  function actionableMessage(error) {
    const message = error && error.message ? error.message : String(error || '未知错误');
    const status = error && error.status;
    if (status === 401) return '登录已失效，请重新登录后再操作。';
    if (status === 403) return `无权访问该内容（${message}）。请确认页面顶部选中的课程 / 教学班；若已被移出班级，旧链接会立即失效。`;
    if (status === 404) return `内容不存在或已被撤回（${message}）。请刷新时间轴后重试。`;
    if (status === 409) return `状态已变化（${message}）。请刷新时间轴，确认最新状态后再操作。`;
    if (status >= 500) return `服务暂时不可用（${message}）。请稍后重试；若持续失败，请联系管理员并提供操作时间。`;
    return message;
  }

  async function fetchBlob(path) {
    const response = await fetch(path, { cache: 'no-store', headers: authHeaders() });
    if (!response.ok) {
      let message = `文件读取失败（${response.status}）`;
      try { const payload = await response.json(); message = payload.msg || message; }
      catch { /* 二进制接口可能返回 HTML 错误页 */ }
      const error = new Error(message);
      error.status = response.status;
      // 撤权 / 撤回后旧地址必须立刻不可用，前端据此清理本地对象链接。
      error.revoked = response.status === 403 || response.status === 404;
      throw error;
    }
    return response.blob();
  }

  function rememberUrl(url) { state.modalUrls.push(url); return url; }
  function clearObjectUrls() {
    state.modalUrls.forEach(url => URL.revokeObjectURL(url));
    state.modalUrls = [];
  }

  function toast(message, error = false) {
    document.querySelectorAll('.g3-toast').forEach(node => node.remove());
    const node = document.createElement('div');
    node.className = `g3-toast${error ? ' error' : ''}`;
    node.textContent = message;
    document.body.appendChild(node);
    setTimeout(() => node.remove(), 4200);
  }

  function formatTime(value) {
    if (!value) return '不限';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat('zh-CN', {
      month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
    }).format(date);
  }

  function formatClock(value) {
    if (!value) return '--:--';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '--:--';
    const pad = number => String(number).padStart(2, '0');
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  function dayKeyOf(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  function dayLabelOf(value, serverNow) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '未安排时间';
    const today = dayKeyOf(serverNow || new Date());
    const key = dayKeyOf(value);
    const tomorrow = new Date(date.getTime());
    tomorrow.setDate(tomorrow.getDate() - 1);
    const yesterday = new Date(date.getTime());
    yesterday.setDate(yesterday.getDate() + 1);
    if (key === today) return '今天';
    if (dayKeyOf(tomorrow) === today) return '明天';
    if (dayKeyOf(yesterday) === today) return '昨天';
    const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()];
    return `${date.getMonth() + 1} 月 ${date.getDate()} 日 · ${week}`;
  }

  function localInput(value) {
    const date = value ? new Date(value) : new Date();
    if (Number.isNaN(date.getTime())) return '';
    const pad = number => String(number).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T`
      + `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  function isoFromInput(value) {
    if (!value) return null;
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) throw new Error('请填写有效的日期与时间');
    return date.toISOString();
  }

  function futureInput(days) { return localInput(new Date(Date.now() + days * 86400000)); }

  function optionMarkup(value, current) {
    return String(value) === String(current) ? ' selected' : '';
  }

  function statusBadge(status) {
    const value = String(status || '').toUpperCase();
    const className = ['READY', 'OPEN', 'ACTIVE', 'PUBLISHED'].includes(value) ? 'ready'
      : ['PENDING', 'UPCOMING', 'DRAFT', 'PRIVATE'].includes(value) ? 'pending'
        : ['FAILED', 'CLOSED', 'ARCHIVED'].includes(value) ? 'failed' : '';
    return `<span class="g3-badge ${className}">${escapeHtml(labels[value] || value || '未知')}</span>`;
  }

  function statePill(value) {
    return `<span class="g3-tl-state s-${escapeHtml(String(value || '').toLowerCase())}">`
      + `${escapeHtml(STATE_LABELS[value] || value || '未知')}</span>`;
  }

  function emptyState(title, hint = '', action = '') {
    return `<div class="g3-empty"><strong>${escapeHtml(title)}</strong>`
      + `${hint ? `<span>${escapeHtml(hint)}</span>` : ''}${action}</div>`;
  }

  function loadingState(text = '正在加载') {
    return `<div class="g3-loading"><span class="g3-spinner"></span><span>${escapeHtml(text)}</span></div>`;
  }

  function errorState(error) {
    return `<div class="g3-error"><strong>加载失败</strong><span>${escapeHtml(actionableMessage(error))}</span>
      <button class="g3-btn" type="button" data-act="retry">刷新重试</button></div>`;
  }

  /* ------------------------------------------------------------------ *
   * 上下文与弹窗
   * ------------------------------------------------------------------ */

  function currentContext() {
    if (!state.contexts || state.contexts.length === 0) return null;
    let item = state.contexts.find(context => String(context.bindingId) === String(state.bindingId));
    if (!item) item = state.contexts[0];
    state.bindingId = String(item.bindingId);
    localStorage.setItem(`zhijiao.group3.binding.${role}`, state.bindingId);
    return item;
  }

  async function ensureContexts() {
    if (!state.contexts) state.contexts = await api('/education/group3/contexts');
    return currentContext();
  }

  function pageHead(title, subtitle, context, note = '') {
    const contextOptions = state.contexts.map(item =>
      `<option value="${escapeHtml(item.bindingId)}"${optionMarkup(item.bindingId, context.bindingId)}>`
      + `${escapeHtml(item.courseTitle)} · ${escapeHtml(item.className)}</option>`).join('');
    return `<div class="g3-page-head">
      <div class="g3-title-block"><h2>${escapeHtml(title)}</h2>
        <p>${escapeHtml(subtitle)}</p>${note ? `<p class="g3-head-note">${escapeHtml(note)}</p>` : ''}</div>
      <div class="g3-context"><label for="g3Context">课程 / 教学班</label>
        <select id="g3Context" class="g3-select">${contextOptions}</select></div>
    </div>`;
  }

  // 教师一天可能给多个班上不同课；总览顶部把「课程 · 教学班」并列出来，切换即换一堂课。
  function contextChips(context) {
    if (!state.contexts || state.contexts.length < 2) return '';
    return `<div class="g3-context-chips"><span>按课堂切换</span>${state.contexts.map(item =>
      `<button type="button" class="g3-chip${String(item.bindingId) === String(context.bindingId) ? ' active' : ''}"
        data-act="switch-context" data-binding="${escapeHtml(item.bindingId)}">
        ${escapeHtml(item.courseTitle)} · ${escapeHtml(item.className)}</button>`).join('')}</div>`;
  }

  async function loadOverview(context) {
    const key = String(context.bindingId);
    if (state.overview && state.overviewBinding === key) return state.overview;
    state.overview = await api(`/education/group3/overview?bindingId=${encodeURIComponent(key)}`);
    state.overviewBinding = key;
    return state.overview;
  }

  function clearOverview() {
    state.overview = null;
    state.overviewBinding = '';
  }

  function openModal({ title, body, footer = '', wide = false, onMount }) {
    closeModal();
    const backdrop = document.createElement('div');
    backdrop.className = 'g3-modal-backdrop';
    backdrop.innerHTML = `<section class="g3-modal${wide ? ' wide' : ''}" role="dialog" aria-modal="true"
        aria-label="${escapeHtml(title)}">
      <div class="g3-modal-head"><h3>${escapeHtml(title)}</h3>
        <button class="g3-btn icon" type="button" data-modal-close aria-label="关闭">×</button></div>
      <div class="g3-modal-body">${body}</div>
      ${footer ? `<div class="g3-modal-foot">${footer}</div>` : ''}
    </section>`;
    document.body.appendChild(backdrop);
    backdrop.addEventListener('click', event => {
      if (event.target === backdrop || event.target.closest('[data-modal-close]')) closeModal();
    });
    if (onMount) onMount(backdrop);
    return backdrop;
  }

  function closeModal() {
    const modal = document.querySelector('.g3-modal-backdrop');
    if (modal) modal.remove();
    clearObjectUrls();
  }

  function setBusy(button, busy, text) {
    if (!button) return;
    if (busy) {
      button.dataset.originalText = button.textContent;
      button.textContent = text || '处理中…';
      button.disabled = true;
    } else {
      button.textContent = button.dataset.originalText || button.textContent;
      button.disabled = false;
    }
  }

  /* ------------------------------------------------------------------ *
   * 时间轴数据层：纯函数，节点全部来自真实接口记录
   * ------------------------------------------------------------------ */

  function timeState(opensAt, closesAt, serverNow) {
    const now = serverNow ? new Date(serverNow).getTime() : Date.now();
    const opens = opensAt ? new Date(opensAt).getTime() : null;
    const closes = closesAt ? new Date(closesAt).getTime() : null;
    if (opens != null && Number.isFinite(opens) && now < opens) return 'UPCOMING';
    if (closes != null && Number.isFinite(closes) && now >= closes) return 'CLOSED';
    return 'OPEN';
  }

  function makeNode(fields) {
    return Object.assign({
      key: '', kind: 'MATERIAL', id: null, title: '', subtitle: '',
      time: null, opensAt: null, closesAt: null, state: 'OPEN',
      tags: [], private: false, meta: {}
    }, fields);
  }

  function collectTeacherNodes(overview, taskRows, plans) {
    const serverNow = overview.serverTime;
    const lessons = overview.lessons || [];
    const lessonTime = new Map(lessons.map(lesson => [String(lesson.lessonId), lesson.scheduledAt]));
    const nodes = [];

    (overview.resources || []).forEach(item => {
      const status = String(item.processingStatus || 'PENDING').toUpperCase();
      const released = Boolean(item.releaseId);
      const tags = [`V${item.versionNo || 1}`, `${Number(item.pageCount || 0)} 页`];
      if (item.mediaKind) tags.push(item.mediaKind);
      const base = {
        id: item.versionId,
        resourceId: item.resourceId,
        releaseId: item.releaseId || null,
        title: item.title || '未命名资料',
        subtitle: item.processingMessage || '',
        opensAt: item.opensAt || null,
        closesAt: item.closesAt || null,
        time: item.opensAt || item.createdAt || null,
        tags,
        meta: item
      };
      if (status === 'FAILED') {
        nodes.push(makeNode({
          ...base, kind: 'DRAFT', key: `draft-${item.resourceId}`,
          state: 'FAILED', time: null, private: true
        }));
      } else if (released) {
        nodes.push(makeNode({
          ...base, kind: 'MATERIAL', key: `material-${item.resourceId}`,
          state: timeState(item.opensAt, item.closesAt, serverNow)
        }));
      } else {
        const reason = status === 'PENDING' ? '资料处理中，暂不能发布' : '尚未设置开放时间，学生看不到';
        nodes.push(makeNode({
          ...base, kind: 'DRAFT', key: `draft-${item.resourceId}`, state: 'DRAFT',
          subtitle: reason, time: null, private: true
        }));
      }
    });

    // 一个预习任务含多道题时后端返回多行，按 assignmentId 聚合成一个环节。
    const quizMap = new Map();
    (taskRows || []).forEach(row => {
      const key = String(row.assignmentId);
      if (!quizMap.has(key)) quizMap.set(key, { row, items: [] });
      if (row.assignmentItemId) quizMap.get(key).items.push(row);
    });
    quizMap.forEach(({ row, items }) => {
      const state = String(row.status || '').toUpperCase() === 'ARCHIVED'
        ? 'ARCHIVED' : timeState(row.opensAt, row.closesAt, serverNow);
      const tags = [`${items.length} 道题`];
      if (row.lessonId && lessonTime.get(String(row.lessonId))) tags.push('已关联课次');
      nodes.push(makeNode({
        key: `quiz-${row.assignmentId}`, kind: 'QUIZ', id: row.assignmentId,
        title: row.title || '预习任务', subtitle: row.instructions || row.stem || '',
        opensAt: row.opensAt, closesAt: row.closesAt,
        time: row.opensAt, state, tags,
        meta: { ...row, itemCount: items.length, submissionCount: row.submissionCount, targetCount: row.targetCount, answerOpensAt: row.answerOpensAt }
      }));
    });

    (plans || []).forEach(plan => {
      const scheduled = lessonTime.get(String(plan.lessonId)) || null;
      nodes.push(makeNode({
        key: `plan-${plan.planId}`, kind: 'PLAN', id: plan.planId,
        title: plan.title || '备课方案',
        subtitle: plan.privateNotes || '教师私有备注不进入学生端',
        time: scheduled || plan.createdAt || null,
        state: 'PRIVATE', private: true,
        tags: [plan.lessonTitle || '未关联课次', `V${plan.versionNo || 1}`,
          `${(plan.materials || []).length} 份材料`],
        meta: plan
      }));
    });

    return nodes;
  }

  function collectStudentNodes(materials, taskRows) {
    const nodes = [];
    (materials || []).forEach(item => {
      nodes.push(makeNode({
        key: `material-${item.releaseId || item.versionId}`, kind: 'MATERIAL',
        id: item.versionId, title: item.title || '课程资料',
        subtitle: `开放至 ${formatTime(item.closesAt)}`,
        opensAt: item.opensAt, closesAt: item.closesAt,
        time: item.opensAt, state: 'OPEN',
        tags: [`V${item.versionNo || 1}`, `${Number(item.pageCount || 0)} 页`],
        meta: item
      }));
    });

    const quizMap = new Map();
    (taskRows || []).forEach(row => {
      const key = String(row.assignmentId);
      if (!quizMap.has(key)) quizMap.set(key, { row, items: [] });
      if (row.assignmentItemId) quizMap.get(key).items.push(row);
    });
    quizMap.forEach(({ row, items }) => {
      const state = String(row.state || 'OPEN').toUpperCase();
      const submitted = Boolean(row.submitted);
      const answerVisible = Boolean(row.answerVisible);
      const tags = [`${items.length} 道题`, submitted ? '已提交' : '待完成'];
      if (answerVisible) tags.push('反馈已公布');
      nodes.push(makeNode({
        key: `quiz-${row.assignmentId}`, kind: 'QUIZ', id: row.assignmentId,
        title: row.title || '预习任务', subtitle: row.instructions || '',
        opensAt: row.opensAt, closesAt: row.closesAt,
        time: row.opensAt, state,
        tags,
        meta: { ...row, itemCount: items.length, submitted, answerVisible, answerOpensAt: row.answerOpensAt }
      }));
    });
    return nodes;
  }

  function filterNodes(nodes, filter) {
    if (!filter || filter === 'all') return nodes.slice();
    if (filter === 'todo') {
      return nodes.filter(node => node.kind === 'QUIZ' && !node.meta.submitted && ['OPEN', 'UPCOMING'].includes(node.state));
    }
    if (filter === 'pending') {
      return nodes.filter(node => ['DRAFT', 'FAILED', 'UPCOMING'].includes(node.state));
    }
    return nodes.filter(node => node.kind === filter.toUpperCase());
  }

  // 按日期分组；无开放时间的草稿归入「待编排」并置顶，方便教师继续设置内容。
  function groupNodesByDay(nodes, serverNow) {
    const dated = nodes.filter(node => node.time && dayKeyOf(node.time));
    const undated = nodes.filter(node => !node.time || !dayKeyOf(node.time));
    dated.sort((a, b) => new Date(a.time) - new Date(b.time));
    const groups = [];
    const index = new Map();
    dated.forEach(node => {
      const key = dayKeyOf(node.time);
      if (!index.has(key)) {
        index.set(key, groups.length);
        groups.push({ key, label: dayLabelOf(node.time, serverNow), nodes: [] });
      }
      groups[index.get(key)].nodes.push(node);
    });
    if (undated.length) groups.unshift({ key: 'unscheduled', label: '待编排 · 学生不可见', nodes: undated });
    return groups;
  }

  /* ------------------------------------------------------------------ *
   * 时间轴视图层
   * ------------------------------------------------------------------ */

  function nodeTags(node) {
    return (node.tags || []).filter(Boolean)
      .map(tag => `<span class="g3-tl-tag">${escapeHtml(tag)}</span>`).join('');
  }

  function renderNodeCard(node, selected) {
    const kind = KINDS[node.kind] || KINDS.MATERIAL;
    const actions = [];
    if (node.kind === 'MATERIAL') {
      actions.push(`<button class="g3-btn quiet" type="button" data-act="stage-preview" data-key="${escapeHtml(node.key)}">预览</button>`);
      if (role === 'student') {
        actions.push(`<button class="g3-btn quiet" type="button" data-act="stage-download" data-key="${escapeHtml(node.key)}">下载</button>`);
      } else {
        actions.push(`<button class="g3-btn quiet" type="button" data-act="stage-revoke" data-key="${escapeHtml(node.key)}">撤回</button>`);
        actions.push(`<button class="g3-btn quiet" type="button" data-act="stage-versions" data-key="${escapeHtml(node.key)}">版本</button>`);
      }
    } else if (node.kind === 'DRAFT') {
      if (node.state === 'FAILED') {
        actions.push(`<button class="g3-btn quiet" type="button" data-act="stage-failure" data-key="${escapeHtml(node.key)}">查看失败原因</button>`);
        actions.push(`<button class="g3-btn quiet" type="button" data-act="stage-new-version" data-key="${escapeHtml(node.key)}">重新上传</button>`);
      } else {
        actions.push(`<button class="g3-btn quiet" type="button" data-act="stage-release" data-key="${escapeHtml(node.key)}">设置开放并发布</button>`);
        actions.push(`<button class="g3-btn quiet" type="button" data-act="stage-preview" data-key="${escapeHtml(node.key)}">预览</button>`);
      }
    } else if (node.kind === 'QUIZ' && role === 'student') {
      actions.push(`<button class="g3-btn quiet" type="button" data-act="stage-select" data-key="${escapeHtml(node.key)}">完成情况</button>`);
    }
    if (role === 'teacher') {
      actions.push(`<button class="g3-btn quiet danger" type="button" data-act="stage-remove" data-key="${escapeHtml(node.key)}">删除环节</button>`);
    }
    // 每个环节都有独立入口：进入后占满内容区，可上下切换环节。
    const enterLabel = role === 'student'
      ? (node.kind === 'QUIZ' ? (node.meta.submitted ? '查看作答' : '进入作答')
        : node.kind === 'MATERIAL' ? '进入阅读' : '进入环节')
      : '编辑环节设置';
    actions.unshift(`<button class="g3-btn primary" type="button" data-act="stage-open" data-key="${escapeHtml(node.key)}">`
      + `${enterLabel}${node.seq ? ` · 第 ${node.seq} 环节` : ''}</button>`);
    return `<div class="g3-tl-item">
      <div class="g3-tl-time"><strong>${escapeHtml(formatClock(node.time))}</strong>
        <span>${node.closesAt ? `至 ${escapeHtml(formatClock(node.closesAt))}` : '开放'}</span></div>
      <div class="g3-tl-dot ${kind.tone}"></div>
      <div class="g3-tl-card ${kind.tone}${selected ? ' active' : ''}"
        data-act="stage-select" data-key="${escapeHtml(node.key)}" role="button" tabindex="0">
        <div class="g3-tl-card-head">
          <span class="g3-tl-kind ${kind.tone}"><i>${kind.icon}</i>${escapeHtml(kind.label)}</span>
          ${seqBadge(node)}
          ${statePill(node.state)}
          ${node.private ? '<span class="g3-tl-private" title="教师私有内容不会进入学生端">仅自己可见</span>' : ''}
        </div>
        <div class="g3-tl-title">${escapeHtml(node.title)}</div>
        ${node.subtitle ? `<div class="g3-tl-subtitle">${escapeHtml(node.subtitle)}</div>` : ''}
        <div class="g3-tl-tags">${nodeTags(node)}</div>
        ${actions.length ? `<div class="g3-tl-actions">${actions.join('')}</div>` : ''}
      </div>
    </div>`;
  }

  function renderDayGroup(group, selectedKey, serverNow) {
    const isToday = group.key === dayKeyOf(serverNow || new Date());
    const numbered = group.nodes.filter(node => node.seq).length;
    const count = group.key === 'unscheduled'
      ? `${group.nodes.length} 个条目`
      : `本堂课 ${numbered} 个环节${numbered ? ` · 1–${numbered} 号` : ''}`
        + (group.nodes.length > numbered ? ` · 另有 ${group.nodes.length - numbered} 条待编排` : '');
    return `<section class="g3-tl-day">
      <div class="g3-tl-day-head"><span class="g3-tl-day-dot"></span>
        <strong>${escapeHtml(group.label)}</strong>
        <span class="g3-tl-day-count">${escapeHtml(count)}</span>
        ${isToday ? '<span class="g3-tl-now">现在</span>' : ''}</div>
      <div class="g3-tl-list">${group.nodes.map(node => renderNodeCard(node, node.key === selectedKey)).join('')}</div>
    </section>`;
  }

  function renderTimeline(nodes, selectedKey, serverNow, emptyHint) {
    const groups = groupNodesByDay(nodes, serverNow);
    if (!groups.length) return emptyState('当前筛选下没有环节', emptyHint);
    return `<div class="g3-timeline">${groups.map(group => renderDayGroup(group, selectedKey, serverNow)).join('')}</div>`;
  }

  function renderTimelineList(nodes, selectedKey) {
    if (!nodes.length) return emptyState('当前筛选下没有环节', '调整筛选条件或添加新环节');
    const rows = nodes.map(node => {
      const kind = KINDS[node.kind] || KINDS.MATERIAL;
      return `<tr><td>${node.seq ? `<span class="g3-tl-seq">${node.seq} / ${node.total}</span>` : '—'}</td>
        <td><div class="g3-cell-title">${escapeHtml(node.title)}</div>
          <div class="g3-cell-meta">${escapeHtml(kind.label)}</div></td>
        <td>${node.time ? formatTime(node.time) : '未安排'}</td>
        <td>${node.closesAt ? formatTime(node.closesAt) : '不限'}</td>
        <td>${statePill(node.state)}</td>
        <td><button class="g3-btn quiet" type="button" data-act="stage-open" data-key="${escapeHtml(node.key)}">进入环节</button></td></tr>`;
    }).join('');
    return `<section class="g3-panel"><div class="g3-table-wrap"><table class="g3-table">
      <thead><tr><th>序号</th><th>环节</th><th>开放</th><th>截止</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>${rows}</tbody></table></div></section>`;
  }

  function filterOptions(current, withPrivate) {
    const options = [['all', '全部环节'], ['MATERIAL', '资料阅读'], ['QUIZ', '预习习题']];
    if (withPrivate) {
      options.push(['PLAN', '备课包'], ['DRAFT', '待编排'], ['pending', '待处理 / 未开放']);
    } else {
      options.push(['todo', '仅看待完成']);
    }
    return options.map(([value, label]) =>
      `<option value="${value}"${optionMarkup(value, current)}>${label}</option>`).join('');
  }

  /* ------------------------------------------------------------------ *
   * 环节序号：一堂课 = 某课程 / 教学班在某一天的课前学习。
   * 换一天、换教学班、换课程就重新从 1 编号；每堂课内部的环节相互独立。
   * 教师私有备课包、待编排草稿与已归档内容不占序号。
   * ------------------------------------------------------------------ */

  function isNumberedStage(node) {
    return !node.private && (node.kind === 'MATERIAL' || node.kind === 'QUIZ')
      && node.state !== 'ARCHIVED';
  }

  // 分组键把「哪一天」放在前面，保证总览按日期先后呈现不同的课堂。
  function stageGroupKey(node, context) {
    const dateKey = dayKeyOf(node.opensAt || node.time);
    if (!dateKey) return '';
    return `${dateKey}|${context ? context.bindingId : state.bindingId}`;
  }

  function stageGroupLabel(node, context) {
    const date = new Date(node.opensAt || node.time);
    const dateText = Number.isNaN(date.getTime()) ? '未安排时间'
      : `${date.getMonth() + 1} 月 ${date.getDate()} 日`;
    return context ? `${dateText} · ${context.courseTitle} · ${context.className}` : dateText;
  }

  function numberStages(nodes, context) {
    const rank = { MATERIAL: 0, QUIZ: 1 };
    const stamp = node => {
      const value = new Date(node.opensAt || node.time || 0).getTime();
      return Number.isFinite(value) ? value : 0;
    };
    const buckets = new Map();
    nodes.filter(isNumberedStage).forEach(node => {
      const groupKey = stageGroupKey(node, context);
      if (!groupKey) return;
      if (!buckets.has(groupKey)) {
        buckets.set(groupKey, { key: groupKey, label: stageGroupLabel(node, context), nodes: [] });
      }
      buckets.get(groupKey).nodes.push(node);
    });
    const meta = new Map();
    buckets.forEach(bucket => {
      bucket.nodes.sort((a, b) => {
        if (stamp(a) !== stamp(b)) return stamp(a) - stamp(b);
        if (rank[a.kind] !== rank[b.kind]) return rank[a.kind] - rank[b.kind];
        return String(a.key).localeCompare(String(b.key));
      });
      bucket.nodes.forEach((node, index) => meta.set(node.key, {
        seq: index + 1,
        total: bucket.nodes.length,
        groupKey: bucket.key,
        groupLabel: bucket.label
      }));
    });
    return nodes.map(node => Object.assign({}, node,
      meta.get(node.key) || { seq: null, total: null, groupKey: '', groupLabel: '' }));
  }

  function buildStageGroups(nodes) {
    const groups = [];
    const index = new Map();
    nodes.filter(node => node.seq).slice()
      .sort((a, b) => String(a.groupKey).localeCompare(String(b.groupKey)) || a.seq - b.seq)
      .forEach(node => {
        if (!index.has(node.groupKey)) {
          index.set(node.groupKey, groups.length);
          groups.push({ key: node.groupKey, label: node.groupLabel, nodes: [] });
        }
        groups[index.get(node.groupKey)].nodes.push(node);
      });
    return groups;
  }

  // 上下环节只在同一堂课内跳转，不会串到别的班级 / 别的日期。
  function stageNodesOf(key) {
    const node = state.nodeIndex[key];
    if (!node || !node.groupKey) return [];
    const group = state.stageGroups.find(item => item.key === node.groupKey);
    return group ? group.nodes : [];
  }

  function seqBadge(node) {
    if (node.seq) {
      return `<span class="g3-tl-seq" title="${escapeHtml(node.groupLabel || '')} · 仅本堂课内编号">`
        + `第 ${node.seq} / ${node.total} 环节</span>`;
    }
    return '<span class="g3-tl-seq muted" title="教师私有或待编排内容不占学生环节的序号">不计入序号</span>';
  }

  // 总览按「堂课」分区显示所有组；独立环节页只显示当前这一堂课。
  function stageStepMarkup(node, activeKey) {
    const kind = KINDS[node.kind] || KINDS.MATERIAL;
    const done = node.kind === 'QUIZ' && Boolean(node.meta.submitted);
    const classes = [kind.tone, node.key === activeKey ? 'active' : '',
      done ? 'done' : '', node.state === 'UPCOMING' ? 'upcoming' : ''].filter(Boolean).join(' ');
    const hint = [kind.label, done ? '已提交' : '', node.state === 'UPCOMING' ? '未开放' : '']
      .filter(Boolean).join(' · ');
    return `<button type="button" class="g3-stage-step ${classes}" data-act="stage-open"
      data-key="${escapeHtml(node.key)}" title="第 ${node.seq} 环节 · ${escapeHtml(node.title)}">
      <span class="g3-stage-step-no">${node.seq}</span>
      <span class="g3-stage-step-text"><strong>${escapeHtml(node.title)}</strong><small>${escapeHtml(hint)}</small></span>
    </button>`;
  }

  function renderStageTrack(activeKey = '', onlyGroup = '') {
    const groups = state.stageGroups.filter(group => !onlyGroup || group.key === onlyGroup);
    if (!groups.length) return '';
    const total = groups.reduce((sum, group) => sum + group.nodes.length, 0);
    const blocks = groups.map(group => `<div class="g3-stage-group">
      <div class="g3-stage-group-label"><strong>${escapeHtml(group.label)}</strong>
        <span>本堂课 ${group.nodes.length} 个环节 · 独立编号 1–${group.nodes.length}</span></div>
      <div class="g3-stage-track-steps">${group.nodes.map(node => stageStepMarkup(node, activeKey)).join('')}</div>
    </div>`).join('');
    return `<div class="g3-stage-track">
      <div class="g3-stage-track-label"><strong>${onlyGroup ? '本堂课环节' : '按堂课编排'}</strong>
        <span>${onlyGroup
          ? `共 ${total} 个环节 · 序号只在这一堂课内排序`
          : `共 ${groups.length} 堂课 / ${total} 个环节 · 换班级、换课程、换日期都从 1 重新编号`}</span></div>
      ${blocks}
    </div>`;
  }

  function indexNodes(nodes) {
    state.nodes = nodes;
    state.nodeIndex = {};
    nodes.forEach(node => { state.nodeIndex[node.key] = node; });
    state.stageGroups = buildStageGroups(nodes);
    state.stageOrder = state.stageGroups.flatMap(group => group.nodes);
    if (!state.nodeIndex[state.selectedKey]) {
      state.selectedKey = '';
    }
  }

  /* ------------------------------------------------------------------ *
   * 教师「课前准备」：时间轴工作台
   * ------------------------------------------------------------------ */

  async function loadTeacherTimeline(context) {
    const binding = encodeURIComponent(state.bindingId);
    const [overview, taskRows, plans] = await Promise.all([
      loadOverview(context),
      api(`/education/group3/preview-tasks?bindingId=${binding}`),
      api(`/education/group3/plans?bindingId=${binding}`)
    ]);
    state.teacherTasks = taskRows || [];
    state.plans = plans || [];
    indexNodes(numberStages(collectTeacherNodes(overview, state.teacherTasks, state.plans), context));
    return overview;
  }

  async function renderTeacherPrepare() {
    clearObjectUrls();
    clearStageUrls();
    root.innerHTML = loadingState('正在加载课前时间轴');
    try {
      const context = await ensureContexts();
      if (!context) {
        root.innerHTML = emptyState('暂无获授权的课程', '请联系教务管理员确认教学班任课关系');
        return;
      }
      const overview = await loadTeacherTimeline(context);
      paintTeacherPrepare(overview, context);
    } catch (error) {
      root.innerHTML = errorState(error);
    }
  }

  function teacherStats(nodes) {
    const materials = nodes.filter(node => node.kind === 'MATERIAL').length;
    const quizzes = nodes.filter(node => node.kind === 'QUIZ').length;
    const drafts = nodes.filter(node => node.kind === 'DRAFT').length;
    const numbered = nodes.filter(node => node.seq).length;
    return `<div class="g3-tl-stats">
      <div class="g3-tl-stat"><small>本堂课环节（已编号）</small><strong>${numbered}</strong></div>
      <div class="g3-tl-stat"><small>资料阅读</small><strong>${materials}</strong></div>
      <div class="g3-tl-stat"><small>预习习题</small><strong>${quizzes}</strong></div>
      <div class="g3-tl-stat ${drafts ? 'warn' : ''}"><small>待编排 / 失败</small><strong>${drafts}</strong></div>
    </div>`;
  }

  function paintTeacherPrepare(overview, context) {
    const flow = createFromHash() || state.createFlow;
    if (flow && CREATE_FLOWS[flow]) {
      state.createFlow = flow;
      state.stageKey = '';
      paintCreatePage(flow);
      return;
    }
    const visible = filterNodes(state.nodes, state.prepareFilter);
    if (!state.nodeIndex[state.selectedKey]) state.selectedKey = '';
    const timeline = state.prepareView === 'timeline'
      ? renderTimeline(visible, state.selectedKey, overview.serverTime, '暂无环节')
      : renderTimelineList(visible, state.selectedKey);
    root.innerHTML = `<div class="g3-workspace">
      ${pageHead('课前准备', `${context.courseTitle} · ${context.className}`, context)}
      ${contextChips(context)}
      <details class="g3-overview-fold" open>
        <summary><span>按堂课总览</span><small>${state.stageGroups.length} 堂课 / ${state.stageOrder.length} 个已编号环节 · 点击可折叠</small></summary>
        <div class="g3-overview-fold-body">
          ${teacherStats(state.nodes)}
          ${renderStageTrack(state.selectedKey)}
        </div>
      </details>
      <div class="g3-tl-toolbar stacked">
        <div class="g3-tl-toolbar-row">
          <div class="g3-segment" data-view-tabs>
            <button type="button" data-act="view" data-view="timeline" class="${state.prepareView === 'timeline' ? 'active' : ''}">时间轴</button>
            <button type="button" data-act="view" data-view="list" class="${state.prepareView === 'list' ? 'active' : ''}">列表</button>
          </div>
          <div class="g3-tl-filters">
            <label for="g3Filter">筛选</label>
            <select id="g3Filter" class="g3-select">${filterOptions(state.prepareFilter, true)}</select>
          </div>
        </div>
        <div class="g3-tl-toolbar-row end">
          <div class="g3-actions">
            <button class="g3-btn" type="button" data-act="go-studio">课程建设</button>
            <button class="g3-btn" type="button" data-act="gen-plan">生成备课包</button>
            <button class="g3-btn primary" type="button" data-act="add-stage">+ 添加环节</button>
          </div>
        </div>
      </div>
      <div class="g3-timeline-layout">
        <div class="g3-tl-main">${timeline}</div>
        <aside class="g3-tl-detail" id="g3StageDetail">${renderTeacherDetail()}</aside>
      </div>
    </div>`;
    applyStageView();
  }

  function renderTeacherDetail() {
    const node = state.nodeIndex[state.selectedKey];
    if (!node) {
      return `<div class="g3-panel"><div class="g3-panel-head"><div class="g3-panel-title">环节详情</div></div>
        <div class="g3-panel-body">${emptyState('选择一个环节', '点击左侧时间轴上的卡片')}</div></div>`;
    }
    const kind = KINDS[node.kind] || KINDS.MATERIAL;
    const meta = node.meta || {};
    const rows = [];
    rows.push(['环节序号', node.seq ? `第 ${node.seq} / ${node.total} 环节` : '不计入序号（教师私有 / 待编排）']);
    rows.push(['环节类型', kind.label]);
    rows.push(['当前状态', STATE_LABELS[node.state] || node.state]);
    rows.push(['开放时间', node.opensAt ? formatTime(node.opensAt) : '未设置']);
    rows.push(['截止时间', node.closesAt ? formatTime(node.closesAt) : '不限']);
    if (node.kind === 'QUIZ') {
      rows.push(['解析公布', meta.answerOpensAt ? formatTime(meta.answerOpensAt) : '未设置']);
      rows.push(['提交进度', `${Number(meta.submissionCount || 0)} / ${Number(meta.targetCount || 0)} 人`]);
      rows.push(['题目数量', `${Number(meta.itemCount || 0)} 道`]);
    }
    if (node.kind === 'MATERIAL' || node.kind === 'DRAFT') {
      rows.push(['资料版本', `V${meta.versionNo || 1}`]);
      rows.push(['处理状态', labels[String(meta.processingStatus || '').toUpperCase()] || meta.processingStatus || '未知']);
    }
    if (node.kind === 'PLAN') {
      rows.push(['关联课次', meta.lessonTitle || '未关联']);
      rows.push(['备课材料', `${(meta.materials || []).length} 份`]);
      rows.push(['活动预设', `${(meta.templates || []).length} 项`]);
    }
    const actions = [];
    actions.push(`<button class="g3-btn primary" type="button" data-act="stage-open" data-key="${escapeHtml(node.key)}">`
      + `${node.seq ? `编辑第 ${node.seq} 环节设置` : '编辑环节设置'}</button>`);
    if (node.kind === 'MATERIAL') {
      actions.push(`<button class="g3-btn" type="button" data-act="stage-preview" data-key="${escapeHtml(node.key)}">预览资料</button>`);
      actions.push(`<button class="g3-btn" type="button" data-act="stage-versions" data-key="${escapeHtml(node.key)}">版本记录</button>`);
      actions.push(`<button class="g3-btn" type="button" data-act="stage-new-version" data-key="${escapeHtml(node.key)}">上传新版本</button>`);
      actions.push(`<button class="g3-btn danger" type="button" data-act="stage-revoke" data-key="${escapeHtml(node.key)}">撤回发布</button>`);
    } else if (node.kind === 'DRAFT') {
      if (node.state === 'FAILED') {
        actions.push(`<button class="g3-btn primary" type="button" data-act="stage-failure" data-key="${escapeHtml(node.key)}">查看失败原因与下一步</button>`);
        actions.push(`<button class="g3-btn" type="button" data-act="stage-new-version" data-key="${escapeHtml(node.key)}">上传新版本替换</button>`);
      } else {
        actions.push(`<button class="g3-btn primary" type="button" data-act="stage-release" data-key="${escapeHtml(node.key)}">设置开放时间并发布</button>`);
        actions.push(`<button class="g3-btn" type="button" data-act="stage-preview" data-key="${escapeHtml(node.key)}">预览资料</button>`);
      }
    } else if (node.kind === 'QUIZ') {
      actions.push(`<button class="g3-btn" type="button" data-act="add-stage">继续发布预习</button>`);
    } else if (node.kind === 'PLAN') {
      actions.push(`<button class="g3-btn" type="button" data-act="gen-plan">新建备课包</button>`);
    }
    actions.push(`<button class="g3-btn danger" type="button" data-act="stage-remove" data-key="${escapeHtml(node.key)}">删除环节</button>`);
    const privateNotes = node.kind === 'PLAN' && meta.privateNotes
      ? `<div class="g3-inline-note warn"><strong>教师私有备注</strong><br>${escapeHtml(meta.privateNotes)}</div>` : '';
    const failure = node.state === 'FAILED'
      ? `<div class="g3-inline-note warn"><strong>处理结果：${escapeHtml(meta.processingMessage || '资料转换失败')}</strong><br>
          上传新版本后可重新发布。</div>` : '';
    return `<div class="g3-panel g3-detail-panel">
      <div class="g3-panel-head"><div class="g3-panel-title">环节详情
        <span class="count">${escapeHtml(kind.label)}</span></div>${statePill(node.state)}</div>
      <div class="g3-panel-body g3-list">
        <div class="g3-detail-title">${escapeHtml(node.title)}</div>
        ${node.subtitle ? `<div class="g3-help">${escapeHtml(node.subtitle)}</div>` : ''}
        ${failure}${privateNotes}
        <div class="g3-divider"></div>
        ${rows.map(([label, value]) => `<div class="g3-detail-row"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`).join('')}
        ${(node.tags || []).length ? `<div class="g3-tl-tags">${nodeTags(node)}</div>` : ''}
        <div class="g3-divider"></div>
        <div class="g3-actions">${actions.join('')}</div>
      </div></div>`;
  }

  function paintTeacherDetail() {
    const box = document.getElementById('g3StageDetail');
    if (box) box.innerHTML = renderTeacherDetail();
    root.querySelectorAll('.g3-tl-card').forEach(card => {
      card.classList.toggle('active', card.dataset.key === state.selectedKey);
    });
  }

  /* ------------------------------------------------------------------ *
   * 独立分步创建页 + 资产选择器
   * 只复用 /education/group3/overview 已经返回的真实资料版本与题目版本，
   * 不新增接口、不重复上传文件、不重复录入题目。
   * ------------------------------------------------------------------ */

  const CREATE_FLOWS = { material: '资料阅读环节', quiz: '预习习题环节', plan: '备课包' };
  const CREATE_DRAFT_KEY = 'zhijiao.group3.createDraft';

  function collectForm(form) {
    const values = {};
    form.querySelectorAll('[name]').forEach(input => {
      const name = input.name;
      if (!name) return;
      if (input.type === 'checkbox') {
        if (!Array.isArray(values[name])) values[name] = [];
        if (input.checked) values[name].push(input.value);
      } else if (input.type === 'radio') {
        if (input.checked) values[name] = input.value;
        else if (!(name in values)) values[name] = '';
      } else if (input.type === 'file') {
        if (input.files && input.files[0]) values[name] = input.files[0];
      } else {
        values[name] = input.value;
      }
    });
    return values;
  }

  function pickerShell({ name, multiple, placeholder, items, selected = [], emptyHint, extra = '' }) {
    const chosen = new Set((selected || []).map(String));
    const rows = items.map(item => `<label class="g3-picker-item" data-picker-item
        data-search="${escapeHtml(item.search || '')}">
        <input type="${multiple ? 'checkbox' : 'radio'}" name="${escapeHtml(name)}"
          value="${escapeHtml(item.value)}"${chosen.has(String(item.value)) ? ' checked' : ''}>
        <span class="g3-picker-item-main"><strong>${escapeHtml(item.title)}</strong>
          <small>${escapeHtml(item.meta || '')}</small>
          ${item.desc ? `<em>${escapeHtml(item.desc)}</em>` : ''}</span>
        <span class="g3-picker-badge">${escapeHtml(item.badge || '')}</span></label>`).join('');
    return `<div class="g3-picker" data-picker>
      <div class="g3-picker-head">
        <input class="g3-input" type="search" data-picker-search placeholder="${escapeHtml(placeholder)}"
          aria-label="${escapeHtml(placeholder)}">
        <div class="g3-picker-tools"><span class="g3-picker-count" data-picker-count></span>
          ${multiple ? `<button class="g3-btn quiet" type="button" data-picker-all>全选</button>
            <button class="g3-btn quiet" type="button" data-picker-clear>清空</button>` : ''}</div>
      </div>
      <div class="g3-picker-list" data-picker-list>${rows}</div>
      <div class="g3-picker-empty${rows ? '' : ' static'}" data-picker-empty${rows ? ' hidden' : ''}>${escapeHtml(emptyHint || '')}</div>
      ${extra ? `<div class="g3-picker-foot">${extra}</div>` : ''}
    </div>`;
  }

  function questionPicker(overview, data = {}, options = {}) {
    const multiple = options.multiple !== false;
    const name = options.name || (multiple ? 'questionVersionIds' : 'questionVersionId');
    const items = (overview.questionVersions || []).map(question => {
      const kind = labels[question.questionKind] || question.questionKind || '题目';
      return {
        value: question.versionId,
        title: question.title || '未命名题目',
        meta: `${kind} · ${Number(question.maxScore || 0)} 分 · V${escapeHtml(question.versionNo || 1)}`,
        desc: question.stem || '',
        badge: `V${question.versionNo || 1}`,
        search: `${question.title || ''} ${question.stem || ''} ${kind}`
      };
    });
    return pickerShell({
      name, multiple, placeholder: '搜索题目名称、题干或题型', items,
      selected: data[name] || [], emptyHint: '题库里还没有可用题目版本',
      extra: options.extra || ''
    });
  }

  function materialPicker(overview, data = {}, options = {}) {
    const name = options.name || 'materialVersionIds';
    const items = (overview.materialVersions || []).map(item => ({
      value: item.versionId,
      title: item.title || '未命名资料',
      meta: `${item.mimeType || '文件'} · ${Number(item.pageCount || 0)} 页`,
      desc: item.createdAt ? `上传于 ${formatTime(item.createdAt)}` : '',
      badge: `V${item.versionNo || 1}`,
      search: `${item.title || ''} ${item.mimeType || ''}`
    }));
    return pickerShell({
      name, multiple: options.multiple !== false,
      placeholder: '搜索资料名称或文件类型', items,
      selected: data[name] || [], emptyHint: '课程资源库里还没有可复用的资料版本',
      extra: options.extra || ''
    });
  }

  function wirePickers(container) {
    container.querySelectorAll('[data-picker]').forEach(picker => {
      const search = picker.querySelector('[data-picker-search]');
      const items = [...picker.querySelectorAll('[data-picker-item]')];
      const empty = picker.querySelector('[data-picker-empty]');
      const count = picker.querySelector('[data-picker-count]');
      const inputs = [...picker.querySelectorAll('input[type="checkbox"], input[type="radio"]')];
      if (empty && items.length) empty.textContent = '没有匹配的条目，换个关键词试试';
      const refreshCount = () => {
        const chosen = inputs.filter(input => input.checked).length;
        if (count) count.textContent = `已选 ${chosen} 项`;
      };
      const applyFilter = () => {
        const query = String(search?.value || '').trim().toLowerCase();
        let shown = 0;
        items.forEach(item => {
          const match = !query || String(item.dataset.search || '').toLowerCase().includes(query);
          item.hidden = !match;
          if (match) shown += 1;
        });
        if (empty) empty.hidden = items.length ? shown > 0 : false;
      };
      search?.addEventListener('input', applyFilter);
      picker.addEventListener('change', refreshCount);
      picker.querySelector('[data-picker-all]')?.addEventListener('click', () => {
        inputs.forEach(input => { input.checked = true; });
        refreshCount();
      });
      picker.querySelector('[data-picker-clear]')?.addEventListener('click', () => {
        inputs.forEach(input => { input.checked = false; });
        refreshCount();
      });
      applyFilter();
      refreshCount();
    });
  }

  function mountStepPage({ eyebrow, title, subtitle, steps, data = {}, submitLabel, onSubmit, onWire, onEnter }) {
    const nav = steps.map((step, index) => `<button type="button" class="g3-step-tab" data-step-go="${index}">
        <span class="g3-step-no">${index + 1}</span>
        <span class="g3-step-tab-text"><strong>${escapeHtml(step.label)}</strong>
          <small>${escapeHtml(step.hint || '')}</small></span></button>`).join('');
    const panels = steps.map((step, index) => `<section class="g3-step-panel" data-step-panel="${index}"${index ? ' hidden' : ''}>
        <div class="g3-step-head"><h2>${escapeHtml(step.label)}</h2><p>${escapeHtml(step.intro || '')}</p></div>
        <div class="g3-step-content">${typeof step.body === 'function' ? step.body(data) : step.body}</div>
      </section>`).join('');
    root.innerHTML = `<div class="g3-create-page">
      <div class="g3-create-topbar">
        <button class="g3-btn" type="button" data-create-back>← 返回课前准备总览</button>
        <div class="g3-create-breadcrumb">${escapeHtml(eyebrow)} · ${escapeHtml(title)}</div>
        <div class="g3-create-counter">第 <strong data-step-current>1</strong> / ${steps.length} 步</div>
      </div>
      <div class="g3-create-title"><h1>${escapeHtml(title)}</h1><p>${escapeHtml(subtitle)}</p></div>
      <div class="g3-create-scope">环节按「课程 / 教学班 / 开放日期」归入对应的一堂课，该堂课内部从 1 重新编号；在「发布设置」里可切换目标教学班，开放日期决定它属于哪一天。</div>
      <form id="g3CreateForm" class="g3-create-grid" novalidate>
        <aside class="g3-step-nav">${nav}
          <div class="g3-step-nav-note">分步填写，切换步骤不会丢失已填内容；发布前可随时回到任意一步修改。</div></aside>
        <div class="g3-step-main">
          ${panels}
          <div class="g3-step-foot">
            <button class="g3-btn" type="button" data-create-back>取消</button>
            <button class="g3-btn" type="button" data-step-prev>← 上一步</button>
            <button class="g3-btn" type="button" data-step-next>下一步 →</button>
            <button class="g3-btn primary" type="submit" data-step-submit hidden>${escapeHtml(submitLabel || '确认发布')}</button>
          </div>
        </div>
      </form>
    </div>`;
    const form = root.querySelector('#g3CreateForm');
    const panelsEls = [...root.querySelectorAll('[data-step-panel]')];
    const tabs = [...root.querySelectorAll('[data-step-go]')];
    const prev = root.querySelector('[data-step-prev]');
    const next = root.querySelector('[data-step-next]');
    const submit = root.querySelector('[data-step-submit]');
    const currentLabel = root.querySelector('[data-step-current]');
    let current = 0;
    const go = index => {
      const wanted = Number(index);
      current = Math.max(0, Math.min(steps.length - 1, Number.isFinite(wanted) ? wanted : 0));
      panelsEls.forEach(panel => { panel.hidden = Number(panel.dataset.stepPanel) !== current; });
      tabs.forEach(tab => { tab.classList.toggle('active', Number(tab.dataset.stepGo) === current); });
      if (prev) prev.disabled = current === 0;
      if (next) next.hidden = current === steps.length - 1;
      if (submit) submit.hidden = current !== steps.length - 1;
      if (currentLabel) currentLabel.textContent = String(current + 1);
      if (onEnter) onEnter(current, form);
    };
    tabs.forEach(tab => tab.addEventListener('click', () => go(tab.dataset.stepGo)));
    prev?.addEventListener('click', () => go(current - 1));
    next?.addEventListener('click', () => go(current + 1));
    root.querySelectorAll('[data-create-back]').forEach(button =>
      button.addEventListener('click', closeCreatePage));
    form.addEventListener('submit', event => {
      event.preventDefault();
      onSubmit(collectForm(form), form, submit);
    });
    wirePickers(root);
    if (onWire) onWire(form, { go, collect: () => collectForm(form) });
    go(0);
  }

  function createFromHash() {
    try {
      const match = String(location.hash || '').match(/create=([^&]+)/);
      return match ? decodeURIComponent(match[1]) : '';
    } catch (_) { return ''; }
  }

  function writeCreateHash(flow) {
    state.createFlow = flow || '';
    state.stageKey = '';
    try {
      const base = location.pathname + location.search;
      if (window.history && typeof window.history.pushState === 'function') {
        window.history.pushState(null, '', base + (flow ? '#create=' + encodeURIComponent(flow) : ''));
      } else {
        location.hash = flow ? 'create=' + encodeURIComponent(flow) : '';
      }
    } catch (_) { /* 不支持 history API 时只依赖内存状态 */ }
  }

  function clearCreateHash() {
    state.createFlow = '';
    state.createDraft = {};
    try { sessionStorage.removeItem(CREATE_DRAFT_KEY); } catch (_) { /* 忽略 */ }
    try {
      if (window.history && typeof window.history.replaceState === 'function') {
        window.history.replaceState(null, '', location.pathname + location.search);
      }
    } catch (_) { /* 忽略 */ }
  }

  function saveCreateDraft(flow, draft) {
    state.createDraft = state.createDraft || {};
    state.createDraft[flow] = draft || {};
    try { sessionStorage.setItem(CREATE_DRAFT_KEY, JSON.stringify(state.createDraft)); } catch (_) { /* 忽略 */ }
  }

  function readCreateDraft(flow) {
    if (state.createDraft && state.createDraft[flow]) return state.createDraft[flow];
    try {
      const parsed = JSON.parse(sessionStorage.getItem(CREATE_DRAFT_KEY) || '{}');
      return parsed[flow] || {};
    } catch (_) { return {}; }
  }

  function openCreateFlow(flow, draft) {
    if (!CREATE_FLOWS[flow] || role !== 'teacher') return;
    closeModal();
    if (draft) saveCreateDraft(flow, draft);
    const query = new URLSearchParams(location.search);
    if (Number(query.get('g')) !== routes.prepare.g || Number(query.get('i')) !== routes.prepare.i) {
      location.href = buildUrl(`?g=${routes.prepare.g}&i=${routes.prepare.i}#create=${encodeURIComponent(flow)}`);
      return;
    }
    writeCreateHash(flow);
    clearObjectUrls();
    clearStageUrls();
    paintCreatePage(flow);
  }

  function closeCreatePage() {
    clearCreateHash();
    clearStageUrls();
    refreshRoute();
  }

  function paintCreatePage(flow) {
    if (!CREATE_FLOWS[flow] || role !== 'teacher') { clearCreateHash(); return refreshRoute(); }
    const context = currentContext();
    if (!context || !state.overview) { clearCreateHash(); return refreshRoute(); }
    const data = readCreateDraft(flow);
    if (flow === 'quiz') renderQuizCreate(context, data);
    else if (flow === 'material') renderMaterialCreate(context, data);
    else renderPlanCreate(context, data);
  }

  /* ------------------------------------------------------------------ *
   * 添加环节：类型选择 → 独立分步创建页
   * ------------------------------------------------------------------ */

  function bindingOptions(current) {
    const value = current == null ? state.bindingId : current;
    return state.contexts.map(item =>
      `<option value="${escapeHtml(item.bindingId)}"${optionMarkup(item.bindingId, value)}>`
      + `${escapeHtml(item.courseTitle)} · ${escapeHtml(item.className)}</option>`).join('');
  }

  function lessonOptions(overview, optional = true, current = '') {
    const lessons = (overview.lessons || []).map(lesson =>
      `<option value="${escapeHtml(lesson.lessonId)}"${optionMarkup(lesson.lessonId, current)}>${escapeHtml(lesson.title)}`
      + `${lesson.scheduledAt ? ` · ${formatTime(lesson.scheduledAt)}` : ''}</option>`).join('');
    return `${optional ? `<option value=""${optionMarkup('', current)}>不关联课次</option>` : ''}${lessons}`;
  }

  function unitOptions(overview, current = '') {
    return `<option value=""${optionMarkup('', current)}>不归档章节</option>${(overview.outline || []).map(unit =>
      `<option value="${escapeHtml(unit.unitId)}"${optionMarkup(unit.unitId, current)}>${escapeHtml(unit.title)}</option>`).join('')}`;
  }

  function showAddStageModal() {
    const overview = state.overview || {};
    const readyCount = (overview.materialVersions || []).length;
    const questionCount = (overview.questionVersions || []).length;
    const modal = openModal({
      title: '在时间轴上添加环节',
      wide: true,
      body: `<p class="g3-help">选择环节类型，下一步可从资源库或题库直接选用。</p>
        <div class="g3-wizard">
          <button type="button" class="g3-wizard-card blue" data-create-flow="material">
            <i>▤</i><strong>资料阅读</strong>
            <span>从资源库选用已有版本 → 设置开放时间并发布</span>
            <small>可直接复用 ${readyCount} 个可用资料版本，也可上传新文件</small></button>
          <button type="button" class="g3-wizard-card teal" data-create-flow="quiz">
            <i>✎</i><strong>预习习题</strong>
            <span>从题库导入题目 → 写导学说明 → 设置开放窗口</span>
            <small>可直接导入 ${questionCount} 个可用题目版本</small></button>
          <button type="button" class="g3-wizard-card amber" data-create-flow="plan">
            <i>◈</i><strong>备课包（教师私有）</strong>
            <span>挑选已有课件与随堂题 → 保存为某课次的私有方案</span>
            <small>私有备注与答案不会进入学生端</small></button>
        </div>`
    });
    modal.querySelectorAll('[data-create-flow]').forEach(button => {
      button.addEventListener('click', () => openCreateFlow(button.dataset.createFlow));
    });
  }

  function asArray(value) {
    if (Array.isArray(value)) return value;
    if (value == null || value === '') return [];
    return [value];
  }

  // 题库里没有目标题目时才新建；新建后刷新 overview 并回到原创建页，已填内容不丢失。
  function wireInlineQuestionCreate(form, apiObj, flow) {
    form.querySelectorAll('[data-picker-new-question]').forEach(button => {
      button.addEventListener('click', () => {
        saveCreateDraft(flow, apiObj.collect());
        showQuestionModal(null, () => {
          const draft = readCreateDraft(flow);
          const versions = state.overview?.questionVersions || [];
          const newest = versions.reduce((best, item) =>
            !best || Number(item.versionId) > Number(best.versionId) ? item : best, null);
          if (newest) {
            const multiple = flow === 'quiz';
            const name = multiple ? 'questionVersionIds' : 'quizQuestionVersionId';
            const chosen = new Set(asArray(draft[name]).map(String));
            chosen.add(String(newest.versionId));
            draft[name] = multiple ? [...chosen] : String(newest.versionId);
            saveCreateDraft(flow, draft);
          }
          paintCreatePage(flow);
        });
      });
    });
  }

  function summaryRows(rows) {
    return `<div class="g3-summary-grid">${rows.map(([label, value]) =>
      `<div><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`).join('')}</div>`;
  }

  function renderMaterialCreate(context, data) {
    const overview = state.overview || {};
    const ready = overview.materialVersions || [];
    const source = data.source || (ready.length ? 'existing' : 'upload');
    const steps = [
      {
        label: '选择来源', hint: '资源库复用 / 上传新文件',
        intro: '如果课程资源库里已经有这份资料，直接选用即可；只有库里没有时才需要上传文件。',
        body: () => `<div class="g3-source-grid">
            <label class="g3-source-card blue"><input type="radio" name="source" value="existing"${source === 'existing' ? ' checked' : ''}>
              <i>▤</i><strong>从课程资源库直接选用</strong>
              <small>已有 ${ready.length} 个可用资料版本，选完即可发布，不重复上传</small><em>推荐</em></label>
            <label class="g3-source-card"><input type="radio" name="source" value="upload"${source === 'upload' ? ' checked' : ''}>
              <i>⇧</i><strong>上传新文件</strong>
              <small>资源库里没有这份资料时使用，仍可归档到章节</small><em>新文件</em></label>
          </div>`
      },
      {
        label: '挑选资料', hint: '已有版本 / 新文件',
        intro: '可勾选多个资料版本，每个版本生成一个独立资料环节；也能按关键词快速找到目标资料。',
        body: () => `<div data-source-panel="existing">
            <div class="g3-picker-lead"><strong>课程资源库 · 已处理完成的资料版本</strong>
              <span>${ready.length} 个可复用版本</span></div>
            ${materialPicker(overview, data, { name: 'materialVersionIds' })}
            <div class="g3-inline-note">仅列出已就绪的版本。</div>
          </div>
          <div data-source-panel="upload" hidden>
            <div class="g3-form-grid">
              <div class="g3-field full"><label for="g3StageTitle">资料名称</label>
                <input id="g3StageTitle" class="g3-input" name="title" maxlength="200"
                  value="${escapeHtml(data.title || '')}" placeholder="不填写时使用文件名"></div>
              <div class="g3-field full"><label for="g3StageUnit">归档章节</label>
                <select id="g3StageUnit" class="g3-select" name="unitId">${unitOptions(overview, data.unitId)}</select></div>
              <div class="g3-field full"><label for="g3StageFile">选择文件</label>
                <input id="g3StageFile" class="g3-input" name="file" type="file"
                  accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md"></div>
            </div>
            <div class="g3-inline-note">上传成功后按开放窗口发布。</div>
          </div>`
      },
      {
        label: '发布设置', hint: '教学班 / 开放窗口 / 确认',
        intro: '设置开放与截止时间，并核对本环节将写入的真实发布记录。',
        body: () => `<div class="g3-form-grid">
            <div class="g3-field"><label for="g3StageBinding">发布到教学班</label>
              <select id="g3StageBinding" class="g3-select" name="bindingId">${bindingOptions(data.bindingId)}</select></div>
            <div class="g3-field"><label>发布范围</label>
              <div class="g3-static-field">按所选教学班发布，学生端只显示本班已开放内容</div></div>
            <div class="g3-field"><label for="g3StageOpen">开放时间</label>
              <input id="g3StageOpen" class="g3-input" type="datetime-local" name="opensAt"
                value="${escapeHtml(data.opensAt || localInput())}"></div>
            <div class="g3-field"><label for="g3StageClose">截止时间</label>
              <input id="g3StageClose" class="g3-input" type="datetime-local" name="closesAt"
                value="${escapeHtml(data.closesAt || futureInput(14))}"></div>
          </div>
          <div class="g3-summary" data-create-summary></div>`
      }
    ];
    mountStepPage({
      eyebrow: `${context.courseTitle} · ${context.className}`,
      title: '添加资料阅读环节',
      subtitle: '课程资源库里已有的资料直接选用即可，不必重新上传；库里没有时再上传新文件。',
      steps, data, submitLabel: '加入时间轴',
      onWire: form => {
        const sync = () => {
          const chosen = form.querySelector('input[name="source"]:checked')?.value || 'existing';
          form.querySelectorAll('[data-source-panel]').forEach(panel => {
            panel.hidden = panel.dataset.sourcePanel !== chosen;
          });
        };
        form.querySelectorAll('input[name="source"]').forEach(radio =>
          radio.addEventListener('change', sync));
        sync();
      },
      onEnter: (index, form) => {
        if (index !== steps.length - 1) return;
        const box = form.querySelector('[data-create-summary]');
        if (!box) return;
        const values = collectForm(form);
        const ids = asArray(values.materialVersionIds);
        const names = ids.map(id => (ready.find(item => String(item.versionId) === String(id)) || {}).title || `版本 ${id}`);
        const count = values.source === 'upload' ? 1 : Math.max(ids.length, 1);
        box.innerHTML = `<h3>发布确认</h3>${summaryRows([
          ['来源', values.source === 'upload' ? '上传新文件' : '复用课程资源库'],
          ['生成环节', `${count} 个资料环节`],
          ['开放', formatTime(values.opensAt)],
          ['截止', formatTime(values.closesAt)]
        ])}${values.source === 'existing' && names.length
          ? `<div class="g3-summary-list"><span>选用的资料</span><ul>${names.map(name => `<li>${escapeHtml(name)}</li>`).join('')}</ul></div>`
          : ''}${values.source === 'upload' && values.title
          ? `<div class="g3-summary-list"><span>上传资料</span><ul><li>${escapeHtml(values.title)}</li></ul></div>` : ''}`;
      },
      onSubmit: async (values, form, button) => {
        let opensAt, closesAt;
        try {
          opensAt = isoFromInput(values.opensAt);
          closesAt = isoFromInput(values.closesAt);
          if (new Date(closesAt) <= new Date(opensAt)) throw new Error('截止时间必须晚于开放时间');
        } catch (error) { toast(error.message, true); return; }
        const bindingId = Number(values.bindingId);
        setBusy(button, true, '发布中…');
        try {
          if (values.source === 'upload') {
            if (!values.file) throw new Error('请选择要上传的文件');
            const payload = new FormData();
            payload.append('bindingId', String(bindingId));
            payload.append('title', String(values.title || '').trim());
            if (values.unitId) payload.append('unitId', values.unitId);
            payload.append('file', values.file);
            const uploaded = await api('/education/group3/resources', { method: 'POST', body: payload });
            if (String(uploaded.processingStatus).toUpperCase() === 'READY' && uploaded.versionId) {
              await api('/education/group3/releases', {
                method: 'POST',
                body: { bindingId, resourceVersionId: uploaded.versionId, opensAt, closesAt }
              });
              toast('资料已处理成功，环节已加入时间轴');
            } else {
              toast('资料已上传但处理失败，已放入「待编排」，请按提示重新上传新版本', true);
            }
          } else {
            const ids = asArray(values.materialVersionIds).map(Number);
            if (!ids.length) throw new Error('请从课程资源库至少勾选一个资料版本');
            for (const versionId of ids) {
              await api('/education/group3/releases', {
                method: 'POST', body: { bindingId, resourceVersionId: versionId, opensAt, closesAt }
              });
            }
            toast(`已复用课程资源库中 ${ids.length} 份资料，生成 ${ids.length} 个资料环节`);
          }
        } catch (error) {
          toast(actionableMessage(error), true);
          setBusy(button, false);
          return;
        }
        state.bindingId = String(bindingId);
        localStorage.setItem(`zhijiao.group3.binding.${role}`, state.bindingId);
        clearCreateHash();
        clearOverview();
        state.selectedKey = '';
        refreshRoute();
      }
    });
  }

  function renderQuizCreate(context, data) {
    const overview = state.overview || {};
    const questions = overview.questionVersions || [];
    const materials = overview.materialVersions || [];
    const steps = [
      {
        label: '基本信息', hint: '教学班 / 课次 / 名称',
        intro: '先确定这一批预习环节发布到哪个教学班、叫什么名字，以及给学生的导学说明。',
        body: () => `<div class="g3-form-grid">
            <div class="g3-field"><label for="g3QuizBinding">发布到教学班</label>
              <select id="g3QuizBinding" class="g3-select" name="bindingId">${bindingOptions(data.bindingId)}</select></div>
            <div class="g3-field"><label for="g3QuizLesson">关联课次</label>
              <select id="g3QuizLesson" class="g3-select" name="lessonId">${lessonOptions(overview, true, data.lessonId)}</select></div>
            <div class="g3-field full"><label for="g3QuizTitle">环节名称</label>
              <input id="g3QuizTitle" class="g3-input" name="title" maxlength="200" required
                value="${escapeHtml(data.title || '')}" placeholder="例如：第 3 章课前预习"></div>
            <div class="g3-field full"><label for="g3QuizInstructions">导学说明</label>
              <textarea id="g3QuizInstructions" class="g3-textarea" name="instructions"
                placeholder="告诉学生先看什么、要完成什么、何时截止">${escapeHtml(data.instructions || '')}</textarea></div>
          </div>
          <div class="g3-inline-note">多题时名称会自动追加「第 N / M 题」。</div>`
      },
      {
        label: '从题库导入题目', hint: '直接选用已有题目版本',
        intro: '题库里的题目直接勾选即可，不需要重新录入；可以按名称、题干或题型搜索。',
        body: () => `<div class="g3-picker-lead"><strong>课程题库 · 可直接导入的题目版本</strong>
            <span>${questions.length} 个可用版本</span>
            <button class="g3-btn quiet" type="button" data-picker-new-question>+ 题库里没有？新建题目</button></div>
          ${questionPicker(overview, data, { multiple: true, name: 'questionVersionIds' })}
          <div class="g3-inline-note warn">每题生成一个独立环节，共用同一开放窗口与导学说明。</div>`
      },
      {
        label: '同步开放资料', hint: '从资源库直接选用',
        intro: '预习需要配套课件或导学案时，直接从课程资源库勾选已有版本，不会重新上传文件。',
        body: () => `<div class="g3-picker-lead"><strong>课程资源库 · 同步开放的资料</strong>
            <span>${materials.length} 个可用版本</span></div>
          ${materialPicker(overview, data, { name: 'materialVersionIds' })}
          <div class="g3-inline-note">多题批次只在第一个环节发布资料。</div>`
      },
      {
        label: '时间与确认', hint: '开放 / 截止 / 解析公布',
        intro: '设置三个真实时间字段并核对发布内容，确认后写入当前教学班的发布记录。',
        body: () => `<div class="g3-form-grid">
            <div class="g3-field"><label for="g3QuizOpen">开放时间</label>
              <input id="g3QuizOpen" class="g3-input" type="datetime-local" name="opensAt"
                value="${escapeHtml(data.opensAt || localInput())}"></div>
            <div class="g3-field"><label for="g3QuizClose">截止时间</label>
              <input id="g3QuizClose" class="g3-input" type="datetime-local" name="closesAt"
                value="${escapeHtml(data.closesAt || futureInput(3))}"></div>
            <div class="g3-field"><label for="g3QuizAnswerOpen">解析公布时间</label>
              <input id="g3QuizAnswerOpen" class="g3-input" type="datetime-local" name="answerOpensAt"
                value="${escapeHtml(data.answerOpensAt || futureInput(4))}"></div>
            <div class="g3-field"><label>提交规则</label>
              <div class="g3-static-field">重复提交由服务端幂等处理</div></div>
          </div>
          <div class="g3-summary" data-create-summary></div>`
      }
    ];
    mountStepPage({
      eyebrow: `${context.courseTitle} · ${context.className}`,
      title: '添加预习习题环节',
      subtitle: '题目从课程题库直接导入、资料从课程资源库直接选用；分步填写，不再把所有字段堆在一个弹窗里。',
      steps, data, submitLabel: '发布到时间轴',
      onWire: (form, apiObj) => wireInlineQuestionCreate(form, apiObj, 'quiz'),
      onEnter: (index, form) => {
        if (index !== steps.length - 1) return;
        const box = form.querySelector('[data-create-summary]');
        if (!box) return;
        const values = collectForm(form);
        const questionIds = asArray(values.questionVersionIds);
        const materialIds = asArray(values.materialVersionIds);
        const names = questionIds.map(id =>
          (questions.find(item => String(item.versionId) === String(id)) || {}).title || `题目版本 ${id}`);
        const stageCount = questionIds.length || 1;
        box.innerHTML = `<h3>发布确认</h3>${summaryRows([
          ['环节名称', values.title || '未填写'],
          ['生成环节', `${stageCount} 个${questionIds.length > 1 ? '（每题一个独立环节）' : ''}`],
          ['导入题目', `${questionIds.length} 道`],
          ['同步资料', `${materialIds.length} 份`],
          ['开放', formatTime(values.opensAt)],
          ['截止', formatTime(values.closesAt)],
          ['解析公布', formatTime(values.answerOpensAt)]
        ])}${names.length
          ? `<div class="g3-summary-list"><span>导入的题目</span><ul>${names.map(name => `<li>${escapeHtml(name)}</li>`).join('')}</ul></div>`
          : '<div class="g3-inline-note">未勾选题目，将生成一个纯资料导学环节。</div>'}`;
      },
      onSubmit: async (values, form, button) => {
        let base;
        try {
          const title = String(values.title || '').trim();
          if (!title) throw new Error('请填写环节名称');
          const opensAt = isoFromInput(values.opensAt);
          const closesAt = isoFromInput(values.closesAt);
          const answerOpensAt = isoFromInput(values.answerOpensAt);
          if (new Date(closesAt) <= new Date(opensAt)) throw new Error('截止时间必须晚于开放时间');
          if (new Date(answerOpensAt) < new Date(closesAt)) throw new Error('解析公布时间不能早于截止时间');
          base = {
            bindingId: Number(values.bindingId),
            lessonId: values.lessonId ? Number(values.lessonId) : null,
            title,
            instructions: String(values.instructions || '').trim(),
            opensAt, closesAt, answerOpensAt,
            resourceVersionIds: asArray(values.materialVersionIds).map(Number)
          };
        } catch (error) { toast(error.message, true); return; }
        const questionIds = asArray(values.questionVersionIds).map(Number);
        const tasks = questionIds.length ? questionIds : [null];
        setBusy(button, true, '发布中…');
        let done = 0;
        try {
          for (let index = 0; index < tasks.length; index += 1) {
            const body = {
              ...base,
              title: tasks.length > 1 ? `${base.title} · 第 ${index + 1} / ${tasks.length} 题` : base.title,
              questionVersionId: tasks[index],
              resourceVersionIds: index === 0 ? base.resourceVersionIds : []
            };
            await api('/education/group3/preview-tasks', { method: 'POST', body });
            done += 1;
          }
        } catch (error) {
          toast(done
            ? `已发布 ${done} / ${tasks.length} 个环节，剩余未发布：${actionableMessage(error)}`
            : actionableMessage(error), true);
          setBusy(button, false);
          return;
        }
        state.bindingId = String(base.bindingId);
        localStorage.setItem(`zhijiao.group3.binding.${role}`, state.bindingId);
        toast(tasks.length > 1
          ? `已从题库导入 ${questionIds.length} 道题，生成 ${tasks.length} 个独立预习环节`
          : '预习习题环节已加入时间轴');
        clearCreateHash();
        clearOverview();
        state.selectedKey = '';
        refreshRoute();
      }
    });
  }

  function renderPlanCreate(context, data) {
    const overview = state.overview || {};
    const lessons = overview.lessons || [];
    const materials = overview.materialVersions || [];
    if (!lessons.length) {
      toast('当前教学班还没有课次，请先由教务侧维护课次后再保存备课包', true);
      closeCreatePage();
      return;
    }
    const steps = [
      {
        label: '基本信息', hint: '教学班 / 课次 / 名称',
        intro: '备课包按课次定位，是时间轴上「仅教师可见」的节点。',
        body: () => `<div class="g3-form-grid">
            <div class="g3-field"><label for="g3PlanBinding">所属教学班</label>
              <select id="g3PlanBinding" class="g3-select" name="bindingId">${bindingOptions(data.bindingId)}</select></div>
            <div class="g3-field"><label for="g3PlanLesson">课次</label>
              <select id="g3PlanLesson" class="g3-select" name="lessonId" required>${lessonOptions(overview, false, data.lessonId)}</select></div>
            <div class="g3-field full"><label for="g3PlanTitle">备课包名称</label>
              <input id="g3PlanTitle" class="g3-input" name="title" maxlength="160" required
                value="${escapeHtml(data.title || '')}"></div>
          </div>`
      },
      {
        label: '课件与随堂题', hint: '复用资源库与题库',
        intro: '从已有资料版本与题目版本里挑选，不需要重新上传或重新录入。',
        body: () => `<div class="g3-picker-lead"><strong>纳入的课件材料</strong>
            <span>${materials.length} 个可用版本</span></div>
          ${materialPicker(overview, data, { name: 'materialVersionIds' })}
          <div class="g3-picker-lead"><strong>随堂题预设（单选一道）</strong>
            <span>${(overview.questionVersions || []).length} 个可用版本</span>
            <button class="g3-btn quiet" type="button" data-picker-new-question>+ 新建题目</button></div>
          ${questionPicker(overview, data, { multiple: false, name: 'quizQuestionVersionId' })}
          <div class="g3-form-grid">
            <div class="g3-field"><label for="g3PlanQuizTitle">活动名称</label>
              <input id="g3PlanQuizTitle" class="g3-input" name="quizTitle"
                value="${escapeHtml(data.quizTitle || '课前测验')}"></div>
            <div class="g3-field"><label for="g3PlanDuration">答题时长（秒）</label>
              <input id="g3PlanDuration" class="g3-input" name="durationSeconds" type="number" min="60"
                value="${escapeHtml(data.durationSeconds || 900)}"></div>
          </div>`
      },
      {
        label: '备注与确认', hint: '教师私有备注 / 发布确认',
        intro: '私有备注只保存在备课包中，不会推送到学生时间轴，也不会返回未公布的答案。',
        body: () => `<div class="g3-form-grid">
            <div class="g3-field full"><label for="g3PlanNotes">教师私有备注</label>
              <textarea id="g3PlanNotes" class="g3-textarea" name="privateNotes"
                placeholder="课堂节奏、提问设计、易错点……仅教师可见">${escapeHtml(data.privateNotes || '')}</textarea></div>
            <div class="g3-field full"><label class="g3-check"><input type="checkbox" name="markReady" value="1"
              ${data.markReady === false ? '' : 'checked'}><span>保存为可用方案</span></label></div>
          </div>
          <div class="g3-summary" data-create-summary></div>
          <div class="g3-inline-note">学生时间轴不会收到私有备注与未公布答案；备课包环节不计入学生环节序号。</div>`
      }
    ];
    mountStepPage({
      eyebrow: `${context.courseTitle} · ${context.className}`,
      title: '生成备课包（教师私有）',
      subtitle: '课件与随堂题都从现有资产库挑选；三步填完，私有备注单独成页。',
      steps, data, submitLabel: '保存备课包',
      onWire: (form, apiObj) => wireInlineQuestionCreate(form, apiObj, 'plan'),
      onEnter: (index, form) => {
        if (index !== steps.length - 1) return;
        const box = form.querySelector('[data-create-summary]');
        if (!box) return;
        const values = collectForm(form);
        const materialIds = asArray(values.materialVersionIds);
        box.innerHTML = `<h3>保存确认</h3>${summaryRows([
          ['备课包名称', values.title || '未填写'],
          ['纳入资料', `${materialIds.length} 份`],
          ['随堂题预设', values.quizQuestionVersionId ? '已选择 1 道' : '不预设'],
          ['活动时长', `${values.durationSeconds || 0} 秒`],
          ['状态', asArray(values.markReady).length ? '可用方案' : '草稿']
        ])}`;
      },
      onSubmit: async (values, form, button) => {
        let body;
        try {
          const title = String(values.title || '').trim();
          if (!title) throw new Error('请填写备课包名称');
          if (!values.lessonId) throw new Error('请选择课次');
          body = {
            bindingId: Number(values.bindingId),
            lessonId: Number(values.lessonId),
            title,
            privateNotes: String(values.privateNotes || '').trim(),
            materialVersionIds: asArray(values.materialVersionIds).map(Number),
            quizQuestionVersionId: values.quizQuestionVersionId ? Number(values.quizQuestionVersionId) : null,
            quizTitle: String(values.quizTitle || '').trim(),
            durationSeconds: Number(values.durationSeconds),
            markReady: asArray(values.markReady).length > 0
          };
        } catch (error) { toast(error.message, true); return; }
        setBusy(button, true, '保存中…');
        try {
          await api('/education/group3/plans', { method: 'POST', body });
        } catch (error) {
          toast(actionableMessage(error), true);
          setBusy(button, false);
          return;
        }
        state.bindingId = String(body.bindingId);
        localStorage.setItem(`zhijiao.group3.binding.${role}`, state.bindingId);
        toast('备课包已加入时间轴（仅教师可见）');
        clearCreateHash();
        clearOverview();
        state.selectedKey = '';
        refreshRoute();
      }
    });
  }

  function showFailureModal(node) {
    const meta = node.meta || {};
    const modal = openModal({
      title: '资料处理失败 · 可执行的修复步骤',
      body: `<div class="g3-failure">
        <p><strong>${escapeHtml(node.title)}</strong>（V${escapeHtml(meta.versionNo || 1)}）</p>
        <p class="g3-help">${escapeHtml(meta.processingMessage || '资料转换失败，请检查文件后重试或上传新版本')}</p>
        <ol>
          <li>确认文件未损坏，且为受支持格式（PPTX / PDF / 图片 / 视频 / 文档）。</li>
          <li>重新导出或另存一份后，用「上传新版本」替换失败版本；旧版本仍保留在版本记录中。</li>
          <li>新版本显示「可用」后，在时间轴上设置开放时间并发布，学生才会看到。</li>
          <li>若多次失败，请联系管理员并附上文件名与操作时间，排查转换服务。</li>
        </ol>
        <div class="g3-inline-note">失败版本不会发布给学生，也不会进入学生时间轴。</div>
      </div>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>稍后处理</button>
        <button class="g3-btn" type="button" data-act="failure-versions">查看版本记录</button>
        <button class="g3-btn primary" type="button" data-act="failure-reupload">上传新版本</button>`
    });
    modal.querySelector('[data-act="failure-versions"]').addEventListener('click', () => {
      closeModal();
      showVersions(meta.resourceId);
    });
    modal.querySelector('[data-act="failure-reupload"]').addEventListener('click', () => {
      closeModal();
      showNewVersionModal(meta.resourceId, node.title);
    });
  }

  /* ------------------------------------------------------------------ *
   * 共享资源操作（沿用原有真实接口）
   * ------------------------------------------------------------------ */

  async function showVersions(resourceId) {
    try {
      const versions = await api(`/education/group3/resources/${resourceId}/versions?bindingId=${encodeURIComponent(state.bindingId)}`);
      const rows = versions.length ? versions.map(version => `<tr>
        <td>V${escapeHtml(version.versionNo)}</td><td>${statusBadge(version.processingStatus)}</td>
        <td>${escapeHtml(version.pageCount || 0)} 页</td><td>${formatTime(version.createdAt)}</td>
        <td><button class="g3-btn quiet" type="button" data-preview-version="${version.versionId}">预览</button></td>
      </tr>`).join('') : `<tr><td colspan="5">${emptyState('暂无版本')}</td></tr>`;
      const modal = openModal({
        title: '资料版本记录', wide: true,
        body: `<div class="g3-table-wrap"><table class="g3-table">
          <thead><tr><th>版本</th><th>处理状态</th><th>预览页</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody>${rows}</tbody></table></div>`
      });
      modal.querySelectorAll('[data-preview-version]').forEach(button => {
        button.addEventListener('click', () => showPreview(button.dataset.previewVersion, state.bindingId));
      });
    } catch (error) { toast(actionableMessage(error), true); }
  }

  function showNewVersionModal(resourceId, title) {
    const modal = openModal({
      title: `上传新版本 · ${title}`,
      body: `<form id="g3VersionForm" class="g3-form-grid">
        <div class="g3-field full"><label for="g3VersionFile">新文件</label>
          <input id="g3VersionFile" class="g3-input" type="file" name="file" required
            accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md"></div>
        <div class="g3-field full"><div class="g3-inline-note warn">新版本不会改写已发布版本；需要重新选择该版本发布，学生旧地址才会切换到新版本。</div></div>
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3VersionForm">保存新版本</button>`
    });
    modal.querySelector('#g3VersionForm').addEventListener('submit', async event => {
      event.preventDefault();
      const button = modal.querySelector('.g3-modal-foot .primary');
      const file = event.currentTarget.file.files[0];
      if (!file) return;
      const data = new FormData();
      data.append('bindingId', state.bindingId);
      data.append('file', file);
      setBusy(button, true, '上传处理中…');
      try {
        const result = await api(`/education/group3/resources/${resourceId}/versions`, { method: 'POST', body: data });
        closeModal();
        clearOverview();
        toast(result.processingStatus === 'READY' ? '新版本已保存，请在时间轴上重新发布' : '新版本处理失败，请在时间轴查看失败提示', result.processingStatus !== 'READY');
        refreshRoute();
      } catch (error) { toast(actionableMessage(error), true); setBusy(button, false); }
    });
  }

  function showReleaseModal(versionId, title) {
    const context = currentContext();
    const modal = openModal({
      title: '设置开放时间并发布',
      body: `<form id="g3ReleaseForm" class="g3-form-grid">
        <div class="g3-field full"><label>${escapeHtml(title)}</label>
          <div class="g3-help">发布到 ${escapeHtml(context ? context.className : '当前教学班')}；学生只在开放窗口内看到该版本，撤回后旧访问地址立即失效。</div></div>
        <div class="g3-field"><label for="g3ReleaseOpen">开放时间</label>
          <input id="g3ReleaseOpen" class="g3-input" type="datetime-local" name="opensAt" value="${localInput()}"></div>
        <div class="g3-field"><label for="g3ReleaseClose">截止时间</label>
          <input id="g3ReleaseClose" class="g3-input" type="datetime-local" name="closesAt" value="${futureInput(14)}"></div>
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3ReleaseForm">确认发布</button>`
    });
    modal.querySelector('#g3ReleaseForm').addEventListener('submit', async event => {
      event.preventDefault();
      const button = modal.querySelector('.g3-modal-foot .primary');
      const form = event.currentTarget;
      let opensAt, closesAt;
      try {
        opensAt = isoFromInput(form.opensAt.value);
        closesAt = isoFromInput(form.closesAt.value);
        if (closesAt && new Date(closesAt) <= new Date(opensAt)) throw new Error('截止时间必须晚于开放时间');
      } catch (error) { toast(error.message, true); return; }
      setBusy(button, true, '发布中…');
      try {
        await api('/education/group3/releases', {
          method: 'POST',
          body: { bindingId: Number(state.bindingId), resourceVersionId: versionId, opensAt, closesAt }
        });
        closeModal(); clearOverview(); state.selectedKey = '';
        toast('环节已发布到时间轴');
        refreshRoute();
      } catch (error) { toast(actionableMessage(error), true); setBusy(button, false); }
    });
  }

  // 环节不是独立实体，删除 = 归档它对应的真实业务对象（后端 /stages/remove）。
  function stageRemoveTarget(node) {
    if (node.kind === 'QUIZ') return { kind: 'QUIZ', id: node.id };
    if (node.kind === 'PLAN') return { kind: 'PLAN', id: node.id };
    return { kind: node.kind === 'DRAFT' ? 'DRAFT' : 'MATERIAL', id: node.meta.resourceId || node.id };
  }

  function confirmRemoveStage(node) {
    const target = stageRemoveTarget(node);
    if (!target.id) return toast('该环节没有可移除的业务记录', true);
    const modal = openModal({
      title: '删除环节',
      body: `<div class="g3-list">
        <div class="g3-detail-title">${escapeHtml(node.title)}</div>
        <div class="g3-inline-note warn">删除后学生立即不可见，已提交的作答保留。</div>
      </div>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn danger" type="button" data-confirm-remove>确认删除</button>`
    });
    const confirm = modal.querySelector('[data-confirm-remove]');
    confirm?.addEventListener('click', async () => {
      setBusy(confirm, true, '删除中…');
      try {
        await api('/education/group3/stages/remove', {
          method: 'POST',
          body: { bindingId: Number(state.bindingId), kind: target.kind, id: Number(target.id) }
        });
        closeModal();
        toast('环节已删除，本堂课环节已重新编号');
        clearStageHash();
        clearOverview();
        state.stageKey = '';
        state.selectedKey = '';
        state.selectedPreviewId = null;
        delete state.stageAnswers[node.key];
        refreshRoute();
      } catch (error) {
        toast(actionableMessage(error), true);
        setBusy(confirm, false);
      }
    });
  }

  async function revokeRelease(node) {
    const context = currentContext();
    if (!window.confirm(`确认撤回“${node.title}”在 ${context ? context.className : '当前教学班'} 的发布？撤回后学生旧链接立即失效。`)) return;
    try {
      await api(`/education/group3/releases/${node.meta.releaseId}/revoke?bindingId=${encodeURIComponent(state.bindingId)}`, {
        method: 'POST', body: { reason: '教师从课前时间轴撤回' }
      });
      clearOverview(); state.selectedKey = '';
      toast('资料已撤回，学生端不再可见');
      refreshRoute();
    } catch (error) { toast(actionableMessage(error), true); }
  }

  async function showPreview(versionId, bindingId) {
    if (!versionId) return;
    const binding = bindingId || state.bindingId;
    let modal;
    try {
      modal = openModal({ title: '资料预览', wide: true, body: loadingState('正在加载预览') });
      const version = await api(`/education/group3/versions/${versionId}?bindingId=${encodeURIComponent(binding)}`);
      if (version.mediaKind === 'LINK' && version.externalUri) {
        modal.querySelector('.g3-modal-body').innerHTML =
          `<div class="g3-inline-note">该资料为外部链接。<a href="${escapeHtml(version.externalUri)}" target="_blank" rel="noopener noreferrer">打开链接</a></div>`;
        return;
      }
      if (version.mediaKind === 'VIDEO') {
        const blob = await fetchBlob(`/education/group3/versions/${versionId}/download?bindingId=${encodeURIComponent(binding)}`);
        const url = rememberUrl(URL.createObjectURL(blob));
        modal.querySelector('.g3-modal-body').innerHTML =
          `<div class="g3-preview-box"><video controls autoplay src="${url}"></video></div>`;
        return;
      }
      if (!version.pages || version.pages.length === 0) {
        modal.querySelector('.g3-modal-body').innerHTML = `<div class="g3-empty">
          <strong>该资料没有分页预览</strong><span>可使用受控下载打开原文件。</span>
          <button class="g3-btn" type="button" data-download-version="${versionId}">下载原文件</button></div>`;
      } else {
        let pageIndex = 0;
        const urls = [];
        modal.querySelector('.g3-modal-body').innerHTML = loadingState('正在读取预览页');
        for (const page of version.pages) {
          const blob = await fetchBlob(`/education/group3/versions/${versionId}/pages/${page.pageNo}?bindingId=${encodeURIComponent(binding)}`);
          urls.push(rememberUrl(URL.createObjectURL(blob)));
        }
        const renderPage = () => {
          modal.querySelector('.g3-modal-body').innerHTML = `<div class="g3-preview-box">
              <img alt="第 ${pageIndex + 1} 页" src="${urls[pageIndex]}"></div>
            <div class="g3-page-nav">
              <button class="g3-btn" type="button" data-page-prev ${pageIndex === 0 ? 'disabled' : ''}>上一页</button>
              <span class="g3-help">${pageIndex + 1} / ${urls.length}</span>
              <button class="g3-btn" type="button" data-page-next ${pageIndex === urls.length - 1 ? 'disabled' : ''}>下一页</button>
            </div>`;
          modal.querySelector('[data-page-prev]').addEventListener('click', () => {
            if (pageIndex > 0) { pageIndex -= 1; renderPage(); }
          });
          modal.querySelector('[data-page-next]').addEventListener('click', () => {
            if (pageIndex < urls.length - 1) { pageIndex += 1; renderPage(); }
          });
        };
        renderPage();
      }
      modal.querySelector('[data-download-version]')?.addEventListener('click', () => downloadVersion(versionId, binding));
    } catch (error) {
      if (error.revoked) return handleRevoked(error);
      if (modal) modal.querySelector('.g3-modal-body').innerHTML =
        `<div class="g3-error"><strong>预览失败</strong><span>${escapeHtml(actionableMessage(error))}</span></div>`;
      else toast(actionableMessage(error), true);
    }
  }

  function handleRevoked(error) {
    clearObjectUrls();
    clearStageUrls();
    closeModal();
    state.selectedPreviewId = null;
    clearStageHash();
    toast('权限已变更或资料已撤回，已关闭预览并重新校验时间轴', true);
    refreshRoute();
  }

  async function downloadVersion(versionId, bindingId) {
    try {
      const blob = await fetchBlob(`/education/group3/versions/${versionId}/download?bindingId=${encodeURIComponent(bindingId || state.bindingId)}`);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `resource-${versionId}`;
      anchor.click();
      setTimeout(() => URL.revokeObjectURL(url), 1500);
    } catch (error) {
      if (error.revoked) return handleRevoked(error);
      toast(actionableMessage(error), true);
    }
  }

  function showUploadModal(presetUnitId = '') {
    const overview = state.overview || {};
    const modal = openModal({
      title: '上传课程资料',
      body: `<form id="g3UploadForm" class="g3-form-grid">
        <div class="g3-field full"><label for="g3UploadTitle">资料名称</label>
          <input id="g3UploadTitle" class="g3-input" name="title" maxlength="200" placeholder="不填写时使用文件名"></div>
        <div class="g3-field"><label for="g3UploadUnit">归档章节</label>
          <select id="g3UploadUnit" class="g3-select" name="unitId">${unitOptions(overview)}</select></div>
        <div class="g3-field"><label for="g3UploadFile">文件</label>
          <input id="g3UploadFile" class="g3-input" name="file" type="file" required
            accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md"></div>
        <div class="g3-field full"><div class="g3-inline-note">PPTX 和 PDF 会转换为受控逐页预览；视频与文档保留原文件受控下载。上传后可在「课前准备」时间轴设置开放时间。</div></div>
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3UploadForm">上传并处理</button>`
    });
    if (presetUnitId) modal.querySelector('#g3UploadUnit').value = String(presetUnitId);
    modal.querySelector('#g3UploadForm').addEventListener('submit', async event => {
      event.preventDefault();
      const button = modal.querySelector('.g3-modal-foot .primary');
      const form = event.currentTarget;
      if (!form.file.files.length) return;
      const data = new FormData();
      data.append('bindingId', state.bindingId);
      data.append('title', form.title.value.trim());
      if (form.unitId.value) data.append('unitId', form.unitId.value);
      data.append('file', form.file.files[0]);
      setBusy(button, true, '上传处理中…');
      try {
        const result = await api('/education/group3/resources', { method: 'POST', body: data });
        closeModal(); clearOverview();
        toast(result.processingStatus === 'READY' ? '资料已上传并处理成功，可在时间轴设置开放时间' : '上传成功但处理失败，请在时间轴查看修复提示', result.processingStatus !== 'READY');
        refreshRoute();
      } catch (error) { toast(actionableMessage(error), true); setBusy(button, false); }
    });
  }

  function knowledgeMarkup(overview) {
    if (!overview.knowledgePoints?.length) return '<div class="g3-help">当前课程尚未维护知识点</div>';
    return overview.knowledgePoints.map(point =>
      `<label class="g3-check"><input type="checkbox" name="knowledgePoint" value="${escapeHtml(point.knowledgePointId)}">
        <span>${escapeHtml(point.name)}</span></label>`).join('');
  }

  function showQuestionModal(edit = null, onCreated = null) {
    const overview = state.overview || {};
    const kinds = ['SINGLE', 'MULTIPLE', 'TRUE_FALSE', 'SHORT_TEXT'];
    const modal = openModal({
      title: edit ? `新建题目版本 · ${edit.title}` : '新建题目',
      wide: true,
      body: `<form id="g3QuestionForm" class="g3-form-grid">
        ${edit ? '' : `<div class="g3-field full"><label for="g3QuestionTitle">题目名称</label>
          <input id="g3QuestionTitle" class="g3-input" name="title" required maxlength="200"></div>`}
        <div class="g3-field"><label for="g3QuestionKind">题型</label>
          <select id="g3QuestionKind" class="g3-select" name="kind">
            ${kinds.map(kind => `<option value="${kind}"${edit && edit.kind === kind ? ' selected' : ''}>${labels[kind]}</option>`).join('')}
          </select></div>
        <div class="g3-field"><label for="g3QuestionScore">满分</label>
          <input id="g3QuestionScore" class="g3-input" name="maxScore" type="number" min="0" step="0.5" value="10" required></div>
        <div class="g3-field full"><label for="g3QuestionStem">题干</label>
          <textarea id="g3QuestionStem" class="g3-textarea" name="stem" required></textarea></div>
        <div class="g3-field full" data-options-field><label for="g3QuestionOptions">选项（每行一个）</label>
          <textarea id="g3QuestionOptions" class="g3-textarea" name="options">选项 A\n选项 B\n选项 C</textarea></div>
        <div class="g3-field full"><label for="g3QuestionAnswer">答案</label>
          <input id="g3QuestionAnswer" class="g3-input" name="answer" placeholder="单选题填选项序号，从 1 开始" required>
          <div class="g3-help" data-answer-help>单选题填写一个序号；多选题填写逗号分隔的序号。</div></div>
        <div class="g3-field full"><label for="g3QuestionExplanation">解析</label>
          <textarea id="g3QuestionExplanation" class="g3-textarea" name="explanation"></textarea></div>
        <div class="g3-field full"><label>关联知识点</label><div class="g3-check-list">${knowledgeMarkup(overview)}</div></div>
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3QuestionForm">保存题目</button>`
    });
    const form = modal.querySelector('#g3QuestionForm');
    const kind = form.kind;
    const optionsField = form.querySelector('[data-options-field]');
    const answerHelp = form.querySelector('[data-answer-help]');
    const syncKind = () => {
      const value = kind.value;
      optionsField.hidden = value === 'TRUE_FALSE' || value === 'SHORT_TEXT';
      if (value === 'TRUE_FALSE') {
        form.answer.value = 'true'; form.answer.placeholder = '填写 true 或 false';
        answerHelp.textContent = '判断题填写 true 或 false。';
      } else if (value === 'SHORT_TEXT') {
        form.answer.value = ''; form.answer.placeholder = '填写参考答案';
        answerHelp.textContent = '简答题填写参考答案，供教师端使用。';
      } else if (value === 'MULTIPLE') {
        form.answer.placeholder = '例如：1,3';
        answerHelp.textContent = '多选题填写逗号分隔的选项序号。';
      } else {
        form.answer.placeholder = '例如：2';
        answerHelp.textContent = '单选题填写一个选项序号，从 1 开始。';
      }
    };
    kind.addEventListener('change', syncKind);
    syncKind();
    form.addEventListener('submit', async event => {
      event.preventDefault();
      const button = modal.querySelector('.g3-modal-foot .primary');
      const selectedKind = form.kind.value;
      const optionValues = form.options.value.split('\n').map(value => value.trim()).filter(Boolean);
      let answer;
      if (selectedKind === 'SINGLE') answer = { choiceIndex: Number(form.answer.value) - 1 };
      if (selectedKind === 'MULTIPLE') answer = { choiceIndices: form.answer.value.split(',').map(value => Number(value.trim()) - 1).filter(Number.isInteger) };
      if (selectedKind === 'TRUE_FALSE') answer = { correctBoolean: form.answer.value.trim().toLowerCase() === 'true' };
      if (selectedKind === 'SHORT_TEXT') answer = { referenceAnswer: form.answer.value.trim() };
      const knowledgePointIds = [...form.querySelectorAll('input[name="knowledgePoint"]:checked')]
        .map(input => Number(input.value));
      const body = {
        bindingId: Number(state.bindingId),
        title: edit ? edit.title : form.title.value.trim(),
        questionKind: selectedKind,
        stem: form.stem.value.trim(),
        options: selectedKind === 'TRUE_FALSE' ? ['正确', '错误'] : selectedKind === 'SHORT_TEXT' ? [] : optionValues,
        answer,
        explanation: form.explanation.value.trim(),
        maxScore: Number(form.maxScore.value),
        knowledgePointIds
      };
      setBusy(button, true, '保存中…');
      try {
        if (edit) await api(`/education/group3/questions/${edit.questionId}/versions`, { method: 'POST', body });
        else await api('/education/group3/questions', { method: 'POST', body });
        closeModal(); clearOverview();
        toast(edit ? '题目新版本已保存' : '题目已创建，可在时间轴发布预习');
        if (onCreated) {
          const context = currentContext();
          if (context) await loadOverview(context);
          await onCreated(state.overview || {});
        } else {
          refreshRoute();
        }
      } catch (error) { toast(actionableMessage(error), true); setBusy(button, false); }
    });
  }

  async function archiveQuestion(questionId) {
    if (!window.confirm('确认归档该题目？归档后不再用于新的预习发布。')) return;
    try {
      await api(`/education/group3/questions/${questionId}/status?bindingId=${encodeURIComponent(state.bindingId)}`, {
        method: 'PATCH', body: { status: 'ARCHIVED' }
      });
      clearOverview(); toast('题目已归档'); refreshRoute();
    } catch (error) { toast(actionableMessage(error), true); }
  }

  /* ------------------------------------------------------------------ *
   * 教师「课程建设」：资源库 / 题库 / 章节知识点
   * ------------------------------------------------------------------ */

  async function renderTeacherStudio() {
    clearObjectUrls();
    root.innerHTML = loadingState('正在加载课程资源');
    try {
      const context = await ensureContexts();
      if (!context) {
        root.innerHTML = emptyState('暂无获授权的课程', '请联系教务管理员确认教学班任课关系');
        return;
      }
      const overview = await loadOverview(context);
      paintTeacherStudio(overview, context);
    } catch (error) { root.innerHTML = errorState(error); }
  }

  function paintTeacherStudio(overview, context) {
    const tab = state.studioTab;
    let content = '';
    if (tab === 'resources') content = renderResourceTable(overview.resources || []);
    if (tab === 'questions') content = renderQuestionTable(overview.questions || []);
    if (tab === 'outline') content = renderOutline(overview.outline || [], overview.knowledgePoints || []);
    root.innerHTML = `<div class="g3-workspace">
      ${pageHead('课程建设', `${context.courseTitle} · ${context.className}`, context,
        '这里维护课程的原始资产；在时间轴上把资料与题目编排成学生看得到的课前环节。')}
      <div class="g3-toolbar">
        <div class="g3-segment" data-studio-tabs>
          <button type="button" data-act="studio-tab" data-tab="resources" class="${tab === 'resources' ? 'active' : ''}">课程资源</button>
          <button type="button" data-act="studio-tab" data-tab="questions" class="${tab === 'questions' ? 'active' : ''}">题库</button>
          <button type="button" data-act="studio-tab" data-tab="outline" class="${tab === 'outline' ? 'active' : ''}">章节与知识点</button>
        </div>
        <div class="g3-actions">
          ${tab === 'resources' ? `<button class="g3-btn" type="button" data-act="create-material-stage">选用已有资料建环节</button>` : ''}
          ${tab === 'questions' ? `<button class="g3-btn" type="button" data-act="create-quiz-stage">用题库题目建预习</button>` : ''}
          <button class="g3-btn" type="button" data-act="go-prepare">前往课前时间轴</button>
          ${tab === 'resources' ? `<button class="g3-btn primary" type="button" data-act="upload-resource">上传新资料</button>` : ''}
          ${tab === 'questions' ? `<button class="g3-btn primary" type="button" data-act="create-question">新建题目</button>` : ''}
        </div>
      </div>
      ${content}
    </div>`;
  }

  function renderResourceTable(resources) {
    if (!resources.length) {
      return emptyState('课程还没有资料', '上传 PPTX、PDF、视频、图片或文档后会显示处理状态',
        '<button class="g3-btn primary" type="button" data-act="upload-resource">上传第一份资料</button>');
    }
    const rows = resources.map(item => {
      const status = String(item.processingStatus || 'PENDING').toUpperCase();
      const released = item.releaseId
        ? `<span class="g3-badge ready">已发布</span><div class="g3-cell-meta">${formatTime(item.opensAt)} 至 ${formatTime(item.closesAt)}</div>`
        : '<span class="g3-badge">未发布</span>';
      const failureHint = status === 'FAILED'
        ? `<div class="g3-cell-error">${escapeHtml(item.processingMessage || '转换失败')}<br>
            <button class="g3-btn quiet" type="button" data-act="failure" data-resource="${item.resourceId}">查看修复步骤</button>
            <button class="g3-btn quiet" type="button" data-act="new-version" data-resource="${item.resourceId}" data-title="${escapeHtml(item.title)}">上传新版本</button></div>` : '';
      return `<tr>
        <td><div class="g3-cell-title">${escapeHtml(item.title)}</div>
          <div class="g3-cell-meta">${escapeHtml(item.mediaKind || item.resourceKind || 'FILE')} · ${Number(item.pageCount || 0)} 页</div></td>
        <td>V${escapeHtml(item.versionNo || 1)}</td>
        <td>${statusBadge(status)}<div class="g3-cell-meta">${escapeHtml(item.processingMessage || '')}</div>${failureHint}</td>
        <td>${released}</td>
        <td><div class="g3-actions">
          <button class="g3-btn quiet" type="button" data-act="preview" data-version="${item.versionId}">预览</button>
          <button class="g3-btn quiet" type="button" data-act="versions" data-resource="${item.resourceId}">版本</button>
          ${status === 'READY' ? `<button class="g3-btn quiet" type="button" data-act="release" data-version="${item.versionId}" data-title="${escapeHtml(item.title)}">发布</button>` : ''}
          ${item.releaseId ? `<button class="g3-btn quiet" type="button" data-act="revoke" data-release="${item.releaseId}" data-title="${escapeHtml(item.title)}">撤回</button>` : ''}
          ${item.resourceKind === 'FILE' ? `<button class="g3-btn quiet" type="button" data-act="new-version" data-resource="${item.resourceId}" data-title="${escapeHtml(item.title)}">新版本</button>` : ''}
        </div></td></tr>`;
    }).join('');
    return `<section class="g3-panel">
      <div class="g3-panel-head"><div class="g3-panel-title">课程资料 <span class="count">${resources.length} 项</span></div>
        <span class="g3-help">处理结果以服务端为准；失败版本不会对学生开放</span></div>
      <div class="g3-table-wrap"><table class="g3-table">
        <thead><tr><th>资料</th><th>版本</th><th>处理状态</th><th>教学班发布</th><th>操作</th></tr></thead>
        <tbody>${rows}</tbody></table></div></section>`;
  }

  function renderQuestionTable(questions) {
    if (!questions.length) {
      return emptyState('题库为空', '创建单选、多选、判断或简答题，并保留答案版本',
        '<button class="g3-btn primary" type="button" data-act="create-question">新建第一道题</button>');
    }
    const rows = questions.map(question => `<tr>
      <td><div class="g3-cell-title">${escapeHtml(question.title)}</div>
        <div class="g3-cell-meta">${escapeHtml(question.stem)}</div></td>
      <td>${escapeHtml(labels[question.questionKind] || question.questionKind)}</td>
      <td>V${escapeHtml(question.versionNo)}<div class="g3-cell-meta">${escapeHtml(question.maxScore)} 分</div></td>
      <td><div class="g3-cell-meta">${escapeHtml(formatAnswer(question.answer, question.questionKind))}</div></td>
      <td>${statusBadge(question.status)}</td>
      <td><div class="g3-actions">
        <button class="g3-btn quiet" type="button" data-act="question-version" data-question="${question.questionId}"
          data-kind="${question.questionKind}" data-title="${escapeHtml(question.title)}">新版本</button>
        ${question.status !== 'ARCHIVED' ? `<button class="g3-btn quiet" type="button" data-act="archive-question" data-question="${question.questionId}">归档</button>` : ''}
      </div></td></tr>`).join('');
    return `<section class="g3-panel">
      <div class="g3-panel-head"><div class="g3-panel-title">课程题库 <span class="count">${questions.length} 题</span></div>
        <span class="g3-help">答案与解析按公布时间裁剪，不提前返回学生</span></div>
      <div class="g3-table-wrap"><table class="g3-table">
        <thead><tr><th>题目</th><th>题型</th><th>版本</th><th>答案</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>${rows}</tbody></table></div></section>`;
  }

  function formatAnswer(answer, kind) {
    if (answer == null) return '未设置';
    if (kind === 'TRUE_FALSE') return answer.correctBoolean ? '正确' : '错误';
    if (kind === 'SINGLE') return `选项 ${Number(answer.choiceIndex) + 1}`;
    if (kind === 'MULTIPLE') return `选项 ${(answer.choiceIndices || []).map(index => Number(index) + 1).join('、')}`;
    if (kind === 'SHORT_TEXT') return answer.referenceAnswer || '参考答案';
    return JSON.stringify(answer);
  }

  function renderOutline(units, points) {
    const unitRows = units.length ? units.map(unit => {
      const count = points.filter(point => String(point.unitId) === String(unit.unitId)).length;
      return `<div class="g3-list-row"><div class="g3-list-main"><strong>${escapeHtml(unit.title)}</strong>
        <span>${escapeHtml(unit.unitKind === 'CHAPTER' ? '章' : '节')} · 排序 ${escapeHtml(unit.sortOrder)} · ${count} 个知识点</span></div>
        <button class="g3-btn quiet" type="button" data-act="upload-for-unit" data-unit="${unit.unitId}" data-title="${escapeHtml(unit.title)}">上传资料到此章节</button></div>`;
    }).join('') : emptyState('暂无章节', '章节由课程侧维护，上传资料时可选择归档章节');
    const pointRows = points.length ? points.map(point =>
      `<div class="g3-list-row"><div class="g3-list-main"><strong>${escapeHtml(point.name)}</strong>
        <span>${escapeHtml(point.code || '未设置编码')}</span></div></div>`).join('') : emptyState('暂无知识点', '题目可在新建 / 新版本时关联知识点');
    return `<div class="g3-grid two">
      <section class="g3-panel"><div class="g3-panel-head"><div class="g3-panel-title">章节结构</div>
        <span class="g3-help">上传时归档到章节</span></div>
        <div class="g3-panel-body g3-list">${unitRows}</div></section>
      <section class="g3-panel"><div class="g3-panel-head"><div class="g3-panel-title">知识点</div>
        <span class="g3-help">题目按知识点关联</span></div>
        <div class="g3-panel-body g3-list">${pointRows}</div></section>
    </div>`;
  }

  /* ------------------------------------------------------------------ *
   * 学生端：同一条时间轴的两个视角
   * ------------------------------------------------------------------ */

  async function loadStudentTimeline() {
    const binding = encodeURIComponent(state.bindingId);
    const [materials, taskRows] = await Promise.all([
      api(`/education/group3/student/materials?bindingId=${binding}`),
      api(`/education/group3/student/previews?bindingId=${binding}`)
    ]);
    state.materials = materials || [];
    state.studentTasks = taskRows || [];
    indexNodes(numberStages(collectStudentNodes(state.materials, state.studentTasks), currentContext()));
  }

  async function renderStudentTimeline(mode) {
    clearObjectUrls();
    clearStageUrls();
    const previousMode = state.route;
    state.route = mode;
    // 两个学生页面是同一条时间轴的不同默认视角；切页时重置筛选，页内刷新保留筛选。
    if (previousMode !== mode) {
      state.studentFilter = mode === 'materials' ? 'MATERIAL' : 'all';
      state.selectedKey = '';
      state.selectedPreviewId = null;
    }
    root.innerHTML = loadingState('正在加载课前时间轴');
    try {
      const context = await ensureContexts();
      if (!context) {
        root.innerHTML = emptyState('暂无在修课程', '请联系任课教师确认教学班成员关系');
        return;
      }
      await loadStudentTimeline();
      if (!state.nodeIndex[state.selectedKey]) {
        state.selectedKey = '';
        state.selectedPreviewId = null;
      }
      paintStudentTimeline(context, mode);
      if (!state.stageKey) paintStudentDetail(mode);
    } catch (error) {
      if (error && error.status === 403) {
        root.innerHTML = emptyState('当前教学班的内容已不可访问',
          '可能已被移出教学班或课程已结束；旧资料链接已失效，请联系任课教师',
          '<button class="g3-btn" type="button" data-act="retry">重新核验</button>');
        return;
      }
      root.innerHTML = errorState(error);
    }
  }

  function studentStats(nodes) {
    const quizzes = nodes.filter(node => node.kind === 'QUIZ');
    const done = quizzes.filter(node => node.meta.submitted).length;
    const percent = quizzes.length ? Math.round(done / quizzes.length * 100) : 0;
    return `<div class="g3-tl-stats">
      <div class="g3-tl-stat"><small>本堂课环节</small><strong>${nodes.filter(node => node.seq).length}</strong></div>
      <div class="g3-tl-stat"><small>已开放资料</small><strong>${nodes.filter(node => node.kind === 'MATERIAL').length}</strong></div>
      <div class="g3-tl-stat"><small>预习任务</small><strong>${quizzes.length}</strong></div>
      <div class="g3-tl-stat"><small>已完成预习</small><strong>${done} / ${quizzes.length}</strong></div>
    </div>
    <div class="g3-tl-progress"><div class="g3-tl-progress-bar"><span style="width:${percent}%"></span></div>
      <span class="g3-help">${quizzes.length ? `完成度 ${percent}%` : '暂无预习任务'}</span></div>`;
  }

  function paintStudentTimeline(context, mode) {
    const title = mode === 'materials' ? '课程资料' : '课前学习';
    const visible = filterNodes(state.nodes, state.studentFilter);
    root.innerHTML = `<div class="g3-workspace">
      ${pageHead(title, `${context.courseTitle} · ${context.className}`, context)}
      ${contextChips(context)}
      <details class="g3-overview-fold" open>
        <summary><span>我的课前学习进度</span><small>${state.stageGroups.length} 堂课 / ${state.stageOrder.length} 个环节 · 点击可折叠</small></summary>
        <div class="g3-overview-fold-body">
          ${studentStats(state.nodes)}
          ${renderStageTrack(state.selectedKey)}
        </div>
      </details>
      <div class="g3-tl-toolbar stacked">
        <div class="g3-tl-toolbar-row">
          <div class="g3-segment">
            <button type="button" class="active">时间轴</button>
          </div>
          <div class="g3-tl-filters"><label for="g3Filter">筛选</label>
            <select id="g3Filter" class="g3-select">${filterOptions(state.studentFilter, false)}</select></div>
        </div>
        <div class="g3-tl-toolbar-row end">
          <div class="g3-actions">
            <button class="g3-btn" type="button" data-act="student-switch"
              data-mode="${mode === 'materials' ? 'prestudy' : 'materials'}">${mode === 'materials' ? '查看课前学习' : '查看课程资料'}</button>
          </div>
        </div>
      </div>
      <div class="g3-timeline-layout">
        <div class="g3-tl-main">${renderTimeline(visible, state.selectedKey,
          state.overview?.serverTime, '暂无内容')}</div>
        <aside class="g3-tl-detail" id="g3StageDetail">${loadingState('正在加载环节')}</aside>
      </div>
    </div>`;
    applyStageView();
  }

  function paintStudentDetail(mode) {
    const box = document.getElementById('g3StageDetail');
    if (!box) return;
    const node = state.nodeIndex[state.selectedKey];
    root.querySelectorAll('.g3-tl-card').forEach(card => {
      card.classList.toggle('active', card.dataset.key === state.selectedKey);
    });
    if (!node) {
      box.innerHTML = `<div class="g3-panel"><div class="g3-panel-head"><div class="g3-panel-title">环节详情</div></div>
        <div class="g3-panel-body">${emptyState('选择一个环节', '点击左侧时间轴上的卡片')}</div></div>`;
      return;
    }
    if (node.kind === 'MATERIAL') {
      const meta = node.meta || {};
      box.innerHTML = `<div class="g3-panel g3-detail-panel g3-task-card">
        <div class="g3-panel-head"><div class="g3-panel-title">${escapeHtml(node.title)}</div>${statePill(node.state)}</div>
        <div class="g3-panel-body">
          <div class="g3-task-meta">
            ${node.seq ? `<span>第 ${node.seq} / ${node.total} 环节</span>` : ''}
            <span>${escapeHtml(formatTime(node.opensAt))} — ${escapeHtml(formatTime(node.closesAt))}</span>
            <span>V${escapeHtml(meta.versionNo || 1)} · ${Number(meta.pageCount || 0)} 页</span>
          </div>
          <div class="g3-task-actions">
            <button class="g3-btn primary" type="button" data-act="stage-open" data-key="${escapeHtml(node.key)}">去阅读</button>
            <button class="g3-btn" type="button" data-act="stage-preview" data-key="${escapeHtml(node.key)}">预览</button>
            <button class="g3-btn" type="button" data-act="stage-download" data-key="${escapeHtml(node.key)}">下载</button>
          </div>
        </div></div>`;
      return;
    }
    state.selectedPreviewId = String(node.id);
    loadStudentPreviewDetail(node);
  }

  async function loadStudentPreviewDetail(node) {
    const container = document.getElementById('g3StageDetail');
    if (!container || !state.selectedPreviewId) return;
    container.innerHTML = `<div class="g3-panel"><div class="g3-panel-body">${loadingState('正在加载任务')}</div></div>`;
    try {
      const detail = await api(`/education/group3/student/previews/${state.selectedPreviewId}?bindingId=${encodeURIComponent(state.bindingId)}`);
      if (document.getElementById('g3StageDetail') !== container) return;
      const stageNode = state.nodeIndex[state.selectedKey];
      const stageEntry = stageNode && stageNode.seq
        ? `<button class="g3-btn primary" type="button" data-act="stage-open" data-key="${escapeHtml(stageNode.key)}">进入作答页</button>`
        : '';
      const items = detail.items || [];
      const canSubmit = detail.state === 'OPEN' && !detail.submitted && items.length > 0;
      container.innerHTML = `<div class="g3-panel g3-detail-panel g3-task-card">
        <div class="g3-panel-head"><div class="g3-panel-title">${escapeHtml(detail.title)}</div>${statusBadge(detail.state)}</div>
        <div class="g3-panel-body g3-list">
          <div class="g3-task-meta">
            ${stageNode && stageNode.seq ? `<span>第 ${stageNode.seq} / ${stageNode.total} 环节</span>` : ''}
            <span>${escapeHtml(formatTime(detail.opensAt))} — ${escapeHtml(formatTime(detail.closesAt))}</span>
            <span>${items.length} 道题</span>
          </div>
          ${stageEntry ? `<div class="g3-task-actions">${stageEntry}</div>` : ''}
          ${detail.instructions ? `<div class="g3-inline-note">${escapeHtml(detail.instructions)}</div>` : ''}
          ${detail.state === 'UPCOMING' ? emptyState('任务尚未开放', `${formatTime(detail.opensAt)} 开放`) : ''}
          ${detail.state !== 'UPCOMING' && !items.length ? emptyState('本任务没有题目') : ''}
          ${canSubmit ? '' : `<div class="g3-inline-note warn">${escapeHtml(submitBlockReason(detail, items))}</div>`}
          <form id="g3StudentAnswerForm" class="g3-list">
            ${items.map(item => renderStudentQuestion(item, detail, 0, !canSubmit)).join('')}
            <div class="g3-actions"><button class="g3-btn primary" type="submit" data-submit-preview${canSubmit ? '' : ' disabled'}>提交预习</button></div>
          </form>
          ${detail.submitted ? `<div class="g3-feedback">已于 ${formatTime(detail.submission?.submittedAt)} 提交${detail.answerVisible ? ' · 解析已公布' : ''}</div>` : ''}
          ${(detail.resources || []).length ? `<div class="g3-task-actions">${detail.resources.map(resource =>
            `<button class="g3-btn quiet" type="button" data-act="detail-preview" data-version="${resource.versionId}">${escapeHtml(resource.title)} · V${escapeHtml(resource.versionNumber || resource.versionNo)}</button>`).join('')}</div>` : ''}
        </div></div>`;
      container.querySelector('#g3StudentAnswerForm')?.addEventListener('submit', event => {
        event.preventDefault();
        if (!canSubmit) return;
        submitStudentPreview(event, detail, event.currentTarget);
      });
    } catch (error) {
      if (error && (error.status === 403 || error.status === 404)) return handleRevoked(error);
      container.innerHTML = `<div class="g3-panel"><div class="g3-panel-body">${errorState(error)}
        </div></div>`;
    }
  }

  // 提交状态以服务端为准，这里只给一句可执行的原因。
  function submitBlockReason(detail, items) {
    if (detail.state === 'UPCOMING') return `${formatTime(detail.opensAt)} 开放后可提交。`;
    if (detail.state === 'CLOSED') return `${formatTime(detail.closesAt)} 已截止，不能再提交。`;
    if (detail.submitted) return '已提交，无需重复提交。';
    if (!(items || []).length) return '本环节没有题目。';
    if (detail.state !== 'OPEN') return `当前状态：${STATE_LABELS[detail.state] || detail.state}`;
    return '';
  }

  function renderStudentQuestion(item, detail, number = 0, readOnly = false) {
    const options = Array.isArray(item.options) ? item.options : [];
    const answered = Boolean(detail.submitted);
    const lock = readOnly ? ' disabled' : '';
    let controls = '';
    if (item.questionKind === 'SINGLE' || item.questionKind === 'TRUE_FALSE') {
      const values = item.questionKind === 'TRUE_FALSE' ? ['正确', '错误'] : options;
      controls = `<div class="g3-options">${values.map((option, index) => `<label class="g3-option">
        <input type="radio" name="question-${item.assignmentItemId}" value="${index}" data-kind="${item.questionKind}"${lock}>
        <span>${escapeHtml(option)}</span></label>`).join('')}</div>`;
    } else if (item.questionKind === 'MULTIPLE') {
      controls = `<div class="g3-options">${options.map((option, index) => `<label class="g3-option">
        <input type="checkbox" name="question-${item.assignmentItemId}" value="${index}" data-kind="MULTIPLE"${lock}>
        <span>${escapeHtml(option)}</span></label>`).join('')}</div>`;
    } else {
      controls = `<textarea class="g3-textarea" name="question-${item.assignmentItemId}"${lock}
        placeholder="${readOnly ? '当前不可作答，仅供回顾' : '填写你的回答'}"></textarea>`;
    }
    const feedback = item.answerVisible && item.answer
      ? `<div class="g3-feedback"><strong>参考答案：</strong>${escapeHtml(formatAnswer(item.answer, item.questionKind))}
          ${item.explanation ? `<br><strong>解析：</strong>${escapeHtml(item.explanation)}` : ''}</div>` : '';
    return `<div class="g3-question">
      <div class="g3-question-stem">${number ? `<span class="g3-q-no">${number}</span>` : ''}${escapeHtml(item.prompt || item.stem)}</div>
      ${answered ? '' : controls}${feedback}
    </div>`;
  }

  async function submitStudentPreview(event, detail, form, stageMode = false) {
    const button = form.querySelector('[data-submit-preview]');
    const answers = {};
    const items = detail.items || [];
    for (const item of items) {
      const itemId = String(item.assignmentItemId);
      const selector = `[name="question-${itemId}"]`;
      if (item.questionKind === 'SINGLE') {
        const checked = form.querySelector(`${selector}:checked`);
        if (!checked) return toast('请完成所有题目后再提交', true);
        answers[itemId] = { choiceIndex: Number(checked.value) };
      } else if (item.questionKind === 'TRUE_FALSE') {
        const checked = form.querySelector(`${selector}:checked`);
        if (!checked) return toast('请完成所有题目后再提交', true);
        answers[itemId] = { correctBoolean: checked.value === '0' };
      } else if (item.questionKind === 'MULTIPLE') {
        const checked = [...form.querySelectorAll(`${selector}:checked`)];
        if (!checked.length) return toast('请完成所有题目后再提交', true);
        answers[itemId] = { choiceIndices: checked.map(input => Number(input.value)) };
      } else {
        const input = form.querySelector(selector);
        if (!input || !input.value.trim()) return toast('请完成所有题目后再提交', true);
        answers[itemId] = input.value.trim();
      }
    }
    setBusy(button, true, '提交中…');
    try {
      await api(`/education/group3/student/previews/${state.selectedPreviewId}/submit?bindingId=${encodeURIComponent(state.bindingId)}`, {
        method: 'POST', body: { answers }
      });
      toast('预习已提交');
      if (stageMode) {
        // 只更新当前环节的完成态，保留过程上下文，不打断「下一环节」的连续操作。
        const current = state.nodeIndex[state.stageKey];
        if (current) {
          current.meta.submitted = true;
          current.tags = (current.tags || []).map(tag => tag === '待完成' ? '已提交' : tag);
        }
        delete state.stageAnswers[state.stageKey];
        const stageGroup = (state.nodeIndex[state.stageKey] || {}).groupKey || '';
        const track = root.querySelector('.g3-stage-track');
        if (track) track.outerHTML = renderStageTrack(state.stageKey, stageGroup);
        const container = document.getElementById('g3StageBody');
        if (container && current) loadStageQuiz(current, container);
        return;
      }
      state.selectedKey = '';
      const context = currentContext();
      await loadStudentTimeline();
      const node = state.nodes.find(item => String(item.id) === String(detail.assignmentId || state.selectedPreviewId));
      state.selectedKey = node ? node.key : '';
      paintStudentTimeline(context, state.route);
      paintStudentDetail(state.route);
    } catch (error) {
      toast(actionableMessage(error), true);
      setBusy(button, false);
      if (error && (error.status === 403 || error.status === 409)) refreshRoute();
    }
  }

  /* ------------------------------------------------------------------ *
   * 独立环节页：总览里每个环节都有一张占满内容区的作答 / 编辑页
   * 用 #stage=<key> 保持「单独页面」的可刷新、可前进后退，但不改 ?g=&i= 路由。
   * ------------------------------------------------------------------ */

  function stageUrl(url) { state.stageUrls.push(url); return url; }

  function clearStageUrls() {
    state.stageUrls.forEach(url => { try { URL.revokeObjectURL(url); } catch (_) { /* 已释放 */ } });
    state.stageUrls = [];
  }

  function stageFromHash() {
    try {
      const match = String(location.hash || '').match(/stage=([^&]+)/);
      return match ? decodeURIComponent(match[1]) : '';
    } catch (_) { return ''; }
  }

  function writeStageHash(key) {
    state.stageKey = key || '';
    const next = key ? 'stage=' + encodeURIComponent(key) : '';
    try {
      if (typeof window.history !== 'undefined' && typeof window.history.pushState === 'function') {
        const base = location.pathname + location.search;
        window.history.pushState(null, '', base + (key ? '#stage=' + encodeURIComponent(key) : ''));
      } else if (String(location.hash || '').replace(/^#/, '') !== next) {
        location.hash = next;
      }
    } catch (_) { /* 不支持 history API 时只依赖内存状态 */ }
  }

  function clearStageHash() {
    state.stageKey = '';
    try {
      if (typeof window.history !== 'undefined' && typeof window.history.replaceState === 'function') {
        window.history.replaceState(null, '', location.pathname + location.search);
      }
    } catch (_) { /* 忽略 */ }
  }

  // 切换环节前把当前表单存进该环节自己的草稿槽，各环节互不覆盖。
  function captureStageDraft() {
    const form = document.getElementById('g3StageAnswerForm');
    if (!form || !state.stageKey) return;
    const draft = {};
    form.querySelectorAll('[name^="question-"]').forEach(input => {
      if (!draft[input.name]) draft[input.name] = [];
      if (input.type === 'radio' || input.type === 'checkbox') {
        if (input.checked) draft[input.name].push(input.value);
      } else {
        draft[input.name].push(input.value);
      }
    });
    state.stageAnswers[state.stageKey] = draft;
  }

  function restoreStageDraft(form) {
    const draft = state.stageAnswers[state.stageKey];
    if (!draft || !form) return;
    Object.keys(draft).forEach(name => {
      const values = draft[name] || [];
      form.querySelectorAll(`[name="${name}"]`).forEach(input => {
        if (input.type === 'radio' || input.type === 'checkbox') input.checked = values.includes(input.value);
        else input.value = values[0] || '';
      });
    });
  }

  function applyStageView() {
    const flow = createFromHash() || state.createFlow;
    if (flow && CREATE_FLOWS[flow] && role === 'teacher') {
      state.createFlow = flow;
      state.stageKey = '';
      paintCreatePage(flow);
      return true;
    }
    const key = stageFromHash() || state.stageKey;
    if (!key || !state.nodeIndex[key]) {
      if (key) clearStageHash();
      state.stageKey = '';
      return false;
    }
    state.stageKey = key;
    state.selectedKey = key;
    paintStage(currentContext());
    return true;
  }

  function openStage(key) {
    if (!state.nodeIndex[key]) {
      toast('该环节已不可访问，请返回总览重新加载', true);
      return;
    }
    captureStageDraft();
    clearStageUrls();
    writeStageHash(key);
    state.selectedKey = key;
    paintStage(currentContext());
  }

  function backToTimeline() {
    captureStageDraft();
    clearStageUrls();
    writeStageHash('');
    // 返回总览时重新向服务端取数，完成状态与可见范围以服务端记录为准。
    if (role === 'teacher') renderTeacherPrepare();
    else renderStudentTimeline(state.route);
  }

  function stepStage(delta) {
    const siblings = stageNodesOf(state.stageKey);
    const index = siblings.findIndex(node => node.key === state.stageKey);
    if (index < 0) return;
    const target = siblings[index + delta];
    if (target) openStage(target.key);
  }

  function syncFromHistory() {
    const flow = createFromHash();
    if (flow && CREATE_FLOWS[flow] && role === 'teacher') {
      if (flow === state.createFlow) return;
      captureStageDraft();
      clearStageUrls();
      state.stageKey = '';
      state.createFlow = flow;
      paintCreatePage(flow);
      return;
    }
    if (state.createFlow) {
      state.createFlow = '';
      state.createDraft = {};
      try { sessionStorage.removeItem(CREATE_DRAFT_KEY); } catch (_) { /* 忽略 */ }
      clearStageUrls();
      if (role === 'teacher') renderTeacherPrepare();
      else renderStudentTimeline(state.route);
      return;
    }
    const key = stageFromHash();
    if (key === state.stageKey) return;
    captureStageDraft();
    clearStageUrls();
    state.stageKey = key && state.nodeIndex[key] ? key : '';
    if (state.stageKey) {
      state.selectedKey = state.stageKey;
      paintStage(currentContext());
    } else if (role === 'teacher') {
      renderTeacherPrepare();
    } else {
      renderStudentTimeline(state.route);
    }
  }

  function paintStage(context) {
    const node = state.nodeIndex[state.stageKey];
    if (!node) { clearStageHash(); return backToTimeline(); }
    const kind = KINDS[node.kind] || KINDS.MATERIAL;
    const siblings = stageNodesOf(node.key);
    const index = siblings.findIndex(item => item.key === node.key);
    const prev = index > 0 ? siblings[index - 1] : null;
    const next = index >= 0 && index < siblings.length - 1 ? siblings[index + 1] : null;
    root.innerHTML = `<div class="g3-stage-page">
      <div class="g3-stage-topbar">
        <button class="g3-btn" type="button" data-act="stage-back">← 返回</button>
        <div class="g3-stage-crumb">${escapeHtml(node.groupLabel
          || (context ? `${context.courseTitle} · ${context.className}` : '本次课前学习'))}
          ${node.seq ? ` · 第 ${node.seq} / ${node.total} 环节` : ' · 教师私有'}</div>
        <div class="g3-stage-counter">${node.seq ? `<strong>${node.seq}</strong> / ${node.total}` : '—'}</div>
      </div>
      <div class="g3-stage-head ${kind.tone}">
        <div class="g3-stage-head-line">
          <span class="g3-tl-kind ${kind.tone}"><i>${kind.icon}</i>${escapeHtml(kind.label)}</span>
          ${seqBadge(node)} ${statePill(node.state)}
        </div>
        <h1>${escapeHtml(node.title)}</h1>
        <div class="g3-stage-meta">
          <span>开放 ${escapeHtml(formatTime(node.opensAt))}</span>
          <span>截止 ${escapeHtml(formatTime(node.closesAt))}</span>
          ${node.kind === 'QUIZ' ? `<span>${Number(node.meta.itemCount || 0)} 道题</span>` : ''}
          ${node.kind === 'MATERIAL' ? `<span>V${escapeHtml(node.meta.versionNo || 1)}</span>` : ''}
        </div>
      </div>
      ${renderStageTrack(node.key, node.groupKey)}
      <div class="g3-stage-body" id="g3StageBody">${loadingState('正在载入本环节内容')}</div>
      <div class="g3-stage-footer">
        <button class="g3-btn" type="button" data-act="stage-prev" ${prev ? '' : 'disabled'}>← 上一环节</button>
        <button class="g3-btn" type="button" data-act="stage-back">环节总览</button>
        <button class="g3-btn primary" type="button" data-act="stage-next" ${next ? '' : 'disabled'}>下一环节 →</button>
      </div>
      <div class="g3-help" id="g3StageHint"></div>
    </div>`;
    mountStageBody(node);
  }

  function mountStageBody(node) {
    const container = document.getElementById('g3StageBody');
    if (!container) return;
    if (role === 'student' && node.kind === 'QUIZ') return loadStageQuiz(node, container);
    if (role === 'student' && node.kind === 'MATERIAL') return mountMaterialReader(node, container);
    return stageTeacherBody(node, container);
  }

  async function loadStageQuiz(node, container) {
    container = container || document.getElementById('g3StageBody');
    if (!container) return;
    const previewId = String(node.id);
    state.selectedPreviewId = previewId;
    try {
      const detail = await api(`/education/group3/student/previews/${encodeURIComponent(previewId)}?bindingId=${encodeURIComponent(state.bindingId)}`);
      if (state.stageKey !== node.key || document.getElementById('g3StageBody') !== container) return;
      const items = detail.items || [];
      const canSubmit = detail.state === 'OPEN' && !detail.submitted && items.length > 0;
      container.innerHTML = `<div class="g3-stage-quiz">
        <div class="g3-stage-section-head"><h2>本环节作答</h2>
          <span>${items.length} 道题 · ${detail.submitted ? '已提交' : '待完成'}</span></div>
        ${detail.instructions ? `<div class="g3-inline-note">${escapeHtml(detail.instructions)}</div>` : ''}
        ${detail.state === 'UPCOMING' ? emptyState('任务尚未开放', `${formatTime(detail.opensAt)} 开放`) : ''}
        ${detail.state !== 'UPCOMING' && !items.length ? emptyState('本任务没有题目') : ''}
        ${canSubmit ? '' : `<div class="g3-inline-note warn">${escapeHtml(submitBlockReason(detail, items))}</div>`}
        <form id="g3StageAnswerForm" class="g3-stage-form">
          ${items.map((item, index) => renderStudentQuestion(item, detail, index + 1, !canSubmit)).join('')}
          <div class="g3-stage-submit">
            <button class="g3-btn primary" type="submit" data-submit-preview${canSubmit ? '' : ' disabled'}>提交本环节预习</button>
          </div>
        </form>
        ${detail.submitted ? `<div class="g3-feedback">已于 ${formatTime(detail.submission?.submittedAt)} 提交${detail.answerVisible ? ' · 解析已公布' : ''}</div>` : ''}
        ${(detail.resources || []).length ? `<div class="g3-stage-links"><div class="g3-task-actions">${detail.resources.map(resource =>
          `<button class="g3-btn quiet" type="button" data-act="detail-preview" data-version="${resource.versionId}">${escapeHtml(resource.title)}</button>`).join('')}</div></div>` : ''}
      </div>`;
      const form = container.querySelector('#g3StageAnswerForm');
      restoreStageDraft(form);
      form?.addEventListener('submit', event => {
        event.preventDefault();
        if (!canSubmit) return;
        submitStudentPreview(event, detail, event.currentTarget, true);
      });
    } catch (error) {
      if (error && (error.status === 403 || error.status === 404 || error.status === 409)) return handleRevoked(error);
      container.innerHTML = errorState(error);
      container.querySelector('[data-act="retry"]')?.addEventListener('click', () => loadStageQuiz(node, container));
    }
  }

  async function mountMaterialReader(node, container) {
    container.innerHTML = `<div class="g3-stage-reader">
      <div class="g3-stage-section-head"><h2>资料阅读</h2>
        <div class="g3-actions">
          <button class="g3-btn" type="button" data-act="stage-download" data-key="${escapeHtml(node.key)}">下载</button>
        </div></div>
      <div class="g3-reader-canvas" id="g3ReaderCanvas">${loadingState('正在读取预览页')}</div>
    </div>`;
    const canvas = container.querySelector('#g3ReaderCanvas');
    if (canvas) await fillReaderCanvas(node, canvas);
  }

  // 学生阅读页与教师环节编辑页共用同一个受控阅读区，blob 地址随环节切换立即释放。
  async function fillReaderCanvas(node, canvas) {
    try {
      const version = await api(`/education/group3/versions/${encodeURIComponent(node.id)}?bindingId=${encodeURIComponent(state.bindingId)}`);
      if (state.stageKey !== node.key) return;
      if (version.mediaKind === 'LINK' && version.externalUri) {
        canvas.innerHTML = `<div class="g3-inline-note">该资料为外部链接。
          <a href="${escapeHtml(version.externalUri)}" target="_blank" rel="noopener noreferrer">打开链接</a></div>`;
        return;
      }
      if (version.mediaKind === 'VIDEO') {
        const blob = await fetchBlob(`/education/group3/versions/${node.id}/download?bindingId=${encodeURIComponent(state.bindingId)}`);
        if (state.stageKey !== node.key) return;
        canvas.innerHTML = `<div class="g3-preview-box"><video controls src="${stageUrl(URL.createObjectURL(blob))}"></video></div>`;
        return;
      }
      if (!version.pages || !version.pages.length) {
        canvas.innerHTML = emptyState('该资料没有分页预览', '可直接下载原文件',
          `<button class="g3-btn" type="button" data-act="stage-download" data-key="${escapeHtml(node.key)}">下载原文件</button>`);
        return;
      }
      const urls = [];
      for (const page of version.pages) {
        const blob = await fetchBlob(`/education/group3/versions/${node.id}/pages/${page.pageNo}?bindingId=${encodeURIComponent(state.bindingId)}`);
        urls.push(stageUrl(URL.createObjectURL(blob)));
      }
      if (state.stageKey !== node.key) return;
      let pageIndex = 0;
      const renderPage = () => {
        canvas.innerHTML = `<div class="g3-preview-box"><img alt="第 ${pageIndex + 1} 页" src="${urls[pageIndex]}"></div>
          <div class="g3-page-nav">
            <button class="g3-btn" type="button" data-reader-prev ${pageIndex === 0 ? 'disabled' : ''}>上一页</button>
            <span class="g3-help">${pageIndex + 1} / ${urls.length}</span>
            <button class="g3-btn" type="button" data-reader-next ${pageIndex === urls.length - 1 ? 'disabled' : ''}>下一页</button>
          </div>`;
        canvas.querySelector('[data-reader-prev]')?.addEventListener('click', () => {
          if (pageIndex > 0) { pageIndex -= 1; renderPage(); }
        });
        canvas.querySelector('[data-reader-next]')?.addEventListener('click', () => {
          if (pageIndex < urls.length - 1) { pageIndex += 1; renderPage(); }
        });
      };
      renderPage();
    } catch (error) {
      if (error && error.revoked) return handleRevoked(error);
      canvas.innerHTML = errorState(error);
      canvas.querySelector('[data-act="retry"]')?.addEventListener('click', () => fillReaderCanvas(node, canvas));
    }
  }

  // 环节对应的真实业务对象主键；编辑与删除共用同一套映射。
  // 资料增删直接作用于按班发布记录：/releases 新增、/releases/{id}/revoke 移出。
  // 不删除环节、不删除资源本体，发布范围与窗口仍由服务端裁决。
  function stageMaterialRows(node, nodes = state.nodes || []) {
    const group = node.groupKey || '';
    return nodes.filter(row =>
      row.kind === 'MATERIAL' && (!group || row.groupKey === group)
    ).map(row => ({
      key: row.key,
      title: row.title,
      versionId: row.id,
      versionNo: row.meta.versionNo || 1,
      releaseId: row.meta.releaseId || row.releaseId || null,
      isCurrent: row.key === node.key
    }));
  }

  function stageMaterialsCard(node) {
    if (node.kind !== 'MATERIAL' && node.kind !== 'QUIZ') return '';
    const rows = stageMaterialRows(node);
    return `<div class="g3-edit-card g3-edit-materials">
      <div class="g3-edit-head compact"><h3>本堂课资料 <span class="count">${rows.length}</span></h3>
        <button class="g3-btn primary" type="button" data-act="stage-material-add"
          data-key="${escapeHtml(node.key)}">+ 添加资料</button></div>
      <div class="g3-edit-body">${rows.length ? rows.map(row => `<div class="g3-material-row">
          <span class="g3-material-icon">▤</span>
          <div class="g3-material-name"><strong>${escapeHtml(row.title)}</strong>
            <small>V${escapeHtml(row.versionNo)}${row.isCurrent ? ' · 当前环节' : ''}</small></div>
          <div class="g3-task-actions">
            <button class="g3-btn quiet" type="button" data-act="stage-material-preview"
              data-version="${escapeHtml(row.versionId)}">预览</button>
            <button class="g3-btn quiet danger" type="button" data-act="stage-material-remove"
              data-release="${escapeHtml(row.releaseId)}" data-title="${escapeHtml(row.title)}"
              data-key="${escapeHtml(node.key)}">移出</button>
          </div></div>`).join('')
        : emptyState('本堂课还没有资料', '点「添加资料」从课程资源库选用或上传新文件')}</div>
    </div>`;
  }

  function stageTimeWindow(node) {
    const form = document.getElementById('g3StageEditForm');
    const opensAt = isoFromInput(form?.elements.opensAt?.value || localInput(node.opensAt));
    const closesAt = isoFromInput(form?.elements.closesAt?.value ?? localInput(node.closesAt));
    if (!opensAt) throw new Error('请先设置本环节的开放时间');
    if (closesAt && new Date(closesAt) <= new Date(opensAt)) throw new Error('截止时间必须晚于开放时间');
    return { opensAt, closesAt };
  }

  function showAddMaterialModal(node) {
    const overview = state.overview || {};
    const rows = stageMaterialRows(node);
    const used = new Set(rows.map(row => String(row.versionId)));
    const ownResource = String(node.meta?.resourceId || '');
    const ready = (overview.materialVersions || []).filter(item =>
      !used.has(String(item.versionId)) && String(item.resourceId) !== ownResource);
    const modal = openModal({
      title: '添加资料到本堂课', wide: true,
      body: `<form id="g3AddMaterialForm" class="g3-list">
        <div class="g3-static-field">沿用「环节设置」中当前的开放 / 截止时间，无需重复填写</div>
        <div class="g3-field"><label>从课程资源库选用</label>
          ${materialPicker({ ...overview, materialVersions: ready }, {}, { name: 'materialVersionIds' })}</div>
        <div class="g3-form-grid">
          <div class="g3-field full"><label for="g3AddMaterialTitle">或上传新文件 · 资料名称</label>
            <input id="g3AddMaterialTitle" class="g3-input" name="title" maxlength="200"
              placeholder="不填写时使用文件名"></div>
          <div class="g3-field full"><label for="g3AddMaterialFile">选择文件</label>
            <input id="g3AddMaterialFile" class="g3-input" name="file" type="file"
              accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md"></div>
        </div>
        ${node.kind === 'MATERIAL' ? `<label class="g3-check-line"><input type="checkbox" name="replaceCurrent">
          替换本环节资料：添加成功后移出当前这份</label>` : ''}
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3AddMaterialForm">确认添加</button>`,
      onMount: box => wirePickers(box)
    });
    const form = modal.querySelector('#g3AddMaterialForm');
    form?.addEventListener('submit', async event => {
      event.preventDefault();
      const button = modal.querySelector('.g3-modal-foot .primary');
      let timeWindow;
      try { timeWindow = stageTimeWindow(node); } catch (error) { return toast(error.message, true); }
      const ids = [...form.querySelectorAll('input[name="materialVersionIds"]:checked')]
        .map(input => Number(input.value));
      const file = form.elements.file?.files?.[0] || null;
      if (!ids.length && !file) return toast('请勾选资料版本或选择要上传的文件', true);
      setBusy(button, true, '添加中…');
      try {
        let uploadId = null;
        if (file) {
          const payload = new FormData();
          payload.append('bindingId', String(state.bindingId));
          payload.append('title', String(form.elements.title?.value || '').trim());
          payload.append('file', file);
          const uploaded = await api('/education/group3/resources', { method: 'POST', body: payload });
          if (String(uploaded.processingStatus).toUpperCase() !== 'READY' || !uploaded.versionId) {
            throw new Error('文件已上传但尚未处理完成，请稍后在「课程建设」查看');
          }
          uploadId = Number(uploaded.versionId);
        }
        for (const versionId of [...ids, uploadId].filter(Boolean)) {
          await api('/education/group3/releases', {
            method: 'POST',
            body: { bindingId: Number(state.bindingId), resourceVersionId: versionId,
              opensAt: timeWindow.opensAt, closesAt: timeWindow.closesAt }
          });
        }
        const replaced = Boolean(form.elements.replaceCurrent?.checked
          && node.kind === 'MATERIAL' && node.meta?.releaseId);
        if (replaced) {
          await api(`/education/group3/releases/${node.meta.releaseId}/revoke?bindingId=${encodeURIComponent(state.bindingId)}`, {
            method: 'POST', body: { reason: '教师在环节设置中替换资料' }
          });
        }
        closeModal();
        clearOverview();
        if (replaced) {
          clearStageHash(); state.stageKey = ''; state.selectedKey = '';
        }
        toast(replaced ? '资料已替换，返回时间轴' : '资料已添加到本堂课');
        refreshRoute();
      } catch (error) {
        toast(actionableMessage(error), true);
        setBusy(button, false);
      }
    });
  }

  async function removeStageMaterial(releaseId, title, node) {
    if (!releaseId) return toast('该资料没有可移出的发布记录', true);
    const context = currentContext();
    if (!window.confirm(`把“${title}”移出${context ? context.className : '当前教学班'}？资源本体保留，以后仍可再次选用。`)) return;
    try {
      await api(`/education/group3/releases/${releaseId}/revoke?bindingId=${encodeURIComponent(state.bindingId)}`, {
        method: 'POST', body: { reason: '教师在环节设置中移出资料' }
      });
      toast('资料已移出本堂课，资源本体保留');
      clearOverview();
      if (node && String(node.meta?.releaseId) === String(releaseId)) {
        clearStageHash(); state.stageKey = ''; state.selectedKey = '';
      }
      refreshRoute();
    } catch (error) { toast(actionableMessage(error), true); }
  }

  function stageUpdateTarget(node) {
    if (node.kind === 'QUIZ') return { kind: 'QUIZ', id: node.id };
    if (node.kind === 'PLAN') return { kind: 'PLAN', id: node.id };
    return { kind: node.kind === 'DRAFT' ? 'DRAFT' : 'MATERIAL', id: node.meta.resourceId || node.id };
  }

  async function saveStageSettings(node, form, button) {
    const data = new FormData(form);
    const title = String(data.get('title') || '').trim();
    if (!title) return toast('请填写环节名称', true);
    let opensAt, closesAt, answerOpensAt = null;
    try {
      opensAt = isoFromInput(data.get('opensAt'));
      closesAt = isoFromInput(data.get('closesAt'));
      if (node.kind === 'QUIZ') answerOpensAt = isoFromInput(data.get('answerOpensAt'));
    } catch (error) { return toast(error.message, true); }
    if (node.kind !== 'PLAN' && !opensAt) return toast('请填写开放时间', true);
    if (node.kind === 'QUIZ' && !closesAt) return toast('请填写截止时间', true);
    if (opensAt && closesAt && new Date(closesAt) <= new Date(opensAt)) {
      return toast('截止时间必须晚于开放时间', true);
    }
    if (answerOpensAt && closesAt && new Date(answerOpensAt) < new Date(closesAt)) {
      return toast('解析公布时间不能早于截止时间', true);
    }
    const draft = node.kind === 'DRAFT';
    const target = stageUpdateTarget(node);
    const payload = draft
      ? { bindingId: Number(state.bindingId), resourceVersionId: Number(node.id), opensAt, closesAt }
      : {
          bindingId: Number(state.bindingId), kind: target.kind, id: Number(target.id), title,
          opensAt, closesAt, answerOpensAt,
          instructions: node.kind === 'QUIZ' ? String(data.get('instructions') || '') : null,
          privateNotes: node.kind === 'PLAN' ? String(data.get('privateNotes') || '') : null
        };
    setBusy(button, true, '保存中…');
    try {
      await api(draft ? '/education/group3/releases' : '/education/group3/stages/update', {
        method: 'POST', body: payload
      });
      toast(draft ? '已发布到时间轴' : '环节设置已保存');
      if (draft) {
        clearOverview(); clearStageHash();
        state.stageKey = ''; state.selectedKey = ''; state.selectedPreviewId = null;
        await renderTeacherPrepare();
        return;
      }
      node.title = title;
      node.opensAt = opensAt;
      node.closesAt = closesAt;
      if (node.kind === 'QUIZ') {
        node.meta.answerOpensAt = answerOpensAt;
        node.meta.instructions = payload.instructions;
        node.subtitle = payload.instructions;
      }
      if (node.kind === 'PLAN') {
        node.meta.privateNotes = payload.privateNotes;
        node.subtitle = payload.privateNotes;
      }
      clearOverview();
      paintStage(currentContext());
    } catch (error) {
      toast(actionableMessage(error), true);
      setBusy(button, false);
    }
  }

  function stageTeacherBody(node, container) {
    const meta = node.meta || {};
    const kind = KINDS[node.kind] || KINDS.MATERIAL;
    const isQuiz = node.kind === 'QUIZ';
    const isPlan = node.kind === 'PLAN';
    const isDraft = node.kind === 'DRAFT';
    const failed = node.state === 'FAILED';

    const stats = [];
    if (node.seq) stats.push(`第 ${node.seq} / ${node.total} 环节`);
    if (isQuiz) {
      stats.push(`${Number(meta.itemCount || 0)} 道题`);
      stats.push(`${Number(meta.submissionCount || 0)} / ${Number(meta.targetCount || 0)} 人已提交`);
    }
    if (node.kind === 'MATERIAL' || isDraft) {
      stats.push(`V${meta.versionNo || 1}`);
      stats.push(`${Number(meta.pageCount || 0)} 页`);
    }
    if (isPlan) stats.push(`${(meta.materials || []).length} 份材料`);

    const openValue = node.opensAt ? localInput(node.opensAt) : localInput();
    const closeValue = node.closesAt ? localInput(node.closesAt)
      : (isDraft || node.kind === 'MATERIAL' ? '' : futureInput(3));
    const answerValue = meta.answerOpensAt ? localInput(meta.answerOpensAt)
      : (node.closesAt ? localInput(node.closesAt) : futureInput(3));

    const timeFields = isPlan ? '' : `
      <div class="g3-field"><label for="g3EditOpen">开放时间</label>
        <input id="g3EditOpen" name="opensAt" class="g3-input" type="datetime-local"
          value="${escapeHtml(openValue)}"></div>
      <div class="g3-field"><label for="g3EditClose">截止时间${node.kind === 'MATERIAL' ? ' · 留空为不限' : ''}</label>
        <input id="g3EditClose" name="closesAt" class="g3-input" type="datetime-local"
          value="${escapeHtml(closeValue)}"></div>
      ${isQuiz ? `<div class="g3-field"><label for="g3EditAnswer">解析公布</label>
        <input id="g3EditAnswer" name="answerOpensAt" class="g3-input" type="datetime-local"
          value="${escapeHtml(answerValue)}"></div>` : ''}`;

    const noteField = isQuiz
      ? `<div class="g3-field full"><label for="g3EditNote">学习要求</label>
          <textarea id="g3EditNote" name="instructions" class="g3-input" rows="2"
            placeholder="选填">${escapeHtml(meta.instructions || node.subtitle || '')}</textarea></div>`
      : isPlan
        ? `<div class="g3-field full"><label for="g3EditNote">私有备注</label>
            <textarea id="g3EditNote" name="privateNotes" class="g3-input" rows="2"
              placeholder="选填，仅教师可见">${escapeHtml(meta.privateNotes || '')}</textarea></div>`
        : '';

    const button = (act, label, style = '') =>
      `<button class="g3-btn ${style}" type="button" data-act="${act}" data-key="${escapeHtml(node.key)}">${label}</button>`;
    const more = [];
    if (node.kind === 'MATERIAL') {
      more.push(button('stage-preview', '预览'), button('stage-versions', '版本'),
        button('stage-new-version', '新版本'), button('stage-revoke', '撤回', 'quiet danger'));
    } else if (isDraft) {
      if (failed) more.push(button('stage-failure', '失败原因'), button('stage-new-version', '重新上传'));
      else more.push(button('stage-preview', '预览'));
    } else if (isQuiz) {
      more.push('<button class="g3-btn quiet" type="button" data-act="add-stage">继续发布预习</button>');
    } else {
      more.push('<button class="g3-btn quiet" type="button" data-act="gen-plan">新建备课包</button>');
    }
    more.push(button('stage-remove', '删除环节', 'quiet danger'));

    let content = '';
    let contentTitle = '';
    if (isQuiz) {
      contentTitle = '本环节题目';
      const stems = state.teacherTasks.filter(row => String(row.assignmentId) === String(node.id));
      content = stems.length
        ? stems.map((row, index) => `<div class="g3-edit-row"><span>${index + 1}</span>
            <strong>${escapeHtml(row.stem || row.title || '题目')}</strong></div>`).join('')
        : emptyState('暂无题目明细');
    } else if (isPlan) {
      contentTitle = '备课材料';
      content = (meta.materials || []).length
        ? meta.materials.map(item => `<div class="g3-edit-row"><span>▤</span>
            <strong>${escapeHtml(item.title || '未命名')}</strong></div>`).join('')
        : emptyState('暂无材料');
    } else if (node.kind === 'MATERIAL') {
      contentTitle = '资料预览';
      content = '<div class="g3-reader-canvas" id="g3TeacherReader">'
        + loadingState('正在读取预览页') + '</div>';
    } else if (failed) {
      contentTitle = '处理结果';
      content = `<div class="g3-inline-note warn">${escapeHtml(meta.processingMessage || '资料转换失败')}
        · 上传新版本后可在此设置开放时间并发布</div>`;
    }

    container.innerHTML = `<div class="g3-edit-card">
      <div class="g3-edit-head">
        <div><span class="g3-edit-kicker">${escapeHtml(kind.label)} · 环节设置</span>
          <h2>${escapeHtml(node.title)}</h2></div>
        ${statePill(node.state)}
      </div>
      <form id="g3StageEditForm" class="g3-edit-form">
        <div class="g3-field full"><label for="g3EditTitle">环节名称</label>
          <input id="g3EditTitle" name="title" class="g3-input" type="text"
            maxlength="160" value="${escapeHtml(node.title)}"></div>
        ${timeFields}${noteField}
        <div class="g3-edit-foot">
          <div class="g3-edit-stats">${stats.map(text =>
            `<span>${escapeHtml(text)}</span>`).join('')}</div>
          <button class="g3-btn primary" type="submit"${failed ? ' disabled' : ''}>
            ${isDraft ? '保存并发布' : '保存设置'}</button>
        </div>
      </form>
    </div>
    ${content ? `<div class="g3-edit-card g3-edit-content">
      <div class="g3-edit-head compact"><h3>${escapeHtml(contentTitle)}</h3></div>
      <div class="g3-edit-body">${content}</div></div>` : ''}
    ${stageMaterialsCard(node)}
    <div class="g3-edit-more">${more.join('')}</div>`;

    const form = container.querySelector('#g3StageEditForm');
    form?.addEventListener('submit', event => {
      event.preventDefault();
      saveStageSettings(node, form, form.querySelector('[type="submit"]'));
    });
    // 截止时间延后时解析公布时间自动跟随，避免两头改还被窗口校验拦住。
    if (isQuiz) {
      const closeInput = form?.querySelector('#g3EditClose');
      const answerInput = form?.querySelector('#g3EditAnswer');
      closeInput?.addEventListener('change', () => {
        if (!answerInput || !closeInput.value) return;
        if (answerInput.value && answerInput.value >= closeInput.value) return;
        answerInput.value = closeInput.value;
      });
    }
    if (node.kind === 'MATERIAL') {
      const canvas = container.querySelector('#g3TeacherReader');
      if (canvas) fillReaderCanvas(node, canvas);
    }
  }

  /* ------------------------------------------------------------------ *
   * 事件委托与路由
   * ------------------------------------------------------------------ */

  function refreshRoute() { routeFromLocation(); }

  function renderUnsupported() {
    clearObjectUrls();
    root.innerHTML = emptyState('该模块尚未接入', '当前可用入口：教师「课程建设」「课前准备」，学生「课程资料」「课前学习」');
  }

  function dispatch(g, i) {
    if (role === 'teacher' && g === routes.studio.g && i === routes.studio.i) return renderTeacherStudio();
    if (role === 'teacher' && g === routes.prepare.g && i === routes.prepare.i) return renderTeacherPrepare();
    if (role === 'student' && g === routes.materials.g && i === routes.materials.i) return renderStudentTimeline('materials');
    if (role === 'student' && g === routes.prestudy.g && i === routes.prestudy.i) return renderStudentTimeline('prestudy');
    return renderUnsupported();
  }

  function routeFromLocation() {
    const query = new URLSearchParams(location.search);
    return dispatch(Number(query.get('g') || 0), Number(query.get('i') || 0));
  }

  function handleAction(trigger) {
    const act = trigger.dataset.act;
    const key = trigger.dataset.key;
    const node = key ? state.nodeIndex[key] : null;

    if (act === 'retry') return refreshRoute();
    if (act === 'switch-context') {
      state.bindingId = trigger.dataset.binding;
      localStorage.setItem(`zhijiao.group3.binding.${role}`, state.bindingId);
      clearOverview();
      clearStageUrls();
      clearStageHash();
      state.stageAnswers = {};
      state.selectedKey = '';
      state.selectedPreviewId = null;
      state.stageKey = '';
      state.createFlow = '';
      return refreshRoute();
    }
    if (act === 'stage-open' && key) return openStage(key);
    if (act === 'stage-back') return backToTimeline();
    if (act === 'stage-prev') return stepStage(-1);
    if (act === 'stage-next') return stepStage(1);
    if (act === 'stage-select') {
      state.selectedKey = key;
      if (state.stageKey) return openStage(key);
      if (role === 'teacher') paintTeacherDetail();
      else paintStudentDetail(state.route);
      return;
    }
    if (act === 'stage-material-add' && node) return showAddMaterialModal(node);
    if (act === 'stage-material-preview') return showPreview(Number(trigger.dataset.version), state.bindingId);
    if (act === 'stage-material-remove') return removeStageMaterial(trigger.dataset.release, trigger.dataset.title, node);
    if (act === 'stage-preview' && node) {
      if (node.kind === 'DRAFT' && node.state === 'FAILED') return showFailureModal(node);
      return showPreview(node.id, state.bindingId);
    }
    if (act === 'stage-download' && node) return downloadVersion(node.id, state.bindingId);
    if (act === 'stage-release' && node) return showReleaseModal(node.id, node.title);
    if (act === 'stage-revoke' && node) return revokeRelease(node);
    if (act === 'stage-remove' && node) {
      if (role !== 'teacher') return toast('仅任课教师可以删除环节', true);
      return confirmRemoveStage(node);
    }
    if (act === 'stage-versions' && node) return showVersions(node.meta.resourceId);
    if (act === 'stage-new-version' && node) return showNewVersionModal(node.meta.resourceId, node.title);
    if (act === 'stage-failure' && node) return showFailureModal(node);
    if (act === 'detail-preview') return showPreview(trigger.dataset.version, state.bindingId);
    if (act === 'add-stage') return showAddStageModal();
    if (act === 'gen-plan') return openCreateFlow('plan');
    if (act === 'create-material-stage') return openCreateFlow('material', { source: 'existing' });
    if (act === 'create-quiz-stage') return openCreateFlow('quiz');
    if (act === 'view') {
      state.prepareView = trigger.dataset.view;
      const context = currentContext();
      if (context && role === 'teacher') paintTeacherPrepare(state.overview, context);
      return;
    }
    // 跨页面跳转沿用原框架的 ?g=&i= 路由，保证菜单、面包屑、页签与内容一致。
    if (act === 'go-studio') { location.href = buildUrl(`?g=${routes.studio.g}&i=${routes.studio.i}`); return; }
    if (act === 'go-prepare') { location.href = buildUrl(`?g=${routes.prepare.g}&i=${routes.prepare.i}`); return; }
    if (act === 'student-switch') {
      const target = trigger.dataset.mode === 'materials' ? routes.materials : routes.prestudy;
      location.href = buildUrl(`?g=${target.g}&i=${target.i}`);
      return;
    }
    if (act === 'studio-tab') {
      state.studioTab = trigger.dataset.tab;
      const context = currentContext();
      if (context) paintTeacherStudio(state.overview, context);
      return;
    }
    if (act === 'upload-resource' || act === 'upload-for-unit') return showUploadModal(trigger.dataset.unit || '');
    if (act === 'create-question') return showQuestionModal(null);
    if (act === 'question-version') return showQuestionModal({
      questionId: trigger.dataset.question, title: trigger.dataset.title, kind: trigger.dataset.kind
    });
    if (act === 'archive-question') return archiveQuestion(trigger.dataset.question);
    if (act === 'preview') return showPreview(trigger.dataset.version, state.bindingId);
    if (act === 'versions') return showVersions(trigger.dataset.resource);
    if (act === 'release') return showReleaseModal(Number(trigger.dataset.version), trigger.dataset.title);
    if (act === 'new-version') return showNewVersionModal(trigger.dataset.resource, trigger.dataset.title);
    if (act === 'failure') {
      const item = (state.overview?.resources || []).find(row => String(row.resourceId) === String(trigger.dataset.resource));
      if (item) return showFailureModal(makeNode({
        key: `draft-${item.resourceId}`, kind: 'DRAFT', id: item.versionId,
        title: item.title, state: 'FAILED', meta: item
      }));
      return;
    }
    if (act === 'revoke') {
      const item = (state.overview?.resources || []).find(row => String(row.releaseId) === String(trigger.dataset.release));
      if (item) return revokeRelease(makeNode({
        key: `material-${item.resourceId}`, kind: 'MATERIAL', id: item.versionId,
        title: item.title, meta: item
      }));
    }
  }

  root.addEventListener('click', event => {
    const trigger = event.target.closest('[data-act]');
    if (!trigger || !root.contains(trigger)) return;
    handleAction(trigger);
  });

  root.addEventListener('keydown', event => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const card = event.target.closest('.g3-tl-card[data-act="stage-select"]');
    if (!card) return;
    event.preventDefault();
    handleAction(card);
  });

  root.addEventListener('change', event => {
    const target = event.target;
    if (target.id === 'g3Context') {
      state.bindingId = target.value;
      localStorage.setItem(`zhijiao.group3.binding.${role}`, state.bindingId);
      clearOverview();
      clearStageUrls();
      clearStageHash();
      state.stageAnswers = {};
      state.selectedKey = '';
      state.selectedPreviewId = null;
      refreshRoute();
      return;
    }
    if (target.id === 'g3Filter') {
      if (role === 'teacher') {
        state.prepareFilter = target.value;
        const context = currentContext();
        if (context) paintTeacherPrepare(state.overview, context);
      } else {
        state.studentFilter = target.value;
        state.selectedKey = '';
        const context = currentContext();
        if (context) { paintStudentTimeline(context, state.route); paintStudentDetail(state.route); }
      }
    }
  });

  // 学生从其它标签页返回时，打开中的预览链接立即失效并重新向服务端校验范围；
  // 未打开预览时不重绘页面，避免打断正在填写的作答内容。
  document.addEventListener('visibilitychange', () => {
    if (document.hidden || role !== 'student') return;
    const modalOpen = document.querySelector('.g3-modal-backdrop');
    clearObjectUrls();
    if (state.createFlow) return;
    if (modalOpen) {
      closeModal();
      toast('已重新核验资料访问权限，预览已关闭', true);
      refreshRoute();
      return;
    }
    if (state.stageKey) {
      // 独立环节页内嵌的资料链接同样要重新向服务端校验，草稿先保留。
      captureStageDraft();
      clearStageUrls();
      paintStage(currentContext());
    }
  });

  window.addEventListener('popstate', syncFromHistory);
  window.addEventListener('hashchange', syncFromHistory);
  window.addEventListener('beforeunload', clearObjectUrls);

  window.Group3App = {
    render(g, i) { return dispatch(Number(g) || 0, Number(i) || 0); },
    // 供离线逻辑校验使用：纯函数，不触发任何网络请求。
    __core: { collectTeacherNodes, collectStudentNodes, groupNodesByDay, filterNodes,
      numberStages, buildStageGroups, stageGroupKey, stageRemoveTarget, stageUpdateTarget,
      stageMaterialRows, timeState, dayKeyOf }
  };

  routeFromLocation();
})();
