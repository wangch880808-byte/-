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
 * 查询单条通知详情。
 *
 * 只读请求；只返回通知元数据，不返回来源正文。
 * noticeId 必须是正整数字符串，路径段使用 encodeURIComponent，禁止 Number()
 * 转换，避免 19 位 ID 精度丢失。403 由页面展示，不让公共拦截器阻断工作台。
 *
 * TODO(6-04-notice-detail-read)：本轮只接详情 GET；来源页跳转和生产授权
 * 适配器不在范围。解除条件：来源路由确认且生产授权适配器完成联调。
 *
 * @param {string} noticeId 通知 ID 字符串
 * @param {{signal?: AbortSignal}} [options] 取消信号；关闭/切换详情时中止旧请求
 * @returns {Promise<{data: {id: string, kind: string, bindingId?: string,
 *   sourceId: string, title: string, visibleAt: string, readAt?: string,
 *   isRead: boolean, createdAt: string}}>} 详情响应体
 */
export function getNoticeDetail(noticeId, options = {}) {
  const id = String(noticeId ?? '')
  if (!/^[1-9]\d*$/.test(id)) {
    return Promise.reject(new Error('noticeId 必须为正整数字符串'))
  }
  return edu({
    url: '/education/student/notices/' + encodeURIComponent(id),
    method: 'get',
    headers: { silent403: true },
    signal: options.signal
  })
}

/**
 * 标记单条通知为已读。
 *
 * 幂等语义：通知仍可见时重复调用返回同一 readAt 且 alreadyRead=true；
 * 已退班或来源不可见时后端返回 404，不能复用旧 readAt。前端不本地减一，
 * 成功后统一重拉列表、未读数和顶栏角标。
 *
 * TODO(6-04-notice-read-source)：本轮只接单条/全部已读；来源页跳转和
 * 生产授权适配器不在范围。解除条件：来源路由确认并完成联调。
 *
 * @param {string} noticeId 通知 ID 字符串
 * @param {{signal?: AbortSignal}} [options] 取消信号；关闭/切换详情时中止旧写入
 * @returns {Promise<{data: {id: string, readAt: string, alreadyRead: boolean}}>} 已读响应体
 */
export function markNoticeRead(noticeId, options = {}) {
  const id = String(noticeId ?? '')
  if (!/^[1-9]\d*$/.test(id)) {
    return Promise.reject(new Error('noticeId 必须为正整数字符串'))
  }
  return edu({
    url: '/education/student/notices/' + encodeURIComponent(id) + '/read',
    method: 'post',
    headers: { silent403: true },
    signal: options.signal
  })
}

/**
 * 标记本人所有教学班当前可见的未读通知。
 *
 * 请求故意不带 bindingId、用户 ID 或请求体：范围由服务端按当前登录人和
 * 当前可见性裁剪，包含其他教学班，不受页面筛选和分页影响。
 * 幂等：重复调用返回 markedCount=0；markedCount=0 也是成功。
 *
 * TODO(6-04-notice-read-all-source)：来源页跳转和生产授权适配器不在范围。
 * 解除条件：来源路由确认且生产授权适配器完成联调。
 *
 * @param {{signal?: AbortSignal}} [options] 取消信号；切班/离开页面时中止旧请求
 * @returns {Promise<{data: {markedCount: number, unreadCount: number, operatedAt: string}}>} 全部已读响应体
 */
export function markAllNoticesRead(options = {}) {
  return edu({
    url: '/education/student/notices/read-all',
    method: 'put',
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
