<template>
  <div class="sync-page">
    <el-alert v-if="!online" title="网络已断开，正在等待重连" type="warning" show-icon :closable="false">
      恢复后会自动拉取最新快照，页码与本人提交状态不会丢失。
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
            <el-tag :type="statusType(status)">{{ statusText(status) }}</el-tag>
          </div>
        </template>
        <p class="muted">{{ binding?.courseTitle || '' }} · 第 {{ pageNo }} 页</p>

        <div class="slide">
          <span class="page-no">第 {{ pageNo }} 页</span>
          <span class="muted">跟随教师翻页（每 3 秒同步服务端快照）</span>
        </div>

        <h4>课堂签到</h4>
        <el-button
          type="primary"
          :disabled="status !== 'RUNNING' || !attendance || checkedIn"
          :loading="busy === 'checkin'"
          @click="checkin"
        >
          {{ checkedIn ? '已签到' : '签到' }}
        </el-button>

        <template v-if="quiz">
          <h4 class="quiz-title">{{ quiz.title || '随堂题' }}</h4>
          <p v-if="quiz.question?.stem" class="stem">{{ quiz.question.stem }}</p>
          <el-radio-group v-model="answer" :disabled="submitted || busy === 'submit'">
            <el-radio
              v-for="(option, index) in quizOptions"
              :key="index"
              :value="String.fromCharCode(65 + index)"
              class="option"
            >
              {{ String.fromCharCode(65 + index) }}. {{ option }}
            </el-radio>
          </el-radio-group>
          <div class="submit-row">
            <el-button type="primary" :disabled="status !== 'RUNNING'" :loading="busy === 'submit'" @click="submit">
              {{ submitted ? '已提交' : '提交答案' }}
            </el-button>
          </div>
        </template>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '@/api/education/classroom'
import { usePolling } from '@/composables/usePolling'
import { statusText, statusType } from '@/utils/classroom'

const router = useRouter()
const binding = ref(null)
const snapshot = ref(null)
const sessionId = ref(null)
const loading = ref(true)
const busy = ref('')
const error = ref('')
const answer = ref('')

const { online, start, stop } = usePolling(refresh, 3000)

const status = computed(() => snapshot.value?.status || 'PLANNED')
const pageNo = computed(() => snapshot.value?.pageNo || 1)
const activities = computed(() => snapshot.value?.openActivities || [])
const attendance = computed(() => activities.value.find(item => item.activityKind === 'ATTENDANCE'))
const quiz = computed(() => activities.value.find(item => item.activityKind === 'QUIZ'))
const quizOptions = computed(() => (Array.isArray(quiz.value?.question?.options) ? quiz.value.question.options : []))
const checkedIn = computed(() => !!snapshot.value?.myParticipation?.checkedIn)
const submitted = computed(() => !!snapshot.value?.myParticipation?.lastResponseId)

async function refresh() {
  if (!sessionId.value) return
  snapshot.value = await api.getSnapshot(sessionId.value, 'student')
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const list = await api.listMyBindings()
    const match = list.find(item => item.role === 'STUDENT')
    if (!match) throw new Error('未找到选课记录（待第 2 组配置）')
    binding.value = match
    const active = await api.getActiveSession(match.bindingId)
    if (!active || !active.sessionId) return
    sessionId.value = active.sessionId
    snapshot.value = await api.joinSession(active.sessionId)
    start()
  } catch (err) {
    error.value = err.message || '加载课堂数据失败'
  } finally {
    loading.value = false
  }
}

async function checkin() {
  if (!attendance.value || busy.value) return
  busy.value = 'checkin'
  try {
    await api.checkin(attendance.value.activityId)
    await refresh()
    ElMessage.success('签到成功')
  } catch (err) {
    ElMessage.error(err.message || '签到失败')
  } finally {
    busy.value = ''
  }
}

async function submit() {
  if (!quiz.value || busy.value) return
  if (!answer.value) {
    ElMessage.warning('请先选择答案')
    return
  }
  busy.value = 'submit'
  try {
    await api.submitResponse(quiz.value.activityId, [{ itemKey: 'q1', value: answer.value }])
    await refresh()
    ElMessage.success('已提交')
  } catch (err) {
    ElMessage.error(err.message || '提交失败')
  } finally {
    busy.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.sync-page { display: flex; flex-direction: column; gap: 12px; }
.card-head { display: flex; align-items: center; justify-content: space-between; }
.muted { color: #6b7280; margin: 0 0 8px; }
.slide { display: flex; flex-direction: column; gap: 6px; padding: 24px 16px; background: #f5f7fb; border-radius: 8px; margin-bottom: 16px; text-align: center; }
.page-no { font-size: 22px; font-weight: 600; color: #235bdb; }
.quiz-title { margin-top: 20px; }
.stem { margin: 4px 0 12px; }
.option { display: block; margin: 6px 0; }
.submit-row { margin-top: 16px; }
</style>
