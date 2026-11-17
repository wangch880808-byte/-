import edu from './http'

/**
 * 学生通知只读接口（6-04 最小接入）。
 *
 * 职责：只封装学生本人通知列表和未读数两个 GET 请求。
 * 身份、班级范围、可见性和已读状态全部由服务端判定；本模块不发送 userId。
 * ID 保持字符串，不做 Number() 转换，避免 Long 精度丢失。
 */

/**
 * 去掉空字符串、null、undefined 查询参数。
 *
 * 原因：学生通知允许不传 bindingId 表示本人全部班级；
 * 传空串会被后端按非法 bindingId 处理，与“未选班级”语义不同。
 *
 * @param {object} params 原始查询参数
 * @returns {object} 仅保留有效值的查询参数
 */
function compactParams(params) {
  const result = {}
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value
    }
  }
  return result
}

/**
 * 查询当前学生可见的通知列表。
 *
 * 只读请求；不包含详情、已读或全部已读操作。
 * 使用 silent403：403 由页面展示“无权限”，不能让公共拦截器把整个工作台置为阻断。
 *
 * @param {{bindingId?: string, pageNum?: number, pageSize?: number}} [query] 查询条件；
 *   bindingId 为课程—教学班绑定 ID 字符串，未选班级时省略
 * @param {{signal?: AbortSignal}} [options] 取消信号；切班时由调用方中止旧请求
 * @returns {Promise<{total: number, rows: Array<object>}>} 分页响应体；rows 为服务端裁剪后的通知
 */
export function listNotices(query = {}, options = {}) {
  return edu({
    url: '/education/student/notices',
    method: 'get',
    params: compactParams({
      bindingId: query.bindingId,
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 10
    }),
    headers: { silent403: true },
    signal: options.signal
  })
}

/**
 * 查询当前学生未读通知数。
 *
 * 只读请求；失败时调用方必须显示“暂不可用”，不能用 0 冒充真实结果。
 * 未选班级时省略 bindingId，表示本人全部当前可见班级。
 *
 * @param {string} [bindingId] 课程—教学班绑定 ID 字符串
 * @param {{signal?: AbortSignal}} [options] 取消信号
 * @returns {Promise<{data: {unreadCount: number, asOf: string}}>} 未读数响应体
 */
export function getUnreadCount(bindingId, options = {}) {
  return edu({
    url: '/education/student/notices/unread-count',
    method: 'get',
    params: compactParams({ bindingId }),
    headers: { silent403: true },
    signal: options.signal
  })
}

/**
 * 顶栏未读角标读取器。
 *
 * 由 AppLayout 的 headerEntry 调用。优先使用 AppLayout 传入的 request，
 * 以继承 header scope、silent403、401 统一处理和取消信号；不能直接复用
 * 页面请求的 page scope，否则未读数 403 会触发全局阻断。
 *
 * 返回值必须是有限非负整数；403、503、网络错误或字段不完整时抛出，
 * 由 AppLayout 决定移除入口或显示“暂不可用”，不得返回 0 兜底。
 *
 * @param {{bindingId?: string}|null} contextInfo 当前课程—教学班上下文
 * @param {{signal?: AbortSignal, request?: Function}} [options] AppLayout 提供的请求器与取消信号
 * @returns {Promise<number>} 当前未读数
 */
export async function getUnreadCountForHeader(contextInfo, options = {}) {
  const bindingId = contextInfo?.bindingId ? String(contextInfo.bindingId) : undefined
  const params = compactParams({ bindingId })
  const request = options.request
  const body = typeof request === 'function'
    ? await request('/education/student/notices/unread-count', { params, signal: options.signal })
    : await getUnreadCount(bindingId, options)
  const count = body?.data?.unreadCount
  if (!Number.isSafeInteger(count) || count < 0) {
    throw new Error('未读数响应不完整')
  }
  return count
}
