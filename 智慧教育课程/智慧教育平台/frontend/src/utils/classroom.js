/** 课堂状态展示用常量。 */
export const STATUS_TEXT = { PLANNED: '未开始', RUNNING: '进行中', PAUSED: '已暂停', ENDED: '已结束' }

export const STATUS_TYPE = { PLANNED: 'info', RUNNING: 'success', PAUSED: 'warning', ENDED: 'info' }

export const KIND_TEXT = { ATTENDANCE: '签到', QUIZ: '随堂题', CLASSWORK: '课堂小作业' }

export function statusText(status) {
  return STATUS_TEXT[status] || '未知'
}

export function statusType(status) {
  return STATUS_TYPE[status] || 'info'
}
