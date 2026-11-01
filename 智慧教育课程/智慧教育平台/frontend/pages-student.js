/* ==========================================================================
   pages-student.js —— 学生端课程中心（学习通风格）
   --------------------------------------------------------------------------
   首页 = 在修课程卡片墙；点卡片进课程：目录 / 资料 / 作业 / 讨论（只读）。
   只显示「已发布 + 所在教学班已绑定」的课程，退班后自然看不到。
   ========================================================================== */
(function (window) {
  'use strict';

  var U = window.EduUI;
  var Api = window.EduApi;
  var fmtDate = U.fmtDate;

  var PATH = { course: '/course' };

  var COVERS = [
    ['#5b8ff9', '#2b7de9'],
    ['#31c48d', '#0e9f6e'],
    ['#f6ad55', '#ed8936'],
    ['#9f7aea', '#6b46c1'],
    ['#f687b3', '#d53f8c']
  ];

  var STATUS = { DRAFT: '未发布', ACTIVE: '在修', ARCHIVED: '已结束', DISABLED: '已停用' };

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

  /* ==========================================================================
     一、在修课程
     ========================================================================== */

  function myCoursesPage(host) {
    state.keyword = '';
    host.classList.add('edu-mounted');
    host.innerHTML = '<section class="xt-page">'
      + '<div class="xt-page-head">'
      + '  <div><h2>我的课程</h2><p>只显示你当前有效在班的课程，退班或被撤权后就看不到。</p></div>'
      + '</div>'
      + '<div id="xtListBody"><div class="xt-loading">'
      + '  <div class="xt-skeleton"></div><div class="xt-skeleton"></div><div class="xt-skeleton"></div>'
      + '</div></div>'
      + '</section>';

    window.EduXt = { onSearch: function (keyword) { state.keyword = keyword.toLowerCase(); paint(host); } };

    load(host);
  }

  function load(host) {
    var body = host.querySelector('#xtListBody');
    if (body) body.innerHTML = '<div class="xt-loading"><div class="xt-skeleton"></div><div class="xt-skeleton"></div></div>';

    Api.list(PATH.course + '/list', { pageNum: 1, pageSize: 100 }).then(function (result) {
      state.rows = result.rows || [];
      paint(host);
    }).catch(function (error) {
      showError(host.querySelector('#xtListBody'), error, function () { load(host); });
    });
  }

  function paint(host) {
    var body = host.querySelector('#xtListBody');
    if (!body) return;
    var keyword = state.keyword;
    var rows = state.rows.filter(function (row) {
      if (!keyword) return true;
      return String(row.name + ' ' + row.teacherName + ' ' + row.semesterName + ' ' + row.className)
        .toLowerCase().indexOf(keyword) !== -1;
    });

    if (!state.rows.length) {
      body.innerHTML = '<div class="xt-panel" style="margin-top:0"><div class="xt-empty"><b>你还没有在修课程</b>'
        + '课程由教师绑定到教学班后才会出现，确认你已加入对应班级。</div></div>';
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
        + '  <div class="xt-course-meta">' + esc(row.teacherName || '未指定教师') + ' · '
        + esc(row.semesterName || '未指定学期') + '</div>'
        + '  <div class="xt-course-foot"><span>' + esc(row.className || '未分班')
        + '</span><span>' + esc(fmtDate(row.updatedAt)) + '</span></div>'
        + '</div></a>';
    }).join('') + '</div>';
  }

  /* ==========================================================================
     二、课程详情（只读）
     ========================================================================== */

  function courseDetailPage(host, courseId) {
    host.classList.add('edu-mounted');
    host.innerHTML = '<div class="xt-loading"><div class="xt-skeleton" style="height:160px"></div></div>';
    window.EduXt = { onSearch: function () {} };

    Api.get(PATH.course + '/' + courseId).then(function (course) {
      if (!course) {
        host.innerHTML = '<div class="xt-panel"><div class="xt-empty"><b>课程不存在</b>可能已下线，或链接里的课程编号不对。</div></div>';
        return;
      }
      render(host, course);
    }).catch(function (error) {
      showError(host, error, function () { courseDetailPage(host, courseId); });
    });
  }

  function render(host, course) {
    var status = STATUS[norm(course.status)] || course.status || '未知';
    var draftWarning = norm(course.status) !== 'ACTIVE'
      ? '<div class="xt-panel" style="border-left:3px solid #ff8a00"><div style="color:#ad6800">'
        + '这门课当前为「' + esc(status) + '」状态，可能无法继续学习或提交作业。</div></div>'
      : '';

    host.innerHTML = '<section class="xt-detail">'
      + '<div class="xt-hero">'
      + '  <div class="xt-hero-main">'
      + '    <span class="xt-hero-tag">' + esc(status) + '</span>'
      + '    <h1>' + esc(course.name) + '</h1>'
      + '    <div class="xt-hero-meta">'
      + '      <span>' + esc(course.teacherName || '未指定教师') + '</span>'
      + '      <span>' + esc(course.semesterName || '未指定学期') + '</span>'
      + '      <span>' + esc(course.className || '未分班') + '</span>'
      + '    </div>'
      + '  </div>'
      + '</div>'
      + '<div class="xt-tabs" id="xtTabs">'
      + '  <button class="active" data-tab="outline">目录</button>'
      + '  <button data-tab="material">资料</button>'
      + '  <button data-tab="homework">作业</button>'
      + '  <button data-tab="discuss">讨论</button>'
      + '</div>'
      + '<div id="xtTabBody"></div>'
      + '</section>';

    var body = host.querySelector('#xtTabBody');

    function show(tab) {
      Array.prototype.forEach.call(host.querySelectorAll('#xtTabs button'), function (btn) {
        btn.classList.toggle('active', btn.getAttribute('data-tab') === tab);
      });
      if (tab === 'outline') renderOutline(body, course, draftWarning);
      else body.innerHTML = tabPlaceholder(tab);
    }

    Array.prototype.forEach.call(host.querySelectorAll('#xtTabs button'), function (btn) {
      btn.addEventListener('click', function () { show(btn.getAttribute('data-tab')); });
    });

    show('outline');
  }

  function tabPlaceholder(tab) {
    var map = {
      material: ['课程资料', '课件、视频和资料由第 3 组（3-04）发布，接口尚未接入。'],
      homework: ['作业与预习', '作业提交、批改反馈由第 5 组（5-04）实现，接口尚未接入。'],
      discuss: ['讨论与答疑', '课程讨论区由第 6 组实现，接口尚未接入。']
    };
    var meta = map[tab] || ['待实现', '该模块尚未排期。'];
    return '<div class="xt-panel"><div class="xt-empty"><b>' + meta[0] + '</b>' + meta[1] + '</div></div>';
  }

  function renderOutline(body, course, draftWarning) {
    var units = course.units || [];
    var points = course.knowledgePoints || [];

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

    body.innerHTML = draftWarning
      + '<div class="xt-panel">'
      + '<div class="xt-panel-title"><span>课程简介</span></div>'
      + '<div style="color:#646a73;line-height:1.8">' + esc(course.summary || '老师还没有填写课程简介。') + '</div>'
      + '<div style="margin-top:16px;color:#646a73;line-height:1.8"><b style="color:#1f2329">教学目标　</b>'
      + esc(course.learningObjectives || '老师还没有填写教学目标。') + '</div>'
      + '</div>'
      + '<div class="xt-panel">'
      + '<div class="xt-panel-title"><span>课程目录</span></div>'
      + (chapters || '<div class="xt-empty"><b>还没有章节</b>老师发布章节后会出现在这里。</div>')
      + (chips ? '<div style="margin-top:16px"><div class="xt-panel-title" style="margin-bottom:10px">知识点</div><div class="xt-chips">' + chips + '</div></div>' : '')
      + '</div>';
  }

  /* ==========================================================================
     三、错误状态
     ========================================================================== */

  function showError(container, error, retry) {
    if (!container) return;
    var title = error.kind === 'forbidden' ? '无权限' : '加载失败';
    var desc = error.kind === 'forbidden'
      ? '你看不到这门课，通常是因为已退出该教学班或被撤销授权。'
      : (error.message || '请稍后重试。');
    container.innerHTML = '<div class="xt-panel"><div class="xt-state"><b>' + title + '</b>' + esc(desc)
      + '<div><button class="xt-btn xt-btn-primary" type="button">重试</button></div></div></div>';
    var btn = container.querySelector('button');
    if (btn) btn.addEventListener('click', retry);
  }

  window.EduPagesStudent = {
    '课程详情': function (host) {
      var courseId = query('courseId');
      if (courseId) courseDetailPage(host, courseId);
      else myCoursesPage(host);
    }
  };
})(window);
