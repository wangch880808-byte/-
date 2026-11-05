/**
 * 6-02 学生个人通知接口（第 6 组 6B）。
 *
 * 契约来源：docs/6-02-frontend-api-handoff.md 第 5.3–5.7 节。
 * 后端已在 main@f21f51d 合并 StudentNoticeController，下列 5 个接口均已实现；
 * 前端按源码核对一致，真实联调仍受生产授权契约与 bindingId 来源阻塞
 * （见 docs/6-02-6B-http-interface-request.md 的 E 段）。
 */
(function (global) {
  'use strict';

  var http = global.EduHttp;
  var BASE = '/education/student/notices';

  function list(query) {
    return http.get(BASE, {
      kind: query.kind,
      bindingId: query.bindingId,
      unreadOnly: query.unreadOnly === true ? 'true' : undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    });
  }

  function detail(noticeId) {
    return http.get(BASE + '/' + encodeURIComponent(noticeId));
  }

  function unreadCount(bindingId) {
    return http.get(BASE + '/unread-count', { bindingId: bindingId });
  }

  function markRead(noticeId) {
    return http.post(BASE + '/' + encodeURIComponent(noticeId) + '/read');
  }

  function markAllRead() {
    return http.put(BASE + '/read-all');
  }

  global.EduNoticeApi = {
    list: list,
    detail: detail,
    unreadCount: unreadCount,
    markRead: markRead,
    markAllRead: markAllRead
  };
})(window);
