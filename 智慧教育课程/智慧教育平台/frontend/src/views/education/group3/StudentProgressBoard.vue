<script setup name="StudentProgressBoard">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getStudentAnswerReview, getStudentDashboard, getStudentNote, saveStudentNote } from '@/api/education/group3'

const props = defineProps({
  bindingId: { type: [String, Number], default: '' },
  compact: { type: Boolean, default: false },
  showReview: { type: Boolean, default: true }
})
const emit = defineEmits(['navigate'])
const route = useRoute()
const router = useRouter()

const dashboard = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const reviewVisible = ref(false)
const reviewLoading = ref(false)
const review = ref(null)
const reviewFilter = ref('wrong')
const noteVisible = ref(false)
const noteLoading = ref(false)
const noteSaving = ref(false)
const noteContent = ref('')
const noteUpdatedAt = ref('')

const summary = computed(() => dashboard.value?.summary || {
  total: 0, completed: 0, pending: 0, upcoming: 0, open: 0, closed: 0, percent: 0
})
const items = computed(() => (Array.isArray(dashboard.value?.items) ? dashboard.value.items : []))
const todos = computed(() => (Array.isArray(dashboard.value?.todos) ? dashboard.value.todos : []))
const reviews = computed(() => (Array.isArray(review.value?.reviews) ? review.value.reviews : []))
const wrongQuestions = computed(() => (Array.isArray(review.value?.wrongQuestions) ? review.value.wrongQuestions : []))
const knowledgePoints = computed(() => (Array.isArray(review.value?.knowledgePoints) ? review.value.knowledgePoints : []))
const visibleReviews = computed(() => reviewFilter.value === 'wrong' ? wrongQuestions.value : reviews.value)

const STATE_LABELS = {
  UPCOMING: '未开放',
  OPEN: '进行中',
  CLOSED: '已截止',
  ARCHIVED: '已归档'
}

const KIND_LABELS = {
  VIDEO: '视频任务点',
  QUIZ: '预习任务',
  DISCUSSION: '讨论任务'
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatTime(value) {
  if (!value) return '不限'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function stateLabel(state) {
  return STATE_LABELS[state] || state || '未知'
}

function stateTagType(state) {
  if (state === 'OPEN') return 'success'
  if (state === 'UPCOMING') return 'warning'
  return 'info'
}

function kindLabel(kind) {
  return KIND_LABELS[kind] || kind || '任务'
}

function toneType(tone) {
  if (tone === 'OVERDUE') return 'danger'
  if (tone === 'DUE_SOON') return 'warning'
  return 'primary'
}

function dueText(item) {
  const time = item.closesAt || item.opensAt
  if (!time) return ''
  return `${item.closesAt ? '截止' : '发布'} ${formatTime(time)}`
}

function actionableMessage(err) {
  const message = err && err.message ? err.message : String(err || '未知错误')
  const status = err && err.status
  if (status === 401) return '登录已失效，请重新登录。'
  if (status === 403) return `无权查看学习进度（${message}）。请确认课程与教学班。`
  return message
}

async function load() {
  errorMessage.value = ''
  if (!props.bindingId) {
    dashboard.value = null
    return
  }
  loading.value = true
  try {
    const res = await getStudentDashboard(props.bindingId)
    dashboard.value = res.data || null
  } catch (err) {
    dashboard.value = null
    if (!err?.cancelled) errorMessage.value = actionableMessage(err)
  } finally {
    loading.value = false
  }
}

async function loadReview() {
  if (!props.bindingId) return
  reviewLoading.value = true
  try {
    const res = await getStudentAnswerReview(props.bindingId)
    review.value = res.data || null
  } catch (err) {
    review.value = null
    if (!err?.cancelled) ElMessage.error(actionableMessage(err))
  } finally {
    reviewLoading.value = false
  }
}

function openReview() {
  reviewVisible.value = true
  if (!review.value) loadReview()
}

async function loadNote() {
  if (!props.bindingId) return
  noteLoading.value = true
  try {
    const res = await getStudentNote(props.bindingId)
    noteContent.value = res.data?.content || ''
    noteUpdatedAt.value = res.data?.updatedAt || ''
  } catch (err) {
    if (!err?.cancelled) ElMessage.error(actionableMessage(err))
  } finally {
    noteLoading.value = false
  }
}

function openNote() {
  noteVisible.value = true
  loadNote()
}

async function saveNote() {
  if (!props.bindingId) return
  noteSaving.value = true
  try {
    const res = await saveStudentNote({ bindingId: props.bindingId, content: noteContent.value })
    noteUpdatedAt.value = res.data?.updatedAt || ''
    ElMessage.success('笔记已保存')
  } catch (err) {
    if (!err?.cancelled) ElMessage.error(actionableMessage(err))
  } finally {
    noteSaving.value = false
  }
}

function openItem(item) {
  emit('navigate', item)
  if (!item || !props.bindingId || route.name !== 'course-detail' || !route.params.courseId) return
  const { topicId, assignmentId, resourceId, ...currentQuery } = route.query
  const query = { ...currentQuery, courseSpaceId: route.params.courseId, bindingId: String(props.bindingId) }
  if (item.kind === 'DISCUSSION') {
    query.tab = 'discuss'
    query.topicId = String(item.refId)
  } else if (item.kind === 'QUIZ') {
    query.tab = 'prepare'
    query.assignmentId = String(item.refId)
  } else {
    query.tab = 'material'
    query.resourceId = String(item.refId)
  }
  router.push({ name: 'course-detail', params: route.params, query })
}

function formatAnswer(answer, kind) {
  if (answer == null) return '未作答'
  if (kind === 'TRUE_FALSE') {
    return answer && answer.correctBoolean !== undefined
      ? (answer.correctBoolean ? '正确' : '错误')
      : String(answer)
  }
  if (kind === 'SINGLE' && answer && answer.choiceIndex !== undefined) {
    return `选项 ${Number(answer.choiceIndex) + 1}`
  }
  if (kind === 'MULTIPLE' && answer && Array.isArray(answer.choiceIndices)) {
    return `选项 ${answer.choiceIndices.map(index => Number(index) + 1).join('、')}`
  }
  if (typeof answer === 'string') return answer
  if (answer.referenceAnswer) return answer.referenceAnswer
  return JSON.stringify(answer)
}

function resultType(row) {
  if (row.correct === false) return 'danger'
  if (row.correct === true) return 'success'
  return 'warning'
}

function resultLabel(row) {
  if (row.correct === false) return '错题'
  if (row.correct === true) return '正确'
  return '待人工批注'
}

watch(() => props.bindingId, load)
onMounted(load)
defineExpose({ refresh: load })
</script>

<template>
  <div class="g3-student-board">
    <el-card shadow="never" class="g3-board-card">
      <template #header>
        <div class="g3-board-head">
          <span class="g3-board-title">学习进度</span>
          <span v-if="dashboard?.serverTime" class="g3-board-time">
            服务端时间 {{ formatTime(dashboard.serverTime) }}
          </span>
          <el-button
            v-if="!compact && showReview"
            size="small"
            type="primary"
            plain
            @click="openReview"
          >
            作答回看 / 错题本
          </el-button>
          <el-button
            v-if="!compact"
            size="small"
            plain
            @click="openNote"
          >
            笔记
          </el-button>
        </div>
      </template>

      <div v-loading="loading">
        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="warning"
          show-icon
          :closable="false"
          class="g3-board-gap"
        />

        <div class="g3-board-summary">
          <div class="g3-board-stat"><small>任务点</small><strong>{{ summary.total }}</strong></div>
          <div class="g3-board-stat"><small>已完成</small><strong>{{ summary.completed }}</strong></div>
          <div class="g3-board-stat"><small>待完成</small><strong>{{ summary.pending }}</strong></div>
          <div class="g3-board-stat"><small>完成率</small><strong>{{ summary.percent }}%</strong></div>
        </div>

        <div class="g3-board-todo">
          <div class="g3-board-subhead">
            <span>待办提醒</span>
            <small>新发布（7 天内）、即将截止（3 天内）或已逾期未完成</small>
          </div>
          <div v-if="todos.length" class="g3-board-todo-list">
            <button
              v-for="todo in todos"
              :key="todo.key"
              type="button"
              class="g3-board-todo-item"
              @click="openItem(todo)"
            >
              <el-tag size="small" :type="toneType(todo.tone)" effect="light">{{ todo.label }}</el-tag>
              <span class="g3-board-todo-title">{{ todo.title || '未命名任务' }}</span>
              <span class="g3-board-todo-kind">{{ kindLabel(todo.kind) }}</span>
              <span class="g3-board-todo-time">{{ dueText(todo) }}</span>
            </button>
          </div>
          <p v-else class="g3-board-empty">暂无待办任务。</p>
        </div>

        <template v-if="!compact">
          <div class="g3-board-subhead g3-board-subhead-spaced">
            <span>任务点明细</span>
            <small>按服务端记录统计；未开放任务不计入完成率</small>
          </div>
          <el-table :data="items" size="small" empty-text="暂无任务点" class="g3-board-table">
            <el-table-column label="类型" width="120">
              <template #default="{ row }">{{ kindLabel(row.kind) }}</template>
            </el-table-column>
            <el-table-column label="任务" min-width="220">
              <template #default="{ row }">
                <div class="g3-board-item-title">{{ row.title || '未命名任务' }}</div>
                <div class="g3-board-item-source">{{ row.source }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="stateTagType(row.state)" effect="light">
                  {{ stateLabel(row.state) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" min-width="180">
              <template #default="{ row }">
                <el-progress :percentage="row.progress || 0" :stroke-width="8" />
                <span class="g3-board-item-hint">{{ row.progressText }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openItem(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-card>

    <el-dialog
      v-if="!compact && showReview"
      v-model="reviewVisible"
      title="作答回看 / 错题本"
      width="960px"
      append-to-body
      class="g3-review-dialog"
    >
      <div v-loading="reviewLoading" class="g3-review-body">
        <el-alert
          v-if="knowledgePoints.length"
          :closable="false"
          type="info"
          show-icon
          class="g3-board-gap"
          title="错题知识点分布"
          :description="knowledgePoints.map(point => `${point.name}（${point.wrongCount}）`).join('、')"
        />
        <el-radio-group v-model="reviewFilter" class="g3-review-filter">
          <el-radio-button value="wrong">错题本（{{ wrongQuestions.length }}）</el-radio-button>
          <el-radio-button value="all">全部作答（{{ reviews.length }}）</el-radio-button>
        </el-radio-group>

        <el-table :data="visibleReviews" size="small" empty-text="暂无已公布解析的作答记录">
          <el-table-column label="题目" min-width="260">
            <template #default="{ row }">
              <div class="g3-review-prompt">{{ row.prompt || '题目' }}</div>
              <div class="g3-review-meta">{{ row.assignmentTitle }} · {{ row.knowledgePoints || '未关联知识点' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="我的作答" min-width="180">
            <template #default="{ row }">{{ formatAnswer(row.studentAnswer, row.questionKind) }}</template>
          </el-table-column>
          <el-table-column label="参考答案" min-width="180">
            <template #default="{ row }">{{ formatAnswer(row.correctAnswer, row.questionKind) }}</template>
          </el-table-column>
          <el-table-column label="结果" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="resultType(row)" effect="light">{{ resultLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="150">
            <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
          </el-table-column>
        </el-table>
        <p class="g3-board-empty">
          简答题不自动判定对错，仅展示参考答案与解析，最终成绩以教师确认为准。
        </p>
      </div>
      <template #footer>
        <el-button @click="reviewVisible = false">关闭</el-button>
        <el-button type="primary" :loading="reviewLoading" @click="loadReview">刷新</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-if="!compact"
      v-model="noteVisible"
      title="我的笔记"
      width="640px"
      append-to-body
      class="g3-note-dialog"
    >
      <div v-loading="noteLoading">
        <el-input
          v-model="noteContent"
          type="textarea"
          :rows="14"
          maxlength="20000"
          show-word-limit
          placeholder="记录本节课的重点、疑问或心得，保存后仅自己可见"
        />
        <p class="g3-board-empty">
          笔记仅本人可见{{ noteUpdatedAt ? ' · 上次保存 ' + formatTime(noteUpdatedAt) : '' }}
        </p>
      </div>
      <template #footer>
        <el-button @click="noteVisible = false">关闭</el-button>
        <el-button type="primary" :loading="noteSaving" @click="saveNote">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.g3-board-card { margin-bottom: 14px; }
.g3-board-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.g3-board-title { font-size: 15px; font-weight: 600; color: #303133; }
.g3-board-time { font-size: 12px; color: #909399; }
.g3-board-head .el-button { margin-left: 0; }
.g3-board-head .el-button:first-of-type { margin-left: auto; }
.g3-board-gap { margin-bottom: 12px; }
.g3-board-summary {
  display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px;
}
.g3-board-stat {
  background: #f5f7fa; border-radius: 6px; padding: 8px 10px;
  display: flex; flex-direction: column; gap: 2px;
}
.g3-board-stat small { font-size: 12px; color: #909399; }
.g3-board-stat strong { font-size: 18px; color: #303133; }
.g3-board-subhead {
  display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap;
  margin: 14px 0 8px; font-size: 13px; font-weight: 600; color: #303133;
}
.g3-board-subhead small { font-size: 12px; font-weight: 400; color: #909399; }
.g3-board-subhead-spaced { margin-top: 18px; }
.g3-board-todo-list { display: flex; flex-direction: column; gap: 6px; }
.g3-board-todo-item {
  width: 100%; display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  border: 1px solid #ebeef5; border-radius: 6px; background: #fff;
  padding: 8px 10px; text-align: left; cursor: pointer;
}
.g3-board-todo-item:hover { border-color: #b3d8ff; background: #f5f9ff; }
.g3-board-todo-title { font-size: 13px; color: #303133; font-weight: 600; }
.g3-board-todo-kind { font-size: 12px; color: #606266; }
.g3-board-todo-time { margin-left: auto; font-size: 12px; color: #909399; }
.g3-board-empty { margin: 6px 0 0; font-size: 12px; color: #909399; }
.g3-board-item-title { color: #303133; font-size: 13px; }
.g3-board-item-source,
.g3-board-item-hint { font-size: 12px; color: #909399; }
.g3-board-table { border: 1px solid #ebeef5; border-radius: 6px; overflow: hidden; }
.g3-review-filter { margin-bottom: 12px; }
.g3-review-prompt { font-size: 13px; color: #303133; }
.g3-review-meta { font-size: 12px; color: #909399; margin-top: 2px; }
.g3-review-body { min-height: 160px; }
@media (max-width: 900px) {
  .g3-board-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
