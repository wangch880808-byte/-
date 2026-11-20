<script setup>
import {defineAsyncComponent,computed,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {context} from '@/education/context'
import {session} from '@/education/session'
const Teacher=defineAsyncComponent(()=>import('./teacher/CourseDetail.vue'))
const Student=defineAsyncComponent(()=>import('./student/CourseDetail.vue'))
const route=useRoute(),router=useRouter()
watch(()=>context.current?.courseSpaceId,id=>{if(id&&String(route.params.courseId)!==id)router.replace({path:'/'+session.role+'/courses/'+id,query:{courseSpaceId:id,...(context.current?.classId?{classId:context.current.classId,bindingId:context.current.bindingId}:{})}})})
const current=computed(()=>session.role==='teacher'?Teacher:Student)
</script>
<template><component :is="current" :key="route.params.courseId" /></template>
