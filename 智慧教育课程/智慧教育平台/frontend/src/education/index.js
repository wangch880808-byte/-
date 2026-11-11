import { registerBusinessPage } from '@/education/ui-pages'
import { resetSession } from '@/education/session'
import { suspendWorkspace,forbidView } from '@/education/workspace'
import usePermissionStore from '@/store/modules/permission'
window.EduVue=Object.freeze({registerPage:registerBusinessPage,refreshHeader:()=>window.dispatchEvent(new Event('edu:refresh-header'))})
window.addEventListener('edu:logout',()=>{resetSession();suspendWorkspace();usePermissionStore().resetRoutes()})
window.addEventListener('edu:forbidden',event=>forbidView(event.detail||'没有访问该资源的权限'))
window.addEventListener('pagehide',()=>{document.querySelector('.page')?.replaceChildren()})
window.addEventListener('pageshow',event=>{if(event.persisted)location.reload()})
window.addEventListener('unhandledrejection',event=>{if(event.reason?.cancelled||event.reason?.unauthorized||event.reason?.forbidden)event.preventDefault()})
