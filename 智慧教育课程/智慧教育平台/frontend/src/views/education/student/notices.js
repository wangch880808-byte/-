/**
 * 学生端「学习通知 / 课程公告」视图（6-04，第 6 组 6B）。
 *
 * 通知接口按 docs/6-02-frontend-api-handoff.md 5.3–5.7 实现，后端 main 尚未提供
 * StudentNoticeController，接口未注册时页面显示"接口未就绪"，不展示任何本地假数据。
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

  function refreshBadge() {
    var badge = document.getElementById(BADGE_ID);
    if (!badge || !global.EduNoticeApi) {
      return Promise.resolve();
    }
    return API.unreadCount(Views.getBinding()).then(function (result) {
      var count = result && result.data ? result.data.unreadCount : 0;
      var display = typeof count === 'number' ? count : 0;
      badge.textContent = display > 99 ? '99+' : String(display);
      badge.hidden = display === 0;
    }).catch(function () {
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
      announcementChild: null,
      pendingAnnouncementId: null
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

    root.querySelectorAll('[data-tab]').forEach(function (button) {
      button.addEventListener('click', function () {
        switchTab(button.getAttribute('data-tab'));
      });
    });

    root.querySelector('#eduKindFilter').addEventListener('change', function (event) {
      state.kind = event.target.value;
      state.pageNum = 1;
      loadNotices();
    });

    root.querySelector('#eduUnreadOnly').addEventListener('change', function (event) {
      state.unreadOnly = event.target.checked;
      state.pageNum = 1;
      loadNotices();
    });

    root.addEventListener('click', function (event) {
      var trigger = event.target.closest('[data-action]');
      if (!trigger) {
        return;
      }
      var action = trigger.getAttribute('data-action');
      if (action === 'refresh') {
        loadNotices();
        loadUnread();
      } else if (action === 'retry') {
        loadNotices();
      } else if (action === 'mark-all') {
        markAll();
      } else if (action === 'detail') {
        openDetail(trigger.getAttribute('data-id'));
      } else if (action === 'page') {
        var page = parseInt(trigger.getAttribute('data-page'), 10);
        if (page >= 1) {
          state.pageNum = page;
          loadNotices();
        }
      }
    });

    function switchTab(tab) {
      if (state.tab === tab) {
        return;
      }
      state.tab = tab;
      root.querySelectorAll('[data-tab]').forEach(function (button) {
        button.classList.toggle('active', button.getAttribute('data-tab') === tab);
      });
      toolbar.hidden = tab !== 'notices';
      disposeChild();
      region.innerHTML = '';
      if (tab === 'notices') {
        loadNotices();
        loadUnread();
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

    function loadUnread() {
      var label = root.querySelector('#eduUnreadCount');
      label.textContent = '…';
      API.unreadCount(Views.getBinding()).then(function (result) {
        var count = result && result.data ? result.data.unreadCount : null;
        label.textContent = typeof count === 'number' ? String(count) : '—';
        refreshBadge();
      }).catch(function () {
        label.textContent = '不可用';
      });
    }

    function loadNotices() {
      region.innerHTML = UI.stateLoading('正在读取通知…');
      API.list({
        kind: state.kind,
        bindingId: Views.getBinding(),
        unreadOnly: state.unreadOnly,
        pageNum: state.pageNum,
        pageSize: state.pageSize
      }).then(function (result) {
        state.total = typeof result.total === 'number' ? result.total : 0;
        state.rows = Array.isArray(result.rows) ? result.rows : [];
        renderNotices();
      }).catch(function (error) {
        region.innerHTML = UI.stateError(error, {
          title: '通知读取失败',
          onRetry: true,
          notFoundHint: '学生通知接口未在后端注册时也会返回 404。6A 交付 NoticeController 前，本页无可联调数据。',
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
      UI.confirm({
        title: '全部标为已读',
        message: '将把本人当前所有可见的未读通知标记为已读，不受当前筛选和分页限制。',
        confirmText: '确认'
      }).then(function (ok) {
        if (!ok) {
          return;
        }
        API.markAllRead().then(function (result) {
          var data = result.data || {};
          var marked = typeof data.markedCount === 'number' ? data.markedCount : 0;
          UI.toast(marked > 0 ? '已标记 ' + marked + ' 条为已读' : '没有需要标记的通知', marked > 0 ? 'ok' : 'info');
          loadNotices();
          loadUnread();
        }).catch(function (error) {
          UI.toast(error.message, 'error');
        });
      });
    }

    function openDetail(noticeId) {
      var dialog = UI.openDialog('<p class="edu-dialog-title">通知详情</p>'
        + '<div id="eduNoticeDetailBody">' + UI.stateLoading('正在读取通知…') + '</div>'
        + '<div class="edu-dialog-actions">'
        + '<button type="button" class="edu-btn" data-role="close">关闭</button>'
        + '</div>');
      dialog.root.querySelector('[data-role="close"]').addEventListener('click', dialog.close);
      var body = dialog.root.querySelector('#eduNoticeDetailBody');

      API.detail(noticeId).then(function (result) {
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
            markButton.disabled = true;
            API.markRead(noticeId).then(function (marked) {
              var readAt = marked && marked.data ? marked.data.readAt : null;
              UI.toast('已标记为已读', 'ok');
              dialog.close();
              loadNotices();
              loadUnread();
              return readAt;
            }).catch(function (error) {
              markButton.disabled = false;
              UI.toast(error.message, 'error');
            });
          });
        }

        var sourceButton = body.querySelector('[data-role="open-source"]');
        if (sourceButton) {
          sourceButton.addEventListener('click', function () {
            dialog.close();
            state.pendingAnnouncementId = item.sourceId;
            switchTab('announcements');
          });
        }
      }).catch(function (error) {
        body.innerHTML = UI.stateError(error, {
          title: '通知打开失败',
          notFoundHint: '通知不存在或当前不可访问：可能属于他人、尚未可见，或你已退出该班级。'
        });
      });
    }

    loadNotices();
    loadUnread();

    return {
      cleanup: function () {
        disposeChild();
      }
    };
  }

  Views.register('student', 0, 3, view);

  global.EduNoticeBadge = { refresh: refreshBadge };
})(window);
