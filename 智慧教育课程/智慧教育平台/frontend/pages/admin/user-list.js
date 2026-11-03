/*
 * 管理员 · 用户管理（1-04）
 *
 * 列表 + 搜索 + 分页 + 新增/编辑 + 停用启用 + 重置密码 + 删除。
 * 接口：/system/user/list、/system/user/{id}、/system/user、/system/user/changeStatus、
 *       /system/user/resetPwd、/system/dept/list、/system/user/（角色与岗位选项）
 *
 * 注册位置：系统管理（g=6）→ 用户管理（i=0）。
 */
(function () {
  'use strict';

  var PAGE_SIZE = 10;

  function pageModule() {
    var host = null;
    var state = null;
    var disposed = false;
    var listController = null;
    var lookups = { roles: null, depts: null };

    function q(id) { return host.querySelector('#' + id); }

    function template() {
      return ''
        + '<div class="adm-page">'
        +   '<div class="adm-toolbar">'
        +     '<input class="adm-input" id="uKeyword" placeholder="按账号搜索" aria-label="按账号搜索">'
        +     '<input class="adm-input" id="uPhone" placeholder="按手机号搜索" aria-label="按手机号搜索">'
        +     '<select class="adm-select" id="uStatus" aria-label="状态筛选">'
        +       '<option value="">全部状态</option>'
        +       '<option value="0">正常</option>'
        +       '<option value="1">停用</option>'
        +     '</select>'
        +     '<button type="button" class="adm-btn primary" id="uSearch">查询</button>'
        +     '<button type="button" class="adm-btn" id="uReset">重置</button>'
        +     '<span class="adm-spacer"></span>'
        +     '<button type="button" class="adm-btn primary" id="uAdd">新增用户</button>'
        +   '</div>'
        +   '<div class="adm-table-wrap">'
        +     '<table class="adm-table">'
        +       '<thead><tr><th>账号</th><th>昵称</th><th>部门</th><th>手机</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>'
        +       '<tbody id="uRows"></tbody>'
        +     '</table>'
        +     '<div class="adm-state" id="uState">加载中…</div>'
        +   '</div>'
        +   '<div class="adm-footer">'
        +     '<span id="uSummary">—</span>'
        +     '<span>'
        +       '<button type="button" class="adm-btn" id="uPrev" disabled>上一页</button> '
        +       '<button type="button" class="adm-btn" id="uNext" disabled>下一页</button>'
        +     '</span>'
        +   '</div>'
        + '</div>';
    }

    function setState(text, isError) {
      var box = q('uState');
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
      var tbody = q('uRows');
      tbody.innerHTML = '';
      if (!rows.length) {
        setState('暂无数据');
        return;
      }
      setState('');
      rows.forEach(function (user) {
        var tr = document.createElement('tr');
        cell(tr, user.userName);
        cell(tr, user.nickName, 'ellipsis');
        cell(tr, user.dept && user.dept.deptName, 'ellipsis');
        cell(tr, user.phonenumber);

        var statusTd = document.createElement('td');
        var tag = document.createElement('span');
        var normal = String(user.status) === '0';
        tag.className = 'adm-tag ' + (normal ? 'ok' : 'off');
        tag.textContent = normal ? '正常' : '停用';
        statusTd.appendChild(tag);
        tr.appendChild(statusTd);

        cell(tr, user.createTime);

        var actionTd = document.createElement('td');
        actionTd.className = 'actions';
        if (String(user.userId) !== '1') {
          if (AdminUI.can('system:user:edit') && AdminUI.can('system:user:query')) actionTd.appendChild(button('编辑', function () { openForm(user.userId); }));
          if (AdminUI.can('system:user:resetPwd')) actionTd.appendChild(button('重置密码', function () { resetPassword(user); }));
          if (AdminUI.can('system:user:edit')) actionTd.appendChild(button(normal ? '停用' : '启用', function () { toggleStatus(user, !normal); }));
          if (AdminUI.can('system:user:remove')) actionTd.appendChild(button('删除', function () { removeUser(user); }, 'danger'));
        }
        tr.appendChild(actionTd);

        tbody.appendChild(tr);
      });
    }

    function button(text, onClick, extra) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'adm-btn link' + (extra === 'danger' ? ' danger' : '');
      btn.textContent = text;
      btn.addEventListener('click', onClick);
      return btn;
    }

    function renderFooter() {
      var pages = Math.max(1, Math.ceil(state.total / PAGE_SIZE));
      q('uSummary').textContent = '第 ' + state.page + ' / ' + pages + ' 页 · 共 ' + state.total + ' 条';
      q('uPrev').disabled = state.page <= 1;
      q('uNext').disabled = state.page >= pages;
    }

    async function load(page) {
      if (!state || state.loading) return;
      var requestState = state;
      var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
      listController = controller;
      state.loading = true;
      setState('加载中…');
      var params = new URLSearchParams({ pageNum: page, pageSize: PAGE_SIZE });
      if (state.keyword) params.set('userName', state.keyword);
      if (state.phone) params.set('phonenumber', state.phone);
      if (state.status !== '') params.set('status', state.status);

      try {
        var data = await EduApi.get('/system/user/list?' + params.toString(), { signal: controller && controller.signal });
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

    async function ensureLookups() {
      var results = await Promise.all([
        EduApi.get('/system/user/'),
        EduApi.get('/system/dept/list')
      ]);
      lookups.roles = (results[0] && results[0].roles) || [];
      lookups.depts = (results[1] && results[1].data) || [];
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
      if (options && options.disabled) node.disabled = true;
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
        if (String(item.value) === String(value)) option.selected = true;
        node.appendChild(option);
      });
      return node;
    }

    async function openForm(userId) {
      var formState = state;
      var ctx = { submit: null };
      var modal = AdminUI.modal({
        title: userId ? '编辑用户' : '新增用户',
        content: '加载中…',
        wide: true,
        okText: '保存',
        onOk: function () { return ctx.submit ? ctx.submit() : false; }
      });

      try {
        await ensureLookups();
        if (disposed || state !== formState) { modal.close(); return; }
        var detail = { user: { status: '0', sex: '0' }, roleIds: [], postIds: [] };
        if (userId) {
          var resp = await EduApi.get('/system/user/' + userId);
          detail.user = resp.data || {};
          detail.roleIds = resp.roleIds || [];
          detail.postIds = resp.postIds || [];
        }
        if (disposed || state !== formState) { modal.close(); return; }
        buildForm(modal, detail, userId, ctx);
      } catch (error) {
        modal.setError('加载表单数据失败：' + (error.message || '未知错误'));
      }
    }

    function buildForm(modal, detail, userId, ctx) {
      var user = detail.user || {};
      var editing = !!userId;

      var form = document.createElement('div');
      form.className = 'adm-form-grid';

      var userName = input(user.userName, { id: 'ufUserName', disabled: editing, placeholder: '2-20 个字符', maxlength: 30 });
      var nickName = input(user.nickName, { id: 'ufNickName', placeholder: '真实姓名 / 显示名', maxlength: 30 });
      var deptOptions = AdminUI.deptOptions(lookups.depts);
      var dept = select(deptOptions.length ? deptOptions : [{ value: '', label: '无部门' }], user.deptId, 'ufDept');
      var phone = input(user.phonenumber, { id: 'ufPhone', placeholder: '11 位手机号', maxlength: 11 });
      var email = input(user.email, { id: 'ufEmail', placeholder: '选填', maxlength: 50 });
      var sex = select([{ value: '0', label: '男' }, { value: '1', label: '女' }, { value: '2', label: '未知' }], user.sex || '0', 'ufSex');
      var status = select([{ value: '0', label: '正常' }, { value: '1', label: '停用' }], user.status || '0', 'ufStatus');
      var password = input('', { id: 'ufPassword', type: 'password', placeholder: '5-20 个字符', maxlength: 20 });

      form.appendChild(labeled('账号', userName, true));
      form.appendChild(labeled('昵称', nickName, true));
      form.appendChild(labeled('部门', dept, false));
      form.appendChild(labeled('手机号', phone, false));
      form.appendChild(labeled('邮箱', email, false));
      form.appendChild(labeled('性别', sex, false));
      form.appendChild(labeled('状态', status, false));
      if (!editing) form.appendChild(labeled('初始密码', password, true));

      var roleField = document.createElement('div');
      roleField.className = 'adm-field full';
      var roleLabel = document.createElement('label');
      roleLabel.className = 'req';
      roleLabel.textContent = '角色';
      var roleBox = document.createElement('div');
      roleBox.className = 'adm-check-list';
      roleBox.id = 'ufRoles';
      var selected = (detail.roleIds || []).map(String);
      (lookups.roles || []).forEach(function (role) {
        var label = document.createElement('label');
        label.className = 'adm-check';
        var box = document.createElement('input');
        box.type = 'checkbox';
        box.value = String(role.roleId);
        box.checked = selected.indexOf(String(role.roleId)) !== -1;
        label.appendChild(box);
        var span = document.createElement('span');
        span.textContent = role.roleName + '（' + role.roleKey + '）';
        label.appendChild(span);
        roleBox.appendChild(label);
      });
      roleField.appendChild(roleLabel);
      roleField.appendChild(roleBox);
      form.appendChild(roleField);

      var remark = document.createElement('textarea');
      remark.className = 'adm-textarea';
      remark.id = 'ufRemark';
      remark.value = user.remark || '';
      remark.placeholder = '选填，最多 500 字';
      remark.maxLength = 500;
      form.appendChild(labeled('备注', remark, false, true));

      modal.body.innerHTML = '';
      modal.body.appendChild(form);

      ctx.submit = async function () {
        var submitState = state;
        var payload = {
          userName: userName.value.trim(),
          nickName: nickName.value.trim(),
          deptId: dept.value ? Number(dept.value) : null,
          phonenumber: phone.value.trim(),
          email: email.value.trim(),
          sex: sex.value,
          status: status.value,
          remark: remark.value.trim(),
          roleIds: Array.prototype.slice.call(roleBox.querySelectorAll('input:checked')).map(function (box) {
            return Number(box.value);
          }),
          postIds: (detail.postIds || []).slice()
        };

        validate(payload, editing, password.value);

        if (editing) {
          payload.userId = userId;
          await EduApi.put('/system/user', payload);
        } else {
          payload.password = password.value;
          await EduApi.post('/system/user', payload);
        }

        if (disposed || state !== submitState) return true;
        modal.close();
        AdminUI.toast(editing ? '已保存' : '已新增用户');
        var activeIdentity = window.EduAuth && window.EduAuth.getIdentity();
        if (editing && activeIdentity && String(activeIdentity.userId) === String(userId)) {
          await window.EduShell.refreshIdentity();
          return true;
        }
        load(state.page);
        return true;
      };

      var focusTarget = form.querySelector('input:not([disabled]), select');
      if (focusTarget) focusTarget.focus();
    }

    function validate(payload, editing, password) {
      if (!payload.userName || payload.userName.length < 2) throw new Error('账号至少 2 个字符');
      if (!payload.nickName) throw new Error('请填写昵称');
      if (!editing && (!password || password.length < 5 || password.length > 20)) throw new Error('密码需为 5-20 个字符');
      if (payload.phonenumber && !/^1[3-9]\d{9}$/.test(payload.phonenumber)) throw new Error('手机号格式不正确');
      if (payload.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email)) throw new Error('邮箱格式不正确');
      if (!payload.roleIds.length) throw new Error('请至少选择一个角色');
    }

    function resetPassword(user) {
      var box = document.createElement('div');
      var field = document.createElement('div');
      field.className = 'adm-field';
      var label = document.createElement('label');
      label.className = 'req';
      label.textContent = '新密码（5-20 个字符）';
      var pwd = input('', { id: 'ufNewPwd', type: 'password', maxlength: 20 });
      field.appendChild(label);
      field.appendChild(pwd);
      box.appendChild(field);
      var hint = document.createElement('p');
      hint.className = 'adm-hint';
      hint.textContent = '将重置账号 ' + user.userName + ' 的登录密码。';
      box.appendChild(hint);

      AdminUI.modal({
        title: '重置密码',
        content: box,
        okText: '重置',
        onOk: async function () {
          if (!pwd.value || pwd.value.length < 5 || pwd.value.length > 20) throw new Error('密码需为 5-20 个字符');
          await EduApi.put('/system/user/resetPwd', { userId: user.userId, password: pwd.value });
          AdminUI.toast('密码已重置');
          return true;
        }
      });
    }

    async function toggleStatus(user, enable) {
      var operationState = state;
      var next = enable ? '0' : '1';
      var ok = await AdminUI.confirm(
        (enable ? '确定启用 ' : '确定停用 ') + user.userName + ' 吗？' + (enable ? '' : '停用后该账号将无法登录。'),
        { danger: !enable, okText: enable ? '启用' : '停用' }
      );
      if (!ok || disposed || state !== operationState) return;
      try {
        await EduApi.put('/system/user/changeStatus', { userId: user.userId, status: next });
        if (disposed || state !== operationState) return;
        AdminUI.toast(enable ? '已启用' : '已停用');
        load(state.page);
      } catch (error) {
        AdminUI.toast(error.message || '操作失败', 'error');
      }
    }

    async function removeUser(user) {
      var operationState = state;
      var ok = await AdminUI.confirm('确定删除账号 ' + user.userName + ' 吗？此操作不可恢复。', { danger: true, okText: '删除' });
      if (!ok || disposed || state !== operationState) return;
      try {
        await EduApi.del('/system/user/' + user.userId);
        if (disposed || state !== operationState) return;
        AdminUI.toast('已删除');
        load(state.page);
      } catch (error) {
        AdminUI.toast(error.message || '删除失败', 'error');
      }
    }

    function bind() {
      q('uSearch').addEventListener('click', function () {
        state.keyword = q('uKeyword').value.trim();
        state.phone = q('uPhone').value.trim();
        state.status = q('uStatus').value;
        load(1);
      });
      q('uKeyword').addEventListener('keydown', function (event) {
        if (event.key === 'Enter') { event.preventDefault(); q('uSearch').click(); }
      });
      q('uPhone').addEventListener('keydown', function (event) {
        if (event.key === 'Enter') { event.preventDefault(); q('uSearch').click(); }
      });
      q('uReset').addEventListener('click', function () {
        q('uKeyword').value = '';
        q('uPhone').value = '';
        q('uStatus').value = '';
        state.keyword = '';
        state.phone = '';
        state.status = '';
        load(1);
      });
      q('uAdd').addEventListener('click', function () { openForm(null); });
      q('uAdd').hidden = !AdminUI.can('system:user:add');
      q('uPrev').addEventListener('click', function () { if (state.page > 1) load(state.page - 1); });
      q('uNext').addEventListener('click', function () {
        var pages = Math.max(1, Math.ceil(state.total / PAGE_SIZE));
        if (state.page < pages) load(state.page + 1);
      });
    }

    return {
      mount: function (mountHost) {
        host = mountHost;
        disposed = false;
        state = { page: 1, total: 0, keyword: '', phone: '', status: '', loading: false };
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
  window.EDU_PAGES.push({ g: 6, i: 0, page: Object.assign(pageModule(), { permission: 'system:user:list', route: 'system/user', roles: ['admin'] }) });
})();
