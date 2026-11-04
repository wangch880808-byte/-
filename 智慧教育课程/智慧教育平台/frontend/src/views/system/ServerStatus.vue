<template>
  <div class="app-container">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb16" />
    <el-descriptions v-else-if="server" :column="2" border>
      <el-descriptions-item label="主机">{{ server.sys?.computerName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="操作系统">{{ server.sys?.osName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="CPU 核数">{{ server.cpu?.cpuNum ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="CPU 使用率">{{ percent(server.cpu?.used) }}</el-descriptions-item>
      <el-descriptions-item label="内存">{{ memText }}</el-descriptions-item>
      <el-descriptions-item label="JVM">{{ server.jvm?.version || '—' }}</el-descriptions-item>
    </el-descriptions>
    <el-empty v-else-if="!loading" description="监控接口没有返回数据" />
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import request, { eduErrorText } from '@/utils/request'

const server = ref(null)
const loading = ref(false)
const error = ref('')
const memText = computed(() => {
  const mem = server.value?.mem
  if (!mem) return '—'
  return `${mem.used ?? '—'} / ${mem.total ?? '—'}`
})
function percent(value) { return value === undefined || value === null || value === '' ? '—' : `${value}%` }

onMounted(async () => {
  loading.value = true
  try {
    const body = await request({ url: '/monitor/server' })
    server.value = body.data || null
  } catch (err) {
    error.value = eduErrorText(err)
  } finally {
    loading.value = false
  }
})
</script>
