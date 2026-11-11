// The second-group API uses the first-group identity, cancellation and 401/403 handling.
import request from '@/utils/request'
export const service=request
export function eduError(message,kind='business',code){return Object.assign(new Error(message),{kind,code})}
export function eduErrorText(error){return error?.cancelled?'':error?.message||'操作失败，请稍后重试'}
export default async function edu(config){
 try{return await request(config)}catch(error){
  error.kind=error.unauthorized?'unauthorized':error.forbidden?'forbidden':error.cancelled?'cancelled':error.response||error.code?'business':'network'
  throw error
 }
}
