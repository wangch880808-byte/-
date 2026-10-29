/*
 * 管理员端页面公共交互：轻提示、确认框、表单弹窗、菜单勾选树、部门下拉。
 * 只依赖 DOM，不依赖 EduApi；所有文本用 textContent 写入，避免注入。
 */
(function () {
  'use strict';

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  var toastTimer = null;
  var openModals = new Set();
  function toast(message, type, duration) {
    var existing = document.querySelector('.adm-toast');
    if (existing) existing.remove();
    var box = el('div', 'adm-toast' + (type === 'error' ? ' error' : ''), message);
    document.body.appendChild(box);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { box.remove(); }, duration || 2200);
  }

  /**
   * 弹窗。options: { title, content(string|Node), wide, okText, cancelText, onOk }
   * onOk 返回 false 或抛错时保持打开；成功（返回 true/undefined）自动关闭。
   */
  function modal(options) {
    options = options || {};
    var lastFocus = document.activeElement;
    var closed = false;

    var mask = el('div', 'adm-mask');
    var dialog = el('div', 'adm-modal' + (options.wide ? ' wide' : ''));
    dialog.setAttribute('role', 'dialog');
    dialog.setAttribute('aria-modal', 'true');
    dialog.setAttribute('aria-label', options.title || '操作确认');
    var head = el('div', 'adm-modal-head');
    head.appendChild(el('h3', null, options.title || ''));
    var closeBtn = el('button', 'adm-modal-close', '×');
    closeBtn.type = 'button';
    closeBtn.setAttribute('aria-label', '关闭');
    head.appendChild(closeBtn);

    var body = el('div', 'adm-modal-body');
    if (options.content instanceof Node) body.appendChild(options.content);
    else body.textContent = options.content || '';

    var foot = el('div', 'adm-modal-foot');
    var error = el('span', 'adm-error-text');
    var cancel = el('button', 'adm-btn', options.cancelText || '取消');
    cancel.type = 'button';
    var ok = el('button', 'adm-btn primary' + (options.danger ? ' danger' : ''), options.okText || '确定');
    ok.type = 'button';
    foot.appendChild(error);
    if (!options.hideCancel) foot.appendChild(cancel);
    foot.appendChild(ok);

    dialog.appendChild(head);
    dialog.appendChild(body);
    dialog.appendChild(foot);
    mask.appendChild(dialog);
    document.body.appendChild(mask);

    function close() {
      if (closed) return;
      closed = true;
      openModals.delete(close);
      document.removeEventListener('keydown', onKey);
      mask.remove();
      if (lastFocus && lastFocus.focus) lastFocus.focus();
      if (options.onClose) options.onClose();
    }

    function setError(message) {
      if (closed) return;
      error.textContent = message || '';
    }

    function setBusy(busy) {
      if (closed) return;
      ok.disabled = busy;
      cancel.disabled = busy;
      closeBtn.disabled = busy;
      ok.textContent = busy ? '处理中…' : (options.okText || '确定');
    }

    async function submit() {
      if (closed || ok.disabled) return;
      if (!options.onOk) {
        close();
        return;
      }
      setError('');
      setBusy(true);
      try {
        var result = await options.onOk();
        if (result === false) {
          setBusy(false);
          return;
        }
        close();
      } catch (e) {
        setError(e && e.message ? e.message : '操作失败');
        setBusy(false);
      }
    }

    ok.addEventListener('click', submit);
    cancel.addEventListener('click', close);
    closeBtn.addEventListener('click', close);
    mask.addEventListener('mousedown', function (event) {
      if (event.target === mask) close();
    });
    function onKey(event) {
      if (event.key === 'Escape') close();
    }
    document.addEventListener('keydown', onKey);
    openModals.add(close);

    var firstInput = body.querySelector('input, select, textarea');
    if (firstInput) firstInput.focus();

    return { close: close, setError: setError, body: body, dialog: dialog };
  }

  function closeAll() {
    Array.from(openModals).forEach(function (close) { close(); });
  }

  function confirm(message, options) {
    options = options || {};
    return new Promise(function (resolve) {
      var settled = false;
      modal({
        title: options.title || '请确认',
        content: message,
        okText: options.okText || '确定',
        danger: options.danger,
        onOk: function () {
          settled = true;
          resolve(true);
          return true;
        },
        onClose: function () {
          if (!settled) {
            settled = true;
            resolve(false);
          }
        }
      });
    });
  }

  /**
   * 菜单勾选树。nodes 形如 [{ id, label, children }]。
   * 返回 { root, getCheckedIds, setAll }；getCheckedIds 会自动补齐祖先节点。
   */
  function menuTree(nodes, checkedIds, options) {
    options = options || {};
    var checked = {};
    (checkedIds || []).forEach(function (id) { checked[String(id)] = true; });

    var root = el('div', 'adm-tree');

    function build(items) {
      var ul = el('ul');
      items.forEach(function (item) {
        var li = el('li');
        var label = el('label');
        var input = document.createElement('input');
        input.type = 'checkbox';
        input.value = String(item.id);
        input.checked = !!checked[String(item.id)];
        input.addEventListener('change', function () {
          checked[String(item.id)] = input.checked;
        });
        label.appendChild(input);
        label.appendChild(el('span', null, item.label));
        li.appendChild(label);
        if (item.children && item.children.length) li.appendChild(build(item.children));
        ul.appendChild(li);
      });
      return ul;
    }

    root.appendChild(build(nodes || []));

    function getCheckedIds() {
      var ids = [];
      root.querySelectorAll('input[type="checkbox"]').forEach(function (input) {
        if (!input.checked) return;
        ids.push(Number(input.value));
        if (options.includeAncestors === false) return;
        var li = input.closest('li');
        while (li && li.parentElement && li.parentElement.closest('li')) {
          var parentLi = li.parentElement.closest('li');
          var parentInput = parentLi.querySelector('input[type="checkbox"]');
          if (parentInput) {
            var pid = Number(parentInput.value);
            if (ids.indexOf(pid) === -1) ids.push(pid);
          }
          li = parentLi;
        }
      });
      return ids;
    }

    function setAll(value) {
      root.querySelectorAll('input[type="checkbox"]').forEach(function (input) {
        input.checked = value;
        checked[input.value] = value;
      });
    }

    return { root: root, getCheckedIds: getCheckedIds, setAll: setAll };
  }

  /**
   * 部门平铺为下拉选项，按树形顺序并带缩进。
   */
  function deptOptions(list) {
    var byParent = {};
    var ids = new Set();
    var seen = new Set();
    (list || []).forEach(function (dept) { ids.add(String(dept.deptId)); });
    (list || []).forEach(function (dept) {
      var key = String(dept.parentId);
      (byParent[key] = byParent[key] || []).push(dept);
    });
    var result = [];
    function walk(parentId, depth) {
      (byParent[String(parentId)] || []).forEach(function (dept) {
        if (seen.has(String(dept.deptId))) return;
        seen.add(String(dept.deptId));
        result.push({
          value: dept.deptId,
          label: new Array(depth + 1).join('　') + dept.deptName
        });
        walk(dept.deptId, depth + 1);
      });
    }
    (list || []).forEach(function (dept) { if (!ids.has(String(dept.parentId))) walk(dept.parentId, 0); });
    (list || []).forEach(function (dept) { if (!seen.has(String(dept.deptId))) walk(dept.parentId, 0); });
    return result;
  }

  var BUSINESS_TYPES = {
    '0': '其它', '1': '新增', '2': '修改', '3': '删除', '4': '授权',
    '5': '导出', '6': '导入', '7': '强退', '8': '生成代码', '9': '清空数据'
  };

  function businessType(code) {
    return BUSINESS_TYPES[String(code)] || ('类型 ' + code);
  }

  window.AdminUI = {
    can: function (permission) { return !!window.EduAuth && window.EduAuth.has(permission); },
    el: el,
    toast: toast,
    modal: modal,
    closeAll: closeAll,
    confirm: confirm,
    menuTree: menuTree,
    deptOptions: deptOptions,
    businessType: businessType
  };
})();
