import router from '@/router'
import { getToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import { bootstrap,canOpen,session } from '@/education/session'
import { prepareContext,resetView,workspace } from '@/education/workspace'
let navigation=0
const versions=new WeakMap()
router.beforeEach(async to=>{
 const run=++navigation;versions.set(to,run);const token=getToken();const current=()=>run===navigation&&token===getToken()
 workspace.pending=true;resetView()
 if(!token)return to.meta.public?true:{path:'/login',query:{redirect:to.fullPath}}
 try{
  await bootstrap();if(!current())return
  if(to.meta.public)return '/'+session.role
  if(to.name==='course-detail'&&String(to.query.courseSpaceId||'')!==String(to.params.courseId))return{path:to.path,query:{courseSpaceId:to.params.courseId}}
  if(to.params.role!==session.role)return '/'+session.role
  if(to.meta.adminPlaceholder&&session.role!=='admin')return '/'+session.role
  if(to.name==='home'&&session.role==='admin')return '/admin/overview'
  if(to.meta.page&&!canOpen(to.meta.page))return{path:'/'+session.role,query:{denied:'1'}}
  await prepareContext(false,to.query);if(!current())return
  return true
 }catch(error){
  if(run!==navigation||(getToken()&&token!==getToken()))return
  if(error.cancelled)return false
  useUserStore().clearIdentity();return to.meta.public?true:{path:'/login',query:{logout:'expired'}}
 }
})
router.afterEach(to=>{if(versions.get(to)===navigation){workspace.pending=false;if(to.query.denied)resetView('该页面未开放或没有访问权限')}})
router.onError((_error,to)=>{if(versions.get(to)===navigation)workspace.pending=false})
