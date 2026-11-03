/*
 * 管理员 · 角色管理（1-04）
 *
 * 列表 + 搜索 + 新增/编辑（含菜单权限树）+ 停用启用 + 删除。
 * 接口：/system/role/list、/system/role/{id}、/system/role、/system/role/changeStatus、
 *       /system/menu/treeselect、/system/menu/roleMenuTreeselect/{roleId}
 *
 * 注册位置：系统管理（g=6）→ 角色管理（i=1）。
 */
(function () {
  'use strict';

  var PAGE_SIZE = 10;

  function pageModule() {
    var host = null;
    var state = null;
    var disposed = false;
    var listController = null;

    var DATA_SCOPES = [
      { value: '1', label: '全部数据权限' },
      { value: '2', label: '自定义部门数据权限' },
      { value: '3', label: '本部门数据权限' },
      { value: '4', label: '本部门及以下数据权限' },
      { value: '5', label: '仅本人数据权限' }
    ];

    function q(id) { return host.querySelector('#' + id); }

    function template() {
      return ''
        + '<div class="adm-page">'
        +   '<div class="adm-toolbar">'
        +     '<input class="adm-input" id="rName" placeholder="按角色名称搜索" aria-label="按角色名称搜索">'
        +     '<input class="adm-input" id="rKey" placeholder="按权限字符搜索" aria-label="按权限字符搜索">'
        +     '<select class="adm-select" id="rStatus" aria-label="状态筛选">'
        +       '<option value="">全部状态</option>'
        +       '<option value="0">正常</option>'
        +       '<option value="1">停用</option>'
        +     '</select>'
        +     '<button type="button" class="adm-btn primary" id="rSearch">查询</button>'
        +     '<button type="button" class="adm-btn" id="rReset">重置</button>'
        +     '<span class="adm-spacer"></span>'
        +     '<button type="button" class="adm-btn primary" id="rAdd">新增角色</button>'
        +   '</div>'
        +   '<div class="adm-table-wrap">'
        +     '<table class="adm-table">'
        +       '<thead><tr><th>角色名称</th><th>权限字符</th><th>显示顺序</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>'
        +       '<tbody id="rRows"></tbody>'
        +     '</table>'
        +     '<div class="adm-state" id="rState">加载中…</div>'
        +   '</div>'
        +   '<div class="adm-footer">'
        +     '<span id="rSummary">—</span>'
        +     '<span>'
        +       '<button type="button" class="adm-btn" id="rPrev" disabled>上一页</button> '
        +       '<button type="button" class="adm-btn" id="rNext" disabled>下一页</button>'
        +     '</span>'
        +   '</div>'
        + '</div>';
    }

    function setState(text, isError) {
      var box = q('rState');
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

    function button(text, onClick, extra) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'adm-btn link' + (extra === 'danger' ? ' danger' : '');
      btn.textContent = text;
      btn.addEventListener('click', onClick);
      return btn;
    }

    function renderRows(rows) {
      var tbody = q('rRows');
      tbody.innerHTML = '';
      if (!rows.length) {
        setState('暂无数据');
        return;
      }
      setState('');
      rows.forEach(function (role) {
        var tr = document.createElement('tr');
        cell(tr, role.roleName);
        cell(tr, role.roleKey);
        cell(tr, role.roleSort);

        var statusTd = document.createElement('td');
        var tag = document.createElement('span');
        var normal = String(role.status) === '0';
        tag.className = 'adm-tag ' + (normal ? 'ok' : 'off');
        tag.textContent = normal ? '正常' : '停用';
        statusTd.appendChild(tag);
        tr.appendChild(statusTd);

        cell(tr, role.createTime);

        var actions = document.createElement('td');
        actions.className = 'actions';
        if (String(role.roleId) !== '1') {
          if (AdminUI.can('system:role:edit') && AdminUI.can('system:role:query')) actions.appendChild(button('编辑', function () { openForm(role.roleId); }));
          if (AdminUI.can('system:role:edit') && AdminUI.can('system:role:query')) actions.appendChild(button('数据权限', function () { openDataScope(role.roleId); }));
          if (AdminUI.can('system:role:edit')) actions.appendChild(button(normal ? '停用' : '启用', function () { toggleStatus(role, !normal); }));
          if (AdminUI.can('system:role:remove')) actions.appendChild(button('删除', function () { removeRole(role); }, 'danger'));
        }
        tr.appendChild(actions);

        tbody.appendChild(tr);
      });
    }

    function renderFooter() {
      var pages = Math.max(1, Math.ceil(state.total / PAGE_SIZE));
      q('rSummary').textContent = '第 ' + state.page + ' / ' + pages + ' 页 · 共 ' + state.total + ' 条';
      q('rPrev').disabled = state.page <= 1;
      q('rNext').disabled = state.page >= pages;
    }

    async function load(page) {
      if (!state || state.loading) return;
      var requestState = state;
      var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
      listController = controller;
      state.loading = true;
      setState('加载中…');
      var params = new URLSearchParams({ pageNum: page, pageSize: PAGE_SIZE });
      if (state.roleName) params.set('roleName', state.roleName);
      if (state.roleKey) params.set('roleKey', state.roleKey);
      if (state.status !== '') params.set('status', state.status);

      try {
        var data = await EduApi.get('/system/role/list?' + params.toString(), { signal: controller && controller.signal });
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

    function labeled(text, control, required, full) {
      var field = document.createElement('div');
      field.className = 'adm-field' + (full ? ' full' : '');
      var label = document.createElement('label');
      label.textContent = text;
      if (control.id) label.htmlFor = control.id;
      if (required) label.className = 'req';
      field.appendChild(label);
      field.appendChild(control);
      return field;
    }

    function input(value, options) {
      var node = document.createElement('input');
      node.className = 'adm-input';
      node.type = (options && options.type) || 'text';
      node.value = value === null || value === undefined ? '' : value;
      if (options && options.id) node.id = options.id;
      if (options && options.placeholder) node.placeholder = options.placeholder;
      if (options && options.maxlength) node.maxLength = options.maxlength;
      return node;
    }

    function select(options, value, id) {
      var node = document.createElement('select');
      node.className = 'adm-select';
      if (id) node.id = id;
      options.forEach(function (item) {
        var option = document.createElement('option');
        option.value = String(item.value);
        option.textContent = item.label;
        option.disabled = !!item.disabled;
        if (String(item.value) === String(value)) option.selected = true;
        node.appendChild(option);
      });
      return node;
    }

    async function openForm(roleId) {
      var formState = state;
      var ctx = { submit: null };
      var modal = AdminUI.modal({
        title: roleId ? '编辑角色' : '新增角色',
        content: '加载中…',
        wide: true,
        okText: '保存',
        onOk: function () { return ctx.submit ? ctx.submit() : false; }
      });

      try {
        var role = { roleSort: 0, status: '0', dataScope: '5' };
        var menuData;
        if (roleId) {
          var results = await Promise.all([
            EduApi.get('/system/role/' + roleId),
            EduApi.get('/system/menu/roleMenuTreeselect/' + roleId)
          ]);
          role = results[0].data || role;
          menuData = { menus: results[1].menus || [], checkedKeys: results[1].checkedKeys || [] };
        } else {
          var menuTree = await EduApi.get('/system/menu/treeselect');
          menuData = { menus: menuTree.data || [], checkedKeys: [] };
        }
        if (disposed || state !== formState) { modal.close(); return; }
        buildForm(modal, role, roleId, menuData, ctx);
      } catch (error) {
        modal.setError('加载角色数据失败：' + (error.message || '未知错误'));
      }
    }

    function buildForm(modal, role, roleId, menuData, ctx) {
      var editing = !!roleId;
      var form = document.createElement('div');
      form.className = 'adm-form-grid';

      var roleName = input(role.roleName, { id: 'rfName', placeholder: '如：教务管理员', maxlength: 30 });
      var roleKey = input(role.roleKey, { id: 'rfKey', placeholder: '如：edu_admin', maxlength: 100 });
      var roleSort = input(role.roleSort === undefined ? 0 : role.roleSort, { id: 'rfSort', type: 'number' });
      var status = select([{ value: '0', label: '正常' }, { value: '1', label: '停用' }], role.status || '0', 'rfStatus');

      var scopeOptions = DATA_SCOPES.map(function (item) {
        return { value: item.value, label: item.label, disabled: item.value === '2' && String(role.dataScope) !== '2' };
      });
      var dataScope = select(scopeOptions, role.dataScope || '5', 'rfScope');

      form.appendChild(labeled('角色名称', roleName, true));
      form.appendChild(labeled('权限字符', roleKey, true));
      form.appendChild(labeled('显示顺序', roleSort, false));
      form.appendChild(labeled('状态', status, false));
      form.appendChild(labeled('数据范围', dataScope, false, true));

      var menuField = document.createElement('div');
      menuField.className = 'adm-field full';
      var menuLabel = document.createElement('label');
      menuLabel.textContent = '菜单权限';
      var treeBox = document.createElement('div');
      treeBox.id = 'rfMenus';
      var actions = document.createElement('div');
      actions.className = 'adm-tree-actions';
      var selectAll = document.createElement('button');
      selectAll.type = 'button';
      selectAll.className = 'adm-btn';
      selectAll.textContent = '全选';
      var clearAll = document.createElement('button');
      clearAll.type = 'button';
      clearAll.className = 'adm-btn';
      clearAll.textContent = '清空';
      actions.appendChild(selectAll);
      actions.appendChild(clearAll);

      var tree = AdminUI.menuTree(menuData.menus, menuData.checkedKeys);
      selectAll.addEventListener('click', function () { tree.setAll(true); });
      clearAll.addEventListener('click', function () { tree.setAll(false); });

      treeBox.appendChild(actions);
      treeBox.appendChild(tree.root);
      menuField.appendChild(menuLabel);
      menuField.appendChild(treeBox);

      var hint = document.createElement('p');
      hint.className = 'adm-hint';
      hint.textContent = '提交时会自动补齐所选菜单的上级节点；自定义部门范围请在列表的“数据权限”中配置。';
      menuField.appendChild(hint);

      form.appendChild(menuField);

      var remark = document.createElement('textarea');
      remark.className = 'adm-textarea';
      remark.id = 'rfRemark';
      remark.value = role.remark || '';
      remark.placeholder = '选填，最多 500 字';
      remark.maxLength = 500;
      form.appendChild(labeled('备注', remark, false, true));

      modal.body.innerHTML = '';
      modal.body.appendChild(form);

      ctx.submit = async function () {
        var submitState = state;
        if (!roleName.value.trim()) throw new Error('请填写角色名称');
        if (!roleKey.value.trim()) throw new Error('请填写权限字符');
        var sort = Number(roleSort.value);
        if (!Number.isFinite(sort) || sort < 0) throw new Error('显示顺序需为非负数字');

        var payload = {
          roleName: roleName.value.trim(),
          roleKey: roleKey.value.trim(),
          roleSort: sort,
          status: status.value,
          dataScope: dataScope.value,
          remark: remark.value.trim(),
          menuIds: tree.getCheckedIds(),
          menuCheckStrictly: true,
          deptCheckStrictly: role.deptCheckStrictly === true
        };

        if (editing) {
          payload.roleId = roleId;
          await EduApi.put('/system/role', payload);
        } else {
          await EduApi.post('/system/role', payload);
        }

        if (disposed || state !== submitState) return true;
        modal.close();
        AdminUI.toast(editing ? '已保存' : '已新增角色');
        await window.EduShell.refreshIdentity();
        return true;
      };

      var focusTarget = form.querySelector('input');
      if (focusTarget) focusTarget.focus();
    }

    async function openDataScope(roleId) {
      var snapshot = state;
      var controller = new AbortController();
      var closed = false;
      var submit = null;
      var modal = AdminUI.modal({
        title: '角色数据权限', content: '加载中…', wide: true, okText: '保存',
        onClose: function () { closed = true; controller.abort(); },
        onOk: function () { return submit ? submit() : false; }
      });
      try {
        var results = await Promise.all([
          EduApi.get('/system/role/' + roleId, { signal: controller.signal }),
          EduApi.get('/system/role/deptTree/' + roleId, { signal: controller.signal })
        ]);
        if (disposed || state !== snapshot || closed) return;
        var role = results[0].data;
        var form = document.createElement('div'); form.className = 'adm-form-grid';
        var scope = select(DATA_SCOPES, role.dataScope, 'roleDataScope');
        form.appendChild(labeled('数据范围', scope, true, true));
        var tree = AdminUI.menuTree(results[1].depts || [], results[1].checkedKeys || [], { includeAncestors: false });
        var field = labeled('授权部门', tree.root, false, true);
        form.appendChild(field);
        var hint = document.createElement('p'); hint.className = 'adm-hint full'; hint.textContent = '自定义范围只提交勾选的部门，不自动增加上级部门。'; form.appendChild(hint);
        function update() { field.hidden = scope.value !== '2'; }
        scope.addEventListener('change', update); update();
        modal.body.replaceChildren(form);
        submit = async function () {
          if (closed || disposed || state !== snapshot) return false;
          var ids = scope.value === '2' ? tree.getCheckedIds() : [];
          if (scope.value === '2' && !ids.length) throw new Error('请至少选择一个授权部门');
          await EduApi.put('/system/role/dataScope', { roleId: roleId, dataScope: scope.value, deptIds: ids, deptCheckStrictly: false }, { signal: controller.signal });
          if (closed || disposed || state !== snapshot) return true;
          modal.close(); AdminUI.toast('数据权限已保存'); await window.EduShell.refreshIdentity(); return true;
        };
      } catch (error) {
        if (!closed && !disposed && state === snapshot && !error.cancelled && !error.forbidden && !error.unauthorized) modal.setError('加载失败：' + error.message);
      }
    }

    async function toggleStatus(role, enable) {
      var operationState = state;
      var next = enable ? '0' : '1';
      var ok = await AdminUI.confirm(
        (enable ? '确定启用角色 ' : '确定停用角色 ') + role.roleName + ' 吗？' + (enable ? '' : '停用后该角色下的用户将失去对应权限。'),
        { danger: !enable, okText: enable ? '启用' : '停用' }
      );
      if (!ok || disposed || state !== operationState) return;
      try {
        await EduApi.put('/system/role/changeStatus', { roleId: role.roleId, status: next });
        if (disposed || state !== operationState) return;
        AdminUI.toast(enable ? '已启用' : '已停用');
        await window.EduShell.refreshIdentity();
      } catch (error) {
        AdminUI.toast(error.message || '操作失败', 'error');
      }
    }

    async function removeRole(role) {
      var operationState = state;
      var ok = await AdminUI.confirm('确定删除角色 ' + role.roleName + ' 吗？此操作不可恢复。', { danger: true, okText: '删除' });
      if (!ok || disposed || state !== operationState) return;
      try {
        await EduApi.del('/system/role/' + role.roleId);
        if (disposed || state !== operationState) return;
        AdminUI.toast('已删除');
        await window.EduShell.refreshIdentity();
      } catch (error) {
        AdminUI.toast(error.message || '删除失败', 'error');
      }
    }

    function bind() {
      q('rSearch').addEventListener('click', function () {
        state.roleName = q('rName').value.trim();
        state.roleKey = q('rKey').value.trim();
        state.status = q('rStatus').value;
        load(1);
      });
      ['rName', 'rKey'].forEach(function (id) {
        q(id).addEventListener('keydown', function (event) {
          if (event.key === 'Enter') { event.preventDefault(); q('rSearch').click(); }
        });
      });
      q('rReset').addEventListener('click', function () {
        q('rName').value = '';
        q('rKey').value = '';
        q('rStatus').value = '';
        state.roleName = '';
        state.roleKey = '';
        state.status = '';
        load(1);
      });
      q('rAdd').addEventListener('click', function () { openForm(null); });
      q('rAdd').hidden = !AdminUI.can('system:role:add');
      q('rPrev').addEventListener('click', function () { if (state.page > 1) load(state.page - 1); });
      q('rNext').addEventListener('click', function () {
        var pages = Math.max(1, Math.ceil(state.total / PAGE_SIZE));
        if (state.page < pages) load(state.page + 1);
      });
    }

    return {
      mount: function (mountHost) {
        host = mountHost;
        disposed = false;
        state = { page: 1, total: 0, roleName: '', roleKey: '', status: '', loading: false };
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
  window.EDU_PAGES.push({ g: 6, i: 1, page: Object.assign(pageModule(), { permission: 'system:role:list', route: 'system/role', roles: ['admin'] }) });
})();
