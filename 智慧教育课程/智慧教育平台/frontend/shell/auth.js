/* 服务端身份、操作权限及授权路由的前端投影。服务端仍负责每次操作的数据权限。 */
(function () {
  'use strict';
  var identity = null;
  var permissions = new Set();
  var routes = new Set();

  function normalize(value) {
    return String(value || '').replace(/^\/+|\/+$/g, '').replace(/\/index$/, '');
  }

  function collect(items, parent) {
    (items || []).forEach(function (item) {
      if (item.hidden) return;
      var route = String(item.path || '');
      var full = route.charAt(0) === '/' ? route : [parent, route].filter(Boolean).join('/');
      routes.add(normalize(full));
      if (item.component) routes.add(normalize(item.component));
      collect(item.children, full);
    });
  }

  function reset() {
    identity = null;
    permissions.clear();
    routes.clear();
  }

  function set(info, routerList) {
    reset();
    if (!info || !info.user || info.user.userId === undefined) throw new Error('身份数据不完整');
    identity = { userId: String(info.user.userId), roles: (info.roles || []).slice() };
    (info.permissions || []).forEach(function (permission) { permissions.add(permission); });
    collect(routerList || [], '');
  }

  function has(permission) {
    return !!identity && (!permission || permissions.has('*:*:*') || permissions.has(permission));
  }

  function canOpen(page) {
    if (!identity || !page) return false;
    if (page.roles && !page.roles.some(function (role) { return identity.roles.indexOf(role) !== -1; })) return false;
    if (!has(page.permission)) return false;
    return !page.route || routes.has(normalize(page.route));
  }

  window.EduAuth = {
    set: set, reset: reset, has: has, canOpen: canOpen,
    getIdentity: function () { return identity ? { userId: identity.userId, roles: identity.roles.slice() } : null; }
  };
})();
