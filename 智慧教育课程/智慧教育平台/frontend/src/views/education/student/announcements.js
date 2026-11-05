/**
 * 学生端「课程公告」视图（6-04，第 6 组 6B）。
 *
 * 对接 main 分支 StudentAnnouncementController：
 *   GET /education/student/announcements?bindingId&pageNum&pageSize
 *   GET /education/student/announcements/{id}
 *
 * 学生端只显示服务端判定为当前可见（OPEN）的公告，不展示 status / visibilityState。
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
      rows: []
    };

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

    var region = root.querySelector('#eduStudentListRegion');

    Views.wireBindingField(root, function (value) {
      state.bindingId = value;
      state.pageNum = 1;
      load();
    });

    root.addEventListener('click', function (event) {
      var trigger = event.target.closest('[data-action]');
      if (!trigger) {
        return;
      }
      var action = trigger.getAttribute('data-action');
      if (action === 'refresh') {
        load();
      } else if (action === 'retry') {
        load();
      } else if (action === 'clear-binding') {
        Views.setBinding('');
        state.bindingId = '';
        state.pageNum = 1;
        root.querySelector('#eduBindingInput').value = '';
        load();
      } else if (action === 'page') {
        var page = parseInt(trigger.getAttribute('data-page'), 10);
        if (page >= 1) {
          state.pageNum = page;
          load();
        }
      } else if (action === 'detail') {
        openDetail(trigger.getAttribute('data-id'));
      }
    });

    function load(openAnnouncementId) {
      region.innerHTML = UI.stateLoading('正在读取公告…');
      API.list({
        bindingId: state.bindingId,
        pageNum: state.pageNum,
        pageSize: state.pageSize
      }).then(function (result) {
        state.total = typeof result.total === 'number' ? result.total : 0;
        state.rows = Array.isArray(result.rows) ? result.rows : [];
        renderList();
        if (openAnnouncementId) {
          openDetail(openAnnouncementId);
        }
      }).catch(function (error) {
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
      var dialog = UI.openDialog('<p class="edu-dialog-title">公告详情</p>'
        + '<div id="eduStudentDetailBody">' + UI.stateLoading('正在读取公告…') + '</div>'
        + '<div class="edu-dialog-actions"><button type="button" class="edu-btn" data-role="close">关闭</button></div>');
      dialog.root.querySelector('[data-role="close"]').addEventListener('click', dialog.close);
      var body = dialog.root.querySelector('#eduStudentDetailBody');

      API.detail(announcementId).then(function (result) {
        var item = result.data || {};
        body.innerHTML = '<p class="edu-detail-title">' + UI.esc(item.title) + '</p>'
          + '<p class="edu-detail-meta"><span>开放：' + UI.esc(UI.formatTime(item.opensAt)) + '</span>'
          + '<span>关闭：' + UI.esc(UI.formatCloseTime(item.closesAt)) + '</span></p>'
          + '<div class="edu-detail-body">' + UI.esc(item.body) + '</div>';
      }).catch(function (error) {
        body.innerHTML = UI.stateError(error, {
          title: '公告打开失败',
          notFoundHint: '公告不存在或当前不可查看：可能已撤回、已关闭，或你已不在该班级。'
        });
      });
    }

    // 从通知跳转过来时自动打开目标公告；公告详情会重新鉴权，通知本身不是访问凭证。
    load(ctx.openAnnouncementId || null);

    return { cleanup: function () {} };
  }

  // 学生原型菜单没有独立的"课程公告"入口：本视图由「学习通知 / 课程公告」页以页签方式挂载。
  global.EduStudentAnnouncementView = view;
})(window);
