<template>
  <div class="app-container">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb16" />
    <el-descriptions v-else-if="prep" :column="2" border title="课前准备">
      <el-descriptions-item label="课程">{{ course.current?.courseTitle || '—' }}</el-descriptions-item>
      <el-descriptions-item label="教学班">{{ course.current?.className || '—' }}</el-descriptions-item>
      <el-descriptions-item label="课件">{{ prep.resourceTitle || '—' }}</el-descriptions-item>
      <el-descriptions-item label="页数">{{ prep.totalPages ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="发布编号">{{ prep.releaseId ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="资源版本">{{ prep.resourceVersionId ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="课时">{{ prep.lessonId ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="备课方案">{{ prep.planId ?? '—' }}</el-descriptions-item>
    </el-descriptions>
    <el-empty v-else-if="!loading" description="当前教学班没有课前准备数据" />
  </div>
</template>
<script setup>
import { onMounted, ref, watch } from 'vue'
import { getClassroomPrep } from '@/api/education/classroom'
import { eduErrorText } from '@/utils/request'
import { useCourseStore } from '@/store/course'

const course = useCourseStore()
const prep = ref(null)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  prep.value = null
  try {
    await course.ensure('TEACHER')
    if (!course.current || course.current.role !== 'TEACHER') {
      error.value = '未找到任课教学班绑定'
      return
    }
    prep.value = await getClassroomPrep(course.current.bindingId)
  } catch (err) {
    error.value = eduErrorText(err)
  } finally {
    loading.value = false
  }
}

watch(() => course.bindingId, (id, prev) => { if (prev && id !== prev) load() })
onMounted(load)
</script>
