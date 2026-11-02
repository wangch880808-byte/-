<template>
  <div class="projection">
    <header class="top">
      <span class="brand">知教 · 课堂投影</span>
      <span class="status">{{ statusText(status) }}</span>
    </header>

    <main class="stage">
      <div class="page-no">第 {{ pageNo }} 页</div>
      <p v-if="openActivity" class="activity">
        {{ openActivity.title }}（{{ KIND_TEXT[openActivity.activityKind] || openActivity.activityKind }}）
      </p>
      <p v-else class="activity muted">当前没有公开活动</p>
    </main>

    <footer class="bottom">
      <span>{{ timeText }}</span>
      <span v-if="!online">网络已断开，恢复后自动同步</span>
    </footer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import * as api from '@/api/education/classroom'
import { usePolling } from '@/composables/usePolling'
import { KIND_TEXT, statusText } from '@/utils/classroom'

const route = useRoute()
const sessionId = computed(() => route.query.sessionId)
const snapshot = ref(null)
const now = ref(new Date())

const { online, start } = usePolling(refresh, 3000)

const status = computed(() => snapshot.value?.status || 'PLANNED')
const pageNo = computed(() => snapshot.value?.pageNo || 1)
// 公开视图只取服务端裁剪后的载荷，不含答案与教师私有信息。
const openActivity = computed(() => snapshot.value?.openActivity || null)
const timeText = computed(() => now.value.toLocaleTimeString('zh-CN'))

async function refresh() {
  if (!sessionId.value) return
  snapshot.value = await api.getSnapshot(sessionId.value, 'public')
  now.value = new Date()
}

onMounted(() => {
  refresh().catch(() => {})
  start()
})
</script>

<style scoped>
.projection {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0f172a;
  color: #f8fafc;
}
.top, .bottom {
  display: flex;
  justify-content: space-between;
  padding: 20px 32px;
  font-size: 14px;
  color: #94a3b8;
}
.stage {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.page-no { font-size: 64px; font-weight: 700; letter-spacing: 2px; }
.activity { font-size: 24px; }
.muted { color: #64748b; font-size: 18px; }
</style>
