<template>
  <div class="sync-page">
    <el-alert v-if="!online" title="网络已断开，正在等待重连" type="warning" show-icon :closable="false">
      恢复后会先拉取最新快照，再按事件序号补查；页码与本人提交状态以服务端为准。
    </el-alert>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <el-card v-if="loading" shadow="never" class="hero" v-loading="true">
      <p class="muted">正在检查课堂状态…</p>
    </el-card>

    <el-card v-else-if="!sessionId || phase === 'join'" shadow="never" class="hero">
      <h3>一键加入 / 同步课堂</h3>
      <p class="muted">
        {{ active
          ? '课堂进行中。加入时服务端会校验本班资格，迟加入也会恢复到最新公开页。'
          : '当前没有进行中的课堂。请先让教师在「课堂教学」中开始上课，然后再来加入。' }}
      </p>
      <el-button type="primary" size="large" class="join-btn" :disabled="!active" :loading="joining" @click="join">
        {{ active ? '一键加入' : '暂无可加入课堂' }}
      </el-button>
      <p class="hint">绑定 #{{ bindingId }}。外班或已退班账号会被服务端拒绝；页码与提交以服务端快照为准。</p>
    </el-card>

    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <span>同步课堂</span>
            <div class="head-meta">
              <el-tag size="small" type="info">seq {{ stateSeq || 0 }}</el-tag>
              <el-tag :type="statusType(status)">{{ statusText(status) }}</el-tag>
              <el-button link type="primary" @click="phase = 'join'">重新加入</el-button>
            </div>
          </div>
        </template>
        <p class="muted">第 {{ pageNo }} 页</p>

        <SlidePreview :preview-url="snapshot?.previewUrl" :page-no="pageNo" empty-text="跟随教师翻页（服务端快照）" />

        <section class="block">
          <div class="block-title">
            <h4>课堂签到</h4>
            <ActivityCountdown v-if="attendance" :closes-at="attendance.closesAt" :server-time="snapshot?.serverTime" />
          </div>
          <el-button
            type="primary"
            class="full"
            :disabled="status !== 'RUNNING' || !attendance || checkedIn || !!busy"
            :loading="busy === 'checkin'"
            @click="checkin"
          >
            {{ checkedIn ? '已签到' : '签到' }}
          </el-button>
        </section>

        <section v-if="quiz" class="block">
          <div class="block-title">
            <h4>{{ quiz.title || '随堂题' }}</h4>
            <ActivityCountdown :closes-at="quiz.closesAt" :server-time="snapshot?.serverTime" />
          </div>
          <p v-if="quiz.question?.stem" class="stem">{{ quiz.question.stem }}</p>

          <el-radio-group
            v-if="kind === 'SINGLE' || kind === 'JUDGE'"
            v-model="answer"
            class="options"
            :disabled="submitted || busy === 'submit' || status !== 'RUNNING'"
          >
            <el-radio v-for="(option, index) in quizOptions" :key="index" :value="optionValue(index)" class="option">
              {{ optionValue(index) }}. {{ option }}
            </el-radio>
          </el-radio-group>

          <el-checkbox-group
            v-else
            v-model="multiAnswer"
            class="options"
            :disabled="submitted || busy === 'submit' || status !== 'RUNNING'"
          >
            <el-checkbox v-for="(option, index) in quizOptions" :key="index" :label="optionValue(index)" class="option">
              {{ optionValue(index) }}. {{ option }}
            </el-checkbox>
          </el-checkbox-group>

          <el-button
            type="primary"
            class="full"
            :disabled="status !== 'RUNNING' || submitted || !!busy"
            :loading="busy === 'submit'"
            @click="submit"
          >
            {{ submitted ? '已提交' : '提交答案' }}
          </el-button>
          <p v-if="submitted" class="ok">提交成功。重复点击不会新增有效作答记录。</p>
        </section>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api/education/classroom'
import { useClassroomSync } from '@/composables/useClassroomSync'
import { usePolling } from '@/composables/usePolling'
import { newIdempotencyKey, quizKind, statusText, statusType } from '@/utils/classroom'
import SlidePreview from '@/components/education/SlidePreview.vue'
import ActivityCountdown from '@/components/education/ActivityCountdown.vue'

const props = defineProps({
  contextBindingId: { type: [String, Number], required: true }
})

const bindingId = computed(() => String(props.contextBindingId || ''))
const phase = ref('join')
const snapshot = ref(null)
const sessionId = ref(null)
const joining = ref(false)
const loading = ref(false)
const busy = ref('')
const error = ref('')
const answer = ref('')
const multiAnswer = ref([])
const pendingKey = ref('')

const { online, stateSeq, start, stop, pull } = useClassroomSync({
  getSessionId: () => sessionId.value,
  view: 'student',
  onSnapshot: data => { snapshot.value = data }
})

const { start: startPoll, stop: stopPoll } = usePolling(refreshActive, 5000)

const status = computed(() => snapshot.value?.status || 'PLANNED')
const pageNo = computed(() => snapshot.value?.pageNo || 1)
const activities = computed(() => snapshot.value?.openActivities || [])
const attendance = computed(() => activities.value.find(item => item.activityKind === 'ATTENDANCE'))
const quiz = computed(() => activities.value.find(item => item.activityKind === 'QUIZ'))
const quizOptions = computed(() => (Array.isArray(quiz.value?.question?.options) ? quiz.value.question.options : []))
const kind = computed(() => quizKind(quiz.value?.question))
const checkedIn = computed(() => !!snapshot.value?.myParticipation?.checkedIn)
const submitted = computed(() => !!snapshot.value?.myParticipation?.lastResponseId)
const active = computed(() => snapshot.value?.status === 'RUNNING' || snapshot.value?.status === 'PAUSED')

function optionValue(index) {
  return String.fromCharCode(65 + index)
}

async function refreshActive() {
  if (!bindingId.value || phase.value !== 'join') return
  try {
    const current = await api.getActiveSession(bindingId.value)
    if (current && current.sessionId) {
      sessionId.value = current.sessionId
      snapshot.value = current
    } else {
      sessionId.value = null
      snapshot.value = null
    }
  } catch (err) {
    error.value = err.message || '检查课堂状态失败'
  }
}

async function load() {
  if (!bindingId.value) {
    error.value = '请先选择课程与教学班'
    return
  }
  loading.value = true
  error.value = ''
  phase.value = 'join'
  stop()
  try {
    await refreshActive()
    startPoll()
  } finally {
    loading.value = false
  }
}

async function join() {
  if (!sessionId.value) return
  joining.value = true
  try {
    snapshot.value = await api.joinSession(sessionId.value)
    stateSeq.value = Number(snapshot.value?.stateSeq) || 0
    phase.value = 'sync'
    stopPoll()
    start()
  } catch (err) {
    ElMessage.error(err.message || '加入课堂失败')
  } finally {
    joining.value = false
  }
}

async function checkin() {
  if (!attendance.value || busy.value || checkedIn.value) return
  busy.value = 'checkin'
  if (!pendingKey.value) pendingKey.value = newIdempotencyKey()
  try {
    await api.checkin(attendance.value.activityId, pendingKey.value)
    pendingKey.value = ''
    await pull('manual')
    ElMessage.success('签到成功')
  } catch (err) {
    ElMessage.error(err.message || '签到失败')
  } finally {
    busy.value = ''
  }
}

async function submit() {
  if (!quiz.value || busy.value || submitted.value) return
  const value = kind.value === 'MULTI' ? [...multiAnswer.value].sort().join('') : answer.value
  if (!value) {
    ElMessage.warning(kind.value === 'MULTI' ? '请至少选择一个选项' : '请先选择答案')
    return
  }
  busy.value = 'submit'
  if (!pendingKey.value) pendingKey.value = newIdempotencyKey()
  try {
    await api.submitResponse(quiz.value.activityId, [{ itemKey: 'q1', value }], pendingKey.value)
    pendingKey.value = ''
    await pull('manual')
    ElMessage.success('提交成功')
  } catch (err) {
    ElMessage.error(err.message || '提交失败')
  } finally {
    busy.value = ''
  }
}

watch(bindingId, (id, prev) => { if (id && id !== prev) load() })
watch(phase, value => {
  if (value === 'join') {
    stop()
    startPoll()
  }
})
onMounted(load)
onBeforeUnmount(() => {
  stop()
  stopPoll()
})
</script>

<style scoped>
.sync-page { display: flex; flex-direction: column; gap: 12px; }
.hero { text-align: center; padding: 20px 12px; }
.muted { color: #6b7280; margin: 8px 0 16px; line-height: 1.7; }
.join-btn { width: min(100%, 280px); min-height: 44px; }
.hint { margin-top: 16px; color: #8a96a9; font-size: 12px; line-height: 1.6; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.head-meta { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.block { margin-top: 16px; padding-top: 12px; border-top: 1px solid #eef2f7; }
.block-title { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 8px; }
.block-title h4 { margin: 0; }
.stem { margin: 0 0 10px; line-height: 1.6; }
.options { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; margin-bottom: 12px; width: 100%; }
.option { margin-right: 0; white-space: normal; height: auto; }
.full { width: 100%; min-height: 40px; }
.ok { color: #16a34a; font-size: 13px; margin-top: 8px; }
@media (max-width: 760px) {
  .join-btn { width: 100%; }
}
</style>
