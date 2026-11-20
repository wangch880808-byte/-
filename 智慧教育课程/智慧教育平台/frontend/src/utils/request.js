import axios from 'axios'
import { ElMessage, ElLoading } from 'element-plus'
import { getToken,getRole } from '@/utils/auth'
import { tansParams } from '@/utils/ruoyi'
import { saveAs } from 'file-saver'
import useUserStore from '@/store/modules/user'
import router from '@/router'
import { educationRuntime } from '@/education/runtime'
export const isRelogin = { show: false }
const pending=new Map()
export function cancelRequests(scope){for(const [controller,kind]of pending)if(!scope||scope===kind)controller.abort()}
function cleanup(config){if(!config)return;pending.delete(config.cancelController);config.originalSignal?.removeEventListener('abort',config.abortListener)}
const service = axios.create({ baseURL: import.meta.env.VITE_APP_BASE_API, timeout: 15000 })
service.interceptors.request.use(config => {
  if (/^[a-z][a-z\d+.-]*:/i.test(config.url || '') || /^(?:\/\/|\\)/.test(config.url || '')) throw new Error('请求必须使用平台内部接口路径')
  config.url='/' + String(config.url || '').replace(/^\/+/, '')
  if(config.url.startsWith('/education/'))config.headers['X-Edu-Role']=getRole()
  const sessionRequest=/^\/(getInfo|getRouters|login|logout|captchaImage)(?:$|\?)/.test(config.url)
  config.scope=config.scope||(sessionRequest?'session':'page')
  if(config.scope==='page')config.viewEpoch=educationRuntime.epoch
  const controller=new AbortController();config.cancelController=controller;pending.set(controller,config.scope)
  const signal=config.signal
  if(signal){config.originalSignal=signal;config.abortListener=()=>controller.abort();if(signal.aborted)controller.abort();else signal.addEventListener('abort',config.abortListener,{once:true})}
  config.signal=controller.signal
  if (config.headers.isToken !== false) {
    config.identityToken = getToken() || ''
    if (config.identityToken) config.headers.Authorization = 'Bearer ' + config.identityToken
  }
  if (config.data instanceof FormData) config.headers['Content-Type'] = undefined
  return config
})
function rejectResponse(code, message, config, status) {
  const error = Object.assign(new Error(message || '请求失败'), { code, status, unauthorized: code === 401, forbidden: code === 403 })
  if (code === 401) {
    educationRuntime.blocked = true
    useUserStore().clearIdentity()
    router.replace({ path: '/login', query: { logout: 'expired' } })
  } else if (code === 403 && !config?.headers?.silent403) {
    educationRuntime.blocked = true
    window.dispatchEvent(new CustomEvent('edu:forbidden',{detail:message||'没有访问该资源的权限'}))
  } else if (!config?.headers?.silent403) ElMessage.error(error.message)
  throw error
}
async function decode(response) {
  const config = response.config
  if (config.identityToken !== undefined && config.identityToken !== (getToken() || '') || config.viewEpoch !== undefined && config.viewEpoch !== educationRuntime.epoch) {
    throw Object.assign(new Error('登录身份已变化，已取消旧请求'), { cancelled: true })
  }
  let body = response.data
  if (body instanceof Blob) {
    if (/json|text\//i.test(body.type)) {
      try { body = JSON.parse(await body.text()) } catch { return rejectResponse(500, '下载响应格式不正确', config, response.status) }
    } else return body
  }
  if (config.identityToken !== undefined && config.identityToken !== (getToken() || '') || config.viewEpoch !== undefined && config.viewEpoch !== educationRuntime.epoch) throw Object.assign(new Error('页面或登录身份已变化'), {cancelled:true})
  const code = Number(body?.code ?? 200)
  if (code !== 200) return rejectResponse(code, body?.msg, config, response.status)
  return body
}
service.interceptors.response.use(async response=>{try{return await decode(response)}finally{cleanup(response.config)}}, async error => {
  cleanup(error.config)
  if (axios.isCancel(error)) throw Object.assign(error, { cancelled: true })
  if (error.response) return decode(error.response).then(body => rejectResponse(error.response.status, body?.msg, error.config, error.response.status))
  if (!error.config?.headers?.silent403) ElMessage.error(error.code === 'ECONNABORTED' ? '请求超时，请重试' : '后端接口连接异常')
  throw error
})
export async function download(url, params, filename, config = {}) {
  const loading = ElLoading.service({ text: '正在下载数据，请稍候' })
  const identityToken = getToken()
  try {
    const data = await service.post(url, params, {
      transformRequest: [value => tansParams(value)],
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, responseType: 'blob', ...config
    })
    if (!identityToken || identityToken !== getToken()) throw Object.assign(new Error('登录身份已变化'), { cancelled: true })
    if (!(data instanceof Blob)) throw new Error('接口未返回文件')
    saveAs(data, filename)
  } finally { loading.close() }
}
export default service
