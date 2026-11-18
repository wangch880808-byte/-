// Compatibility API for the original views. Transport and identity use RuoYi.
import requestClient, { cancelRequests } from '@/utils/request'
import { getToken, getRole, setToken, setRole } from '@/utils/auth'
import useUserStore from '@/store/modules/user'
export { getToken, getRole, cancelRequests }
export class ApiError extends Error { constructor(message,extra={}){super(message);Object.assign(this,extra)} }
export function clearSession(){useUserStore().clearIdentity()}
export function setSession(token,role){setRole(role);setToken(token);const user=useUserStore();user.token=token;user.role=role}
export function request(path,options={}){
 const {body,scope:requestedScope,silent403,...config}=options
 const publicRequest=/^\/(captchaImage|login)(?:$|\?)/.test(path)
 const scope=requestedScope||(/^\/(captchaImage|login|getInfo|getRouters|logout)(?:$|\?)/.test(path)?'session':'page')
 return requestClient({...config,url:path,data:body,scope,headers:{...config.headers,...(publicRequest?{isToken:false}:{}),silent403:!!silent403}})
}
export const api={get:(url,options)=>request(url,{...options,method:'get'}),post:(url,body,options)=>request(url,{...options,method:'post',body}),put:(url,body,options)=>request(url,{...options,method:'put',body}),del:(url,options)=>request(url,{...options,method:'delete'})}
