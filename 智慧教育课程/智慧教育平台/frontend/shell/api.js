/*
 * 智慧教育平台 · 统一请求客户端
 *
 * 目标：让所有业务页用同一套规则说话，避免各页自己写 fetch 后
 *      出现 401 不跳登录、403 露出私有数据、请求永不返回等问题。
 *
 * 约定：
 *   - 自动带 Bearer token（sessionStorage: zhijiao.login.token）
 *   - 默认 15 秒超时（可传 options.timeout 覆盖）
 *   - HTTP/业务码 401 → 清会话并跳登录页（?logout=expired）
 *   - HTTP/业务码 403 → 派发 window 事件 edu:forbidden，由外壳渲染无权限视图
 *   - 其他非 200 → 抛出带 status/code 的 Error，message 用服务端 msg
 *
 * 用法：
 *   const data = await EduApi.get('/education/course/list');
 *   const res  = await EduApi.post('/education/course', { name: '...' });
 *   EduApi.request('/export', { raw: true });   // 需要二进制时自行处理
 */
(function () {
  'use strict';

  var TOKEN_KEY = 'zhijiao.login.token';
  var ROLE_KEY = 'zhijiao.login.role';
  var DEFAULT_TIMEOUT = 15000;
  var pendingControllers = new Set();

  function makeError(message, extra) {
    var error = new Error(message || '请求失败');
    if (extra) {
      for (var key in extra) {
        if (Object.prototype.hasOwnProperty.call(extra, key)) error[key] = extra[key];
      }
    }
    return error;
  }

  function getToken() {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  function clearSession() {
    if (window.EduShell) window.EduShell.suspend();
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(ROLE_KEY);
    if (window.EduAuth) window.EduAuth.reset();
    if (window.EduContext) window.EduContext.clear();
  }

  function goToLogin(reason) {
    clearSession();
    window.location.replace('/login.html' + (reason ? '?logout=' + reason : ''));
  }

  function notifyForbidden(message) {
    try {
      window.dispatchEvent(new CustomEvent('edu:forbidden', { detail: { message: message } }));
    } catch (e) {
      // CustomEvent 在极旧环境不可用时忽略，仍会抛错给调用方
    }
  }

  function cancelAll() {
    pendingControllers.forEach(function (controller) { controller.abort(); });
  }

  function rejectUnauthorized() {
    goToLogin('expired');
    throw makeError('登录状态已失效，请重新登录', { status: 401, unauthorized: true });
  }

  function rejectForbidden(message, options) {
    var error = makeError(message || '没有访问该资源的权限', { status: 403, forbidden: true });
    if (!options.silent403) notifyForbidden(error.message);
    throw error;
  }

  /**
   * @param {string} path
   * @param {object} [options] method/headers/body/timeout/silent403/signal/raw
   * raw=true 返回未消费的 Response；主动取消抛出 error.cancelled，超时抛出 error.timeout。
   */
  async function request(path, options) {
    options = options || {};
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timedOut = false;
    var timeout = options.timeout || DEFAULT_TIMEOUT;
    var timer = controller ? setTimeout(function () {
      timedOut = true;
      controller.abort();
    }, timeout) : null;
    var abortFromCaller = function () { if (controller) controller.abort(); };
    if (controller) pendingControllers.add(controller);
    if (options.signal && controller) {
      options.signal.addEventListener('abort', abortFromCaller, { once: true });
      if (options.signal.aborted) controller.abort();
    }

    function checkCancelled() {
      if ((controller && controller.signal.aborted) || (options.signal && options.signal.aborted)) {
        var error = new Error('请求已取消');
        error.name = 'AbortError';
        throw error;
      }
    }

    try {
      checkCancelled();
      if (window.location.origin && new URL(path, window.location.href).origin !== window.location.origin) {
        throw makeError('接口地址必须与当前站点同源', { client: true });
      }
      var headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
      var token = getToken();
      if (token) headers.Authorization = 'Bearer ' + token;

      var body = options.body;
      if (body && typeof body === 'object' && !(typeof FormData !== 'undefined' && body instanceof FormData)) {
        headers['Content-Type'] = headers['Content-Type'] || 'application/json';
        body = JSON.stringify(body);
      }

      var response = await fetch(path, {
        method: options.method || 'GET',
        headers: headers,
        body: body,
        cache: 'no-store',
        signal: controller ? controller.signal : options.signal
      });
      checkCancelled();

      // 网关或反向代理也可能返回 HTML，HTTP 鉴权状态不依赖 JSON 解析成功。
      if (response.status === 401) rejectUnauthorized();
      if (response.status === 403) rejectForbidden(null, options);

      var contentType = response.headers.get('content-type') || '';
      if (options.raw && response.ok && !/\bjson\b/i.test(contentType)) return response;

      var data;
      try {
        data = await (options.raw ? response.clone() : response).json();
      } catch (parseError) {
        checkCancelled();
        throw makeError('服务器响应无法解析（HTTP ' + response.status + '）', { status: response.status });
      }
      checkCancelled();

      var code = data && typeof data.code !== 'undefined' ? data.code : response.status;

      if (code === 401) rejectUnauthorized();

      if (code === 403) rejectForbidden(data && data.msg, options);

      if (!response.ok || code !== 200) {
        throw makeError((data && data.msg) || ('请求失败（' + response.status + '）'), { status: response.status, code: code });
      }

      return options.raw ? response : data;
    } catch (error) {
      if (error && (error.unauthorized || error.forbidden)) throw error;
      if ((error && error.name === 'AbortError') || (controller && controller.signal.aborted)) {
        if (timedOut) throw makeError('请求超时（' + timeout + 'ms），请稍后重试', { timeout: true });
        throw makeError('请求已取消', { cancelled: true });
      }
      if (error instanceof TypeError) {
        throw makeError('网络连接异常，请检查网络后重试', { network: true });
      }
      throw error;
    } finally {
      if (timer) clearTimeout(timer);
      if (controller) pendingControllers.delete(controller);
      if (options.signal && controller) options.signal.removeEventListener('abort', abortFromCaller);
    }
  }

  window.EduApi = {
    request: request,
    get: function (path, options) {
      return request(path, Object.assign({}, options, { method: 'GET' }));
    },
    post: function (path, body, options) {
      return request(path, Object.assign({}, options, { method: 'POST', body: body }));
    },
    put: function (path, body, options) {
      return request(path, Object.assign({}, options, { method: 'PUT', body: body }));
    },
    del: function (path, options) {
      return request(path, Object.assign({}, options, { method: 'DELETE' }));
    },
    getToken: getToken,
    cancelAll: cancelAll,
    clearSession: clearSession,
    goToLogin: goToLogin
  };
})();
