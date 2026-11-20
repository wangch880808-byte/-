import router, { constantRoutes, dynamicRoutes } from '@/router'
import { getRouters } from '@/api/menu'
import useUserStore from './user'
import auth from '@/plugins/auth'
import { buildEducationRoutes, businessGroup, businessRoute, allowed } from '@/education/routes'
import { pages } from '@/education/pages'
const usePermissionStore=defineStore('permission',{
 state:()=>({routes:[],addRoutes:[],defaultRoutes:[],topbarRouters:[],sidebarRouters:[],backendRoutes:[]}),
 actions:{
  identity(){const user=useUserStore();return{ready:true,user:{userId:user.id},role:user.role,roles:user.roles,permissions:user.permissions,routes:this.backendRoutes}},
  async generateRoutes(isCurrent=()=>true){
   const response=await getRouters();if(!isCurrent())throw Object.assign(new Error('旧路由查询已取消'),{cancelled:true});this.resetRoutes();this.backendRoutes=Array.isArray(response.data)?response.data:[]
   const user=useUserStore(), routes=buildEducationRoutes(this.identity())
   const utility=dynamicRoutes.filter(route=>['/system/user-auth','/system/role-auth'].includes(route.path)&&user.role==='admin'&&route.permissions?.some(p=>auth.hasPermi(p)))
   this.addRoutes=[...routes,...utility];this.routes=[...constantRoutes,...this.addRoutes];this.sidebarRouters=routes;this.defaultRoutes=routes;this.topbarRouters=routes
   for(const page of pages)this.installBusinessPage(page,false)
   return this.addRoutes
  },
  resetRoutes(){for(const route of this.addRoutes)if(route.name&&router.hasRoute(route.name))router.removeRoute(route.name);this.routes=[];this.addRoutes=[];this.sidebarRouters=[];this.defaultRoutes=[];this.topbarRouters=[];this.backendRoutes=[]},
  installBusinessPage(page,install=true){
   const role=useUserStore().role;if(!page.roles.includes(role)||!allowed(page,this.identity()))return
   let group=this.sidebarRouters.find(group=>group.children?.some(child=>child.path==='/'+role+'/'+page.path))
   if(group){const route=businessRoute(page,role),index=group.children.findIndex(child=>child.path===route.path);const old=group.children[index];if(install&&old.name&&router.hasRoute(old.name))router.removeRoute(old.name);group.children.splice(index,1,route);if(install)router.addRoute(group.name,route)}
   else{group=businessGroup(page,role);this.addRoutes.push(group);this.sidebarRouters.push(group);if(install)router.addRoute(group)}
  }
 }
})
export default usePermissionStore
