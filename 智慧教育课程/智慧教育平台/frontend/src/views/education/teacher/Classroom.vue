<template>
  <div class="classroom-page">
    <el-alert v-if="!online" title="网络已断开，恢复后会自动补拉最新课堂快照" type="warning" show-icon :closable="false" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <el-empty v-if="!loading && !sessionId" description="当前没有进行中的课堂">
      <el-button type="primary" @click="router.push('/education/teacher/quick-start')">去开始上课</el-button>
    </el-empty>

    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <span>课堂教学控制台</span>
            <el-tag :type="statusType(status)">{{ statusText(status) }}</el-tag>
          </div>
        </template>
        <p class="muted">
          会话 {{ sessionId || '—' }} · {{ binding?.courseTitle || '' }} · {{ binding?.className || '' }}
        </p>

        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="inner">
              <h4>课件演示</h4>
              <div class="slide">
                <span class="page-no">第 {{ pageNo }} 页</span>
                <span>{{ prep?.resourceTitle || '课件' }}（共 {{ totalPages }} 页）</span>
              </div>
              <el-space wrap>
                <el-button :disabled="pageNo <= 1 || status !== 'RUNNING'" :loading="busy === 'prev'" @click="turn(-1)">
                  上一页
                </el-button>
                <el-button :disabled="pageNo >= totalPages || status !== 'RUNNING'" :loading="busy === 'next'" @click="turn(1)">
                  下一页
                </el-button>
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
                <el-button type="danger" :disabled="status === 'PLANNED' || status === 'ENDED'" :loading="busy === 'end'" @click="end">
                  结束
                </el-button>
              </el-space>
            </el-card>

            <el-card shadow="never" class="inner">
              <h4>课堂活动</h4>
              <el-space wrap>
                <el-button :disabled="status !== 'RUNNING'" :loading="busy === 'checkin'" @click="startCheckin">发起签到</el-button>
                <el-button :disabled="status !== 'RUNNING'" :loading="busy === 'quiz'" @click="startQuiz">发随堂题</el-button>
                <el-button :disabled="!openQuiz" :loading="busy === 'publish'" @click="publish">公布答案</el-button>
              </el-space>

              <div class="activity-list">
                <p v-if="!activities.length" class="muted">暂无进行中的活动</p>
                <div v-for="item in activities" :key="item.activityId" class="activity-row">
                  <span>{{ item.title }}（{{ KIND_TEXT[item.activityKind] || item.activityKind }}）</span>
                  <span class="activity-meta">
                    {{ statusText(item.status) }}
                    <template v-if="stats[item.activityId]">
                      · {{ item.activityKind === 'ATTENDANCE' ? '已签到' : '已提交' }}
                      {{ stats[item.activityId].respondedCount }}/{{ stats[item.activityId].targetCount }}
                    </template>
                  </span>
                </div>
              </div>
            </el-card>

            <el-card shadow="never" class="inner">
              <h4>教师私有区</h4>
              <p class="muted">备注、未公布答案、学生明细只在此显示，不进入投影。</p>
            </el-card>
          </el-col>
        </el-row>
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
import { KIND_TEXT, statusText, statusType } from '@/utils/classroom'

const router = useRouter()
const binding = ref(null)
const prep = ref(null)
const snapshot = ref(null)
const sessionId = ref(null)
const totalPages = ref(2)
const stats = ref({})
const loading = ref(true)
const busy = ref('')
const error = ref('')

const { online, start, stop } = usePolling(refresh, 3000)

const status = computed(() => snapshot.value?.status || 'PLANNED')
const pageNo = computed(() => snapshot.value?.pageNo || 1)
const activities = computed(() => snapshot.value?.openActivities || [])
const openQuiz = computed(() => activities.value.find(item => item.activityKind === 'QUIZ'))

async function refresh() {
  if (!sessionId.value) return
  snapshot.value = await api.getSnapshot(sessionId.value, 'teacher')
  await loadStats()
}

async function loadStats() {
  const next = {}
  await Promise.all(activities.value.map(async item => {
    try {
      next[item.activityId] = await api.getStatistics(item.activityId)
    } catch (err) {
      /* 统计不可用时只是不显示进度 */
    }
  }))
  stats.value = next
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const list = await api.listMyBindings()
    const match = list.find(item => item.role === 'TEACHER')
    if (!match) throw new Error('未找到任课教学班绑定（待第 2 组配置）')
    binding.value = match
    prep.value = await api.getClassroomPrep(match.bindingId)
    totalPages.value = prep.value?.totalPages || 2
    const active = await api.getActiveSession(match.bindingId)
    if (active && active.sessionId) {
      sessionId.value = active.sessionId
      snapshot.value = active
      await loadStats()
      start()
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

const turn = step => withBusy(step > 0 ? 'next' : 'prev', async () => {
  await api.changePage(sessionId.value, pageNo.value + step)
  await refresh()
})

const pause = () => withBusy('pause', async () => {
  await api.pauseSession(sessionId.value)
  await refresh()
}, '课堂已暂停')

const resume = () => withBusy('resume', async () => {
  await api.resumeSession(sessionId.value)
  await refresh()
}, '课堂已继续')

const end = () => withBusy('end', async () => {
  await api.endSession(sessionId.value)
  await refresh()
  stop()
}, '课堂已结束')

const startCheckin = () => withBusy('checkin', async () => {
  await api.createActivity({
    sessionId: sessionId.value,
    bindingId: binding.value.bindingId,
    activityKind: 'ATTENDANCE',
    title: '课堂签到',
    opensAt: new Date().toISOString(),
    closesAt: new Date(Date.now() + 10 * 60 * 1000).toISOString()
  })
  await refresh()
}, '签到已发起')

const startQuiz = () => withBusy('quiz', async () => {
  if (!prep.value?.defaultQuestionId) {
    throw new Error('当前课件未配置可用的随堂题（待第 3 组题库）')
  }
  await api.createActivity({
    sessionId: sessionId.value,
    bindingId: binding.value.bindingId,
    activityKind: 'QUIZ',
    title: '课堂随堂题',
    questionId: prep.value.defaultQuestionId,
    questionVersionId: prep.value.defaultQuestionVersionId,
    opensAt: new Date().toISOString(),
    closesAt: new Date(Date.now() + 15 * 60 * 1000).toISOString()
  })
  await refresh()
}, '随堂题已发布')

const publish = () => withBusy('publish', async () => {
  await api.publishResult(openQuiz.value.activityId)
  await refresh()
}, '答案已公布')

function openProjection() {
  window.open(`/app/#/education/projection?sessionId=${encodeURIComponent(sessionId.value)}`, '_blank', 'noopener')
}

onMounted(load)
</script>

<style scoped>
.classroom-page { display: flex; flex-direction: column; gap: 12px; }
.card-head { display: flex; align-items: center; justify-content: space-between; }
.muted { color: #6b7280; margin: 0 0 8px; }
.inner { margin-bottom: 12px; }
.slide { display: flex; flex-direction: column; gap: 6px; padding: 20px; background: #f5f7fb; border-radius: 8px; margin-bottom: 12px; }
.page-no { font-size: 20px; font-weight: 600; color: #235bdb; }
.activity-list { margin-top: 12px; display: flex; flex-direction: column; gap: 8px; }
.activity-row { display: flex; justify-content: space-between; padding: 8px 12px; background: #f7f9fc; border-radius: 6px; font-size: 13px; }
.activity-meta { color: #6b7280; }
</style>
