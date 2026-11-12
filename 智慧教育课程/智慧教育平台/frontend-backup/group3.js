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
    prepareView: 'timeline',
    prepareFilter: 'all',
    selectedKey: '',
    overview: null,
    overviewBinding: '',
    nodes: [],
    nodeIndex: {},
    teacherTasks: [],
    plans: [],
    selectedPreviewId: null,
    currentFolderId: null,
    currentQuestionFolderId: null,
    modalUrls: [],
    stageKey: '',
    stageAnswers: {},
    stageUrls: [],
    stageOrder: [],
    stageGroups: [],
    route: '',
    studentFilter: 'all',
    materials: [],
    studentTasks: [],
    createFlow: '',
    createDraft: {}
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

  const resourceSources = [
    { label: '本地上传', action: 'upload-resource' },
    { label: '云盘', action: 'cloud-drive' },
    { label: '添加网址', action: 'source-coming' },
    { label: '在线图书', action: 'source-coming' },
    { label: '笔记', action: 'source-coming' },
    { label: '教学资源库', action: 'open-library' },
    { label: '跨课导入', action: 'cross-course' },
    { label: '课程思政', action: 'source-coming' }
  ];

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatFileSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB';
    return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB';
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

  function pageHead(title, subtitle, context, note) {
    const contextOptions = state.contexts.map(item =>
      `<option value="${escapeHtml(item.bindingId)}"${optionMarkup(item.bindingId, context.bindingId)}>`
      + `${escapeHtml(item.courseTitle)} · ${escapeHtml(item.className)}</option>`).join('');
    return `<div class="g3-page-head">
      <div class="g3-title-block">
        <h2>${escapeHtml(title)}</h2>
        <p>${escapeHtml(subtitle)}</p>
        ${note ? `<span class="g3-head-note">${escapeHtml(note)}</span>` : ''}
      </div>
      <div class="g3-context">
        <label for="g3Context">课程 / 教学班</label>
        <select id="g3Context" class="g3-select">${contextOptions}</select>
        ${role === 'teacher' ? `<button class="g3-btn quiet" type="button" data-action="classroom-settings">教学班设置</button>` : ''}
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
      state.currentFolderId = null;
      state.currentQuestionFolderId = null;
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
      if (tab === 'resources') {
        const folders = ensureDemoFolder(context, overview.resources || []);
        const activeFolder = folders.find(folder => folder.id === state.currentFolderId) || null;
        if (!activeFolder) state.currentFolderId = null;
        content = renderResourceTable(overview.resources || [], context, folders, activeFolder);
      }
      if (tab === 'questions') content = renderQuestionTable(overview.questions || [], overview.knowledgePoints || [], context);
      if (tab === 'outline') content = renderOutline(overview.outline || [], overview.knowledgePoints || [], context);
      root.innerHTML = `<div class="g3-workspace">
        ${pageHead('课程建设', `${context.courseTitle} · ${context.className}`, context)}
        <div class="g3-toolbar">
          <div class="g3-segment" data-studio-tabs>
            <button type="button" data-tab="resources" class="${tab === 'resources' ? 'active' : ''}">资料</button>
            <button type="button" data-tab="questions" class="${tab === 'questions' ? 'active' : ''}">题库</button>
            <button type="button" data-tab="outline" class="${tab === 'outline' ? 'active' : ''}">章节与知识点</button>
          </div>
        </div>
        ${content}
      </div>`;
      bindContext(root, renderTeacherStudio);
      root.querySelector('[data-action="classroom-settings"]')?.addEventListener('click', () => {
        showClassroomSettings(context);
      });
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

  function resourceIcon(item) {
    const mime = String(item.mimeType || '').toLowerCase();
    if (mime.includes('presentation') || mime.includes('powerpoint')) return 'PPT';
    if (mime.includes('pdf')) return 'PDF';
    if (mime.startsWith('video/')) return 'VID';
    if (mime.startsWith('image/')) return 'IMG';
    if (item.resourceKind === 'LINK') return 'URL';
    return 'DOC';
  }

  function folderStorageKey(context) {
    return `zhijiao.group3.folders.${context.courseSpaceId || context.bindingId}`;
  }

  function readFolders(context) {
    try {
      const stored = JSON.parse(localStorage.getItem(folderStorageKey(context)) || '[]');
      return Array.isArray(stored)
        ? stored.filter(folder => folder && folder.id && folder.name)
        : [];
    } catch (error) {
      return [];
    }
  }

  function writeFolders(context, folders) {
    localStorage.setItem(folderStorageKey(context), JSON.stringify(folders));
  }

  function questionFolderStorageKey(context) {
    return `zhijiao.group3.questionFolders.${context.courseSpaceId || context.bindingId}`;
  }

  function readQuestionFolders(context) {
    try {
      const stored = JSON.parse(localStorage.getItem(questionFolderStorageKey(context)) || '[]');
      return Array.isArray(stored)
        ? stored.filter(folder => folder && folder.id && folder.name)
        : [];
    } catch (error) {
      return [];
    }
  }

  function writeQuestionFolders(context, folders) {
    localStorage.setItem(questionFolderStorageKey(context), JSON.stringify(folders));
  }

  function questionImportMenuMarkup() {
    return `<div class="g3-question-create-menu g3-question-import-menu">
      <button class="g3-btn g3-pill g3-outline-pill" type="button" data-action="toggle-question-import"
        aria-haspopup="menu" aria-expanded="false">批量导入</button>
      <div class="g3-question-create-dropdown" role="menu">
        <button class="g3-dropdown-item" type="button" data-action="import-questions-template"
          data-label="模板导入">模板导入</button>
        <button class="g3-dropdown-item" type="button" data-action="import-questions-smart"
          data-label="智能导入">智能导入</button>
        <button class="g3-dropdown-item" type="button" data-action="import-questions-cross"
          data-label="跨课导入">跨课导入</button>
      </div>
    </div>`;
  }

  function questionCreateMenuMarkup(label, withPlus) {
    return `<div class="g3-question-create-menu">
      <button class="g3-btn primary${withPlus ? ' g3-pill' : ''}" type="button"
        data-action="toggle-question-create" aria-haspopup="menu" aria-expanded="false">
        ${withPlus ? '<span class="g3-plus">+</span>' : ''}${label}</button>
      <div class="g3-question-create-dropdown" role="menu">
        <button class="g3-dropdown-item" type="button" data-action="create-question-manual">手动创建</button>
        <button class="g3-dropdown-item" type="button" data-action="create-question-library">教学资源库</button>
      </div>
    </div>`;
  }

  function ensureDemoFolder(context, resources) {
    const folders = readFolders(context);
    const resourceIds = resources.map(item => String(item.resourceId));
    if (!folders.length) {
      const demo = {
        id: 'demo-folder',
        name: '演示文件夹',
        demo: true,
        visibility: 'PUBLIC',
        resourceIds,
        createdAt: new Date().toISOString()
      };
      writeFolders(context, [demo]);
      return [demo];
    }
    const demo = folders.find(folder => folder.demo);
    if (demo) {
      const current = Array.isArray(demo.resourceIds) ? demo.resourceIds.map(String) : [];
      if (JSON.stringify(current) !== JSON.stringify(resourceIds)) {
        demo.resourceIds = resourceIds;
        writeFolders(context, folders);
      }
    }
    return folders;
  }

  function folderIconMarkup() {
    return `<span class="g3-folder-icon" aria-hidden="true">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        stroke-width="1.8"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z"/></svg>
    </span>`;
  }

  function renderResourceTable(resources, context, folders, activeFolder) {
    const visibleResources = activeFolder
      ? resources.filter(item => activeFolder.demo
        || (activeFolder.resourceIds || []).map(String).includes(String(item.resourceId)))
      : resources;
    const folderRows = activeFolder ? '' : folders.map(folder => {
      const resourceIds = new Set(resources.map(item => String(item.resourceId)));
      const count = folder.demo
        ? resources.length
        : (folder.resourceIds || []).map(String).filter(id => resourceIds.has(id)).length;
      return `<tr class="g3-folder-row" data-folder-row data-folder="${folder.id}"
        data-title="${escapeHtml(folder.name)}">
        <td class="g3-check-col"><input type="checkbox" data-row-check aria-label="选择 ${escapeHtml(folder.name)}"></td>
        <td><div class="g3-file-name">${folderIconMarkup()}
          <div class="g3-file-text">
            <div class="g3-file-title">${escapeHtml(folder.name)}</div>
            <div class="g3-cell-meta">${count} 个资料${folder.demo ? ' · 演示文件夹' : ''}</div>
          </div></div></td>
        <td class="g3-file-dim">-</td><td class="g3-file-dim">-</td>
        <td class="g3-file-dim">-</td><td class="g3-file-dim">-</td>
        <td>${formatTime(folder.createdAt)}</td>
      </tr>`;
    }).join('');
    const rows = visibleResources.map(item => {
      const status = item.processingStatus || 'PENDING';
      const size = Number(item.byteSize);
      const metaParts = [
        item.releaseId ? '已发布' : '未发布',
        `V${escapeHtml(item.versionNo || 1)}`,
        `${Number(item.pageCount || 0)} 页`
      ];
      if (status !== 'READY') metaParts.push(labels[status] || status);
      return `<tr data-resource-row data-title="${escapeHtml(item.title)}">
        <td class="g3-check-col"><div class="g3-check-wrap">
          <span class="g3-drag-handle" aria-hidden="true"></span>
          <input type="checkbox" data-row-check aria-label="选择 ${escapeHtml(item.title)}">
        </div></td>
        <td class="g3-file-cell"><div class="g3-file-row">
          <div class="g3-file-name">
            <span class="g3-file-icon">${resourceIcon(item)}</span>
            <div class="g3-file-text">
              <div class="g3-file-title"><button class="g3-file-title-link" type="button"
                data-action="preview" data-version="${item.versionId}"
                data-binding="${item.bindingId || state.bindingId}">${escapeHtml(item.title)}</button></div>
              <div class="g3-cell-meta">${metaParts.join(' · ')}</div>
            </div>
          </div>
          <div class="g3-row-actions">
            <button class="g3-row-action" type="button" data-action="edit-resource"
              data-resource="${item.resourceId}" data-title="${escapeHtml(item.title)}">修改</button>
            <button class="g3-row-action" type="button" data-action="download-resource"
              data-version="${item.versionId}" data-binding="${item.bindingId || state.bindingId}">下载</button>
            <div class="g3-more-menu">
              <button class="g3-row-action" type="button" data-action="toggle-more"
                aria-haspopup="menu" aria-expanded="false">更多</button>
              <div class="g3-more-dropdown" role="menu">
                <button class="g3-dropdown-item" type="button" data-action="preview" data-version="${item.versionId}" data-binding="${item.bindingId || state.bindingId}">预览</button>
                <button class="g3-dropdown-item" type="button" data-action="versions" data-resource="${item.resourceId}">版本记录</button>
                ${status === 'READY' ? `<button class="g3-dropdown-item" type="button" data-action="release" data-version="${item.versionId}" data-resource="${item.resourceId}" data-title="${escapeHtml(item.title)}">发布</button>` : ''}
                ${item.releaseId ? `<button class="g3-dropdown-item" type="button" data-action="revoke" data-release="${item.releaseId}" data-title="${escapeHtml(item.title)}">撤回</button>` : ''}
                ${item.resourceKind === 'FILE' ? `<button class="g3-dropdown-item" type="button" data-action="new-version" data-resource="${item.resourceId}" data-title="${escapeHtml(item.title)}">新版本</button>` : ''}
                ${item.resourceStatus === 'ARCHIVED'
                  ? `<button class="g3-dropdown-item" type="button" data-action="unarchive-resource" data-resource="${item.resourceId}" data-title="${escapeHtml(item.title)}">取消归档</button>`
                  : `<button class="g3-dropdown-item danger" type="button" data-action="archive-resource" data-resource="${item.resourceId}" data-title="${escapeHtml(item.title)}">归档</button>`}
              </div>
            </div>
          </div>
        </div></td>
        <td class="g3-file-dim">${escapeHtml(item.readCount ?? '-')}</td>
        <td class="g3-file-dim">${escapeHtml(item.downloadCount ?? '-')}</td>
        <td class="g3-file-dim">${size > 0 ? formatFileSize(size) : '-'}</td>
        <td class="g3-file-dim">${escapeHtml(item.creatorName || '-')}</td>
        <td>${formatTime(item.createdAt)}</td>
      </tr>`;
    }).join('');
    const menuItems = resourceSources.map(source => `<button type="button" role="menuitem"
      class="g3-dropdown-item" data-action="${source.action}"${source.action === 'source-coming' ? ` data-label="${source.label}"` : ''}>${source.label}</button>`).join('');
    const listMarkup = (folderRows || rows)
      ? `<div class="g3-table-wrap"><table class="g3-table g3-file-table">
          <thead><tr>
            <th class="g3-check-col"><input type="checkbox" data-action="select-all" aria-label="全选"></th>
            <th>文件名</th><th>阅读人数</th><th>下载次数</th><th>大小</th><th>创建者</th><th>创建日期</th>
          </tr></thead>
          <tbody data-resource-rows>${folderRows}${rows}</tbody>
        </table></div>`
      : `<div class="g3-file-empty">${activeFolder
        ? emptyState('此文件夹暂无资料', '返回“全部文件”可查看课程全部资料')
        : emptyState('课程还没有资料', '点击“添加资料”上传 PPTX、PDF、视频、图片或文档')}</div>`;
    const summaryMarkup = activeFolder
      ? `<div class="g3-file-breadcrumb">
          <div class="g3-breadcrumb-path">
            <button class="g3-breadcrumb-link" type="button" data-action="folder-back">全部文件</button>
            <span class="g3-breadcrumb-sep">›</span>
            <span class="g3-breadcrumb-current">${escapeHtml(activeFolder.name)}</span>
          </div>
          <span data-resource-count>当前页共 ${visibleResources.length} 个</span>
        </div>`
      : `<div class="g3-file-summary"><strong>全部文件</strong>
          <span data-resource-count><span data-selected-count hidden></span>当前页共 ${folders.length + resources.length} 个</span></div>`;
    return `<section class="g3-panel g3-file-panel">
      <div class="g3-file-toolbar">
        <div class="g3-file-toolbar-main">
          <div class="g3-add-menu" data-add-menu>
            <button class="g3-btn primary g3-pill" type="button" data-action="toggle-add-menu"
              aria-haspopup="menu" aria-expanded="false"><span class="g3-plus">+</span>添加资料</button>
            <div class="g3-add-dropdown">
              <div class="g3-add-dropdown-inner" role="menu">${menuItems}</div>
            </div>
          </div>
          <button class="g3-btn g3-pill g3-outline-pill" type="button" data-action="new-folder">新建文件夹</button>
          <div class="g3-bulk-bar" data-bulk-bar hidden>
            <button class="g3-btn g3-pill g3-bulk-btn" type="button" data-action="bulk-move">移动到</button>
            <button class="g3-btn g3-pill g3-bulk-btn" type="button" data-action="bulk-delete">删除</button>
            <button class="g3-btn g3-pill g3-bulk-btn" type="button" data-action="bulk-permission">权限设置</button>
            <button class="g3-btn g3-pill g3-bulk-btn" type="button" data-action="bulk-download">下载</button>
            <button class="g3-btn g3-pill g3-bulk-btn" type="button" data-action="bulk-link">关联知识点</button>
          </div>
        </div>
        <div class="g3-file-toolbar-tools">
          <button class="g3-btn quiet g3-tool-btn" type="button" data-action="export-resources">
            <svg class="g3-tool-icon" width="14" height="14" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 16V4m0 0-4 4m4-4 4 4"/>
              <path d="M5 15v4a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-4"/></svg>导出</button>
          <div class="g3-search">
            <input id="g3ResourceSearch" class="g3-input" type="search" placeholder="搜索" aria-label="搜索资料">
            <span class="g3-search-icon" aria-hidden="true">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                stroke-width="1.8"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
            </span>
          </div>
        </div>
      </div>
      ${summaryMarkup}
      ${listMarkup}
    </section>`;
  }

  function renderQuestionTable(questions, points, context) {
    const kinds = ['SINGLE', 'MULTIPLE', 'TRUE_FALSE', 'SHORT_TEXT'];
    const folders = readQuestionFolders(context);
    const activeFolder = folders.find(folder => folder.id === state.currentQuestionFolderId) || null;
    if (!activeFolder) state.currentQuestionFolderId = null;
    const visibleQuestions = activeFolder
      ? questions.filter(question => (activeFolder.questionIds || [])
        .map(String).includes(String(question.questionId)))
      : questions;
    const courseOptions = (state.contexts || []).map(item =>
      `<option value="${escapeHtml(item.bindingId)}"${optionMarkup(item.bindingId, state.bindingId)}>`
      + `${escapeHtml(item.courseTitle)} · ${escapeHtml(item.className)}</option>`).join('');
    const pointOptions = points.map(point =>
      `<option value="${escapeHtml(point.name)}">${escapeHtml(point.name)}</option>`).join('');
    const folderRows = activeFolder ? '' : folders.map(folder => `<tr class="g3-question-folder-row"
      data-question-folder-row data-folder="${folder.id}" data-title="${escapeHtml(folder.name)}">
      <td class="g3-check-col"><input type="checkbox" data-row-check aria-label="选择 ${escapeHtml(folder.name)}"></td>
      <td class="g3-file-dim">-</td>
      <td><div class="g3-file-name g3-question-folder-name">
        ${folderIconMarkup()}
        <div class="g3-file-text">
          <div class="g3-file-title">${escapeHtml(folder.name)}</div>
          <div class="g3-cell-meta">共 ${(folder.questionIds || []).length} 题
            · ${folder.visibility === 'PRIVATE' ? '私密' : '公开'}</div>
        </div></div></td>
      <td class="g3-file-dim">-</td><td class="g3-file-dim">-</td><td class="g3-file-dim">-</td>
      <td class="g3-file-dim">-</td><td class="g3-file-dim">-</td>
      <td>${formatTime(folder.createdAt)}</td>
      <td></td>
    </tr>`).join('');
    const rows = visibleQuestions.map((question, index) => {
      const answer = formatAnswer(question.answer, question.questionKind);
      const pointNames = question.knowledgePoints || '';
      return `<tr data-question-row data-question-id="${question.questionId}" data-title="${escapeHtml(question.title)}"
        data-stem="${escapeHtml(question.stem || '')}" data-kind="${escapeHtml(question.questionKind)}"
        data-points="${escapeHtml(pointNames)}">
        <td class="g3-check-col"><input type="checkbox" data-row-check aria-label="选择 ${escapeHtml(question.title)}"></td>
        <td class="g3-file-dim">${index + 1}</td>
        <td><div class="g3-file-name">
          <span class="g3-file-icon">题</span>
          <div class="g3-file-text">
            <div class="g3-file-title">${escapeHtml(question.title)}</div>
            <div class="g3-cell-meta">V${escapeHtml(question.versionNo)} · ${escapeHtml(question.maxScore)} 分
              · 答案 ${escapeHtml(answer)}${pointNames ? ` · ${escapeHtml(pointNames)}` : ''}</div>
            <div class="g3-cell-meta g3-q-stem">${escapeHtml(question.stem)}</div>
          </div></div></td>
        <td>${escapeHtml(labels[question.questionKind] || question.questionKind)}</td>
        <td class="g3-file-dim">-</td>
        <td class="g3-file-dim">-</td>
        <td class="g3-file-dim">-</td>
        <td class="g3-file-dim">-</td>
        <td>${formatTime(question.createdAt)}</td>
        <td><div class="g3-actions">
          <button class="g3-btn quiet" type="button" data-action="question-version" data-question="${question.questionId}"
            data-kind="${question.questionKind}" data-title="${escapeHtml(question.title)}">新版本</button>
          ${question.status !== 'ARCHIVED' ? `<button class="g3-btn quiet" type="button" data-action="archive-question" data-question="${question.questionId}">归档</button>` : ''}
        </div></td>
      </tr>`;
    }).join('');
    const emptyFolderRow = (activeFolder && !rows)
      ? `<tr><td colspan="10"><div class="g3-question-folder-empty">此文件夹暂无题目</div></td></tr>`
      : '';
    const listMarkup = (questions.length || folders.length)
      ? `<div class="g3-table-wrap"><table class="g3-table g3-question-table">
          <thead><tr>
            <th class="g3-check-col"><input type="checkbox" data-action="select-all" aria-label="全选"></th>
            <th>序号</th><th>文件夹 / 题目</th><th>题型</th><th>难易</th><th>使用量</th><th>正确率</th><th>创建者</th><th>创建时间</th><th>操作</th>
          </tr></thead>
          <tbody data-question-rows>${folderRows}${rows || emptyFolderRow}</tbody>
        </table></div>`
      : `<div class="g3-file-empty">${emptyState('题库为空', '创建单选、多选、判断或简答题，并保留答案版本')}</div>`;
    return `<section class="g3-panel g3-question-panel">
      <div class="g3-file-toolbar g3-question-toolbar">
        <div class="g3-file-toolbar-main">
          ${questionCreateMenuMarkup('创建题目', true)}
          ${questionImportMenuMarkup()}
          <button class="g3-btn g3-pill g3-outline-pill" type="button" data-action="new-question-folder">新建文件夹</button>
          <label class="g3-q-detail-toggle"><input type="checkbox" data-action="toggle-question-detail" checked><span>显示题目详情</span></label>
        </div>
        <div class="g3-file-toolbar-tools">
          <button class="g3-btn quiet g3-tool-btn" type="button" data-action="studio-coming" data-label="标签管理">标签管理</button>
          <button class="g3-btn quiet g3-tool-btn" type="button" data-action="studio-coming" data-label="题型管理">题型管理</button>
          <button class="g3-btn quiet g3-tool-btn" type="button" data-action="studio-coming" data-label="查重">查重</button>
          <button class="g3-btn quiet g3-tool-btn" type="button" data-action="export-questions">
            <svg class="g3-tool-icon" width="14" height="14" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 16V4m0 0-4 4m4-4 4 4"/>
              <path d="M5 15v4a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-4"/></svg>导出全部</button>
          <button class="g3-btn g3-ai-btn" type="button" data-action="studio-coming" data-label="AI 出题">AI 出题</button>
          <div class="g3-search">
            <input id="g3QuestionSearch" class="g3-input" type="search" placeholder="搜索" aria-label="搜索题目">
            <span class="g3-search-icon" aria-hidden="true">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                stroke-width="1.8"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
            </span>
          </div>
        </div>
      </div>
      <div class="g3-q-filter">
        <label class="g3-q-filter-item"><span>课程</span>
          <select id="g3QuestionCourse" class="g3-select">${courseOptions}</select></label>
        <label class="g3-q-filter-item"><span>题型</span>
          <select id="g3QuestionKind" class="g3-select"><option value="">全部题型</option>
            ${kinds.map(kind => `<option value="${kind}">${labels[kind]}</option>`).join('')}</select></label>
        <label class="g3-q-filter-item"><span>知识点</span>
          <select id="g3QuestionPoint" class="g3-select"><option value="">请选择</option>${pointOptions}</select></label>
      </div>
      <div class="${activeFolder ? 'g3-file-breadcrumb' : 'g3-file-summary'}">
        ${activeFolder
          ? `<div class="g3-breadcrumb-path">
              <button class="g3-breadcrumb-link" type="button" data-action="question-folder-back">全部题目</button>
              <span class="g3-breadcrumb-sep">›</span>
              <span class="g3-breadcrumb-current">${escapeHtml(activeFolder.name)}</span>
             </div>
             <span data-question-count>共 ${visibleQuestions.length} 题</span>`
          : `<strong>全部题目</strong><span data-question-count>共 ${questions.length} 题</span>`}
      </div>
      ${listMarkup}
    </section>`;
  }

  function formatAnswer(answer, kind) {
    if (answer == null) return '未设置';
    if (kind === 'TRUE_FALSE') return answer.correctBoolean ? '正确' : '错误';
    if (kind === 'SINGLE') return `选项 ${Number(answer.choiceIndex) + 1}`;
    if (kind === 'MULTIPLE') return `选项 ${(answer.choiceIndices || []).map(index => Number(index) + 1).join('、')}`;
    if (kind === 'SHORT_TEXT') return answer.referenceAnswer || '参考答案';
    return JSON.stringify(answer);
  }

  function renderOutline(units, points, context) {
    const roots = units.filter(unit => !unit.parentUnitId);
    const childrenOf = parent => units.filter(unit => String(unit.parentUnitId) === String(parent.unitId));
    const pointsOf = unitId => points.filter(point => String(point.unitId) === String(unitId));
    // 章节/小节行的操作按钮
    const unitActions = (unitId, kind) => `<span class="g3-outline-actions">
      <button class="g3-outline-mini" type="button" data-outline-op="edit-unit"
        data-unit-id="${escapeHtml(unitId)}" title="编辑">✎</button>
      <button class="g3-outline-mini" type="button" data-outline-op="move-up"
        data-unit-id="${escapeHtml(unitId)}" title="上移">↑</button>
      <button class="g3-outline-mini" type="button" data-outline-op="move-down"
        data-unit-id="${escapeHtml(unitId)}" title="下移">↓</button>
      ${kind === 'CHAPTER'
        ? `<button class="g3-outline-mini" type="button" data-outline-op="add-section"
            data-unit-id="${escapeHtml(unitId)}" title="新建小节">+节</button>
           <button class="g3-outline-mini" type="button" data-outline-op="add-point"
            data-unit-id="${escapeHtml(unitId)}" title="新建知识点">+点</button>`
        : `<button class="g3-outline-mini" type="button" data-outline-op="add-point"
            data-unit-id="${escapeHtml(unitId)}" title="新建知识点">+点</button>`}
      <button class="g3-outline-mini danger" type="button" data-outline-op="delete-unit"
        data-unit-id="${escapeHtml(unitId)}" title="删除">🗑</button>
    </span>`;
    // 知识点行的操作按钮
    const pointActions = pointId => `<span class="g3-outline-actions">
      <button class="g3-outline-mini" type="button" data-outline-op="edit-point"
        data-point-id="${escapeHtml(pointId)}" title="编辑">✎</button>
      <button class="g3-outline-mini danger" type="button" data-outline-op="delete-point"
        data-point-id="${escapeHtml(pointId)}" title="删除">🗑</button>
    </span>`;
    const pointRow = point => `<div class="g3-outline-row point"
      data-outline-title="${escapeHtml(point.name)}"
      data-point-id="${escapeHtml(point.knowledgePointId || point.name)}">
      <span class="g3-outline-dot"></span>
      <span class="g3-outline-title">${point.code ? `<span class="g3-outline-code">${escapeHtml(point.code)}</span>` : ''}${escapeHtml(point.name)}</span>
      ${pointActions(point.knowledgePointId || point.name)}</div>`;
    const sideRow = kind => `<div class="g3-outline-side-row ${kind}">
      ${kind === 'chapter' ? '<span class="g3-outline-open"></span>' : `<span class="g3-outline-open">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
          stroke-width="2" aria-hidden="true"><path d="m5 13 4 4 10-10"/></svg>开放</span>`}
      <span class="g3-outline-progress">-</span></div>`;
    const pointLine = point => `${pointRow(point)}${sideRow('point')}`;
    let counter = 0;
    const rows = [];
    roots.forEach((root, rootIndex) => {
      rows.push(`<div class="g3-outline-row chapter"
        data-unit-id="${escapeHtml(root.unitId || '')}"
        data-unit-kind="CHAPTER"
        data-sort-order="${escapeHtml(root.sortOrder ?? rootIndex)}"
        data-outline-title="${escapeHtml(root.title)}">
        <span class="g3-outline-num">${rootIndex}</span>
        <span class="g3-outline-title">${escapeHtml(root.title)}</span>
        ${unitActions(root.unitId || '', 'CHAPTER')}
        <button class="g3-outline-caret" type="button" data-action="toggle-chapter"
          aria-label="收起 ${escapeHtml(root.title)}">⌄</button></div>`, sideRow('chapter'));
      const childRows = [];
      childrenOf(root).forEach((child, childIndex) => {
        counter += 1;
        childRows.push(`<div class="g3-outline-row section"
          data-unit-id="${escapeHtml(child.unitId || '')}"
          data-unit-kind="SECTION"
          data-sort-order="${escapeHtml(child.sortOrder ?? childIndex)}"
          data-outline-title="${escapeHtml(child.title)}">
          <span class="g3-outline-num">${counter}</span>
          <span class="g3-outline-title"><span class="g3-outline-index">${rootIndex + 1}.${childIndex + 1}</span>${escapeHtml(child.title)}</span>
          ${unitActions(child.unitId || '', 'SECTION')}</div>`, sideRow('section'));
        pointsOf(child.unitId).forEach(point => childRows.push(pointLine(point)));
      });
      pointsOf(root.unitId).forEach(point => childRows.push(pointLine(point)));
      if (childRows.length) {
        rows.push(`<div class="g3-outline-children">${childRows.join('')}</div>`);
      }
    });
    points.filter(point => !units.some(unit => String(unit.unitId) === String(point.unitId)))
      .forEach(point => rows.push(pointLine(point)));
    return `<section class="g3-panel g3-outline-panel">
      <div class="g3-outline-toolbar">
        <div class="g3-outline-toolbar-main">
          <button class="g3-btn primary g3-pill g3-pill-purple" type="button" data-action="add-chapter">
            + 新建章</button>
          <button class="g3-btn quiet g3-tool-btn" type="button" data-action="add-point-root">
            + 新建知识点</button>
          <button class="g3-btn quiet g3-tool-btn" type="button" data-action="edit-outline">批量编辑</button>
          <button class="g3-reminder" type="button" data-action="studio-coming" data-label="定时开放任务点提醒">
            定时开放任务点提醒<span class="g3-reminder-switch"></span></button>
        </div>
        <div class="g3-outline-toolbar-tools">
          <div class="g3-outline-menu" data-outline-menu>
            <button class="g3-btn quiet g3-tool-btn" type="button" data-action="toggle-outline-import"
              aria-haspopup="menu" aria-expanded="false">导入</button>
            <div class="g3-outline-dropdown" role="menu">
              <button class="g3-outline-item" type="button" role="menuitem"
                data-action="studio-coming" data-label="导入课程章节">课程章节</button>
              <button class="g3-outline-item" type="button" role="menuitem"
                data-action="studio-coming" data-label="从教学资源库导入">教学资源库</button>
            </div>
          </div>
          <div class="g3-outline-menu" data-outline-menu>
            <button class="g3-btn quiet g3-tool-btn" type="button" data-action="toggle-outline-export"
              aria-haspopup="menu" aria-expanded="false">导出</button>
            <div class="g3-outline-dropdown" role="menu">
              <button class="g3-outline-item" type="button" role="menuitem"
                data-action="studio-coming" data-label="导出 Word">导出 Word</button>
              <button class="g3-outline-item" type="button" role="menuitem"
                data-action="studio-coming" data-label="导出 PDF">导出 PDF</button>
            </div>
          </div>
          <button class="g3-btn quiet g3-tool-btn" type="button" data-action="studio-coming" data-label="打印">打印</button>
          <button class="g3-btn quiet g3-tool-btn" type="button" data-action="studio-coming" data-label="批量设置">批量设置</button>
          <div class="g3-search">
            <input id="g3OutlineSearch" class="g3-input" type="search" placeholder="搜索" aria-label="搜索章节">
            <span class="g3-search-icon" aria-hidden="true">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                stroke-width="1.8"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
            </span>
          </div>
        </div>
      </div>
      <div class="g3-outline-body">
        <div class="g3-outline-heading-spacer"></div>
        <div class="g3-outline-side-title">${escapeHtml(context.courseTitle)} <span>⌄</span></div>
        <div class="g3-outline-heading">目录</div>
        <div class="g3-outline-side-cols"><span>开放状态</span><span>章节进度</span></div>
        ${rows.length ? `<div class="g3-outline-rows" data-outline-rows>${rows.join('')}</div>`
          : `<div class="g3-outline-empty">${emptyState('暂无章节', '章节与知识点结构建立后会显示在这里')}</div>`}
      </div>
    </section>`;
  }

  function bindTeacherStudioActions(overview, context) {
    root.querySelector('[data-action="toggle-add-menu"]')?.addEventListener('click', event => {
      event.stopPropagation();
      const menu = event.currentTarget.closest('[data-add-menu]');
      const open = menu.classList.toggle('open');
      event.currentTarget.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    root.querySelectorAll('[data-action="source-coming"]').forEach(button => {
      button.addEventListener('click', () => {
        closeAddMenu();
        toast(`“${button.dataset.label}”来源暂未接入，当前请使用本地上传`);
      });
    });
    root.querySelector('[data-action="open-library"]')?.addEventListener('click', () => {
      closeAddMenu();
      openResourceLibraryModal();
    });
    root.querySelector('[data-action="cross-course"]')?.addEventListener('click', () => {
      closeAddMenu();
      openCrossCourseModal();
    });
    root.querySelector('[data-action="cloud-drive"]')?.addEventListener('click', () => {
      closeAddMenu();
      openCloudDriveModal();
    });
    root.querySelector('[data-action="upload-resource"]')?.addEventListener('click', () => {
      closeAddMenu();
      showUploadModal(overview, context);
    });
    root.querySelector('[data-action="new-folder"]')?.addEventListener('click', () => {
      showNewFolderModal(context);
    });
    root.querySelector('[data-action="export-resources"]')?.addEventListener('click', () => {
      exportResources(overview.resources || []);
    });
    bindResourceList();
    root.querySelectorAll('[data-folder-row]').forEach(row => {
      row.addEventListener('click', event => {
        if (event.target.closest('input') || event.target.closest('button')) return;
        state.currentFolderId = row.dataset.folder;
        renderTeacherStudio();
      });
    });
    root.querySelector('[data-action="folder-back"]')?.addEventListener('click', () => {
      state.currentFolderId = null;
      renderTeacherStudio();
    });
    root.querySelectorAll('[data-action="toggle-more"]').forEach(button => {
      button.addEventListener('click', event => {
        event.stopPropagation();
        const menu = button.closest('.g3-more-menu');
        const open = !menu.classList.contains('open');
        root.querySelectorAll('.g3-more-menu.open').forEach(node => node.classList.remove('open'));
        if (!open) {
          button.setAttribute('aria-expanded', 'false');
          return;
        }
        menu.classList.add('open');
        button.setAttribute('aria-expanded', 'true');
        const dropdown = menu.querySelector('.g3-more-dropdown');
        const rect = button.getBoundingClientRect();
        const width = dropdown.offsetWidth || 126;
        dropdown.style.top = `${rect.bottom + 8}px`;
        dropdown.style.left = `${Math.max(8, rect.right - width)}px`;
      });
    });
    root.querySelectorAll('.g3-more-dropdown [data-action]').forEach(button => {
      button.addEventListener('click', () => {
        root.querySelectorAll('.g3-more-menu.open').forEach(node => node.classList.remove('open'));
      });
    });
    root.querySelectorAll('[data-action="edit-resource"]').forEach(button => {
      button.addEventListener('click', () => promptEditResource(context, button.dataset.resource, button.dataset.title));
    });
    root.querySelectorAll('[data-action="archive-resource"]').forEach(button => {
      button.addEventListener('click', () => confirmArchiveResource(context, button.dataset.resource, button.dataset.title, 'ARCHIVED'));
    });
    root.querySelectorAll('[data-action="unarchive-resource"]').forEach(button => {
      button.addEventListener('click', () => confirmArchiveResource(context, button.dataset.resource, button.dataset.title, 'ACTIVE'));
    });
    root.querySelectorAll('[data-action="download-resource"]').forEach(button => {
      button.addEventListener('click', () => downloadVersion(button.dataset.version, button.dataset.binding));
    });
    root.querySelectorAll('[data-action="toggle-question-create"], [data-action="toggle-question-import"]').forEach(button => {
      button.addEventListener('click', event => {
        event.stopPropagation();
        const menu = button.closest('.g3-question-create-menu');
        const open = !menu.classList.contains('open');
        root.querySelectorAll('.g3-question-create-menu.open').forEach(node => node.classList.remove('open'));
        menu.classList.toggle('open', open);
        button.setAttribute('aria-expanded', open ? 'true' : 'false');
      });
    });
    root.querySelectorAll('[data-action="create-question-manual"]').forEach(button => {
      button.addEventListener('click', () => {
        root.querySelectorAll('.g3-question-create-menu.open').forEach(node => node.classList.remove('open'));
        showQuestionModal(overview, context);
      });
    });
    root.querySelectorAll('[data-action="create-question-library"]').forEach(button => {
      button.addEventListener('click', () => {
        root.querySelectorAll('.g3-question-create-menu.open').forEach(node => node.classList.remove('open'));
        showQuestionLibraryModal(context);
      });
    });
    root.querySelectorAll('[data-action^="import-questions-"]').forEach(button => {
      button.addEventListener('click', () => {
        root.querySelectorAll('.g3-question-create-menu.open').forEach(node => node.classList.remove('open'));
        toast(`${button.dataset.label}暂未接入`);
      });
    });
    root.querySelector('[data-action="new-question-folder"]')?.addEventListener('click', () => {
      const tbody = root.querySelector('[data-question-rows]');
      if (!tbody) return;
      const existing = tbody.querySelector('[data-question-folder-editor]');
      if (existing) {
        existing.querySelector('input')?.focus();
        return;
      }
      const row = document.createElement('tr');
      row.className = 'g3-question-folder-editor';
      row.setAttribute('data-question-folder-editor', '');
      row.innerHTML = `<td class="g3-check-col"></td>
        <td class="g3-file-dim">-</td>
        <td><div class="g3-folder-inline">
          <span class="g3-folder-inline-icon">${folderIconMarkup()}</span>
          <input class="g3-input g3-folder-inline-input" value="新建文件夹" aria-label="文件夹名称">
          <button class="g3-folder-inline-btn confirm" type="button" aria-label="确认">✓</button>
          <button class="g3-folder-inline-btn cancel" type="button" aria-label="取消">×</button>
        </div></td>
        <td class="g3-file-dim">-</td><td class="g3-file-dim">-</td><td class="g3-file-dim">-</td>
        <td class="g3-file-dim">-</td><td class="g3-file-dim">-</td>
        <td>${formatTime(new Date().toISOString())}</td><td></td>`;
      tbody.insertBefore(row, tbody.firstElementChild);
      const input = row.querySelector('input');
      const finish = () => {
        const folderName = input.value.trim();
        if (!folderName) {
          input.focus();
          return;
        }
        const folders = readQuestionFolders(context);
        folders.push({
          id: `question-folder-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          name: folderName,
          visibility: 'PUBLIC',
          questionIds: [],
          createdAt: new Date().toISOString()
        });
        writeQuestionFolders(context, folders);
        state.currentQuestionFolderId = null;
        row.remove();
        toast('文件夹已创建');
        renderTeacherStudio();
      };
      row.querySelector('.g3-folder-inline-btn.confirm').addEventListener('click', finish);
      row.querySelector('.g3-folder-inline-btn.cancel').addEventListener('click', () => row.remove());
      input.addEventListener('keydown', event => {
        if (event.key === 'Enter') {
          event.preventDefault();
          finish();
        } else if (event.key === 'Escape') {
          event.preventDefault();
          row.remove();
        }
      });
      input.focus();
      input.select();
    });
    root.querySelectorAll('[data-action="studio-coming"]').forEach(button => {
      button.addEventListener('click', () => {
        root.querySelectorAll('[data-outline-menu].open').forEach(node => node.classList.remove('open'));
        toast(`“${button.dataset.label}”暂未接入`);
      });
    });
    root.querySelectorAll('[data-action="toggle-outline-import"], [data-action="toggle-outline-export"]').forEach(button => {
      button.addEventListener('click', event => {
        event.stopPropagation();
        const menu = button.closest('[data-outline-menu]');
        const open = !menu.classList.contains('open');
        root.querySelectorAll('[data-outline-menu].open').forEach(node => node.classList.remove('open'));
        menu.classList.toggle('open', open);
        button.setAttribute('aria-expanded', open ? 'true' : 'false');
      });
    });
    root.querySelectorAll('[data-action="toggle-chapter"]').forEach(button => {
      button.addEventListener('click', event => {
        event.stopPropagation();
        const children = button.closest('.g3-outline-row').nextElementSibling;
        if (!children || !children.classList.contains('g3-outline-children')) return;
        const collapsed = children.hidden = !children.hidden;
        button.closest('.g3-outline-row').classList.toggle('collapsed', collapsed);
        button.setAttribute('aria-label', collapsed ? '展开' : '收起');
      });
    });
    root.querySelector('[data-action="export-questions"]')?.addEventListener('click', () => {
      exportQuestions(overview.questions || []);
    });
    root.querySelector('[data-action="toggle-question-detail"]')?.addEventListener('change', event => {
      root.querySelectorAll('.g3-q-stem').forEach(node => {
        node.hidden = !event.target.checked;
      });
    });
    bindQuestionList();
    root.querySelectorAll('[data-question-folder-row]').forEach(row => {
      row.addEventListener('click', event => {
        if (event.target.closest('input') || event.target.closest('button')) return;
        state.currentQuestionFolderId = row.dataset.folder;
        renderTeacherStudio();
      });
    });
    root.querySelectorAll('[data-question-row]').forEach(row => {
      row.addEventListener('click', event => {
        if (event.target.closest('button') || event.target.closest('input') || event.target.closest('a')) return;
        const list = [...root.querySelectorAll('[data-question-row]')]
          .map(node => (overview.questions || [])
            .find(question => String(question.questionId) === node.dataset.questionId))
          .filter(Boolean);
        const start = list.findIndex(question => String(question.questionId) === row.dataset.questionId);
        showQuestionPreviewModal(list, Math.max(start, 0));
      });
    });
    root.querySelector('[data-action="question-folder-back"]')?.addEventListener('click', () => {
      state.currentQuestionFolderId = null;
      renderTeacherStudio();
    });
    root.querySelector('[data-action="edit-outline"]')?.addEventListener('click', () => {
      showChapterEditorModal(overview, context);
    });
    bindOutlineList();
    // 新建章
    root.querySelector('[data-action="add-chapter"]')?.addEventListener('click', () => {
      promptCreateUnit(context, 'CHAPTER', null);
    });
    // 根级新建知识点
    root.querySelector('[data-action="add-point-root"]')?.addEventListener('click', () => {
      promptCreateKnowledgePoint(context, null);
    });
    // 知识点行点击打开详情（排除按钮）
    root.querySelectorAll('[data-point-id]').forEach(row => {
      row.addEventListener('click', event => {
        if (event.target.closest('button')) return;
        showChapterDetailModal(overview, context, row.dataset.pointId, row.dataset.outlineTitle);
      });
    });
    // 章节/知识点 CRUD 操作
    root.querySelectorAll('[data-outline-op]').forEach(btn => {
      btn.addEventListener('click', event => {
        event.stopPropagation();
        const op = btn.dataset.outlineOp;
        const unitId = btn.dataset.unitId;
        const pointId = btn.dataset.pointId;
        const row = btn.closest('[data-unit-id]') || btn.closest('[data-point-id]');
        const sortOrder = row ? Number(row.dataset.sortOrder || 0) : 0;
        switch (op) {
          case 'edit-unit':
            promptEditUnit(context, unitId, row?.dataset.outlineTitle || '', sortOrder);
            break;
          case 'add-section':
            promptCreateUnit(context, 'SECTION', unitId);
            break;
          case 'add-point':
            promptCreateKnowledgePoint(context, unitId);
            break;
          case 'delete-unit':
            confirmDeleteUnit(context, unitId, row?.dataset.outlineTitle || '');
            break;
          case 'move-up':
            reorderUnit(context, unitId, sortOrder, -1);
            break;
          case 'move-down':
            reorderUnit(context, unitId, sortOrder, +1);
            break;
          case 'edit-point':
            promptEditKnowledgePoint(context, pointId, row);
            break;
          case 'delete-point':
            confirmDeleteKnowledgePoint(context, pointId, row?.dataset.outlineTitle || '');
            break;
        }
      });
    });
    root.querySelectorAll('[data-action="preview"]').forEach(button => {
      button.addEventListener('click', () => showPreview(button.dataset.version, button.dataset.binding));
    });
    root.querySelectorAll('[data-action="versions"]').forEach(button => {
      button.addEventListener('click', () => showVersions(button.dataset.resource, context));
    });
    root.querySelectorAll('[data-action="release"]').forEach(button => {
      button.addEventListener('click', () => showReleaseModal(button.dataset.version, button.dataset.title));
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

  function closeAddMenu() {
    root.querySelectorAll('[data-add-menu].open').forEach(menu => {
      menu.classList.remove('open');
      menu.querySelector('[data-action="toggle-add-menu"]')?.setAttribute('aria-expanded', 'false');
    });
  }

  function bindResourceList() {
    const search = root.querySelector('#g3ResourceSearch');
    const rows = [...root.querySelectorAll('[data-resource-row], [data-folder-row]')];
    const bulkBar = root.querySelector('[data-bulk-bar]');
    const selectedCount = root.querySelector('[data-selected-count]');
    const selectAll = root.querySelector('[data-action="select-all"]');
    if (!rows.length) return;
    const syncSelection = () => {
      const checked = rows
        .map(row => row.querySelector('[data-row-check]'))
        .filter(box => box && box.checked).length;
      if (bulkBar) bulkBar.hidden = checked === 0;
      if (selectedCount) {
        selectedCount.hidden = checked === 0;
        selectedCount.textContent = checked ? `已选 ${checked} 个，` : '';
      }
      if (selectAll) {
        const boxes = rows.map(row => row.querySelector('[data-row-check]')).filter(Boolean);
        selectAll.checked = boxes.length > 0 && boxes.every(box => box.checked);
        selectAll.indeterminate = checked > 0 && checked < boxes.length;
      }
    };
    search?.addEventListener('input', () => {
      const keyword = search.value.trim().toLowerCase();
      let visible = 0;
      rows.forEach(row => {
        const match = !keyword || (row.dataset.title || '').toLowerCase().includes(keyword);
        row.hidden = !match;
        if (match) visible += 1;
      });
      const textNode = selectedCount?.nextSibling;
      const countText = keyword ? `筛选出 ${visible} 个` : `当前页共 ${rows.length} 个`;
      if (textNode && textNode.nodeType === Node.TEXT_NODE) {
        textNode.textContent = countText;
      }
    });
    selectAll?.addEventListener('change', event => {
      rows.forEach(row => {
        const box = row.querySelector('[data-row-check]');
        if (box && !row.hidden) box.checked = event.target.checked;
      });
      syncSelection();
    });
    rows.forEach(row => {
      row.querySelector('[data-row-check]')?.addEventListener('change', syncSelection);
    });
    root.querySelectorAll('[data-bulk-bar] [data-action]').forEach(button => {
      button.addEventListener('click', () => {
        const labels = {
          'bulk-move': '移动到', 'bulk-delete': '删除', 'bulk-permission': '权限设置',
          'bulk-download': '下载', 'bulk-link': '关联知识点'
        };
        toast(`批量${labels[button.dataset.action] || ''}暂未接入`);
      });
    });
  }

  function bindQuestionList() {
    const search = root.querySelector('#g3QuestionSearch');
    const kind = root.querySelector('#g3QuestionKind');
    const point = root.querySelector('#g3QuestionPoint');
    const course = root.querySelector('#g3QuestionCourse');
    const rows = [...root.querySelectorAll('[data-question-row]')];
    const folderRows = [...root.querySelectorAll('[data-question-folder-row]')];
    const count = root.querySelector('[data-question-count]');
    course?.addEventListener('change', event => {
      if (!event.target.value || event.target.value === state.bindingId) return;
      state.bindingId = event.target.value;
      localStorage.setItem(`zhijiao.group3.binding.${role}`, state.bindingId);
      clearOverview();
      state.selectedPreviewId = null;
      state.currentFolderId = null;
      state.currentQuestionFolderId = null;
      rerender();
    });
    if (!rows.length && !folderRows.length) return;
    const apply = () => {
      const keyword = (search?.value || '').trim().toLowerCase();
      const selectedKind = kind?.value || '';
      const selectedPoint = point?.value || '';
      let visible = 0;
      rows.forEach(row => {
        const match = (!keyword || (row.dataset.title || '').toLowerCase().includes(keyword)
            || (row.dataset.stem || '').toLowerCase().includes(keyword))
          && (!selectedKind || row.dataset.kind === selectedKind)
          && (!selectedPoint || (row.dataset.points || '').includes(selectedPoint));
        row.hidden = !match;
        if (match) visible += 1;
      });
      folderRows.forEach(row => {
        const keywordMatch = !keyword || (row.dataset.title || '').toLowerCase().includes(keyword);
        row.hidden = !keywordMatch || Boolean(selectedKind || selectedPoint);
      });
      if (count) count.textContent = `共 ${visible} 题`;
    };
    search?.addEventListener('input', apply);
    kind?.addEventListener('change', apply);
    point?.addEventListener('change', apply);
    course?.addEventListener('change', event => {
      if (!event.target.value || event.target.value === state.bindingId) return;
      state.bindingId = event.target.value;
      localStorage.setItem(`zhijiao.group3.binding.${role}`, state.bindingId);
      clearOverview();
      state.selectedPreviewId = null;
      state.currentFolderId = null;
      state.currentQuestionFolderId = null;
      rerender();
    });
    root.querySelector('[data-action="select-all"]')?.addEventListener('change', event => {
      [...rows, ...folderRows].forEach(row => {
        const box = row.querySelector('[data-row-check]');
        if (box) box.checked = event.target.checked;
      });
    });
  }

  function bindOutlineList() {
    const search = root.querySelector('#g3OutlineSearch');
    const rows = [...root.querySelectorAll('[data-outline-rows] .g3-outline-row')];
    search?.addEventListener('input', () => {
      const keyword = search.value.trim().toLowerCase();
      rows.forEach(row => {
        row.hidden = Boolean(keyword) && !(row.dataset.outlineTitle || '').toLowerCase().includes(keyword);
      });
    });
  }

  function saveCsv(filename, lines) {
    const csv = lines.map(line => line.map(value => `"${String(value).replace(/"/g, '""')}"`).join(','))
      .join('\r\n');
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1500);
  }

  function exportQuestions(questions) {
    if (!questions.length) return toast('当前没有可导出的题目', true);
    const lines = [['序号', '题目', '题型', '版本', '满分', '答案', '知识点', '创建时间']];
    questions.forEach((question, index) => {
      lines.push([
        index + 1,
        question.title,
        labels[question.questionKind] || question.questionKind,
        `V${question.versionNo}`,
        `${question.maxScore} 分`,
        formatAnswer(question.answer, question.questionKind),
        question.knowledgePoints || '-',
        formatTime(question.createdAt)
      ]);
    });
    saveCsv('课程题库.csv', lines);
  }

  function exportResources(resources) {
    if (!resources.length) return toast('当前没有可导出的资料', true);
    const lines = [['文件名', '阅读人数', '下载次数', '大小', '创建者', '创建日期']];
    resources.forEach(item => {
      const size = Number(item.byteSize);
      lines.push([
        item.title,
        item.readCount ?? '-',
        item.downloadCount ?? '-',
        size > 0 ? formatFileSize(size) : '-',
        item.creatorName || '-',
        formatTime(item.createdAt)
      ]);
    });
    saveCsv('课程资料.csv', lines);
  }

  function knowledgeMarkup(overview) {
    if (!overview.knowledgePoints?.length) return '<div class="g3-help">当前课程尚未维护知识点</div>';
    return overview.knowledgePoints.map(point =>
      `<label class="g3-check"><input type="checkbox" name="knowledgePoint" value="${escapeHtml(point.knowledgePointId)}">
        <span>${escapeHtml(point.name)}</span></label>`).join('');
  }

  function openResourceLibraryModal() {
    const categories = ['无所属分类', '微课库', '课程资源素材库'];
    const mediaTypes = ['全部', '文本类', '视频类', 'PPT类', '图片类', '动画类', '音频类', '更多'];
    const openLibrarySvg = `<svg width="118" height="88" viewBox="0 0 118 88" fill="none" aria-hidden="true">
      <ellipse cx="59" cy="77" rx="36" ry="8" fill="#f2f4f7"/>
      <path d="M25 33 59 17l34 16-34 16-34-16Z" fill="#f6f8fa" stroke="#e8edf2"/>
      <path d="M25 33v28l34 16V49" fill="#fbfcfd" stroke="#e8edf2"/>
      <path d="M93 33v28L59 77V49" fill="#f6f8fa" stroke="#e8edf2"/>
      <path d="M42 25l34 16v8l-34-16v-8Z" fill="#fbfcfd" stroke="#e8edf2"/></svg>`;
    const body = `<div class="g3-library">
      <div class="g3-library-tabs">
        <button class="g3-library-tab active" type="button" data-library-tab>本校资源库</button>
        <button class="g3-library-tab" type="button" data-library-tab>教学资源库</button>
      </div>
      <div class="g3-library-body">
        <aside class="g3-library-side">
          <select class="g3-select g3-library-source" aria-label="资源库">
            <option>本校数字资源库</option>
          </select>
          <div class="g3-search g3-library-search">
            <input class="g3-input" type="search" placeholder="搜索" aria-label="搜索资源分类">
            <span class="g3-search-icon" aria-hidden="true">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                stroke-width="1.8"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
            </span>
          </div>
          <div class="g3-library-cats">
            ${categories.map((name, index) => `<button class="g3-library-cat${index === 0 ? ' active' : ''}"
              type="button" data-library-cat>${name}</button>`).join('')}
          </div>
        </aside>
        <div class="g3-library-main">
          <div class="g3-library-filters">
            <div class="g3-search">
              <input class="g3-input" type="search" placeholder="资源名称/上传人" aria-label="搜索资源">
              <span class="g3-search-icon" aria-hidden="true">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                  stroke-width="1.8"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
              </span>
            </div>
            <select class="g3-select" aria-label="所属课程"><option>所属课程</option></select>
            <select class="g3-select" aria-label="所属分类"><option>所属分类</option></select>
          </div>
          <div class="g3-library-types">
            ${mediaTypes.map((name, index) => `<button class="g3-library-type${index === 0 ? ' active' : ''}"
              type="button" data-library-type>${name}</button>`).join('')}
          </div>
          <div class="g3-library-count">全部共 <span data-library-count>0</span> 个资源，已选 0 个</div>
          <div class="g3-table-wrap">
            <table class="g3-table g3-library-table">
              <thead><tr>
                <th class="g3-check-col"><input type="checkbox" aria-label="全选"></th>
                <th>资源名称</th><th>上传人</th><th>上传时间</th><th>引用量</th><th>所属课程</th><th>大小</th>
              </tr></thead>
              <tbody></tbody>
            </table>
            <div class="g3-library-empty">${openLibrarySvg}<span>当前资源库暂无资源</span></div>
          </div>
        </div>
      </div>
    </div>`;
    openModal({
      title: '教学资源库',
      wide: true,
      body,
      footer: '<button class="g3-btn primary g3-pill" type="button" data-action="library-add">添加</button>',
      onMount: backdrop => {
        backdrop.querySelector('.g3-modal').classList.add('g3-library-modal');
        backdrop.querySelectorAll('[data-library-tab]').forEach(tab => {
          tab.addEventListener('click', () => {
            backdrop.querySelectorAll('[data-library-tab]').forEach(node => node.classList.remove('active'));
            tab.classList.add('active');
          });
        });
        backdrop.querySelectorAll('[data-library-cat]').forEach(item => {
          item.addEventListener('click', () => {
            backdrop.querySelectorAll('[data-library-cat]').forEach(node => node.classList.remove('active'));
            item.classList.add('active');
          });
        });
        backdrop.querySelectorAll('[data-library-type]').forEach(item => {
          item.addEventListener('click', () => {
            backdrop.querySelectorAll('[data-library-type]').forEach(node => node.classList.remove('active'));
            item.classList.add('active');
          });
        });
        backdrop.querySelector('[data-action="library-add"]')?.addEventListener('click', () => {
          toast('当前资源库暂无可添加的资源');
        });
      }
    });
  }

  function openCrossCourseModal() {
    const others = (state.contexts || []).filter(item => String(item.bindingId) !== String(state.bindingId));
    const seen = new Set();
    const courses = [];
    others.forEach(item => {
      const key = item.courseTitle || item.className || String(item.bindingId);
      if (seen.has(key)) return;
      seen.add(key);
      courses.push(item);
    });
    const courseIconSvg = `<svg width="44" height="44" viewBox="0 0 44 44" aria-hidden="true">
      <rect width="44" height="44" rx="8" fill="#3b82f6"/>
      <path d="M12 14h8a3 3 0 0 1 3 3v14a3 3 0 0 0-3-3h-8V14Z" fill="#fff" opacity=".92"/>
      <path d="M32 14h-8a3 3 0 0 0-3 3v14a3 3 0 0 1 3-3h8V14Z" fill="#dbeafe"/></svg>`;
    const renderCourseList = keyword => {
      const filtered = keyword
        ? courses.filter(item => (item.courseTitle || '').toLowerCase().includes(keyword))
        : courses;
      if (!filtered.length) {
        return `<div class="g3-cross-empty">暂无可导入的课程</div>`;
      }
      return filtered.map(item => `<button class="g3-cross-course" type="button"
          data-cross-course="${escapeHtml(item.bindingId)}">
          <span class="g3-cross-course-icon">${courseIconSvg}</span>
          <span class="g3-cross-course-name">${escapeHtml(item.courseTitle || item.className || '未命名课程')}</span>
          <span class="g3-cross-course-caret">›</span>
        </button>`).join('');
    };
    openModal({
      title: '跨课导入',
      wide: true,
      body: `<div class="g3-cross">
        <div class="g3-cross-toolbar">
          <span class="g3-cross-count">我的课程 共 ${courses.length} 个</span>
          <div class="g3-search">
            <input class="g3-input" type="search" placeholder="搜索" aria-label="搜索课程" data-cross-search>
            <span class="g3-search-icon" aria-hidden="true">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                stroke-width="1.8"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
            </span>
          </div>
        </div>
        <div class="g3-cross-list" data-cross-list>${renderCourseList('')}</div>
      </div>`,
      onMount: backdrop => {
        backdrop.querySelector('.g3-modal').classList.add('g3-cross-modal');
        const list = backdrop.querySelector('[data-cross-list]');
        backdrop.querySelector('[data-cross-search]')?.addEventListener('input', event => {
          list.innerHTML = renderCourseList(event.target.value.trim().toLowerCase());
        });
        list.addEventListener('click', event => {
          const course = event.target.closest('[data-cross-course]');
          if (!course) return;
          const target = courses.find(item => String(item.bindingId) === course.dataset.crossCourse);
          if (target) showCrossCourseResources(backdrop, target);
        });
      }
    });
  }

  function showCrossCourseResources(backdrop, course) {
    const body = backdrop.querySelector('.g3-modal-body');
    const foot = backdrop.querySelector('.g3-modal-foot');
    const titleEl = backdrop.querySelector('.g3-modal-head h3');
    const sampleFiles = [
      { name: '示例资料 1.pdf', size: 1024 * 620, time: '2024-10-19 15:48', type: 'PDF' },
      { name: '示例资料 2.docx', size: 1024 * 162, time: '2024-10-19 15:47', type: 'DOC' }
    ];
    const renderRows = () => sampleFiles.map((item, index) => `<tr>
        <td class="g3-check-col"><input type="checkbox" data-cross-check="${index}" aria-label="选择 ${escapeHtml(item.name)}"></td>
        <td><div class="g3-file-name"><span class="g3-file-icon">${item.type}</span>
          <div class="g3-file-text"><div class="g3-file-title">${escapeHtml(item.name)}</div></div></div></td>
        <td class="g3-file-dim">${formatFileSize(item.size)}</td>
        <td class="g3-file-dim">${escapeHtml(item.time)}</td>
      </tr>`).join('');
    if (titleEl) titleEl.innerHTML = `跨课导入<span class="g3-cross-tip">(单次最多导入100个)</span>`;
    body.innerHTML = `<div class="g3-cross g3-cross-detail">
      <div class="g3-cross-toolbar">
        <div class="g3-cross-back-wrap">
          <button class="g3-cross-back" type="button" data-cross-back>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              stroke-width="2" aria-hidden="true"><path d="M15 18l-6-6 6-6"/></svg>返回</button>
          <span class="g3-cross-course-title">${escapeHtml(course.courseTitle || '')}</span>
        </div>
        <div class="g3-search">
          <input class="g3-input" type="search" placeholder="搜索" aria-label="搜索资料">
          <span class="g3-search-icon" aria-hidden="true">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              stroke-width="1.8"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
          </span>
        </div>
      </div>
      <div class="g3-table-wrap">
        <table class="g3-table g3-file-table">
          <thead><tr>
            <th class="g3-check-col"><input type="checkbox" data-cross-all aria-label="全选"></th>
            <th>文件名</th><th>大小</th><th>创建时间</th>
          </tr></thead>
          <tbody>${renderRows()}</tbody>
        </table>
      </div>
    </div>`;
    if (!foot) {
      const modal = backdrop.querySelector('.g3-modal');
      modal.insertAdjacentHTML('beforeend', `<div class="g3-modal-foot g3-cross-foot">
        <span class="g3-cross-selected">已选 <b data-cross-count>0</b> 个</span>
        <div class="g3-cross-actions">
          <button class="g3-btn g3-pill g3-outline-pill" type="button" data-modal-close>取消</button>
          <button class="g3-btn primary g3-pill" type="button" data-cross-confirm>确定</button>
        </div>
      </div>`);
    }
    body.querySelector('[data-cross-back]')?.addEventListener('click', () => openCrossCourseModal());
    const syncCount = () => {
      const count = body.querySelectorAll('[data-cross-check]:checked').length;
      const el = backdrop.querySelector('[data-cross-count]');
      if (el) el.textContent = count;
    };
    body.querySelector('[data-cross-all]')?.addEventListener('change', event => {
      body.querySelectorAll('[data-cross-check]').forEach(box => { box.checked = event.target.checked; });
      syncCount();
    });
    body.querySelectorAll('[data-cross-check]').forEach(box => box.addEventListener('change', syncCount));
    backdrop.querySelector('[data-cross-confirm]')?.addEventListener('click', () => {
      toast('跨课导入暂未接入后端');
    });
  }

  function openCloudDriveModal() {
    const driveFolderIcon = `<span class="g3-drive-folder-icon" aria-hidden="true">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        stroke-width="1.8"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z"/></svg>
    </span>`;
    const tableHead = `<thead><tr>
      <th class="g3-check-col"><input type="checkbox" data-drive-all aria-label="全选"></th>
      <th>文件名</th><th>大小</th><th>创建时间</th>
    </tr></thead>`;
    const rootRow = `<tr>
      <td class="g3-check-col"></td>
      <td><div class="g3-file-name">${driveFolderIcon}
        <div class="g3-file-text"><div class="g3-file-title">
          <button class="g3-drive-folder-link" type="button" data-drive-shared>共享给我的文件夹</button>
        </div></div></div></td>
      <td class="g3-file-dim">-</td>
      <td class="g3-file-dim">-</td>
    </tr>`;
    const renderToolbar = crumbs => `<div class="g3-drive-toolbar">
      <span class="g3-drive-title">${crumbs.length
        ? `<button class="g3-drive-crumb" type="button" data-drive-root>全部文件</button>
           <span class="g3-breadcrumb-sep">›</span> ${escapeHtml(crumbs.join(' › '))}`
        : '全部文件'}</span>
      <span class="g3-drive-total">共 0 个</span>
      <div class="g3-search">
        <input class="g3-input" type="search" placeholder="搜索" aria-label="搜索云盘文件">
        <span class="g3-search-icon" aria-hidden="true">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
            stroke-width="1.8"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
        </span>
      </div>
    </div>`;
    const renderView = crumbs => `${renderToolbar(crumbs)}
      <div class="g3-table-wrap g3-drive-table">
        <table class="g3-table g3-file-table">
          ${tableHead}
          <tbody>${crumbs.length ? '' : rootRow}</tbody>
        </table>
        ${crumbs.length ? '<div class="g3-drive-empty">暂无文件</div>' : ''}
      </div>
      <div class="g3-drive-tip">提示：每次仅能上传一个文件夹，且默认最多处理其中100个文件，超出部分将不会被导入。</div>`;
    openModal({
      title: '云盘资源',
      wide: true,
      body: `<div class="g3-drive">${renderView([])}</div>`,
      footer: `<div class="g3-cross-actions">
        <button class="g3-btn g3-pill g3-outline-pill" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary g3-pill" type="button" data-drive-confirm>确定</button>
      </div>`,
      onMount: backdrop => {
        backdrop.querySelector('.g3-modal').classList.add('g3-drive-modal');
        const drive = backdrop.querySelector('.g3-drive');
        const bindView = () => {
          drive.querySelector('[data-drive-shared]')?.addEventListener('click', () => {
            drive.innerHTML = renderView(['共享给我的文件夹']);
            bindView();
          });
          drive.querySelector('[data-drive-root]')?.addEventListener('click', () => {
            drive.innerHTML = renderView([]);
            bindView();
          });
        };
        bindView();
        backdrop.querySelector('[data-drive-confirm]')?.addEventListener('click', () => {
          toast('云盘导入暂未接入后端');
        });
      }
    });
  }

  function showChapterEditorModal(overview, context, focusTitle) {
    const units = overview.outline || [];
    const points = overview.knowledgePoints || [];
    const roots = units.filter(unit => !unit.parentUnitId);
    const childrenOf = parent => units.filter(unit => String(unit.parentUnitId) === String(parent.unitId));
    const pointsOf = unitId => points.filter(point => String(point.unitId) === String(unitId));
    const flat = [];
    roots.forEach((rootUnit, rootIndex) => {
      flat.push({ type: 'chapter', title: rootUnit.title, code: `0${rootIndex + 1}`, children: [] });
      const children = childrenOf(rootUnit);
      children.forEach((child, childIndex) => {
        const item = {
          type: 'section',
          title: child.title,
          code: `${rootIndex + 1}.${childIndex + 1}`,
          children: pointsOf(child.unitId).map(point => ({
            type: 'point', title: point.name, code: point.code || ''
          }))
        };
        flat.push(item);
      });
    });
    const renderTree = () => flat.map(item => {
      const indent = item.type === 'chapter' ? 0 : item.type === 'section' ? 1 : 2;
      const badge = item.type === 'chapter' ? `<span class="g3-editor-badge ch">${escapeHtml(item.code)}</span>` : '';
      const idx = item.type !== 'chapter' ? `<span class="g3-editor-idx">${escapeHtml(item.code)}</span>` : '';
      const highlight = focusTitle && item.title === focusTitle ? ' active' : '';
      return `<div class="g3-editor-node lv${indent}${highlight}">
        ${badge}${idx}<span class="g3-editor-title">${escapeHtml(item.title)}</span>
        <span class="g3-editor-ops">
          <button type="button" data-editor-op="edit" title="编辑">✎</button>
          <button type="button" data-editor-op="add" title="添加">+</button>
          <button type="button" data-editor-op="del" title="删除">🗑</button>
        </span>
      </div>`;
    }).join('');
    openModal({
      title: '编辑章节',
      wide: true,
      body: `<div class="g3-editor">
        <div class="g3-editor-head">
          <button class="g3-editor-btn" type="button" data-editor-action="undo" disabled>撤销</button>
          <button class="g3-editor-btn" type="button" data-editor-action="redo" disabled>恢复</button>
          <span class="g3-editor-head-title">${escapeHtml(context.courseTitle)}</span>
          <button class="g3-btn primary g3-pill" type="button" data-editor-action="save">保存</button>
          <button class="g3-btn g3-pill g3-outline-pill" type="button" data-editor-action="preview">预览</button>
          <button class="g3-btn g3-pill g3-outline-pill" type="button" data-modal-close>完成</button>
        </div>
        <div class="g3-editor-body">
          <div class="g3-editor-side">
            <div class="g3-editor-side-head">
              <button class="g3-editor-tool" type="button" data-editor-action="add-sibling">+ 同级目录</button>
              <button class="g3-editor-tool" type="button" data-editor-action="add-child">+ 子目录</button>
              <span class="g3-editor-more">更多</span>
            </div>
            <div class="g3-editor-tree">${renderTree()}</div>
          </div>
          <div class="g3-editor-main">
            <div class="g3-editor-toolbar">
              <select class="g3-select"><option>默认字体</option></select>
              <select class="g3-select"><option>18px</option></select>
              <button type="button" title="加粗">B</button>
              <button type="button" title="斜体">I</button>
              <button type="button" title="下划线">U</button>
              <button type="button" title="插入资料">📁</button>
            </div>
            <div class="g3-editor-canvas">
              <div class="g3-editor-placeholder">无标题 <span>✎</span> <span>+</span></div>
            </div>
          </div>
        </div>
      </div>`,
      footer: '',
      onMount: backdrop => {
        backdrop.querySelector('.g3-modal').classList.add('g3-editor-modal');
        backdrop.querySelectorAll('[data-editor-action]').forEach(button => {
          button.addEventListener('click', () => {
            if (button.disabled) return;
            toast(`“${button.textContent.trim()}”暂未接入`);
          });
        });
      }
    });
  }

  /* === 章节大纲 CRUD（第三组补充） === */

  async function promptCreateUnit(context, unitKind, parentUnitId) {
    const label = unitKind === 'CHAPTER' ? '章节标题' : '小节标题';
    const title = await openPromptModal(
      unitKind === 'CHAPTER' ? '新建章' : '新建小节',
      [
        { key: 'title', label, placeholder: '请输入标题', required: true, maxLength: 160 }
      ]);
    if (!title) return;
    try {
      await api('/education/group3/outline/units', {
        method: 'POST',
        body: {
          bindingId: context.bindingId,
          unitKind,
          title: title.title.trim(),
          parentUnitId: parentUnitId ? Number(parentUnitId) : null
        }
      });
      toast(unitKind === 'CHAPTER' ? '章节已创建' : '小节已创建');
      await reloadOverview(context);
    } catch (error) { toast(error.message, true); }
  }

  async function promptEditUnit(context, unitId, currentTitle, currentSort) {
    const result = await openPromptModal('编辑章节', [
      { key: 'title', label: '标题', default: currentTitle, required: true, maxLength: 160 },
      { key: 'sortOrder', label: '排序号', default: currentSort, type: 'number', min: 0 }
    ]);
    if (!result) return;
    try {
      await api(`/education/group3/outline/units/${unitId}`, {
        method: 'PATCH',
        body: {
          bindingId: context.bindingId,
          title: result.title.trim(),
          sortOrder: Number(result.sortOrder)
        }
      });
      toast('章节已更新');
      await reloadOverview(context);
    } catch (error) { toast(error.message, true); }
  }

  async function reorderUnit(context, unitId, currentSort, delta) {
    const newSort = Math.max(0, Number(currentSort) + delta);
    if (newSort === Number(currentSort) && delta < 0) return toast('已经在最前');
    try {
      await api(`/education/group3/outline/units/${unitId}/sort`, {
        method: 'PATCH',
        body: { bindingId: context.bindingId, sortOrder: newSort }
      });
      await reloadOverview(context);
    } catch (error) { toast(error.message, true); }
  }

  async function confirmDeleteUnit(context, unitId, title) {
    if (!await openConfirmModal(
      `确认删除 "${title || '该章节'}"？`,
      '章节下的小节和知识点也会一并删除，此操作不可恢复。')) return;
    try {
      await api(`/education/group3/outline/units/${unitId}?bindingId=${context.bindingId}`, {
        method: 'DELETE'
      });
      toast('章节已删除');
      await reloadOverview(context);
    } catch (error) { toast(error.message, true); }
  }

  async function promptCreateKnowledgePoint(context, unitId) {
    const result = await openPromptModal('新建知识点', [
      { key: 'code', label: '编码', placeholder: '如 KP001', required: true, maxLength: 60 },
      { key: 'name', label: '名称', placeholder: '请输入知识点名称', required: true, maxLength: 160 }
    ]);
    if (!result) return;
    try {
      await api('/education/group3/outline/knowledge-points', {
        method: 'POST',
        body: {
          bindingId: context.bindingId,
          code: result.code.trim(),
          name: result.name.trim(),
          unitId: unitId ? Number(unitId) : null
        }
      });
      toast('知识点已创建');
      await reloadOverview(context);
    } catch (error) { toast(error.message, true); }
  }

  async function promptEditKnowledgePoint(context, pointId, rowEl) {
    // 从 DOM 或 overview 中读当前值
    const overview = state.overview || {};
    const points = overview.knowledgePoints || [];
    const point = points.find(p => String(p.knowledgePointId) === String(pointId)) || {};
    const result = await openPromptModal('编辑知识点', [
      { key: 'code', label: '编码', default: point.code || '', required: true, maxLength: 60 },
      { key: 'name', label: '名称', default: point.name || rowEl?.dataset?.outlineTitle || '', required: true, maxLength: 160 }
    ]);
    if (!result) return;
    try {
      await api(`/education/group3/outline/knowledge-points/${pointId}`, {
        method: 'PATCH',
        body: {
          bindingId: context.bindingId,
          code: result.code.trim(),
          name: result.name.trim()
        }
      });
      toast('知识点已更新');
      await reloadOverview(context);
    } catch (error) { toast(error.message, true); }
  }

  async function confirmDeleteKnowledgePoint(context, pointId, title) {
    if (!await openConfirmModal(
      `确认删除知识点 "${title || '该知识点'}"？`,
      '此操作不可恢复。')) return;
    try {
      await api(`/education/group3/outline/knowledge-points/${pointId}?bindingId=${context.bindingId}`, {
        method: 'DELETE'
      });
      toast('知识点已删除');
      await reloadOverview(context);
    } catch (error) { toast(error.message, true); }
  }

  /* === 资料修改/归档 === */

  async function promptEditResource(context, resourceId, currentTitle) {
    const result = await openPromptModal('修改资料', [
      { key: 'title', label: '资料名称', default: currentTitle, required: true, maxLength: 200 }
    ]);
    if (!result) return;
    try {
      await api(`/education/group3/resources/${resourceId}`, {
        method: 'PATCH',
        body: { bindingId: context.bindingId, title: result.title.trim() }
      });
      toast('资料已更新');
      await reloadOverview(context);
    } catch (error) { toast(error.message, true); }
  }

  async function confirmArchiveResource(context, resourceId, title, targetStatus) {
    const actionText = targetStatus === 'ARCHIVED' ? '归档' : '取消归档';
    const bodyText = targetStatus === 'ARCHIVED'
      ? `归档后 "${title || '该资料'}" 将不再出现在默认列表中。`
      : `取消归档后 "${title || '该资料'}" 将恢复为可用状态。`;
    if (!await openConfirmModal(`确认${actionText} "${title || '该资料'}"？`, bodyText)) return;
    try {
      await api(`/education/group3/resources/${resourceId}/status`, {
        method: 'PATCH',
        body: { bindingId: context.bindingId, status: targetStatus }
      });
      toast(`资料已${actionText}`);
      await reloadOverview(context);
    } catch (error) { toast(error.message, true); }
  }

  /* === 教学班设置弹窗 === */

  async function showClassroomSettings(context) {
    let detail;
    try {
      detail = await api(`/education/group3/classrooms?bindingId=${context.bindingId}`);
    } catch (error) {
      toast(error.message, true);
      return;
    }
    const teachers = detail.teachers || [];
    const roleLabelMap = { PRIMARY: '主讲教师', CO_TEACHER: '协同教师', ASSISTANT: '助教', GUEST: '客座' };
    const teacherRows = teachers.map(t => {
      const name = t.nickName || t.userName || `用户 #${t.userId}`;
      const role = roleLabelMap[t.teacherRole] || t.teacherRole || '教师';
      const perms = [];
      if (t.canPublish) perms.push('发布');
      if (t.canGrade) perms.push('批改');
      if (t.canExport) perms.push('导出');
      return `<tr>
        <td>${escapeHtml(name)}</td>
        <td>${escapeHtml(role)}</td>
        <td>${perms.map(p => `<span class="g3-chip">${p}</span>`).join(' ') || '-'}</td>
      </tr>`;
    }).join('') || '<tr><td colspan="3" style="text-align:center;color:#888;padding:16px;">暂无任课教师记录</td></tr>';

    const fmtDate = v => v ? String(v).slice(0, 10) : '-';

    const body = `<div class="g3-classroom-info">
      <h4>课程信息</h4>
      <div class="g3-info-grid">
        <div class="g3-info-item"><span class="g3-info-label">课程名称</span><span class="g3-info-value">${escapeHtml(detail.courseTitle || '-')}</span></div>
        <div class="g3-info-item"><span class="g3-info-label">课程简介</span><span class="g3-info-value">${escapeHtml(detail.courseSummary || '-')}</span></div>
        <div class="g3-info-item"><span class="g3-info-label">教学班名称</span><span class="g3-info-value">${escapeHtml(detail.className || '-')}</span></div>
        <div class="g3-info-item"><span class="g3-info-label">教学班代码</span><span class="g3-info-value">${escapeHtml(detail.classCode || '-')}</span></div>
        <div class="g3-info-item"><span class="g3-info-label">授课学期</span><span class="g3-info-value">${escapeHtml(detail.termName || '-')}（${fmtDate(detail.termStartsOn)} ~ ${fmtDate(detail.termEndsOn)}）</span></div>
        <div class="g3-info-item"><span class="g3-info-label">开放范围</span><span class="g3-info-value">${escapeHtml(detail.openScope || '-')}</span></div>
        <div class="g3-info-item"><span class="g3-info-label">学生人数</span><span class="g3-info-value">${detail.studentCount ?? 0} 人</span></div>
        <div class="g3-info-item"><span class="g3-info-label">我的角色</span><span class="g3-info-value">${roleLabelMap[detail.teacherRole] || detail.teacherRole || '-'}</span></div>
      </div>
      <h4 style="margin-top:20px;">任课/协同教师（${teachers.length}）</h4>
      <table class="g3-table g3-classroom-table">
        <thead><tr><th>姓名</th><th>角色</th><th>权限</th></tr></thead>
        <tbody>${teacherRows}</tbody>
      </table>
    </div>`;
    openModal({ title: '教学班设置', body, footer: `<button class="g3-btn" type="button" data-modal-close>关闭</button>` });
  }

  // 辅助：刷新 overview 并重渲染整个教师工作室
  async function reloadOverview(context) {
    clearOverview();
    await loadOverview(context || {});
    renderTeacherStudio();
  }

  // 辅助：通用输入弹窗（返回用户填写的表单对象，取消返回 null）
  function openPromptModal(title, fields) {
    return new Promise(resolve => {
      const rows = fields.map(field => {
        const inputType = field.type === 'number' ? 'number' : 'text';
        const minAttr = field.min != null ? ` min="${field.min}"` : '';
        const maxAttr = field.maxLength ? ` maxlength="${field.maxLength}"` : '';
        const requiredAttr = field.required ? ' required' : '';
        return `<div class="g3-field full"><label for="prompt_${field.key}">${field.label}${field.required ? ' *' : ''}</label>
          <input id="prompt_${field.key}" class="g3-input" name="${field.key}" type="${inputType}"
            placeholder="${escapeHtml(field.placeholder || '')}"${minAttr}${maxAttr}${requiredAttr}
            value="${escapeHtml(field.default ?? '')}"></div>`;
      }).join('');
      const modal = openModal({
        title,
        body: `<form id="g3PromptForm" class="g3-form-grid">${rows}</form>`,
        footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
          <button class="g3-btn primary" type="submit" form="g3PromptForm">确定</button>`
      });
      modal.querySelector('#g3PromptForm').addEventListener('submit', event => {
        event.preventDefault();
        const form = event.currentTarget;
        const data = {};
        let valid = true;
        fields.forEach(field => {
          const el = form.elements[field.key];
          let val = el.value.trim();
          if (field.type === 'number') val = val === '' ? null : Number(val);
          if (field.required && (val === null || val === '')) {
            valid = false;
            el.focus();
            el.reportValidity();
          }
          data[field.key] = val;
        });
        if (!valid) return;
        closeModal();
        resolve(data);
      });
      const closeHandler = () => { closeModal(); resolve(null); };
      modal.querySelectorAll('[data-modal-close]').forEach(btn => btn.addEventListener('click', closeHandler));
    });
  }

  // 辅助：确认弹窗
  function openConfirmModal(title, bodyText) {
    return new Promise(resolve => {
      openModal({
        title: '确认',
        body: `<div class="g3-inline-note">${escapeHtml(title)}</div>
          <div class="g3-inline-note" style="margin-top:8px;color:#888;">${escapeHtml(bodyText)}</div>`,
        footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
          <button class="g3-btn primary" type="button" data-confirm-ok>确定</button>`,
        onMount: backdrop => {
          backdrop.querySelectorAll('[data-modal-close]').forEach(btn =>
            btn.addEventListener('click', () => { closeModal(); resolve(false); }));
          backdrop.querySelector('[data-confirm-ok]')?.addEventListener('click', () => {
            closeModal(); resolve(true);
          });
        }
      });
    });
  }

  function showChapterDetailModal(overview, context, pointId, pointTitle) {
    const points = overview.knowledgePoints || [];
    const point = points.find(p => String(p.pointId || p.name) === String(pointId)) || { name: pointTitle, code: '' };
    const parentUnit = (overview.outline || []).find(unit => String(unit.unitId) === String(point.unitId));
    const chapterTitle = parentUnit ? parentUnit.title : '章节';
    openModal({
      title: '章节详情',
      wide: true,
      body: `<div class="g3-detail">
        <div class="g3-detail-head">
          <span class="g3-detail-title">${escapeHtml(point.name)}</span>
          <div class="g3-detail-tags">
            <span class="g3-detail-tag blue">1 学习目标</span>
            <span class="g3-detail-tag">2 学习课件</span>
          </div>
        </div>
        <div class="g3-detail-body">
          <p>1、能够结合现实场景的问题处理需要，解释说明数据、数据元素、数据对象、数据类型等概念或术语。</p>
          <p>2、能够列举并描述线性、层次、图状等现实经典案例场景。</p>
          <p>3、能够理解计算机内存的数据存取原理，并结合特定场景正确描述顺序和链式两种存储形态和技术实现。</p>
        </div>
        <div class="g3-detail-foot">
          <button class="g3-btn g3-pill g3-outline-pill" type="button" data-detail-prev>上一节</button>
          <button class="g3-btn primary g3-pill" type="button" data-detail-next>下一节</button>
        </div>
      </div>`,
      footer: `<div class="g3-detail-side">
        <div class="g3-detail-side-head">目录</div>
        <div class="g3-detail-side-tree">
          ${(overview.outline || []).filter(u => !u.parentUnitId).map((root, i) => {
            const kids = (overview.outline || []).filter(u => String(u.parentUnitId) === String(root.unitId));
            return `<div class="g3-detail-side-node">
              <div class="g3-detail-side-ch">⌄ ${i + 1} ${escapeHtml(root.title)}</div>
              ${kids.map((kid, j) => `<div class="g3-detail-side-se">${i + 1}.${j + 1} ${escapeHtml(kid.title)}</div>`).join('')}
            </div>`;
          }).join('')}
        </div>
      </div>`,
      onMount: backdrop => {
        backdrop.querySelector('.g3-modal').classList.add('g3-detail-modal');
        backdrop.querySelector('[data-detail-prev]')?.addEventListener('click', () => toast('上一节暂未接入'));
        backdrop.querySelector('[data-detail-next]')?.addEventListener('click', () => toast('下一节暂未接入'));
      }
    });
  }

  function showNewFolderModal(context) {
    const classNames = [...new Set((state.contexts || [])
      .filter(item => !context.courseSpaceId || item.courseSpaceId === context.courseSpaceId)
      .map(item => item.className)
      .filter(Boolean))];
    if (!classNames.length && context.className) classNames.push(context.className);
    openModal({
      title: '新建文件夹',
      body: `<form id="g3FolderForm" class="g3-folder-form">
        <input id="g3FolderName" class="g3-input g3-folder-name" name="name" value="新建文件夹"
          maxlength="100" required aria-label="文件夹名称">
        <div>
          <div class="g3-folder-section-title">共享给教学团队</div>
          <div class="g3-folder-hint">选择可见教师</div>
          <div class="g3-folder-member-panel">
            <label class="g3-folder-member"><input type="checkbox" checked data-folder-all><span>全部成员</span></label>
            <div class="g3-folder-member-empty">暂无可选教学团队成员</div>
          </div>
        </div>
        <div>
          <div class="g3-folder-section-title">共享给班级</div>
          <div class="g3-folder-hint">选择可见班级</div>
          <div class="g3-folder-visibility">
            <label><input type="radio" name="folderVisibility" value="PUBLIC" checked>
              <span><strong>公开</strong><small>课程下所有班级可见</small></span></label>
            <label><input type="radio" name="folderVisibility" value="PRIVATE">
              <span><strong>私密</strong><small>课程下所有班级不可见</small></span></label>
            <label><input type="radio" name="folderVisibility" value="PARTIAL">
              <span><strong>部分可见</strong><small>选中的班级可见，可开放给您管理的班级。若班级管理权限变更，会提示您调整可见范围</small></span></label>
          </div>
          <div class="g3-folder-classes" hidden>
            ${classNames.map(name => `<label class="g3-folder-member">
              <input type="checkbox" checked value="${escapeHtml(name)}"><span>${escapeHtml(name)}</span></label>`).join('')}
          </div>
        </div>
      </form>`,
      footer: `<button class="g3-btn g3-pill g3-outline-pill" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary g3-pill" type="submit" form="g3FolderForm">确定</button>`,
      onMount: backdrop => {
        backdrop.querySelector('.g3-modal').classList.add('g3-folder-modal');
        const name = backdrop.querySelector('#g3FolderName');
        name.focus();
        name.select();
        const classes = backdrop.querySelector('.g3-folder-classes');
        backdrop.querySelectorAll('input[name="folderVisibility"]').forEach(radio => {
          radio.addEventListener('change', () => {
            classes.hidden = radio.value !== 'PARTIAL' || !radio.checked;
          });
        });
        backdrop.querySelector('#g3FolderForm').addEventListener('submit', event => {
          event.preventDefault();
          const folderName = name.value.trim();
          if (!folderName) {
            name.focus();
            return;
          }
          const visibility = backdrop.querySelector('input[name="folderVisibility"]:checked')?.value || 'PUBLIC';
          const folders = readFolders(context);
          folders.push({
            id: `folder-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            name: folderName,
            visibility,
            demo: false,
            resourceIds: [],
            createdAt: new Date().toISOString()
          });
          writeFolders(context, folders);
          state.currentFolderId = null;
          closeModal();
          toast('文件夹已创建');
          renderTeacherStudio();
        });
      }
    });
  }

  function showUploadModal(overview, context) {
    const units = overview.outline || [];
    // 生成章节/小节下拉选项
    const unitOptions = [
      '<option value="">不归档章节</option>',
      ...units.filter(u => u.unitKind === 'CHAPTER').map(chapter => {
        const children = units.filter(u => String(u.parentUnitId) === String(chapter.unitId) && u.unitKind === 'SECTION');
        const chapterOpt = `<option value="${escapeHtml(chapter.unitId)}">${escapeHtml(chapter.title)}</option>`;
        const sectionOpts = children.map(sec =>
          `<option value="${escapeHtml(sec.unitId)}">　├ ${escapeHtml(chapter.title)} / ${escapeHtml(sec.title)}</option>`).join('');
        return chapterOpt + sectionOpts;
      })
    ].join('');

    const modal = openModal({
      title: '本地上传',
      body: `<form id="g3UploadForm">
        <div class="g3-upload-notes">
          <div class="g3-upload-notes-title">注：</div>
          <ol class="g3-upload-notes-list">
            <li>文件数量：每次最多50个文件，超出部分将不会被导入。</li>
            <li>文件大小：最大支持1g文件，超出大小文件将上传失败，建议通过云盘上传。</li>
            <li>文件名称：名称不能包含特殊字符: \`~!@#$%^&amp;*+&lt;&gt;?:&quot;{}\\/\`</li>
          </ol>
        </div>
        <div class="g3-upload-form-row">
          <label for="g3UploadUnit">归档章节</label>
          <select id="g3UploadUnit" class="g3-select">${unitOptions}</select>
        </div>
        <div class="g3-upload-dropzone" id="g3UploadDropzone">
          <input id="g3UploadFile" name="file" type="file" multiple hidden
            accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md">
          <input id="g3UploadFolder" name="folder" type="file" webkitdirectory multiple hidden>
          <div class="g3-upload-dropzone-text">将文件拖拽至此区域</div>
          <div class="g3-upload-dropzone-or">-或-</div>
          <button type="button" class="g3-btn primary g3-pill" id="g3SelectFileBtn">选择本地文件</button>
          <button type="button" class="g3-btn primary g3-pill" id="g3SelectFolderBtn">选择本地文件夹</button>
          <div class="g3-upload-filelist" id="g3UploadFileList"></div>
        </div>
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3UploadForm">开始上传</button>`
    });
    const fileInput = modal.querySelector('#g3UploadFile');
    const folderInput = modal.querySelector('#g3UploadFolder');
    const fileListEl = modal.querySelector('#g3UploadFileList');
    const dropzone = modal.querySelector('#g3UploadDropzone');
    let selectedFiles = [];

    function renderFileList() {
      if (!selectedFiles.length) {
        fileListEl.innerHTML = '';
        return;
      }
      fileListEl.innerHTML = selectedFiles.map((file, index) =>
        `<div class="g3-upload-file-item">
          <span class="g3-upload-file-name" title="${escapeHtml(file.name)}">${escapeHtml(file.name)}</span>
          <span class="g3-upload-file-size">${formatFileSize(file.size)}</span>
          <button type="button" class="g3-upload-file-remove" data-index="${index}" aria-label="移除">×</button>
        </div>`).join('');
    }

    modal.querySelector('#g3SelectFileBtn').addEventListener('click', () => fileInput.click());
    modal.querySelector('#g3SelectFolderBtn').addEventListener('click', () => folderInput.click());

    fileInput.addEventListener('change', () => {
      selectedFiles = Array.from(fileInput.files);
      renderFileList();
    });
    folderInput.addEventListener('change', () => {
      selectedFiles = Array.from(folderInput.files);
      renderFileList();
    });

    ['dragenter', 'dragover'].forEach(type => {
      dropzone.addEventListener(type, event => {
        event.preventDefault();
        dropzone.classList.add('drag-over');
      });
    });
    ['dragleave', 'drop'].forEach(type => {
      dropzone.addEventListener(type, event => {
        event.preventDefault();
        dropzone.classList.remove('drag-over');
      });
    });
    dropzone.addEventListener('drop', event => {
      const files = Array.from(event.dataTransfer.files || []);
      if (files.length) {
        selectedFiles = files;
        renderFileList();
      }
    });

    fileListEl.addEventListener('click', event => {
      const btn = event.target.closest('.g3-upload-file-remove');
      if (!btn) return;
      selectedFiles.splice(Number(btn.dataset.index), 1);
      renderFileList();
    });

    modal.querySelector('#g3UploadForm').addEventListener('submit', async event => {
      event.preventDefault();
      const button = modal.querySelector('.g3-modal-foot .primary');
      const unitIdEl = modal.querySelector('#g3UploadUnit');
      if (!selectedFiles.length) return toast('请选择要上传的文件', true);
      setBusy(button, true, '上传处理中…');
      try {
        for (const file of selectedFiles) {
          const data = new FormData();
          data.append('bindingId', state.bindingId);
          data.append('title', file.name.replace(/\.[^.]+$/, ''));
          if (unitIdEl?.value) data.append('unitId', unitIdEl.value);
          data.append('file', file);
          await api('/education/group3/resources', { method: 'POST', body: data });
        }
        closeModal();
        await reloadOverview(context);
        toast(`已上传 ${selectedFiles.length} 个文件`);
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
        <div class="g3-field full"><label>新文件</label>
          <label class="g3-file-input" id="g3VersionFileBox">
            <input id="g3VersionFile" name="file" type="file" required
              accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md">
            <span class="g3-file-input-icon" id="g3VersionFileIcon">📄</span>
            <span class="g3-file-input-label" id="g3VersionFileLabel">点击选择要上传的新文件</span>
          </label>
          <span class="g3-file-input-hint">支持格式：.pptx、.pdf、.png/.jpg/.gif/.webp、.mp4/.mov/.webm、.docx/.txt/.md</span>
        </div>
        <div class="g3-field full"><div class="g3-inline-note warn">新版本不会改写已发布版本；需要重新选择该版本发布。</div></div>
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3VersionForm">保存新版本</button>`
    });
    const fileInput = modal.querySelector('#g3VersionFile');
    const fileBox = modal.querySelector('#g3VersionFileBox');
    const fileLabel = modal.querySelector('#g3VersionFileLabel');
    const fileIcon = modal.querySelector('#g3VersionFileIcon');
    const fileIconMap = { pdf: '📕', pptx: '📊', docx: '📘', txt: '📝', md: '📝', mp4: '🎬', mov: '🎬', webm: '🎬', png: '🖼️', jpg: '🖼️', jpeg: '🖼️', gif: '🖼️', webp: '🖼️' };
    fileInput.addEventListener('change', () => {
      const file = fileInput.files[0];
      if (file) {
        const ext = (file.name.split('.').pop() || '').toLowerCase();
        fileBox.classList.add('has-file');
        fileIcon.textContent = fileIconMap[ext] || '📄';
        fileLabel.textContent = `${file.name} (${formatFileSize(file.size)})`;
      } else {
        fileBox.classList.remove('has-file');
        fileIcon.textContent = '📄';
        fileLabel.textContent = '点击选择要上传的新文件';
      }
    });
    modal.querySelector('#g3VersionForm').addEventListener('submit', async event => {
      event.preventDefault();
      const button = modal.querySelector('.g3-modal-foot .primary');
      const file = event.currentTarget.file.files[0];
      if (!file) return toast('请选择要上传的文件', true);
      const data = new FormData();
      data.append('bindingId', state.bindingId);
      data.append('file', file);
      setBusy(button, true, '上传处理中…');
      try {
        const result = await api(`/education/group3/resources/${resourceId}/versions`, { method: 'POST', body: data });
        closeModal();
        clearOverview();
        toast(result.processingStatus === 'READY' ? '新版本已保存' : result.processingMessage);
        refreshRoute();
      } catch (error) {
        toast(error.message, true);
        setBusy(button, false);
      }
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
    async function revokeRelease(releaseId, title, context) {
    if (!window.confirm(`确认撤回“${title}”在 ${context ? context.className : '当前教学班'} 的发布？撤回后学生旧链接立即失效。`)) return;
    try {
      await api(`/education/group3/releases/${releaseId}/revoke?bindingId=${encodeURIComponent(state.bindingId)}`, {
        method: 'POST', body: { reason: '教师从课前时间轴撤回' }
      });
      clearOverview(); state.selectedKey = '';
      toast('资料已撤回，学生端不再可见');
      refreshRoute();
    } catch (error) { toast(actionableMessage(error), true); }
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
  function showQuestionPreviewModal(questions, startIndex) {
    if (!questions.length) return;
    let index = Math.min(Math.max(startIndex, 0), questions.length - 1);
    openModal({
      title: '题目预览',
      wide: true,
      body: `<div class="g3-qpreview">
        <div class="g3-qpreview-head">
          <span class="g3-qpreview-title">题目预览</span>
          <div class="g3-qpreview-nav">
            <button type="button" data-qpreview-prev>上一题</button>
            <button class="primary" type="button" data-qpreview-next>下一题</button>
          </div>
        </div>
        <div class="g3-qpreview-body">
          <article class="g3-qpreview-card" data-qpreview-card></article>
        </div>
      </div>`,
      onMount: backdrop => {
        backdrop.querySelector('.g3-modal').classList.add('g3-question-preview-modal');
        const card = backdrop.querySelector('[data-qpreview-card]');
        const prev = backdrop.querySelector('[data-qpreview-prev]');
        const next = backdrop.querySelector('[data-qpreview-next]');
        const render = () => {
          const question = questions[index];
          const answer = formatAnswer(question.answer, question.questionKind);
          const analysis = (question.answer && (question.answer.analysis || question.answer.explanation)) || '';
          card.innerHTML = `<div class="g3-qpreview-kind">（${escapeHtml(labels[question.questionKind] || question.questionKind)}）</div>
            <div class="g3-qpreview-stem">${escapeHtml(question.stem || question.title)}</div>
            <div class="g3-qpreview-answer">
              <span class="g3-qpreview-answer-label">答案：</span>
              <span>${escapeHtml(answer)}</span>
            </div>
            <div class="g3-qpreview-section">答案解析:</div>
            <div class="g3-qpreview-analysis">${escapeHtml(analysis || '暂无答案解析')}</div>
            <div class="g3-qpreview-meta">难易：${escapeHtml(question.difficulty || '-')}</div>
            <div class="g3-qpreview-meta">知识点：${escapeHtml(question.knowledgePoints || '未关联知识点')}</div>`;
          prev.disabled = index <= 0;
          next.disabled = index >= questions.length - 1;
        };
        prev.addEventListener('click', () => {
          if (index > 0) {
            index -= 1;
            render();
          }
        });
        next.addEventListener('click', () => {
          if (index < questions.length - 1) {
            index += 1;
            render();
          }
        });
        render();
      }
    });
  }

  function showQuestionLibraryModal(context) {
    openModal({
      title: '题库教学资源库',
      wide: true,
      body: `<div class="g3-qlib">
        <div class="g3-qlib-head">
          <button class="g3-qlib-back" type="button" data-action="qlib-back">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              stroke-width="2" aria-hidden="true"><path d="m15 5-7 7 7 7"/></svg>返回</button>
          <div class="g3-qlib-tabs">
            <button class="active" type="button" data-qlib-tab="school">本校资源库</button>
            <button type="button" data-qlib-tab="teaching">教学资源库</button>
          </div>
        </div>
        <div class="g3-qlib-filterbar">
          <label class="g3-qlib-filter">专业：<select class="g3-input" aria-label="选择专业"><option>选择专业</option></select></label>
          <label class="g3-qlib-filter">所属课程：<select class="g3-input" aria-label="所属课程"><option>所属课程</option></select></label>
          <div class="g3-search g3-qlib-search">
            <input class="g3-input" type="search" placeholder="搜索" aria-label="搜索试题">
            <span class="g3-search-icon" aria-hidden="true">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                stroke-width="1.8"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
            </span>
          </div>
        </div>
        <div class="g3-qlib-main">
          <aside class="g3-qlib-left">
            <div class="g3-search">
              <input class="g3-input" type="search" placeholder="请输入关键词" aria-label="关键词搜索">
              <span class="g3-search-icon" aria-hidden="true">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                  stroke-width="1.8"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
              </span>
            </div>
          </aside>
          <section class="g3-qlib-right">
            <div class="g3-qlib-row">
              <span class="g3-qlib-label">类型</span>
              <button class="g3-qlib-pill active" type="button" data-qlib-type>全部</button>
              <button class="g3-qlib-more" type="button" data-action="qlib-more">更多</button>
            </div>
            <div class="g3-qlib-row">
              <span class="g3-qlib-label">排序</span>
              <button class="g3-qlib-pill active" type="button" data-qlib-sort>综合排序</button>
              <button class="g3-qlib-plain" type="button" data-qlib-sort>创建时间</button>
            </div>
            <table class="g3-table g3-qlib-table">
              <thead><tr>
                <th class="g3-check-col"><input type="checkbox" aria-label="全选"></th>
                <th>试题名</th><th>题型</th><th>难易度</th><th>上传人</th><th>创建时间</th><th>所属课程</th>
              </tr></thead>
              <tbody><tr><td colspan="7"><div class="g3-qlib-empty">暂无可选试题</div></td></tr></tbody>
            </table>
          </section>
        </div>
      </div>`,
      footer: `<div class="g3-qlib-foot">
        <span class="g3-qlib-selected">已选 <b data-qlib-count>0</b> 个</span>
        <button class="g3-btn primary g3-pill" type="button" data-action="qlib-confirm">确定</button>
      </div>`,
      onMount: backdrop => {
        backdrop.querySelector('.g3-modal').classList.add('g3-question-library-modal');
        backdrop.querySelector('[data-action="qlib-back"]')?.addEventListener('click', closeModal);
        backdrop.querySelectorAll('[data-qlib-tab]').forEach(tab => {
          tab.addEventListener('click', () => {
            backdrop.querySelectorAll('[data-qlib-tab]')
              .forEach(node => node.classList.toggle('active', node === tab));
          });
        });
        backdrop.querySelectorAll('[data-qlib-type]').forEach(pill => {
          pill.addEventListener('click', () => {
            backdrop.querySelectorAll('[data-qlib-type]')
              .forEach(node => node.classList.toggle('active', node === pill));
          });
        });
        backdrop.querySelectorAll('[data-qlib-sort]').forEach(button => {
          button.addEventListener('click', () => {
            backdrop.querySelectorAll('[data-qlib-sort]')
              .forEach(node => node.classList.toggle('active', node === button));
          });
        });
        backdrop.querySelector('[data-action="qlib-more"]')?.addEventListener('click', () => toast('更多题型暂未接入'));
        backdrop.querySelector('[data-action="qlib-confirm"]')?.addEventListener('click', () => {
          closeModal();
          toast('暂无可选试题');
        });
      }
    });
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
        <div class="g3-field full" data-rubric-field>
          <label for="g3QuestionRubric">评分标准（简答题）</label>
          <textarea id="g3QuestionRubric" class="g3-textarea" name="scoringRubric"
            placeholder="请输入评分标准，例如：回答包含 3 个要点各得 3 分；语句通顺 1 分；..."></textarea>
          <div class="g3-help">仅供简答题使用。设置评分细则后，教师可据此进行主观题评分。</div>
        </div>
        <div class="g3-field full"><label>关联知识点</label><div class="g3-check-list">${knowledgeMarkup(overview)}</div></div>
      </form>`,
      footer: `<button class="g3-btn" type="button" data-modal-close>取消</button>
        <button class="g3-btn primary" type="submit" form="g3QuestionForm">保存题目</button>`
    });
    const form = modal.querySelector('#g3QuestionForm');
    const kind = form.kind;
    const optionsField = form.querySelector('[data-options-field]');
    const answerHelp = form.querySelector('[data-answer-help]');
    const rubricField = form.querySelector('[data-rubric-field]');
    const syncKind = () => {
      const value = kind.value;
      optionsField.hidden = value === 'TRUE_FALSE' || value === 'SHORT_TEXT';
      rubricField.hidden = value !== 'SHORT_TEXT';
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
        scoringRubric: selectedKind === 'SHORT_TEXT' ? form.scoringRubric.value.trim() : null,
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

  /* === 课前准备：时间轴工作台（第三组） === */

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

  function statePill(value) {
    return `<span class="g3-tl-state s-${escapeHtml(String(value || '').toLowerCase())}">`
      + `${escapeHtml(STATE_LABELS[value] || value || '未知')}</span>`;
  }

  // 教师一天可能给多个班上不同课；总览顶部把「课程 · 教学班」并列出来，切换即换一堂课。
  function contextChips(context) {
    if (!state.contexts || state.contexts.length < 2) return '';
    return `<div class="g3-context-chips"><span>按课堂切换</span>${state.contexts.map(item =>
      `<button type="button" class="g3-chip${String(item.bindingId) === String(context.bindingId) ? ' active' : ''}"
        data-act="switch-context" data-binding="${escapeHtml(item.bindingId)}">
        ${escapeHtml(item.courseTitle)} · ${escapeHtml(item.className)}</button>`).join('')}</div>`;
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
    const enterLabel = node.kind === 'QUIZ'
      ? (role === 'student' ? (node.meta.submitted ? '查看作答' : '进入作答') : '查看环节')
      : node.kind === 'MATERIAL' ? (role === 'student' ? '进入阅读' : '查看环节')
        : node.kind === 'DRAFT' ? '处理此环节' : '查看环节';
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
      ? renderTimeline(visible, state.selectedKey, overview.serverTime, '点击右上角「添加环节」把资料或习题放到时间轴上')
      : renderTimelineList(visible, state.selectedKey);
    root.innerHTML = `<div class="g3-workspace">
      ${pageHead('课前准备', `${context.courseTitle} · ${context.className}`, context,
        '环节按「课程 / 教学班 / 上课日期」分成一堂堂课，每堂课内部从 1 重新编号；切换上方课堂或教学班即可编排另一堂课。')}
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
          <span class="g3-help">按开放时间排列；点任意环节进入独立编辑页</span>
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
    root.querySelector('[data-action="classroom-settings"]')?.addEventListener('click', () => {
      showClassroomSettings(context);
    });
    applyStageView();
  }

  function renderTeacherDetail() {
    const node = state.nodeIndex[state.selectedKey];
    if (!node) {
      return `<div class="g3-panel"><div class="g3-panel-head"><div class="g3-panel-title">环节详情</div></div>
        <div class="g3-panel-body">${emptyState('选择一个环节', '点击时间轴上的卡片，查看内容、发布范围、完成情况和可执行操作')}</div></div>`;
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
      + `${node.seq ? `打开第 ${node.seq} 环节编辑页` : '打开环节编辑页'}</button>`);
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
      ? `<div class="g3-inline-note warn"><strong>教师私有备注</strong><br>${escapeHtml(meta.privateNotes)}<br>
          该内容只保存在备课包中，不会推送到学生时间轴。</div>` : '';
    const failure = node.state === 'FAILED'
      ? `<div class="g3-inline-note warn"><strong>处理结果：${escapeHtml(meta.processingMessage || '资料转换失败')}</strong><br>
          此版本不会对学生开放。可上传新版本替换，或检查文件后重新上传。</div>` : '';
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
        <div class="g3-help">发布范围由页面顶部「课程 / 教学班」决定；开放、截止与解析公布均以服务端时间为准。</div>
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
      location.href = `?g=${routes.prepare.g}&i=${routes.prepare.i}#create=${encodeURIComponent(flow)}`;
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
      body: `<p class="g3-help">先选环节类型，再进入独立创建页分步填写。课程资源库与题库里已有的内容都可以直接选用，不需要重新上传或重新录入。</p>
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
          </div>
          <div class="g3-inline-note">两条路径都写入同一类真实发布记录（上传走 /resources，复用走 /releases），学生端在时间轴上看到的资料环节完全一致。</div>`
      },
      {
        label: '挑选资料', hint: '已有版本 / 新文件',
        intro: '可勾选多个资料版本，每个版本生成一个独立资料环节；也能按关键词快速找到目标资料。',
        body: () => `<div data-source-panel="existing">
            <div class="g3-picker-lead"><strong>课程资源库 · 已处理完成的资料版本</strong>
              <span>${ready.length} 个可复用版本</span></div>
            ${materialPicker(overview, data, { name: 'materialVersionIds' })}
            <div class="g3-inline-note">这里只列出服务端标记为 READY 的版本；发布时直接引用版本 ID，不会重新上传文件。</div>
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
            <div class="g3-inline-note">上传后先显示真实处理结果：成功才按开放窗口发布；转换失败会留在「待编排」并给出可执行的修复提示，不会对学生开放。</div>
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
          <div class="g3-inline-note">名称与说明会用于本批次生成的每一个独立环节；多题时会自动追加「第 N / M 题」以便学生区分。</div>`
      },
      {
        label: '从题库导入题目', hint: '直接选用已有题目版本',
        intro: '题库里的题目直接勾选即可，不需要重新录入；可以按名称、题干或题型搜索。',
        body: () => `<div class="g3-picker-lead"><strong>课程题库 · 可直接导入的题目版本</strong>
            <span>${questions.length} 个可用版本</span>
            <button class="g3-btn quiet" type="button" data-picker-new-question>+ 题库里没有？新建题目</button></div>
          ${questionPicker(overview, data, { multiple: true, name: 'questionVersionIds' })}
          <div class="g3-inline-note warn">发布接口每条预习任务只能绑定一道题：勾选多道题时，会按「每题一个独立环节」依次发布，共享同一开放窗口与导学说明；未勾选任何题目时为纯资料导学环节。这是真实接口行为，页面不会伪造一个包含多道题的任务。</div>`
      },
      {
        label: '同步开放资料', hint: '从资源库直接选用',
        intro: '预习需要配套课件或导学案时，直接从课程资源库勾选已有版本，不会重新上传文件。',
        body: () => `<div class="g3-picker-lead"><strong>课程资源库 · 同步开放的资料</strong>
            <span>${materials.length} 个可用版本</span></div>
          ${materialPicker(overview, data, { name: 'materialVersionIds' })}
          <div class="g3-inline-note">勾选的资料按同一开放窗口按班发布；多题批次只在第一个环节发布资料，避免重复发布记录。答案与解析在公布时间到达前不会返回给学生。</div>`
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

  // 环节不是独立实体，删除 = 归档它对应的真实业务对象（后端 /stages/remove）。
  function stageRemoveTarget(node) {
    if (node.kind === 'QUIZ') return { kind: 'QUIZ', id: node.id };
    if (node.kind === 'PLAN') return { kind: 'PLAN', id: node.id };
    return { kind: node.kind === 'DRAFT' ? 'DRAFT' : 'MATERIAL', id: node.meta.resourceId || node.id };
  }

  function confirmRemoveStage(node) {
    const target = stageRemoveTarget(node);
    if (!target.id) return toast('该环节没有可移除的业务记录', true);
    const scope = node.groupLabel || '当前课堂';
    const extra = target.kind === 'MATERIAL' || target.kind === 'DRAFT'
      ? '资料本体及其版本会一并从「课程建设」归档；' : '';
    const modal = openModal({
      title: '删除环节',
      body: `<div class="g3-list">
        <div class="g3-detail-title">${escapeHtml(node.title)}</div>
        <div class="g3-inline-note warn"><strong>删除后学生立即不可见，本页不提供恢复</strong><br>
          该环节会从「${escapeHtml(scope)}」移除；${extra}对应业务记录仅做归档（软删除），
          学生已提交的作答与成绩完整保留。删除后本堂课剩余环节会重新编号。</div>
        <div class="g3-help">类型：${escapeHtml((KINDS[node.kind] || KINDS.MATERIAL).label)}
          · 记录 ID：${escapeHtml(target.id)}</div>
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

  function handleRevoked(error) {
    clearObjectUrls();
    clearStageUrls();
    closeModal();
    state.selectedPreviewId = null;
    clearStageHash();
    toast('权限已变更或资料已撤回，已关闭预览并重新校验时间轴', true);
    refreshRoute();
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
    const pageName = role === 'teacher' ? '课前准备' : '课前学习';
    root.innerHTML = `<div class="g3-stage-page">
      <div class="g3-stage-topbar">
        <button class="g3-btn" type="button" data-act="stage-back">← 返回${pageName}总览</button>
        <div class="g3-stage-crumb">${escapeHtml(node.groupLabel
          || (context ? `${context.courseTitle} · ${context.className}` : '本次课前学习'))}
          ${node.seq ? ` · 第 ${node.seq} / ${node.total} 环节` : ' · 教师私有 / 待编排环节'}</div>
        <div class="g3-stage-counter">${node.seq ? `<strong>${node.seq}</strong> / ${node.total}` : '—'}</div>
      </div>
      <div class="g3-stage-head ${kind.tone}">
        <div class="g3-stage-head-line">
          <span class="g3-tl-kind ${kind.tone}"><i>${kind.icon}</i>${escapeHtml(kind.label)}</span>
          ${seqBadge(node)} ${statePill(node.state)}
          ${node.private ? '<span class="g3-tl-private">仅教师可见 · 不进入学生时间轴</span>' : ''}
        </div>
        <h1>${escapeHtml(node.title)}</h1>
        ${node.subtitle ? `<p>${escapeHtml(node.subtitle)}</p>` : ''}
        <div class="g3-stage-meta">
          <span>开放：${escapeHtml(formatTime(node.opensAt))}</span>
          <span>截止：${escapeHtml(formatTime(node.closesAt))}</span>
          ${node.kind === 'QUIZ' ? `<span>题目：${Number(node.meta.itemCount || 0)} 道</span>` : ''}
          ${node.kind === 'MATERIAL' ? `<span>版本：V${escapeHtml(node.meta.versionNo || 1)} · ${Number(node.meta.pageCount || 0)} 页</span>` : ''}
        </div>
      </div>
      ${renderStageTrack(node.key, node.groupKey)}
      <div class="g3-stage-body" id="g3StageBody">${loadingState('正在载入本环节内容')}</div>
      <div class="g3-stage-footer">
        <button class="g3-btn" type="button" data-act="stage-prev" ${prev ? '' : 'disabled'}>
          ← ${prev ? `上一环节 ${prev.seq}：${escapeHtml(prev.title)}` : '已是本堂课第一个环节'}</button>
        <button class="g3-btn" type="button" data-act="stage-back">环节总览</button>
        <button class="g3-btn primary" type="button" data-act="stage-next" ${next ? '' : 'disabled'}>
          ${next ? `下一环节 ${next.seq}：${escapeHtml(next.title)}` : '已是本堂课最后一个环节'} →</button>
      </div>
      <div class="g3-help" id="g3StageHint">${index < 0
        ? '教师私有 / 待编排环节不占学生序号，可通过上方环节条直接进入其它环节。'
        : '环节按「课程 / 教学班 / 日期」分组，上下环节只在同一堂课内切换；其它堂课请回总览选择。'}</div>
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
        canvas.innerHTML = emptyState('该资料没有分页预览',
          '可直接下载原文件阅读；阅读完用下方「下一环节」继续，或用上方序号跳到其它环节',
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

  function stageTeacherBody(node, container) {
    const meta = node.meta || {};
    const rows = [];
    rows.push(['环节序号', node.seq ? `第 ${node.seq} / ${node.total} 环节` : '不计入序号（教师私有）']);
    rows.push(['发布范围', '由页面顶部「课程 / 教学班」决定，仅本班可见']);
    rows.push(['开放 / 截止', `${formatTime(node.opensAt)} — ${formatTime(node.closesAt)}`]);
    if (node.kind === 'QUIZ') {
      rows.push(['解析公布', meta.answerOpensAt ? formatTime(meta.answerOpensAt) : '未设置']);
      rows.push(['提交进度', `${Number(meta.submissionCount || 0)} / ${Number(meta.targetCount || 0)} 人`]);
      rows.push(['题目数量', `${Number(meta.itemCount || 0)} 道`]);
    }
    if (node.kind === 'MATERIAL' || node.kind === 'DRAFT') {
      rows.push(['资料版本', `V${meta.versionNo || 1}`]);
      rows.push(['处理状态', labels[String(meta.processingStatus || '').toUpperCase()] || meta.processingStatus || '未知']);
      rows.push(['处理结果', meta.processingMessage || '—']);
    }
    if (node.kind === 'PLAN') {
      rows.push(['关联课次', meta.lessonTitle || '未关联']);
      rows.push(['备课材料', `${(meta.materials || []).length} 份`]);
      rows.push(['活动预设', `${(meta.templates || []).length} 项`]);
    }
    const actions = [];
    const button = (act, label, style = '') =>
      `<button class="g3-btn ${style}" type="button" data-act="${act}" data-key="${escapeHtml(node.key)}">${label}</button>`;
    if (node.kind === 'MATERIAL') {
      actions.push(button('stage-preview', '预览资料'), button('stage-versions', '版本记录'),
        button('stage-new-version', '上传新版本'), button('stage-revoke', '撤回发布', 'danger'));
    } else if (node.kind === 'DRAFT') {
      if (node.state === 'FAILED') {
        actions.push(button('stage-failure', '查看失败原因与下一步', 'primary'), button('stage-new-version', '上传新版本替换'));
      } else {
        actions.push(button('stage-release', '设置开放时间并发布', 'primary'), button('stage-preview', '预览资料'));
      }
    } else if (node.kind === 'QUIZ') {
      actions.push('<button class="g3-btn primary" type="button" data-act="add-stage">继续发布预习</button>');
    } else {
      actions.push('<button class="g3-btn primary" type="button" data-act="gen-plan">新建备课包</button>');
    }
    actions.push(button('stage-remove', '删除环节', 'danger'));
    let content = '';
    if (node.kind === 'QUIZ') {
      const stems = state.teacherTasks.filter(row => String(row.assignmentId) === String(node.id));
      content = stems.length ? `<div class="g3-stage-subsection"><h3>本环节题目</h3>
        ${stems.map((row, index) => `<div class="g3-question"><div class="g3-question-stem">
          <span class="g3-q-no">${index + 1}</span>${escapeHtml(row.stem || row.title || '题目')}</div></div>`).join('')}</div>`
        : '<div class="g3-help">该任务未返回题目明细，可在「课程建设」题库中查看版本。</div>';
    } else if (node.kind === 'PLAN') {
      content = `<div class="g3-stage-subsection"><h3>备课包内容（仅教师可见）</h3>
        <div class="g3-help">材料：${(meta.materials || []).map(item => escapeHtml(item.title || '未命名')).join('、') || '暂无'}</div>
        ${meta.privateNotes ? `<div class="g3-inline-note warn"><strong>私有备注</strong><br>${escapeHtml(meta.privateNotes)}</div>` : ''}</div>`;
    } else if (node.kind === 'MATERIAL') {
      content = `<div class="g3-stage-subsection"><h3>资料内容</h3>
        <div class="g3-reader-canvas" id="g3TeacherReader">${loadingState('正在读取预览页')}</div></div>`;
    } else if (node.state === 'FAILED') {
      content = `<div class="g3-inline-note warn"><strong>处理失败：${escapeHtml(meta.processingMessage || '资料转换失败')}</strong><br>
        该环节不会对学生开放。上传新版本替换后，再设置开放时间发布；学生端序号不会因失败草稿而跳号。</div>`;
    }
    container.innerHTML = `<div class="g3-stage-editor">
      <div class="g3-stage-section-head"><h2>环节编辑</h2><span>${escapeHtml(KINDS[node.kind].label)} · 内容来自既有业务记录</span></div>
      <div class="g3-stage-editor-grid">
        <section class="g3-panel"><div class="g3-panel-head"><div class="g3-panel-title">配置与状态</div>${statePill(node.state)}</div>
          <div class="g3-panel-body g3-list">${rows.map(([label, value]) =>
            `<div class="g3-detail-row"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`).join('')}
            <div class="g3-tl-tags">${nodeTags(node)}</div></div></section>
        <section class="g3-panel"><div class="g3-panel-head"><div class="g3-panel-title">可执行操作</div></div>
          <div class="g3-panel-body g3-list"><div class="g3-actions">${actions.join('')}</div>
            <div class="g3-help">资料可撤回后重新发布以调整开放时间；预习任务与备课包目前没有编辑 / 撤回接口，
              需要调整时间或删除时请新建环节，并在后端补齐契约。</div></div></section>
      </div>
      ${content}
    </div>`;
    if (node.kind === 'MATERIAL') {
      const canvas = container.querySelector('#g3TeacherReader');
      if (canvas) fillReaderCanvas(node, canvas);
    }
  }

  /* === 课前准备：事件委托（仅教师端） === */

  function refreshRoute() { routeFromLocation(); }

  function handlePrepareAction(trigger) {
    const act = trigger.dataset.act;
    const key = trigger.dataset.key;
    const node = key ? state.nodeIndex[key] : null;
    const context = currentContext();
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
      return paintTeacherDetail();
    }
    if (act === 'stage-preview' && node) {
      if (node.kind === 'DRAFT' && node.state === 'FAILED') return showFailureModal(node);
      return showPreview(node.id, state.bindingId);
    }
    if (act === 'stage-download' && node) return downloadVersion(node.id, state.bindingId);
    if (act === 'stage-release' && node) return showReleaseModal(node.id, node.title);
    if (act === 'stage-revoke' && node) return revokeRelease(node.meta.releaseId, node.title, context);
    if (act === 'stage-remove' && node) return confirmRemoveStage(node);
    if (act === 'stage-versions' && node) return showVersions(node.meta.resourceId, context);
    if (act === 'stage-new-version' && node) return showNewVersionModal(node.meta.resourceId, node.title, context);
    if (act === 'stage-failure' && node) return showFailureModal(node);
    if (act === 'add-stage') return showAddStageModal();
    if (act === 'gen-plan') return openCreateFlow('plan');
    if (act === 'view') {
      state.prepareView = trigger.dataset.view;
      if (context) paintTeacherPrepare(state.overview, context);
      return;
    }
    if (act === 'go-studio') {
      location.href = `?g=${routes.studio.g}&i=${routes.studio.i}`;
      return;
    }
  }

  // 课前准备页面的点击 / 键盘 / 下拉变化（只在时间轴或环节页存在时生效，避免与工作室绑定冲突）
  root.addEventListener('click', event => {
    const trigger = event.target.closest('[data-act]');
    if (!trigger || !root.contains(trigger)) return;
    handlePrepareAction(trigger);
  });

  root.addEventListener('keydown', event => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const card = event.target.closest('.g3-tl-card[data-act="stage-select"]');
    if (!card) return;
    event.preventDefault();
    handlePrepareAction(card);
  });

  root.addEventListener('change', event => {
    const target = event.target;
    if (target.id === 'g3Context'
      && root.querySelector('.g3-timeline-layout, .g3-stage-page, .g3-create-page')) {
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
        const context = currentContext();
        if (context) paintStudentTimeline(context, state.route);
      }
    }
  });

  window.addEventListener('popstate', syncFromHistory);
  window.addEventListener('hashchange', syncFromHistory);

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

  function paintStudentTimeline(context, mode) {
    const title = mode === 'materials' ? '课程资料' : '课前学习';
    const visible = filterNodes(state.nodes, state.studentFilter);
    root.innerHTML = `<div class="g3-workspace">
      ${pageHead(title, `${context.courseTitle} · ${context.className}`, context,
        '这里只显示本人教学班、当前已开放的内容；未开放、已撤回或不属于本班的内容不会出现，旧链接也不会继续有效。')}
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
          <span class="g3-help">每个环节独立作答与提交，可用上方序号或底部按钮切换</span>
          <div class="g3-actions">
            <button class="g3-btn" type="button" data-act="student-switch"
              data-mode="${mode === 'materials' ? 'prestudy' : 'materials'}">${mode === 'materials' ? '查看课前学习' : '查看课程资料'}</button>
          </div>
        </div>
      </div>
      <div class="g3-timeline-layout">
        <div class="g3-tl-main">${renderTimeline(visible, state.selectedKey,
          state.overview?.serverTime, '教师发布资料或预习后，环节会出现在这条时间轴上')}</div>
        <aside class="g3-tl-detail" id="g3StageDetail">${loadingState('正在加载环节')}</aside>
      </div>
    </div>`;
    applyStageView();
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
      <span class="g3-help">${quizzes.length ? `本人完成度 ${percent}%，以服务端提交记录为准` : '暂无预习任务，不显示推测的掌握度'}</span></div>`;
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
        <div class="g3-panel-body">${emptyState('选择一个环节', '资料环节可预览或下载；预习环节可作答、提交并查看允许公开的反馈')}</div></div>`;
      return;
    }
    if (node.kind === 'MATERIAL') {
      const meta = node.meta || {};
      box.innerHTML = `<div class="g3-panel g3-detail-panel">
        <div class="g3-panel-head"><div class="g3-panel-title">资料环节 ${seqBadge(node)}</div>${statePill(node.state)}</div>
        <div class="g3-panel-body g3-list">
          <div class="g3-detail-title">${escapeHtml(node.title)}</div>
          <div class="g3-detail-row"><span>环节序号</span><strong>第 ${node.seq || '—'} / ${node.total || '—'} 环节</strong></div>
          <div class="g3-detail-row"><span>开放时间</span><strong>${formatTime(node.opensAt)}</strong></div>
          <div class="g3-detail-row"><span>开放至</span><strong>${formatTime(node.closesAt)}</strong></div>
          <div class="g3-detail-row"><span>版本</span><strong>V${escapeHtml(meta.versionNo || 1)}</strong></div>
          <div class="g3-detail-row"><span>预览页</span><strong>${Number(meta.pageCount || 0)} 页</strong></div>
          <div class="g3-divider"></div>
          <div class="g3-actions">
            <button class="g3-btn primary" type="button" data-act="stage-open" data-key="${escapeHtml(node.key)}">进入独立阅读页</button>
            <button class="g3-btn" type="button" data-act="stage-preview" data-key="${escapeHtml(node.key)}">弹窗预览</button>
            <button class="g3-btn" type="button" data-act="stage-download" data-key="${escapeHtml(node.key)}">下载</button>
          </div>
          <div class="g3-help">每次预览与下载都会重新校验身份与发布范围；撤回后旧地址会立即失效。</div>
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
      const stageEntry = stageNode && stageNode.seq ? `<div class="g3-inline-note">
          <strong>第 ${stageNode.seq} / ${stageNode.total} 环节</strong> · 本环节有独立的作答页，题目占满作答区，可用「上一环节 / 下一环节」继续。
          <div class="g3-actions" style="margin-top:8px">
            <button class="g3-btn primary" type="button" data-act="stage-open" data-key="${escapeHtml(stageNode.key)}">进入第 ${stageNode.seq} 环节作答</button>
          </div></div>` : '';
      const items = detail.items || [];
      const canSubmit = detail.state === 'OPEN' && !detail.submitted && items.length > 0;
      container.innerHTML = `<div class="g3-panel g3-detail-panel">
        <div class="g3-panel-head"><div class="g3-panel-title">${escapeHtml(detail.title)}</div>${statusBadge(detail.state)}</div>
        <div class="g3-panel-body g3-list">
          <div class="g3-help">${formatTime(detail.opensAt)} 至 ${formatTime(detail.closesAt)}</div>
          ${stageEntry}
          ${detail.instructions ? `<div class="g3-inline-note">${escapeHtml(detail.instructions)}</div>` : ''}
          ${detail.state === 'UPCOMING' ? emptyState('任务尚未开放', `开放时间：${formatTime(detail.opensAt)}`) : ''}
          ${detail.state !== 'UPCOMING' && !items.length ? emptyState('本任务没有题目', '可先查看时间轴上的课程资料') : ''}
          ${canSubmit ? '' : `<div class="g3-inline-note warn"><strong>当前不可提交</strong><br>${escapeHtml(submitBlockReason(detail, items))}</div>`}
          <form id="g3StudentAnswerForm" class="g3-list">
            ${items.map(item => renderStudentQuestion(item, detail, 0, !canSubmit)).join('')}
            <div class="g3-actions"><button class="g3-btn primary" type="submit" data-submit-preview${canSubmit ? '' : ' disabled'}>提交预习</button></div>
          </form>
          ${detail.submitted ? `<div class="g3-feedback">已于 ${formatTime(detail.submission?.submittedAt)} 提交。
            ${detail.answerVisible ? '参考答案与解析已公布。' : '参考答案将在教师设置的公布时间显示。'}</div>` : ''}
          ${(detail.resources || []).length ? `<div><div class="g3-help">本班当前开放资料</div>
            <div class="g3-actions g3-resource-links" style="margin-top:8px">${detail.resources.map(resource =>
              `<button class="g3-btn" type="button" data-act="detail-preview" data-version="${resource.versionId}">${escapeHtml(resource.title)} · V${escapeHtml(resource.versionNumber || resource.versionNo)}</button>`).join('')}</div></div>` : ''}
          ${detail.answerVisible && detail.answerVisibleAt ? `<div class="g3-help">解析公布时间：${formatTime(detail.answerVisibleAt)}</div>` : ''}
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

  function submitBlockReason(detail, items) {
    if (detail.state === 'UPCOMING') {
      return `本环节尚未开放，${formatTime(detail.opensAt)} 开放后才能提交。`;
    }
    if (detail.state === 'CLOSED') {
      return `本环节已于 ${formatTime(detail.closesAt)} 截止，服务端不再接收提交；当前填写的内容只保留在本页草稿，不会写入完成记录。`;
    }
    if (detail.submitted) {
      return `本环节已于 ${formatTime(detail.submission && detail.submission.submittedAt)} 提交，无需重复提交。`;
    }
    if (!(items || []).length) return '本环节没有可提交的题目。';
    if (detail.state !== 'OPEN') {
      return `本环节当前为「${STATE_LABELS[detail.state] || detail.state}」，暂不可提交。`;
    }
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
      const hint = document.getElementById('g3StageHint');
      if (hint) hint.textContent = canSubmit
        ? '每个环节独立作答与提交；切换环节时自动保留本题草稿，只有点「提交本环节预习」才写入服务端。'
        : (detail.state === 'CLOSED'
          ? `本环节已于 ${formatTime(detail.closesAt)} 截止，作答内容仅作本地回顾、不会写入服务端；可用上方环节条查看已公布解析。`
          : '本环节当前不可提交，原因见作答区提示；切换环节时草稿仍会保留。');
      container.innerHTML = `<div class="g3-stage-quiz">
        <div class="g3-stage-section-head"><h2>本环节作答</h2>
          <span>${items.length} 道题 · ${detail.submitted ? '已提交' : '待完成'}</span></div>
        ${detail.instructions ? `<div class="g3-inline-note">${escapeHtml(detail.instructions)}</div>` : ''}
        ${detail.state === 'UPCOMING' ? emptyState('任务尚未开放', `开放时间：${formatTime(detail.opensAt)}`) : ''}
        ${detail.state !== 'UPCOMING' && !items.length ? emptyState('本任务没有题目', '可先阅读课程资料，再用下方按钮进入下一环节') : ''}
        ${canSubmit ? '' : `<div class="g3-inline-note warn"><strong>本环节暂不能提交</strong><br>${escapeHtml(submitBlockReason(detail, items))}</div>`}
        <form id="g3StageAnswerForm" class="g3-stage-form">
          ${items.map((item, index) => renderStudentQuestion(item, detail, index + 1, !canSubmit)).join('')}
          <div class="g3-stage-submit">
            <button class="g3-btn primary" type="submit" data-submit-preview${canSubmit ? '' : ' disabled'}>提交本环节预习</button>
            <span class="g3-help">${canSubmit
              ? '提交后仍可用页面底部的「下一环节」继续，或用上方序号跳到任意环节。'
              : '按钮按当前开放窗口禁用；这不是页面故障，原因见上方提示。'}</span>
          </div>
        </form>
        ${detail.submitted ? `<div class="g3-feedback">已于 ${formatTime(detail.submission?.submittedAt)} 提交本环节。
          ${detail.answerVisible ? '参考答案与解析已公布。' : '参考答案将在教师设置的公布时间显示。'}</div>` : ''}
        ${(detail.resources || []).length ? `<div class="g3-stage-links"><div class="g3-help">本环节关联的本班开放资料</div>
          <div class="g3-actions">${detail.resources.map(resource =>
            `<button class="g3-btn" type="button" data-act="detail-preview" data-version="${resource.versionId}">${escapeHtml(resource.title)}</button>`).join('')}</div></div>` : ''}
        ${detail.answerVisible && detail.answerVisibleAt ? `<div class="g3-help">解析公布时间：${formatTime(detail.answerVisibleAt)}</div>` : ''}
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
      <div class="g3-stage-section-head"><h2>本环节资料阅读</h2>
        <div class="g3-actions">
          <button class="g3-btn" type="button" data-act="stage-download" data-key="${escapeHtml(node.key)}">下载原文件</button>
        </div></div>
      <div class="g3-inline-note">阅读完成后用页面底部的「下一环节」继续。资料阅读不产生服务端已读记录，完成进度以习题提交为准。</div>
      <div class="g3-reader-canvas" id="g3ReaderCanvas">${loadingState('正在读取预览页')}</div>
    </div>`;
    const canvas = container.querySelector('#g3ReaderCanvas');
    if (canvas) await fillReaderCanvas(node, canvas);
  }

  async function renderStudentMaterials() { return renderStudentTimeline('materials'); }

  async function renderStudentPrestudy() { return renderStudentTimeline('prestudy'); }

  function renderUnsupported() {
    clearObjectUrls();
    root.innerHTML = emptyState('该模块尚未接入', '当前可用入口：教师「课程建设」「课前准备」，学生「课程资料」「课前学习」');
  }

  function routeFromLocation() {
    const query = new URLSearchParams(location.search);
    return window.Group3App.render(Number(query.get('g') || 0), Number(query.get('i') || 0));
  }

  window.Group3App = {
    render(gi, ii) {
      if (role === 'teacher' && gi === routes.studio.g && ii === routes.studio.i) return renderTeacherStudio();
      if (role === 'teacher' && gi === routes.prepare.g && ii === routes.prepare.i) return renderTeacherPrepare();
      if (role === 'student' && gi === routes.materials.g && ii === routes.materials.i) return renderStudentTimeline('materials');
      if (role === 'student' && gi === routes.prestudy.g && ii === routes.prestudy.i) return renderStudentTimeline('prestudy');
      renderUnsupported();
    }
  };

  window.addEventListener('beforeunload', clearObjectUrls);
  routeFromLocation();
})();
