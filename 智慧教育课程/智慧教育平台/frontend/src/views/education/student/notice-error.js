/**
 * 学生通知/公告读取错误的统一分类。
 *
 * 6-02 契约：HTTP 200 + 业务 code=503 + 服务端 msg 明确指向授权，才归为
 * 教学班授权不可用；网关 HTTP 503 即使正文含糊提到“授权”也按通用服务故障
 * 处理，避免把基础设施故障误报成第 2 组授权契约问题。
 */

/**
 * 判定 503 是否来自教学班授权服务。
 *
 * @param {object} error 统一请求模块抛出的错误对象
 * @returns {'authorization'|'generic'} 授权 503 或通用 503
 */
export function classifyNotice503(error) {
  const httpStatus = Number(error?.status)
  const message = String(error?.message || '')
  return httpStatus === 200 && message.includes('授权') ? 'authorization' : 'generic'
}

/**
 * 503 详情文案。
 *
 * 优先保留服务端 msg；网关 HTML 503 没有业务 msg 时补充 HTTP 状态，
 * 方便定位是网关/容器故障而不是通知或公告空数据。
 *
 * @param {object} error 统一请求模块抛出的错误对象
 * @returns {string} 展示给用户的详情
 */
export function notice503Detail(error) {
  const message = typeof error?.message === 'string' ? error.message.trim() : ''
  if (message && message !== '请求失败') return message
  if (Number(error?.status) === 503) return '请求失败（HTTP 503）'
  return message || '请稍后重试。'
}
