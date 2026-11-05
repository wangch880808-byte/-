/* ==========================================================================
   pages-teacher.js —— 教师端课程中心（学习通风格）
   --------------------------------------------------------------------------
   首页 = 我的课程卡片墙；点卡片进课程空间：目录 / 资料 / 作业 / 讨论 / 统计。
   只有「目录」有真实数据（章节、知识点、已绑定教学班），其余模块显示归属说明。
   接口沿用现有契约：/education/course、/education/teaching-class、/education/official-course。
   ========================================================================== */
(function (window) {
  'use strict';

  var U = window.EduUI;
  var Api = window.EduApi;
  var el = U.el;
  var toast = U.toast;
  var fmtDate = U.fmtDate;

  var PATH = { course: '/course', teachingClass: '/teaching-class', officialCourse: '/official-course' };

  var COVERS = [
    ['#5b8ff9', '#2b7de9'],
    ['#31c48d', '#0e9f6e'],
    ['#f6ad55', '#ed8936'],
    ['#9f7aea', '#6b46c1'],
    ['#f687b3', '#d53f8c']
  ];

  var STATUS = {
    DRAFT: '未发布',
    ACTIVE: '进行中',
    ARCHIVED: '已归档',
    DISABLED: '已停用'
  };

  var state = { rows: [], keyword: '' };

  function esc(value) {
    return String(value === null || value === undefined ? '' : value).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function cover(id) {
    var pair = COVERS[Math.abs(Number(id) || 0) % COVERS.length];
    return '--c1:' + pair[0] + ';--c2:' + pair[1];
  }

  function norm(value) { return String(value === null || value === undefined ? '' : value).toUpperCase(); }

  function query(name) {
    var match = location.search.match(new RegExp('[?&]' + name + '=([^&]*)'));
    return match ? decodeURIComponent(match[1]) : '';
  }

  function go(url) { window.location.href = url; }

  /* ==========================================================================
     一、我的课程（卡片墙）
     ========================================================================== */

  function courseListPage(host) {
    state.keyword = '';
    host.classList.add('edu-mounted');
    host.innerHTML = '<section class="xt-page">'
      + '<div class="xt-page-head">'
      + '  <div><h2>我的课程</h2><p>只显示你被授权任教的课程，没授权就不会出现。</p></div>'
      + '  <div class="xt-head-actions"><button class="xt-btn xt-btn-primary" data-act="new">+ 新建课程</button></div>'
      + '</div>'
      + '<div id="xtListBody"><div class="xt-loading">'
      + '  <div class="xt-skeleton"></div><div class="xt-skeleton"></div><div class="xt-skeleton"></div>'
      + '</div></div>'
      + '</section>';

    host.querySelector('[data-act="new"]').addEventListener('click', function () {
      openCourseForm(null, function () { reload(host); });
    });

    window.EduXt = { onSearch: function (keyword) { state.keyword = keyword.toLowerCase(); paint(); } };

    function reload(target) { load(target); }

    function load(target) {
      var body = target.querySelector('#xtListBody');
      if (body) {
        body.innerHTML = '<div class="xt-loading"><div class="xt-skeleton"></div><div class="xt-skeleton"></div></div>';
      }
      Api.list(PATH.course + '/list', { pageNum: 1, pageSize: 100 }).then(function (result) {
        state.rows = result.rows || [];
        paint();
      }).catch(function (error) {
        var box = target.querySelector('#xtListBody');
        if (box) showError(box, error, function () { load(target); });
      });
    }

    function paint() {
      var body = host.querySelector('#xtListBody');
      if (!body) return;
      var keyword = state.keyword;
      var rows = state.rows.filter(function (row) {
        if (!keyword) return true;
        return String(row.name + ' ' + row.semesterName + ' ' + row.className + ' ' + row.officialCourseName)
          .toLowerCase().indexOf(keyword) !== -1;
      });

      if (!state.rows.length) {
        body.innerHTML = '<div class="xt-panel" style="margin-top:0"><div class="xt-empty"><b>你还没有创建课程</b>'
          + '建好课程后绑定到教学班，学生才能看到。</div></div>';
        return;
      }
      if (!rows.length) {
        body.innerHTML = '<div class="xt-panel" style="margin-top:0"><div class="xt-empty"><b>没有匹配的课程</b>换个关键字试试。</div></div>';
        return;
      }

      body.innerHTML = '<div class="xt-course-grid">' + rows.map(function (row) {
        var status = STATUS[norm(row.status)] || row.status || '未知';
        return '<a class="xt-course-card" href="' + location.pathname + '?courseId=' + encodeURIComponent(row.id) + '">'
          + '<div class="xt-cover" style="' + cover(row.id) + '">'
          + '  <span class="xt-cover-badge">' + esc(status) + '</span>'
          + '  <span class="xt-cover-title">' + esc(row.name) + '</span>'
          + '</div>'
          + '<div class="xt-course-body">'
          + '  <div class="xt-course-name">' + esc(row.name) + '</div>'
          + '  <div class="xt-course-meta">' + esc(row.semesterName || '未指定学期') + ' · '
          + esc(row.classCount || 0) + ' 个班 · ' + esc(row.studentCount || 0) + ' 名学生</div>'
          + '  <div class="xt-course-foot"><span>' + esc(row.officialCourseName || '未关联正式课程')
          + '</span><span>更新 ' + esc(fmtDate(row.updatedAt)) + '</span></div>'
          + '</div></a>';
      }).join('') + '</div>';
    }

    load(host);
  }

  /* ==========================================================================
     二、课程空间
     ========================================================================== */

  function courseDetailPage(host, courseId) {
    host.classList.add('edu-mounted');
    host.innerHTML = '<div class="xt-loading"><div class="xt-skeleton" style="height:160px"></div></div>';
    window.EduXt = { onSearch: function () {} };

    Api.get(PATH.course + '/' + courseId).then(function (course) {
      if (!course) {
        host.innerHTML = '<div class="xt-panel"><div class="xt-empty"><b>课程不存在</b>可能已被删除，或链接里的编号不对。</div></div>';
        return;
      }
      render(host, course);
    }).catch(function (error) {
      showError(host, error, function () { courseDetailPage(host, courseId); });
    });
  }

  function render(host, course) {
    var status = STATUS[norm(course.status)] || course.status || '未知';
    host.innerHTML = '<section class="xt-detail">'
      + '<div class="xt-hero">'
      + '  <div class="xt-hero-main">'
      + '    <span class="xt-hero-tag">' + esc(status) + '</span>'
      + '    <h1>' + esc(course.name) + '</h1>'
      + '    <div class="xt-hero-meta">'
      + '      <span>' + esc(course.teacherName || '未指定教师') + '</span>'
      + '      <span>' + esc(course.semesterName || '未指定学期') + '</span>'
      + '      <span>' + esc(course.classCount || 0) + ' 个教学班</span>'
      + '      <span>' + esc(course.studentCount || 0) + ' 名学生</span>'
      + '    </div>'
      + '  </div>'
      + '  <div class="xt-hero-actions">'
      + '    <button class="xt-btn xt-btn-ghost" data-act="edit">编辑课程</button>'
      + '    <button class="xt-btn xt-btn-ghost" data-act="toggle">'
      + (norm(course.status) === 'ACTIVE' ? '撤回发布' : '发布课程') + '</button>'
      + '    <button class="xt-btn xt-btn-primary" data-act="bind">绑定教学班</button>'
      + '  </div>'
      + '</div>'
      + '<div class="xt-tabs" id="xtTabs">'
      + '  <button class="active" data-tab="outline">目录</button>'
      + '  <button data-tab="material">资料</button>'
      + '  <button data-tab="homework">作业</button>'
      + '  <button data-tab="discuss">讨论</button>'
      + '  <button data-tab="stat">统计</button>'
      + '</div>'
      + '<div id="xtTabBody"></div>'
      + '</section>';

    var body = host.querySelector('#xtTabBody');
    var current = 'outline';

    function show(tab) {
      current = tab;
      Array.prototype.forEach.call(host.querySelectorAll('#xtTabs button'), function (btn) {
        btn.classList.toggle('active', btn.getAttribute('data-tab') === tab);
      });
      if (tab === 'outline') renderOutline(body, course, host);
      else body.innerHTML = tabPlaceholder(tab);
    }

    Array.prototype.forEach.call(host.querySelectorAll('#xtTabs button'), function (btn) {
      btn.addEventListener('click', function () { show(btn.getAttribute('data-tab')); });
    });

    host.querySelector('[data-act="edit"]').addEventListener('click', function () {
      openCourseForm(course, function () { courseDetailPage(host, course.id); });
    });

    host.querySelector('[data-act="toggle"]').addEventListener('click', function () {
      var publish = norm(course.status) !== 'ACTIVE';
      U.confirm({
        title: publish ? '发布课程' : '撤回发布',
        message: publish
          ? '发布后，绑定教学班的学生就能看到这门课。'
          : '撤回后学生端立刻看不到这门课，已提交的作业和成绩不受影响。',
        danger: !publish,
        okText: publish ? '发布' : '撤回'
      }).then(function (ok) {
        if (!ok) return;
        Api.put(PATH.course + '/' + course.id + '/status', { status: publish ? 'ACTIVE' : 'DRAFT' })
          .then(function () {
            toast(publish ? '已发布' : '已撤回', 'success');
            courseDetailPage(host, course.id);
          })
          .catch(function (error) { toast(error.message, 'error'); });
      });
    });

    host.querySelector('[data-act="bind"]').addEventListener('click', function () {
      openBindClass(course, function () { courseDetailPage(host, course.id); });
    });

    show('outline');
  }

  function tabPlaceholder(tab) {
    var map = {
      material: ['课程资料', '课件、视频与资料版本由第 3 组（3-03）实现，接口尚未接入。'],
      homework: ['作业与考试', '发布作业、批改与成绩发布由第 5 组（5-03）实现，接口尚未接入。'],
      discuss: ['讨论与答疑', '课程讨论区由第 6 组实现，接口尚未接入。'],
      stat: ['学情统计', '学习进度与统计口径由第 5 组实现，接口尚未接入。']
    };
    var meta = map[tab] || ['待实现', '该模块尚未排期。'];
    return '<div class="xt-panel"><div class="xt-empty"><b>' + meta[0] + '</b>' + meta[1] + '</div></div>';
  }

  function renderOutline(body, course, host) {
    var units = course.units || [];
    var points = course.knowledgePoints || [];
    var classes = course.classes || [];

    var chapters = units.map(function (unit) {
      var sections = (unit.children || []).map(function (child) {
        return '<div class="xt-section">' + esc(child.title) + '</div>';
      }).join('');
      return '<div class="xt-chapter">'
        + '<div class="xt-chapter-title"><span class="dot"></span>' + esc(unit.title) + '</div>'
        + sections + '</div>';
    }).join('');

    var chips = points.map(function (point) {
      return '<span class="xt-chip">' + esc(point.code ? point.code + ' ' + point.name : point.name) + '</span>';
    }).join('');

    var rows = classes.map(function (item) {
      var className = esc(item.name || item.className);
      // 后端 classes[] 里 id 是绑定 ID、classId 是教学班 ID；演示数据只有 id（就是班级 ID）
      var classId = esc(item.classId || item.id);
      return '<tr>'
        + '<td>' + className + '</td>'
        + '<td>' + esc(item.termName || '—') + '</td>'
        + '<td><span class="xt-link" data-students="' + classId + '" data-class-name="' + className + '"'
        + ' title="查看学生名单">' + esc(item.studentCount || 0) + ' 人</span></td>'
        + '<td>' + esc(fmtDate(item.publishedAt)) + '</td>'
        + '<td class="xt-row-actions">'
        + '<span class="xt-link" data-students="' + classId + '" data-class-name="' + className + '">学生名单</span>'
        + '<span class="xt-link-danger" data-unbind="' + esc(item.id) + '">解绑</span>'
        + '</td>'
        + '</tr>';
    }).join('');

    body.innerHTML = '<div class="xt-panel">'
      + '<div class="xt-panel-title"><span>课程简介</span></div>'
      + '<div style="color:#646a73;line-height:1.8">' + esc(course.summary || '教师还没有填写课程简介。') + '</div>'
      + '<div style="margin-top:16px;color:#646a73;line-height:1.8"><b style="color:#1f2329">教学目标　</b>'
      + esc(course.learningObjectives || '教师还没有填写教学目标。') + '</div>'
      + '</div>'
      + '<div class="xt-panel">'
      + '<div class="xt-panel-title"><span>课程目录</span><span style="font-weight:400;color:#8f959e;font-size:12.5px">章节编辑由第 3 组（3-03）实现</span></div>'
      + (chapters || '<div class="xt-empty"><b>还没有章节</b>章节会出现在这里，学生按顺序学习。</div>')
      + (chips ? '<div style="margin-top:16px"><div class="xt-panel-title" style="margin-bottom:10px">知识点</div><div class="xt-chips">' + chips + '</div></div>' : '')
      + '</div>'
      + '<div class="xt-panel">'
      + '<div class="xt-panel-title"><span>已绑定教学班</span></div>'
      + (rows
        ? '<table class="xt-table"><thead><tr><th>教学班</th><th>学期</th><th>学生数</th><th>绑定时间</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table>'
        : '<div class="xt-empty"><b>还没有绑定教学班</b>没绑定班级时，学生端看不到这门课。</div>')
      + '</div>';

    Array.prototype.forEach.call(body.querySelectorAll('[data-students]'), function (node) {
      node.addEventListener('click', function () {
        openClassStudents(node.getAttribute('data-students'), node.getAttribute('data-class-name'));
      });
    });

    Array.prototype.forEach.call(body.querySelectorAll('[data-unbind]'), function (node) {
      node.addEventListener('click', function () {
        var bindingId = node.getAttribute('data-unbind');
        U.confirm({
          title: '解绑教学班',
          message: '解绑后该班学生看不到这门课，历史作业与成绩仍保留。',
          danger: true,
          okText: '解绑'
        }).then(function (ok) {
          if (!ok) return;
          Api.del(PATH.course + '/' + course.id + '/classes/' + bindingId)
            .then(function () {
              toast('已解绑', 'success');
              courseDetailPage(host, course.id);
            })
            .catch(function (error) { toast(error.message, 'error'); });
        });
      });
    });
  }

  /* ==========================================================================
     查看某个教学班的学生名单
     —— 课程详情里「3 人」点得进来，看得到具体是谁。
     教师只能看自己任教的班，服务端会再校验一次（/teaching-class/{id}/members）。
     ========================================================================== */
  function openClassStudents(classId, className) {
    var panel = U.panel({ title: (className || '教学班') + ' · 学生名单', width: 720 });
    var host = el('div');

    panel.body.appendChild(el('div', { class: 'edu-alert edu-alert-info' }, [
      el('span', { text: 'ⓘ' }),
      el('span', { text: '只显示当前在班学生；退班记录保留在「班级管理」里，便于追溯。' })
    ]));
    panel.body.appendChild(host);

    function render(rows) {
      var active = (rows || []).filter(function (member) {
        var status = norm(member.status) || (member.leftAt ? 'LEFT' : 'ACTIVE');
        return status === 'ACTIVE';
      });

      U.clear(host);
      if (!active.length) {
        host.appendChild(U.state('empty', {
          title: '这个班还没有学生',
          desc: '学生入班由管理员在「班级管理 → 成员」里维护，入班后这里会自动出现。'
        }));
        return;
      }

      var table = el('table', { class: 'edu-table' });
      table.appendChild(el('thead', {}, [el('tr', {}, [
        el('th', { text: '学号' }),
        el('th', { text: '姓名' }),
        el('th', { text: '入班时间' })
      ])]));
      var tbody = el('tbody');
      active.forEach(function (member) {
        tbody.appendChild(el('tr', {}, [
          el('td', { class: 'edu-mono', text: member.studentNo || member.userName || '—' }),
          el('td', { text: member.studentName || member.nickName || '—' }),
          el('td', { text: fmtDate(member.joinedAt) })
        ]));
      });
      table.appendChild(tbody);
      host.appendChild(el('div', { class: 'edu-table-wrap' }, [table]));
      host.appendChild(el('div', { class: 'edu-hint', text: '共 ' + active.length + ' 名在班学生' }));
    }

    host.appendChild(U.state('loading'));
    Api.list(PATH.teachingClass + '/' + classId + '/members', {})
      .then(function (result) { render(result.rows); })
      .catch(function (error) {
        U.clear(host);
        if (error.kind === 'forbidden') {
          host.appendChild(U.state('forbidden', { desc: '你不是这个班的任课教师，看不到学生名单。' }));
        } else {
          host.appendChild(U.state('error', {
            desc: error.message,
            actions: [{ text: '重试', primary: true, onClick: function () { openClassStudents(classId, className); } }]
          }));
        }
      });
  }

  /* ==========================================================================
     三、弹窗
     ========================================================================== */

  function openCourseForm(row, onDone) {
    var modalRef = U.modal({
      title: row ? '编辑课程' : '新建课程',
      submitText: row ? '保存' : '创建',
      width: 560,
      values: row ? {
        name: row.name,
        summary: row.summary,
        learningObjectives: row.learningObjectives,
        officialCourseId: row.officialCourseId
      } : {},
      fields: [
        { key: 'name', label: '课程名称', required: true, placeholder: '如 软件工程导论' },
        { key: 'officialCourseId', label: '对应正式课程', type: 'select', options: [], placeholder: '可暂不关联' },
        { key: 'summary', label: '课程简介', type: 'textarea', placeholder: '一两句话说明这门课讲什么' },
        { key: 'learningObjectives', label: '教学目标', type: 'textarea', placeholder: '学完能做到什么，一行一条' }
      ],
      onSubmit: function (values) {
        var payload = {
          name: (values.name || '').trim(),
          officialCourseId: values.officialCourseId || null,
          summary: (values.summary || '').trim(),
          learningObjectives: (values.learningObjectives || '').trim()
        };
        var request = row
          ? Api.put(PATH.course + '/' + row.id, payload)
          : Api.post(PATH.course, payload);
        return request.then(function () {
          toast(row ? '已保存' : '课程已创建', 'success');
          onDone && onDone();
        });
      }
    });

    Api.list(PATH.officialCourse + '/list', { pageSize: 200 }).then(function (result) {
      var select = document.querySelector('#edu-f-officialCourseId');
      if (!select) return;
      (result.rows || []).forEach(function (item) {
        var option = document.createElement('option');
        option.value = item.id;
        option.textContent = item.code ? item.code + ' ' + item.name : item.name;
        select.appendChild(option);
      });
    }).catch(function () { /* 下拉失败不影响建课 */ });

    return modalRef;
  }

  function openBindClass(course, onDone) {
    var bound = {};
    (course.classes || []).forEach(function (item) { bound[String(item.classId)] = true; });

    var modalRef = U.modal({
      title: '绑定教学班',
      submitText: '绑定',
      width: 560,
      fields: [
        { key: 'classId', label: '教学班', type: 'select', required: true, options: [], placeholder: '请选择你任教的班级' }
      ],
      onSubmit: function (values) {
        return Api.post(PATH.course + '/' + course.id + '/classes', { classId: values.classId })
          .then(function () {
            toast('已绑定', 'success');
            onDone && onDone();
          });
      }
    });

    Api.list(PATH.teachingClass + '/mine', { pageSize: 200 }).then(function (result) {
      var select = document.querySelector('#edu-f-classId');
      if (!select) return;
      var options = (result.rows || []).filter(function (item) { return !bound[String(item.id)]; });
      if (!options.length) {
        var empty = document.createElement('option');
        empty.value = '';
        empty.textContent = '暂无可绑定的教学班';
        select.appendChild(empty);
        return;
      }
      options.forEach(function (item) {
        var option = document.createElement('option');
        option.value = item.id;
        option.textContent = item.name;
        select.appendChild(option);
      });
    }).catch(function () { /* 下拉为空时由必填校验拦住 */ });

    return modalRef;
  }

  /* ==========================================================================
     四、错误状态
     ========================================================================== */

  function showError(container, error, retry) {
    var title = error.kind === 'forbidden' ? '无权限' : '加载失败';
    var desc = error.kind === 'forbidden'
      ? '当前账号没有访问范围，请联系管理员确认授权。'
      : (error.message || '请稍后重试。');
    container.innerHTML = '<div class="xt-panel"><div class="xt-state"><b>' + title + '</b>' + esc(desc)
      + '<div><button class="xt-btn xt-btn-primary" type="button">重试</button></div></div></div>';
    var btn = container.querySelector('button');
    if (btn) btn.addEventListener('click', retry);
  }

  /* ==========================================================================
     注册
     ========================================================================== */

  window.EduPagesTeacher = {
    '课程详情': function (host) {
      var courseId = query('courseId');
      if (courseId) courseDetailPage(host, courseId);
      else courseListPage(host);
    }
  };
})(window);
