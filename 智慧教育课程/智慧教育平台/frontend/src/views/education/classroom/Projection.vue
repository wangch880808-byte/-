<template>
  <div class="projection">
    <header class="top">
      <span class="brand">知教 · 课堂投影</span>
      <div class="top-right">
        <span>seq {{ snapshot?.stateSeq || 0 }}</span>
        <span class="status">{{ statusText(status) }}</span>
      </div>
    </header>

    <main class="stage">
      <SlidePreview
        class="slide"
        :preview-url="snapshot?.previewUrl"
        :page-no="pageNo"
        empty-text="公开课件页（不含教师备注与未公布答案）"
      />

      <section v-if="openActivity" class="activity-panel">
        <div class="activity-title">
          <span>{{ openActivity.title }}（{{ KIND_TEXT[openActivity.activityKind] || openActivity.activityKind }}）</span>
          <ActivityCountdown :closes-at="openActivity.closesAt" :server-time="snapshot?.serverTime" />
        </div>
        <p v-if="openActivity.question?.stem" class="stem">{{ openActivity.question.stem }}</p>
        <ul v-if="options.length" class="options">
          <li v-for="(option, index) in options" :key="index">
            {{ String.fromCharCode(65 + index) }}. {{ option }}
          </li>
        </ul>
        <p v-if="publishedHint" class="published">{{ publishedHint }}</p>
      </section>
      <p v-else class="activity muted">当前没有公开活动</p>
    </main>

    <footer class="bottom">
      <span>{{ timeText }}</span>
      <span v-if="!online">网络已断开，恢复后自动同步</span>
      <span v-else>仅显示公开页与允许公开的结果</span>
    </footer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useClassroomSync } from '@/composables/useClassroomSync'
import { KIND_TEXT, statusText } from '@/utils/classroom'
import SlidePreview from '@/components/education/SlidePreview.vue'
import ActivityCountdown from '@/components/education/ActivityCountdown.vue'

const route = useRoute()
const sessionId = computed(() => route.query.sessionId)
const snapshot = ref(null)
const now = ref(new Date())
const publishedHint = ref('')

const { online, start, pull } = useClassroomSync({
  getSessionId: () => sessionId.value,
  view: 'public',
  onSnapshot: data => {
    snapshot.value = data
    now.value = new Date()
    const question = data?.openActivity?.question || {}
    // 公开视图不应出现 correctOption / answer；若误带则不展示。
    publishedHint.value = question.answer || question.correctOption
      ? ''
      : (data?.openActivity?.status === 'CLOSED' ? '活动已关闭' : '')
  }
})

const status = computed(() => snapshot.value?.status || 'PLANNED')
const pageNo = computed(() => snapshot.value?.pageNo || 1)
const openActivity = computed(() => snapshot.value?.openActivity || null)
const options = computed(() => Array.isArray(openActivity.value?.question?.options) ? openActivity.value.question.options : [])
const timeText = computed(() => now.value.toLocaleTimeString('zh-CN'))

onMounted(() => {
  pull('resume').catch(() => {})
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
  gap: 12px;
}
.top-right { display: flex; gap: 16px; }
.stage {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 12px 24px 28px;
}
.slide { width: min(960px, 100%); background: #1e293b; }
.slide :deep(.hint), .slide :deep(.page-no) { color: #e2e8f0; }
.activity-panel {
  width: min(960px, 100%);
  background: #1e293b;
  border-radius: 12px;
  padding: 20px 24px;
}
.activity-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  font-size: 22px;
  margin-bottom: 10px;
}
.stem { font-size: 20px; line-height: 1.6; margin: 0 0 12px; }
.options { margin: 0; padding-left: 22px; font-size: 18px; line-height: 1.8; }
.published { margin: 12px 0 0; color: #93c5fd; }
.muted { color: #64748b; font-size: 18px; }
</style>
