/**
 * 6-02 页面共用小工具（第 6 组 6B）。
 *
 * 只提供展示层能力：转义、时间、枚举文案、状态卡片、分页、确认框、轻提示。
 * 不包含业务请求，也不做本地缓存。
 */
(function (global) {
  'use strict';

  var STATUS_LABEL = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档'
  };

  var VISIBILITY_LABEL = {
    DRAFT: '草稿',
    NOT_YET_OPEN: '未开放',
    OPEN: '展示中',
    CLOSED: '已关闭',
    ARCHIVED: '已归档'
  };

  var VISIBILITY_TONE = {
    DRAFT: 'muted',
    NOT_YET_OPEN: 'warn',
    OPEN: 'ok',
    CLOSED: 'muted',
    ARCHIVED: 'muted'
  };

  var NOTICE_KIND_LABEL = {
    ANNOUNCEMENT: '公告',
    PREVIEW: '预习任务',
    ACTIVITY: '课堂活动',
    ASSIGNMENT: '作业',
    GRADE: '成绩'
  };

  function esc(value) {
    if (value === null || value === undefined) {
      return '';
    }
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function pad2(value) {
    return value < 10 ? '0' + value : String(value);
  }

  /** 后端时间为 UTC 毫秒（yyyy-MM-ddTHH:mm:ss.SSSZ），此处转本地时区展示。 */
  function formatTime(iso) {
    if (!iso) {
      return '—';
    }
    var date = new Date(iso);
    if (isNaN(date.getTime())) {
      return esc(iso);
    }
    return date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate())
      + ' ' + pad2(date.getHours()) + ':' + pad2(date.getMinutes());
  }

  function formatCloseTime(iso) {
    return iso ? formatTime(iso) : '不关闭';
  }

  /** datetime-local 输入值（本地时区）转后端需要的 UTC 毫秒字符串。 */
  function toUtcIso(localValue) {
    if (!localValue) {
      return null;
    }
    var date = new Date(localValue);
    if (isNaN(date.getTime())) {
      return null;
    }
    return date.toISOString();
  }

  /** UTC 毫秒字符串转 datetime-local 输入值。 */
  function toLocalInput(iso) {
    if (!iso) {
      return '';
    }
    var date = new Date(iso);
    if (isNaN(date.getTime())) {
      return '';
    }
    return date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate())
      + 'T' + pad2(date.getHours()) + ':' + pad2(date.getMinutes());
  }

  function utf8Length(text) {
    if (!text) {
      return 0;
    }
    if (global.TextEncoder) {
      return new global.TextEncoder().encode(text).length;
    }
    return unescape(encodeURIComponent(text)).length;
  }

  function codePointLength(text) {
    if (!text) {
      return 0;
    }
    return Array.from(text).length;
  }

  /**
   * 状态徽标只认服务端计算的 visibilityState。
   *
   * 缺失时按"未知状态"展示并记录错误，不用 status 兜底：
   * PUBLISHED 本身不能说明当前是否处于开放窗口，用 status 会把它错显成"展示中"。
   */
  function statusBadge(item) {
    var state = item && item.visibilityState;
    if (!state) {
      if (global.console && typeof global.console.error === 'function') {
        global.console.error('[EduUI] 公告响应缺少 visibilityState，按未知状态展示', item);
      }
      return '<span class="edu-badge edu-badge-warn">未知状态</span>';
    }
    var label = VISIBILITY_LABEL[state] || ('未知状态（' + state + '）');
    var tone = VISIBILITY_TONE[state] || 'warn';
    return '<span class="edu-badge edu-badge-' + tone + '">' + esc(label) + '</span>';
  }

  function kindLabel(kind) {
    return NOTICE_KIND_LABEL[kind] || kind || '通知';
  }

  function stateLoading(text) {
    return '<div class="edu-state"><div class="edu-spinner"></div><p>' + esc(text || '加载中…') + '</p></div>';
  }

  function stateEmpty(title, hint, actionHtml) {
    return '<div class="edu-state">'
      + '<p class="edu-state-title">' + esc(title) + '</p>'
      + (hint ? '<p class="edu-state-hint">' + esc(hint) + '</p>' : '')
      + (actionHtml || '')
      + '</div>';
  }

  /**
   * 失败态。所有失败都必须给出"这次没拿到数据"的明确说明，不能渲染成 0 或空列表冒充成功。
   */
  function stateError(error, options) {
    var opts = options || {};
    var hint = '';
    var isPermission = typeof error.isPermission === 'function' && error.isPermission();
    var isNotFound = typeof error.isNotFound === 'function' && error.isNotFound();
    if (isPermission && opts.permissionHint) {
      hint = opts.permissionHint;
    } else if (isNotFound && opts.notFoundHint) {
      hint = opts.notFoundHint;
    } else if (error.code === 404) {
      // 404 只说明"对象不存在或当前不可见"，不能推断为 Controller 未部署。
      hint = '对象不存在或当前不可见：可能不属于当前账号、尚未开放，或你已不在对应教学班。';
    } else if (error.code === 503) {
      // 只有服务端明确回传授权相关原因时，才按授权未接入解释；
      // 其余 503 一律按通用服务不可用处理，避免把网关/上游故障误判成授权问题。
      var serviceMessage = String(error.message || '');
      if (serviceMessage.indexOf('授权') >= 0) {
        hint = '教学班授权服务暂不可用（第 2 组公开契约未接入）。这是服务端的安全拒绝，'
          + '接口接入后即可正常读取，无需重复重试。';
      } else {
        hint = '服务端暂时不可用，与页面数据无关。请稍后重试；持续失败时请把上面的错误信息与请求路径一并反馈。';
      }
    } else if (error.isServerSide()) {
      hint = '服务端异常。可稍后重试；仍失败时请把本页错误码与路径交给 6A 排查。';
    } else if (error.isNetwork()) {
      hint = '请检查后端服务与网络连接后重试。';
    }
    return '<div class="edu-state edu-state-error">'
      + '<p class="edu-state-title">' + esc(opts.title || '数据加载失败') + '</p>'
      + '<p class="edu-state-hint">' + esc(error.message) + '（code ' + esc(error.code) + '）</p>'
      + (hint ? '<p class="edu-state-hint edu-state-hint-strong">' + esc(hint) + '</p>' : '')
      + '<p class="edu-state-meta">' + esc(error.method + ' ' + error.url) + '</p>'
      + (opts.onRetry ? '<button type="button" class="edu-btn" data-action="retry">重试</button>' : '')
      + '</div>';
  }

  function pagerHtml(total, pageNum, pageSize) {
    var totalNumber = Number(total) || 0;
    var pages = Math.max(1, Math.ceil(totalNumber / pageSize));
    var summary = '共 ' + totalNumber + ' 条 · 第 ' + pageNum + ' / ' + pages + ' 页';
    return '<div class="edu-pager">'
      + '<span class="edu-pager-info">' + esc(summary) + '</span>'
      + '<button type="button" class="edu-btn" data-action="page" data-page="' + (pageNum - 1) + '"'
      + (pageNum <= 1 ? ' disabled' : '') + '>上一页</button>'
      + '<button type="button" class="edu-btn" data-action="page" data-page="' + (pageNum + 1) + '"'
      + (pageNum >= pages ? ' disabled' : '') + '>下一页</button>'
      + '</div>';
  }

  function toast(message, tone) {
    var box = document.getElementById('eduToastBox');
    if (!box) {
      box = document.createElement('div');
      box.id = 'eduToastBox';
      box.className = 'edu-toast-box';
      document.body.appendChild(box);
    }
    var item = document.createElement('div');
    item.className = 'edu-toast edu-toast-' + (tone || 'info');
    item.textContent = message;
    box.appendChild(item);
    global.setTimeout(function () {
      if (item.parentNode) {
        item.parentNode.removeChild(item);
      }
    }, 3200);
  }

  /**
   * 二次确认。返回 Promise<boolean>；取消或关闭返回 false。
   */
  function confirm(options) {
    var opts = options || {};
    return new Promise(function (resolve) {
      var overlay = document.createElement('div');
      overlay.className = 'edu-overlay';
      overlay.innerHTML = '<div class="edu-dialog" role="dialog" aria-modal="true">'
        + '<p class="edu-dialog-title">' + esc(opts.title || '请确认') + '</p>'
        + '<p class="edu-dialog-text">' + esc(opts.message || '') + '</p>'
        + '<div class="edu-dialog-actions">'
        + '<button type="button" class="edu-btn" data-role="cancel">取消</button>'
        + '<button type="button" class="edu-btn ' + (opts.danger ? 'edu-btn-danger' : 'edu-btn-primary')
        + '" data-role="ok">' + esc(opts.confirmText || '确认') + '</button>'
        + '</div></div>';
      document.body.appendChild(overlay);
      function close(result) {
        if (overlay.parentNode) {
          overlay.parentNode.removeChild(overlay);
        }
        resolve(result);
      }
      overlay.querySelector('[data-role="cancel"]').addEventListener('click', function () { close(false); });
      overlay.querySelector('[data-role="ok"]').addEventListener('click', function () { close(true); });
      overlay.addEventListener('click', function (event) {
        if (event.target === overlay) {
          close(false);
        }
      });
    });
  }

  function openDialog(html) {
    var overlay = document.createElement('div');
    overlay.className = 'edu-overlay';
    overlay.innerHTML = '<div class="edu-dialog edu-dialog-wide" role="dialog" aria-modal="true">' + html + '</div>';
    document.body.appendChild(overlay);
    return {
      root: overlay,
      close: function () {
        if (overlay.parentNode) {
          overlay.parentNode.removeChild(overlay);
        }
      }
    };
  }

  global.EduUI = {
    esc: esc,
    formatTime: formatTime,
    formatCloseTime: formatCloseTime,
    toUtcIso: toUtcIso,
    toLocalInput: toLocalInput,
    utf8Length: utf8Length,
    codePointLength: codePointLength,
    statusBadge: statusBadge,
    kindLabel: kindLabel,
    stateLoading: stateLoading,
    stateEmpty: stateEmpty,
    stateError: stateError,
    pagerHtml: pagerHtml,
    toast: toast,
    confirm: confirm,
    openDialog: openDialog,
    statusLabel: STATUS_LABEL,
    visibilityLabel: VISIBILITY_LABEL,
    noticeKindLabel: NOTICE_KIND_LABEL
  };
})(window);
