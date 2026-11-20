/** 第三组业务字典：与后端 Group3Constants 保持一致，避免魔法字符串散落各页面。 */
export const RELEASE_SOURCE = {
  STUDIO: 'STUDIO',
  PREPARE: 'PREPARE',
  BOTH: 'BOTH',
  /** 仅保存资料，不自动对学生发布 */
  NONE: 'NONE'
}

export const PROCESSING_STATUS = {
  PENDING: 'PENDING',
  READY: 'READY',
  FAILED: 'FAILED'
}

export const PROCESSING_LABELS = {
  [PROCESSING_STATUS.READY]: '可用',
  [PROCESSING_STATUS.PENDING]: '处理中',
  [PROCESSING_STATUS.FAILED]: '处理失败'
}
