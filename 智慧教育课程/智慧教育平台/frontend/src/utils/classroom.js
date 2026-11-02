/** 课堂状态展示与活动辅助。 */

export const STATUS_TEXT = { PLANNED: '未开始', RUNNING: '进行中', PAUSED: '已暂停', ENDED: '已结束', OPEN: '进行中', CLOSED: '已关闭' }

export const STATUS_TYPE = { PLANNED: 'info', RUNNING: 'success', PAUSED: 'warning', ENDED: 'info', OPEN: 'success', CLOSED: 'info' }

export const KIND_TEXT = { ATTENDANCE: '签到', QUIZ: '随堂题', CLASSWORK: '课堂小作业' }

export function statusText(status) {
  return STATUS_TEXT[status] || status || '未知'
}

export function statusType(status) {
  return STATUS_TYPE[status] || 'info'
}

/** 写操作幂等键：同一次点击重试用同一键，避免重复有效提交。 */
export function newIdempotencyKey() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function formatAnswer(answer) {
  if (answer == null) return '—'
  if (typeof answer === 'string' || typeof answer === 'number') return String(answer)
  if (Array.isArray(answer)) return answer.join('、')
  if (typeof answer === 'object') {
    if (answer.value != null) return formatAnswer(answer.value)
    if (answer.correctOption != null) return formatAnswer(answer.correctOption)
    if (answer.options != null) return formatAnswer(answer.options)
    try { return JSON.stringify(answer) } catch { return '—' }
  }
  return String(answer)
}

/** 倒计时仅展示；截止以服务端 closesAt / serverTime 为准。 */
export function remainingSeconds(closesAt, serverTime, now = Date.now()) {
  if (!closesAt) return null
  const end = Date.parse(closesAt)
  if (Number.isNaN(end)) return null
  const skew = serverTime ? Date.parse(serverTime) - Date.now() : 0
  const left = Math.floor((end - (now + (Number.isNaN(skew) ? 0 : skew))) / 1000)
  return left
}

export function formatCountdown(seconds) {
  if (seconds == null) return ''
  if (seconds <= 0) return '已截止'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

export function quizKind(question) {
  const kind = String(question?.questionKind || question?.kind || 'SINGLE').toUpperCase()
  if (kind.includes('MULTI') || kind.includes('MULTIPLE')) return 'MULTI'
  if (kind.includes('TRUE') || kind.includes('JUDGE') || kind.includes('BOOL')) return 'JUDGE'
  return 'SINGLE'
}
