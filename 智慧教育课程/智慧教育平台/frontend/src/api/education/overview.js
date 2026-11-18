import edu from './http'

/**
 * 6-03 教师/学生总览接口。
 *
 * 身份由服务端认证上下文取得，本模块不发送 userId、studentId、organizationId。
 * 教师 classId 是可选教学班过滤，必须保持字符串，不能混用上下文 bindingId；
 * 学生端点只接收 from/to。时间窗由服务端最终解释，前端只做成对和顺序校验。
 */

/**
 * 校验并整理 from/to。
 *
 * 两个都不传表示使用服务端默认 UTC 自然日；只传一个、格式错误或开始时间
 * 不早于结束时间时拒绝请求，避免把非法窗口发给后端。
 *
 * @param {{from?: string, to?: string}} query 查询参数
 * @returns {{from: string, to: string}|null} 规范化时间参数；都不传时为 null
 */
function buildWindow(query) {
  const from = query?.from === undefined || query?.from === null ? '' : String(query.from)
  const to = query?.to === undefined || query?.to === null ? '' : String(query.to)
  if (!from && !to) return null
  if (!from || !to) {
    throw new Error('from 和 to 必须同时提供')
  }
  const fromMs = Date.parse(from)
  const toMs = Date.parse(to)
  if (Number.isNaN(fromMs) || Number.isNaN(toMs) || fromMs >= toMs) {
    throw new Error('from 必须早于 to')
  }
  return { from, to }
}

/**
 * 校验教师 classId。
 *
 * 只接受正整数字符串，保留原始字符串，避免超过 Number.MAX_SAFE_INTEGER 的
 * Long 精度损失。空值表示查询全部授权教学班，不把 bindingId 当 classId。
 *
 * @param {string|number|undefined|null} value 原始 classId
 * @returns {string} 空串或正整数字符串
 */
function normalizeClassId(value) {
  const classId = value === undefined || value === null ? '' : String(value)
  if (classId && !/^[1-9]\d*$/.test(classId)) {
    throw new Error('classId 必须是正整数字符串')
  }
  return classId
}

function buildQuery(query, withClassId) {
  const window = buildWindow(query)
  const params = {}
  if (window) {
    params.from = window.from
    params.to = window.to
  }
  if (withClassId) {
    const classId = normalizeClassId(query?.classId)
    if (classId) params.classId = classId
  }
  return params
}

/**
 * 查询教师教学总览。
 *
 * 只发送 from、to、classId。classId 仅用于教师端点，不能与实际页面上下文中的
 * bindingId 互换；服务端会校验 classId 是否属于当前教师的授权范围。
 *
 * @param {{from?: string, to?: string, classId?: string|number}} [query] 查询参数
 * @param {{signal?: AbortSignal}} [options] 取消信号
 * @returns {Promise<{data: {asOf: string, window: object, items: Array<object>}}>} 总览响应
 */
export function getTeacherOverview(query = {}, options = {}) {
  let params
  try {
    params = buildQuery(query, true)
  } catch (error) {
    return Promise.reject(error)
  }
  return edu({
    url: '/education/teacher/overview',
    method: 'get',
    params,
    headers: { silent403: true },
    signal: options.signal
  })
}

/**
 * 查询学生学习总览 / 今日待办。
 *
 * 只发送 from、to。学生端点不接收 classId、bindingId、date 或任何身份参数；
 * 即使调用方误传，也不会进入查询字符串。当前课程/班级上下文不能偷偷变成
 * 总览筛选条件。
 *
 * @param {{from?: string, to?: string}} [query] 查询参数
 * @param {{signal?: AbortSignal}} [options] 取消信号
 * @returns {Promise<{data: {asOf: string, window: object, items: Array<object>}}>} 总览响应
 */
export function getStudentTodayTodos(query = {}, options = {}) {
  let params
  try {
    params = buildQuery(query, false)
  } catch (error) {
    return Promise.reject(error)
  }
  return edu({
    url: '/education/student/today-todos',
    method: 'get',
    params,
    headers: { silent403: true },
    signal: options.signal
  })
}
