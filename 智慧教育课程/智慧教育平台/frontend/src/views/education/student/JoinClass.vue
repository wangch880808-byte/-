<template>
  <div class="join-page">
    <el-alert v-if="!online" title="网络已断开，恢复后会自动检查课堂状态" type="warning" show-icon :closable="false" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <el-card shadow="never" class="hero">
      <div class="icon">{{ active ? '📡' : '💤' }}</div>
      <h2>正在上课 / 一键加入</h2>
      <p class="course">{{ binding?.courseTitle || '课程' }} · {{ binding?.className || '教学班' }}</p>
      <p class="muted">
        {{ active
          ? '课堂进行中。加入时服务端会校验本班资格，迟加入也会恢复到最新公开页。'
          : '当前没有进行中的课堂，请稍后再试。' }}
      </p>
      <el-button type="primary" size="large" class="join-btn" :disabled="!active" :loading="joining" @click="join">
        一键加入
      </el-button>
      <p class="hint">外班或已退班账号会被服务端拒绝；不依赖 WebSocket 连接状态。</p>
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

const router = useRouter()
const course = useCourseStore()
const binding = ref(null)
const snapshot = ref(null)
const joining = ref(false)
const error = ref('')

const { online, start } = usePolling(refresh, 5000)

const active = computed(() => snapshot.value?.status === 'RUNNING' || snapshot.value?.status === 'PAUSED')

async function refresh() {
  if (!binding.value) return
  const current = await api.getActiveSession(binding.value.bindingId)
  snapshot.value = current && current.sessionId ? current : null
}

async function load() {
  try {
    await course.ensure('STUDENT')
    const match = course.current?.role === 'STUDENT' ? course.current : null
    if (!match) throw new Error('未找到选课记录（待第 2 组配置）')
    binding.value = match
    await refresh()
    start()
  } catch (err) {
    error.value = err.message || '加载课堂数据失败'
  }
}

async function join() {
  if (!snapshot.value?.sessionId) return
  joining.value = true
  try {
    await api.joinSession(snapshot.value.sessionId)
    router.push('/education/student/classroom')
  } catch (err) {
    ElMessage.error(err.message || '加入课堂失败')
  } finally {
    joining.value = false
  }
}

watch(() => course.bindingId, (id, prev) => { if (prev && id !== prev) load() })
onMounted(load)
</script>

<style scoped>
.join-page { display: flex; flex-direction: column; gap: 12px; max-width: 560px; margin: 0 auto; }
.hero { text-align: center; padding: 28px 16px; }
.icon { font-size: 44px; }
.course { margin: 8px 0 0; font-weight: 600; }
.muted { color: #6b7280; margin: 8px 0 20px; line-height: 1.7; }
.join-btn { width: min(100%, 280px); min-height: 44px; }
.hint { margin-top: 18px; color: #8a96a9; font-size: 12px; line-height: 1.6; }
@media (max-width: 760px) {
  .join-page { max-width: none; }
  .join-btn { width: 100%; }
}
</style>
