/**
 * 学生端「学习通知 / 课程公告」视图（6-04，第 6 组 6B）。
 *
 * 通知接口已由 6A 合并到 main（main@f21f51d）：GET 列表/详情/未读数、
 * POST 单条已读、PUT 全部已读，字段与 docs/6-02-frontend-api-handoff.md 5.3–5.7 一致。
 * 源码核对一致不等于真实联调通过：生产授权契约与 bindingId 来源仍是外部阻塞。
 * 公告页签复用 student/announcements.js 的视图。
 */
(function (global) {
  'use strict';

  var UI = global.EduUI;
  var Views = global.EduViews;
  var API = global.EduNoticeApi;
  var BADGE_ID = 'noticeBadge';

  var KIND_OPTIONS = [
    { value: '', label: '全部类型' },
    { value: 'ANNOUNCEMENT', label: '公告' },
    { value: 'PREVIEW', label: '预习任务' },
    { value: 'ACTIVITY', label: '课堂活动' },
    { value: 'ASSIGNMENT', label: '作业' },
    { value: 'GRADE', label: '成绩' }
  ];

  /**
   * 刷新侧栏未读角标。
   *
   * @param {Function} [isCurrent] 可选的仍有效判断；返回 false 时丢弃迟到响应，
   *   避免 pagehide/cleanup 后旧请求更新旧页面
   * @returns {Promise<void>} 角标刷新结束（失败时保持隐藏）
   */
  function refreshBadge(isCurrent) {
    var badge = document.getElementById(BADGE_ID);
    if (!badge || !global.EduNoticeApi) {
      return Promise.resolve();
    }
    return API.unreadCount(Views.getBinding()).then(function (result) {
      if (typeof isCurrent === 'function' && !isCurrent()) {
        return;
      }
      var count = result && result.data ? result.data.unreadCount : 0;
      var display = typeof count === 'number' ? count : 0;
      badge.textContent = display > 99 ? '99+' : String(display);
      badge.hidden = display === 0;
    }).catch(function () {
      if (typeof isCurrent === 'function' && !isCurrent()) {
        return;
      }
      // 未读数失败时不显示数字，也不显示 0 冒充真实结果。
      badge.textContent = '';
      badge.hidden = true;
    });
  }

  function view(ctx) {
    var root = ctx.root;
    var state = {
      tab: 'notices',
      kind: '',
      unreadOnly: false,
      pageNum: 1,
      pageSize: 10,
      total: 0,
      rows: [],
      listSeq: 0,
      unreadSeq: 0,
      detailSeq: 0,
      epoch: 0,
      unreadRequested: false,
      dialogs: [],
      confirms: [],
      announcementChild: null,
      pendingAnnouncementId: null,
      disposed: false,
      authFailure: false
    };

    root.innerHTML = '<div class="edu-page">'
      + '<div class="edu-segmented">'
      + '<button type="button" class="edu-seg active" data-tab="notices">学习通知</button>'
      + '<button type="button" class="edu-seg" data-tab="announcements">课程公告</button>'
      + '</div>'
      + '<div class="edu-toolbar" id="eduNoticeToolbar">'
      + '<select id="eduKindFilter" class="edu-select">'
      + KIND_OPTIONS.map(function (item) {
        return '<option value="' + item.value + '">' + UI.esc(item.label) + '</option>';
      }).join('')
      + '</select>'
      + '<label class="edu-checkbox"><input type="checkbox" id="eduUnreadOnly"> 只看未读</label>'
      + '<span class="edu-toolbar-gap"></span>'
      + '<span class="edu-unread">未读 <b id="eduUnreadCount">—</b></span>'
      + '<button type="button" class="edu-btn" data-action="mark-all">全部标为已读</button>'
      + '<button type="button" class="edu-btn" data-action="refresh">刷新</button>'
      + '</div>'
      + '<div id="eduNoticeRegion" class="edu-region"></div>'
      + '</div>';

    var toolbar = root.querySelector('#eduNoticeToolbar');
    var region = root.querySelector('#eduNoticeRegion');
    var gate = null;

    function showAuthFailure() {
      if (state.disposed || state.tab !== 'notices') {
        return;
      }
      state.authFailure = true;
      root.querySelector('#eduUnreadCount').textContent = '—';
      region.innerHTML = UI.stateEmpty(
        '登录状态核验失败',
        '未读取通知数据。请刷新页面或重新登录后再试。',
        '<button type="button" class="edu-btn" data-action="retry-auth">重新核验登录</button>'
      );
    }

    function showAuthPending() {
      if (state.disposed || state.tab !== 'notices') {
        return;
      }
      region.innerHTML = UI.stateLoading('正在核验登录状态…');
    }

    /**
     * 页面离开或进入 bfcache 时作废本视图代际并关闭临时 UI。
     *
     * 代际（epoch）在恢复后仍不回退，因此离开前已发出的请求即使迟到，
     * 也不能再写页面、弹窗或提示。
     */
    function handlePageHidden() {
      state.epoch++;
      state.listSeq++;
      state.unreadSeq++;
      state.detailSeq++;
      state.confirms.forEach(function (confirmPromise) {
        confirmPromise.close();
      });
      state.confirms = [];
      state.dialogs.forEach(function (dialog) {
        dialog.close();
      });
      state.dialogs = [];
    }

    /**
     * pageshow persisted 时按当前页签返回只读恢复任务。
     *
     * 通知页签只重读列表和未读数；公告页签由子视图自己的门卫恢复，
     * 这里返回 null，避免父视图的核验/加载覆盖公告区域。
     */
    function handlePageRestore() {
      state.epoch++;
      if (state.tab !== 'notices') {
        return null;
      }
      return function restoreNotices() {
        loadNotices();
        loadUnread();
      };
    }

    gate = Views.createAuthGate({
      onPending: showAuthPending,
      onFailure: showAuthFailure,
      onHidden: handlePageHidden,
      onRestore: handlePageRestore
    });

    root.querySelectorAll('[data-tab]').forEach(function (button) {
      button.addEventListener('click', function () {
        switchTab(button.getAttribute('data-tab'));
      });
    });

    root.querySelector('#eduKindFilter').addEventListener('change', function (event) {
      state.kind = event.target.value;
      state.pageNum = 1;
      gate.request(loadFilteredNotices);
    });

    root.querySelector('#eduUnreadOnly').addEventListener('change', function (event) {
      state.unreadOnly = event.target.checked;
      state.pageNum = 1;
      gate.request(loadFilteredNotices);
    });

    function onRootClick(event) {
      var trigger = event.target.closest('[data-action]');
      if (!trigger) {
        return;
      }
      var action = trigger.getAttribute('data-action');
      if (action === 'refresh') {
        gate.request(function () {
          loadNotices();
          loadUnread();
        });
      } else if (action === 'retry') {
        gate.request(loadNotices);
      } else if (action === 'retry-auth') {
        gate.retry(function () {
          loadNotices();
          loadUnread();
        });
      } else if (action === 'mark-all') {
        gate.request(markAll);
      } else if (action === 'detail') {
        gate.request(function () {
          openDetail(trigger.getAttribute('data-id'));
        });
      } else if (action === 'page') {
        var page = parseInt(trigger.getAttribute('data-page'), 10);
        if (page >= 1) {
          state.pageNum = page;
          gate.request(loadFilteredNotices);
        }
      }
    }

    root.addEventListener('click', onRootClick);

    function switchTab(tab) {
      if (state.tab === tab) {
        return;
      }
      state.tab = tab;
      // 页签切换会使旧通知列表/未读响应失效，不能写进新页签区域。
      state.listSeq++;
      state.unreadSeq++;
      state.detailSeq++;
      if (tab !== 'notices') {
        // 核验期间保存的通知加载任务也作废；否则核验通过后它会发通知请求并覆盖公告区域。
        gate.cancelPending();
      }
      root.querySelectorAll('[data-tab]').forEach(function (button) {
        button.classList.toggle('active', button.getAttribute('data-tab') === tab);
      });
      toolbar.hidden = tab !== 'notices';
      disposeChild();
      region.innerHTML = '';
      if (tab === 'notices') {
        gate.request(function () {
          loadNotices();
          loadUnread();
        });
      } else {
        mountAnnouncements();
      }
    }

    function disposeChild() {
      if (state.announcementChild && typeof state.announcementChild.cleanup === 'function') {
        state.announcementChild.cleanup();
      }
      state.announcementChild = null;
    }

    function mountAnnouncements() {
      if (typeof global.EduStudentAnnouncementView !== 'function') {
        region.innerHTML = UI.stateEmpty('课程公告视图未加载', '页面脚本未正确引入。');
        return;
      }
      var host = document.createElement('div');
      region.appendChild(host);
      state.announcementChild = global.EduStudentAnnouncementView({
        root: host,
        bindingId: Views.getBinding(),
        label: '课程公告',
        openAnnouncementId: state.pendingAnnouncementId || null
      });
      state.pendingAnnouncementId = null;
    }

    // 核验期间保存最新筛选：列表按最终条件加载，首次未读数只补一次。
    function loadFilteredNotices() {
      loadNotices();
      ensureUnread();
    }

    // 门卫在核验 pending 时只保留最新 action；筛选项替换初始加载后，
    // 首次未读数请求不能丢，但后续筛选也不能重复请求。
    function ensureUnread() {
      if (!state.unreadRequested) {
        loadUnread();
      }
    }

    function loadUnread() {
      if (state.disposed) {
        return;
      }
      var label = root.querySelector('#eduUnreadCount');
      var ticket = ++state.unreadSeq;
      var epoch = state.epoch;
      state.unreadRequested = true;
      label.textContent = '…';
      Promise.resolve().then(function () {
        if (state.disposed || state.epoch !== epoch || ticket !== state.unreadSeq) {
          return null;
        }
        return API.unreadCount(Views.getBinding());
      }).then(function (result) {
        if (result === null || state.disposed || state.epoch !== epoch || ticket !== state.unreadSeq) {
          return;
        }
        var count = result && result.data ? result.data.unreadCount : null;
        label.textContent = typeof count === 'number' ? String(count) : '—';
        refreshBadge(function () {
          return !state.disposed && state.epoch === epoch && ticket === state.unreadSeq;
        });
      }).catch(function () {
        if (state.disposed || state.epoch !== epoch || ticket !== state.unreadSeq) {
          return;
        }
        label.textContent = '不可用';
      });
    }

    function loadNotices() {
      if (state.disposed) {
        return;
      }
      var ticket = ++state.listSeq;
      var epoch = state.epoch;
      state.authFailure = false;
      region.innerHTML = UI.stateLoading('正在读取通知…');
      Promise.resolve().then(function () {
        if (state.disposed || state.epoch !== epoch || ticket !== state.listSeq) {
          return null;
        }
        return API.list({
          kind: state.kind,
          bindingId: Views.getBinding(),
          unreadOnly: state.unreadOnly,
          pageNum: state.pageNum,
          pageSize: state.pageSize
        });
      }).then(function (result) {
        if (result === null || state.disposed || state.epoch !== epoch || ticket !== state.listSeq) {
          return;
        }
        state.total = typeof result.total === 'number' ? result.total : 0;
        state.rows = Array.isArray(result.rows) ? result.rows : [];
        renderNotices();
      }).catch(function (error) {
        if (state.disposed || state.epoch !== epoch || ticket !== state.listSeq) {
          return;
        }
        region.innerHTML = UI.stateError(error, {
          title: '通知读取失败',
          onRetry: true,
          notFoundHint: '通知列表不可用：可能你已不在该教学班，或 bindingId 不属于当前账号。接口已实现，404 不代表 Controller 未部署。',
          permissionHint: '当前账号没有该课程上下文，或不是学生账号。'
        });
      });
    }

    function renderNotices() {
      if (state.rows.length === 0) {
        region.innerHTML = UI.stateEmpty(
          state.unreadOnly ? '没有未读通知' : '暂无通知',
          state.unreadOnly ? '取消"只看未读"可查看历史通知。' : '作业、成绩、公告等发布后会出现在这里。'
        ) + UI.pagerHtml(state.total, state.pageNum, state.pageSize);
        return;
      }

      var items = state.rows.map(function (row) {
        return '<li class="edu-notice-item' + (row.isRead ? '' : ' is-unread') + '">'
          + '<button type="button" class="edu-notice-main" data-action="detail" data-id="' + UI.esc(row.id) + '">'
          + '<span class="edu-notice-top">'
          + (row.isRead ? '' : '<i class="edu-dot" aria-label="未读"></i>')
          + '<span class="edu-notice-title">' + UI.esc(row.title) + '</span>'
          + '<span class="edu-tag">' + UI.esc(UI.kindLabel(row.kind)) + '</span>'
          + '</span>'
          + '<span class="edu-notice-meta">' + UI.esc(UI.formatTime(row.visibleAt))
          + (row.isRead ? ' · 已读 ' + UI.esc(UI.formatTime(row.readAt)) : ' · 未读') + '</span>'
          + '</button></li>';
      }).join('');

      region.innerHTML = '<ul class="edu-notice-list">' + items + '</ul>'
        + UI.pagerHtml(state.total, state.pageNum, state.pageSize);
    }

    function markAll() {
      if (state.disposed || !gate.isReady()) {
        return;
      }
      // 代际在确认前捕获：确认框关闭、页面离开或恢复后都不得重放旧的全部已读操作。
      var epoch = state.epoch;
      var confirmPromise = UI.confirm({
        title: '全部标为已读',
        message: '将标记本人所有教学班当前可见的未读通知，包括其他教学班，不受当前筛选和分页限制。',
        confirmText: '确认'
      });
      state.confirms.push(confirmPromise);
      confirmPromise.then(function (ok) {
        if (!ok || state.disposed || state.epoch !== epoch) {
          return;
        }
        Promise.resolve().then(function () {
          if (state.disposed || state.epoch !== epoch) {
            return null;
          }
          return API.markAllRead();
        }).then(function (result) {
          if (result === null || state.disposed || state.epoch !== epoch) {
            return;
          }
          var data = result.data || {};
          var marked = typeof data.markedCount === 'number' ? data.markedCount : 0;
          UI.toast(marked > 0 ? '已标记 ' + marked + ' 条为已读' : '没有需要标记的通知', marked > 0 ? 'ok' : 'info');
          gate.request(function () {
            loadNotices();
            loadUnread();
          });
        }).catch(function (error) {
          if (state.disposed || state.epoch !== epoch) {
            return;
          }
          UI.toast(error.message, 'error');
        });
      });
    }

    function openDetail(noticeId) {
      if (state.disposed) {
        return;
      }
      var ticket = ++state.detailSeq;
      var epoch = state.epoch;
      var dialog = UI.openDialog('<p class="edu-dialog-title">通知详情</p>'
        + '<div id="eduNoticeDetailBody">' + UI.stateLoading('正在读取通知…') + '</div>'
        + '<div class="edu-dialog-actions">'
        + '<button type="button" class="edu-btn" data-role="close">关闭</button>'
        + '</div>');
      state.dialogs.push(dialog);
      dialog.root.querySelector('[data-role="close"]').addEventListener('click', dialog.close);
      var body = dialog.root.querySelector('#eduNoticeDetailBody');

      Promise.resolve().then(function () {
        if (state.disposed || state.epoch !== epoch || ticket !== state.detailSeq) {
          return null;
        }
        return API.detail(noticeId);
      }).then(function (result) {
        if (result === null || state.disposed || state.epoch !== epoch || ticket !== state.detailSeq) {
          return;
        }
        var item = result.data || {};
        body.innerHTML = '<p class="edu-detail-title">' + UI.esc(item.title) + '</p>'
          + '<p class="edu-detail-meta"><span class="edu-tag">' + UI.esc(UI.kindLabel(item.kind)) + '</span>'
          + '<span>可见时间：' + UI.esc(UI.formatTime(item.visibleAt)) + '</span>'
          + '<span>' + (item.isRead ? '已读于 ' + UI.esc(UI.formatTime(item.readAt)) : '未读') + '</span></p>'
          + '<p class="edu-form-note">通知详情只包含元数据，正文由来源页面提供。</p>'
          + '<div class="edu-detail-actions">'
          + (item.isRead ? '' : '<button type="button" class="edu-btn edu-btn-primary" data-role="mark-read">标为已读</button>')
          + (item.kind === 'ANNOUNCEMENT'
            ? '<button type="button" class="edu-btn" data-role="open-source">打开公告</button>'
            : '<span class="edu-note">来源页面路由待第 3／4／5 组确认，暂不跳转。</span>')
          + '</div>';

        var markButton = body.querySelector('[data-role="mark-read"]');
        if (markButton) {
          markButton.addEventListener('click', function () {
            if (state.disposed || state.epoch !== epoch || ticket !== state.detailSeq || !gate.isReady()) {
              return;
            }
            markButton.disabled = true;
            Promise.resolve().then(function () {
              if (state.disposed || state.epoch !== epoch || ticket !== state.detailSeq) {
                return null;
              }
              return API.markRead(noticeId);
            }).then(function (result) {
              if (result === null || state.disposed || state.epoch !== epoch || ticket !== state.detailSeq) {
                return;
              }
              // 不依赖 alreadyRead / readAt 做前端推算：成功后统一重拉列表与未读数。
              UI.toast('已标记为已读', 'ok');
              dialog.close();
              gate.request(function () {
                loadNotices();
                loadUnread();
              });
            }).catch(function (error) {
              if (state.disposed || state.epoch !== epoch || ticket !== state.detailSeq) {
                return;
              }
              markButton.disabled = false;
              if (error.isNotFound()) {
                // 仅当通知仍可见时重复请求才返回原 readAt；退班或内容不可见时可能 404。
                UI.toast('该通知已不可见：可能已退出该班级，或来源内容已下架', 'info');
                dialog.close();
                gate.request(function () {
                  loadNotices();
                  loadUnread();
                });
                return;
              }
              UI.toast(error.message, 'error');
            });
          });
        }

        var sourceButton = body.querySelector('[data-role="open-source"]');
        if (sourceButton) {
          sourceButton.addEventListener('click', function () {
            if (state.disposed) {
              return;
            }
            dialog.close();
            state.pendingAnnouncementId = item.sourceId;
            switchTab('announcements');
          });
        }
      }).catch(function (error) {
        if (state.disposed || state.epoch !== epoch || ticket !== state.detailSeq) {
          return;
        }
        body.innerHTML = UI.stateError(error, {
          title: '通知打开失败',
          notFoundHint: '通知不存在或当前不可访问：可能属于他人、尚未可见，或你已退出该班级。'
        });
      });
    }

    gate.request(function () {
      loadNotices();
      loadUnread();
    });

    return {
      cleanup: function () {
        state.disposed = true;
        state.epoch++;
        state.listSeq++;
        state.unreadSeq++;
        state.detailSeq++;
        root.removeEventListener('click', onRootClick);
        state.confirms.forEach(function (confirmPromise) {
          confirmPromise.close();
        });
        state.confirms = [];
        state.dialogs.forEach(function (dialog) {
          dialog.close();
        });
        state.dialogs = [];
        disposeChild();
        gate.cleanup();
        root.innerHTML = '';
      }
    };
  }

  Views.register('student', 0, 3, view);

  global.EduNoticeBadge = { refresh: refreshBadge };
})(window);
