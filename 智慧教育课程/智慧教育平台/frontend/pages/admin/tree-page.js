/* 菜单、部门共用的树表与编辑流程；只调用已有若依系统接口。 */
(function () {
  'use strict';

  function orderedRows(list, idKey) {
    var byId = new Map();
    var children = new Map();
    list.forEach(function (row) { byId.set(String(row[idKey]), row); });
    list.forEach(function (row) {
      var parent = String(row.parentId || 0);
      if (!children.has(parent)) children.set(parent, []);
      children.get(parent).push(row);
    });
    var result = [];
    var seen = new Set();
    function visit(row, depth) {
      var id = String(row[idKey]);
      if (seen.has(id)) return;
      seen.add(id);
      result.push({ row: row, depth: depth });
      (children.get(id) || []).forEach(function (child) { visit(child, depth + 1); });
    }
    list.filter(function (row) { return !byId.has(String(row.parentId || 0)); }).forEach(function (row) { visit(row, 0); });
    list.forEach(function (row) { visit(row, 0); });
    return result;
  }

  function parentOptions(list, idKey, excludedId) {
    var blocked = new Set(excludedId === null ? [] : [String(excludedId)]);
    var changed = true;
    while (changed) {
      changed = false;
      list.forEach(function (row) {
        if (blocked.has(String(row.parentId)) && !blocked.has(String(row[idKey]))) {
          blocked.add(String(row[idKey])); changed = true;
        }
      });
    }
    return orderedRows(list, idKey).filter(function (item) { return !blocked.has(String(item.row[idKey])); });
  }

  function create(config) {
    var state = null;
    var host = null;
    function can(action) { return window.EduAuth.has(config.permission + ':' + action); }
    function current(snapshot) { return state === snapshot && !!state; }
    function q(name) { return host.querySelector('[data-ui="' + name + '"]'); }
    function node(tag, cls, text) { return AdminUI.el(tag, cls, text); }
    function action(text, fn, danger) {
      var button = node('button', 'adm-btn link' + (danger ? ' danger' : ''), text);
      button.type = 'button'; button.addEventListener('click', fn); return button;
    }
    function message(text, error) {
      q('state').hidden = !text;
      q('state').textContent = text || '';
      q('state').className = 'adm-state' + (error ? ' error' : '');
    }

    function renderRows() {
      var body = q('rows'); body.replaceChildren();
      if (!state.rows.length) { message('暂无数据'); q('count').textContent = '共 0 条'; return; }
      message('');
      orderedRows(state.rows, config.id).forEach(function (item) {
        if (!state.expanded && item.depth > 0) return;
        var row = item.row;
        var tr = node('tr');
        config.columns.forEach(function (column, index) {
          var value = column.format ? column.format(row[column.key], row) : row[column.key];
          var cell = node('td', column.className, value === null || value === undefined || value === '' ? '—' : String(value));
          if (index === 0) cell.style.paddingLeft = (12 + item.depth * 22) + 'px';
          tr.appendChild(cell);
        });
        var controls = node('td', 'actions');
        if (can('add') && row.menuType !== 'F' && String(row.status) !== '1') controls.appendChild(action('新增下级', function () { openForm(null, row[config.id]); }));
        if (can('edit') && can('query')) controls.appendChild(action('编辑', function () { openForm(row[config.id]); }));
        if (can('remove')) controls.appendChild(action('删除', function () { remove(row); }, true));
        tr.appendChild(controls); body.appendChild(tr);
      });
      q('count').textContent = '共 ' + state.rows.length + ' 条';
    }

    async function load() {
      var snapshot = state;
      if (!snapshot) return;
      if (snapshot.controller) snapshot.controller.abort();
      var controller = new AbortController(); snapshot.controller = controller;
      snapshot.rows = [];
      var params = new URLSearchParams();
      if (q('keyword').value.trim()) params.set(config.label, q('keyword').value.trim());
      if (q('status').value !== '') params.set('status', q('status').value);
      message('加载中…'); q('rows').replaceChildren(); q('count').textContent = '';
      try {
        var result = await EduApi.get(config.base + '/list?' + params, { signal: controller.signal });
        if (!current(snapshot) || controller.signal.aborted) return;
        if (!Array.isArray(result.data)) throw new Error('列表响应格式不正确');
        snapshot.rows = result.data; renderRows();
      } catch (error) {
        if (!current(snapshot) || error.cancelled || error.forbidden || error.unauthorized) return;
        message('加载失败：' + error.message, true);
      }
    }

    async function openForm(id, parentId) {
      if (!can(id === null ? 'add' : 'edit')) return;
      var snapshot = state;
      var controller = new AbortController(); snapshot.forms.add(controller);
      var submit = null;
      var closed = false;
      var modal = AdminUI.modal({
        title: (id === null ? '新增' : '编辑') + config.title, content: '加载中…', wide: true, okText: '保存',
        onClose: function () { closed = true; controller.abort(); snapshot.forms.delete(controller); },
        onOk: function () { return submit ? submit() : false; }
      });
      try {
        var results = await Promise.all([
          EduApi.get(config.base + '/list', { signal: controller.signal }),
          id === null ? Promise.resolve({ data: Object.assign({}, config.defaults, { parentId: parentId === undefined ? config.defaultParent : parentId }) })
            : EduApi.get(config.base + '/' + encodeURIComponent(id), { signal: controller.signal })
        ]);
        if (!current(snapshot) || closed) return;
        var fullList = results[0].data;
        if (!Array.isArray(fullList)) throw new Error('上级列表响应格式不正确');
        var detail = results[1].data;
        if (!detail) throw new Error('对象不存在或已不可用');
        var fields = new Map();
        var wrappers = new Map();
        var form = node('div', 'adm-form-grid');
        config.fields.forEach(function (field) {
          var wrapper = node('div', 'adm-field' + (field.full ? ' full' : ''));
          var label = node('label', field.required ? 'req' : '', field.title);
          var input;
          if (field.options || field.parent) {
            input = node('select', 'adm-select');
            var options = field.options;
            if (field.parent) {
              options = config.rootAllowed || (id !== null && String(detail.parentId) === '0') ? [{ value: '0', label: '顶级' }] : [];
              parentOptions(fullList, config.id, id).forEach(function (item) {
                if (item.row.menuType === 'F') return;
                if (config.kind === 'dept' && String(item.row.status) === '1' && String(detail.parentId) !== String(item.row[config.id])) return;
                options.push({ value: String(item.row[config.id]), label: '　'.repeat(item.depth) + item.row[config.label] });
              });
            }
            options.forEach(function (option) {
              var el = node('option', null, option.label); el.value = String(option.value); input.appendChild(el);
            });
          } else {
            input = node('input', 'adm-input'); input.type = field.type || 'text';
            if (field.max) input.maxLength = field.max;
            if (input.type === 'number') { input.min = '0'; input.step = '1'; }
          }
          input.id = config.kind + '-' + field.key;
          label.htmlFor = input.id;
          var initial = detail[field.key];
          if (initial !== null && initial !== undefined) input.value = String(initial);
          if (field.parent && config.kind === 'dept' && id !== null && String(detail.parentId) === '0') input.disabled = true;
          fields.set(field.key, input); wrappers.set(field.key, wrapper);
          wrapper.appendChild(label); wrapper.appendChild(input); form.appendChild(wrapper);
        });
        if (config.kind === 'menu') {
          function updateFields() {
            var type = fields.get('menuType').value;
            config.fields.forEach(function (field) { wrappers.get(field.key).hidden = field.types ? field.types.indexOf(type) === -1 : false; });
          }
          fields.get('menuType').addEventListener('change', updateFields); updateFields();
        }
        modal.body.replaceChildren(form);
        submit = async function () {
          if (!current(snapshot) || closed) return false;
          var payload = {};
          config.fields.forEach(function (field) {
            if (wrappers.get(field.key).hidden) { payload[field.key] = ''; return; }
            var value = fields.get(field.key).value.trim();
            if (field.required && !value) throw new Error('请填写' + field.title);
            if (field.type === 'number') {
              if (!/^\d+$/.test(value) || !Number.isSafeInteger(Number(value))) throw new Error(field.title + '需为非负整数');
              payload[field.key] = Number(value);
            } else payload[field.key] = value;
          });
          if (!payload.parentId) throw new Error('没有可用的上级' + config.title);
          if (id !== null) payload[config.id] = id;
          if (config.kind === 'menu') {
            if (payload.menuType !== 'F' && !payload.path) throw new Error('请填写路由地址');
            if (payload.menuType !== 'F' && payload.isFrame === '0' && !/^https?:\/\//i.test(payload.path)) throw new Error('外链地址需以 http:// 或 https:// 开头');
            if (payload.menuType === 'C' && payload.isFrame !== '0' && !payload.component) throw new Error('请填写组件路径');
            if (payload.query) { try { JSON.parse(payload.query); } catch (e) { throw new Error('路由参数需为 JSON 格式'); } }
            if (payload.menuType === 'F') Object.assign(payload, { path: '', component: '', isFrame: '1', isCache: '1', visible: '0' });
            if (payload.menuType === 'M') Object.assign(payload, { component: '', query: '', isCache: '1' });
          } else {
            if (payload.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email)) throw new Error('邮箱格式不正确');
            if (payload.phone && !/^[\d+\-]{1,11}$/.test(payload.phone)) throw new Error('联系电话格式不正确');
          }
          await EduApi.request(config.base, { method: id === null ? 'POST' : 'PUT', body: payload, signal: controller.signal });
          if (!current(snapshot) || closed) return true;
          modal.close(); AdminUI.toast('已保存');
          if (config.kind === 'menu') await window.EduShell.refreshIdentity();
          if (current(snapshot)) await load();
          return true;
        };
      } catch (error) {
        if (!current(snapshot) || closed || error.cancelled || error.forbidden || error.unauthorized) return;
        modal.setError('加载失败：' + error.message);
      }
    }

    async function remove(row) {
      var snapshot = state;
      if (!can('remove')) return;
      var confirmed = await AdminUI.confirm('确定删除' + config.title + '“' + row[config.label] + '”吗？', { danger: true, okText: '删除' });
      if (!confirmed || !current(snapshot)) return;
      try {
        await EduApi.del(config.base + '/' + encodeURIComponent(row[config.id]));
        if (!current(snapshot)) return;
        AdminUI.toast('已删除');
        if (config.kind === 'menu') await window.EduShell.refreshIdentity();
        if (current(snapshot)) await load();
      } catch (error) {
        if (current(snapshot) && !error.forbidden && !error.unauthorized && !error.cancelled) AdminUI.toast(error.message, 'error');
      }
    }

    return {
      permission: config.permission + ':list', route: config.route, roles: ['admin'],
      mount: function (target) {
        host = target; state = { rows: [], expanded: true, controller: null, forms: new Set() };
        host.innerHTML = '<div class="adm-page"><div class="adm-toolbar">'
          + '<input class="adm-input" data-ui="keyword" aria-label="名称筛选" placeholder="按名称搜索">'
          + '<select class="adm-select" data-ui="status" aria-label="状态筛选"><option value="">全部状态</option><option value="0">正常</option><option value="1">停用</option></select>'
          + '<button class="adm-btn primary" data-ui="search">查询</button><button class="adm-btn" data-ui="reset">重置</button>'
          + '<button class="adm-btn" data-ui="expand">折叠全部</button><span class="adm-spacer"></span><button class="adm-btn primary" data-ui="add">新增</button></div>'
          + '<div class="adm-table-wrap"><table class="adm-table"><thead><tr></tr></thead><tbody data-ui="rows"></tbody></table><div class="adm-state" data-ui="state">加载中…</div></div>'
          + '<div class="adm-footer"><span data-ui="count"></span></div></div>';
        var header = host.querySelector('thead tr');
        config.columns.forEach(function (column) { header.appendChild(node('th', null, column.title)); });
        header.appendChild(node('th', null, '操作'));
        q('search').addEventListener('click', load);
        q('keyword').addEventListener('keydown', function (event) { if (event.key === 'Enter') load(); });
        q('reset').addEventListener('click', function () { q('keyword').value = ''; q('status').value = ''; load(); });
        q('expand').addEventListener('click', function () { state.expanded = !state.expanded; q('expand').textContent = state.expanded ? '折叠全部' : '展开全部'; renderRows(); });
        q('add').hidden = !can('add');
        q('add').addEventListener('click', function () { openForm(null); });
        load();
      },
      unmount: function () {
        if (state) { if (state.controller) state.controller.abort(); state.forms.forEach(function (controller) { controller.abort(); }); }
        state = null; host = null;
      }
    };
  }

  window.AdminTreePage = { create: create, orderedRows: orderedRows, parentOptions: parentOptions };
})();
