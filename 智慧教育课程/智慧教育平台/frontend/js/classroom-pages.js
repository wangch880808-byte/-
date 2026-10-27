(() => {
  'use strict';

  const DEMO_SESSION_KEY = 'zhijiao.classroom.demoSession';

  const TEACHER_PAGES = {
    '今日课程 / 快捷上课': 'teacherQuickStart',
    '课堂教学': 'teacherConsole'
  };

  const STUDENT_PAGES = {
    '正在上课 / 一键加入': 'studentJoin',
    '同步课堂': 'studentSync'
  };

  function statusBadge(status) {
    const map = {
      PLANNED: ['未开始', 'classroom-badge--draft'],
      RUNNING: ['进行中', 'classroom-badge--running'],
      PAUSED: ['已暂停', 'classroom-badge--paused'],
      ENDED: ['已结束', 'classroom-badge--ended']
    };
    const item = map[status] || ['未知', 'classroom-badge--draft'];
    return `<span class="classroom-badge ${item[1]}">${item[0]}</span>`;
  }

  function getDemoSession() {
    try {
      const raw = sessionStorage.getItem(DEMO_SESSION_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  function saveDemoSession(session) {
    sessionStorage.setItem(DEMO_SESSION_KEY, JSON.stringify(session));
  }

  function defaultDemoSession() {
    return {
      sessionId: 'demo-5001',
      bindingId: 1001,
      className: '计算机科学 2024-1 班',
      courseName: '数据结构',
      status: 'PLANNED',
      pageNo: 1,
      totalPages: 12,
      checkedInCount: 0,
      totalStudents: 32
    };
  }

  function ensureDemoSession() {
    let session = getDemoSession();
    if (!session) {
      session = defaultDemoSession();
      saveDemoSession(session);
    }
    return session;
  }

  function renderInto(pageName, html, mobile) {
    const content = document.querySelector('.content');
    const pageTitle = document.getElementById('pageTitle');
    const pageBody = document.getElementById('pageBody');
    const defaultHint = document.getElementById('defaultHint');
    if (!content || !pageBody) return false;

    content.classList.add('classroom-active');
    if (mobile) content.classList.add('classroom-mobile');
    else content.classList.remove('classroom-mobile');

    if (pageTitle) pageTitle.textContent = pageName;
    if (defaultHint) defaultHint.hidden = true;
    pageBody.hidden = false;
    pageBody.innerHTML = html;
    bindPageEvents(pageName);
    return true;
  }

  function resetContent() {
    const content = document.querySelector('.content');
    const pageBody = document.getElementById('pageBody');
    const defaultHint = document.getElementById('defaultHint');
    const pageTitle = document.getElementById('pageTitle');
    if (content) {
      content.classList.remove('classroom-active', 'classroom-mobile');
    }
    if (pageBody) {
      pageBody.hidden = true;
      pageBody.innerHTML = '';
    }
    if (defaultHint) defaultHint.hidden = false;
    if (pageTitle && window.MENU && window.currentGi !== undefined) {
      pageTitle.textContent = window.MENU[window.currentGi].children[window.currentIi];
    }
  }

  function teacherQuickStartHtml(session) {
    return `
      <div class="classroom-panel">
        <h2>今日课程 / 快捷上课 ${statusBadge(session.status)}</h2>
        <p class="sub">${session.courseName} · ${session.className}</p>
        <div class="classroom-card">
          <h3>快捷操作</h3>
          <p style="font-size:13px;color:#666;margin-bottom:12px;">选择教学班与课件后一键开课。当前为占位页，按钮仅演示本地状态。</p>
          <div class="classroom-actions">
            <button type="button" class="classroom-btn classroom-btn--primary" data-action="start-session">开始上课</button>
            <button type="button" class="classroom-btn" data-action="open-console">进入课堂教学控制台</button>
            <button type="button" class="classroom-btn" data-action="open-projection" ${session.status === 'PLANNED' ? 'disabled' : ''}>打开放射页</button>
          </div>
        </div>
        <div class="classroom-notice">接口尚未实现。后端就绪后将调用 <code>POST /education/classroom/sessions</code>。</div>
      </div>`;
  }

  function teacherConsoleHtml(session) {
    return `
      <div class="classroom-panel">
        <h2>课堂教学控制台 ${statusBadge(session.status)}</h2>
        <p class="sub">会话 ${session.sessionId} · ${session.courseName} · ${session.className}</p>
        <div class="classroom-grid classroom-grid--2">
          <div class="classroom-card">
            <h3>课件演示</h3>
            <div class="classroom-slide-preview">
              <span class="page-no">第 ${session.pageNo} 页</span>
              <span>课件预览占位（共 ${session.totalPages} 页）</span>
            </div>
            <div class="classroom-actions">
              <button type="button" class="classroom-btn" data-action="prev-page" ${session.pageNo <= 1 ? 'disabled' : ''}>上一页</button>
              <button type="button" class="classroom-btn" data-action="next-page" ${session.pageNo >= session.totalPages ? 'disabled' : ''}>下一页</button>
              <button type="button" class="classroom-btn" data-action="open-projection" ${session.status === 'PLANNED' ? 'disabled' : ''}>投影</button>
            </div>
          </div>
          <div>
            <div class="classroom-card" style="margin-bottom:12px;">
              <h3>课堂控制</h3>
              <div class="classroom-actions">
                <button type="button" class="classroom-btn classroom-btn--primary" data-action="start-session" ${session.status !== 'PLANNED' ? 'disabled' : ''}>开始</button>
                <button type="button" class="classroom-btn" data-action="pause-session" ${session.status !== 'RUNNING' ? 'disabled' : ''}>暂停</button>
                <button type="button" class="classroom-btn" data-action="resume-session" ${session.status !== 'PAUSED' ? 'disabled' : ''}>继续</button>
                <button type="button" class="classroom-btn classroom-btn--danger" data-action="end-session" ${session.status === 'PLANNED' || session.status === 'ENDED' ? 'disabled' : ''}>结束</button>
              </div>
            </div>
            <div class="classroom-card" style="margin-bottom:12px;">
              <h3>课堂活动</h3>
              <div class="classroom-actions">
                <button type="button" class="classroom-btn" data-action="start-checkin" ${session.status !== 'RUNNING' ? 'disabled' : ''}>发起签到</button>
                <button type="button" class="classroom-btn" data-action="start-quiz" ${session.status !== 'RUNNING' ? 'disabled' : ''}>发随堂题</button>
              </div>
              <div class="classroom-stat" style="margin-top:12px;"><span>已签到</span><span>${session.checkedInCount} / ${session.totalStudents}</span></div>
            </div>
            <div class="classroom-card">
              <h3>教师私有区</h3>
              <p style="font-size:12px;color:#999;">备注、未公布答案、学生明细仅在此显示，不进入投影。</p>
            </div>
          </div>
        </div>
        <div class="classroom-notice classroom-notice--warn">占位演示：状态保存在浏览器 sessionStorage，刷新后仍保留。正式版以服务端快照为准。</div>
      </div>`;
  }

  function studentJoinHtml(session) {
    const canJoin = session.status === 'RUNNING' || session.status === 'PAUSED';
    return `
      <div class="classroom-panel classroom-join-hero">
        <div class="join-icon">${canJoin ? '📡' : '💤'}</div>
        <h2>正在上课 / 一键加入</h2>
        <p>${canJoin ? `${session.courseName} 正在进行中，点击加入同步课堂。` : '当前没有进行中的课堂，请稍后再试或从课程详情进入。'}</p>
        <div class="classroom-actions" style="justify-content:center;">
          <button type="button" class="classroom-btn classroom-btn--primary" data-action="join-class" ${canJoin ? '' : 'disabled'}>一键加入</button>
        </div>
        <div class="classroom-notice" style="text-align:left;margin-top:24px;">加入后将调用 <code>POST /education/classroom/sessions/{id}/join</code> 校验本班资格。</div>
      </div>`;
  }

  function studentSyncHtml(session) {
    const checkedIn = session.selfCheckedIn || false;
    return `
      <div class="classroom-panel">
        <h2>同步课堂 ${statusBadge(session.status)}</h2>
        <p class="sub">${session.courseName} · 第 ${session.pageNo} 页</p>
        <div class="classroom-card">
          <div class="classroom-slide-preview" style="min-height:160px;">
            <span class="page-no">第 ${session.pageNo} 页</span>
            <span>跟随教师翻页（占位）</span>
          </div>
        </div>
        <div class="classroom-card" style="margin-top:12px;">
          <h3>课堂签到</h3>
          <div class="classroom-actions">
            <button type="button" class="classroom-btn classroom-btn--primary" data-action="checkin" ${session.status !== 'RUNNING' || checkedIn ? 'disabled' : ''}>
              ${checkedIn ? '已签到' : '签到'}
            </button>
          </div>
        </div>
        <div class="classroom-card" style="margin-top:12px;">
          <h3>随堂练习（占位）</h3>
          <p style="font-size:13px;color:#666;margin-bottom:8px;">以下哪项是栈的特点？</p>
          <div class="classroom-quiz-options" data-quiz>
            <label class="classroom-quiz-option"><input type="radio" name="demoQuiz" value="A" hidden> A. 先进先出</label>
            <label class="classroom-quiz-option"><input type="radio" name="demoQuiz" value="B" hidden> B. 后进先出</label>
            <label class="classroom-quiz-option"><input type="radio" name="demoQuiz" value="C" hidden> C. 随机访问</label>
          </div>
          <div class="classroom-actions">
            <button type="button" class="classroom-btn classroom-btn--primary" data-action="submit-quiz" ${session.status !== 'RUNNING' ? 'disabled' : ''}>提交答案</button>
          </div>
        </div>
        <div class="classroom-notice">手机优先布局。断线后将通过快照接口恢复页码与本人提交状态。</div>
      </div>`;
  }

  function updateSession(patch) {
    const session = { ...ensureDemoSession(), ...patch };
    saveDemoSession(session);
    return session;
  }

  function bindPageEvents(pageName) {
    const pageBody = document.getElementById('pageBody');
    if (!pageBody) return;

    pageBody.querySelectorAll('[data-action]').forEach((btn) => {
      btn.addEventListener('click', () => handleAction(btn.getAttribute('data-action')));
    });

    pageBody.querySelectorAll('.classroom-quiz-option').forEach((opt) => {
      opt.addEventListener('click', () => {
        pageBody.querySelectorAll('.classroom-quiz-option').forEach((o) => o.classList.remove('selected'));
        opt.classList.add('selected');
        const input = opt.querySelector('input');
        if (input) input.checked = true;
      });
    });
  }

  function handleAction(action) {
    let session = ensureDemoSession();

    switch (action) {
      case 'start-session':
        session = updateSession({ status: 'RUNNING', pageNo: 1 });
        break;
      case 'pause-session':
        session = updateSession({ status: 'PAUSED' });
        break;
      case 'resume-session':
        session = updateSession({ status: 'RUNNING' });
        break;
      case 'end-session':
        session = updateSession({ status: 'ENDED' });
        break;
      case 'prev-page':
        session = updateSession({ pageNo: Math.max(1, session.pageNo - 1) });
        break;
      case 'next-page':
        session = updateSession({ pageNo: Math.min(session.totalPages, session.pageNo + 1) });
        break;
      case 'start-checkin':
        alert('占位：将调用 POST /education/activity/instances 发起签到');
        return;
      case 'start-quiz':
        alert('占位：将调用 POST /education/activity/instances 发起随堂题');
        return;
      case 'open-projection':
        window.open(`/projection.html?sessionId=${encodeURIComponent(session.sessionId)}`, '_blank', 'noopener');
        return;
      case 'open-console':
        if (document.body.dataset.role === 'teacher') {
          window.location.href = '/teacher.html?g=1&i=3';
        }
        return;
      case 'join-class':
        window.location.href = '/student.html?g=1&i=3';
        return;
      case 'checkin':
        session = updateSession({ selfCheckedIn: true, checkedInCount: session.checkedInCount + 1 });
        break;
      case 'submit-quiz':
        alert('占位：将调用 POST /education/activity/instances/{id}/responses');
        return;
      default:
        return;
    }

    const role = document.body.dataset.role;
    const pageName = window.MENU[window.currentGi].children[window.currentIi];
    render(role, pageName);
  }

  function render(role, pageName) {
    const teacherKey = TEACHER_PAGES[pageName];
    const studentKey = STUDENT_PAGES[pageName];

    if (role === 'teacher' && teacherKey) {
      const session = ensureDemoSession();
      const html = teacherKey === 'teacherQuickStart'
        ? teacherQuickStartHtml(session)
        : teacherConsoleHtml(session);
      return renderInto(pageName, html, false);
    }

    if (role === 'student' && studentKey) {
      const session = ensureDemoSession();
      const html = studentKey === 'studentJoin'
        ? studentJoinHtml(session)
        : studentSyncHtml(session);
      return renderInto(pageName, html, studentKey === 'studentSync');
    }

    resetContent();
    return false;
  }

  window.ClassroomPages = { render, resetContent, ensureDemoSession };
})();
