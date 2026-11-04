(() => {
  'use strict';

  const TOKEN_KEY = 'zhijiao.login.token';

  function getToken() {
    return sessionStorage.getItem(TOKEN_KEY) || '';
  }

  function clearSession() {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem('zhijiao.login.role');
  }

  async function request(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    const token = getToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    if (options.body && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }

    const response = await fetch(path, {
      cache: 'no-store',
      ...options,
      headers
    });

    let result;
    try {
      result = await response.json();
    } catch {
      const error = new Error('服务器响应无法解析');
      error.status = response.status;
      throw error;
    }

    if (!response.ok || result.code !== 200) {
      const error = new Error(result.msg || `请求失败（${response.status}）`);
      error.status = response.status;
      error.code = result.code;
      error.unauthorized = response.status === 401 || result.code === 401;
      error.forbidden = response.status === 403 || result.code === 403;
      throw error;
    }

    return result;
  }

  function get(path, options = {}) {
    return request(path, { ...options, method: 'GET' });
  }

  function post(path, body, options = {}) {
    return request(path, {
      ...options,
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  }

  window.ZhijiaoApi = {
    getToken,
    clearSession,
    request,
    get,
    post
  };
})();
