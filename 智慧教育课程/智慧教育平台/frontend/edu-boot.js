/* ==========================================================================
   edu-boot.js —— 把业务页面挂到三端公共外壳里（第 2 组 2B）
   --------------------------------------------------------------------------
   为什么需要这个文件？
   admin.html / teacher.html / student.html 是第 1 组维护的公共外壳（顶部栏、侧边菜单、
   页签）。外壳本身不带业务，内容区原来只有一行「内容区（待实现）」。
   本文件做三件事，让外壳和业务页面解耦：
     1. 等登录核验通过（role-home.js 跑完）再渲染，避免带着空令牌去请求接口；
     2. 按「当前角色 + 当前菜单标题」找到对应页面函数执行；
     3. 找不到页面时，说明这一页归哪个组，避免点进去一片白。
   ========================================================================== */
(function (window, document) {
  'use strict';

  var U = window.EduUI;
  var el = U.el;

  /* 菜单归属表：不属于第 2 组的页面，点进来时告诉用户归谁，
     避免误以为是「坏页面」。任务编号来自分工文档第 3 节。 */
  var OWNER = {
    admin: {
      '管理总览': '第 6 组（6-03）', '待办事项': '第 6 组（6-05）', '运行概况': '第 6 组',
      '留言互动': '第 6 组（6-05，M1）',
      '评价指标管理': '第 5 组（M1）', '证书配置': '待确认，暂不排期',
      '教学过程分析': '第 5 组', '教学问题研判': '第 5 组', '管理员看板': '第 6 组',
      '教师工作台': '第 6 组', '实训课程': '待立项', '实训任务': '待立项',
      '校园资讯': '第 6 组（6-06，M1）', '学习首页': '第 6 组', '我的课程': '第 6 组',
      '任务中心': '第 5 组', '能力分析': '第 5 组', '单项能力评测': '待确认',
      '个人中心': '第 1 组', '我的留言': '第 6 组（6-05，M1）',
      'AI 对话': '项目负责人', '控制台': '项目负责人',
      '用户管理': '第 1 组（1-04）', '角色管理': '第 1 组', '菜单管理': '第 1 组',
      '字典管理': '第 1 组（M1）', '通知中心': '第 6 组', '审计日志': '第 1 组',
      '第三方登录': '第 1 组（M1）',
      '配置管理': '第 1 组（M1）', '文件管理': '第 1 组（M1）',
      '监控中心': '第 1 组（M1）', '定时任务': '第 1 组（M1）'
    },
    teacher: {
      '教学总览': '第 6 组（6-03）', '今日课程 / 快捷上课': '第 4 组（4-03）',
      '教学待办': '第 5 组（5-03）', '班级消息 / 公告': '第 6 组（6-04）',
      '课程建设': '第 3 组（3-03）', '课前准备': '第 3 组（3-03）',
      '课堂教学': '第 4 组（4-03）', '课后作业与反馈': '第 5 组（5-03）',
      '学情分析与教学改进': '第 5 组', 'AI 教学助手': '项目负责人'
    },
    student: {
      '学习总览 / 今日待办': '第 6 组（6-01）', '正在上课 / 一键加入': '第 4 组（4-04）',
      '作业与预习截止提醒': '第 5 组（5-04）', '学习通知 / 课程公告': '第 6 组（6-04）',
      '课程资料': '第 3 组（3-04）', '课前学习': '第 3 组（3-04）',
      '同步课堂': '第 4 组（4-04）', '课后学习': '第 5 组（5-04）',
      '我的学情': '第 5 组', 'AI 学习助手': '项目负责人'
    }
  };

  var PAGES = {
    admin: window.EduPagesAdmin || {},
    teacher: window.EduPagesTeacher || {},
    student: window.EduPagesStudent || {}
  };

  function mount() {
    var role = document.body.dataset.role || '';
    var host = document.querySelector('.main .content') || document.querySelector('.content');
    if (!host) return;

    var titleEl = document.getElementById('pageTitle');
    var title = titleEl ? titleEl.textContent.trim() : '';
    // 业务页面渲染会整体替换 .content，标题元素可能已被覆盖；
    // 用首次挂载时缓存的值兜底，避免二次挂载（切换左侧导航）时找不到页面。
    if (title) {
      window.__eduPageTitle = title;
    } else {
      title = window.__eduPageTitle || '';
    }
    var registry = PAGES[role] || {};

    if (registry[title]) {
      registry[title](host);
      return;
    }

    // 不是第 2 组的页面：给出归属说明，不留白屏
    var owner = (OWNER[role] || {})[title];
    U.clear(host);
    var page = el('div', { class: 'edu-page' });
    var head = el('div', { class: 'edu-head' });
    var headLeft = el('div');
    headLeft.appendChild(el('div', { class: 'page-title', text: title || '未命名页面' }));
    headLeft.appendChild(el('div', {
      class: 'edu-head-sub',
      text: owner ? '本页由 ' + owner + ' 负责实现' : '本页暂未实现'
    }));
    head.appendChild(headLeft);
    page.appendChild(head);
    page.appendChild(U.state('unavailable', {
      title: owner ? '这一页归 ' + owner : '这一页还没有实现',
      desc: owner
        ? '第 2 组（2B）负责的是：管理员端教务组织的 5 个页面、教师端「我的课程」、学生端「在修课程」。'
        : '完整分工见 docs/team-division-plan.md。',
      actions: [{
        text: '回到我负责的页面',
        primary: true,
        onClick: function () { location.href = role === 'admin' ? '?g=1&i=1' : '?g=1&i=0'; }
      }]
    }));
    host.appendChild(page);
    host.classList.add('edu-mounted');
  }

  /* 等外壳的登录核验结束再挂载：
     role-home.js 核验期间会给 body 加 auth-pending 类，核验完移除。
     用 MutationObserver 监听这个类，比 setTimeout 猜时间可靠。 */
  function waitForAuth(callback) {
    if (!document.body.classList.contains('auth-pending')) { callback(); return; }
    var done = false;
    var observer = new MutationObserver(function () {
      if (!document.body.classList.contains('auth-pending') && !done) {
        done = true;
        observer.disconnect();
        callback();
      }
    });
    observer.observe(document.body, { attributes: true, attributeFilter: ['class'] });
    // 兜底：6 秒还没变化（比如核验接口很慢），先渲染页面；接口自身的 401 会再跳登录
    setTimeout(function () {
      if (done) return;
      done = true;
      observer.disconnect();
      if (document.body.classList.contains('auth-pending')) callback();
    }, 6000);
  }

  waitForAuth(mount);
  window.EduBoot = { mount: mount };
})(window, document);
