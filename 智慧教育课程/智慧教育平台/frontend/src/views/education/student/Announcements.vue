<template>
  <div class="app-container">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb16" />
    <el-table v-loading="loading" :data="rows" border @row-click="open">
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column prop="opensAt" label="开始" min-width="180" />
      <el-table-column prop="closesAt" label="结束" min-width="180" />
      <el-table-column prop="createdAt" label="发布时间" min-width="180" />
    </el-table>
    <pagination :total="total" :page="query.pageNum" :limit="query.pageSize"
      @update:page="query.pageNum = $event" @update:limit="query.pageSize = $event" @pagination="load" />
    <el-drawer v-model="openDetail" :title="detail?.title || '公告'" size="420px">
      <p class="body">{{ detail?.body || '没有正文' }}</p>
    </el-drawer>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { getStudentAnnouncement, listStudentAnnouncements } from '@/api/education/announcement'
import { eduErrorText } from '@/utils/request'
import { useCourseStore } from '@/store/course'

const course = useCourseStore()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const detail = ref(null)
const openDetail = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    await course.ensure('STUDENT')
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (course.current?.role === 'STUDENT') params.bindingId = String(course.current.bindingId)
    const body = await listStudentAnnouncements(params)
    rows.value = Array.isArray(body.rows) ? body.rows : []
    total.value = Number(body.total) || 0
  } catch (err) {
    rows.value = []
    total.value = 0
    error.value = eduErrorText(err)
  } finally {
    loading.value = false
  }
}

async function open(row) {
  try {
    detail.value = await getStudentAnnouncement(row.id)
    openDetail.value = true
  } catch (err) {
    error.value = eduErrorText(err)
  }
}

watch(() => course.bindingId, (id, prev) => { if (prev && id !== prev) { query.pageNum = 1; load() } })
onMounted(load)
</script>
<style scoped>
.body { white-space: pre-wrap; line-height: 1.7; }
</style>
