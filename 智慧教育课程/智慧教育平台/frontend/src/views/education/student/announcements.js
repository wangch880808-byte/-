/**
 * 学生端「课程公告」视图（6-04，第 6 组 6B）。
 *
 * 对接 main 分支 StudentAnnouncementController：
 *   GET /education/student/announcements?bindingId&pageNum&pageSize
 *   GET /education/student/announcements/{id}
 *
 * 学生端只显示服务端判定为当前可见（OPEN）的公告，不展示 status / visibilityState。
 *
 * 生命周期：
 * - 所有加载/筛选/分页/详情入口先经 EduViews.createAuthGate；
 * - 列表和详情各自持有请求代际，cleanup 后不再写页面；
 * - cleanup 移除根监听、binding 输入监听并关闭本视图弹窗。
 */
(function (global) {
  'use strict';

  var UI = global.EduUI;
  var Views = global.EduViews;
  var API = global.EduAnnouncementApi.student;

  function view(ctx) {
    var root = ctx.root;
    var state = {
      bindingId: ctx.bindingId || '',
      pageNum: 1,
      pageSize: 10,
      total: 0,
      rows: [],
      listSeq: 0,
      detailSeq: 0,
      epoch: 0,
      dialogs: [],
      bindingCleanup: null,
      disposed: false
    };
    var region = null;
    var gate = null;

    root.innerHTML = '<div class="edu-page">'
      + '<div class="edu-toolbar">'
      + '<div class="edu-binding">'
      + '<label for="eduBindingInput">课程筛选</label>'
      + '<input id="eduBindingInput" type="text" inputmode="numeric" autocomplete="off"'
      + ' placeholder="bindingId（可空）" value="' + UI.esc(state.bindingId) + '">'
      + '<button type="button" class="edu-btn" data-action="apply-binding">应用</button>'
      + '<button type="button" class="edu-btn edu-btn-text" data-action="clear-binding">看全部</button>'
      + '<span class="edu-note">留空时返回本人所有在修班级的可见公告。</span>'
      + '</div>'
      + '<span class="edu-toolbar-gap"></span>'
      + '<button type="button" class="edu-btn" data-action="refresh">刷新</button>'
      + '</div>'
      + '<div id="eduStudentListRegion" class="edu-region"></div>'
      + '</div>';

    region = root.querySelector('#eduStudentListRegion');

    function showAuthFailure() {
      if (state.disposed) {
        return;
      }
      region.innerHTML = UI.stateEmpty(
        '登录状态核验失败',
        '未读取公告数据。请刷新页面或重新登录后再试。',
        '<button type="button" class="edu-btn" data-action="retry-auth">重新核验登录</button>'
      );
    }

    function showAuthPending() {
      if (state.disposed) {
        return;
      }
      region.innerHTML = UI.stateLoading('正在核验登录状态…');
    }

    /**
     * 页面离开或进入 bfcache：作废本视图代际并关闭详情弹窗。
     *
     * 代际不回退，离开前已发出的公告读取即使迟到，也不能再写旧页面或弹窗。
     */
    function handlePageHidden() {
      state.epoch++;
      state.listSeq++;
      state.detailSeq++;
      state.dialogs.forEach(function (dialog) {
        dialog.close();
      });
      state.dialogs = [];
    }

    /**
     * pageshow persisted 时只返回当前公告列表的只读加载。
     *
     * 不重开离开前的详情弹窗，也不恢复任何写操作。
     */
    function handlePageRestore() {
      state.epoch++;
      return function restoreList() {
        load();
      };
    }

    gate = Views.createAuthGate({
      onPending: showAuthPending,
      onFailure: showAuthFailure,
      onHidden: handlePageHidden,
      onRestore: handlePageRestore
    });

    state.bindingCleanup = Views.wireBindingField(root, function (value) {
      state.bindingId = value;
      state.pageNum = 1;
      gate.request(load);
    });

    function onRootClick(event) {
      var trigger = event.target.closest('[data-action]');
      if (!trigger) {
        return;
      }
      var action = trigger.getAttribute('data-action');
      if (action === 'refresh' || action === 'retry') {
        gate.request(load);
      } else if (action === 'retry-auth') {
        gate.retry(load);
      } else if (action === 'clear-binding') {
        gate.request(function () {
          Views.setBinding('');
          state.bindingId = '';
          state.pageNum = 1;
          root.querySelector('#eduBindingInput').value = '';
          load();
        });
      } else if (action === 'page') {
        var page = parseInt(trigger.getAttribute('data-page'), 10);
        if (page >= 1) {
          state.pageNum = page;
          gate.request(load);
        }
      } else if (action === 'detail') {
        gate.request(function () {
          openDetail(trigger.getAttribute('data-id'));
        });
      }
    }

    root.addEventListener('click', onRootClick);

    function load(openAnnouncementId) {
      if (state.disposed) {
        return;
      }
      var ticket = ++state.listSeq;
      var epoch = state.epoch;
      region.innerHTML = UI.stateLoading('正在读取公告…');

      // API 调用前再次检查代际；cleanup/pagehide 后不派发排队请求。
      Promise.resolve().then(function () {
        if (state.disposed || state.epoch !== epoch || ticket !== state.listSeq) {
          return null;
        }
        return API.list({
          bindingId: state.bindingId,
          pageNum: state.pageNum,
          pageSize: state.pageSize
        });
      }).then(function (result) {
        if (result === null || state.disposed || state.epoch !== epoch || ticket !== state.listSeq) {
          return;
        }
        state.total = typeof result.total === 'number' ? result.total : 0;
        state.rows = Array.isArray(result.rows) ? result.rows : [];
        renderList();
        if (openAnnouncementId) {
          openDetail(openAnnouncementId);
        }
      }).catch(function (error) {
        if (state.disposed || state.epoch !== epoch || ticket !== state.listSeq) {
          return;
        }
        region.innerHTML = UI.stateError(error, {
          title: '公告读取失败',
          onRetry: true,
          permissionHint: '当前账号没有该班级的访问权限，或不是学生账号。已退出班级的课程不会继续返回公告。'
        });
      });
    }

    function renderList() {
      if (state.rows.length === 0) {
        region.innerHTML = UI.stateEmpty(
          '当前没有可见公告',
          state.bindingId
            ? '该课程—教学班暂未发布处于开放时间的公告。'
            : '你的在修班级暂未发布处于开放时间的公告。'
        );
        return;
      }

      var items = state.rows.map(function (row) {
        return '<li class="edu-timeline-item">'
          + '<button type="button" class="edu-timeline-main" data-action="detail" data-id="' + UI.esc(row.id) + '">'
          + '<span class="edu-timeline-title">' + UI.esc(row.title) + '</span>'
          + '<span class="edu-timeline-meta">开放：' + UI.esc(UI.formatTime(row.opensAt))
          + ' · 关闭：' + UI.esc(UI.formatCloseTime(row.closesAt)) + '</span>'
          + '</button></li>';
      }).join('');

      region.innerHTML = '<ul class="edu-timeline">' + items + '</ul>'
        + UI.pagerHtml(state.total, state.pageNum, state.pageSize);
    }

    function openDetail(announcementId) {
      if (state.disposed) {
        return;
      }
      var ticket = ++state.detailSeq;
      var epoch = state.epoch;
      var dialog = UI.openDialog('<p class="edu-dialog-title">公告详情</p>'
        + '<div id="eduStudentDetailBody">' + UI.stateLoading('正在读取公告…') + '</div>'
        + '<div class="edu-dialog-actions"><button type="button" class="edu-btn" data-role="close">关闭</button></div>');
      state.dialogs.push(dialog);
      dialog.root.querySelector('[data-role="close"]').addEventListener('click', dialog.close);
      var body = dialog.root.querySelector('#eduStudentDetailBody');

      API.detail(announcementId).then(function (result) {
        if (state.disposed || state.epoch !== epoch || ticket !== state.detailSeq) {
          return;
        }
        var item = result.data || {};
        body.innerHTML = '<p class="edu-detail-title">' + UI.esc(item.title) + '</p>'
          + '<p class="edu-detail-meta"><span>开放：' + UI.esc(UI.formatTime(item.opensAt)) + '</span>'
          + '<span>关闭：' + UI.esc(UI.formatCloseTime(item.closesAt)) + '</span></p>'
          + '<div class="edu-detail-body">' + UI.esc(item.body) + '</div>';
      }).catch(function (error) {
        if (state.disposed || state.epoch !== epoch || ticket !== state.detailSeq) {
          return;
        }
        body.innerHTML = UI.stateError(error, {
          title: '公告打开失败',
          notFoundHint: '公告不存在或当前不可查看：可能已撤回、已关闭，或你已不在该班级。'
        });
      });
    }

    // 从通知跳转过来时自动打开目标公告；公告详情会重新鉴权，通知本身不是访问凭证。
    gate.request(function () {
      load(ctx.openAnnouncementId || null);
    });

    return {
      cleanup: function () {
        state.disposed = true;
        state.epoch++;
        state.listSeq++;
        state.detailSeq++;
        if (state.bindingCleanup) {
          state.bindingCleanup();
          state.bindingCleanup = null;
        }
        root.removeEventListener('click', onRootClick);
        state.dialogs.forEach(function (dialog) {
          dialog.close();
        });
        state.dialogs = [];
        gate.cleanup();
        root.innerHTML = '';
      }
    };
  }

  // 学生原型菜单没有独立的"课程公告"入口：本视图由「学习通知 / 课程公告」页以页签方式挂载。
  global.EduStudentAnnouncementView = view;
})(window);
