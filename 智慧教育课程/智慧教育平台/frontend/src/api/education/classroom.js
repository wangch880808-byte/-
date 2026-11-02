import request from '@/utils/request'

/** 第 4 组课堂与活动接口（契约见 docs/api-contracts-classroom.md）。 */

export const listMyBindings = () =>
  request({ url: '/education/course/bindings/mine' }).then(r => r.data || [])

export const getClassroomPrep = bindingId =>
  request({ url: `/education/course/bindings/${bindingId}/classroom-prep` }).then(r => r.data)

export const getActiveSession = bindingId =>
  request({ url: `/education/classroom/sessions/active?bindingId=${bindingId}` }).then(r => r.data)

export const startSession = data =>
  request({ url: '/education/classroom/sessions', method: 'post', data }).then(r => r.data)

export const getSnapshot = (sessionId, view) =>
  request({ url: `/education/classroom/sessions/${sessionId}/snapshot?view=${view}` }).then(r => r.data)

export const pauseSession = sessionId =>
  request({ url: `/education/classroom/sessions/${sessionId}/pause`, method: 'post' })

export const resumeSession = sessionId =>
  request({ url: `/education/classroom/sessions/${sessionId}/resume`, method: 'post' })

export const endSession = sessionId =>
  request({ url: `/education/classroom/sessions/${sessionId}/end`, method: 'post' }).then(r => r.data)

export const changePage = (sessionId, pageNo) =>
  request({
    url: `/education/classroom/sessions/${sessionId}/page`,
    method: 'post',
    data: { pageNo, studentCanBrowse: false }
  }).then(r => r.data)

export const joinSession = sessionId =>
  request({ url: `/education/classroom/sessions/${sessionId}/join`, method: 'post' }).then(r => r.data)

export const createActivity = data =>
  request({ url: '/education/activity/instances', method: 'post', data }).then(r => r.data)

export const checkin = activityId =>
  request({ url: `/education/activity/instances/${activityId}/checkin`, method: 'post' }).then(r => r.data)

export const submitResponse = (activityId, answers) =>
  request({
    url: `/education/activity/instances/${activityId}/responses`,
    method: 'post',
    data: { answers }
  }).then(r => r.data)

export const publishResult = activityId =>
  request({ url: `/education/activity/instances/${activityId}/publish-result`, method: 'post' })

export const getStatistics = activityId =>
  request({ url: `/education/activity/instances/${activityId}/statistics` }).then(r => r.data)
