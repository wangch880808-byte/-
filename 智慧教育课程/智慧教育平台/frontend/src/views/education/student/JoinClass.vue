<template>
  <div class="join-page">
    <el-alert v-if="!online" title="网络已断开，恢复后会自动检查课堂状态" type="warning" show-icon :closable="false" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <el-card shadow="never" class="hero">
      <div class="icon">{{ active ? '📡' : '💤' }}</div>
      <h2>正在上课 / 一键加入</h2>
      <p class="muted">
        {{ active
          ? `${binding?.courseTitle || '课程'} 正在进行中，点击加入同步课堂。`
          : '当前没有进行中的课堂，请稍后再试或从课程详情进入。' }}
      </p>
      <el-button type="primary" size="large" :disabled="!active" :loading="joining" @click="join">
        一键加入
      </el-button>
      <el-alert
        class="hint"
        type="info"
        :closable="false"
        title="加入时由服务端校验本班资格，非本班或已退班将返回 403。"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '@/api/education/classroom'
import { usePolling } from '@/composables/usePolling'

const router = useRouter()
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
    const list = await api.listMyBindings()
    const match = list.find(item => item.role === 'STUDENT')
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

onMounted(load)
</script>

<style scoped>
.join-page { display: flex; flex-direction: column; gap: 12px; }
.hero { text-align: center; padding: 24px 12px; }
.icon { font-size: 44px; }
.muted { color: #6b7280; margin: 8px 0 20px; }
.hint { margin-top: 20px; text-align: left; }
</style>
