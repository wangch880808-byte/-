import request from '@/utils/request'

/** Group 4 classroom / activity APIs under /education/group4. */

const unwrap = r => (r && Object.prototype.hasOwnProperty.call(r, 'data') ? r.data : r)

export const getClassroomPrep = bindingId =>
  request({ url: '/education/group4/sessions/prep', params: { bindingId } }).then(unwrap)

export const getActiveSession = bindingId =>
  request({ url: '/education/group4/sessions/active', params: { bindingId } }).then(unwrap)

export const startSession = data =>
  request({ url: '/education/group4/sessions', method: 'post', data }).then(unwrap)

export const getSnapshot = (sessionId, view) =>
  request({
    url: `/education/group4/sessions/${sessionId}/snapshot`,
    params: { view },
    headers: view === 'public' ? { isToken: false, silent403: true } : { silent403: true }
  }).then(unwrap)

export const getEvents = (sessionId, afterSeq = 0, view = 'public') =>
  request({
    url: `/education/group4/sessions/${sessionId}/events`,
    params: { afterSeq, view },
    headers: { silent403: true }
  }).then(r => unwrap(r) || [])

export const pauseSession = sessionId =>
  request({ url: `/education/group4/sessions/${sessionId}/pause`, method: 'post' })

export const resumeSession = sessionId =>
  request({ url: `/education/group4/sessions/${sessionId}/resume`, method: 'post' })

export const endSession = sessionId =>
  request({ url: `/education/group4/sessions/${sessionId}/end`, method: 'post' }).then(unwrap)

export const changePage = (sessionId, pageNo) =>
  request({
    url: `/education/group4/sessions/${sessionId}/page`,
    method: 'post',
    data: { pageNo, studentCanBrowse: false }
  }).then(unwrap)

export const joinSession = sessionId =>
  request({ url: `/education/group4/sessions/${sessionId}/join`, method: 'post' }).then(unwrap)

export const createActivity = data =>
  request({ url: '/education/group4/activities', method: 'post', data }).then(unwrap)

export const checkin = (activityId, idempotencyKey) =>
  request({
    url: `/education/group4/activities/${activityId}/checkin`,
    method: 'post',
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
  }).then(unwrap)

export const submitResponse = (activityId, answers, idempotencyKey) =>
  request({
    url: `/education/group4/activities/${activityId}/responses`,
    method: 'post',
    data: { answers },
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
  }).then(unwrap)

export const closeActivity = activityId =>
  request({ url: `/education/group4/activities/${activityId}/close`, method: 'post' })

export const publishResult = activityId =>
  request({ url: `/education/group4/activities/${activityId}/publish-result`, method: 'post' })

export const getStatistics = activityId =>
  request({
    url: `/education/group4/activities/${activityId}/statistics`,
    headers: { silent403: true }
  }).then(unwrap)

/** Fetch slide preview blob with auth; returns object URL or null. */
export async function fetchPreviewObjectUrl(previewUrl) {
  if (!previewUrl) return null
  try {
    const blob = await request({
      url: previewUrl,
      responseType: 'blob',
      headers: { silent403: true }
    })
    if (!(blob instanceof Blob) || blob.type.includes('json')) return null
    return URL.createObjectURL(blob)
  } catch {
    return null
  }
}
