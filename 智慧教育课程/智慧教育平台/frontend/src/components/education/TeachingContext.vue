<template>
 <div v-if="user.role!=='admin'" class="teaching-context">
  <span>{{ context.term?.termName || '课程与教学班' }}</span>
  <el-select aria-label="当前课程" :model-value="context.current?.courseSpaceId || context.pending?.courseSpaceId" placeholder="请选择课程" clearable :disabled="loading" @change="course">
   <el-option v-for="item in context.courses" :key="item.courseSpaceId" :value="item.courseSpaceId" :label="item.title" />
  </el-select>
  <el-select aria-label="当前教学班" :model-value="context.current?.classId" placeholder="请选择教学班" clearable :disabled="loading || !current" @change="klass">
   <el-option v-for="item in current?.classes || []" :key="item.classId" :value="item.classId" :label="item.className" />
  </el-select>
  <span role="status">{{context.message}}</span><el-button v-if="['error','unavailable'].includes(context.status)" link type="primary" @click="prepareContext(user,true)">重试</el-button>
 </div>
</template>
<script setup>
import useUserStore from '@/store/modules/user'
import {context,contexts,prepareContext} from '@/education/context'
const user=useUserStore()
const loading=computed(()=>['loading','checking'].includes(context.status))
const current=computed(()=>context.courses.find(item=>item.courseSpaceId===context.current?.courseSpaceId))
function course(id){id?contexts.select(id):contexts.clear()}
function klass(id){contexts.select(context.current?.courseSpaceId,id)}
</script>
<style scoped>.teaching-context{display:flex;gap:12px;align-items:center;flex-wrap:wrap;padding:16px 20px;background:white;border-bottom:1px solid #eee}.el-select{width:220px}.teaching-context [role=status]{font-size:13px;color:#606266}</style>
