/**
 * 6-02 统一请求客户端（第 6 组 6B）。
 *
 * 只做三件事：附加若依令牌、按 code 判定成败、把错误归一成可判别的 ApiError。
 * 不在本文件写死任何业务路径，也不缓存业务数据。
 */
(function (global) {
  'use strict';

  var TOKEN_KEY = 'zhijiao.login.token';
  var ROLE_KEY = 'zhijiao.login.role';
  var redirecting = false;

  var KIND_BY_CODE = {
    0: 'network',
    400: 'validation',
    401: 'auth',
    403: 'permission',
    404: 'missing',
    409: 'conflict',
    500: 'server',
    502: 'network',
    503: 'unavailable',
    504: 'network'
  };

  function ApiError(detail) {
    this.name = 'ApiError';
    this.code = detail.code;
    this.httpStatus = detail.httpStatus || 0;
    this.kind = detail.kind || KIND_BY_CODE[detail.code] || 'unknown';
    this.message = detail.message || '请求失败';
    this.method = detail.method || 'GET';
    this.url = detail.url || '';
    this.requiresLogin = detail.requiresLogin === true;
    if (typeof Error.captureStackTrace === 'function') {
      Error.captureStackTrace(this, ApiError);
    }
  }

  ApiError.prototype = Object.create(Error.prototype);
  ApiError.prototype.constructor = ApiError;

  ApiError.prototype.isNetwork = function () {
    return this.kind === 'network';
  };

  ApiError.prototype.isAuth = function () {
    return this.code === 401;
  };

  ApiError.prototype.isPermission = function () {
    return this.code === 403;
  };

  ApiError.prototype.isNotFound = function () {
    return this.code === 404;
  };

  ApiError.prototype.isConflict = function () {
    return this.code === 409;
  };

  /** 路径未注册（后端 Controller 未部署）与"对象不存在"无法仅凭 code 区分，统一由调用方决定文案。 */
  ApiError.prototype.isServerSide = function () {
    return this.code === 500 || this.code === 503;
  };

  function buildQuery(query) {
    if (!query) {
      return '';
    }
    var parts = [];
    Object.keys(query).forEach(function (key) {
      var value = query[key];
      if (value === undefined || value === null || value === '') {
        return;
      }
      parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(String(value)));
    });
    return parts.length > 0 ? '?' + parts.join('&') : '';
  }

  function clearSession() {
    try {
      global.sessionStorage.removeItem(TOKEN_KEY);
      global.sessionStorage.removeItem(ROLE_KEY);
    } catch (e) {
      /* 存储不可用时忽略：跳登录页已足够终止流程 */
    }
  }

  function backToLogin() {
    if (redirecting) {
      return;
    }
    redirecting = true;
    clearSession();
    global.location.replace('/login.html?logout=expired');
  }

  function parseBody(text) {
    if (!text) {
      return null;
    }
    try {
      return JSON.parse(text);
    } catch (e) {
      return null;
    }
  }

  /**
   * @param {{method?:string, url:string, query?:object, body?:object}} options
   * @returns {Promise<object>} 成功时返回原始响应体（带 code / msg / data 或 total / rows）
   */
  function request(options) {
    var opts = options || {};
    var method = (opts.method || 'GET').toUpperCase();

    // Mock 模式：显式开启后业务请求全部走内置数据，不再打后端。
    // 未覆盖的路径直接报错而不是回落到真实请求，避免"一半真一半假"。
    if (global.EduMock && global.EduMock.enabled()) {
      return global.EduMock.request({
        method: method,
        path: opts.url,
        query: opts.query || {},
        body: opts.body
      });
    }

    var url = opts.url + buildQuery(opts.query);
    var headers = { Accept: 'application/json' };
    var token = null;

    try {
      token = global.sessionStorage.getItem(TOKEN_KEY);
    } catch (e) {
      token = null;
    }
    if (token) {
      headers.Authorization = 'Bearer ' + token;
    }

    var init = {
      method: method,
      headers: headers,
      cache: 'no-store',
      credentials: 'same-origin'
    };
    if (opts.body !== undefined) {
      headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(opts.body);
    }

    return global.fetch(url, init).then(function (response) {
      return response.text().then(function (text) {
        var body = parseBody(text);
        var httpStatus = response.status;
        var hasCode = body !== null && typeof body.code === 'number';
        var code = hasCode ? body.code : (httpStatus || 0);

        if (!response.ok && !hasCode) {
          throw new ApiError({
            code: httpStatus,
            httpStatus: httpStatus,
            message: '请求失败（HTTP ' + httpStatus + '）',
            method: method,
            url: url
          });
        }
        if (hasCode && code === 200 && response.ok) {
          return body;
        }
        if (hasCode && code === 200 && !response.ok) {
          throw new ApiError({
            code: httpStatus,
            httpStatus: httpStatus,
            message: '请求失败（HTTP ' + httpStatus + '）',
            method: method,
            url: url
          });
        }
        if (code === 401 || httpStatus === 401) {
          backToLogin();
          throw new ApiError({
            code: 401,
            httpStatus: httpStatus,
            message: '登录状态已失效，请重新登录',
            method: method,
            url: url,
            requiresLogin: true
          });
        }
        throw new ApiError({
          code: code,
          httpStatus: httpStatus,
          message: hasCode ? ((body && body.msg) || ('请求失败（' + code + '）')) : '响应缺少业务码 code，无法判定结果',
          method: method,
          url: url
        });
      });
    }).catch(function (error) {
      if (error instanceof ApiError) {
        throw error;
      }
      throw new ApiError({
        code: 0,
        message: '网络异常，未能连接到服务端',
        method: method,
        url: url
      });
    });
  }

  global.EduHttp = {
    request: request,
    get: function (url, query) {
      return request({ method: 'GET', url: url, query: query });
    },
    post: function (url, body) {
      return request({ method: 'POST', url: url, body: body === undefined ? {} : body });
    },
    put: function (url, body) {
      return request({ method: 'PUT', url: url, body: body === undefined ? {} : body });
    },
    ApiError: ApiError,
    backToLogin: backToLogin,
    tokenKey: TOKEN_KEY
  };
})(window);
