(() => {
  'use strict';

  const roleInfo = {
    admin: { title: '管理员登录', sub: '周口师范学院 · 教务管理端 · 仅限授权人员访问', placeholder: '请输入工号 / 管理员账号', label: '管理员' },
    teacher: { title: '教师登录', sub: '周口师范学院 · 教学工作台 · 仅限授权人员访问', placeholder: '请输入工号 / 教师账号', label: '教师' },
    student: { title: '学生登录', sub: '周口师范学院 · 学习空间 · 仅限授权人员访问', placeholder: '请输入学号 / 学生账号', label: '学生' }
  };
  const tokenKey = 'zhijiao.login.token';
  const roleKey = 'zhijiao.login.role';
  const form = document.getElementById('loginForm');
  const accountInput = document.getElementById('accountInput');
  const passwordInput = document.getElementById('passwordInput');
  const captchaInput = document.getElementById('captchaInput');
  const captchaField = document.getElementById('captchaField');
  const captchaImage = document.getElementById('captchaImage');
  const captchaFallback = document.getElementById('captchaFallback');
  const loginButton = document.getElementById('loginBtn');
  const notice = document.getElementById('statusNotice');
  let selectedRole = 'admin';
  let captchaUuid = '';
  let captchaEnabled = true;
  let captchaRequest = 0;
  let submitting = false;

  function showStatus(message, success = false) {
    notice.textContent = message;
    notice.className = `notice ${success ? 'notice-info' : 'notice-warn'}`;
    notice.hidden = false;
  }

  function clearStatus() {
    notice.hidden = true;
    notice.textContent = '';
  }

  function selectRole(role) {
    if (!roleInfo[role]) return;
    selectedRole = role;
    const info = roleInfo[role];
    document.getElementById('cardTitle').textContent = info.title;
    document.getElementById('cardSub').textContent = info.sub;
    accountInput.placeholder = info.placeholder;
    document.querySelectorAll('.role').forEach(button => {
      const active = button.dataset.role === role;
      button.classList.toggle('active', active);
      button.setAttribute('aria-pressed', String(active));
    });
    clearStatus();
  }

  async function api(path, options = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), options.timeout || 15000);
    try {
      const response = await fetch(path, { cache: 'no-store', signal: controller.signal, ...options });
      let result;
      try {
        result = await response.json();
      } catch {
        throw new Error('服务器响应无法解析');
      }
      if (!response.ok || result.code !== 200) {
        throw new Error(result.msg || `请求失败（${response.status}）`);
      }
      return result;
    } catch (error) {
      if (error.name === 'AbortError') throw new Error('请求超时，请稍后重试');
      if (error instanceof TypeError) throw new Error('网络连接异常，请检查网络后重试');
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }

  async function loadCaptcha() {
    const seq = ++captchaRequest;
    captchaUuid = '';
    captchaInput.value = '';
    captchaImage.hidden = true;
    captchaFallback.hidden = false;
    captchaFallback.textContent = '加载中';
    try {
      const result = await api('/captchaImage');
      if (seq !== captchaRequest) return; // 已有更新的请求，丢弃旧响应
      captchaEnabled = result.captchaEnabled !== false;
      captchaField.hidden = !captchaEnabled;
      captchaInput.required = captchaEnabled;
      if (captchaEnabled) {
        if (!result.uuid || !result.img) throw new Error('验证码数据不完整');
        captchaUuid = result.uuid;
        captchaImage.src = `data:image/jpeg;base64,${result.img}`;
        captchaImage.hidden = false;
        captchaFallback.hidden = true;
      }
    } catch (error) {
      if (seq !== captchaRequest) return;
      captchaEnabled = true;
      captchaField.hidden = false;
      captchaInput.required = true;
      captchaFallback.textContent = '点击重试';
      showStatus(`验证码加载失败：${error.message}`);
    }
  }

  function clearSession() {
    sessionStorage.removeItem(tokenKey);
    sessionStorage.removeItem(roleKey);
  }

  async function logoutOnServer(token) {
    if (!token) return;
    await api('/logout', { method: 'POST', headers: { Authorization: `Bearer ${token}` } });
  }

  async function loadIdentity(token, role) {
    const headers = { Authorization: `Bearer ${token}` };
    const info = await api('/getInfo', { headers });
    if (!Array.isArray(info.roles) || !info.roles.includes(role)) {
      throw new Error(`此账号没有${roleInfo[role].label}角色，请选择正确入口或联系管理员`);
    }
    await api('/getRouters', { headers });
  }

  function openRolePage(role) {
    window.location.replace(`/${role}.html`);
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (submitting) return; // 防止回车/连点重复提交
    clearStatus();
    if (captchaEnabled && !captchaUuid) {
      showStatus('请先加载验证码');
      await loadCaptcha();
      return;
    }
    submitting = true;
    loginButton.disabled = true;
    loginButton.textContent = '登录中…';
    let token = '';
    try {
      const result = await api('/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: accountInput.value.trim(),
          password: passwordInput.value,
          role: selectedRole,
          code: captchaEnabled ? captchaInput.value.trim() : '',
          uuid: captchaEnabled ? captchaUuid : ''
        })
      });
      if (!result.token) throw new Error('登录成功但未收到令牌');
      token = result.token;
      await loadIdentity(token, selectedRole);
      sessionStorage.setItem(tokenKey, token);
      sessionStorage.setItem(roleKey, selectedRole);
      passwordInput.value = '';
      openRolePage(selectedRole);
    } catch (error) {
      if (token) {
        try { await logoutOnServer(token); } catch { /* 本页已清理令牌 */ }
      }
      clearSession();
      showStatus(error.message || '登录失败，请稍后重试');
      await loadCaptcha();
      captchaInput.focus();
    } finally {
      submitting = false;
      loginButton.disabled = false;
      loginButton.textContent = '登 录';
    }
  });

  document.querySelectorAll('.role').forEach(button => {
    button.addEventListener('click', () => selectRole(button.dataset.role));
  });
  document.getElementById('captchaRefresh').addEventListener('click', loadCaptcha);
  async function initialize() {
    const token = sessionStorage.getItem(tokenKey);
    const role = sessionStorage.getItem(roleKey);
    if (token && roleInfo[role]) {
      selectRole(role);
      try {
        await loadIdentity(token, role);
        openRolePage(role);
        return;
      } catch {
        clearSession();
      }
    }
    await loadCaptcha();
    const logout = new URLSearchParams(window.location.search).get('logout');
    if (logout === 'ok') {
      showStatus('已退出登录。', true);
    } else if (logout === 'unconfirmed') {
      showStatus('本页已退出，服务端退出状态未确认。');
    } else if (logout === 'expired') {
      showStatus('登录状态已失效，请重新登录。');
    }
  }

  initialize();
})();
