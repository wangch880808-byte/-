/*
 * 管理员 · 审计日志（1-04）
 *
 * 只读查询：操作日志列表 + 条件筛选 + 详情弹窗。
 * 接口：GET /monitor/operlog/list?pageNum&pageSize&title&operName&status&params[beginTime]&params[endTime]
 *
 * 注册位置：系统管理（g=6）→ 审计日志（i=5）。
 */
(function () {
  'use strict';

  var PAGE_SIZE = 10;

  function pageModule() {
    var host = null;
    var state = null;
    var disposed = false;
    var listController = null;

    function q(id) { return host.querySelector('#' + id); }

    function template() {
      return ''
        + '<div class="adm-page">'
        +   '<div class="adm-toolbar">'
        +     '<input class="adm-input" id="lTitle" placeholder="按系统模块搜索" aria-label="按系统模块搜索">'
        +     '<input class="adm-input" id="lOper" placeholder="按操作人员搜索" aria-label="按操作人员搜索">'
        +     '<select class="adm-select" id="lStatus" aria-label="状态筛选">'
        +       '<option value="">全部状态</option>'
        +       '<option value="0">成功</option>'
        +       '<option value="1">失败</option>'
        +     '</select>'
        +     '<input class="adm-input" id="lBegin" type="date" aria-label="开始日期">'
        +     '<span class="adm-hint">至</span>'
        +     '<input class="adm-input" id="lEnd" type="date" aria-label="结束日期">'
        +     '<button type="button" class="adm-btn primary" id="lSearch">查询</button>'
        +     '<button type="button" class="adm-btn" id="lReset">重置</button>'
        +   '</div>'
        +   '<div class="adm-table-wrap">'
        +     '<table class="adm-table">'
        +       '<thead><tr><th>系统模块</th><th>操作类型</th><th>请求方式</th><th>操作人</th><th>状态</th><th>操作时间</th><th>耗时</th><th>操作</th></tr></thead>'
        +       '<tbody id="lRows"></tbody>'
        +     '</table>'
        +     '<div class="adm-state" id="lState">加载中…</div>'
        +   '</div>'
        +   '<div class="adm-footer">'
        +     '<span id="lSummary">—</span>'
        +     '<span>'
        +       '<button type="button" class="adm-btn" id="lPrev" disabled>上一页</button> '
        +       '<button type="button" class="adm-btn" id="lNext" disabled>下一页</button>'
        +     '</span>'
        +   '</div>'
        + '</div>';
    }

    function setState(text, isError) {
      var box = q('lState');
      box.hidden = !text;
      box.textContent = text || '';
      box.className = 'adm-state' + (isError ? ' error' : '');
    }

    function cell(row, text, className) {
      var td = document.createElement('td');
      td.textContent = (text === null || text === undefined || text === '') ? '—' : String(text);
      if (className) td.className = className;
      row.appendChild(td);
      return td;
    }

    function renderRows(rows) {
      var tbody = q('lRows');
      tbody.innerHTML = '';
      if (!rows.length) {
        setState('暂无数据');
        return;
      }
      setState('');
      rows.forEach(function (log) {
        var tr = document.createElement('tr');
        cell(tr, log.title);
        cell(tr, AdminUI.businessType(log.businessType));
        cell(tr, log.requestMethod);
        cell(tr, log.operName);
        var statusTd = document.createElement('td');
        var tag = document.createElement('span');
        var success = String(log.status) === '0';
        tag.className = 'adm-tag ' + (success ? 'ok' : 'fail');
        tag.textContent = success ? '成功' : '失败';
        statusTd.appendChild(tag);
        tr.appendChild(statusTd);
        cell(tr, log.operTime);
        cell(tr, log.costTime === null || log.costTime === undefined ? '—' : log.costTime + ' ms');

        var actions = document.createElement('td');
        actions.className = 'actions';
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'adm-btn link';
        btn.textContent = '详情';
        btn.addEventListener('click', function () { showDetail(log); });
        actions.appendChild(btn);
        tr.appendChild(actions);

        tbody.appendChild(tr);
      });
    }

    function renderFooter() {
      var pages = Math.max(1, Math.ceil(state.total / PAGE_SIZE));
      q('lSummary').textContent = '第 ' + state.page + ' / ' + pages + ' 页 · 共 ' + state.total + ' 条';
      q('lPrev').disabled = state.page <= 1;
      q('lNext').disabled = state.page >= pages;
    }

    async function load(page) {
      if (!state || state.loading) return;
      var requestState = state;
      var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
      listController = controller;
      state.loading = true;
      setState('加载中…');

      var params = new URLSearchParams({ pageNum: page, pageSize: PAGE_SIZE });
      if (state.title) params.set('title', state.title);
      if (state.operName) params.set('operName', state.operName);
      if (state.status !== '') params.set('status', state.status);
      if (state.begin) params.set('params[beginTime]', state.begin + ' 00:00:00');
      if (state.end) params.set('params[endTime]', state.end + ' 23:59:59');

      try {
        var data = await EduApi.get('/monitor/operlog/list?' + params.toString(), { signal: controller && controller.signal });
        if (disposed || state !== requestState) return;
        state.page = page;
        state.total = Number(data.total || 0);
        renderRows(data.rows || []);
        renderFooter();
      } catch (error) {
        if (disposed || state !== requestState || error.cancelled || error.forbidden || error.unauthorized) return;
        setState('加载失败：' + (error.message || '未知错误'), true);
      } finally {
        requestState.loading = false;
        if (listController === controller) listController = null;
      }
    }

    function detailRow(dl, label, value) {
      dl.appendChild(AdminUI.el('dt', null, label));
      var dd = AdminUI.el('dd', null, value === null || value === undefined || value === '' ? '—' : String(value));
      dl.appendChild(dd);
    }

    function codeBlock(title, text) {
      var wrap = document.createElement('div');
      wrap.className = 'adm-field full';
      wrap.appendChild(AdminUI.el('label', null, title));
      wrap.appendChild(AdminUI.el('code', 'adm-code', text || '—'));
      return wrap;
    }

    function showDetail(log) {
      var body = document.createElement('div');
      body.style.display = 'flex';
      body.style.flexDirection = 'column';
      body.style.gap = '12px';

      var dl = document.createElement('dl');
      dl.className = 'adm-detail';
      detailRow(dl, '系统模块', log.title);
      detailRow(dl, '操作类型', AdminUI.businessType(log.businessType));
      detailRow(dl, '请求方式', log.requestMethod);
      detailRow(dl, '请求地址', log.operUrl);
      detailRow(dl, '操作方法', log.method);
      detailRow(dl, '操作人员', log.operName);
      detailRow(dl, '所属部门', log.deptName);
      detailRow(dl, '主机', log.operIp);
      detailRow(dl, '操作地点', log.operLocation);
      detailRow(dl, '操作时间', log.operTime);
      detailRow(dl, '消耗时间', log.costTime === null || log.costTime === undefined ? '—' : log.costTime + ' ms');
      detailRow(dl, '状态', String(log.status) === '0' ? '成功' : '失败');

      body.appendChild(dl);

      if (log.errorMsg) body.appendChild(codeBlock('错误信息', log.errorMsg));
      body.appendChild(codeBlock('请求参数', log.operParam));
      body.appendChild(codeBlock('返回结果', log.jsonResult));

      AdminUI.modal({
        title: '操作日志详情',
        content: body,
        wide: true,
        okText: '关闭',
        hideCancel: true,
        onOk: function () { return true; }
      });
    }

    function bind() {
      q('lSearch').addEventListener('click', function () {
        state.title = q('lTitle').value.trim();
        state.operName = q('lOper').value.trim();
        state.status = q('lStatus').value;
        state.begin = q('lBegin').value;
        state.end = q('lEnd').value;
        if (state.begin && state.end && state.begin > state.end) {
          AdminUI.toast('开始日期不能晚于结束日期', 'error');
          return;
        }
        load(1);
      });
      ['lTitle', 'lOper'].forEach(function (id) {
        q(id).addEventListener('keydown', function (event) {
          if (event.key === 'Enter') { event.preventDefault(); q('lSearch').click(); }
        });
      });
      q('lReset').addEventListener('click', function () {
        q('lTitle').value = '';
        q('lOper').value = '';
        q('lStatus').value = '';
        q('lBegin').value = '';
        q('lEnd').value = '';
        state.title = '';
        state.operName = '';
        state.status = '';
        state.begin = '';
        state.end = '';
        load(1);
      });
      q('lPrev').addEventListener('click', function () { if (state.page > 1) load(state.page - 1); });
      q('lNext').addEventListener('click', function () {
        var pages = Math.max(1, Math.ceil(state.total / PAGE_SIZE));
        if (state.page < pages) load(state.page + 1);
      });
    }

    return {
      mount: function (mountHost) {
        host = mountHost;
        disposed = false;
        state = { page: 1, total: 0, title: '', operName: '', status: '', begin: '', end: '', loading: false };
        host.innerHTML = template();
        bind();
        load(1);
      },
      unmount: function () {
        disposed = true;
        if (listController) listController.abort();
        listController = null;
        host = null;
        state = null;
      }
    };
  }

  window.EDU_PAGES = window.EDU_PAGES || [];
  window.EDU_PAGES.push({ g: 6, i: 5, page: Object.assign(pageModule(), { permission: 'monitor:operlog:list', route: 'monitor/operlog', roles: ['admin'] }) });
})();
