import { markRaw } from 'vue'
import Layout from '@/layout'
import { menuByRole } from './navigation'
import { canAccess } from './core/access'
import { pages } from './pages'
const blank=()=>import('@/views/education/BlankPage.vue')
const builtins={users:['system:user:list','system/user',()=>import('@/views/system/user/index.vue')],roles:['system:role:list','system/role',()=>import('@/views/system/role/index.vue')],menus:['system:menu:list','system/menu',()=>import('@/views/system/menu/index.vue')],depts:['system:dept:list','system/dept',()=>import('@/views/system/dept/index.vue')],logs:['monitor:operlog:list','monitor/operlog',()=>import('@/views/monitor/operlog/index.vue')]}
const groupKeys=['workbench','academic','ability','learning','ai','infrastructure','system']
const icons=['dashboard','tree','education','user','example','monitor','system']
export function buildEducationRoutes(identity){
 if(identity.role!=='admin')return[{path:'/'+identity.role,component:markRaw(Layout),alwaysShow:true,name:'Edu'+identity.role,meta:{title:identity.role==='teacher'?'教学工作台':'学习工作台',icon:'education'},children:[{path:'overview',name:'Edu'+identity.role+'Overview',component:blank,meta:{title:identity.role==='teacher'?'教学总览':'学习总览',noCache:true,eduRole:identity.role,placeholder:true}}]}]
 return menuByRole.admin.map((group,index)=>({path:'/admin/group-'+groupKeys[index],name:'EduGroup'+index,component:markRaw(Layout),alwaysShow:true,redirect:'noRedirect',meta:{title:group.title,icon:icons[index]},children:group.items.map(item=>{
  const actual=builtins[item.path],extension=pages.find(page=>page.roles.includes('admin')&&page.path===item.path)
  const page=extension||(actual?{id:item.id,path:item.path,title:item.title,roles:['admin'],permission:actual[0],serviceRoute:actual[1],component:actual[2]}:null)
  return{path:'/admin/'+item.path,name:'EduAdmin-'+item.id,component:page?page.component:blank,meta:{title:item.title,noCache:true,eduRole:'admin',page:page||null,placeholder:!page,affix:item.path==='overview'}}
 })}))
}
export function allowed(page,identity){return canAccess(page,identity)}
export function businessRoute(page,role){return{path:'/'+role+'/'+page.path,name:'EduBusiness-'+role+'-'+page.id,component:page.component,meta:{title:page.title,noCache:true,eduRole:role,page}}}
export function businessGroup(page,role){return{path:'/'+role+'/group-'+page.id,name:'EduExtension-'+role+'-'+page.id,component:markRaw(Layout),alwaysShow:true,meta:{title:page.group||'工作台',icon:'education'},children:[businessRoute(page,role)]}}
