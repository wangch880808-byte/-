<template>
 <el-button v-for="entry in entries" :key="entry.page.id" link class="right-menu-item" @click="open(entry.page)">
  {{entry.page.headerEntry.label}}<el-badge v-if="entry.count>0" :value="entry.count" :max="99" /><span v-if="entry.unavailable">（暂不可用）</span>
 </el-button>
</template>
<script setup>
import { pages } from '@/education/pages'
import { allowed } from '@/education/routes'
import { context } from '@/education/context'
import { educationRuntime } from '@/education/runtime'
import useUserStore from '@/store/modules/user'
import usePermissionStore from '@/store/modules/permission'
import request from '@/utils/request'
const user=useUserStore(),permissions=usePermissionStore(),router=useRouter(),entries=ref([])
let version=0,controller
async function refresh(){
 const run=++version;controller?.abort();controller=new AbortController();const signal=controller.signal
 entries.value=[]
 if(!user.token||educationRuntime.blocked||educationRuntime.pending)return
 const available=pages.filter(page=>page.headerEntry&&allowed(page,permissions.identity())&&(!page.requiresContext||(context.current&&(page.requiresContext==='course'||context.current.classId))))
 entries.value=available.map(page=>({page,count:null,unavailable:false}))
 await Promise.all(entries.value.map(async entry=>{
  if(!entry.page.headerEntry.getUnreadCount)return
  try{const count=await entry.page.headerEntry.getUnreadCount(context.current?Object.freeze({...context.current}):null,{signal,request:(url,options={})=>request({...options,url,method:'get',signal,headers:{silent403:true}})});if(signal.aborted||run!==version)return;if(!Number.isSafeInteger(count)||count<0)throw new Error('未读数无效');entry.count=count}
  catch(error){if(signal.aborted||run!==version)return;if(error.forbidden)entries.value=entries.value.filter(item=>item!==entry);else if(!error.cancelled&&!error.unauthorized)entry.unavailable=true}
 }))
}
function open(page){const query={};for(const key of ['courseSpaceId','classId','bindingId'])if(context.current?.[key])query[key]=context.current[key];router.push({path:'/'+user.role+'/'+page.path,query})}
watch(()=>[user.token,user.permissions.join(','),educationRuntime.epoch,educationRuntime.pending,pages.length,context.current],refresh,{immediate:true})
onMounted(()=>window.addEventListener('edu:refresh-header',refresh))
onBeforeUnmount(()=>{version++;controller?.abort();window.removeEventListener('edu:refresh-header',refresh)})
</script>
