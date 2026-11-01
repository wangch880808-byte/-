/* ==========================================================================
   xt-shell.js —— 学习通式左侧导航外壳（教师端 / 学生端共用）
   --------------------------------------------------------------------------
   导航从顶部移到左侧后，这里负责三件事：
   1. 点导航切换选中态，并同步右侧顶部面包屑；
   2. 「首页 / 我的课程」在课程详情页点击时回到课程列表（不整页刷新）；
   3. 「消息」「我的」还没接业务，点开给一句明确说明，不留白屏也不放假数据。
   搜索框仍把关键字交给页面脚本自己过滤。
   ========================================================================== */
(function (window, document) {
  'use strict';

  var nav = document.getElementById('xtNav');
  var search = document.getElementById('xtSearch');
  var crumb = document.getElementById('xtCrumb');

  function content() {
    return document.querySelector('.main .content');
  }

  function labelOf(link) {
    var span = link.querySelector('span');
    return span ? span.textContent.trim() : link.textContent.trim();
  }

  function setActive(target) {
    if (!nav) return;
    Array.prototype.forEach.call(nav.querySelectorAll('a[data-nav]'), function (a) {
      a.classList.toggle('active', a === target);
    });
    if (crumb && target) {
      crumb.textContent = labelOf(target);
    }
  }

  function placeholder(title, desc) {
    var host = content();
    if (!host) return;
    host.innerHTML = '<section class="xt-panel" style="margin-top:0">'
      + '<div class="xt-empty"><b>' + title + '</b>' + desc + '</div>'
      + '</section>';
  }

  function remount() {
    if (window.EduBoot && typeof window.EduBoot.mount === 'function') {
      window.EduBoot.mount();
    }
  }

  function hasCourseParam() {
    return /[?&]courseId=/.test(window.location.search);
  }

  if (nav) {
    nav.addEventListener('click', function (event) {
      var link = event.target.closest ? event.target.closest('a[data-nav]') : null;
      if (!link) return;
      var key = link.getAttribute('data-nav');

      if (key === 'message') {
        event.preventDefault();
        setActive(link);
        placeholder('消息中心', '站内消息与课程公告由第 6 组（6-04）实现，接口尚未接入。');
        return;
      }

      if (key === 'me') {
        event.preventDefault();
        setActive(link);
        placeholder('个人中心', '账号资料、密码与角色由第 1 组维护，本页不提供编辑入口。');
        return;
      }

      /* 首页 / 我的课程：课程空间里点它们，回到课程列表，不整页刷新 */
      event.preventDefault();
      setActive(link);
      if (hasCourseParam()) {
        window.history.pushState({ nav: key }, '', window.location.pathname);
      }
      remount();
    });
  }

  /* 浏览器前进/后退：详情页 ↔ 列表页，导航选中态跟着回列表 */
  window.addEventListener('popstate', function () {
    var home = nav && nav.querySelector('a[data-nav="home"]');
    if (home) setActive(home);
    remount();
  });

  if (search) {
    search.addEventListener('input', function () {
      if (window.EduXt && typeof window.EduXt.onSearch === 'function') {
        window.EduXt.onSearch(search.value.trim());
      }
    });
  }

  /* 直接打开课程详情链接时，面包屑显示两级 */
  if (crumb && hasCourseParam()) {
    crumb.textContent = '我的课程 / 课程详情';
  }
})(window, document);
