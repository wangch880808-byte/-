/**
 * 6-02 学生个人通知接口（第 6 组 6B）。
 *
 * 契约来源：docs/6-02-frontend-api-handoff.md 第 5.3–5.7 节。
 * 后端 main 分支当前只有 StudentAnnouncementController，通知 Controller 尚未落地；
 * 本文件按契约实现，接口未注册时由页面显示"接口未就绪"，不写本地假数据。
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
