/* ==========================================================================
   edu-api.js —— 教学业务的统一请求层（第 2 组 2B）
   --------------------------------------------------------------------------
   为什么单独写一个请求层，而不是每个页面自己 fetch？
   1. 令牌只有一份，放在 sessionStorage 的 'zhijiao.login.token'（和 role-home.js 一致），
      统一在这里取，页面代码不用关心认证细节。
   2. 后端返回结构统一是若依的 { code, msg, data } / { code, rows, total }。
      在这里一次性转成「成功就返回数据，失败就抛错」，页面只写 try/catch。
   3. 401（没登录 / 令牌过期）和 403（登录了但无权限）必须区别对待：
      401 → 回登录页；403 → 页面显示「无权限」状态，不能白屏也不能弹原始错误码。
   4. 打开演示模式时，请求会被 edu-mock.js 接管，没有后端也能看页面。
   ========================================================================== */
(function (window) {
  'use strict';

  var TOKEN_KEY = 'zhijiao.login.token';

  /* 业务接口统一前缀。接口约定见 docs/api-contracts.md 的建议命名空间 /education，
     2A 的 Controller 若用了别的前缀，只改这一行即可。 */
  var BASE = '/education';

  /* 自定义错误对象：页面靠 kind 判断该显示哪种状态 */
  function EduApiError(message, options) {
    var err = new Error(message || '请求失败');
    err.name = 'EduApiError';
    err.kind = (options && options.kind) || 'business'; // unauthorized | forbidden | network | business
    err.status = options && options.status;
    err.code = options && options.code;
    return err;
  }

  function token() {
    try { return sessionStorage.getItem(TOKEN_KEY) || ''; } catch (e) { return ''; }
  }

  /* 把 { a: 1, b: '' } 拼成 ?a=1（空值不传，避免后端把空字符串当条件） */
  function buildQuery(params) {
    if (!params) return '';
    var parts = [];
    for (var key in params) {
      if (!Object.prototype.hasOwnProperty.call(params, key)) continue;
      var value = params[key];
      if (value === undefined || value === null || value === '') continue;
      parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value));
    }
    return parts.length ? '?' + parts.join('&') : '';
  }

  function redirectToLogin() {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem('zhijiao.login.role');
    } catch (e) { /* 忽略隐私模式下的存储异常 */ }
    if (location.pathname.indexOf('login.html') === -1) {
      location.replace('/login.html?logout=expired');
    }
  }

  /* ---- 核心：发一个请求 ---- */
  function request(method, path, options) {
    options = options || {};
    var url = BASE + path + buildQuery(options.params);

    // 演示模式：不真的发请求，交给假数据层生成响应
    if (window.EduMock && window.EduMock.isEnabled()) {
      return window.EduMock.handle(method, path, options.params, options.body);
    }

    var headers = { Accept: 'application/json' };
    var currentToken = token();
    if (currentToken) headers.Authorization = 'Bearer ' + currentToken;

    var init = { method: method, headers: headers, cache: 'no-store' };
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json;charset=utf-8';
      init.body = JSON.stringify(options.body);
    }

    return fetch(url, init).then(function (response) {
      return response.text().then(function (text) {
        var result = null;
        try { result = text ? JSON.parse(text) : null; } catch (e) { result = null; }

        // 1) HTTP 层就失败的
        if (!response.ok) {
          if (response.status === 401) {
            redirectToLogin();
            throw EduApiError('登录已过期，请重新登录', { kind: 'unauthorized', status: 401 });
          }
          if (response.status === 403) {
            throw EduApiError('你没有权限执行该操作', { kind: 'forbidden', status: 403 });
          }
          throw EduApiError('服务不可用（' + response.status + '）', { kind: 'network', status: response.status });
        }

        // 2) 若依习惯：HTTP 200 但在 body 里用 code 表示业务结果
        if (result && typeof result.code === 'number' && result.code !== 200) {
          if (result.code === 401) {
            redirectToLogin();
            throw EduApiError('登录已过期，请重新登录', { kind: 'unauthorized', code: 401 });
          }
          if (result.code === 403) {
            throw EduApiError(result.msg || '你没有权限执行该操作', { kind: 'forbidden', code: 403 });
          }
          throw EduApiError(result.msg || '操作失败', { kind: 'business', code: result.code });
        }

        if (!result) throw EduApiError('服务器返回内容无法解析', { kind: 'network' });

        // 3) 分页结构：{ code, rows, total } 或 { code, data: { rows, total } }
        if (Array.isArray(result.rows)) {
          return { rows: result.rows, total: Number(result.total) || result.rows.length };
        }
        if (result.data !== undefined) return result.data;
        return result;
      });
    }, function () {
      // 只捕获网络层失败（断网、跨域、服务未启动）
      throw EduApiError('无法连接服务器，请检查网络后重试', { kind: 'network' });
    });
  }

  window.EduApi = {
    BASE: BASE,
    request: request,
    get: function (path, params) { return request('GET', path, { params: params }); },
    post: function (path, body, params) { return request('POST', path, { body: body, params: params }); },
    put: function (path, body) { return request('PUT', path, { body: body }); },
    del: function (path, params) { return request('DELETE', path, { params: params }); },

    /* 列表接口的容错包装：无论后端返回数组、{rows,total} 还是 {data:{...}}，都统一成 {rows,total} */
    list: function (path, params) {
      return request('GET', path, { params: params }).then(function (data) {
        if (Array.isArray(data)) return { rows: data, total: data.length };
        if (data && Array.isArray(data.rows)) return { rows: data.rows, total: Number(data.total) || data.rows.length };
        return { rows: [], total: 0 };
      });
    },

    token: token,
    redirectToLogin: redirectToLogin,
    error: EduApiError
  };
})(window);
