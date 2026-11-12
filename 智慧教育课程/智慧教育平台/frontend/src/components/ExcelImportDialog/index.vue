<template>
  <el-dialog v-model="visible" :title="title" width="500px" append-to-body @closed="reset">
    <el-alert :closable="false" type="info" :title="hint" />
    <el-upload ref="uploadRef" :limit="1" accept=".xlsx,.xls" :auto-upload="false" :disabled="busy" :on-change="select" :on-remove="remove" drag>
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖入文件或点击选择 Excel 文件</div>
      <template #tip><div class="el-upload__tip">仅支持 .xls/.xlsx，最大 10 MB，每次最多 1000 行。仅新增，不覆盖已有数据。</div></template>
    </el-upload>
    <el-button v-if="templateAction" link type="primary" :disabled="busy" @click="template">下载导入模板</el-button>
    <el-alert v-if="result" :title="result" :type="failed ? 'error' : 'success'" :closable="false" class="import-result" />
    <template #footer><el-button @click="visible=false">关闭</el-button><el-button type="primary" :loading="busy" @click="submit">开始导入</el-button></template>
  </el-dialog>
</template>
<script setup>
import request, { download } from '@/utils/request'
import { ElMessage } from 'element-plus'
const props=defineProps({title:String,action:{type:String,required:true},templateAction:String,templateFileName:{type:String,default:'template'},hint:{type:String,default:'请使用模板填写数据。用户初始密码按系统配置生成，角色需另行分配。'}})
const emit=defineEmits(['success'])
const visible=ref(false),busy=ref(false),file=shallowRef(null),result=ref(''),failed=ref(false),uploadRef=ref()
let controller
function reset(){controller?.abort();busy.value=false;file.value=null;result.value='';uploadRef.value?.clearFiles()}
function open(){reset();visible.value=true}
function select(value){file.value=value.raw;result.value=''}
function remove(){file.value=null}
async function template(){try{await download(props.templateAction,{},props.templateFileName+'.xlsx')}catch{/* 请求客户端已显示错误。 */}}
async function submit(){
 if(!file.value||!/\.(xls|xlsx)$/i.test(file.value.name)){ElMessage.warning('请选择 .xls 或 .xlsx 文件');return}
 if(file.value.size>10*1024*1024){ElMessage.warning('文件不能超过 10 MB');return}
 const form=new FormData();form.append('file',file.value);form.append('updateSupport','false')
 controller=new AbortController();busy.value=true;failed.value=false;result.value=''
 try{const response=await request({url:props.action,method:'post',data:form,signal:controller.signal,timeout:120000});if(!visible.value)return;result.value=String(response.msg||'导入成功').replace(/<br\s*\/?\s*>/gi,'\n').replace(/<[^>]+>/g,'');emit('success');file.value=null;uploadRef.value?.clearFiles()}
 catch(error){if(!error.cancelled&&visible.value){failed.value=true;result.value=error.message;emit('success')}}finally{busy.value=false}
}
onBeforeUnmount(reset)
defineExpose({open})
</script>
<style scoped>.import-result{margin-top:16px;white-space:pre-wrap;overflow-wrap:anywhere}</style>
