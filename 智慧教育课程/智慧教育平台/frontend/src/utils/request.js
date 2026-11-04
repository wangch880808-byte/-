import axios from 'axios'
import { getToken } from './auth'

export function eduError(message, kind = 'business', code) {
  return Object.assign(new Error(message), { name: 'EduApiError', kind, code })
}

export function eduErrorText(error) {
  if (!error) return ''
  if (error.kind === 'unauthorized') return '登录已过期，请重新登录'
  if (error.kind === 'forbidden') return error.message || '你没有权限查看或操作该内容'
  if (error.kind === 'network') return '无法连接服务器，请检查后端服务和网络后重试'
  return error.message || '操作失败，请稍后重试'
}

export const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '',
  timeout: 20000,
  paramsSerializer: { indexes: null }
})

let sessionCheck = null

/** 仅在确认 /getInfo 也失败时才清登录态，避免单个业务接口误报 401 把人踢出。 */
function confirmSessionExpired(config) {
  if (!config || config.auth === false || config.skipSessionRedirect) return
  if (!getToken()) {
    window.dispatchEvent(new Event('zhijiao:session-expired'))
    return
  }
  if (!sessionCheck) {
    sessionCheck = service.get('/getInfo', { skipSessionRedirect: true })
      .then(body => {
        const code = body?.code === undefined ? 200 : Number(body.code)
        if (code === 401) window.dispatchEvent(new Event('zhijiao:session-expired'))
      })
      .catch(error => {
        if (error?.kind === 'unauthorized' || error?.response?.status === 401 || Number(error?.code) === 401) {
          window.dispatchEvent(new Event('zhijiao:session-expired'))
        }
      })
      .finally(() => { sessionCheck = null })
  }
}

function rejectResponse(code, message, config) {
  const kind = code === 401 ? 'unauthorized' : code === 403 ? 'forbidden' : 'business'
  if (code === 401) confirmSessionExpired(config)
  return Promise.reject(eduError(message || '请求失败', kind, code))
}

service.interceptors.request.use(config => {
  const token = getToken()
  if (token && config.auth !== false) config.headers.Authorization = 'Bearer ' + token
  return config
})

service.interceptors.response.use(response => {
  const body = response.data
  if (body instanceof Blob || body instanceof ArrayBuffer) return body
  if (!body || typeof body !== 'object') return Promise.reject(eduError('服务器返回了无法识别的数据，请检查接口地址'))
  const code = body.code === undefined ? 200 : Number(body.code)
  return code === 200 ? body : rejectResponse(code, body.msg, response.config)
}, error => {
  if (!error.response) return Promise.reject(eduError('无法连接服务器', 'network'))
  // 避免确认会话时的二次 getInfo 再走进错误分支形成环
  if (error.config?.skipSessionRedirect && error.response.status === 401) {
    return Promise.reject(eduError(error.response.data?.msg || '登录已过期', 'unauthorized', 401))
  }
  return rejectResponse(
    error.response.status,
    error.response.data?.msg || '服务异常（' + error.response.status + '）',
    error.config || {}
  )
})

export default function request(config) { return service(config) }
