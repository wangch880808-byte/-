<script setup>
import {defineAsyncComponent,computed} from 'vue'
import {session} from '@/education/session'
const Teacher=defineAsyncComponent(()=>import('./teacher/CourseList.vue'))
const Student=defineAsyncComponent(()=>import('./student/MyCourses.vue'))
const current=computed(()=>session.role==='teacher'?Teacher:Student)
</script>
<template><component :is="current" /></template>
