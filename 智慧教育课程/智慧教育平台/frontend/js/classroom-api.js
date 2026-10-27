(() => {
  'use strict';

  const API = window.ZhijiaoApi;

  function listMyBindings() {
    return API.get('/education/course/bindings/mine').then((r) => r.data || []);
  }

  function getClassroomPrep(bindingId) {
    return API.get('/education/course/bindings/' + bindingId + '/classroom-prep').then((r) => r.data);
  }

  function getActiveSession(bindingId) {
    return API.get('/education/classroom/sessions/active?bindingId=' + bindingId).then((r) => r.data);
  }

  function startSession(body) {
    return API.post('/education/classroom/sessions', body).then((r) => r.data);
  }

  function getSession(sessionId) {
    return API.get('/education/classroom/sessions/' + sessionId).then((r) => r.data);
  }

  function getSnapshot(sessionId, view) {
    const q = view ? '?view=' + encodeURIComponent(view) : '';
    return API.get('/education/classroom/sessions/' + sessionId + '/snapshot' + q).then((r) => r.data);
  }

  function pauseSession(sessionId) {
    return API.post('/education/classroom/sessions/' + sessionId + '/pause');
  }

  function resumeSession(sessionId) {
    return API.post('/education/classroom/sessions/' + sessionId + '/resume');
  }

  function endSession(sessionId) {
    return API.post('/education/classroom/sessions/' + sessionId + '/end').then((r) => r.data);
  }

  function changePage(sessionId, pageNo, studentCanBrowse) {
    return API.post('/education/classroom/sessions/' + sessionId + '/page', {
      pageNo,
      studentCanBrowse: !!studentCanBrowse
    }).then((r) => r.data);
  }

  function joinSession(sessionId) {
    return API.post('/education/classroom/sessions/' + sessionId + '/join').then((r) => r.data);
  }

  function createActivity(body) {
    return API.post('/education/activity/instances', body).then((r) => r.data);
  }

  function checkin(activityId) {
    return API.post('/education/activity/instances/' + activityId + '/checkin').then((r) => r.data);
  }

  function submitResponse(activityId, answers) {
    return API.post('/education/activity/instances/' + activityId + '/responses', { answers })
      .then((r) => r.data);
  }

  function closeActivity(activityId) {
    return API.post('/education/activity/instances/' + activityId + '/close');
  }

  function publishResult(activityId) {
    return API.post('/education/activity/instances/' + activityId + '/publish-result');
  }

  window.ClassroomApi = {
    listMyBindings,
    getClassroomPrep,
    getActiveSession,
    startSession,
    getSession,
    getSnapshot,
    pauseSession,
    resumeSession,
    endSession,
    changePage,
    joinSession,
    createActivity,
    checkin,
    submitResponse,
    closeActivity,
    publishResult
  };
})();
