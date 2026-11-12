<template>
  <div class="classroom-page">
    <el-alert v-if="!online" title="网络已断开，恢复后会自动补拉最新课堂快照与事件" type="warning" show-icon :closable="false" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <el-card shadow="never" class="start-card">
      <template #header>
        <div class="card-head">
          <span>快捷上课</span>
          <el-tag :type="statusType(status)">{{ statusText(status) }}</el-tag>
        </div>
      </template>
      <p class="muted">课件：{{ prep?.resourceTitle || '—' }} · {{ prep?.totalPages || '—' }} 页</p>
      <el-space wrap>
        <el-button type="primary" :disabled="status !== 'PLANNED' || loading || !canStart" :loading="busy === 'start'" @click="onStart">
          开始上课
        </el-button>
        <el-button :disabled="!sessionId || status === 'PLANNED'" @click="openProjection">打开投影</el-button>
      </el-space>
      <el-alert
        class="hint"
        type="info"
        :closable="false"
        title="开课后学生与投影通过服务端快照同步同一公开页；页码与活动状态以服务端为准，不以“已连接”为验收依据。"
      />
    </el-card>

    <el-empty v-if="!loading && !sessionId" description="当前没有进行中的课堂" />

    <el-card v-else shadow="never">
      <template #header>
        <div class="card-head">
          <span>课堂教学控制台</span>
          <div class="head-meta">
            <el-tag size="small" type="info">seq {{ stateSeq || 0 }}</el-tag>
            <el-tag :type="statusType(status)">{{ statusText(status) }}</el-tag>
          </div>
        </div>
      </template>
      <p class="muted">会话 {{ sessionId || '—' }}</p>

      <el-row :gutter="12">
        <el-col :xs="24" :md="12">
          <el-card shadow="never" class="inner">
            <h4>课件演示</h4>
            <SlidePreview :preview-url="snapshot?.previewUrl" :page-no="pageNo" />
            <p class="muted">{{ prep?.resourceTitle || '课件' }}（共 {{ totalPages }} 页）</p>
            <el-space wrap>
              <el-button :disabled="pageNo <= 1 || status !== 'RUNNING'" :loading="busy === 'prev'" @click="turn(-1)">上一页</el-button>
              <el-button :disabled="pageNo >= totalPages || status !== 'RUNNING'" :loading="busy === 'next'" @click="turn(1)">下一页</el-button>
              <el-button :disabled="!sessionId || status === 'PLANNED'" @click="openProjection">投影</el-button>
            </el-space>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="12">
          <el-card shadow="never" class="inner">
            <h4>课堂控制</h4>
            <el-space wrap>
              <el-button :disabled="status !== 'RUNNING'" :loading="busy === 'pause'" @click="pause">暂停</el-button>
              <el-button :disabled="status !== 'PAUSED'" :loading="busy === 'resume'" @click="resume">继续</el-button>
              <el-button type="danger" :disabled="status === 'PLANNED' || status === 'ENDED'" :loading="busy === 'end'" @click="end">结束</el-button>
            </el-space>
          </el-card>

          <el-card shadow="never" class="inner">
            <h4>课堂活动</h4>
            <el-space wrap>
              <el-button :disabled="status !== 'RUNNING'" :loading="busy === 'checkin'" @click="startCheckin">发起签到</el-button>
              <el-button :disabled="status !== 'RUNNING'" :loading="busy === 'quiz'" @click="startQuiz">发随堂题</el-button>
            </el-space>

            <div class="activity-list">
              <p v-if="!activities.length" class="muted">暂无进行中的活动</p>
              <div v-for="item in activities" :key="item.activityId" class="activity-row">
                <div>
                  <div>
                    {{ item.title }}（{{ KIND_TEXT[item.activityKind] || item.activityKind }}）
                    <ActivityCountdown :closes-at="item.closesAt" :server-time="snapshot?.serverTime" />
                  </div>
                  <div class="activity-meta">
                    {{ statusText(item.status) }}
                    <template v-if="stats[item.activityId]">
                      · {{ item.activityKind === 'ATTENDANCE' ? '已签到' : '已提交' }}
                      {{ stats[item.activityId].respondedCount }}/{{ stats[item.activityId].targetCount }}
                      <template v-if="stats[item.activityId].correctCount != null">
                        · 答对 {{ stats[item.activityId].correctCount }}
                      </template>
                    </template>
                  </div>
                </div>
                <el-space wrap>
                  <el-button
                    v-if="item.activityKind === 'QUIZ'"
                    size="small"
                    :disabled="!!stats[item.activityId]?.answerPublished"
                    :loading="busy === `publish-${item.activityId}`"
                    @click="publish(item)"
                  >公布答案</el-button>
                  <el-button
                    size="small"
                    :disabled="item.status !== 'OPEN'"
                    :loading="busy === `close-${item.activityId}`"
                    @click="close(item)"
                  >关闭</el-button>
                </el-space>
              </div>
            </div>
          </el-card>

          <el-card shadow="never" class="inner">
            <h4>教师私有区</h4>
            <p class="muted">备注、未公布答案、名单统计只在此显示，不进入投影与学生公开载荷。</p>
            <template v-if="openQuiz?.question">
              <p><strong>当前随堂题</strong>：{{ openQuiz.question.stem || '—' }}</p>
              <p><strong>参考答案</strong>：{{ formatAnswer(openQuiz.question.answer) }}</p>
              <p v-if="openQuiz.question.explanation"><strong>解析</strong>：{{ openQuiz.question.explanation }}</p>
            </template>
            <p v-else class="muted">当前没有带答案的随堂题。</p>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api/education/classroom'
import { useClassroomSync } from '@/composables/useClassroomSync'
import { KIND_TEXT, formatAnswer, statusText, statusType } from '@/utils/classroom'
import SlidePreview from '@/components/education/SlidePreview.vue'
import ActivityCountdown from '@/components/education/ActivityCountdown.vue'

const props = defineProps({
  contextBindingId: { type: [String, Number], required: true }
})

const bindingId = computed(() => String(props.contextBindingId || ''))
const prep = ref(null)
const snapshot = ref(null)
const sessionId = ref(null)
const totalPages = ref(2)
const stats = ref({})
const loading = ref(true)
const busy = ref('')
const error = ref('')

const { online, stateSeq, start, stop, pull } = useClassroomSync({
  getSessionId: () => sessionId.value,
  view: 'teacher',
  onSnapshot: async data => {
    snapshot.value = data
    await loadStats()
  }
})

const status = computed(() => snapshot.value?.status || 'PLANNED')
const pageNo = computed(() => snapshot.value?.pageNo || 1)
const activities = computed(() => snapshot.value?.openActivities || [])
const openQuiz = computed(() => activities.value.find(item => item.activityKind === 'QUIZ'))
const canStart = computed(() => !!(prep.value?.releaseId && prep.value?.resourceVersionId))

async function loadStats() {
  const next = {}
  await Promise.all(activities.value.map(async item => {
    try {
      next[item.activityId] = await api.getStatistics(item.activityId)
    } catch {
      /* ignore */
    }
  }))
  stats.value = next
}

async function load() {
  if (!bindingId.value) {
    error.value = '请先选择课程与教学班'
    return
  }
  loading.value = true
  error.value = ''
  stop()
  try {
    prep.value = await api.getClassroomPrep(bindingId.value)
    totalPages.value = prep.value?.totalPages || 2
    const active = await api.getActiveSession(bindingId.value)
    if (active && active.sessionId) {
      sessionId.value = active.sessionId
      snapshot.value = active
      stateSeq.value = Number(active.stateSeq) || 0
      await loadStats()
      start()
    } else {
      sessionId.value = null
      snapshot.value = { status: 'PLANNED', pageNo: 1 }
    }
  } catch (err) {
    error.value = err.message || '加载课堂数据失败'
  } finally {
    loading.value = false
  }
}

async function withBusy(name, action, successText) {
  if (busy.value) return
  busy.value = name
  try {
    await action()
    if (successText) ElMessage.success(successText)
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  } finally {
    busy.value = ''
  }
}

async function onStart() {
  if (!prep.value?.releaseId) {
    ElMessage.warning('尚未获取到可用课件发布，请先在课前准备发布课件')
    return
  }
  await withBusy('start', async () => {
    const started = await api.startSession({
      bindingId: Number(bindingId.value),
      lessonId: prep.value.lessonId,
      planId: prep.value.planId,
      releaseId: prep.value.releaseId,
      resourceVersionId: prep.value.resourceVersionId,
      initialPageNo: 1
    })
    sessionId.value = started.sessionId
    snapshot.value = await api.getSnapshot(started.sessionId, 'teacher')
    stateSeq.value = Number(snapshot.value?.stateSeq) || 0
    start()
  }, '课堂已开始')
}

const turn = step => withBusy(step > 0 ? 'next' : 'prev', async () => {
  snapshot.value = await api.changePage(sessionId.value, pageNo.value + step)
  stateSeq.value = Number(snapshot.value?.stateSeq) || stateSeq.value
  await loadStats()
})

const pause = () => withBusy('pause', async () => { await api.pauseSession(sessionId.value); await pull('manual') }, '课堂已暂停')
const resume = () => withBusy('resume', async () => { await api.resumeSession(sessionId.value); await pull('manual') }, '课堂已继续')
const end = () => withBusy('end', async () => {
  snapshot.value = await api.endSession(sessionId.value)
  stop()
}, '课堂已结束')

const startCheckin = () => withBusy('checkin', async () => {
  await api.createActivity({
    sessionId: sessionId.value,
    bindingId: Number(bindingId.value),
    activityKind: 'ATTENDANCE',
    title: '课堂签到',
    opensAt: new Date().toISOString(),
    closesAt: new Date(Date.now() + 10 * 60 * 1000).toISOString()
  })
  await pull('manual')
}, '签到已发起')

const startQuiz = () => withBusy('quiz', async () => {
  if (!prep.value?.defaultQuestionId) throw new Error('当前未配置可用的随堂题模板/题库')
  await api.createActivity({
    sessionId: sessionId.value,
    bindingId: Number(bindingId.value),
    activityKind: 'QUIZ',
    title: '课堂随堂题',
    questionId: prep.value.defaultQuestionId,
    questionVersionId: prep.value.defaultQuestionVersionId,
    opensAt: new Date().toISOString(),
    closesAt: new Date(Date.now() + 15 * 60 * 1000).toISOString()
  })
  await pull('manual')
}, '随堂题已发布')

const publish = item => withBusy(`publish-${item.activityId}`, async () => {
  await api.publishResult(item.activityId)
  await pull('manual')
}, '答案已公布')

const close = item => withBusy(`close-${item.activityId}`, async () => {
  await api.closeActivity(item.activityId)
  await pull('manual')
}, '活动已关闭')

function openProjection() {
  if (!sessionId.value) return
  const url = new URL(window.location.href)
  url.hash = `/education/projection?sessionId=${encodeURIComponent(sessionId.value)}`
  window.open(url.toString(), '_blank', 'noopener')
}

watch(bindingId, (id, prev) => { if (id && id !== prev) load() })
onMounted(load)
onBeforeUnmount(stop)
</script>

<style scoped>
.classroom-page { display: flex; flex-direction: column; gap: 12px; }
.start-card { margin-bottom: 0; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.head-meta { display: flex; gap: 8px; align-items: center; }
.muted { color: #6b7280; margin: 0 0 8px; }
.hint { margin-top: 12px; }
.inner { margin-bottom: 12px; }
.activity-list { margin-top: 12px; display: flex; flex-direction: column; gap: 8px; }
.activity-row { display: flex; justify-content: space-between; gap: 12px; align-items: center; padding: 10px 12px; background: #f7f9fc; border-radius: 6px; font-size: 13px; }
.activity-meta { color: #6b7280; margin-top: 4px; }
</style>
