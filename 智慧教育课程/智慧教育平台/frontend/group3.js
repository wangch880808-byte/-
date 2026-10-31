(() => {
  'use strict';

  const tokenKey = 'zhijiao.login.token';
  const role = document.body.dataset.role;
  const root = document.getElementById('group3Content');
  if (!root || !['teacher', 'student'].includes(role)) return;

  const state = {
    contexts: null,
    bindingId: localStorage.getItem(`zhijiao.group3.binding.${role}`) || '',
    studioTab: 'resources',
    prepareTab: 'plans',
    overview: null,
    overviewBinding: '',
    selectedPreviewId: null,
    modalUrls: []
  };

  const routes = role === 'teacher'
    ? {
        studio: { g: 1, i: 1 },
        prepare: { g: 1, i: 2 }
      }
    : {
        materials: { g: 1, i: 1 },
        prestudy: { g: 1, i: 2 }
      };

  const labels = {
    SINGLE: '单选题',
    MULTIPLE: '多选题',
    TRUE_FALSE: '判断题',
    SHORT_TEXT: '简答题',
    READY: '可用',
    PENDING: '处理中',
    FAILED: '处理失败',
    PUBLISHED: '已发布',
    CLOSED: '已截止',
    ARCHIVED: '已归档',
    UPCOMING: '未开放',
    OPEN: '进行中',
    DRAFT: '草稿',
    ACTIVE: '启用'
  };

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function authHeaders(extra = {}) {
    const token = sessionStorage.getItem(tokenKey);
    return token ? { ...extra, Authorization: `Bearer ${token}` } : extra;
  }

  async function parseResponse(response) {
    let result = null;
    try {
      result = await response.json();
    } catch {
      throw new Error(`服务器响应无法解析（${response.status}）`);
    }
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
    try {
      return await parseResponse(response);
    } catch (error) {
      if (error.code === 401 || error.status === 401) {
        sessionStorage.removeItem(tokenKey);
        sessionStorage.removeItem('zhijiao.login.role');
        location.replace('/login.html?logout=expired');
      }
      throw error;
    }
  }

  async function fetchBlob(path) {
    const response = await fetch(path, { cache: 'no-store', headers: authHeaders() });
    if (!response.ok) {
      let message = `文件读取失败（${response.status}）`;
      try {
        const payload = await response.json();
        message = payload.msg || message;
      } catch {
        // The binary endpoint may return an HTML error page.
      }
      throw new Error(message);
    }
    return response.blob();
  }

  function rememberUrl(url) {
    state.modalUrls.push(url);
    return url;
  }

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
    setTimeout(() => node.remove(), 3600);
  }

  function formatTime(value) {
    if (!value) return '不限';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    }).format(date);
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
    if (Number.isNaN(date.getTime())) throw new Error('请填写有效时间');
    return date.toISOString();
  }

  function futureInput(days) {
    return localInput(new Date(Date.now() + days * 86400000));
  }

  function statusBadge(status) {
    const value = String(status || '').toUpperCase();
    const className = ['READY', 'OPEN', 'ACTIVE', 'PUBLISHED'].includes(value)
      ? 'ready'
      : ['PENDING', 'UPCOMING', 'DRAFT'].includes(value)
        ? 'pending'
        : ['FAILED', 'CLOSED', 'ARCHIVED'].includes(value) ? 'failed' : '';
    return `<span class="g3-badge ${className}">${escapeHtml(labels[value] || value || '未知')}</span>`;
  }

  function emptyState(title, hint = '') {
    return `<div class="g3-empty"><strong>${escapeHtml(title)}</strong>${hint ? `<span>${escapeHtml(hint)}</span>` : ''}</div>`;
  }

  function loadingState(text = '正在加载') {
    return `<div class="g3-loading"><span class="g3-spinner"></span><span>${escapeHtml(text)}</span></div>`;
  }

  function errorState(error) {
    return `<div class="g3-error"><strong>加载失败</strong><span>${escapeHtml(error.message || error)}</span>
      <button class="g3-btn" type="button" data-action="retry">重试</button></div>`;
  }

  function optionMarkup(value, current) {
    return String(value) === String(current) ? ' selected' : '';
  }

  function currentContext() {
    if (!state.contexts || state.contexts.length === 0) return null;
    let item = state.contexts.find(context => String(context.bindingId) === String(state.bindingId));
    if (!item) item = state.contexts[0];
    state.bindingId = String(item.bindingId);
    localStorage.setItem(`zhijiao.group3.binding.${role}`, state.bindingId);
    return item;
  }

  async function ensureContexts() {
    if (!state.contexts) {
      state.contexts = await api('/education/group3/contexts');
    }
    return currentContext();
  }

  function pageHead(title, subtitle, context) {
    const contextOptions = state.contexts.map(item =>
      `<option value="${escapeHtml(item.bindingId)}"${optionMarkup(item.bindingId, context.bindingId)}>`
      + `${escapeHtml(item.courseTitle)} · ${escapeHtml(item.className)}</option>`).join('');
    return `<div class="g3-page-head">
      <div class="g3-title-block">
        <h2>${escapeHtml(title)}</h2>
        <p>${escapeHtml(subtitle)}</p>
      </div>
      <div class="g3-context">
        <label for="g3Context">课程 / 教学班</label>
        <select id="g3Context" class="g3-select">${contextOptions}</select>
      </div>
    </div>`;
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

  function bindContext(rootNode, rerender) {
    const select = rootNode.querySelector('#g3Context');
    if (!select) return;
    select.addEventListener('change', () => {
      state.bindingId = select.value;
      localStorage.setItem(`zhijiao.group3.binding.${role}`, state.bindingId);
      clearOverview();
      state.selectedPreviewId = null;
      rerender();
    });
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
      const tab = state.studioTab;
      let content = '';
      if (tab === 'resources') content = renderResourceTable(overview.resources || []);
      if (tab === 'questions') content = renderQuestionTable(overview.questions || []);
      if (tab === 'outline') content = renderOutline(overview.outline || [], overview.knowledgePoints || []);
      root.innerHTML = `<div class="g3-workspace">
        ${pageHead('课程建设', `${context.courseTitle} · ${context.className}`, context)}
        <div class="g3-toolbar">
          <div class="g3-segment" data-studio-tabs>
            <button type="button" data-tab="resources" class="${tab === 'resources' ? 'active' : ''}">课程资源</button>
            <button type="button" data-tab="questions" class="${tab === 'questions' ? 'active' : ''}">题库</button>
            <button type="button" data-tab="outline" class="${tab === 'outline' ? 'active' : ''}">章节与知识点</button>
          </div>
          <div class="g3-actions">
            ${tab === 'resources' ? `<button class="g3-btn primary" type="button" data-action="upload-resource">上传资料</button>` : ''}
            ${tab === 'questions' ? `<button class="g3-btn primary" type="button" data-action="create-question">新建题目</button>` : ''}
          </div>
        </div>
        ${content}
      </div>`;
      bindContext(root, renderTeacherStudio);
      root.querySelectorAll('[data-studio-tabs] button').forEach(button => {
        button.addEventListener('click', () => {
          state.studioTab = button.dataset.tab;
          renderTeacherStudio();
        });
      });
      bindTeacherStudioActions(overview, context);
    } catch (error) {
      root.innerHTML = errorState(error);
      bindRetry(root, renderTeacherStudio);
    }
  }

  function renderResourceTable(resources) {
    if (resources.length === 0) {
      return emptyState('课程还没有资料', '上传 PPTX、PDF、视频、图片或文档后会显示处理状态');
    }
    const rows = resources.map(item => {
      const status = item.processingStatus || 'PENDING';
      const released = item.releaseId
        ? `<span class="g3-badge ready">已发布</span><div class="g3-cell-meta">${formatTime(item.opensAt)} 至 ${formatTime(item.closesAt)}</div>`
        : '<span class="g3-badge">未发布</span>';
      return `<tr>
        <td><div class="g3-cell-title">${escapeHtml(item.title)}</div>
          <div class="g3-cell-meta">${escapeHtml(item.mediaKind || item.resourceKind || 'FILE')} · ${Number(item.pageCount || 0)} 页</div></td>
        <td>V${escapeHtml(item.versionNo || 1)}</td>
        <td>${statusBadge(status)}<div class="g3-cell-meta">${escapeHtml(item.processingMessage || '')}</div></td>
        <td>${released}</td>
        <td><div class="g3-actions">
          <button class="g3-btn quiet" type="button" data-action="preview" data-version="${item.versionId}" data-binding="${item.bindingId || state.bindingId}">预览</button>
          <button class="g3-btn quiet" type="button" data-action="versions" data-resource="${item.resourceId}">版本</button>
          ${status === 'READY' ? `<button class="g3-btn quiet" type="button" data-action="release" data-version="${item.versionId}" data-resource="${item.resourceId}" data-title="${escapeHtml(item.title)}">发布</button>` : ''}
          ${item.releaseId ? `<button class="g3-btn quiet" type="button" data-action="revoke" data-release="${item.releaseId}" data-title="${escapeHtml(item.title)}">撤回</button>` : ''}
          ${item.resourceKind === 'FILE' ? `<button class="g3-btn quiet" type="button" data-action="new-version" data-resource="${item.resourceId}" data-title="${escapeHtml(item.title)}">新版本</button>` : ''}
        </div></td>
      </tr>`;
    }).join('');
    return `<section class="g3-panel">
      <div class="g3-panel-head"><div class="g3-panel-title">课程资料 <span class="count">${resources.length} 项</span></div></div>
      <div class="g3-table-wrap"><table class="g3-table">
        <thead><tr><th>资料</th><th>版本</th><th>处理状态</th><th>教学班发布</th><th>操作</th></tr></thead>
        <tbody>${rows}</tbody>
      </table></div></section>`;
  }

  function renderQuestionTable(questions) {
    if (questions.length === 0) {
      return emptyState('题库为空', '创建单选、多选、判断或简答题，并保留答案版本');
    }
    const rows = questions.map(question => {
      const answer = formatAnswer(question.answer, question.questionKind);
      return `<tr>
        <td><div class="g3-cell-title">${escapeHtml(question.title)}</div>
          <div class="g3-cell-meta">${escapeHtml(question.stem)}</div></td>
        <td>${escapeHtml(labels[question.questionKind] || question.questionKind)}</td>
        <td>V${escapeHtml(question.versionNo)}<div class="g3-cell-meta">${escapeHtml(question.maxScore)} 分</div></td>
        <td><div class="g3-cell-meta">${escapeHtml(answer)}</div></td>
        <td>${statusBadge(question.status)}</td>
        <td><div class="g3-actions">
          <button class="g3-btn quiet" type="button" data-action="question-version" data-question="${question.questionId}"
            data-kind="${question.questionKind}" data-title="${escapeHtml(question.title)}">新版本</button>
          ${question.status !== 'ARCHIVED' ? `<button class="g3-btn quiet" type="button" data-action="archive-question" data-question="${question.questionId}">归档</button>` : ''}
        </div></td>
      </tr>`;
    }).join('');
    return `<section class="g3-panel">
      <div class="g3-panel-head"><div class="g3-panel-title">课程题库 <span class="count">${questions.length} 题</span></div></div>
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
    const unitRows = units.length ? units.map(unit =>
      `<div class="g3-list-row"><div class="g3-list-main"><strong>${escapeHtml(unit.title)}</strong>
        <span>${escapeHtml(unit.unitKind === 'CHAPTER' ? '章' : '节')} · 排序 ${escapeHtml(unit.sortOrder)}</span></div>
        ${statusBadge('ACTIVE')}</div>`).join('') : emptyState('暂无章节');
    const pointRows = points.length ? points.map(point =>
      `<div class="g3-list-row"><div class="g3-list-main"><strong>${escapeHtml(point.name)}</strong>
        <span>${escapeHtml(point.code || '未设置编码')}</span></div></div>`).join('') : emptyState('暂无知识点');
    return `<div class="g3-grid two">
      <section class="g3-panel"><div class="g3-panel-head"><div class="g3-panel-title">章节结构</div></div>
        <div class="g3-panel-body g3-list">${unitRows}</div></section>
      <section class="g3-panel"><div class="g3-panel-head"><div class="g3-panel-title">知识点</div></div>
        <div class="g3-panel-body g3-list">${pointRows}</div></section>
    </div>`;
  }

  function bindTeacherStudioActions(overview, context) {
    root.querySelector('[data-action="upload-resource"]')?.addEventListener('click', () => showUploadModal(overview, context));
    root.querySelector('[data-action="create-question"]')?.addEventListener('click', () => showQuestionModal(overview, context));
    root.querySelectorAll('[data-action="preview"]').forEach(button => {
      button.addEventListener('click', () => showPreview(button.dataset.version, button.dataset.binding));
    });
    root.querySelectorAll('[data-action="versions"]').forEach(button => {
      button.addEventListener('click', () => showVersions(button.dataset.resource, context));
    });
    root.querySelectorAll('[data-action="release"]').forEach(button => {
      button.addEventListener('click', () => showReleaseModal(button.dataset.version, button.dataset.title, context));
    });
    root.querySelectorAll('[data-action="revoke"]').forEach(button => {
      button.addEventListener('click', () => revokeRelease(button.dataset.release, button.dataset.title, context));
    });
    root.querySelectorAll('[data-action="new-version"]').forEach(button => {
      button.addEventListener('click', () => showNewVersionModal(button.dataset.resource, button.dataset.title, context));
    });
    root.querySelectorAll('[data-action="question-version"]').forEach(button => {
      button.addEventListener('click', () => showQuestionModal(overview, context, {
        questionId: button.dataset.question,
        title: button.dataset.title,
        kind: button.dataset.kind
      }));
    });
    root.querySelectorAll('[data-action="archive-question"]').forEach(button => {
      button.addEventListener('click', () => archiveQuestion(button.dataset.question, context));
    });
  }

  function knowledgeMarkup(overview) {
    if (!overview.knowledgePoints?.length) return '<div class="g3-help">当前课程尚未维护知识点</div>';
    return overview.knowledgePoints.map(point =>
      `<label class="g3-check"><input type="checkbox" name="knowledgePoint" value="${escapeHtml(point.knowledgePointId)}">
        <span>${escapeHtml(point.name)}</span></label>`).join('');
  }

  function showUploadModal(overview, context) {
    const units = (overview.outline || []).map(unit =>
      `<option value="${escapeHtml(unit.unitId)}">${escapeHtml(unit.title)}</option>`).join('');
    const modal = openModal({
      title: '上传课程资料',
      body: `<form id="g3UploadForm" class="g3-form-grid">
        <div class="g3-field full"><label for="g3UploadTitle">资料名称</label>
          <input id="g3UploadTitle" class="g3-input" name="title" maxlength="200" placeholder="不填写时使用文件名"></div>
        <div class="g3-field"><label for="g3UploadUnit">归档章节</label>
          <select id="g3UploadUnit" class="g3-select" name="unitId"><option value="">不归档章节</option>${units}</select></div>
        <div class="g3-field"><label for="g3UploadFile">文件</label>
          <input id="g3UploadFile" class="g3-input" name="file" type="file" required
            accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md"></div>
        <div class="g3-field full"><div class="g3-inline-note">PPTX 和 PDF 会转换为受控逐页预览；视频与文档保留原文件受控下载。</div></div>
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3UploadForm">上传并处理</button>`
    });
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
        closeModal();
        clearOverview();
        toast(result.processingStatus === 'READY' ? '资料已上传并完成处理' : result.processingMessage);
        renderTeacherStudio();
      } catch (error) {
        toast(error.message, true);
        setBusy(button, false);
      }
    });
  }

  function showNewVersionModal(resourceId, title, context) {
    const modal = openModal({
      title: `上传新版本 · ${title}`,
      body: `<form id="g3VersionForm" class="g3-form-grid">
        <div class="g3-field full"><label for="g3VersionFile">新文件</label>
          <input id="g3VersionFile" class="g3-input" type="file" name="file" required
            accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md"></div>
        <div class="g3-field full"><div class="g3-inline-note warn">新版本不会改写已发布版本；需要重新选择该版本发布。</div></div>
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
        toast(result.processingStatus === 'READY' ? '新版本已保存' : result.processingMessage);
        renderTeacherStudio();
      } catch (error) {
        toast(error.message, true);
        setBusy(button, false);
      }
    });
  }

  function showReleaseModal(versionId, title, context) {
    const modal = openModal({
      title: `发布到 ${context.className}`,
      body: `<form id="g3ReleaseForm" class="g3-form-grid">
        <div class="g3-field full"><label>${escapeHtml(title)}</label>
          <div class="g3-help">学生只会在发布开放时间内看到该版本；撤回后旧访问地址立即失效。</div></div>
        <div class="g3-field"><label for="g3ReleaseOpen">开放时间</label>
          <input id="g3ReleaseOpen" class="g3-input" type="datetime-local" name="opensAt" value="${localInput()}"></div>
        <div class="g3-field"><label for="g3ReleaseClose">截止时间</label>
          <input id="g3ReleaseClose" class="g3-input" type="datetime-local" name="closesAt" value="${futureInput(30)}"></div>
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3ReleaseForm">确认发布</button>`
    });
    modal.querySelector('#g3ReleaseForm').addEventListener('submit', async event => {
      event.preventDefault();
      const button = modal.querySelector('.g3-modal-foot .primary');
      const form = event.currentTarget;
      const opensAt = isoFromInput(form.opensAt.value);
      const closesAt = isoFromInput(form.closesAt.value);
      setBusy(button, true, '发布中…');
      try {
        await api('/education/group3/releases', {
          method: 'POST',
          body: { bindingId: state.bindingId, resourceVersionId: versionId, opensAt, closesAt }
        });
        closeModal();
        clearOverview();
        toast('发布范围已更新');
        renderTeacherStudio();
      } catch (error) {
        toast(error.message, true);
        setBusy(button, false);
      }
    });
  }

  async function revokeRelease(releaseId, title, context) {
    if (!window.confirm(`确认撤回“${title}”在 ${context.className} 的发布？`)) return;
    try {
      await api(`/education/group3/releases/${releaseId}/revoke?bindingId=${encodeURIComponent(state.bindingId)}`, {
        method: 'POST',
        body: { reason: '教师从课程建设页撤回' }
      });
      clearOverview();
      toast('资料已撤回');
      renderTeacherStudio();
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function showVersions(resourceId, context) {
    try {
      const versions = await api(`/education/group3/resources/${resourceId}/versions?bindingId=${encodeURIComponent(state.bindingId)}`);
      const rows = versions.length ? versions.map(version => `<tr>
        <td>V${escapeHtml(version.versionNo)}</td><td>${statusBadge(version.processingStatus)}</td>
        <td>${escapeHtml(version.pageCount || 0)} 页</td><td>${formatTime(version.createdAt)}</td>
        <td><button class="g3-btn quiet" type="button" data-preview-version="${version.versionId}">预览</button></td>
      </tr>`).join('') : `<tr><td colspan="5">${emptyState('暂无版本')}</td></tr>`;
      const modal = openModal({
        title: '资料版本记录',
        wide: true,
        body: `<div class="g3-table-wrap"><table class="g3-table">
          <thead><tr><th>版本</th><th>处理状态</th><th>预览页</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody>${rows}</tbody></table></div>`
      });
      modal.querySelectorAll('[data-preview-version]').forEach(button => {
        button.addEventListener('click', () => showPreview(button.dataset.previewVersion, state.bindingId));
      });
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function showPreview(versionId, bindingId) {
    if (!versionId) return;
    const binding = bindingId || state.bindingId;
    let modal;
    try {
      modal = openModal({
        title: '资料预览',
        wide: true,
        body: loadingState('正在加载预览')
      });
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
      if (modal) modal.querySelector('.g3-modal-body').innerHTML = errorState(error);
      else toast(error.message, true);
    }
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
      toast(error.message, true);
    }
  }

  function showQuestionModal(overview, context, edit = null) {
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
        form.answer.value = 'true';
        form.answer.placeholder = '填写 true 或 false';
        answerHelp.textContent = '判断题填写 true 或 false。';
      } else if (value === 'SHORT_TEXT') {
        form.answer.value = '';
        form.answer.placeholder = '填写参考答案';
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
      if (selectedKind === 'MULTIPLE') {
        answer = { choiceIndices: form.answer.value.split(',').map(value => Number(value.trim()) - 1).filter(Number.isInteger) };
      }
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
        if (edit) {
          await api(`/education/group3/questions/${edit.questionId}/versions`, { method: 'POST', body });
        } else {
          await api('/education/group3/questions', { method: 'POST', body });
        }
        closeModal();
        clearOverview();
        toast(edit ? '题目新版本已保存' : '题目已创建');
        renderTeacherStudio();
      } catch (error) {
        toast(error.message, true);
        setBusy(button, false);
      }
    });
  }

  async function archiveQuestion(questionId, context) {
    if (!window.confirm('确认归档该题目？')) return;
    try {
      await api(`/education/group3/questions/${questionId}/status?bindingId=${encodeURIComponent(state.bindingId)}`, {
        method: 'PATCH',
        body: { status: 'ARCHIVED' }
      });
      clearOverview();
      toast('题目已归档');
      renderTeacherStudio();
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function renderTeacherPrepare() {
    clearObjectUrls();
    root.innerHTML = loadingState('正在加载备课与预习');
    try {
      const context = await ensureContexts();
      if (!context) {
        root.innerHTML = emptyState('暂无获授权的课程', '请联系教务管理员确认教学班任课关系');
        return;
      }
      const overview = await loadOverview(context);
      const [plans, tasks] = await Promise.all([
        api(`/education/group3/plans?bindingId=${encodeURIComponent(state.bindingId)}`),
        api(`/education/group3/preview-tasks?bindingId=${encodeURIComponent(state.bindingId)}`)
      ]);
      const tab = state.prepareTab;
      root.innerHTML = `<div class="g3-workspace">
        ${pageHead('课前准备', `${context.courseTitle} · ${context.className}`, context)}
        <div class="g3-toolbar">
          <div class="g3-segment" data-prepare-tabs>
            <button type="button" data-tab="plans" class="${tab === 'plans' ? 'active' : ''}">备课方案</button>
            <button type="button" data-tab="previews" class="${tab === 'previews' ? 'active' : ''}">预习发布</button>
          </div>
          <div class="g3-actions">
            ${tab === 'plans'
              ? `<button class="g3-btn primary" type="button" data-action="create-plan" ${overview.lessons?.length ? '' : 'disabled'}>新建备课方案</button>`
              : `<button class="g3-btn primary" type="button" data-action="publish-preview">发布预习</button>`}
          </div>
        </div>
        ${tab === 'plans' ? renderPlans(plans) : renderTeacherPreviewTasks(tasks)}
      </div>`;
      bindContext(root, renderTeacherPrepare);
      root.querySelectorAll('[data-prepare-tabs] button').forEach(button => {
        button.addEventListener('click', () => {
          state.prepareTab = button.dataset.tab;
          renderTeacherPrepare();
        });
      });
      root.querySelector('[data-action="create-plan"]')?.addEventListener('click', () => showPlanModal(overview, context));
      root.querySelector('[data-action="publish-preview"]')?.addEventListener('click', () => showPublishPreviewModal(overview, context));
    } catch (error) {
      root.innerHTML = errorState(error);
      bindRetry(root, renderTeacherPrepare);
    }
  }

  function renderPlans(plans) {
    if (!plans.length) return emptyState('还没有备课方案', '选择课次、课件与活动模板形成私有备课包');
    return `<div class="g3-grid two">${plans.map(plan => `<section class="g3-panel">
      <div class="g3-panel-head"><div class="g3-panel-title">${escapeHtml(plan.title)}
        <span class="count">V${escapeHtml(plan.versionNo)}</span></div>${statusBadge(plan.status)}</div>
      <div class="g3-panel-body g3-list">
        <div class="g3-list-main"><strong>${escapeHtml(plan.lessonTitle)}</strong>
          <span>${escapeHtml(plan.privateNotes || '无教师私有备注')}</span></div>
        <div class="g3-divider"></div>
        <div><div class="g3-help">备课材料</div>${(plan.materials || []).length
          ? (plan.materials || []).map(material => `<div class="g3-list-row"><div class="g3-list-main">
              <strong>${escapeHtml(material.title)}</strong><span>V${escapeHtml(material.versionNo)} · ${Number(material.pageCount || 0)} 页</span>
              </div></div>`).join('')
          : '<div class="g3-help">未选择材料</div>'}</div>
        <div><div class="g3-help">活动预设</div>${(plan.templates || []).length
          ? (plan.templates || []).map(template => `<div class="g3-list-row"><div class="g3-list-main">
              <strong>${escapeHtml(template.title)}</strong><span>${escapeHtml(labels[template.templateKind] || template.templateKind)} · ${escapeHtml(template.durationSeconds || 0)} 秒</span>
              </div>${statusBadge(template.status)}</div>`).join('')
          : '<div class="g3-help">未设置活动预设</div>'}</div>
      </div></section>`).join('')}</div>`;
  }

  function renderTeacherPreviewTasks(tasks) {
    if (!tasks.length) return emptyState('还没有发布预习', '预习任务仅对当前教学班学生生效');
    return `<section class="g3-panel">
      <div class="g3-panel-head"><div class="g3-panel-title">预习任务 <span class="count">${tasks.length} 项</span></div></div>
      <div class="g3-table-wrap"><table class="g3-table">
        <thead><tr><th>任务</th><th>开放时间</th><th>状态</th><th>完成</th><th>解析公布</th></tr></thead>
        <tbody>${tasks.map(task => `<tr>
          <td><div class="g3-cell-title">${escapeHtml(task.title)}</div>
            <div class="g3-cell-meta">${escapeHtml(task.stem || task.instructions || '资料预习')}</div></td>
          <td>${formatTime(task.opensAt)}<div class="g3-cell-meta">至 ${formatTime(task.closesAt)}</div></td>
          <td>${statusBadge(task.status)}</td>
          <td>${escapeHtml(task.submissionCount || 0)} / ${escapeHtml(task.targetCount || 0)}</td>
          <td>${formatTime(task.answerOpensAt)}</td>
        </tr>`).join('')}</tbody></table></div></section>`;
  }

  function materialCheckboxes(overview) {
    const ready = overview.materialVersions?.length
      ? overview.materialVersions
      : (overview.resources || []).filter(item => item.processingStatus === 'READY' && item.versionId);
    if (!ready.length) return '<div class="g3-help">暂无可用资料</div>';
    return ready.map(item => `<label class="g3-check"><input type="checkbox" name="materialVersion" value="${item.versionId}">
      <span>${escapeHtml(item.title)} · V${escapeHtml(item.versionNo)}</span></label>`).join('');
  }

  function questionOptions(overview) {
    const questions = overview.questionVersions?.length
      ? overview.questionVersions
      : (overview.questions || []).filter(question => question.status !== 'ARCHIVED');
    if (!questions.length) return '<option value="">不使用题目</option>';
    return `<option value="">不使用题目</option>${questions.map(question =>
      `<option value="${question.versionId}">${escapeHtml(question.title)} · V${question.versionNo}</option>`).join('')}`;
  }

  function showPlanModal(overview, context) {
    const lessons = (overview.lessons || []).map(lesson =>
      `<option value="${lesson.lessonId}">${escapeHtml(lesson.title)}${lesson.scheduledAt ? ` · ${formatTime(lesson.scheduledAt)}` : ''}</option>`).join('');
    const modal = openModal({
      title: '新建备课方案',
      wide: true,
      body: `<form id="g3PlanForm" class="g3-form-grid">
        <div class="g3-field"><label for="g3PlanLesson">课次</label>
          <select id="g3PlanLesson" class="g3-select" name="lessonId" required>${lessons}</select></div>
        <div class="g3-field"><label for="g3PlanTitle">方案名称</label>
          <input id="g3PlanTitle" class="g3-input" name="title" maxlength="160" required></div>
        <div class="g3-field full"><label for="g3PlanNotes">教师私有备注</label>
          <textarea id="g3PlanNotes" class="g3-textarea" name="privateNotes"></textarea></div>
        <div class="g3-field"><label>课堂材料</label><div class="g3-check-list">${materialCheckboxes(overview)}</div></div>
        <div class="g3-field"><label for="g3PlanQuestion">随堂题预设</label>
          <select id="g3PlanQuestion" class="g3-select" name="questionVersionId">${questionOptions(overview)}</select>
          <label for="g3PlanQuizTitle">活动名称</label><input id="g3PlanQuizTitle" class="g3-input" name="quizTitle" value="课前测验">
          <label for="g3PlanDuration">答题时长（秒）</label><input id="g3PlanDuration" class="g3-input" name="durationSeconds" type="number" min="60" value="900"></div>
        <div class="g3-field full"><label class="g3-check"><input type="checkbox" name="markReady" checked><span>保存为可用方案</span></label></div>
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3PlanForm">保存方案</button>`
    });
    modal.querySelector('#g3PlanForm').addEventListener('submit', async event => {
      event.preventDefault();
      const button = modal.querySelector('.g3-modal-foot .primary');
      const form = event.currentTarget;
      const materialVersionIds = [...form.querySelectorAll('input[name="materialVersion"]:checked')]
        .map(input => Number(input.value));
      const body = {
        bindingId: Number(state.bindingId),
        lessonId: Number(form.lessonId.value),
        title: form.title.value.trim(),
        privateNotes: form.privateNotes.value.trim(),
        materialVersionIds,
        quizQuestionVersionId: form.questionVersionId.value ? Number(form.questionVersionId.value) : null,
        quizTitle: form.quizTitle.value.trim(),
        durationSeconds: Number(form.durationSeconds.value),
        markReady: form.markReady.checked
      };
      setBusy(button, true, '保存中…');
      try {
        await api('/education/group3/plans', { method: 'POST', body });
        closeModal();
        toast('备课方案已保存');
        renderTeacherPrepare();
      } catch (error) {
        toast(error.message, true);
        setBusy(button, false);
      }
    });
  }

  function showPublishPreviewModal(overview, context) {
    const lessons = (overview.lessons || []).map(lesson =>
      `<option value="${lesson.lessonId}">${escapeHtml(lesson.title)}</option>`).join('');
    const modal = openModal({
      title: `发布预习 · ${context.className}`,
      wide: true,
      body: `<form id="g3PreviewForm" class="g3-form-grid">
        <div class="g3-field"><label for="g3PreviewLesson">关联课次</label>
          <select id="g3PreviewLesson" class="g3-select" name="lessonId"><option value="">不关联课次</option>${lessons}</select></div>
        <div class="g3-field"><label for="g3PreviewTitle">任务名称</label>
          <input id="g3PreviewTitle" class="g3-input" name="title" maxlength="200" required></div>
        <div class="g3-field full"><label for="g3PreviewInstructions">导学说明</label>
          <textarea id="g3PreviewInstructions" class="g3-textarea" name="instructions"></textarea></div>
        <div class="g3-field"><label for="g3PreviewOpen">开放时间</label>
          <input id="g3PreviewOpen" class="g3-input" type="datetime-local" name="opensAt" value="${localInput()}"></div>
        <div class="g3-field"><label for="g3PreviewClose">截止时间</label>
          <input id="g3PreviewClose" class="g3-input" type="datetime-local" name="closesAt" value="${futureInput(3)}"></div>
        <div class="g3-field"><label for="g3PreviewAnswerOpen">解析公布时间</label>
          <input id="g3PreviewAnswerOpen" class="g3-input" type="datetime-local" name="answerOpensAt" value="${futureInput(4)}"></div>
        <div class="g3-field"><label for="g3PreviewQuestion">预习题目</label>
          <select id="g3PreviewQuestion" class="g3-select" name="questionVersionId">${questionOptions(overview)}</select></div>
        <div class="g3-field full"><label>同步开放资料</label><div class="g3-check-list">${materialCheckboxes(overview)}</div></div>
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3PreviewForm">发布</button>`
    });
    modal.querySelector('#g3PreviewForm').addEventListener('submit', async event => {
      event.preventDefault();
      const button = modal.querySelector('.g3-modal-foot .primary');
      const form = event.currentTarget;
      const body = {
        bindingId: Number(state.bindingId),
        lessonId: form.lessonId.value ? Number(form.lessonId.value) : null,
        title: form.title.value.trim(),
        instructions: form.instructions.value.trim(),
        opensAt: isoFromInput(form.opensAt.value),
        closesAt: isoFromInput(form.closesAt.value),
        answerOpensAt: isoFromInput(form.answerOpensAt.value),
        questionVersionId: form.questionVersionId.value ? Number(form.questionVersionId.value) : null,
        resourceVersionIds: [...form.querySelectorAll('input[name="materialVersion"]:checked')]
          .map(input => Number(input.value))
      };
      setBusy(button, true, '发布中…');
      try {
        await api('/education/group3/preview-tasks', { method: 'POST', body });
        closeModal();
        toast('预习任务已发布');
        clearOverview();
        renderTeacherPrepare();
      } catch (error) {
        toast(error.message, true);
        setBusy(button, false);
      }
    });
  }

  async function renderStudentMaterials() {
    clearObjectUrls();
    root.innerHTML = loadingState('正在加载课程资料');
    try {
      const context = await ensureContexts();
      if (!context) {
        root.innerHTML = emptyState('暂无在修课程', '请联系任课教师确认教学班成员关系');
        return;
      }
      const materials = await api(`/education/group3/student/materials?bindingId=${encodeURIComponent(state.bindingId)}`);
      root.innerHTML = `<div class="g3-workspace">
        ${pageHead('课程资料', `${context.courseTitle} · ${context.className}`, context)}
        <div class="g3-grid three">
          <div class="g3-stat"><small>当前开放资料</small><strong>${materials.length}</strong></div>
          <div class="g3-stat"><small>可分页预览</small><strong>${materials.filter(item => Number(item.pageCount) > 0).length}</strong></div>
          <div class="g3-stat"><small>教学班</small><strong style="font-size:18px">${escapeHtml(context.className)}</strong></div>
        </div>
        ${materials.length ? `<div class="g3-material-grid">${materials.map(material => `
          <article class="g3-material-card">
            <div class="g3-material-icon">${escapeHtml((material.mimeType || '').includes('pdf') ? 'PDF' : (material.mimeType || '').includes('presentation') ? 'PPT' : material.mediaKind === 'VIDEO' ? 'VID' : 'DOC')}</div>
            <div><div class="g3-material-title">${escapeHtml(material.title)}</div>
              <div class="g3-material-meta">V${escapeHtml(material.versionNo)} · ${Number(material.pageCount || 0)} 页<br>
                开放至 ${formatTime(material.closesAt)}</div></div>
            <div class="g3-material-actions">
              <button class="g3-btn primary" type="button" data-student-preview="${material.versionId}">查看资料</button>
              <button class="g3-btn" type="button" data-student-download="${material.versionId}">下载</button>
            </div>
          </article>`).join('')}</div>` : emptyState('当前没有开放资料', '教师发布资料后会在此显示')}
      </div>`;
      bindContext(root, renderStudentMaterials);
      root.querySelectorAll('[data-student-preview]').forEach(button => {
        button.addEventListener('click', () => showPreview(button.dataset.studentPreview, state.bindingId));
      });
      root.querySelectorAll('[data-student-download]').forEach(button => {
        button.addEventListener('click', () => downloadVersion(button.dataset.studentDownload, state.bindingId));
      });
    } catch (error) {
      root.innerHTML = errorState(error);
      bindRetry(root, renderStudentMaterials);
    }
  }

  async function renderStudentPrestudy() {
    clearObjectUrls();
    root.innerHTML = loadingState('正在加载预习任务');
    try {
      const context = await ensureContexts();
      if (!context) {
        root.innerHTML = emptyState('暂无在修课程', '请联系任课教师确认教学班成员关系');
        return;
      }
      const tasks = await api(`/education/group3/student/previews?bindingId=${encodeURIComponent(state.bindingId)}`);
      if (!tasks.length) {
        root.innerHTML = `<div class="g3-workspace">
          ${pageHead('课前学习', `${context.courseTitle} · ${context.className}`, context)}
          ${emptyState('当前没有预习任务', '教师发布后会显示开放时间和完成状态')}</div>`;
        bindContext(root, renderStudentPrestudy);
        return;
      }
      if (!tasks.some(task => String(task.assignmentId) === String(state.selectedPreviewId))) {
        state.selectedPreviewId = String((tasks.find(task => task.state === 'OPEN') || tasks[0]).assignmentId);
      }
      root.innerHTML = `<div class="g3-workspace">
        ${pageHead('课前学习', `${context.courseTitle} · ${context.className}`, context)}
        <div class="g3-split">
          <section class="g3-panel"><div class="g3-panel-head"><div class="g3-panel-title">我的预习
            <span class="count">${tasks.length} 项</span></div></div>
            <div class="g3-panel-body g3-list">${tasks.map(task => `
              <button type="button" class="g3-task-card${String(task.assignmentId) === String(state.selectedPreviewId) ? ' active' : ''}"
                data-task-select="${task.assignmentId}" style="text-align:left;cursor:pointer">
                <div class="g3-toolbar"><strong>${escapeHtml(task.title)}</strong>${statusBadge(task.state)}</div>
                <div class="g3-help">${formatTime(task.opensAt)} 至 ${formatTime(task.closesAt)}</div>
                <div class="g3-actions"><span class="g3-badge ${task.submitted ? 'ready' : ''}">${task.submitted ? '已提交' : '待完成'}</span>
                  ${task.answerVisible ? '<span class="g3-badge ready">解析已公布</span>' : ''}</div>
              </button>`).join('')}</div></section>
          <section class="g3-panel" id="g3PreviewDetail">${loadingState('正在加载任务')}</section>
        </div>
      </div>`;
      bindContext(root, renderStudentPrestudy);
      root.querySelectorAll('[data-task-select]').forEach(button => {
        button.addEventListener('click', () => {
          state.selectedPreviewId = button.dataset.taskSelect;
          root.querySelectorAll('[data-task-select]').forEach(item => item.classList.toggle('active', item === button));
          loadStudentPreviewDetail();
        });
      });
      loadStudentPreviewDetail();
    } catch (error) {
      root.innerHTML = errorState(error);
      bindRetry(root, renderStudentPrestudy);
    }
  }

  async function loadStudentPreviewDetail() {
    const container = document.getElementById('g3PreviewDetail');
    if (!container || !state.selectedPreviewId) return;
    container.innerHTML = loadingState('正在加载任务');
    try {
      const detail = await api(`/education/group3/student/previews/${state.selectedPreviewId}?bindingId=${encodeURIComponent(state.bindingId)}`);
      container.innerHTML = `<div class="g3-panel-head"><div class="g3-panel-title">${escapeHtml(detail.title)}
        ${statusBadge(detail.state)}</div><span class="g3-help">${formatTime(detail.opensAt)} 至 ${formatTime(detail.closesAt)}</span></div>
        <div class="g3-panel-body g3-list">
          ${detail.instructions ? `<div class="g3-inline-note">${escapeHtml(detail.instructions)}</div>` : ''}
          ${detail.state === 'UPCOMING' ? emptyState('任务尚未开放', `开放时间：${formatTime(detail.opensAt)}`) : ''}
          ${detail.state !== 'UPCOMING' && (detail.items || []).length === 0 ? emptyState('本任务没有题目', '请查看已开放课程资料') : ''}
          <form id="g3StudentAnswerForm" class="g3-list">
            ${(detail.items || []).map(item => renderStudentQuestion(item, detail)).join('')}
            ${detail.state === 'OPEN' && !detail.submitted && (detail.items || []).length
              ? `<div class="g3-actions"><button class="g3-btn primary" type="submit" data-submit-preview>提交预习</button></div>` : ''}
          </form>
          ${detail.submitted ? `<div class="g3-feedback">已于 ${formatTime(detail.submission?.submittedAt)} 提交。
            ${detail.answerVisible ? '参考答案与解析已公布。' : '参考答案将在教师设置的公布时间显示。'}</div>` : ''}
          ${(detail.resources || []).length ? `<div><div class="g3-help">本课程当前开放资料</div>
            <div class="g3-actions g3-resource-links" style="margin-top:8px">${detail.resources.map(resource =>
              `<button class="g3-btn" type="button" data-detail-preview="${resource.versionId}">${escapeHtml(resource.title)} · V${resource.versionNumber || resource.versionNo}</button>`).join('')}</div></div>` : ''}
          ${detail.answerVisible && detail.answerVisibleAt ? `<div class="g3-help">解析公布时间：${formatTime(detail.answerVisibleAt)}</div>` : ''}
        </div>`;
      const form = container.querySelector('#g3StudentAnswerForm');
      form?.addEventListener('submit', event => submitStudentPreview(event, detail, form));
      container.querySelectorAll('[data-detail-preview]').forEach(button => {
        button.addEventListener('click', () => showPreview(button.dataset.detailPreview, state.bindingId));
      });
    } catch (error) {
      container.innerHTML = errorState(error);
      container.querySelector('[data-action="retry"]')?.addEventListener('click', loadStudentPreviewDetail);
    }
  }

  function renderStudentQuestion(item, detail) {
    const options = Array.isArray(item.options) ? item.options : [];
    const answered = Boolean(detail.submitted);
    let controls = '';
    if (item.questionKind === 'SINGLE' || item.questionKind === 'TRUE_FALSE') {
      const values = item.questionKind === 'TRUE_FALSE' ? ['正确', '错误'] : options;
      controls = `<div class="g3-options">${values.map((option, index) => `<label class="g3-option">
        <input type="radio" name="question-${item.assignmentItemId}" value="${index}" data-kind="${item.questionKind}">
        <span>${escapeHtml(option)}</span></label>`).join('')}</div>`;
    } else if (item.questionKind === 'MULTIPLE') {
      controls = `<div class="g3-options">${options.map((option, index) => `<label class="g3-option">
        <input type="checkbox" name="question-${item.assignmentItemId}" value="${index}" data-kind="MULTIPLE">
        <span>${escapeHtml(option)}</span></label>`).join('')}</div>`;
    } else {
      controls = `<textarea class="g3-textarea" name="question-${item.assignmentItemId}"
        placeholder="填写你的回答"></textarea>`;
    }
    const feedback = item.answerVisible && item.answer
      ? `<div class="g3-feedback"><strong>参考答案：</strong>${escapeHtml(formatAnswer(item.answer, item.questionKind))}
          ${item.explanation ? `<br><strong>解析：</strong>${escapeHtml(item.explanation)}` : ''}</div>` : '';
    return `<div class="g3-question">
      <div class="g3-question-stem">${escapeHtml(item.prompt || item.stem)}</div>
      ${answered ? '' : controls}
      ${feedback}
    </div>`;
  }

  async function submitStudentPreview(event, detail, form) {
    event.preventDefault();
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
        method: 'POST',
        body: { answers }
      });
      toast('预习已提交');
      await loadStudentPreviewDetail();
    } catch (error) {
      toast(error.message, true);
      setBusy(button, false);
    }
  }

  function bindRetry(container, callback) {
    container.querySelector('[data-action="retry"]')?.addEventListener('click', callback);
  }

  function renderUnsupported() {
    clearObjectUrls();
    root.innerHTML = emptyState('该模块尚未接入', '当前可用入口：教师“课程建设”“课前准备”，学生“课程资料”“课前学习”');
  }

  function routeFromLocation() {
    const query = new URLSearchParams(location.search);
    const gi = Number(query.get('g') || 0);
    const ii = Number(query.get('i') || 0);
    if (role === 'teacher') {
      if (gi === routes.studio.g && ii === routes.studio.i) return renderTeacherStudio();
      if (gi === routes.prepare.g && ii === routes.prepare.i) return renderTeacherPrepare();
    } else {
      if (gi === routes.materials.g && ii === routes.materials.i) return renderStudentMaterials();
      if (gi === routes.prestudy.g && ii === routes.prestudy.i) return renderStudentPrestudy();
    }
    return renderUnsupported();
  }

  window.Group3App = {
    render(gi, ii) {
      if (role === 'teacher' && gi === routes.studio.g && ii === routes.studio.i) return renderTeacherStudio();
      if (role === 'teacher' && gi === routes.prepare.g && ii === routes.prepare.i) return renderTeacherPrepare();
      if (role === 'student' && gi === routes.materials.g && ii === routes.materials.i) return renderStudentMaterials();
      if (role === 'student' && gi === routes.prestudy.g && ii === routes.prestudy.i) return renderStudentPrestudy();
      renderUnsupported();
    }
  };

  window.addEventListener('beforeunload', clearObjectUrls);
  routeFromLocation();
})();
