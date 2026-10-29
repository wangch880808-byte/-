/* 三端共用布局、已授权页面导航及挂载生命周期。g/i 保留原型位置，过滤菜单不改变深链接。 */
(function () {
  'use strict';
  var config = window.EDU_SHELL;
  if (!config || !Array.isArray(config.menu) || !config.role) return;
  var MENU = config.menu;
  var ROLE = config.role;
  var MAX_TABS = 15;
  var pages = {};
  var current = null;
  var activePage = null;
  var authorized = false;
  var mountVersion = 0;
  var pageController = null;
  var headerController = null;
  var headerVersion = 0;
  var lastTabKey = null;
  function byId(id) { return document.getElementById(id); }
  function element(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function key(g, i) { return g + ':' + i; }
  function available(g, i) {
    var page = pages[key(g, i)];
    return authorized && !!page && page.roles.indexOf(ROLE) !== -1 && window.EduAuth.canOpen(page);
  }
  function positions() {
    var result = [];
    MENU.forEach(function (group, g) {
      group.children.forEach(function (name, i) { if (available(g, i)) result.push({ g: g, i: i }); });
    });
    return result;
  }
  function getContext() { return window.EduContext ? window.EduContext.get() : null; }
  function tabKey() {
    var identity = window.EduAuth.getIdentity();
    var context = getContext();
    return 'zhijiao.tabs.' + (identity ? identity.userId : 'anonymous') + '.' + ROLE + '.'
      + (context ? context.courseSpaceId + '.' + (context.classId || 'course') : 'none');
  }
  function storedTabs() {
    try {
      var tabs = JSON.parse(localStorage.getItem(tabKey()) || '[]');
      if (!Array.isArray(tabs)) return [];
      return tabs.filter(function (tab) { return tab && Number.isInteger(tab.g) && Number.isInteger(tab.i) && available(tab.g, tab.i); }).slice(-MAX_TABS);
    } catch (error) { return []; }
  }
  function saveTabs(tabs) { try { localStorage.setItem(tabKey(), JSON.stringify(tabs)); } catch (error) {} }
  function urlFor(g, i) {
    var url = new URL(window.location.href);
    url.searchParams.set('g', g); url.searchParams.set('i', i);
    return url.pathname + url.search;
  }
  function parseLocation() {
    var query = new URL(window.location.href).searchParams;
    if (!query.has('g') && !query.has('i')) return null;
    if (!/^\d+$/.test(query.get('g') || '') || !/^\d+$/.test(query.get('i') || '')) return { g: -1, i: -1 };
    return { g: Number(query.get('g')), i: Number(query.get('i')) };
  }
  function unmountPage() {
    mountVersion += 1;
    if (pageController) pageController.abort();
    pageController = null;
    var previous = activePage; activePage = null;
    if (previous && previous.unmount) {
      try { previous.unmount(); } catch (error) { console.error('[EduShell] 页面卸载失败', error); }
    }
    if (window.AdminUI && window.AdminUI.closeAll) window.AdminUI.closeAll();
  }
  function showMessage(title, message) {
    unmountPage();
    var content = byId('pageContent');
    content.classList.remove('page-active');
    content.replaceChildren(element('div', 'page-title', title), element('div', 'hint', message));
  }
  function renderForbidden(message) { showMessage('无权限访问', message || '当前账号没有访问该页面的权限。'); }
  function renderFrame() {
    document.body.insertAdjacentHTML('beforeend', '<header class="app-header">'
      + '<div class="logo"><button type="button" class="nav-toggle" id="navToggle" aria-label="展开或收起导航" aria-expanded="false">☰</button><img src="/logo.png" alt="校徽"><b>知教·智慧教育课程平台</b></div>'
      + '<div class="header-right"><div id="contextHost" class="context-host"></div><div id="headerActions" class="header-actions" aria-label="快捷入口"></div><span class="avatar"></span><button type="button" class="logout-button" id="logoutBtn">退出登录</button></div></header>'
      + '<div class="layout"><aside class="sidebar" id="sidebar" aria-label="功能导航"></aside><main class="main"><div class="breadcrumb" id="breadcrumb"></div><div class="tabs" id="tabs"></div><div class="content" id="pageContent"></div></main></div>');
    document.querySelector('.avatar').textContent = config.avatar || '';
    byId('navToggle').addEventListener('click', function () {
      var expanded = document.body.classList.toggle('nav-open'); byId('navToggle').setAttribute('aria-expanded', String(expanded));
    });
    if (window.EduContext) window.EduContext.mount(byId('contextHost'));
  }
  function bindNavigation(link, g, i) {
    link.href = urlFor(g, i);
    link.addEventListener('click', function (event) {
      if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button > 0) return;
      event.preventDefault(); navigate(g, i, true);
    });
  }
  function clearHeader() {
    headerVersion += 1;
    if (headerController) headerController.abort();
    headerController = null;
    byId('headerActions').replaceChildren();
  }
  function refreshHeader() {
    clearHeader();
    if (!authorized) return;
    headerController = typeof AbortController === 'undefined' ? null : new AbortController();
    var controller = headerController;
    var generation = headerVersion;
    var context = getContext();
    positions().forEach(function (position) {
      var page = pages[key(position.g, position.i)];
      if (!page.headerEntry) return;
      if (page.requiresContext && (!context || (page.requiresContext !== 'course' && !context.classId))) return;
      var entry = page.headerEntry;
      var link = element('a', 'header-action');
      link.appendChild(element('span', null, entry.label));
      link.setAttribute('aria-label', entry.label);
      bindNavigation(link, position.g, position.i);
      byId('headerActions').appendChild(link);
      if (typeof entry.getUnreadCount !== 'function') return;
      var badge = element('span', 'header-badge', '…');
      badge.setAttribute('aria-hidden', 'true'); link.appendChild(badge);
      function live() { return authorized && generation === headerVersion && (!controller || !controller.signal.aborted); }
      Promise.resolve().then(function () {
        if (!live()) return null;
        return entry.getUnreadCount(context, { signal: controller ? controller.signal : undefined });
      }).then(function (count) {
        if (!live()) return;
        if (!Number.isSafeInteger(count) || count < 0) throw new Error('未读数量格式不正确');
        badge.hidden = count === 0; badge.textContent = count > 99 ? '99+' : String(count);
        link.setAttribute('aria-label', count ? entry.label + '，' + count + ' 条未读' : entry.label + '，无未读');
      }).catch(function (error) {
        if (!live() || error.unauthorized) return;
        if (error.forbidden) { link.hidden = true; return; }
        if (error.cancelled) { badge.hidden = true; return; }
        badge.textContent = '!'; link.setAttribute('aria-label', entry.label + '，未读数量暂不可用');
        link.title = '未读数量暂不可用，点击查看';
      });
    });
  }
  function renderMenu() {
    var sidebar = byId('sidebar'); sidebar.replaceChildren();
    MENU.forEach(function (group, g) {
      var visible = group.children.map(function (name, i) { return { name: name, i: i }; }).filter(function (item) { return available(g, item.i); });
      if (!visible.length) return;
      var section = element('div', 'menu-group'); section.dataset.group = g;
      var title = element('button', 'menu-group-title'); title.type = 'button';
      title.appendChild(element('span', null, group.name)); title.appendChild(element('span', 'arrow', '▾')); title.setAttribute('aria-expanded', 'true');
      title.addEventListener('click', function () { var collapsed = section.classList.toggle('collapsed'); title.setAttribute('aria-expanded', String(!collapsed)); });
      var list = element('ul', 'menu-items');
      var subheadAdded = false;
      visible.forEach(function (item) {
        if (group.subhead && item.i >= group.subhead.at && !subheadAdded) { list.appendChild(element('li', 'menu-subhead', group.subhead.label)); subheadAdded = true; }
        var li = element('li');
        var selected = current && current.g === g && current.i === item.i;
        var link = element('a', 'menu-item' + (selected ? ' active' : ''), item.name);
        if (selected) link.setAttribute('aria-current', 'page');
        bindNavigation(link, g, item.i); li.appendChild(link); list.appendChild(li);
      });
      section.appendChild(title); section.appendChild(list); sidebar.appendChild(section);
    });
    if (!sidebar.children.length) sidebar.appendChild(element('p', 'nav-empty', '暂无可用功能'));
  }
  function renderTabs() {
    var box = byId('tabs'); box.replaceChildren();
    storedTabs().forEach(function (tab) {
      var selected = current && current.g === tab.g && current.i === tab.i;
      var wrapper = element('span', 'tab' + (selected ? ' active' : ''));
      var link = element('a', 'tab-link', MENU[tab.g].children[tab.i]); bindNavigation(link, tab.g, tab.i);
      var close = element('button', 'tab-close', '×'); close.type = 'button'; close.setAttribute('aria-label', '关闭' + MENU[tab.g].children[tab.i]);
      close.addEventListener('click', function () {
        var tabs = storedTabs().filter(function (item) { return key(item.g, item.i) !== key(tab.g, tab.i); }); saveTabs(tabs);
        if (selected) {
          var next = tabs[tabs.length - 1] || positions()[0];
          if (next) navigate(next.g, next.i, true); else renderTabs();
        } else renderTabs();
      });
      wrapper.appendChild(link); wrapper.appendChild(close); box.appendChild(wrapper);
    });
  }
  function mountCurrent() {
    if (!current || !available(current.g, current.i)) return;
    var page = pages[key(current.g, current.i)];
    var context = getContext();
    if (page.requiresContext && (!context || (page.requiresContext !== 'course' && !context.classId))) {
      var status = window.EduContext && window.EduContext.getStatus();
      showMessage('请选择课程与教学班', status && status.message ? status.message : '请在顶部选择当前课程与教学班后继续。'); return;
    }
    unmountPage(); activePage = page;
    var version = mountVersion;
    pageController = typeof AbortController === 'undefined' ? null : new AbortController();
    var controller = pageController;
    var content = byId('pageContent'); content.classList.add('page-active');
    var host = element('div', 'page-host'); content.replaceChildren(host);
    try {
      var mounted = page.mount(host, context, Object.freeze({
        signal: controller ? controller.signal : undefined,
        isCurrent: function () { return authorized && version === mountVersion && (!controller || !controller.signal.aborted); }
      }));
      if (mounted && typeof mounted.catch === 'function') mounted.catch(function (error) {
        if (version === mountVersion && !error.cancelled && !error.forbidden && !error.unauthorized) showMessage('页面加载失败', error.message || '请稍后重试');
      });
    } catch (error) { showMessage('页面加载失败', error.message || '请稍后重试'); }
  }
  function navigate(g, i, push) {
    if (!authorized) return;
    current = { g: g, i: i };
    if (push) window.history.pushState(null, '', urlFor(g, i));
    document.body.classList.remove('nav-open'); byId('navToggle').setAttribute('aria-expanded', 'false');
    renderMenu();
    var breadcrumb = byId('breadcrumb'); breadcrumb.replaceChildren();
    if (!available(g, i)) {
      renderTabs();
      if (pages[key(g, i)]) renderForbidden(); else showMessage('页面尚未开放', '此页面暂不可用，请从导航选择其他功能。');
      return;
    }
    breadcrumb.appendChild(element('span', null, MENU[g].name)); breadcrumb.appendChild(element('span', null, '›')); breadcrumb.appendChild(element('span', 'cur', MENU[g].children[i]));
    var tabs = storedTabs().filter(function (tab) { return key(tab.g, tab.i) !== key(g, i); }); tabs.push({ g: g, i: i }); saveTabs(tabs.slice(-MAX_TABS));
    lastTabKey = tabKey(); renderTabs(); mountCurrent();
  }
  function authorize(info, routers) {
    unmountPage(); window.EduAuth.set(info, routers); authorized = true;
    refreshHeader();
    var target = parseLocation() || positions()[0];
    if (target) navigate(target.g, target.i, false);
    else { current = null; renderMenu(); renderTabs(); byId('breadcrumb').replaceChildren(); showMessage('当前暂无可用功能', '已开通且获授权的功能将在这里显示。如需开通，请联系管理员。'); }
  }
  function suspend() {
    authorized = false; unmountPage();
    clearHeader();
    document.body.classList.add('auth-pending');
    byId('pageContent').replaceChildren(); byId('tabs').replaceChildren(); byId('sidebar').replaceChildren();
    if (window.EduApi) window.EduApi.cancelAll();
  }
  function registerPage(g, i, page) {
    if (!Number.isInteger(g) || !Number.isInteger(i) || !MENU[g] || !MENU[g].children[i] || !page || typeof page.mount !== 'function') throw new Error('业务页注册参数无效');
    if (!page.permission && !page.allowAuthenticated) throw new Error('业务页需声明 permission 或 allowAuthenticated');
    if (pages[key(g, i)] && pages[key(g, i)] !== page) throw new Error('该导航位置已注册，请核对页面归属');
    if (page.headerEntry && (typeof page.headerEntry.label !== 'string' || !page.headerEntry.label.trim()
        || (page.headerEntry.getUnreadCount !== undefined && typeof page.headerEntry.getUnreadCount !== 'function'))) throw new Error('顶部入口需提供名称和有效的未读数量读取方法');
    if (!page.roles) page.roles = [ROLE];
    if (!Array.isArray(page.roles) || !page.roles.length || page.roles.some(function (role) { return ['admin', 'teacher', 'student'].indexOf(role) === -1; })) throw new Error('页面需声明有效的角色列表');
    pages[key(g, i)] = page;
    if (authorized) {
      renderMenu(); refreshHeader();
      var target = parseLocation();
      if ((!current && !target) || (target && target.g === g && target.i === i) || (current && current.g === g && current.i === i)) {
        var next = target || current || positions()[0];
        if (next) navigate(next.g, next.i, false);
      }
    }
  }
  window.EduShell = {
    role: ROLE, menu: MENU, maxTabs: MAX_TABS, registerPage: registerPage, authorize: authorize, suspend: suspend,
    showForbidden: renderForbidden, getContext: getContext,
    getCurrent: function () { return current && MENU[current.g] ? { g: current.g, i: current.i, group: MENU[current.g].name, title: MENU[current.g].children[current.i] } : null; },
    navigate: function (g, i) { navigate(g, i, true); },
    refreshIdentity: function () { return window.EduSession ? window.EduSession.refresh() : Promise.reject(new Error('身份服务尚未就绪')); },
    refreshHeader: refreshHeader
  };
  renderFrame();
  (window.EDU_PAGES || []).forEach(function (item) { registerPage(item.g, item.i, item.page); });
  window.addEventListener('edu:forbidden', function (event) {
    var page = current && pages[key(current.g, current.i)];
    if (page && page.requiresContext && window.EduContext) window.EduContext.clear('该课程或教学班已不可用');
    renderForbidden(event.detail && event.detail.message);
  });
  window.addEventListener('edu:context-change', function () {
    try { if (lastTabKey) localStorage.removeItem(lastTabKey); localStorage.removeItem(tabKey()); } catch (error) {}
    lastTabKey = tabKey();
    if (authorized) { refreshHeader(); renderTabs(); mountCurrent(); }
  });
  window.addEventListener('popstate', function () { if (window.EduSession) window.EduSession.refresh(); });
})();
