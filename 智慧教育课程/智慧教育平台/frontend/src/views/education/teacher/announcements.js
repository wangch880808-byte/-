/**
 * 教师端「班级消息 / 公告」视图（6-04，第 6 组 6B）。
 *
 * 对接 main 分支 TeacherAnnouncementController：
 *   GET    /education/teacher/announcements?bindingId&status&pageNum&pageSize
 *   GET    /education/teacher/announcements/{id}
 *   POST   /education/teacher/announcements
 *   PUT    /education/teacher/announcements/{id}
 *   POST   /education/teacher/announcements/{id}/publish
 *   POST   /education/teacher/announcements/{id}/archive
 *
 * 约定：列表缺少 bindingId 时不发请求；所有失败都显式展示，不把失败渲染成空列表。
 */
(function (global) {
  'use strict';

  var UI = global.EduUI;
  var Views = global.EduViews;
  var API = global.EduAnnouncementApi.teacher;
  var PERM = API.permissions;

  var STATUS_OPTIONS = [
    { value: '', label: '全部状态' },
    { value: 'DRAFT', label: '草稿' },
    { value: 'PUBLISHED', label: '已发布' },
    { value: 'ARCHIVED', label: '已归档' }
  ];

  function view(ctx) {
    var root = ctx.root;
    var state = {
      bindingId: ctx.bindingId || '',
      status: '',
      pageNum: 1,
      pageSize: 10,
      total: 0,
      rows: [],
      submitting: false,
      detailDialog: null
    };

    root.innerHTML = '<div class="edu-page">'
      + '<div class="edu-toolbar">'
      + Views.bindingFieldHtml({
        label: '课程—教学班上下文',
        placeholder: '填写 bindingId',
        hint: '公告必须按课程—教学班发布。2-04 课程列表接口就绪前，联调期手工填写（演示库：910000000001 / 910000000002）。'
      })
      + '<span class="edu-toolbar-gap"></span>'
      + '<select id="eduStatusFilter" class="edu-select">'
      + STATUS_OPTIONS.map(function (item) {
        return '<option value="' + item.value + '">' + UI.esc(item.label) + '</option>';
      }).join('')
      + '</select>'
      + '<button type="button" class="edu-btn" data-action="refresh">刷新</button>'
      + '<button type="button" class="edu-btn edu-btn-primary" data-action="create">新建公告</button>'
      + '</div>'
      + '<div id="eduListRegion" class="edu-region"></div>'
      + '</div>';

    var region = root.querySelector('#eduListRegion');

    Views.wireBindingField(root, function (value) {
      state.bindingId = value;
      state.pageNum = 1;
      load();
    });

    root.querySelector('#eduStatusFilter').addEventListener('change', function (event) {
      state.status = event.target.value;
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
      } else if (action === 'create') {
        openForm(null);
      } else if (action === 'retry') {
        load();
      } else if (action === 'page') {
        var page = parseInt(trigger.getAttribute('data-page'), 10);
        if (page >= 1) {
          state.pageNum = page;
          load();
        }
      } else if (action === 'detail') {
        openDetail(trigger.getAttribute('data-id'));
      } else if (action === 'edit') {
        openForm(trigger.getAttribute('data-id'));
      } else if (action === 'publish') {
        changeState(trigger.getAttribute('data-id'), 'publish');
      } else if (action === 'archive') {
        changeState(trigger.getAttribute('data-id'), 'archive');
      }
    });

    function needBinding() {
      region.innerHTML = UI.stateEmpty(
        '尚未选择课程—教学班上下文',
        '教师公告按课程—教学班发布与授权。填写上方 bindingId 后点击"应用"再读取列表。',
        ''
      );
    }

    function load() {
      if (!state.bindingId) {
        needBinding();
        return;
      }
      region.innerHTML = UI.stateLoading('正在读取公告…');
      API.list({
        bindingId: state.bindingId,
        status: state.status,
        pageNum: state.pageNum,
        pageSize: state.pageSize
      }).then(function (result) {
        state.total = typeof result.total === 'number' ? result.total : 0;
        state.rows = Array.isArray(result.rows) ? result.rows : [];
        renderList();
      }).catch(renderError);
    }

    function renderError(error) {
      region.innerHTML = UI.stateError(error, {
        title: '公告列表读取失败',
        onRetry: true,
        permissionHint: '当前账号缺少 ' + PERM.list + ' 权限。该权限需要写入 sys_menu 并分配给教师角色，属第 1 组菜单权限范围。'
      });
    }

    function renderList() {
      if (state.rows.length === 0) {
        var isEmptyFiltered = state.status !== '';
        region.innerHTML = UI.stateEmpty(
          isEmptyFiltered ? '当前筛选条件下没有公告' : '本班还没有公告',
          isEmptyFiltered ? '可切换到"全部状态"再查看。' : '点击右上角"新建公告"创建草稿，发布后本班学生才能看到。',
          '<button type="button" class="edu-btn edu-btn-primary" data-action="create">新建公告</button>'
        ) + UI.pagerHtml(state.total, state.pageNum, state.pageSize);
        return;
      }

      var rows = state.rows.map(function (row) {
        var isDraft = row.status === 'DRAFT';
        var isPublished = row.status === 'PUBLISHED';
        return '<tr>'
          + '<td class="edu-cell-title"><button type="button" class="edu-link" data-action="detail" data-id="'
          + UI.esc(row.id) + '">' + UI.esc(row.title) + '</button></td>'
          + '<td>' + UI.statusBadge(row) + '</td>'
          + '<td>' + UI.esc(UI.formatTime(row.opensAt)) + '</td>'
          + '<td>' + UI.esc(UI.formatCloseTime(row.closesAt)) + '</td>'
          + '<td>' + UI.esc(UI.formatTime(row.createdAt)) + '</td>'
          + '<td class="edu-cell-ops">'
          + '<button type="button" class="edu-btn edu-btn-text" data-action="detail" data-id="' + UI.esc(row.id) + '">查看</button>'
          + (isDraft ? '<button type="button" class="edu-btn edu-btn-text" data-action="edit" data-id="' + UI.esc(row.id) + '">编辑</button>' : '')
          + (isDraft ? '<button type="button" class="edu-btn edu-btn-text" data-action="publish" data-id="' + UI.esc(row.id) + '">发布</button>' : '')
          + (isPublished ? '<button type="button" class="edu-btn edu-btn-text edu-btn-text-warn" data-action="archive" data-id="' + UI.esc(row.id) + '">归档</button>' : '')
          + '</td>'
          + '</tr>';
      }).join('');

      region.innerHTML = '<table class="edu-table">'
        + '<thead><tr><th>标题</th><th>状态</th><th>开放时间</th><th>关闭时间</th><th>创建时间</th><th>操作</th></tr></thead>'
        + '<tbody>' + rows + '</tbody>'
        + '</table>'
        + UI.pagerHtml(state.total, state.pageNum, state.pageSize);
    }

    function openDetail(announcementId) {
      var dialog = UI.openDialog('<p class="edu-dialog-title">公告详情</p><div id="eduDetailBody">'
        + UI.stateLoading('正在读取公告…') + '</div>'
        + '<div class="edu-dialog-actions"><button type="button" class="edu-btn" data-role="close">关闭</button></div>');
      dialog.root.querySelector('[data-role="close"]').addEventListener('click', dialog.close);
      var body = dialog.root.querySelector('#eduDetailBody');

      API.detail(announcementId).then(function (result) {
        var item = result.data || {};
        body.innerHTML = '<p class="edu-detail-title">' + UI.esc(item.title) + '</p>'
          + '<p class="edu-detail-meta">' + UI.statusBadge(item)
          + '<span>开放：' + UI.esc(UI.formatTime(item.opensAt)) + '</span>'
          + '<span>关闭：' + UI.esc(UI.formatCloseTime(item.closesAt)) + '</span>'
          + '<span>创建：' + UI.esc(UI.formatTime(item.createdAt)) + '</span></p>'
          + '<div class="edu-detail-body">' + UI.esc(item.body) + '</div>'
          + '<p class="edu-detail-meta edu-detail-ids"><span>公告 ID：' + UI.esc(item.id) + '</span>'
          + '<span>创建人：' + UI.esc(item.createdByUserId) + '</span>'
          + '<span>发布人：' + UI.esc(item.publishedByUserId || '—') + '</span></p>';
      }).catch(function (error) {
        body.innerHTML = UI.stateError(error, {
          title: '公告详情读取失败',
          notFoundHint: '公告不存在、不属于当前班级，或当前账号不可查看。'
        });
      });
    }

    function changeState(announcementId, action) {
      var isPublish = action === 'publish';
      UI.confirm({
        title: isPublish ? '发布公告' : '归档公告',
        message: isPublish
          ? '发布后本班学生在开放时间内即可查看；当前版本不生成站内通知。'
          : '归档后学生不再可见；已发布状态不可恢复。',
        confirmText: isPublish ? '发布' : '归档',
        danger: !isPublish
      }).then(function (ok) {
        if (!ok) {
          return;
        }
        var request = isPublish ? API.publish(announcementId) : API.archive(announcementId);
        request.then(function () {
          UI.toast(isPublish ? '已发布' : '已归档', 'ok');
          load();
        }).catch(function (error) {
          if (error.isConflict()) {
            UI.toast('状态已变化，请刷新后重试', 'warn');
            load();
            return;
          }
          UI.toast(error.message, 'error');
        });
      });
    }

    function openForm(announcementId) {
      var editing = Boolean(announcementId);
      var dialog = UI.openDialog('<p class="edu-dialog-title">' + (editing ? '编辑公告草稿' : '新建公告草稿') + '</p>'
        + '<div class="edu-form">'
        + '<label class="edu-field"><span>标题</span><input id="eduFormTitle" maxlength="200" type="text" placeholder="1–200 字"></label>'
        + '<label class="edu-field"><span>正文</span><textarea id="eduFormBody" rows="8" placeholder="公告正文"></textarea>'
        + '<em id="eduFormCount" class="edu-counter"></em></label>'
        + '<div class="edu-field-row">'
        + '<label class="edu-field"><span>开放时间（可空）</span><input id="eduFormOpensAt" type="datetime-local"></label>'
        + '<label class="edu-field"><span>关闭时间（可空）</span><input id="eduFormClosesAt" type="datetime-local"></label>'
        + '</div>'
        + '<p class="edu-form-note">留空即：开放时间取服务端发布时刻，关闭时间表示不关闭。时间为本机时区，提交时转 UTC。</p>'
        + '<p id="eduFormError" class="edu-form-error" hidden></p>'
        + '</div>'
        + '<div class="edu-dialog-actions">'
        + '<button type="button" class="edu-btn" data-role="close">取消</button>'
        + '<button type="button" class="edu-btn edu-btn-primary" data-role="submit">' + (editing ? '保存' : '保存草稿') + '</button>'
        + '</div>');

      var titleInput = dialog.root.querySelector('#eduFormTitle');
      var bodyInput = dialog.root.querySelector('#eduFormBody');
      var opensInput = dialog.root.querySelector('#eduFormOpensAt');
      var closesInput = dialog.root.querySelector('#eduFormClosesAt');
      var countLabel = dialog.root.querySelector('#eduFormCount');
      var errorLabel = dialog.root.querySelector('#eduFormError');
      var submitButton = dialog.root.querySelector('[data-role="submit"]');

      function updateCount() {
        var codePoints = UI.codePointLength(bodyInput.value);
        var bytes = UI.utf8Length(bodyInput.value);
        countLabel.textContent = codePoints + ' / 20000 字 · ' + bytes + ' / 65535 字节';
        countLabel.classList.toggle('over', codePoints > 20000 || bytes > 65535);
      }

      bodyInput.addEventListener('input', updateCount);
      updateCount();
      dialog.root.querySelector('[data-role="close"]').addEventListener('click', dialog.close);

      if (editing) {
        submitButton.disabled = true;
        API.detail(announcementId).then(function (result) {
          var item = result.data || {};
          if (item.status !== 'DRAFT') {
            dialog.close();
            UI.toast('仅草稿可以编辑，请刷新列表', 'warn');
            load();
            return;
          }
          titleInput.value = item.title || '';
          bodyInput.value = item.body || '';
          opensInput.value = UI.toLocalInput(item.opensAt);
          closesInput.value = UI.toLocalInput(item.closesAt);
          updateCount();
          submitButton.disabled = false;
          titleInput.focus();
        }).catch(function (error) {
          errorLabel.hidden = false;
          errorLabel.textContent = error.message;
          submitButton.disabled = false;
        });
      } else {
        titleInput.focus();
      }

      submitButton.addEventListener('click', function () {
        if (state.submitting) {
          return;
        }
        var title = titleInput.value.trim();
        var bodyText = bodyInput.value;
        var opensAt = UI.toUtcIso(opensInput.value);
        var closesAt = UI.toUtcIso(closesInput.value);
        var problem = '';

        if (!title) {
          problem = '标题不能为空';
        } else if (UI.codePointLength(title) > 200) {
          problem = '标题最多 200 字';
        } else if (!bodyText.trim()) {
          problem = '正文不能为空';
        } else if (UI.codePointLength(bodyText) > 20000) {
          problem = '正文超过 20000 字';
        } else if (UI.utf8Length(bodyText) > 65535) {
          problem = '正文 UTF-8 编码后超过 65535 字节';
        } else if (opensAt && closesAt && new Date(closesAt) <= new Date(opensAt)) {
          problem = '关闭时间必须晚于开放时间';
        }

        if (problem) {
          errorLabel.hidden = false;
          errorLabel.textContent = problem;
          return;
        }

        errorLabel.hidden = true;
        state.submitting = true;
        submitButton.disabled = true;
        submitButton.textContent = '提交中…';

        var payload = { title: title, body: bodyText, opensAt: opensAt, closesAt: closesAt };
        var request = editing
          ? API.update(announcementId, payload)
          : API.create({
            bindingId: state.bindingId,
            title: payload.title,
            body: payload.body,
            opensAt: payload.opensAt,
            closesAt: payload.closesAt
          });

        request.then(function (result) {
          state.submitting = false;
          dialog.close();
          UI.toast(editing ? '草稿已保存' : '草稿已创建，可继续发布', 'ok');
          if (!editing && result && result.data && result.data.id) {
            state.pageNum = 1;
          }
          load();
        }).catch(function (error) {
          state.submitting = false;
          submitButton.disabled = false;
          submitButton.textContent = editing ? '保存' : '保存草稿';
          errorLabel.hidden = false;
          if (error.isPermission()) {
            errorLabel.textContent = error.message + '（需要权限 ' + (editing ? PERM.edit : PERM.add) + '）';
          } else if (error.isConflict()) {
            errorLabel.textContent = '状态已变化，请刷新后重试';
            load();
          } else {
            errorLabel.textContent = error.message;
          }
        });
      });
    }

    load();

    return {
      cleanup: function () {
        if (state.detailDialog) {
          state.detailDialog.close();
        }
      }
    };
  }

  Views.register('teacher', 0, 3, view);
})(window);
