import { reactive } from 'vue'
import {getRole as sessionRole} from '@/utils/auth'
import request from '@/utils/request'
import { createContextController } from './core/context'
import { resetView } from './runtime'
export const context=reactive({current:null,pending:null,courses:[],term:null,status:'idle',message:''})
function readSelection(){const q=new URLSearchParams(location.hash.split('?')[1]||'');return q.has('courseSpaceId')||q.has('classId')?Object.fromEntries(['courseSpaceId','classId','bindingId'].map(k=>[k,q.get(k)])):null}
function writeSelection(value){const [path,query='']=location.hash.slice(1).split('?');const p=new URLSearchParams(query);for(const k of ['courseSpaceId','classId','bindingId']){if(value?.[k])p.set(k,value[k]);else p.delete(k)}history.replaceState(history.state,'',location.pathname+location.search+'#'+(path||'/')+(p.size?'?'+p:''))}
export const contexts=createContextController({state:context,storage:sessionStorage,readSelection,writeSelection,
  request:(url,options)=>request({url,method:'get',signal:options?.signal,params:{role:sessionRole()},headers:{silent403:true}}),onChange:()=>resetView()})
let identity=''
export async function prepareContext(user,force=false,selection=readSelection()){const key=user.id+':'+user.role,desired=selection&&['courseSpaceId','classId'].some(k=>selection[k])?Object.fromEntries(['courseSpaceId','classId','bindingId'].map(k=>[k,selection[k]||null])):null;const changed=desired&&['courseSpaceId','classId','bindingId'].some(k=>desired[k]&&desired[k]!==context.current?.[k]);if(force||changed||identity!==key||context.status==='idle'){identity=key;await contexts.initialize({userId:String(user.id),role:user.role},desired)}}
export function resetEducation(){contexts.suspend();identity='';resetView()}
