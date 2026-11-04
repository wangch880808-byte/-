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
  request({
    url: `/education/classroom/sessions/${sessionId}/snapshot`,
    params: { view },
    auth: view !== 'public'
  }).then(r => r.data)

/** 断线补查：先 snapshot，再用 afterSeq 拉增量事件。 */
export const getEvents = (sessionId, afterSeq = 0, view = 'public') =>
  request({
    url: `/education/classroom/sessions/${sessionId}/events`,
    params: { afterSeq, view }
  }).then(r => r.data || [])

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

export const checkin = (activityId, idempotencyKey) =>
  request({
    url: `/education/activity/instances/${activityId}/checkin`,
    method: 'post',
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
  }).then(r => r.data)

export const submitResponse = (activityId, answers, idempotencyKey) =>
  request({
    url: `/education/activity/instances/${activityId}/responses`,
    method: 'post',
    data: { answers },
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
  }).then(r => r.data)

export const closeActivity = activityId =>
  request({ url: `/education/activity/instances/${activityId}/close`, method: 'post' })

export const publishResult = activityId =>
  request({ url: `/education/activity/instances/${activityId}/publish-result`, method: 'post' })

export const getStatistics = activityId =>
  request({ url: `/education/activity/instances/${activityId}/statistics` }).then(r => r.data)

/** 带令牌拉取预览图；失败时返回 null，不阻断课堂主流程。 */
export async function fetchPreviewObjectUrl(previewUrl) {
  if (!previewUrl) return null
  try {
    const blob = await request({ url: previewUrl, responseType: 'blob', skipSessionRedirect: true })
    if (!(blob instanceof Blob) || blob.type.includes('json')) return null
    return URL.createObjectURL(blob)
  } catch {
    return null
  }
}
