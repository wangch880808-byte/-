/* ==========================================================================
   edu-ui.js —— 业务页面的公共组件（第 2 组 2B）
   --------------------------------------------------------------------------
   这里只做「长得一样、行为一样」的东西，避免 5 个页面各写一遍：
     · 列表页骨架 EduUI.listView()：页头 + 筛选 + 表格 + 分页 + 四种状态
     · 弹窗表单   EduUI.modal()  ：声明式字段，自动校验、自动 loading
     · 二次确认   EduUI.confirm()
     · 轻提示     EduUI.toast()
     · 状态/加载   EduUI.state() / EduUI.loading()
   页面代码只描述「有哪些列、有哪些筛选、点按钮干什么」，不碰 DOM 细节。
   ========================================================================== */
(function (window, document) {
  'use strict';

  var EduUI = {};

  /* ---------- 1. 小工具 ---------- */

  // 创建元素：el('div', {class:'a', text:'x', onclick:fn}, [子元素])
  function el(tag, attrs, children) {
    var node = document.createElement(tag);
    if (attrs) {
      for (var key in attrs) {
        if (!Object.prototype.hasOwnProperty.call(attrs, key)) continue;
        var value = attrs[key];
        if (value === undefined || value === null || value === false) continue;
        if (key === 'text') node.textContent = value;
        else if (key === 'html') node.innerHTML = value;
        else if (key === 'class') node.className = value;
        else if (key === 'style') node.setAttribute('style', value);
        else if (key.indexOf('on') === 0 && typeof value === 'function') node.addEventListener(key.slice(2), value);
        else if (key === 'dataset') { for (var d in value) node.dataset[d] = value[d]; }
        else node.setAttribute(key, value === true ? '' : value);
      }
    }
    if (children) {
      (Array.isArray(children) ? children : [children]).forEach(function (child) {
        if (child === null || child === undefined || child === false) return;
        node.appendChild(typeof child === 'string' || typeof child === 'number'
          ? document.createTextNode(String(child)) : child);
      });
    }
    return node;
  }

  function clear(node) { while (node && node.firstChild) node.removeChild(node.firstChild); }

  // 日期：后端可能给 '2026-09-01'、'2026-09-01 00:00:00' 或时间戳，统一显示成 2026-09-01
  function fmtDate(value) {
    if (value === null || value === undefined || value === '') return '—';
    if (typeof value === 'number') {
      var d = new Date(value);
      return isNaN(d.getTime()) ? '—' : d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
    }
    return String(value).slice(0, 10);
  }
  function pad(n) { return n < 10 ? '0' + n : '' + n; }

  function text(value, fallback) {
    if (value === null || value === undefined || value === '') return fallback === undefined ? '—' : fallback;
    return String(value);
  }

  // 标签：把状态码翻译成中文 + 颜色，避免每个页面各写一套 if
  function tag(label, type) {
    return el('span', { class: 'edu-tag edu-tag-' + (type || 'grey'), text: label });
  }

  /* ---------- 2. 轻提示 ---------- */
  var toastWrap = null;
  function toast(message, type) {
    if (!toastWrap) {
      toastWrap = el('div', { class: 'edu-toast-wrap' });
      document.body.appendChild(toastWrap);
    }
    var node = el('div', { class: 'edu-toast' + (type ? ' edu-toast-' + type : ''), text: message });
    toastWrap.appendChild(node);
    setTimeout(function () {
      if (node.parentNode) node.parentNode.removeChild(node);
    }, type === 'error' ? 3200 : 2200);
  }

  /* ---------- 3. 四种状态 ---------- */
  var STATE_PRESET = {
    loading: { icon: '', title: '正在加载…' },
    empty: { icon: '▤', title: '暂无数据' },
    error: { icon: '!', title: '加载失败' },
    forbidden: { icon: '⊘', title: '你没有权限查看此内容' },
    unavailable: { icon: '◷', title: '该页暂未开放' }
  };

  function state(kind, options) {
    options = options || {};
    var preset = STATE_PRESET[kind] || STATE_PRESET.empty;
    var box = el('div', { class: 'edu-state' });

    if (kind === 'loading') {
      var skel = el('div', { class: 'edu-skeleton', style: 'width:100%;max-width:680px' });
      for (var i = 0; i < 5; i++) {
        skel.appendChild(el('div', { class: 'edu-skel-row', style: 'width:' + (100 - i * 9) + '%' }));
      }
      box.appendChild(skel);
      return box;
    }

    box.appendChild(el('div', { class: 'edu-state-icon', text: options.icon || preset.icon }));
    box.appendChild(el('div', { class: 'edu-state-title', text: options.title || preset.title }));
    if (options.desc) box.appendChild(el('div', { class: 'edu-state-desc', text: options.desc }));

    if (options.actions && options.actions.length) {
      var actions = el('div', { class: 'edu-state-actions' });
      options.actions.forEach(function (action) {
        actions.appendChild(el('button', {
          class: 'edu-btn' + (action.primary ? ' edu-btn-primary' : ''),
          type: 'button',
          text: action.text,
          onclick: action.onClick
        }));
      });
      box.appendChild(actions);
    }
    return box;
  }

  /* ---------- 4. 弹窗 ---------- */
  /**
   * EduUI.modal({
   *   title, width, values, fields:[{key,label,type,options,placeholder,required,hint,validate,disabled,
   *                                  min,max,span}],
   *   submitText, onSubmit(values, helpers) -> Promise
   * })
   * onSubmit 返回 Promise：resolve 关闭弹窗，reject 弹窗保留并显示错误。
   */
  function modal(config) {
    var mask = el('div', { class: 'edu-mask' });
    var dialog = el('div', { class: 'edu-modal', style: config.width ? 'width:' + config.width + 'px' : '' });
    var body = el('div', { class: 'edu-modal-body' });
    var foot = el('div', { class: 'edu-modal-foot' });
    var values = {};
    var controls = {};
    var errorNodes = {};

    // 复制初始值（含默认值），避免直接改调用方对象
    (config.fields || []).forEach(function (field) {
      values[field.key] = config.values && config.values[field.key] !== undefined
        ? config.values[field.key]
        : (field.defaultValue !== undefined ? field.defaultValue : '');
    });

    function close() { if (mask.parentNode) mask.parentNode.removeChild(mask); }

    function showError(key, message) {
      var node = errorNodes[key];
      if (!node) return;
      node.textContent = message || '';
      node.style.display = message ? 'block' : 'none';
    }

    function collect() {
      (config.fields || []).forEach(function (field) {
        var control = controls[field.key];
        if (!control) return;
        if (field.type === 'checkbox') values[field.key] = control.checked;
        else values[field.key] = control.value;
      });
    }

    function validate() {
      var ok = true;
      (config.fields || []).forEach(function (field) {
        showError(field.key, '');
        if (field.type === 'checkbox') return;
        var value = controls[field.key] ? controls[field.key].value : '';
        if (field.required && String(value).trim() === '') {
          showError(field.key, '请填写' + field.label);
          ok = false;
          return;
        }
        if (field.validate) {
          var message = field.validate(value, values);
          if (message) { showError(field.key, message); ok = false; }
        }
      });
      return ok;
    }

    (config.fields || []).forEach(function (field) {
      var control;
      var attrs = { class: '', id: 'edu-f-' + field.key, placeholder: field.placeholder || '' };
      if (field.disabled) attrs.disabled = true;
      if (field.placeholder) attrs.placeholder = field.placeholder;

      if (field.type === 'select') {
        attrs.class = 'edu-select';
        control = el('select', attrs);
        control.appendChild(el('option', { value: '', text: field.placeholder || '请选择' }));
        (field.options || []).forEach(function (option) {
          control.appendChild(el('option', { value: option.value, text: option.label }));
        });
      } else if (field.type === 'textarea') {
        attrs.class = 'edu-textarea';
        control = el('textarea', attrs);
      } else if (field.type === 'checkbox') {
        control = el('input', { type: 'checkbox', id: 'edu-f-' + field.key });
      } else if (field.type === 'number') {
        attrs.class = 'edu-input';
        attrs.type = 'number';
        if (field.min !== undefined) attrs.min = field.min;
        if (field.max !== undefined) attrs.max = field.max;
        control = el('input', attrs);
      } else if (field.type === 'date') {
        attrs.class = 'edu-input';
        attrs.type = 'date';
        control = el('input', attrs);
      } else {
        attrs.class = 'edu-input';
        attrs.type = 'text';
        control = el('input', attrs);
      }

      control.value = values[field.key] === undefined || values[field.key] === null ? '' : values[field.key];
      if (field.type === 'checkbox') control.checked = !!values[field.key];
      controls[field.key] = control;

      var label = el('label', {
        class: field.required ? 'edu-required' : '',
        text: field.label
      });
      if (field.type === 'checkbox') label.style.lineHeight = '32px';

      var controlBox = el('div', { class: 'edu-form-control' });
      if (field.type === 'checkbox') {
        controlBox.appendChild(el('label', { class: 'edu-checkbox' }, [control, el('span', { text: field.checkboxText || '是' })]));
      } else {
        controlBox.appendChild(control);
      }
      if (field.hint) controlBox.appendChild(el('div', { class: 'edu-form-hint', text: field.hint }));
      errorNodes[field.key] = el('div', { class: 'edu-form-error', style: 'display:none' });
      controlBox.appendChild(errorNodes[field.key]);

      // 没有 label 的字段（如整行说明）直接铺满
      var item = el('div', { class: 'edu-form-item' }, field.label ? [label, controlBox] : [controlBox]);
      body.appendChild(item);
    });

    var submitButton = el('button', {
      class: 'edu-btn edu-btn-primary',
      type: 'button',
      text: config.submitText || '确定',
      onclick: function () {
        collect();
        if (!validate()) return;
        var result = config.onSubmit ? config.onSubmit(values, { close: close, showError: showError }) : null;
        if (!result || typeof result.then !== 'function') { close(); return; }
        submitButton.disabled = true;
        submitButton.textContent = '提交中…';
        result.then(function (keepOpen) {
          submitButton.disabled = false;
          submitButton.textContent = config.submitText || '确定';
          if (keepOpen !== false) close();
        }, function (error) {
          submitButton.disabled = false;
          submitButton.textContent = config.submitText || '确定';
          toast((error && error.message) || '提交失败，请重试', 'error');
        });
      }
    });

    foot.appendChild(el('button', { class: 'edu-btn', type: 'button', text: '取消', onclick: close }));
    foot.appendChild(submitButton);

    dialog.appendChild(el('div', { class: 'edu-modal-head' }, [
      el('span', { text: config.title || '' }),
      el('button', { class: 'edu-modal-close', type: 'button', html: '&times;', onclick: close })
    ]));
    dialog.appendChild(body);
    dialog.appendChild(foot);
    mask.appendChild(dialog);

    // 点遮罩关闭；点弹窗内部不关
    mask.addEventListener('click', function (event) {
      if (event.target === mask) close();
    });

    document.body.appendChild(mask);
    // 自动聚焦第一个可编辑控件，减少一次点击
    var first = body.querySelector('input:not([disabled]), select:not([disabled]), textarea:not([disabled])');
    if (first) first.focus();
    return { close: close, values: values };
  }

  /* ---------- 5. 二次确认 ---------- */
  function confirm(config) {
    return new Promise(function (resolve) {
      var mask = el('div', { class: 'edu-mask' });
      var dialog = el('div', { class: 'edu-modal', style: 'width:420px' });
      function done(result) {
        if (mask.parentNode) mask.parentNode.removeChild(mask);
        resolve(result);
      }
      dialog.appendChild(el('div', { class: 'edu-modal-head' }, [el('span', { text: config.title || '请确认' })]));
      dialog.appendChild(el('div', { class: 'edu-modal-body' }, [
        el('div', { class: 'edu-paragraph', text: config.message || '' })
      ]));
      dialog.appendChild(el('div', { class: 'edu-modal-foot' }, [
        el('button', { class: 'edu-btn', type: 'button', text: config.cancelText || '取消', onclick: function () { done(false); } }),
        el('button', {
          class: 'edu-btn ' + (config.danger ? 'edu-btn-danger' : 'edu-btn-primary'),
          type: 'button',
          text: config.okText || '确定',
          onclick: function () { done(true); }
        })
      ]));
      mask.appendChild(dialog);
      mask.addEventListener('click', function (event) { if (event.target === mask) done(false); });
      document.body.appendChild(mask);
    });
  }

  /* ---------- 6. 列表页骨架 ---------- */
  /**
   * EduUI.listView(host, {
   *   title, subtitle,
   *   actions: [{text, primary, onClick(ctx)}],
   *   filters: [{key, type:'input'|'select', label, placeholder, options, width}],
   *   search(query): Promise<{rows,total}>,
   *   columns: [{key,label,width,render(row)}],
   *   rowActions(row): [{text, danger, onClick(row, ctx)}],
   *   pageSize, emptyText, emptyHint, emptyAction, alert
   * })
   * 返回 ctx：{ reload(), query(), params() }
   */
  function listView(host, config) {
    // 先抓住公共外壳里的标题节点，再清空内容区，否则节点的引用就丢了
    var pageTitle = document.getElementById('pageTitle');
    clear(host);
    var page = el('div', { class: 'edu-page' });
    var query = { pageNum: 1, pageSize: config.pageSize || 10 };
    var bodyHost = el('div');
    var pagerHost = el('div');

    /* 页头 */
    var head = el('div', { class: 'edu-head' });
    var headLeft = el('div');
    if (pageTitle) {
      pageTitle.style.display = '';
      headLeft.appendChild(pageTitle);
    } else {
      headLeft.appendChild(el('div', { class: 'page-title', text: config.title || '' }));
    }
    if (config.subtitle) headLeft.appendChild(el('div', { class: 'edu-head-sub', text: config.subtitle }));
    head.appendChild(headLeft);

    var ctx = {
      query: function () { return query; },
      params: function () {
        var params = {};
        for (var key in query) params[key] = query[key];
        return params;
      },
      reload: function () { return load(); }
    };

    if (config.actions && config.actions.length) {
      var actions = el('div', { class: 'edu-head-actions' });
      config.actions.forEach(function (action) {
        actions.appendChild(el('button', {
          class: 'edu-btn' + (action.primary ? ' edu-btn-primary' : ''),
          type: 'button',
          text: action.text,
          onclick: function () { action.onClick(ctx); }
        }));
      });
      head.appendChild(actions);
    }
    page.appendChild(head);

    /* 提示条 */
    if (config.alert) {
      page.appendChild(el('div', { class: 'edu-alert edu-alert-info' }, [
        el('span', { text: 'ⓘ' }),
        el('span', { text: config.alert })
      ]));
    }

    /* 筛选栏 */
    if (config.filters && config.filters.length) {
      var toolbar = el('div', { class: 'edu-toolbar' });
      config.filters.forEach(function (filter) {
        var control;
        if (filter.type === 'select') {
          control = el('select', { class: 'edu-select', style: filter.width ? 'width:' + filter.width + 'px' : '' });
          control.appendChild(el('option', { value: '', text: filter.placeholder || '全部' }));
          (filter.options || []).forEach(function (option) {
            control.appendChild(el('option', { value: option.value, text: option.label }));
          });
        } else {
          control = el('input', {
            class: 'edu-input',
            type: 'text',
            placeholder: filter.placeholder || '',
            style: filter.width ? 'width:' + filter.width + 'px' : ''
          });
        }
        control.addEventListener('keydown', function (event) {
          if (event.key === 'Enter') doSearch();
        });
        // 异步下拉（如「学期」需要先请求一次接口）：先渲染空下拉，选项到了再补
        if (filter.optionsPromise) {
          filter.optionsPromise.then(function (options) {
            (options || []).forEach(function (option) {
              control.appendChild(el('option', { value: option.value, text: option.label }));
            });
          });
        }
        filter.__control = control;
        toolbar.appendChild(el('span', { class: 'edu-field' }, [
          filter.label ? el('span', { text: filter.label }) : null,
          control
        ]));
      });

      function doSearch() {
        query.pageNum = 1;
        config.filters.forEach(function (filter) {
          query[filter.key] = filter.__control ? filter.__control.value.trim() : '';
        });
        load();
      }

      toolbar.appendChild(el('button', { class: 'edu-btn edu-btn-primary', type: 'button', text: '查询', onclick: doSearch }));
      toolbar.appendChild(el('button', {
        class: 'edu-btn', type: 'button', text: '重置', onclick: function () {
          config.filters.forEach(function (filter) { if (filter.__control) filter.__control.value = ''; });
          doSearch();
        }
      }));
      page.appendChild(toolbar);
    }

    page.appendChild(bodyHost);
    page.appendChild(pagerHost);
    host.appendChild(page);

    /* ---- 加载与渲染 ---- */
    function renderPager(total) {
      clear(pagerHost);
      if (!total) return;
      var pageCount = Math.max(1, Math.ceil(total / query.pageSize));
      var pager = el('div', { class: 'edu-pager' });
      pager.appendChild(el('span', { text: '共 ' + total + ' 条' }));
      pager.appendChild(el('button', {
        class: 'edu-btn edu-btn-sm', type: 'button', text: '上一页',
        disabled: query.pageNum <= 1,
        onclick: function () { query.pageNum--; load(); }
      }));
      pager.appendChild(el('span', { class: 'edu-page-num', text: query.pageNum + ' / ' + pageCount }));
      pager.appendChild(el('button', {
        class: 'edu-btn edu-btn-sm', type: 'button', text: '下一页',
        disabled: query.pageNum >= pageCount,
        onclick: function () { query.pageNum++; load(); }
      }));
      pagerHost.appendChild(pager);
    }

    function renderRows(rows, total) {
      clear(bodyHost);
      clear(pagerHost);
      if (!rows.length) {
        bodyHost.appendChild(state('empty', {
          title: config.emptyText || '暂无数据',
          desc: config.emptyHint,
          actions: config.emptyAction ? [config.emptyAction(ctx)] : null
        }));
        return;
      }
      var table = el('table', { class: 'edu-table' });
      var thead = el('thead');
      var headRow = el('tr');
      config.columns.forEach(function (column) {
        headRow.appendChild(el('th', {
          text: column.label,
          style: column.width ? 'width:' + column.width : ''
        }));
      });
      if (config.rowActions) headRow.appendChild(el('th', { class: 'edu-col-actions', text: '操作' }));
      thead.appendChild(headRow);
      table.appendChild(thead);

      var tbody = el('tbody');
      rows.forEach(function (row) {
        var tr = el('tr');
        config.columns.forEach(function (column) {
          var cell = el('td', { class: column.className || '' });
          var content = column.render ? column.render(row, ctx) : text(row[column.key]);
          if (content !== null && content !== undefined) {
            if (typeof content === 'object' && content.nodeType) cell.appendChild(content);
            else cell.textContent = content;
          }
          tr.appendChild(cell);
        });
        if (config.rowActions) {
          var actionCell = el('td', { class: 'edu-col-actions' });
          (config.rowActions(row, ctx) || []).forEach(function (action) {
            if (!action) return;
            actionCell.appendChild(el('button', {
              class: 'edu-btn edu-btn-text' + (action.danger ? ' edu-btn-danger' : ''),
              type: 'button',
              text: action.text,
              onclick: function (event) {
                event.stopPropagation();
                action.onClick(row, ctx);
              }
            }));
          });
          tr.appendChild(actionCell);
        }
        tbody.appendChild(tr);
      });
      table.appendChild(tbody);

      bodyHost.appendChild(el('div', { class: 'edu-table-wrap' }, [table]));
      renderPager(total);
      if (config.onLoaded) config.onLoaded(rows, ctx);
    }

    var requestId = 0;
    function load() {
      var currentRequest = ++requestId;
      clear(bodyHost);
      clear(pagerHost);
      bodyHost.appendChild(state('loading'));
      return Promise.resolve(config.search(ctx.params(), ctx))
        .then(function (result) {
          if (currentRequest !== requestId) return; // 快速切换筛选时丢弃旧响应，避免闪回
          result = result || {};
          var rows = result.rows || [];
          var total = result.total === undefined ? rows.length : Number(result.total);
          renderRows(rows, total);
        })
        .catch(function (error) {
          if (currentRequest !== requestId) return;
          clear(bodyHost);
          clear(pagerHost);
          var kind = error && error.kind;
          if (kind === 'forbidden') {
            bodyHost.appendChild(state('forbidden', {
              desc: '当前账号没有访问该数据的权限。若你认为这是误判，请联系管理员确认角色授权。',
              actions: [{ text: '重新加载', onClick: load }]
            }));
          } else if (kind === 'unauthorized') {
            bodyHost.appendChild(state('forbidden', {
              title: '登录已过期', desc: '请重新登录后继续操作。',
              actions: [{ text: '去登录', primary: true, onClick: function () { location.href = '/login.html?logout=expired'; } }]
            }));
          } else {
            bodyHost.appendChild(state('error', {
              desc: (error && error.message) || '请稍后重试',
              actions: [{ text: '重试', primary: true, onClick: load }]
            }));
          }
        });
    }

    load();

    // 让内容区变成正常文档流（原来这里是居中提示）
    host.classList.add('edu-mounted');
    return ctx;
  }

  /* ---------- 7. 详情页壳 ---------- */
  function detailPage(host, config) {
    var pageTitle = document.getElementById('pageTitle');
    clear(host);
    var page = el('div', { class: 'edu-page' });
    var head = el('div', { class: 'edu-head' });
    var headLeft = el('div');
    if (pageTitle) {
      pageTitle.style.display = '';
      pageTitle.textContent = config.title || pageTitle.textContent;
      headLeft.appendChild(pageTitle);
    }
    if (config.subtitle) headLeft.appendChild(el('div', { class: 'edu-head-sub', text: config.subtitle }));
    head.appendChild(headLeft);
    if (config.actions && config.actions.length) {
      var actions = el('div', { class: 'edu-head-actions' });
      config.actions.forEach(function (action) {
        actions.appendChild(el('button', {
          class: 'edu-btn' + (action.primary ? ' edu-btn-primary' : ''),
          type: 'button',
          text: action.text,
          onclick: action.onClick
        }));
      });
      head.appendChild(actions);
    }
    page.appendChild(head);
    host.appendChild(page);
    host.classList.add('edu-mounted');
    return page;
  }

  /* 一张信息卡：config.items = [{label, value}] */
  function card(title, children, extra) {
    var cardNode = el('div', { class: 'edu-card' });
    var titleRow = el('div', { class: 'edu-card-title' }, [el('span', { text: title })]);
    if (extra) titleRow.appendChild(extra);
    cardNode.appendChild(titleRow);
    (Array.isArray(children) ? children : [children]).forEach(function (child) {
      if (child) cardNode.appendChild(child);
    });
    return cardNode;
  }

  function descList(items) {
    var dl = el('dl', { class: 'edu-desc' });
    items.forEach(function (item) {
      if (!item) return;
      dl.appendChild(el('div', {}, [
        el('dt', { text: item.label }),
        el('dd', { text: item.value === null || item.value === undefined || item.value === '' ? '—' : String(item.value) })
      ]));
    });
    return dl;
  }

  /* ---------- 7.5 自定义内容面板 ---------- */
  /**
   * 有些弹窗不是简单表单（比如「班级成员」「任教关系」要放一张小表格）。
   * panel 给一个空壳，内容自己往里塞。
   *   var p = EduUI.panel({ title, width, onClose });
   *   p.body.appendChild(...);   // 往内容区塞东西
   *   p.close();
   */
  function panel(config) {
    config = config || {};
    var mask = el('div', { class: 'edu-mask' });
    var dialog = el('div', { class: 'edu-modal', style: 'width:' + (config.width || 720) + 'px' });
    var body = el('div', { class: 'edu-modal-body' });
    var foot = el('div', { class: 'edu-modal-foot' });
    var api = {
      body: body,
      foot: foot,
      close: function () {
        if (mask.parentNode) mask.parentNode.removeChild(mask);
        if (config.onClose) config.onClose();
      }
    };
    dialog.appendChild(el('div', { class: 'edu-modal-head' }, [
      el('span', { text: config.title || '' }),
      el('button', { class: 'edu-modal-close', type: 'button', html: '&times;', onclick: api.close })
    ]));
    dialog.appendChild(body);
    dialog.appendChild(foot);
    foot.appendChild(el('button', { class: 'edu-btn', type: 'button', text: '关闭', onclick: api.close }));
    mask.appendChild(dialog);
    mask.addEventListener('click', function (event) { if (event.target === mask) api.close(); });
    document.body.appendChild(mask);
    return api;
  }

  /* ---------- 8. 导出 ---------- */
  EduUI.panel = panel;
  EduUI.el = el;
  EduUI.clear = clear;
  EduUI.text = text;
  EduUI.fmtDate = fmtDate;
  EduUI.tag = tag;
  EduUI.toast = toast;
  EduUI.state = state;
  EduUI.modal = modal;
  EduUI.confirm = confirm;
  EduUI.listView = listView;
  EduUI.detailPage = detailPage;
  EduUI.card = card;
  EduUI.descList = descList;

  window.EduUI = EduUI;
})(window, document);
