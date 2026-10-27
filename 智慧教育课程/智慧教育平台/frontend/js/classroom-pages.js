(() => {
  'use strict';

  const CTX_KEY = 'zhijiao.classroom.ctx';
  const POLL_MS = 3000;

  const TEACHER_PAGES = {
    '今日课程 / 快捷上课': 'teacherQuickStart',
    '课堂教学': 'teacherConsole'
  };

  const STUDENT_PAGES = {
    '正在上课 / 一键加入': 'studentJoin',
    '同步课堂': 'studentSync'
  };

  let pollTimer = null;
  let pageState = {
    loading: true,
    error: '',
    binding: null,
    prep: null,
    snapshot: null,
    sessionId: null,
    totalPages: 2,
    joined: false
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

  function saveCtx(patch) {
    const prev = loadCtx();
    const next = { ...prev, ...patch };
    sessionStorage.setItem(CTX_KEY, JSON.stringify(next));
    return next;
  }

  function loadCtx() {
    try {
      const raw = sessionStorage.getItem(CTX_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch {
      return {};
    }
  }

  function isoNow() {
    return new Date().toISOString();
  }

  function isoPlusMinutes(minutes) {
    const d = new Date();
    d.setMinutes(d.getMinutes() + minutes);
    return d.toISOString();
  }

  function showError(message) {
    pageState.error = message || '';
  }

  function clearError() {
    pageState.error = '';
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }

  function startPolling(refreshFn) {
    stopPolling();
    pollTimer = setInterval(() => {
      refreshFn().catch(() => {});
    }, POLL_MS);
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
    stopPolling();
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

  function errorNotice() {
    return pageState.error
      ? `<div class="classroom-notice classroom-notice--warn">${pageState.error}</div>`
      : '';
  }

  function findOpenActivity(kind) {
    const list = pageState.snapshot?.openActivities || [];
    if (pageState.snapshot?.openActivity && !list.length) {
      return pageState.snapshot.openActivity.activityKind === kind
        ? pageState.snapshot.openActivity : null;
    }
    return list.find((a) => a.activityKind === kind && a.status === 'OPEN') || null;
  }

  function quizBlock() {
    const quiz = findOpenActivity('QUIZ');
    if (!quiz || !quiz.question) {
      return '<p style="font-size:13px;color:#999;">当前没有进行中的随堂题。</p>';
    }
    const options = Array.isArray(quiz.question.options) ? quiz.question.options : [];
    const disabled = pageState.snapshot?.status !== 'RUNNING';

    const optionsHtml = options.map((text, idx) => {
      const letter = String.fromCharCode(65 + idx);
      return `<label class="classroom-quiz-option" data-value="${letter}">
        <input type="radio" name="liveQuiz" value="${letter}" hidden> ${letter}. ${text}
      </label>`;
    }).join('');

    return `
      <h3>${quiz.title || '随堂练习'}</h3>
      <p style="font-size:13px;color:#666;margin-bottom:8px;">${quiz.question.stem || ''}</p>
      <div class="classroom-quiz-options" data-quiz>${optionsHtml}</div>
      <div class="classroom-actions">
        <button type="button" class="classroom-btn classroom-btn--primary" data-action="submit-quiz"
          data-activity-id="${quiz.activityId}" ${disabled ? 'disabled' : ''}>
          提交答案
        </button>
      </div>`;
  }

  function activityListHtml() {
    const list = pageState.snapshot?.openActivities || [];
    if (!list.length) {
      return '<p style="font-size:12px;color:#999;">暂无进行中的活动</p>';
    }
    return list.map((a) => `
      <div class="classroom-stat">
        <span>${a.title}（${a.activityKind}）</span>
        <span>${a.status}</span>
      </div>`).join('');
  }

  function teacherQuickStartHtml() {
    const binding = pageState.binding;
    const status = pageState.snapshot?.status || 'PLANNED';
    const sessionId = pageState.sessionId;
    return `
      <div class="classroom-panel">
        <h2>今日课程 / 快捷上课 ${statusBadge(status)}</h2>
        <p class="sub">${binding?.courseTitle || '—'} · ${binding?.className || '—'}</p>
        ${pageState.loading ? '<p class="sub">加载中…</p>' : ''}
        <div class="classroom-card">
          <h3>快捷操作</h3>
          <p style="font-size:13px;color:#666;margin-bottom:12px;">
            选择教学班与课件后一键开课。已接后端 <code>/education/classroom/sessions</code>。
          </p>
          <div class="classroom-actions">
            <button type="button" class="classroom-btn classroom-btn--primary" data-action="start-session"
              ${status !== 'PLANNED' || pageState.loading ? 'disabled' : ''}>开始上课</button>
            <button type="button" class="classroom-btn" data-action="open-console"
              ${!sessionId ? 'disabled' : ''}>进入课堂教学控制台</button>
            <button type="button" class="classroom-btn" data-action="open-projection"
              ${!sessionId || status === 'PLANNED' ? 'disabled' : ''}>打开放射页</button>
          </div>
        </div>
        ${errorNotice()}
        <div class="classroom-notice">课件与班级信息来自第 2/3 组占位接口，后期可替换为正式 API。</div>
      </div>`;
  }

  function teacherConsoleHtml() {
    const binding = pageState.binding;
    const snap = pageState.snapshot || {};
    const status = snap.status || 'PLANNED';
    const pageNo = snap.pageNo || 1;
    const total = pageState.totalPages || 2;
    const sessionId = pageState.sessionId;
    const attendance = findOpenActivity('ATTENDANCE');

    return `
      <div class="classroom-panel">
        <h2>课堂教学控制台 ${statusBadge(status)}</h2>
        <p class="sub">会话 ${sessionId || '—'} · ${binding?.courseTitle || ''} · ${binding?.className || ''}</p>
        <div class="classroom-grid classroom-grid--2">
          <div class="classroom-card">
            <h3>课件演示</h3>
            <div class="classroom-slide-preview">
              <span class="page-no">第 ${pageNo} 页</span>
              <span>${pageState.prep?.resourceTitle || '课件'}（共 ${total} 页）</span>
            </div>
            <div class="classroom-actions">
              <button type="button" class="classroom-btn" data-action="prev-page"
                ${pageNo <= 1 || status !== 'RUNNING' ? 'disabled' : ''}>上一页</button>
              <button type="button" class="classroom-btn" data-action="next-page"
                ${pageNo >= total || status !== 'RUNNING' ? 'disabled' : ''}>下一页</button>
              <button type="button" class="classroom-btn" data-action="open-projection"
                ${!sessionId || status === 'PLANNED' ? 'disabled' : ''}>投影</button>
            </div>
          </div>
          <div>
            <div class="classroom-card" style="margin-bottom:12px;">
              <h3>课堂控制</h3>
              <div class="classroom-actions">
                <button type="button" class="classroom-btn classroom-btn--primary" data-action="start-session"
                  ${status !== 'PLANNED' || pageState.loading ? 'disabled' : ''}>开始</button>
                <button type="button" class="classroom-btn" data-action="pause-session"
                  ${status !== 'RUNNING' ? 'disabled' : ''}>暂停</button>
                <button type="button" class="classroom-btn" data-action="resume-session"
                  ${status !== 'PAUSED' ? 'disabled' : ''}>继续</button>
                <button type="button" class="classroom-btn classroom-btn--danger" data-action="end-session"
                  ${status === 'PLANNED' || status === 'ENDED' ? 'disabled' : ''}>结束</button>
              </div>
            </div>
            <div class="classroom-card" style="margin-bottom:12px;">
              <h3>课堂活动</h3>
              <div class="classroom-actions">
                <button type="button" class="classroom-btn" data-action="start-checkin"
                  ${status !== 'RUNNING' ? 'disabled' : ''}>发起签到</button>
                <button type="button" class="classroom-btn" data-action="start-quiz"
                  ${status !== 'RUNNING' ? 'disabled' : ''}>发随堂题</button>
                <button type="button" class="classroom-btn" data-action="publish-quiz"
                  ${!findOpenActivity('QUIZ') ? 'disabled' : ''}>公布答案</button>
              </div>
              <div style="margin-top:12px;">${activityListHtml()}</div>
              ${attendance ? '<div class="classroom-stat"><span>签到活动</span><span>进行中</span></div>' : ''}
            </div>
            <div class="classroom-card">
              <h3>教师私有区</h3>
              <p style="font-size:12px;color:#999;">备注、未公布答案、学生明细仅在此显示，不进入投影。</p>
            </div>
          </div>
        </div>
        ${errorNotice()}
        <div class="classroom-notice">每 ${POLL_MS / 1000} 秒自动同步服务端快照。</div>
      </div>`;
  }

  function studentJoinHtml() {
    const binding = pageState.binding;
    const status = pageState.snapshot?.status;
    const canJoin = status === 'RUNNING' || status === 'PAUSED';
    return `
      <div class="classroom-panel classroom-join-hero">
        <div class="join-icon">${canJoin ? '📡' : '💤'}</div>
        <h2>正在上课 / 一键加入</h2>
        <p>${canJoin
          ? `${binding?.courseTitle || '课程'} 正在进行中，点击加入同步课堂。`
          : '当前没有进行中的课堂，请稍后再试或从课程详情进入。'}</p>
        <div class="classroom-actions" style="justify-content:center;">
          <button type="button" class="classroom-btn classroom-btn--primary" data-action="join-class"
            ${canJoin ? '' : 'disabled'}>一键加入</button>
        </div>
        ${errorNotice()}
        <div class="classroom-notice" style="text-align:left;margin-top:24px;">
          加入时调用 <code>POST /education/classroom/sessions/{id}/join</code> 校验本班资格。
        </div>
      </div>`;
  }

  function studentSyncHtml() {
    const binding = pageState.binding;
    const snap = pageState.snapshot || {};
    const status = snap.status || 'PLANNED';
    const pageNo = snap.pageNo || 1;
    const checkedIn = !!snap.myParticipation?.checkedIn;
    const attendance = findOpenActivity('ATTENDANCE');

    return `
      <div class="classroom-panel">
        <h2>同步课堂 ${statusBadge(status)}</h2>
        <p class="sub">${binding?.courseTitle || ''} · 第 ${pageNo} 页</p>
        <div class="classroom-card">
          <div class="classroom-slide-preview" style="min-height:160px;">
            <span class="page-no">第 ${pageNo} 页</span>
            <span>跟随教师翻页（服务端快照）</span>
          </div>
        </div>
        <div class="classroom-card" style="margin-top:12px;">
          <h3>课堂签到</h3>
          <div class="classroom-actions">
            <button type="button" class="classroom-btn classroom-btn--primary" data-action="checkin"
              data-activity-id="${attendance?.activityId || ''}"
              ${status !== 'RUNNING' || checkedIn || !attendance ? 'disabled' : ''}>
              ${checkedIn ? '已签到' : '签到'}
            </button>
          </div>
        </div>
        <div class="classroom-card" style="margin-top:12px;">
          ${quizBlock()}
        </div>
        ${errorNotice()}
        <div class="classroom-notice">手机优先布局。断线后通过快照接口恢复页码与本人提交状态。</div>
      </div>`;
  }

  async function pickBinding(role) {
    const bindings = await ClassroomApi.listMyBindings();
    const targetRole = role === 'teacher' ? 'TEACHER' : 'STUDENT';
    const match = bindings.find((b) => b.role === targetRole);
    if (!match) {
      throw new Error(role === 'teacher' ? '未找到任课教学班绑定（待第2组配置）' : '未找到选课记录（待第2组配置）');
    }
    return match;
  }

  async function loadTeacherState() {
    clearError();
    pageState.loading = true;
    try {
      const binding = await pickBinding('teacher');
      pageState.binding = binding;
      saveCtx({ bindingId: binding.bindingId, courseTitle: binding.courseTitle, className: binding.className });

      const prep = await ClassroomApi.getClassroomPrep(binding.bindingId);
      pageState.prep = prep;
      pageState.totalPages = prep.totalPages || 2;

      const active = await ClassroomApi.getActiveSession(binding.bindingId);
      if (active && active.sessionId) {
        pageState.sessionId = active.sessionId;
        pageState.snapshot = active;
        saveCtx({ sessionId: active.sessionId });
      } else {
        pageState.sessionId = loadCtx().sessionId || null;
        if (pageState.sessionId) {
          pageState.snapshot = await ClassroomApi.getSnapshot(pageState.sessionId, 'teacher');
        } else {
          pageState.snapshot = { status: 'PLANNED', pageNo: 1 };
        }
      }
    } catch (err) {
      showError(err.message || '加载课堂数据失败');
      pageState.snapshot = pageState.snapshot || { status: 'PLANNED', pageNo: 1 };
    } finally {
      pageState.loading = false;
    }
  }

  async function loadStudentState(joinOnSync) {
    clearError();
    pageState.loading = true;
    try {
      const binding = await pickBinding('student');
      pageState.binding = binding;
      saveCtx({ bindingId: binding.bindingId, courseTitle: binding.courseTitle, className: binding.className });

      const active = await ClassroomApi.getActiveSession(binding.bindingId);
      if (!active || !active.sessionId) {
        pageState.sessionId = null;
        pageState.snapshot = { status: 'PLANNED', pageNo: 1 };
        return;
      }
      pageState.sessionId = active.sessionId;
      saveCtx({ sessionId: active.sessionId });

      if (joinOnSync) {
        pageState.snapshot = await ClassroomApi.joinSession(active.sessionId);
        pageState.joined = true;
      } else {
        pageState.snapshot = await ClassroomApi.getSnapshot(active.sessionId, 'student');
      }
    } catch (err) {
      showError(err.message || '加载课堂数据失败');
      pageState.snapshot = pageState.snapshot || { status: 'PLANNED', pageNo: 1 };
    } finally {
      pageState.loading = false;
    }
  }

  async function refreshSnapshot(view) {
    if (!pageState.sessionId) return;
    try {
      pageState.snapshot = await ClassroomApi.getSnapshot(pageState.sessionId, view);
      clearError();
      const role = document.body.dataset.role;
      const pageName = window.MENU[window.currentGi].children[window.currentIi];
      paint(role, pageName);
    } catch (err) {
      showError(err.message || '同步快照失败');
    }
  }

  function bindPageEvents() {
    const pageBody = document.getElementById('pageBody');
    if (!pageBody) return;

    pageBody.querySelectorAll('[data-action]').forEach((btn) => {
      btn.addEventListener('click', () => handleAction(btn));
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

  async function handleAction(btn) {
    const action = btn.getAttribute('data-action');
    const role = document.body.dataset.role;

    try {
      clearError();
      switch (action) {
        case 'start-session': {
          const prep = pageState.prep || await ClassroomApi.getClassroomPrep(pageState.binding.bindingId);
          const started = await ClassroomApi.startSession({
            bindingId: prep.bindingId,
            lessonId: prep.lessonId,
            planId: prep.planId,
            releaseId: prep.releaseId,
            resourceVersionId: prep.resourceVersionId,
            initialPageNo: 1
          });
          pageState.sessionId = started.sessionId;
          pageState.snapshot = await ClassroomApi.getSnapshot(started.sessionId, 'teacher');
          saveCtx({ sessionId: started.sessionId });
          break;
        }
        case 'pause-session':
          await ClassroomApi.pauseSession(pageState.sessionId);
          pageState.snapshot = await ClassroomApi.getSnapshot(pageState.sessionId, 'teacher');
          break;
        case 'resume-session':
          await ClassroomApi.resumeSession(pageState.sessionId);
          pageState.snapshot = await ClassroomApi.getSnapshot(pageState.sessionId, 'teacher');
          break;
        case 'end-session':
          pageState.snapshot = await ClassroomApi.endSession(pageState.sessionId);
          break;
        case 'prev-page':
        case 'next-page': {
          const pageNo = pageState.snapshot?.pageNo || 1;
          const next = action === 'prev-page' ? pageNo - 1 : pageNo + 1;
          pageState.snapshot = await ClassroomApi.changePage(pageState.sessionId, next, false);
          break;
        }
        case 'start-checkin':
          await ClassroomApi.createActivity({
            sessionId: pageState.sessionId,
            bindingId: pageState.binding.bindingId,
            activityKind: 'ATTENDANCE',
            title: '课堂签到',
            opensAt: isoNow(),
            closesAt: isoPlusMinutes(10)
          });
          pageState.snapshot = await ClassroomApi.getSnapshot(pageState.sessionId, 'teacher');
          break;
        case 'start-quiz': {
          const prep = pageState.prep;
          await ClassroomApi.createActivity({
            sessionId: pageState.sessionId,
            bindingId: pageState.binding.bindingId,
            activityKind: 'QUIZ',
            title: '课堂随堂题',
            questionId: prep?.defaultQuestionId,
            questionVersionId: prep?.defaultQuestionVersionId,
            opensAt: isoNow(),
            closesAt: isoPlusMinutes(15)
          });
          pageState.snapshot = await ClassroomApi.getSnapshot(pageState.sessionId, 'teacher');
          break;
        }
        case 'publish-quiz': {
          const quiz = findOpenActivity('QUIZ');
          if (quiz) await ClassroomApi.publishResult(quiz.activityId);
          pageState.snapshot = await ClassroomApi.getSnapshot(pageState.sessionId, 'teacher');
          break;
        }
        case 'open-projection':
          window.open('/projection.html?sessionId=' + encodeURIComponent(pageState.sessionId), '_blank', 'noopener');
          return;
        case 'open-console':
          window.location.href = '/teacher.html?g=1&i=3';
          return;
        case 'join-class':
          window.location.href = '/student.html?g=1&i=3';
          return;
        case 'checkin': {
          const activityId = btn.getAttribute('data-activity-id');
          if (!activityId) throw new Error('当前没有签到活动');
          await ClassroomApi.checkin(activityId);
          pageState.snapshot = await ClassroomApi.joinSession(pageState.sessionId);
          break;
        }
        case 'submit-quiz': {
          const activityId = btn.getAttribute('data-activity-id');
          const selected = document.querySelector('.classroom-quiz-option.selected input');
          if (!selected) throw new Error('请先选择答案');
          await ClassroomApi.submitResponse(activityId, [{ itemKey: 'q1', value: selected.value }]);
          pageState.snapshot = await ClassroomApi.getSnapshot(pageState.sessionId, 'student');
          break;
        }
        default:
          return;
      }
    } catch (err) {
      showError(err.message || '操作失败');
    }

    const pageName = window.MENU[window.currentGi].children[window.currentIi];
    paint(role, pageName);
  }

  function paint(role, pageName) {
    const teacherKey = TEACHER_PAGES[pageName];
    const studentKey = STUDENT_PAGES[pageName];

    if (role === 'teacher' && teacherKey) {
      const html = teacherKey === 'teacherQuickStart' ? teacherQuickStartHtml() : teacherConsoleHtml();
      renderInto(pageName, html, false);
      return true;
    }

    if (role === 'student' && studentKey) {
      const html = studentKey === 'studentJoin' ? studentJoinHtml() : studentSyncHtml();
      renderInto(pageName, html, studentKey === 'studentSync');
      return true;
    }

    resetContent();
    return false;
  }

  async function render(role, pageName) {
    stopPolling();
    const teacherKey = TEACHER_PAGES[pageName];
    const studentKey = STUDENT_PAGES[pageName];

    if (role === 'teacher' && teacherKey) {
      await loadTeacherState();
      paint(role, pageName);
      if (pageState.sessionId) {
        startPolling(() => refreshSnapshot('teacher'));
      }
      return true;
    }

    if (role === 'student' && studentKey) {
      const joinOnSync = studentKey === 'studentSync';
      await loadStudentState(joinOnSync);
      paint(role, pageName);
      if (pageState.sessionId && joinOnSync) {
        startPolling(() => refreshSnapshot('student'));
      }
      return true;
    }

    resetContent();
    return false;
  }

  function ensureDemoSession() {
    return loadCtx();
  }

  window.ClassroomPages = { render, resetContent, ensureDemoSession };
})();
