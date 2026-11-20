(() => {
  'use strict';

  const tokenKey = 'zhijiao.login.token';
  const roleKey = 'zhijiao.login.role';
  const pageRole = document.body.dataset.role;
  const knownRoles = ['admin', 'teacher', 'student'];
  const status = document.getElementById('authStatus');
  const message = document.getElementById('authMessage');
  const retryButton = document.getElementById('authRetry');
  const logoutButton = document.getElementById('logoutBtn');

  function clearSession() {
    sessionStorage.removeItem(tokenKey);
    sessionStorage.removeItem(roleKey);
  }

  function goToLogin(reason = '') {
    window.location.replace(`/login.html${reason ? `?logout=${reason}` : ''}`);
  }

  function showStatus(text, canRetry = false) {
    document.body.classList.add('auth-pending');
    status.hidden = false;
    message.textContent = text;
    retryButton.hidden = !canRetry;
  }

  async function api(path, options = {}) {
    const response = await fetch(path, { cache: 'no-store', ...options });
    let result;
    try {
      result = await response.json();
    } catch {
      throw new Error('服务器响应无法解析');
    }
    if (!response.ok || result.code !== 200) {
      const error = new Error(result.msg || `请求失败（${response.status}）`);
      error.unauthorized = response.status === 401 || result.code === 401;
      throw error;
    }
    return result;
  }

  async function initialize() {
    showStatus('正在核验登录信息…');
    const token = sessionStorage.getItem(tokenKey);
    if (!token || !knownRoles.includes(pageRole)) {
      clearSession();
      goToLogin();
      return;
    }
    try {
      const headers = { Authorization: `Bearer ${token}` };
      const info = await api('/getInfo', { headers });
      const roles = Array.isArray(info.roles) ? info.roles : [];
      if (!roles.includes(pageRole)) {
        const allowedRole = [sessionStorage.getItem(roleKey), ...knownRoles]
          .find(role => knownRoles.includes(role) && roles.includes(role));
        if (allowedRole) {
          sessionStorage.setItem(roleKey, allowedRole);
          window.location.replace(`/${allowedRole}.html`);
        } else {
          clearSession();
          goToLogin();
        }
        return;
      }
      await api('/getRouters', { headers });
      sessionStorage.setItem(roleKey, pageRole);
      const user = info.user || {};
      document.querySelector('.avatar').title = `${user.nickName || user.userName || '已登录用户'} (${user.userName || '账号未知'})`;
      status.hidden = true;
      document.body.classList.remove('auth-pending');
    } catch (error) {
      if (error.unauthorized) {
        clearSession();
        goToLogin();
      } else {
        showStatus(`无法加载页面：${error.message}`, true);
      }
    }
  }

  retryButton.addEventListener('click', initialize);
  logoutButton.addEventListener('click', async () => {
    logoutButton.disabled = true;
    showStatus('正在退出登录…');
    let reason = 'ok';
    try {
      await api('/logout', {
        method: 'POST',
        headers: { Authorization: `Bearer ${sessionStorage.getItem(tokenKey)}` }
      });
    } catch {
      reason = 'unconfirmed';
    }
    clearSession();
    goToLogin(reason);
  });
  window.addEventListener('pagehide', () => document.body.classList.add('auth-pending'));
  window.addEventListener('pageshow', event => { if (event.persisted) initialize(); });
  initialize();
})();
