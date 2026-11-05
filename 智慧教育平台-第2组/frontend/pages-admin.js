/* ==========================================================================
   pages-admin.js —— 管理员端 5 个业务页（任务 2-03 / 2B）
   --------------------------------------------------------------------------
   本文件只做「页面拼装」：列怎么显示、按钮点了做什么、调用哪个接口。
   通用表格、弹窗、四种状态都在 edu-ui.js；请求和错误处理在 edu-api.js。

   覆盖的菜单（按侧边栏标题匹配，标题来自 admin.html 的 MENU）：
     教务组织 > 组织架构管理 / 班级管理 / 教师管理 / 学生管理 / 学期管理

   接口契约来源：2B 工作笔记《三、字段清单》，尚未与 2A 最终确认。
   路径都挂在 /education 下（BASE 在 edu-api.js），若 2A 改了前缀或字段名，
   只需要改本文件的 PATH 对象和渲染字段，不用动组件层。
   ========================================================================== */
(function (window) {
  'use strict';

  var U = window.EduUI;
  var Api = window.EduApi;
  var el = U.el;
  var text = U.text;
  var fmtDate = U.fmtDate;
  var tag = U.tag;
  var toast = U.toast;

  /* 1 组（任务 1-04）的账号编辑页地址。1B 给出真实地址后填这里，
     填了就点击直接跳转；没填则弹提示说明为什么不能跳。 */
  var ACCOUNT_PAGE = '';

  /* 接口路径集中放一处，方便和 2A 对齐时只改这里 */
  var PATH = {
    term: '/term',
    teachingClass: '/teaching-class',
    officialCourse: '/official-course',
    teacher: '/teacher',
    student: '/student',
    organization: '/organization'
  };

  /* ---------------- 通用小工具 ---------------- */

  // 后端状态值大小写不统一（ACTIVE / active），统一转大写再判断
  function norm(value) { return String(value === null || value === undefined ? '' : value).toUpperCase(); }

  var CLASS_STATUS = {
    DRAFT: { label: '草稿', type: 'grey' },
    ACTIVE: { label: '有效', type: 'green' },
    ARCHIVED: { label: '已归档', type: 'grey' },
    DISABLED: { label: '已停用', type: 'red' }
  };
  var MEMBER_STATUS = {
    ACTIVE: { label: '在班', type: 'green' },
    LEFT: { label: '已退出', type: 'grey' },
    QUIT: { label: '已退出', type: 'grey' }
  };
  var TEACHER_ROLE = { LEAD: '主讲', CO_TEACHER: '协作教师' };

  function classTag(value) {
    var meta = CLASS_STATUS[norm(value)] || { label: text(value, '未知'), type: 'grey' };
    return tag(meta.label, meta.type);
  }
  // 学生成员状态：优先看 status，没有就看 leftAt（有退出时间就是已退出）
  function memberTag(row) {
    var key = norm(row.status) || (row.leftAt ? 'LEFT' : 'ACTIVE');
    var meta = MEMBER_STATUS[key] || { label: text(row.status, '未知'), type: 'grey' };
    return tag(meta.label, meta.type);
  }
  // 学期状态不是存出来的，是按「是否为当前学期 + 今天在不在区间内」算的
  function termTag(row) {
    if (row.current === true || row.current === 1) return tag('当前学期', 'blue');
    var today = new Date().toISOString().slice(0, 10);
    var start = String(row.startsOn || '').slice(0, 10);
    var end = String(row.endsOn || '').slice(0, 10);
    if (start && today < start) return tag('未开始', 'gold');
    if (end && today > end) return tag('已结束', 'grey');
    return tag('进行中', 'green');
  }

  // 下拉选项缓存：一页里会被多个弹窗用，避免每次打开都请求一遍
  var optionsCache = {};
  function loadOptions(key, loader) {
    if (optionsCache[key]) return Promise.resolve(optionsCache[key]);
    return Promise.resolve(loader()).then(function (list) {
      optionsCache[key] = list || [];
      return optionsCache[key];
    }).catch(function () { return []; });
  }
  function termOptions() {
    return loadOptions('terms', function () {
      return Api.list(PATH.term + '/list', { pageSize: 200 }).then(function (result) {
        return result.rows.map(function (item) {
          return { value: item.id, label: item.name };
        });
      });
    });
  }
  function courseOptions() {
    return loadOptions('courses', function () {
      return Api.list(PATH.officialCourse + '/list', { pageSize: 200 }).then(function (result) {
        return result.rows.map(function (item) {
          return { value: item.id, label: item.code ? item.code + ' ' + item.name : item.name };
        });
      });
    });
  }
  function classOptions() {
    return loadOptions('classes', function () {
      return Api.list(PATH.teachingClass + '/list', { pageSize: 200 }).then(function (result) {
        return result.rows.map(function (item) {
          return { value: item.id, label: item.name };
        });
      });
    });
  }
  function teacherOptions() {
    return loadOptions('teachers', function () {
      return Api.list(PATH.teacher + '/list', { pageSize: 200 }).then(function (result) {
        return result.rows.map(function (item) {
          return { value: item.userId, label: (item.userName || '') + ' ' + (item.nickName || '') };
        });
      });
    });
  }
  function studentOptions() {
    return loadOptions('students', function () {
      return Api.list(PATH.student + '/list', { pageSize: 200 }).then(function (result) {
        return result.rows.map(function (item) {
          return { value: item.userId, label: (item.userName || '') + ' ' + (item.nickName || '') };
        });
      });
    });
  }

  /* 把下拉选项异步塞进一个已经打开的表单：先给空列表，加载完再填充。
     这样弹窗能立刻出现，不会卡在「等下拉数据」上。
     注意要回填原值：编辑场景下 select 的选项还没到，直接赋值会被浏览器丢掉。 */
  function fillSelect(optionsPromise, modalRef, fieldKey) {
    var desired = modalRef && modalRef.values ? modalRef.values[fieldKey] : '';
    optionsPromise.then(function (options) {
      var select = document.querySelector('#edu-f-' + fieldKey);
      if (!select) return;
      options.forEach(function (option) {
        select.appendChild(el('option', { value: option.value, text: option.label }));
      });
      if (desired !== undefined && desired !== null && desired !== '') {
        select.value = String(desired);
      }
    });
  }

  /* ==========================================================================
     页面 1：学期管理  —— 新建学期、设为当前学期
     ========================================================================== */
  function termPage(host) {
    var ctx = U.listView(host, {
      title: '学期管理',
      subtitle: '学期是教学班的归属基础：先有学期，才能建教学班。',
      alert: '「当前学期」决定教师端和学生端默认看到的课程范围。同一时间只应有一个当前学期。',
      actions: [{
        text: '+ 新建学期', primary: true,
        onClick: function () { openTermForm(null, ctx); }
      }],
      filters: [
        { key: 'keyword', type: 'input', label: '学期名称', placeholder: '按名称或编码搜索' }
      ],
      search: function (params) {
        return Api.list(PATH.term + '/list', params);
      },
      columns: [
        { key: 'name', label: '学期名称', render: function (row) { return el('span', { class: 'edu-strong', text: text(row.name) }); } },
        { key: 'code', label: '编码', className: 'edu-mono', render: function (row) { return text(row.code); } },
        {
          key: 'dates', label: '起止日期', render: function (row) {
            return fmtDate(row.startsOn) + ' ~ ' + fmtDate(row.endsOn);
          }
        },
        { key: 'status', label: '状态', width: '110px', render: function (row) { return termTag(row); } }
      ],
      rowActions: function (row, listCtx) {
        return [
          { text: '编辑', onClick: function () { openTermForm(row, listCtx); } },
          (row.current === true || row.current === 1) ? null : {
            text: '设为当前', onClick: function () {
              U.confirm({
                title: '设为当前学期',
                message: '把「' + text(row.name) + '」设为当前学期？教师端和学生端默认展示的课程范围会随之切换。'
              }).then(function (ok) {
                if (!ok) return;
                Api.put(PATH.term + '/' + row.id + '/current').then(function () {
                  toast('已设为当前学期', 'success');
                  listCtx.reload();
                }).catch(function (error) { toast(error.message, 'error'); });
              });
            }
          }
        ];
      },
      emptyText: '还没有学期',
      emptyHint: '学期是建教学班的前提，先建一个学期再回来建班。',
      emptyAction: function (listCtx) {
        return { text: '+ 新建学期', primary: true, onClick: function () { openTermForm(null, listCtx); } };
      }
    });
    return ctx;
  }

  function openTermForm(row, ctx) {
    U.modal({
      title: row ? '编辑学期' : '新建学期',
      submitText: row ? '保存' : '创建',
      values: row ? {
        name: row.name, code: row.code,
        startsOn: fmtDate(row.startsOn) === '—' ? '' : fmtDate(row.startsOn),
        endsOn: fmtDate(row.endsOn) === '—' ? '' : fmtDate(row.endsOn)
      } : {},
      fields: [
        { key: 'name', label: '学期名称', required: true, placeholder: '如 2026-2027 学年第一学期' },
        { key: 'code', label: '学期编码', required: true, placeholder: '如 2026-2027-1（同一学校内唯一）' },
        { key: 'startsOn', label: '开始日期', type: 'date', required: true },
        {
          key: 'endsOn', label: '结束日期', type: 'date', required: true,
          validate: function (value, values) {
            if (value && values.startsOn && value < values.startsOn) return '结束日期不能早于开始日期';
            return '';
          }
        }
      ],
      onSubmit: function (values) {
        var payload = {
          name: values.name.trim(),
          code: values.code.trim(),
          startsOn: values.startsOn,
          endsOn: values.endsOn
        };
        var request = row
          ? Api.put(PATH.term + '/' + row.id, payload)
          : Api.post(PATH.term, payload);
        return request.then(function () {
          toast(row ? '已保存' : '学期已创建', 'success');
          ctx.reload();
        });
      }
    });
  }

  /* ==========================================================================
     页面 2：班级管理（核心页）—— 教学班 + 成员 + 任课教师授权
     ========================================================================== */
  function classPage(host) {
    var ctx = U.listView(host, {
      title: '班级管理',
      subtitle: '教学班是「课程 × 学期 × 一批学生」的组合。教师授权和成员名单都在这里维护。',
      filters: [
        { key: 'keyword', type: 'input', label: '班级', placeholder: '班级名称 / 编码' },
        { key: 'termId', type: 'select', label: '学期', placeholder: '全部学期', optionsPromise: termOptions() },
        { key: 'status', type: 'select', label: '状态', placeholder: '全部状态', options: [
          { value: 'ACTIVE', label: '有效' },
          { value: 'DRAFT', label: '草稿' },
          { value: 'ARCHIVED', label: '已归档' }
        ] }
      ],
      actions: [{
        text: '+ 新建教学班', primary: true,
        onClick: function () { openClassForm(null, ctx); }
      }],
      search: function (params) {
        return Api.list(PATH.teachingClass + '/list', params);
      },
      columns: [
        {
          key: 'name', label: '教学班', render: function (row) {
            var box = el('div');
            box.appendChild(el('div', { class: 'edu-strong', text: text(row.name) }));
            if (row.code) box.appendChild(el('div', { class: 'edu-mono', text: row.code }));
            return box;
          }
        },
        { key: 'courseName', label: '课程', render: function (row) { return text(row.courseName, '未绑定课程'); } },
        { key: 'semesterName', label: '学期', render: function (row) { return text(row.semesterName, text(row.termName)); } },
        { key: 'teacherName', label: '任课教师', render: function (row) { return text(row.teacherName, '未指定'); } },
        {
          key: 'studentCount', label: '学生数', width: '90px',
          render: function (row) { return text(row.studentCount, '0') + ' 人'; }
        },
        { key: 'status', label: '状态', width: '100px', render: function (row) { return classTag(row.status); } }
      ],
      rowActions: function (row, listCtx) {
        return [
          { text: '成员', onClick: function () { openMembers(row, listCtx); } },
          { text: '任课教师', onClick: function () { openTeachers(row, listCtx); } },
          { text: '编辑', onClick: function () { openClassForm(row, listCtx); } }
        ];
      },
      emptyText: '还没有教学班',
      emptyHint: '点右上角「新建教学班」，填名称、学期和课程就能建好。',
      emptyAction: function (listCtx) {
        return { text: '+ 新建教学班', primary: true, onClick: function () { openClassForm(null, listCtx); } };
      }
    });

    return ctx;
  }

  function openClassForm(row, ctx) {
    var modalRef = U.modal({
      title: row ? '编辑教学班' : '新建教学班',
      submitText: row ? '保存' : '创建',
      values: row ? {
        name: row.name, code: row.code, termId: row.semesterId || row.termId,
        officialCourseId: row.courseId, classKind: row.classKind || 'FORMAL', status: row.status || 'DRAFT'
      } : { classKind: 'FORMAL', status: 'DRAFT' },
      fields: [
        { key: 'name', label: '班级名称', required: true, placeholder: '如 2026 秋-软件工程 1 班' },
        { key: 'code', label: '班级编码', required: true, placeholder: '同一学期内唯一，如 SE-2026-01' },
        { key: 'termId', label: '所属学期', type: 'select', required: true, options: [], placeholder: '请选择学期' },
        { key: 'officialCourseId', label: '正式课程', type: 'select', options: [], placeholder: '可暂不绑定' },
        { key: 'classKind', label: '班级类型', type: 'select', required: true, options: [
          { value: 'FORMAL', label: '正式班' }, { value: 'TRIAL', label: '试听班' }
        ], hint: '试听班不计入正式教学统计。' },
        { key: 'status', label: '状态', type: 'select', required: true, options: [
          { value: 'DRAFT', label: '草稿' }, { value: 'ACTIVE', label: '有效' }, { value: 'ARCHIVED', label: '已归档' }
        ], hint: '草稿状态不对学生开放；归档后只读。' }
      ],
      onSubmit: function (values) {
        var payload = {
          name: values.name.trim(),
          code: values.code.trim(),
          termId: values.termId,
          officialCourseId: values.officialCourseId || null,
          classKind: values.classKind,
          status: values.status
        };
        var request = row
          ? Api.put(PATH.teachingClass + '/' + row.id, payload)
          : Api.post(PATH.teachingClass, payload);
        return request.then(function () {
          toast(row ? '已保存' : '教学班已创建', 'success');
          optionsCache.classes = null; // 班级列表变了，下拉缓存失效
          ctx.reload();
        });
      }
    });
    fillSelect(termOptions(), modalRef, 'termId');
    fillSelect(courseOptions(), modalRef, 'officialCourseId');
  }

  /* ---- 成员管理：加人 / 移人，历史记录不删只标退出 ---- */
  function openMembers(row, ctx) {
    var panel = U.panel({ title: text(row.name) + ' · 成员管理', width: 780 });
    var bodyHost = el('div');
    var toolbar = el('div', { class: 'edu-toolbar' }, [
      el('span', { class: 'edu-field', text: '每个学生在同一教学班只有一条有效记录；移出后记录保留为「已退出」，便于追溯。' }),
      el('button', {
        class: 'edu-btn edu-btn-primary edu-btn-sm', type: 'button', text: '+ 添加学生',
        style: 'margin-left:auto', onclick: function () { openAddMember(row, afterChange); }
      })
    ]);
    panel.body.appendChild(el('div', { class: 'edu-alert edu-alert-info' }, [
      el('span', { text: 'ⓘ' }),
      el('span', { text: '移出成员不会删除历史记录，只是标记退出时间；重新加入会生成新的有效记录。' })
    ]));
    panel.body.appendChild(toolbar);
    panel.body.appendChild(bodyHost);

    function renderMembers(rows) {
      U.clear(bodyHost);
      if (!rows.length) {
        bodyHost.appendChild(U.state('empty', {
          title: '这个班还没有学生',
          desc: '点上方「添加学生」，按学号把学生加进来。',
          actions: [{ text: '+ 添加学生', primary: true, onClick: function () { openAddMember(row, afterChange); } }]
        }));
        return;
      }
      var table = el('table', { class: 'edu-table' });
      table.appendChild(el('thead', {}, [el('tr', {}, [
        el('th', { text: '学号' }),
        el('th', { text: '姓名' }),
        el('th', { text: '状态' }),
        el('th', { text: '加入时间' }),
        el('th', { text: '退出时间' }),
        el('th', { class: 'edu-col-actions', text: '操作' })
      ])]));
      var tbody = el('tbody');
      rows.forEach(function (member) {
        var isActive = (norm(member.status) || (member.leftAt ? 'LEFT' : 'ACTIVE')) === 'ACTIVE';
        var actionCell = el('td', { class: 'edu-col-actions' });
        if (isActive) {
          actionCell.appendChild(el('button', {
            class: 'edu-btn edu-btn-text edu-btn-danger', type: 'button', text: '移出',
            onclick: function () {
              U.confirm({
                title: '移出学生',
                message: '把「' + text(member.studentName || member.nickName) + '」移出本教学班？移出后该学生看不到本班课程内容。',
                danger: true, okText: '移出'
              }).then(function (ok) {
                if (!ok) return;
                Api.del(PATH.teachingClass + '/' + row.id + '/members/' + (member.studentId || member.userId))
                  .then(function () { toast('已移出', 'success'); reload(); if (ctx) ctx.reload(); })
                  .catch(function (error) { toast(error.message, 'error'); });
              });
            }
          }));
        } else {
          actionCell.appendChild(el('span', { class: 'edu-mono', text: '—' }));
        }
        tbody.appendChild(el('tr', {}, [
          el('td', { class: 'edu-mono', text: text(member.studentNo || member.userName) }),
          el('td', { text: text(member.studentName || member.nickName) }),
          el('td', {}, [memberTag(member)]),
          el('td', { text: fmtDate(member.joinedAt) }),
          el('td', { text: fmtDate(member.leftAt) }),
          actionCell
        ]));
      });
      table.appendChild(tbody);
      bodyHost.appendChild(el('div', { class: 'edu-table-wrap' }, [table]));
    }

    // 成员变动后：本面板刷新 + 外层列表的学生数也要刷新
    function afterChange() { reload(); if (ctx) ctx.reload(); }

    function reload() {
      U.clear(bodyHost);
      bodyHost.appendChild(U.state('loading'));
      Api.list(PATH.teachingClass + '/' + row.id + '/members', {})
        .then(function (result) {
          renderMembers(result.rows);
        })
        .catch(function (error) {
          U.clear(bodyHost);
          if (error.kind === 'forbidden') bodyHost.appendChild(U.state('forbidden', { desc: '你没有查看本班成员的权限。' }));
          else bodyHost.appendChild(U.state('error', { desc: error.message, actions: [{ text: '重试', primary: true, onClick: reload }] }));
        });
    }
    reload();
  }

  function openAddMember(row, onDone) {
    var modalRef = U.modal({
      title: '添加学生到 ' + text(row.name),
      submitText: '添加',
      fields: [
        { key: 'studentId', label: '学生', type: 'select', required: true, options: [], placeholder: '请选择学生' },
        { key: 'joinedAt', label: '加入时间', type: 'date', required: true, defaultValue: new Date().toISOString().slice(0, 10) },
        { key: 'note', label: '备注', type: 'textarea', placeholder: '可留空。例如：本学期转入。', hint: '备注只给管理员看，学生端不可见。' }
      ],
      onSubmit: function (values) {
        return Api.post(PATH.teachingClass + '/' + row.id + '/members', {
          studentId: values.studentId,
          joinedAt: values.joinedAt,
          note: values.note.trim()
        }).then(function () {
          toast('已添加', 'success');
          onDone();
        });
      }
    });
    fillSelect(studentOptions(), modalRef, 'studentId');
  }

  /* ---- 任课教师授权：谁能教、能发布、能批改、能导出 ---- */
  function openTeachers(row, ctx) {
    var panel = U.panel({ title: text(row.name) + ' · 任课教师', width: 860 });
    var bodyHost = el('div');
    panel.body.appendChild(el('div', { class: 'edu-alert edu-alert-info' }, [
      el('span', { text: 'ⓘ' }),
      el('span', { text: '教师能看到哪个班，由这里的授权决定；撤权后该教师立即看不到本班，不依赖前端按钮隐藏。' })
    ]));
    panel.body.appendChild(el('div', { class: 'edu-toolbar' }, [
      el('button', {
        class: 'edu-btn edu-btn-primary edu-btn-sm', type: 'button', text: '+ 指定教师',
        onclick: function () { openAddTeacher(row, reload); }
      })
    ]));
    panel.body.appendChild(bodyHost);

    function flag(value) {
      return value === true || value === 1 || value === '1' ? tag('是', 'green') : tag('否', 'grey');
    }

    function renderTeachers(rows) {
      U.clear(bodyHost);
      if (!rows.length) {
        bodyHost.appendChild(U.state('empty', {
          title: '还没有指定任课教师',
          desc: '未指定教师时，这个班只有管理员能看到。',
          actions: [{ text: '+ 指定教师', primary: true, onClick: function () { openAddTeacher(row, reload); } }]
        }));
        return;
      }
      var table = el('table', { class: 'edu-table' });
      table.appendChild(el('thead', {}, [el('tr', {}, [
        el('th', { text: '工号' }),
        el('th', { text: '姓名' }),
        el('th', { text: '角色' }),
        el('th', { text: '可发布' }),
        el('th', { text: '可批改' }),
        el('th', { text: '可导出' }),
        el('th', { text: '有效期' }),
        el('th', { class: 'edu-col-actions', text: '操作' })
      ])]));
      var tbody = el('tbody');
      rows.forEach(function (teacher) {
        var active = !teacher.validTo;
        var actionCell = el('td', { class: 'edu-col-actions' });
        if (active) {
          actionCell.appendChild(el('button', {
            class: 'edu-btn edu-btn-text edu-btn-danger', type: 'button', text: '撤权',
            onclick: function () {
              U.confirm({
                title: '撤销授课授权',
                message: '撤销「' + text(teacher.nickName || teacher.teacherName) + '」在本班的授课权限？撤销后其立刻无法访问本班数据。',
                danger: true, okText: '撤权'
              }).then(function (ok) {
                if (!ok) return;
                Api.del(PATH.teachingClass + '/' + row.id + '/teachers/' + (teacher.userId || teacher.teacherId))
                  .then(function () { toast('已撤权', 'success'); reload(); if (ctx) ctx.reload(); })
                  .catch(function (error) { toast(error.message, 'error'); });
              });
            }
          }));
        } else {
          actionCell.appendChild(el('span', { class: 'edu-mono', text: '已结束' }));
        }
        tbody.appendChild(el('tr', {}, [
          el('td', { class: 'edu-mono', text: text(teacher.userName) }),
          el('td', { text: text(teacher.nickName || teacher.teacherName) }),
          el('td', { text: TEACHER_ROLE[norm(teacher.teacherRole)] || text(teacher.teacherRole) }),
          el('td', {}, [flag(teacher.canPublish)]),
          el('td', {}, [flag(teacher.canGrade)]),
          el('td', {}, [flag(teacher.canExport)]),
          el('td', { text: fmtDate(teacher.validFrom) + ' ~ ' + (teacher.validTo ? fmtDate(teacher.validTo) : '长期') }),
          actionCell
        ]));
      });
      table.appendChild(tbody);
      bodyHost.appendChild(el('div', { class: 'edu-table-wrap' }, [table]));
    }

    function reload() {
      U.clear(bodyHost);
      bodyHost.appendChild(U.state('loading'));
      Api.list(PATH.teachingClass + '/' + row.id + '/teachers', {})
        .then(function (result) { renderTeachers(result.rows); })
        .catch(function (error) {
          U.clear(bodyHost);
          if (error.kind === 'forbidden') bodyHost.appendChild(U.state('forbidden', { desc: '你没有查看授课授权的权限。' }));
          else bodyHost.appendChild(U.state('error', { desc: error.message, actions: [{ text: '重试', primary: true, onClick: reload }] }));
        });
    }
    reload();
  }

  function openAddTeacher(row, onDone) {
    var modalRef = U.modal({
      title: '指定任课教师',
      submitText: '授权',
      values: { teacherRole: 'LEAD', canPublish: true, canGrade: true, canExport: false },
      fields: [
        { key: 'userId', label: '教师', type: 'select', required: true, options: [], placeholder: '请选择教师' },
        { key: 'teacherRole', label: '角色', type: 'select', required: true, options: [
          { value: 'LEAD', label: '主讲' }, { value: 'CO_TEACHER', label: '协作教师' }
        ] },
        { key: 'canPublish', label: '发布资料', type: 'checkbox', checkboxText: '允许发布课程资料与公告' },
        { key: 'canGrade', label: '批改作业', type: 'checkbox', checkboxText: '允许批改并提交成绩' },
        { key: 'canExport', label: '导出数据', type: 'checkbox', checkboxText: '允许导出学情与成绩明细' },
        { key: 'validTo', label: '有效期至', type: 'date', hint: '留空表示长期有效；填了到期后自动失效，需要重新授权。' }
      ],
      onSubmit: function (values) {
        return Api.post(PATH.teachingClass + '/' + row.id + '/teachers', {
          userId: values.userId,
          teacherRole: values.teacherRole,
          canPublish: values.canPublish ? 1 : 0,
          canGrade: values.canGrade ? 1 : 0,
          canExport: values.canExport ? 1 : 0,
          validTo: values.validTo || null
        }).then(function () {
          toast('已授权', 'success');
          onDone();
        });
      }
    });
    fillSelect(teacherOptions(), modalRef, 'userId');
  }

  /* ==========================================================================
     页面 3：教师管理 —— 看任教关系；改账号跳第 1 组
     ========================================================================== */
  function teacherPage(host) {
    U.listView(host, {
      title: '教师管理',
      subtitle: '本页只看教学关系（教哪些班、有哪些权限）。姓名、密码、角色属于账号体系，由第 1 组维护。',
      alert: '「编辑账号」不会在这里改，会跳到第 1 组（任务 1-04）的用户管理页，避免两处改同一份账号数据。',
      filters: [
        { key: 'keyword', type: 'input', label: '教师', placeholder: '工号 / 姓名' }
      ],
      search: function (params) { return Api.list(PATH.teacher + '/list', params); },
      columns: [
        { key: 'userName', label: '工号', className: 'edu-mono', render: function (row) { return text(row.userName); } },
        { key: 'nickName', label: '姓名', render: function (row) { return el('span', { class: 'edu-strong', text: text(row.nickName || row.teacherName) }); } },
        { key: 'deptName', label: '所属部门', render: function (row) { return text(row.deptName); } },
        { key: 'classCount', label: '任教班级数', width: '110px', render: function (row) { return text(row.classCount, '0') + ' 个'; } },
        { key: 'phone', label: '联系电话', render: function (row) { return text(row.phonenumber || row.phone); } }
      ],
      rowActions: function (row) {
        return [
          { text: '任教关系', onClick: function () { openTeacherClasses(row); } },
          { text: '编辑账号', onClick: function () { gotoAccountPage(row); } }
        ];
      },
      emptyText: '没有匹配的教师',
      emptyHint: '教师账号本身在第 1 组的用户管理里创建；这里显示的是已有账号的教学关系。'
    });
  }

  function openTeacherClasses(row) {
    var panel = U.panel({ title: text(row.nickName || row.teacherName) + ' · 任教关系', width: 720 });
    var bodyHost = el('div');
    panel.body.appendChild(bodyHost);

    function reload() {
      U.clear(bodyHost);
      bodyHost.appendChild(U.state('loading'));
      Api.list(PATH.teacher + '/' + row.userId + '/classes', {})
        .then(function (result) {
          U.clear(bodyHost);
          if (!result.rows.length) {
            bodyHost.appendChild(U.state('empty', {
              title: '该教师暂未任教任何班级',
              desc: '去「班级管理」里指定任课教师后，这里就会出现。'
            }));
            return;
          }
          var table = el('table', { class: 'edu-table' });
          table.appendChild(el('thead', {}, [el('tr', {}, [
            el('th', { text: '教学班' }), el('th', { text: '学期' }), el('th', { text: '角色' }),
            el('th', { text: '权限' }), el('th', { text: '状态' })
          ])]));
          var tbody = el('tbody');
          result.rows.forEach(function (item) {
            var permissions = [];
            if (item.canPublish === 1 || item.canPublish === true) permissions.push('发布');
            if (item.canGrade === 1 || item.canGrade === true) permissions.push('批改');
            if (item.canExport === 1 || item.canExport === true) permissions.push('导出');
            tbody.appendChild(el('tr', {}, [
              el('td', { text: text(item.className || item.name) }),
              el('td', { text: text(item.termName || item.semesterName) }),
              el('td', { text: TEACHER_ROLE[norm(item.teacherRole)] || text(item.teacherRole) }),
              el('td', { text: permissions.length ? permissions.join(' / ') : '无' }),
              el('td', {}, [item.validTo ? tag('已结束', 'grey') : tag('有效', 'green')])
            ]));
          });
          table.appendChild(tbody);
          bodyHost.appendChild(el('div', { class: 'edu-table-wrap' }, [table]));
        })
        .catch(function (error) {
          U.clear(bodyHost);
          if (error.kind === 'forbidden') bodyHost.appendChild(U.state('forbidden', { desc: '你没有查看该教师任教关系的权限。' }));
          else bodyHost.appendChild(U.state('error', { desc: error.message, actions: [{ text: '重试', primary: true, onClick: reload }] }));
        });
    }
    reload();
  }

  function gotoAccountPage(row) {
    if (ACCOUNT_PAGE) {
      window.open(ACCOUNT_PAGE + '?userId=' + encodeURIComponent(row.userId), '_blank');
      return;
    }
    U.confirm({
      title: '账号编辑不在本页',
      message: '教师账号的姓名、密码、角色由第 1 组（任务 1-04）的用户管理页维护。\n\n'
        + '等 1B 给出页面地址后，在 frontend/education/pages-admin.js 顶部的 ACCOUNT_PAGE 填入，'
        + '这个按钮就会自动跳过去。',
      okText: '知道了'
    });
  }

  /* ==========================================================================
     页面 4：学生管理 —— 看在班状态；调整班级归属
     ========================================================================== */
  function studentPage(host) {
    U.listView(host, {
      title: '学生管理',
      subtitle: '本页只看学生在哪些教学班、是否仍有效。学籍和账号信息在第 1 组。',
      alert: '同一个学生可以同时在多个教学班（比如不同课程）。「已退出」的记录会保留，不会被删除。',
      filters: [
        { key: 'keyword', type: 'input', label: '学生', placeholder: '学号 / 姓名' },
        { key: 'status', type: 'select', label: '状态', placeholder: '全部', options: [
          { value: 'ACTIVE', label: '在读' }, { value: 'LEFT', label: '已退出' }
        ] }
      ],
      search: function (params) { return Api.list(PATH.student + '/list', params); },
      columns: [
        { key: 'userName', label: '学号', className: 'edu-mono', render: function (row) { return text(row.userName); } },
        { key: 'nickName', label: '姓名', render: function (row) { return el('span', { class: 'edu-strong', text: text(row.nickName || row.studentName) }); } },
        {
          key: 'className', label: '所在班级', render: function (row) {
            // 可能是单个字符串，也可能是数组，统一展示
            if (Array.isArray(row.classNames)) {
              if (!row.classNames.length) return text(null, '未分班');
              return row.classNames.join('、');
            }
            return text(row.className || row.classNames, '未分班');
          }
        },
        { key: 'classCount', label: '班级数', width: '90px', render: function (row) { return text(row.classCount, '0') + ' 个'; } },
        { key: 'status', label: '状态', width: '100px', render: function (row) { return memberTag(row); } }
      ],
      rowActions: function (row) {
        return [
          { text: '在班记录', onClick: function () { openStudentClasses(row); } },
          { text: '调整班级', onClick: function () { openAdjustClass(row); } }
        ];
      },
      emptyText: '没有匹配的学生',
      emptyHint: '学生账号在第 1 组的用户管理里创建，分班在「班级管理 → 成员」里完成。'
    });
  }

  function openStudentClasses(row) {
    var panel = U.panel({ title: text(row.nickName || row.studentName) + ' · 在班记录', width: 720 });
    var bodyHost = el('div');
    panel.body.appendChild(bodyHost);

    function reload() {
      U.clear(bodyHost);
      bodyHost.appendChild(U.state('loading'));
      Api.list(PATH.student + '/' + row.userId + '/classes', {})
        .then(function (result) {
          U.clear(bodyHost);
          if (!result.rows.length) {
            bodyHost.appendChild(U.state('empty', { title: '该学生还没有加入任何教学班', desc: '去「班级管理 → 成员」里添加。' }));
            return;
          }
          var table = el('table', { class: 'edu-table' });
          table.appendChild(el('thead', {}, [el('tr', {}, [
            el('th', { text: '教学班' }), el('th', { text: '学期' }), el('th', { text: '加入时间' }),
            el('th', { text: '退出时间' }), el('th', { text: '状态' })
          ])]));
          var tbody = el('tbody');
          result.rows.forEach(function (item) {
            tbody.appendChild(el('tr', {}, [
              el('td', { text: text(item.className || item.name) }),
              el('td', { text: text(item.termName || item.semesterName) }),
              el('td', { text: fmtDate(item.joinedAt) }),
              el('td', { text: fmtDate(item.leftAt) }),
              el('td', {}, [memberTag(item)])
            ]));
          });
          table.appendChild(tbody);
          bodyHost.appendChild(el('div', { class: 'edu-table-wrap' }, [table]));
        })
        .catch(function (error) {
          U.clear(bodyHost);
          if (error.kind === 'forbidden') bodyHost.appendChild(U.state('forbidden', { desc: '你没有查看该学生在班记录的权限。' }));
          else bodyHost.appendChild(U.state('error', { desc: error.message, actions: [{ text: '重试', primary: true, onClick: reload }] }));
        });
    }
    reload();
  }

  function openAdjustClass(row) {
    var modalRef = U.modal({
      title: '调整「' + text(row.nickName || row.studentName) + '」的班级',
      submitText: '执行',
      values: { action: 'JOIN' },
      fields: [
        { key: 'classId', label: '教学班', type: 'select', required: true, options: [], placeholder: '请选择教学班' },
        { key: 'action', label: '操作', type: 'select', required: true, options: [
          { value: 'JOIN', label: '加入该班' }, { value: 'LEAVE', label: '从该班移出' }
        ], hint: '移出只是标记退出时间，记录会保留。' }
      ],
      onSubmit: function (values) {
        var request = values.action === 'JOIN'
          ? Api.post(PATH.teachingClass + '/' + values.classId + '/members', { studentId: row.userId })
          : Api.del(PATH.teachingClass + '/' + values.classId + '/members/' + row.userId);
        return request.then(function () {
          toast(values.action === 'JOIN' ? '已加入班级' : '已移出班级', 'success');
          optionsCache.classes = null;
        });
      }
    });
    fillSelect(classOptions(), modalRef, 'classId');
  }

  /* ==========================================================================
     页面 5：组织架构管理 —— 左侧部门树，右侧该部门的教师
     ========================================================================== */
  function orgPage(host) {
    U.clear(host);
    var page = el('div', { class: 'edu-page' });
    var pageTitle = document.getElementById('pageTitle');
    var head = el('div', { class: 'edu-head' });
    var headLeft = el('div');
    if (pageTitle) { pageTitle.style.display = ''; headLeft.appendChild(pageTitle); }
    headLeft.appendChild(el('div', {
      class: 'edu-head-sub',
      text: '部门和组织结构本身由第 1 组维护；本页只把教学数据挂到部门下展示。'
    }));
    head.appendChild(headLeft);
    page.appendChild(head);

    var split = el('div', { class: 'edu-split' });
    var treeHost = el('div', { class: 'edu-tree' });
    var mainHost = el('div', { class: 'edu-split-main' });
    split.appendChild(treeHost);
    split.appendChild(mainHost);
    page.appendChild(split);
    host.appendChild(page);
    host.classList.add('edu-mounted');

    U.clear(treeHost);
    treeHost.appendChild(U.state('loading'));
    U.clear(mainHost);
    mainHost.appendChild(U.state('empty', { title: '从左侧选择一个部门', desc: '选中后可查看该部门的教师和教学班。' }));

    Api.get(PATH.organization + '/tree').then(function (data) {
      var nodes = Array.isArray(data) ? data : (data && data.rows) || [];
      U.clear(treeHost);
      if (!nodes.length) {
        treeHost.appendChild(U.state('empty', { title: '没有组织结构数据' }));
        return;
      }
      renderTree(nodes);
      // 默认选中第一个节点，避免右侧一片空白
      var first = treeHost.querySelector('.edu-tree-node');
      if (first) first.click();
    }).catch(function (error) {
      U.clear(treeHost);
      treeHost.appendChild(error.kind === 'forbidden'
        ? U.state('forbidden', { desc: '你没有查看组织架构的权限。' })
        : U.state('error', { desc: error.message, actions: [{ text: '重试', primary: true, onClick: function () { orgPage(host); } }] }));
    });

    // 后端可能返回扁平列表，也可能返回带 children 的树，两种都兼容
    function renderTree(nodes) {
      var byParent = {};
      nodes.forEach(function (node) {
        var parent = node.parentId === undefined || node.parentId === null ? 'root' : String(node.parentId);
        (byParent[parent] = byParent[parent] || []).push(node);
      });
      function walk(parentId, depth) {
        (byParent[parentId] || []).forEach(function (node) {
          var label = node.name || node.label || node.deptName;
          var counts = [];
          if (node.teacherCount !== undefined) counts.push('师 ' + node.teacherCount);
          if (node.studentCount !== undefined) counts.push('生 ' + node.studentCount);
          var row = el('div', {
            class: 'edu-tree-node',
            style: 'padding-left:' + (12 + depth * 14) + 'px',
            text: label + (counts.length ? '（' + counts.join(' · ') + '）' : ''),
            dataset: { id: node.id, name: label, teacherCount: node.teacherCount, studentCount: node.studentCount }
          });
          row.addEventListener('click', function () {
            treeHost.querySelectorAll('.edu-tree-node').forEach(function (n) { n.classList.remove('active'); });
            row.classList.add('active');
            loadDept(row.dataset);
          });
          treeHost.appendChild(row);
          walk(String(node.id), depth + 1);
        });
      }
      walk('root', 0);
      // 没有 parentId 字段时，全部当一级节点
      if (!treeHost.children.length) {
        nodes.forEach(function (node) {
          var label = node.name || node.label;
          var row = el('div', { class: 'edu-tree-node', text: label, dataset: { id: node.id, name: label } });
          row.addEventListener('click', function () {
            treeHost.querySelectorAll('.edu-tree-node').forEach(function (n) { n.classList.remove('active'); });
            row.classList.add('active');
            loadDept(row.dataset);
          });
          treeHost.appendChild(row);
        });
      }
    }

    function loadDept(dataset) {
      U.clear(mainHost);
      var info = U.card('部门信息', U.descList([
        { label: '部门名称', value: dataset.name },
        { label: '部门 ID', value: dataset.id },
        { label: '教师数', value: dataset.teacherCount },
        { label: '学生数', value: dataset.studentCount }
      ]));
      mainHost.appendChild(info);

      var listCard = U.card('该部门教师');
      var listHost = el('div');
      listCard.appendChild(listHost);
      mainHost.appendChild(listCard);
      U.clear(listHost);
      listHost.appendChild(U.state('loading'));

      Api.list(PATH.teacher + '/list', { deptId: dataset.id, pageSize: 100 }).then(function (result) {
        U.clear(listHost);
        if (!result.rows.length) {
          listHost.appendChild(U.state('empty', { title: '该部门暂无教师', desc: '教师账号由第 1 组在用户管理中创建并分配部门。' }));
          return;
        }
        var table = el('table', { class: 'edu-table' });
        table.appendChild(el('thead', {}, [el('tr', {}, [
          el('th', { text: '工号' }), el('th', { text: '姓名' }), el('th', { text: '任教班级数' }), el('th', { text: '联系电话' })
        ])]));
        var tbody = el('tbody');
        result.rows.forEach(function (teacher) {
          tbody.appendChild(el('tr', {}, [
            el('td', { class: 'edu-mono', text: text(teacher.userName) }),
            el('td', { text: text(teacher.nickName) }),
            el('td', { text: text(teacher.classCount, '0') + ' 个' }),
            el('td', { text: text(teacher.phonenumber) })
          ]));
        });
        table.appendChild(tbody);
        listHost.appendChild(el('div', { class: 'edu-table-wrap' }, [table]));
      }).catch(function (error) {
        U.clear(listHost);
        listHost.appendChild(error.kind === 'forbidden'
          ? U.state('forbidden', { desc: '你没有查看该部门教师的权限。' })
          : U.state('error', { desc: error.message, actions: [{ text: '重试', primary: true, onClick: function () { loadDept(dataset); } }] }));
      });
    }
  }

  /* ---------------- 注册：菜单标题 → 页面函数 ---------------- */
  window.EduPagesAdmin = {
    '学期管理': termPage,
    '班级管理': classPage,
    '教师管理': teacherPage,
    '学生管理': studentPage,
    '组织架构管理': orgPage
  };
})(window);
