<script setup>
import {computed,reactive,ref} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import edu from '@/api/education/http'
const props=defineProps({course:{type:Object,required:true}})
const emit=defineEmits(['saved'])
const opened=ref(false),dialog=ref(false),busy=ref(false),isPoint=ref(false)
const form=reactive({})
const units=computed(()=>props.course.units||[])
const flatUnits=computed(()=>units.value.flatMap(u=>[u,...(u.children||[])]))
function edit(row,point=false,parent=null){
 isPoint.value=point
 Object.keys(form).forEach(k=>delete form[k])
 Object.assign(form,point?{id:null,code:'',name:'',unitId:null}:{id:null,title:'',unitKind:parent?'SECTION':'CHAPTER',parentUnitId:parent,sortOrder:0},row||{})
 dialog.value=true
}
async function save(){
 if(isPoint.value?(!form.code?.trim()||!form.name?.trim()):!form.title?.trim()){ElMessage.warning('请填写必填内容');return}
 busy.value=true
 try{
  const part=isPoint.value?'points':'units'
  await edu({url:`/education/course/${props.course.id}/outline/${part}${form.id?'/'+form.id:''}`,method:form.id?'put':'post',data:{...form}})
  dialog.value=false;emit('saved');ElMessage.success('已保存')
 }catch(e){if(!e.cancelled)ElMessage.error(e.message)}finally{busy.value=false}
}
async function remove(row,point=false){
 try{await ElMessageBox.confirm('删除后无法恢复；已有教学业务引用时会阻止删除。','删除确认',{type:'warning'})}catch{return}
 try{await edu({url:`/education/course/${props.course.id}/outline/${point?'points':'units'}/${row.id}`,method:'delete'});emit('saved');ElMessage.success('已删除')}
 catch(e){if(!e.cancelled)ElMessage.error(e.message)}
}
</script>
<template>
 <el-button size="small" type="primary" plain @click="opened=true">维护章节与知识点</el-button>
 <el-drawer v-model="opened" title="课程目录管理" size="760px" append-to-body destroy-on-close>
  <el-button type="primary" @click="edit(null)">新增章</el-button>
  <el-table :data="units" row-key="id" default-expand-all style="margin:12px 0 24px">
   <el-table-column prop="title" label="章节名称" min-width="200" />
   <el-table-column prop="sortOrder" label="顺序" width="70" />
   <el-table-column label="操作" width="210"><template #default="{row}">
    <el-button v-if="row.unitKind==='CHAPTER'" link type="primary" @click="edit(null,false,row.id)">新增节</el-button>
    <el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button>
   </template></el-table-column>
  </el-table>
  <el-button type="primary" @click="edit(null,true)">新增知识点</el-button>
  <el-table :data="course.knowledgePoints||[]" style="margin-top:12px">
   <el-table-column prop="code" label="编码" /><el-table-column prop="name" label="知识点名称" />
   <el-table-column label="操作" width="130"><template #default="{row}"><el-button link type="primary" @click="edit(row,true)">编辑</el-button><el-button link type="danger" @click="remove(row,true)">删除</el-button></template></el-table-column>
  </el-table>
 </el-drawer>
 <el-dialog v-model="dialog" :title="(form.id?'编辑':'新增')+(isPoint?'知识点':form.unitKind==='CHAPTER'?'章':'节')" width="520px" append-to-body>
  <el-form label-width="95px">
   <template v-if="isPoint">
    <el-form-item label="编码" required><el-input v-model="form.code" maxlength="60" /></el-form-item>
    <el-form-item label="名称" required><el-input v-model="form.name" maxlength="160" /></el-form-item>
    <el-form-item label="所属章节"><el-select v-model="form.unitId" clearable placeholder="可不关联章节"><el-option v-for="u in flatUnits" :key="u.id" :label="u.title" :value="u.id" /></el-select></el-form-item>
   </template>
   <template v-else>
    <el-form-item label="标题" required><el-input v-model="form.title" maxlength="160" /></el-form-item>
    <el-form-item v-if="form.unitKind==='SECTION'" label="所属章" required><el-select v-model="form.parentUnitId"><el-option v-for="u in units" :key="u.id" :label="u.title" :value="u.id" /></el-select></el-form-item>
    <el-form-item label="显示顺序"><el-input-number v-model="form.sortOrder" :min="0" :max="2147483647" :precision="0" /></el-form-item>
   </template>
  </el-form>
  <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="busy" @click="save">保存</el-button></template>
 </el-dialog>
</template>
