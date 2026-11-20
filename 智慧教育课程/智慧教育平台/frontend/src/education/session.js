import { reactive } from 'vue'
import useUserStore from '@/store/modules/user'
import usePermissionStore from '@/store/modules/permission'
import { getToken, getRole } from '@/utils/auth'
import { getRouters } from '@/api/menu'
import { canAccess } from '@/education/core/access'
const verification=reactive({ready:false})
export const session=reactive({
 get ready(){return verification.ready&&!!getToken()&&useUserStore().token===getToken()},
 get role(){return useUserStore().role},get user(){return useUserStore().profile},
 get roles(){return useUserStore().roles},get permissions(){return useUserStore().permissions},
 get routes(){return usePermissionStore().backendRoutes}
})
let inflight=null
export function hasPermission(permission){return session.ready&&!!session.user&&(!permission||session.permissions.includes('*:*:*')||session.permissions.includes(permission))}
export function hasRole(role){return session.ready&&session.roles.includes(role)}
export function canOpen(page){return canAccess(page,session)}
export function resetSession(){verification.ready=false;inflight=null}
export async function bootstrap(){
 const token=getToken()
 if(!token){resetSession();throw Object.assign(new Error('请先登录'),{unauthorized:true})}
 if(inflight?.token===token)return inflight.promise
 verification.ready=false
 const promise=(async()=>{
  const [info,routes]=await Promise.all([useUserStore().getInfo(),getRouters()])
  if(token!==getToken())throw Object.assign(new Error('会话已变化'),{cancelled:true})
  if(!Array.isArray(routes.data))throw new Error('服务器路由响应不完整')
  usePermissionStore().backendRoutes=routes.data;verification.ready=true;return info
 })()
 inflight={token,promise}
 try{return await promise}finally{if(inflight?.promise===promise)inflight=null}
}
export async function login(payload){await useUserStore().login(payload);try{await bootstrap();return session}catch(error){if(!error.cancelled)useUserStore().clearIdentity();throw error}}
export async function logout(){await useUserStore().logOut();resetSession()}
