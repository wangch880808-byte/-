import edu from './http'

/**
 * 学生课程公告只读接口（6-04 学生公告页签）。
 *
 * 只封装列表和详情两个 GET。身份、班级范围、开放时间和可见性全部由
 * 服务端判定；ID 保持字符串，不发送 userId。
 */

/**
 * 去掉空字符串、null、undefined 查询参数。
 *
 * 未选择班级时省略 bindingId，表示本人所有在修班级的当前可见公告；
 * 传空串会与“未选择”语义混淆。
 *
 * @param {object} params 原始查询参数
 * @returns {object} 有效查询参数
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
 * 查询当前学生可见的课程公告列表。
 *
 * @param {{bindingId?: string, pageNum?: number, pageSize?: number}} [query] 查询条件；
 *   bindingId 为当前课程—教学班绑定 ID 字符串，无班级时省略
 * @param {{signal?: AbortSignal}} [options] 取消信号；切班/切页签时中止旧请求
 * @returns {Promise<{total: number, rows: Array<object>}>} 公告摘要分页响应
 */
export function listStudentAnnouncements(query = {}, options = {}) {
  return edu({
    url: '/education/student/announcements',
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
 * 查询单条课程公告详情。
 *
 * 详情服务端重新校验可见性；已撤回、已关闭、已退班或不存在统一按 404 处理。
 * 详情只返回公告正文和开放时间，不返回通知已读状态。
 *
 * @param {string} announcementId 公告 ID 字符串
 * @param {{signal?: AbortSignal}} [options] 取消信号；关闭/切换公告时中止旧请求
 * @returns {Promise<{data: {id: string, bindingId?: string, title: string,
 *   body: string, opensAt: string, closesAt?: string, createdAt: string}}>} 公告详情响应
 */
export function getStudentAnnouncement(announcementId, options = {}) {
  const id = String(announcementId ?? '')
  if (!/^[1-9]\d*$/.test(id)) {
    return Promise.reject(new Error('announcementId 必须为正整数字符串'))
  }
  return edu({
    url: '/education/student/announcements/' + encodeURIComponent(id),
    method: 'get',
    headers: { silent403: true },
    signal: options.signal
  })
}

/**
 * 查询教师公告列表。
 *
 * 教师公告必须带 bindingId；未选有效教学班时调用方不应发请求。
 * status 为空时省略；列表不返回正文。
 *
 * @param {{bindingId: string, status?: string, pageNum?: number, pageSize?: number}} query 查询条件
 * @param {{signal?: AbortSignal}} [options] 取消信号；切班/筛选/卸载时中止旧请求
 * @returns {Promise<{total: number, rows: Array<object>}>} 公告摘要分页响应
 */
export function listTeacherAnnouncements(query = {}, options = {}) {
  return edu({
    url: '/education/teacher/announcements',
    method: 'get',
    params: compactParams({
      bindingId: query.bindingId,
      status: query.status,
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 10
    }),
    headers: { silent403: true },
    signal: options.signal
  })
}

/**
 * 查询教师公告详情。
 *
 * 详情返回正文和 visibilityState 可见性快照；服务端会重新校验绑定、教学班和
 * 当前任课权限。列表只用作索引，详情才是正文事实源。
 *
 * @param {string} announcementId 公告 ID 字符串
 * @param {{signal?: AbortSignal}} [options] 取消信号
 * @returns {Promise<{data: {id: string, bindingId?: string, title: string,
 *   body: string, status: string, visibilityState: string, opensAt: string,
 *   closesAt?: string, createdAt: string}}>} 公告详情响应
 */
export function getTeacherAnnouncementDetail(announcementId, options = {}) {
  const id = String(announcementId ?? '')
  if (!/^[1-9]\d*$/.test(id)) {
    return Promise.reject(new Error('announcementId 必须为正整数字符串'))
  }
  return edu({
    url: '/education/teacher/announcements/' + encodeURIComponent(id),
    method: 'get',
    headers: { silent403: true },
    signal: options.signal
  })
}
