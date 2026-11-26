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

const CLASS_ID_MAX = '9223372036854775807'

/**
 * 判断是否为不超过 Long.MAX_VALUE 的正整数字符串。
 *
 * 使用十进制字符串长度和同长度字典序比较，不经过 Number/parseInt，避免
 * 19 位 Long 在 JavaScript 中被舍入后再误判为合法。Long.MAX_VALUE 为
 * 9223372036854775807，19 位；更短的正整数必然不大于它。
 *
 * @param {string} value 待校验字符串
 * @returns {boolean} 是否为正 Long 范围内字符串
 */
export function isPositiveLongString(value) {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return false
  if (value.length !== 19) return value.length < 19
  return value <= CLASS_ID_MAX
}

/**
 * 校验教师 classId。
 *
 * 字符串保持原样，避免超过 Number.MAX_SAFE_INTEGER 的 Long 精度损失。
 * number 入参只接受安全正整数，拒绝已舍入的非安全整数；空值表示查询全部
 * 授权教学班，不把 bindingId 当 classId。
 *
 * @param {string|number|undefined|null} value 原始 classId
 * @returns {string} 空串或 Long 范围内正整数字符串
 * @throws {Error} classId 格式或 Long 上界非法
 */
function normalizeClassId(value) {
  if (value === undefined || value === null) return ''
  if (typeof value === 'number') {
    if (!Number.isSafeInteger(value) || value <= 0) {
      throw new Error('classId 必须是安全正整数或正 Long 字符串')
    }
    const fromNumber = String(value)
    if (!isPositiveLongString(fromNumber)) {
      throw new Error('classId 必须是正 Long 字符串，最大 9223372036854775807')
    }
    return fromNumber
  }
  const classId = String(value)
  if (classId && !isPositiveLongString(classId)) {
    throw new Error('classId 必须是正 Long 字符串，最大 9223372036854775807')
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

/**
 * 查询管理员管理总览。
 *
 * 只发送可选 from/to。组织范围由服务端认证上下文中的 deptId 决定，前端
 * 不发送 organizationId、deptId、userId、classId 或 bindingId；这些参数即使
 * 被误传也不会进入查询字符串。
 *
 * @param {{from?: string, to?: string}} [query] 查询参数
 * @param {{signal?: AbortSignal}} [options] 取消信号
 * @returns {Promise<{data: {asOf: string, window: object, items: Array<object>}}>} 总览响应
 */
export function getAdminOverview(query = {}, options = {}) {
  let params
  try {
    params = buildQuery(query, false)
  } catch (error) {
    return Promise.reject(error)
  }
  return edu({
    url: '/education/admin/overview',
    method: 'get',
    params,
    headers: { silent403: true },
    signal: options.signal
  })
}

/**
 * 查询管理员待办事项。
 *
 * 当前后端只返回 admin.todo.pending 的 NO_DATA 占位项；前端仍真实请求，
 * 不发送 organizationId/deptId 等客户端范围参数。合法 NO_DATA 和字段违约
 * 的区分由共享总览组件完成。
 *
 * @param {{from?: string, to?: string}} [query] 查询参数
 * @param {{signal?: AbortSignal}} [options] 取消信号
 * @returns {Promise<{data: {asOf: string, window: object, items: Array<object>}}>} 待办响应
 */
export function getAdminTodos(query = {}, options = {}) {
  let params
  try {
    params = buildQuery(query, false)
  } catch (error) {
    return Promise.reject(error)
  }
  return edu({
    url: '/education/admin/todos',
    method: 'get',
    params,
    headers: { silent403: true },
    signal: options.signal
  })
}
