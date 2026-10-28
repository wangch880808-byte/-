/**
 * 6-02 公告接口（第 6 组 6B）。
 *
 * 对应后端 main 分支：
 * com.ruoyi.education.course.controller.TeacherAnnouncementController
 * com.ruoyi.education.course.controller.StudentAnnouncementController
 *
 * ID 一律按字符串传递，不做 Number() 转换；时间由后端输出 UTC 毫秒。
 */
(function (global) {
  'use strict';

  var http = global.EduHttp;
  var BASE = '/education';

  function teacherList(query) {
    return http.get(BASE + '/teacher/announcements', {
      bindingId: query.bindingId,
      status: query.status,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    });
  }

  function teacherDetail(announcementId) {
    return http.get(BASE + '/teacher/announcements/' + encodeURIComponent(announcementId));
  }

  function teacherCreate(payload) {
    return http.post(BASE + '/teacher/announcements', {
      bindingId: payload.bindingId,
      title: payload.title,
      body: payload.body,
      opensAt: payload.opensAt,
      closesAt: payload.closesAt
    });
  }

  function teacherUpdate(announcementId, payload) {
    return http.put(BASE + '/teacher/announcements/' + encodeURIComponent(announcementId), {
      title: payload.title,
      body: payload.body,
      opensAt: payload.opensAt,
      closesAt: payload.closesAt
    });
  }

  function teacherPublish(announcementId) {
    return http.post(BASE + '/teacher/announcements/' + encodeURIComponent(announcementId) + '/publish');
  }

  function teacherArchive(announcementId) {
    return http.post(BASE + '/teacher/announcements/' + encodeURIComponent(announcementId) + '/archive');
  }

  function studentList(query) {
    return http.get(BASE + '/student/announcements', {
      bindingId: query.bindingId,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    });
  }

  function studentDetail(announcementId) {
    return http.get(BASE + '/student/announcements/' + encodeURIComponent(announcementId));
  }

  global.EduAnnouncementApi = {
    teacher: {
      list: teacherList,
      detail: teacherDetail,
      create: teacherCreate,
      update: teacherUpdate,
      publish: teacherPublish,
      archive: teacherArchive,
      permissions: {
        list: 'education:announcement:list',
        add: 'education:announcement:add',
        edit: 'education:announcement:edit',
        publish: 'education:announcement:publish',
        archive: 'education:announcement:archive'
      }
    },
    student: {
      list: studentList,
      detail: studentDetail
    }
  };
})(window);
