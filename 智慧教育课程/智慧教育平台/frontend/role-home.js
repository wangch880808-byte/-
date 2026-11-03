(() => {
  'use strict';
  const roleKey = 'zhijiao.login.role';
  const pageRole = document.body.dataset.role;
  const knownRoles = ['admin', 'teacher', 'student'];
  const status = document.getElementById('authStatus');
  const message = document.getElementById('authMessage');
  const retryButton = document.getElementById('authRetry');
  const logoutButton = document.getElementById('logoutBtn');
  let version = 0;
  let checking = null;
  let leaving = false;

  function showStatus(text, canRetry = false) {
    document.body.classList.add('auth-pending');
    status.hidden = false; message.textContent = text; retryButton.hidden = !canRetry;
  }
  function goToLogin(reason = '') {
    EduApi.clearSession();
    window.location.replace('/login.html' + (reason ? '?logout=' + reason : ''));
  }
  async function initialize() {
    const attempt = ++version;
    if (checking) checking.abort();
    checking = new AbortController();
    const signal = checking.signal;
    showStatus('正在核验登录信息…');
    window.EduShell.suspend();
    if (window.EduContext) window.EduContext.suspend();
    if (!EduApi.getToken() || !knownRoles.includes(pageRole)) { goToLogin(); return; }
    try {
      const info = await EduApi.get('/getInfo', { signal, silent403: true });
      if (attempt !== version) return;
      const roles = Array.isArray(info.roles) ? info.roles : [];
      if (!roles.includes(pageRole)) {
        const allowed = [sessionStorage.getItem(roleKey), ...knownRoles].find(role => knownRoles.includes(role) && roles.includes(role));
        if (allowed) { sessionStorage.setItem(roleKey, allowed); window.location.replace('/' + allowed + '.html'); }
        else goToLogin();
        return;
      }
      const routers = await EduApi.get('/getRouters', { signal, silent403: true });
      if (attempt !== version) return;
      if (window.EduContext) await window.EduContext.initialize({ userId: info.user.userId, role: pageRole });
      if (attempt !== version || !EduApi.getToken()) return;
      window.EduShell.authorize(info, Array.isArray(routers.data) ? routers.data : []);
      sessionStorage.setItem(roleKey, pageRole);
      const user = info.user;
      document.querySelector('.avatar').title = (user.nickName || user.userName || '已登录用户') + ' (' + (user.userName || '账号未知') + ')';
      status.hidden = true; document.body.classList.remove('auth-pending');
    } catch (error) {
      if (attempt !== version || error.cancelled) return;
      if (error.unauthorized) goToLogin('expired');
      else showStatus('无法加载页面：' + error.message, true);
    }
  }
  window.EduSession = { refresh: initialize };
  retryButton.addEventListener('click', initialize);
  logoutButton.addEventListener('click', async () => {
    if (leaving) return;
    leaving = true; version += 1;
    if (checking) checking.abort();
    logoutButton.disabled = true;
    showStatus('正在退出登录…');
    window.EduShell.suspend();
    let reason = 'ok';
    try { await EduApi.post('/logout'); } catch (error) { reason = error.unauthorized ? 'expired' : 'unconfirmed'; }
    goToLogin(reason);
  });
  window.addEventListener('pagehide', () => {
    version += 1; if (checking) checking.abort();
    document.body.classList.add('auth-pending');
    window.EduShell.suspend();
    if (window.EduContext) window.EduContext.suspend();
  });
  window.addEventListener('pageshow', event => { if (event.persisted && !leaving) initialize(); });
  initialize();
})();
