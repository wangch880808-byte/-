<template>
  <div class="app-container">
    <el-alert v-if="course.error" :title="course.error" type="error" show-icon :closable="false" class="mb16" />
    <el-table v-else v-loading="!course.loaded" :data="rows" border>
      <el-table-column prop="courseTitle" label="课程" min-width="160" />
      <el-table-column prop="className" label="教学班" min-width="140" />
      <el-table-column prop="bindingId" label="绑定编号" min-width="120" />
      <el-table-column prop="teachingClassId" label="教学班编号" min-width="120" />
      <el-table-column prop="courseSpaceId" label="课程空间" min-width="120" />
      <el-table-column prop="role" label="身份" width="100" />
    </el-table>
    <el-empty v-if="course.loaded && !course.error && !rows.length" description="当前账号没有课程绑定" />
  </div>
</template>
<script setup>
import { computed, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { useCourseStore } from '@/store/course'

const API_ROLE = { teacher: 'TEACHER', student: 'STUDENT' }
const user = useUserStore()
const course = useCourseStore()
const rows = computed(() => course.forRole(API_ROLE[user.role]))
onMounted(() => course.ensure(API_ROLE[user.role]).catch(() => {}))
</script>
