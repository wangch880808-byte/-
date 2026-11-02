<template>
  <div class="sync-page">
    <el-alert v-if="!online" title="网络已断开，正在等待重连" type="warning" show-icon :closable="false">
      恢复后会先拉取最新快照，再按事件序号补查；页码与本人提交状态以服务端为准。
    </el-alert>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <el-empty v-if="!loading && !sessionId" description="当前没有进行中的课堂">
      <el-button type="primary" @click="router.push('/education/student/join')">去一键加入</el-button>
    </el-empty>

    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <span>同步课堂</span>
            <div class="head-meta">
              <el-tag size="small" type="info">seq {{ stateSeq || 0 }}</el-tag>
              <el-tag :type="statusType(status)">{{ statusText(status) }}</el-tag>
            </div>
          </div>
        </template>
        <p class="muted">{{ binding?.courseTitle || '' }} · 第 {{ pageNo }} 页</p>

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
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '@/api/education/classroom'
import { useCourseStore } from '@/store/course'
import { useClassroomSync } from '@/composables/useClassroomSync'
import { newIdempotencyKey, quizKind, statusText, statusType } from '@/utils/classroom'
import SlidePreview from '@/components/education/SlidePreview.vue'
import ActivityCountdown from '@/components/education/ActivityCountdown.vue'

const router = useRouter()
const course = useCourseStore()
const binding = ref(null)
const snapshot = ref(null)
const sessionId = ref(null)
const loading = ref(true)
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

const status = computed(() => snapshot.value?.status || 'PLANNED')
const pageNo = computed(() => snapshot.value?.pageNo || 1)
const activities = computed(() => snapshot.value?.openActivities || [])
const attendance = computed(() => activities.value.find(item => item.activityKind === 'ATTENDANCE'))
const quiz = computed(() => activities.value.find(item => item.activityKind === 'QUIZ'))
const quizOptions = computed(() => (Array.isArray(quiz.value?.question?.options) ? quiz.value.question.options : []))
const kind = computed(() => quizKind(quiz.value?.question))
const checkedIn = computed(() => !!snapshot.value?.myParticipation?.checkedIn)
const submitted = computed(() => !!snapshot.value?.myParticipation?.lastResponseId)

function optionValue(index) {
  return String.fromCharCode(65 + index)
}

async function load() {
  loading.value = true
  error.value = ''
  stop()
  try {
    await course.ensure('STUDENT')
    const match = course.current?.role === 'STUDENT' ? course.current : null
    if (!match) throw new Error('未找到选课记录（待第 2 组配置）')
    binding.value = match
    const active = await api.getActiveSession(match.bindingId)
    if (!active || !active.sessionId) {
      sessionId.value = null
      snapshot.value = null
      return
    }
    sessionId.value = active.sessionId
    snapshot.value = await api.joinSession(active.sessionId)
    stateSeq.value = Number(snapshot.value?.stateSeq) || 0
    start()
  } catch (err) {
    error.value = err.message || '加载课堂数据失败'
  } finally {
    loading.value = false
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
    ElMessage.success('已提交')
  } catch (err) {
    ElMessage.error(err.message || '提交失败')
  } finally {
    busy.value = ''
  }
}

watch(quiz, () => {
  answer.value = ''
  multiAnswer.value = []
  pendingKey.value = ''
})
watch(() => course.bindingId, (id, prev) => { if (prev && id !== prev) load() })
onMounted(load)
</script>

<style scoped>
.sync-page { display: flex; flex-direction: column; gap: 12px; max-width: 720px; margin: 0 auto; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.head-meta { display: flex; gap: 8px; align-items: center; }
.muted { color: #6b7280; margin: 0 0 8px; }
.block { margin-top: 18px; }
.block-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.block h4 { margin: 0 0 10px; }
.stem { margin: 4px 0 12px; line-height: 1.6; }
.options { display: flex; flex-direction: column; align-items: stretch; gap: 8px; width: 100%; margin-bottom: 14px; }
.option { margin: 0; height: auto; padding: 10px 12px; border: 1px solid #e8edf4; border-radius: 8px; }
.full { width: 100%; min-height: 44px; }
.ok { margin: 10px 0 0; color: #15803d; font-size: 13px; }
@media (max-width: 760px) {
  .sync-page { max-width: none; }
  .option { font-size: 15px; }
}
</style>
