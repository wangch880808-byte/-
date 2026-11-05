/**
 * 6-02 视图宿主（第 6 组 6B）。
 *
 * 负责三件事：把菜单项映射到视图、维护"课程—教学班"上下文、在切换菜单时清理上一个视图。
 * 只接管注册过的菜单项；未注册项仍显示原"内容区（待实现）"占位。
 */
(function (global) {
  'use strict';

  var BINDING_KEY = 'zhijiao.bindingId';
  var TOKEN_KEY = 'zhijiao.login.token';
  var registry = {};
  var activeView = null;

  function key(role, groupIndex, itemIndex) {
    return role + ':' + groupIndex + ':' + itemIndex;
  }

  function register(role, groupIndex, itemIndex, factory) {
    registry[key(role, groupIndex, itemIndex)] = factory;
  }

  function getBinding() {
    try {
      return global.sessionStorage.getItem(BINDING_KEY) || '';
    } catch (e) {
      return '';
    }
  }

  function setBinding(bindingId) {
    try {
      if (bindingId) {
        global.sessionStorage.setItem(BINDING_KEY, bindingId);
      } else {
        global.sessionStorage.removeItem(BINDING_KEY);
      }
    } catch (e) {
      /* 存储不可用时不阻断页面，本次输入仍生效于当前视图 */
    }
  }

  function hasToken() {
    try {
      return Boolean(global.sessionStorage.getItem(TOKEN_KEY));
    } catch (e) {
      return false;
    }
  }

  /** 等 role-home.js 完成登录态核验；超时返回 false，由调用方按失败态处理。 */
  function whenAuthorized() {
    return new Promise(function (resolve) {
      var deadline = Date.now() + 10000;
      function tick() {
        if (hasToken() && !document.body.classList.contains('auth-pending')) {
          resolve(true);
          return;
        }
        if (Date.now() > deadline) {
          resolve(false);
          return;
        }
        global.setTimeout(tick, 120);
      }
      tick();
    });
  }

  function bindingFieldHtml(options) {
    var opts = options || {};
    var value = getBinding();
    return '<div class="edu-binding">'
      + '<label for="eduBindingInput">' + EduUI.esc(opts.label || '课程—教学班上下文') + '</label>'
      + '<input id="eduBindingInput" type="text" inputmode="numeric" autocomplete="off"'
      + ' placeholder="' + EduUI.esc(opts.placeholder || 'bindingId') + '" value="' + EduUI.esc(value) + '">'
      + '<button type="button" class="edu-btn" data-action="apply-binding">应用</button>'
      + (opts.hint ? '<span class="edu-note">' + EduUI.esc(opts.hint) + '</span>' : '')
      + '</div>';
  }

  function wireBindingField(root, onChange) {
    var input = root.querySelector('#eduBindingInput');
    var button = root.querySelector('[data-action="apply-binding"]');
    if (!input || !button) {
      return;
    }
    button.addEventListener('click', function () {
      var value = input.value.trim();
      if (value && !/^\d{1,19}$/.test(value)) {
        EduUI.toast('bindingId 只能由数字组成', 'warn');
        return;
      }
      setBinding(value);
      if (typeof onChange === 'function') {
        onChange(value);
      }
    });
    input.addEventListener('keydown', function (event) {
      if (event.key === 'Enter') {
        button.click();
      }
    });
  }

  function ensureRoot() {
    var content = document.querySelector('.content');
    if (!content) {
      return null;
    }
    var root = document.getElementById('viewRoot');
    if (!root) {
      root = document.createElement('div');
      root.id = 'viewRoot';
      root.className = 'edu-view-root';
      content.appendChild(root);
    }
    return root;
  }

  function clearActive() {
    if (activeView && typeof activeView.cleanup === 'function') {
      try {
        activeView.cleanup();
      } catch (e) {
        /* 清理失败不应阻断下一个视图 */
      }
    }
    activeView = null;
  }

  /**
   * 挂载视图。没有登记该菜单项时返回 false，调用方保留原占位。
   */
  function mount(role, groupIndex, itemIndex, menuLabel) {
    var root = ensureRoot();
    if (!root) {
      return false;
    }
    clearActive();
    var factory = registry[key(role, groupIndex, itemIndex)];
    var content = document.querySelector('.content');
    root.innerHTML = '';

    if (!factory) {
      content.classList.remove('view-active');
      root.hidden = true;
      return false;
    }

    root.hidden = false;
    content.classList.add('view-active');
    try {
      activeView = factory({
        root: root,
        bindingId: getBinding(),
        label: menuLabel || ''
      }) || null;
    } catch (error) {
      activeView = null;
      root.innerHTML = '<div class="edu-state edu-state-error">'
        + '<p class="edu-state-title">页面初始化失败</p>'
        + '<p class="edu-state-hint">' + (window.EduUI ? window.EduUI.esc(error.message) : '') + '</p>'
        + '</div>';
      if (window.console) {
        window.console.error('[EduViews] view init failed', error);
      }
    }
    return true;
  }

  global.EduViews = {
    register: register,
    mount: mount,
    getBinding: getBinding,
    setBinding: setBinding,
    whenAuthorized: whenAuthorized,
    bindingFieldHtml: bindingFieldHtml,
    wireBindingField: wireBindingField
  };
})(window);
