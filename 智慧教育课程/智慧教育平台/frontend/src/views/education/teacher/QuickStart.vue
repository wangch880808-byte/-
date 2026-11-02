<template>
  <div class="classroom-page">
    <el-alert v-if="!online" title="网络已断开，恢复后会自动同步课堂状态" type="warning" show-icon :closable="false" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>今日课程 / 快捷上课</span>
          <el-tag :type="statusType(status)">{{ statusText(status) }}</el-tag>
        </div>
      </template>

      <p class="muted">{{ binding?.courseTitle || '—' }} · {{ binding?.className || '—' }}</p>
      <p v-if="prep" class="muted">课件：{{ prep.resourceTitle || '—' }} · {{ prep.totalPages || '—' }} 页</p>

      <el-space wrap>
        <el-button type="primary" :disabled="status !== 'PLANNED' || loading" :loading="busy" @click="onStart">
          开始上课
        </el-button>
        <el-button :disabled="!sessionId" @click="goConsole">进入课堂教学控制台</el-button>
        <el-button :disabled="!sessionId || status === 'PLANNED'" @click="openProjection">打开投影</el-button>
      </el-space>

      <el-alert
        class="hint"
        type="info"
        :closable="false"
        title="开课后学生与投影通过服务端快照同步同一公开页；课件准备信息来自临时接口，正式 API 就绪后替换。"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '@/api/education/classroom'
import { useCourseStore } from '@/store/course'
import { usePolling } from '@/composables/usePolling'
import { statusText, statusType } from '@/utils/classroom'

const router = useRouter()
const course = useCourseStore()
const binding = ref(null)
const prep = ref(null)
const snapshot = ref(null)
const sessionId = ref(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')

const status = computed(() => snapshot.value?.status || 'PLANNED')

const { online, start } = usePolling(refreshActive, 5000)

async function refreshActive() {
  if (!binding.value) return
  const active = await api.getActiveSession(binding.value.bindingId)
  if (active && active.sessionId) {
    sessionId.value = active.sessionId
    snapshot.value = active
  } else {
    sessionId.value = null
    snapshot.value = { status: 'PLANNED', pageNo: 1 }
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    await course.ensure('TEACHER')
    const match = course.current?.role === 'TEACHER' ? course.current : null
    if (!match) throw new Error('未找到任课教学班绑定（待第 2 组配置）')
    binding.value = match
    prep.value = await api.getClassroomPrep(match.bindingId)
    await refreshActive()
    start()
  } catch (err) {
    error.value = err.message || '加载课堂数据失败'
  } finally {
    loading.value = false
  }
}

async function onStart() {
  if (!prep.value) {
    ElMessage.warning('尚未获取到开课准备数据')
    return
  }
  busy.value = true
  try {
    const started = await api.startSession({
      bindingId: prep.value.bindingId,
      lessonId: prep.value.lessonId,
      planId: prep.value.planId,
      releaseId: prep.value.releaseId,
      resourceVersionId: prep.value.resourceVersionId,
      initialPageNo: 1
    })
    sessionId.value = started.sessionId
    snapshot.value = await api.getSnapshot(started.sessionId, 'teacher')
    ElMessage.success('课堂已开始')
  } catch (err) {
    ElMessage.error(err.message || '开课失败')
  } finally {
    busy.value = false
  }
}

function goConsole() {
  router.push('/education/teacher/classroom')
}

function openProjection() {
  window.open(`/app/#/education/projection?sessionId=${encodeURIComponent(sessionId.value)}`, '_blank', 'noopener')
}

watch(() => course.bindingId, (id, prev) => { if (prev && id !== prev) load() })
onMounted(load)
</script>

<style scoped>
.classroom-page { display: flex; flex-direction: column; gap: 12px; }
.card-head { display: flex; align-items: center; justify-content: space-between; }
.muted { color: #6b7280; margin: 0 0 16px; }
.hint { margin-top: 16px; }
</style>
