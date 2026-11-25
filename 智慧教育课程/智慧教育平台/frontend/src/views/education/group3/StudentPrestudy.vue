<template>
  <div class="g3-student-page">
    <Group3ContextBar
      v-if="!contextBindingId"
      :list="list"
      :binding-id="bindingId"
      :loading="loading"
      :error="error"
      @select="handleSelect"
      @refresh="refresh"
    />

    <el-empty v-if="!bindingId" description="暂无在修课程，请联系任课教师确认教学班成员关系" />

    <template v-else>
      <div class="g3-page-head">
        <div class="g3-title-block">
          <h2>课前学习</h2>
          <p>{{ current ? `${current.courseTitle} · ${current.className}` : '—' }}</p>
          <span class="g3-head-note">
            这里汇总教师在「课前准备」发布的资料阅读、预习任务与讨论环节；未开放、已撤回或不属于本班的内容不会出现，只有点「提交预习」才写入服务端。
          </span>
        </div>
        <div class="g3-head-filter">
          <span>筛选</span>
          <el-select v-model="stateFilter" style="width: 150px">
            <el-option v-for="item in STATE_FILTERS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
      </div>

      <StudentProgressBoard :binding-id="bindingId" />

      <el-alert v-if="loadError" :title="loadError" type="warning" show-icon :closable="false" class="g3-gap" />

      <div class="g3-layout">
        <div v-loading="loadingList" class="g3-tl-main">
          <StudentTimeline
            :nodes="visibleNodes"
            :selected-key="selectedKey"
            mode="prestudy"
            empty-hint="教师发布预习任务后，任务会出现在这条时间轴上"
            @select="handleNodeSelect"
          />
        </div>
      </div>

    <el-dialog
      v-model="detailVisible"
      width="min(920px, 94vw)"
      top="6vh"
      append-to-body
      class="g3-detail-dialog"
    >
          <el-card v-if="selectedNode && selectedNode.kind === 'QUIZ'" shadow="never" class="g3-detail">
            <template #header>
              <div class="g3-detail-head">
                <span class="g3-kind">预习习题</span>
                <el-tag size="small" effect="plain">{{ seqText(selectedNode) }}</el-tag>
                <el-tag size="small" :type="stateTagType(detailState)">{{ stateLabel(detailState) }}</el-tag>
                <el-button
                  class="g3-enter-study"
                  size="small"
                  type="primary"
                  plain
                  @click="showIndependent = true"
                >
                  独立作答
                </el-button>
              </div>
            </template>

            <div v-loading="detailLoading" class="g3-detail-body">
              <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false" class="g3-gap">
                <el-button size="small" @click="loadDetail(selectedNode)">重试</el-button>
              </el-alert>

              <template v-else-if="detail">
                <h3 class="g3-detail-title">{{ detail.title }}</h3>
                <p class="g3-hint g3-detail-time">
                  开放：{{ formatTime(detail.opensAt) }} 至 {{ formatTime(detail.closesAt) }}
                </p>

                <el-alert
                  v-if="detail.instructions"
                  :title="detail.instructions"
                  type="info"
                  :closable="false"
                  class="g3-gap"
                />
                <el-alert
                  v-if="!canSubmit && !detail.submitted"
                  :title="blockReason"
                  type="warning"
                  show-icon
                  :closable="false"
                  class="g3-gap"
                />

                <el-empty v-if="detail.state === 'UPCOMING'" description="任务尚未开放" :image-size="80" />
                <el-empty
                  v-else-if="!items.length"
                  description="本任务没有题目，可先阅读课程资料"
                  :image-size="80"
                />

                <div v-for="(item, index) in items" :key="item.assignmentItemId" class="g3-question">
                  <div class="g3-question-stem">
                    <span class="g3-q-no">{{ index + 1 }}</span>
                    <span>{{ item.prompt || item.stem }}</span>
                    <el-tag size="small" effect="plain" type="info">{{ questionKindLabel(item.questionKind) }}</el-tag>
                  </div>

                  <template v-if="!detail.submitted">
                    <el-radio-group
                      v-if="item.questionKind === 'SINGLE'"
                      v-model="answers[String(item.assignmentItemId)]"
                      :disabled="!canSubmit"
                      class="g3-options"
                    >
                      <el-radio
                        v-for="(option, optionIndex) in optionsOf(item)"
                        :key="optionIndex"
                        :value="optionIndex"
                        class="g3-option"
                      >
                        {{ option }}
                      </el-radio>
                    </el-radio-group>

                    <el-radio-group
                      v-else-if="item.questionKind === 'TRUE_FALSE'"
                      v-model="answers[String(item.assignmentItemId)]"
                      :disabled="!canSubmit"
                      class="g3-options"
                    >
                      <el-radio :value="0" class="g3-option">正确</el-radio>
                      <el-radio :value="1" class="g3-option">错误</el-radio>
                    </el-radio-group>

                    <el-checkbox-group
                      v-else-if="item.questionKind === 'MULTIPLE'"
                      v-model="answers[String(item.assignmentItemId)]"
                      :disabled="!canSubmit"
                      class="g3-options"
                    >
                      <el-checkbox
                        v-for="(option, optionIndex) in optionsOf(item)"
                        :key="optionIndex"
                        :value="optionIndex"
                        class="g3-option"
                      >
                        {{ option }}
                      </el-checkbox>
                    </el-checkbox-group>

                    <el-input
                      v-else
                      v-model="answers[String(item.assignmentItemId)]"
                      type="textarea"
                      :rows="3"
                      :disabled="!canSubmit"
                      placeholder="填写你的回答"
                    />
                  </template>

                  <div v-if="item.answerVisible && item.answer" class="g3-feedback">
                    <div><strong>参考答案：</strong>{{ formatAnswer(item.answer, item.questionKind) }}</div>
                    <div v-if="item.explanation"><strong>解析：</strong>{{ item.explanation }}</div>
                  </div>
                </div>

                <div v-if="items.length && !detail.submitted" class="g3-submit">
                  <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">
                    提交预习
                  </el-button>
                  <span class="g3-hint">
                    {{ canSubmit
                      ? '提交后不可重复提交；参考答案按教师设置的公布时间显示。'
                      : '按钮按当前开放窗口禁用，这不是页面故障，原因见上方提示。' }}
                  </span>
                </div>

                <el-alert
                  v-if="detail.submitted"
                  type="success"
                  :closable="false"
                  show-icon
                  class="g3-gap g3-submitted"
                  :title="submittedTitle"
                  :description="detail.answerVisible ? '参考答案与解析已公布。' : '参考答案将在教师设置的公布时间显示。'"
                />

                <p v-if="detail.answerVisible && detail.answerVisibleAt" class="g3-hint">
                  解析公布时间：{{ formatTime(detail.answerVisibleAt) }}
                </p>

                <template v-if="detail.resources && detail.resources.length">
                  <el-divider content-position="left">本班当前开放资料</el-divider>
                  <div class="g3-resource-links">
                    <el-button
                      v-for="resource in detail.resources"
                      :key="resource.versionId"
                      size="small"
                      :loading="downloadingId === resource.versionId"
                      @click="downloadResource(resource)"
                    >
                      {{ resource.title }} · V{{ resource.versionNumber || resource.versionNo }}
                    </el-button>
                  </div>
                </template>
              </template>

              <el-empty v-else description="正在加载任务" :image-size="80" />
            </div>
          </el-card>

          <el-card v-else-if="selectedNode" shadow="never" class="g3-detail">
            <template #header>
              <div class="g3-detail-head">
                <span class="g3-kind">{{ selectedNode.kind === 'DISCUSSION' ? '讨论环节' : '资料阅读' }}</span>
                <el-tag size="small" effect="plain">{{ seqText(selectedNode) }}</el-tag>
                <el-tag size="small" :type="stateTagType(selectedNode.state)">{{ stateLabel(selectedNode.state) }}</el-tag>
                <el-button
                  v-if="selectedNode.kind === 'MATERIAL'"
                  size="small"
                  type="primary"
                  plain
                  @click="openMaterial(selectedNode)"
                >
                  打开资料
                </el-button>
                <el-button
                  v-else
                  size="small"
                  type="primary"
                  plain
                  @click="openDiscussion(selectedNode)"
                >
                  进入讨论
                </el-button>
              </div>
            </template>
            <h3 class="g3-detail-title">{{ selectedNode.title }}</h3>
            <p class="g3-hint">
              开放：{{ formatTime(selectedNode.opensAt) }} 至 {{ formatTime(selectedNode.closesAt) }}
            </p>
            <p v-if="selectedNode.subtitle" class="g3-hint">{{ selectedNode.subtitle }}</p>
            <div v-if="selectedNode.tags && selectedNode.tags.length" class="g3-material-tags">
              <el-tag v-for="(tag, index) in selectedNode.tags" :key="index" size="small" effect="plain">{{ tag }}</el-tag>
            </div>
          </el-card>

          <el-card v-else shadow="never" class="g3-detail">
            <template #header><span>环节详情</span></template>
            <el-empty description="选择一个环节" :image-size="90">
              <p class="g3-hint">预习任务可逐题作答、提交并查看允许公开的反馈；资料与讨论可直接查看。</p>
            </el-empty>
          </el-card>
    </el-dialog>
    </template>

    <el-dialog
      v-model="materialVisible"
      :title="materialNode ? materialNode.title : '资料阅读'"
      width="860px"
      append-to-body
      class="g3-material-dialog"
    >
      <div v-loading="materialLoading" class="g3-material-body">
        <el-alert
          v-if="materialError"
          :title="materialError"
          type="error"
          show-icon
          :closable="false"
          class="g3-gap"
        >
          <el-button size="small" @click="materialNode && openMaterial(materialNode)">重试</el-button>
        </el-alert>
        <template v-else>
          <p v-if="materialNode" class="g3-hint g3-material-meta">
            开放：{{ formatTime(materialNode.opensAt) }} 至 {{ formatTime(materialNode.closesAt) }}
            <span v-if="materialNode.meta">· V{{ materialNode.meta.versionNo || 1 }} · {{ Number(materialNode.meta.pageCount || 0) }} 页</span>
          </p>
          <div v-if="materialVideoUrl" class="g3-material-video">
            <video :src="materialVideoUrl" controls />
          </div>
          <div v-else-if="materialPages.length" class="g3-material-pages">
            <img
              v-for="(url, index) in materialPages"
              :key="index"
              :src="url"
              :alt="`第 ${index + 1} 页`"
            />
          </div>
          <el-empty
            v-else-if="!materialLoading"
            description="该资料暂无可预览页面，可下载查看"
            :image-size="80"
          />
        </template>
      </div>
      <template #footer>
        <el-button @click="materialVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="downloadingId === (materialNode && materialNode.meta && materialNode.meta.versionId)"
          @click="downloadMaterial"
        >
          下载资料
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="showIndependent"
      size="92%"
      direction="rtl"
      :with-header="false"
      :modal="false"
      append-to-body
      class="g3-study-drawer"
    >
      <div class="g3-independent-head">
        <el-button size="small" @click="showIndependent = false">← 返回</el-button>
        <el-button
          size="small"
          :disabled="!indepPrevNode"
          @click="jumpIndep(indepPrevNode)"
        >上一个任务</el-button>
        <el-tag size="default" effect="plain">
          {{ seqText(selectedNode || indepCurrentNode) }}
        </el-tag>
        <span class="g3-independent-title">
          {{ (selectedNode || indepCurrentNode) ? (selectedNode || indepCurrentNode).title : '—' }}
        </span>
        <el-button
          size="small"
          :disabled="!indepNextNode"
          @click="jumpIndep(indepNextNode)"
        >下一个任务</el-button>
      </div>

      <div v-if="detail" class="g3-independent-body">
        <div v-loading="detailLoading" class="g3-detail-body">
          <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false" class="g3-gap">
            <el-button size="small" @click="loadDetail(selectedNode)">重试</el-button>
          </el-alert>

          <template v-else>
            <h3 class="g3-detail-title">{{ detail.title }}</h3>
            <p class="g3-hint g3-detail-time">
              开放：{{ formatTime(detail.opensAt) }} 至 {{ formatTime(detail.closesAt) }}
            </p>

            <el-alert
              v-if="detail.instructions"
              :title="detail.instructions"
              type="info"
              :closable="false"
              class="g3-gap"
            />
            <el-alert
              v-if="!canSubmit && !detail.submitted"
              :title="blockReason"
              type="warning"
              show-icon
              :closable="false"
              class="g3-gap"
            />

            <el-empty v-if="detail.state === 'UPCOMING'" description="任务尚未开放" :image-size="80" />
            <el-empty
              v-else-if="!items.length"
              description="本任务没有题目，可先阅读课程资料"
              :image-size="80"
            />

            <div v-for="(item, index) in items" :key="item.assignmentItemId" class="g3-question">
              <div class="g3-question-stem">
                <span class="g3-q-no">{{ index + 1 }}</span>
                <span>{{ item.prompt || item.stem }}</span>
                <el-tag size="small" effect="plain" type="info">{{ questionKindLabel(item.questionKind) }}</el-tag>
              </div>

              <template v-if="!detail.submitted">
                <el-radio-group
                  v-if="item.questionKind === 'SINGLE'"
                  v-model="answers[String(item.assignmentItemId)]"
                  :disabled="!canSubmit"
                  class="g3-options"
                >
                  <el-radio
                    v-for="(option, optionIndex) in optionsOf(item)"
                    :key="optionIndex"
                    :value="optionIndex"
                    class="g3-option"
                  >
                    {{ option }}
                  </el-radio>
                </el-radio-group>

                <el-radio-group
                  v-else-if="item.questionKind === 'TRUE_FALSE'"
                  v-model="answers[String(item.assignmentItemId)]"
                  :disabled="!canSubmit"
                  class="g3-options"
                >
                  <el-radio :value="0" class="g3-option">正确</el-radio>
                  <el-radio :value="1" class="g3-option">错误</el-radio>
                </el-radio-group>

                <el-checkbox-group
                  v-else-if="item.questionKind === 'MULTIPLE'"
                  v-model="answers[String(item.assignmentItemId)]"
                  :disabled="!canSubmit"
                  class="g3-options"
                >
                  <el-checkbox
                    v-for="(option, optionIndex) in optionsOf(item)"
                    :key="optionIndex"
                    :value="optionIndex"
                    class="g3-option"
                  >
                    {{ option }}
                  </el-checkbox>
                </el-checkbox-group>

                <el-input
                  v-else
                  v-model="answers[String(item.assignmentItemId)]"
                  type="textarea"
                  :rows="3"
                  :disabled="!canSubmit"
                  placeholder="填写你的回答"
                />
              </template>

              <div v-if="item.answerVisible && item.answer" class="g3-feedback">
                <div><strong>参考答案：</strong>{{ formatAnswer(item.answer, item.questionKind) }}</div>
                <div v-if="item.explanation"><strong>解析：</strong>{{ item.explanation }}</div>
              </div>
            </div>

            <div v-if="items.length && !detail.submitted" class="g3-submit">
              <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">
                提交预习
              </el-button>
              <span class="g3-hint">
                {{ canSubmit
                  ? '提交后不可重复提交；参考答案按教师设置的公布时间显示。'
                  : '按钮按当前开放窗口禁用，这不是页面故障，原因见上方提示。' }}
              </span>
            </div>

            <el-alert
              v-if="detail.submitted"
              type="success"
              :closable="false"
              show-icon
              class="g3-gap g3-submitted"
              :title="submittedTitle"
              :description="detail.answerVisible ? '参考答案与解析已公布。' : '参考答案将在教师设置的公布时间显示。'"
            />

            <p v-if="detail.answerVisible && detail.answerVisibleAt" class="g3-hint">
              解析公布时间：{{ formatTime(detail.answerVisibleAt) }}
            </p>

            <template v-if="detail.resources && detail.resources.length">
              <el-divider content-position="left">本班当前开放资料</el-divider>
              <div class="g3-resource-links">
                <el-button
                  v-for="resource in detail.resources"
                  :key="resource.versionId"
                  size="small"
                  :loading="downloadingId === resource.versionId"
                  @click="downloadResource(resource)"
                >
                  {{ resource.title }} · V{{ resource.versionNumber || resource.versionNo }}
                </el-button>
              </div>
            </template>
          </template>
        </div>
      </div>

      <el-empty v-else description="选择环节后进入独立作答" :image-size="90" />
    </el-drawer>
  </div>
</template>

<script setup name="StudentPrestudy">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { saveAs } from 'file-saver'
import {
  downloadVersion,
  getStudentPreview,
  getVersion,
  getVersionPage,
  listDiscussions,
  listStudentPrepMaterials,
  listStudentPreviews,
  submitStudentPreview
} from '@/api/education/group3'
import Group3ContextBar from './Group3ContextBar.vue'
import StudentProgressBoard from './StudentProgressBoard.vue'
import StudentTimeline from './StudentTimeline.vue'
import { useGroup3Binding } from './useGroup3Binding.js'

const props = defineProps({ contextBindingId: { type: String, default: '' } })
const { list, bindingId, current, loading, error, load, select } = useGroup3Binding(() => props.contextBindingId)
const router = useRouter()
const route = useRoute()

const STATE_FILTERS = [
  { value: 'all', label: '全部任务' },
  { value: 'todo', label: '待完成' },
  { value: 'submitted', label: '已提交' },
  { value: 'closed', label: '已截止' }
]

const STATE_LABELS = {
  UPCOMING: '未开放',
  OPEN: '进行中',
  CLOSED: '已截止',
  ARCHIVED: '已归档'
}

const QUESTION_KIND_LABELS = {
  SINGLE: '单选题',
  MULTIPLE: '多选题',
  TRUE_FALSE: '判断题',
  SHORT_TEXT: '简答题'
}

const tasks = ref([])
const materials = ref([])
const discussions = ref([])
const stateFilter = ref('all')
const selectedKey = ref('')
const detailVisible = ref(false)
const loadingList = ref(false)
const loadError = ref('')

const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const answers = reactive({})
const answerDrafts = new Map()
const submitting = ref(false)
const downloadingId = ref(null)

// 课前准备资料阅读
const materialVisible = ref(false)
const materialNode = ref(null)
const materialLoading = ref(false)
const materialError = ref('')
const materialDetail = ref(null)
const materialPages = ref([])
const materialVideoUrl = ref('')

let listToken = 0
let detailToken = 0
let materialToken = 0

// 独立模式
const showIndependent = ref(false)
const indepCurrentNode = ref(null)

const indepNodes = computed(() => visibleNodes.value.filter(node => node.kind === 'QUIZ'))
const indepIndex = computed(() => {
  if (!selectedKey.value) return -1
  return indepNodes.value.findIndex(n => n.key === selectedKey.value)
})
const indepPrevNode = computed(() => {
  const idx = indepIndex.value
  return idx > 0 ? indepNodes.value[idx - 1] : null
})
const indepNextNode = computed(() => {
  const idx = indepIndex.value
  if (idx < 0) return null
  return idx < indepNodes.value.length - 1 ? indepNodes.value[idx + 1] : null
})

function jumpIndep(target) {
  if (!target) return
  handleNodeSelect(target)
}

watch(showIndependent, (v) => {
  if (v && selectedKey.value) {
    indepCurrentNode.value = selectedNode.value
  } else if (v) {
    const firstNavigable = indepNodes.value.find(n => n.state !== 'CLOSED') || indepNodes.value[0]
    if (firstNavigable) handleNodeSelect(firstNavigable)
  }
})

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatTime(value) {
  if (!value) return '不限'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function dayKeyOf(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function actionableMessage(err) {
  const message = err && err.message ? err.message : String(err || '未知错误')
  const status = err && err.status
  if (status === 401) return '登录已失效，请重新登录后再操作。'
  if (status === 403) return `无权访问该内容（${message}）。请确认页面顶部选中的课程 / 教学班；若已被移出班级，旧链接会立即失效。`
  if (status === 404) return `内容不存在或已被撤回（${message}）。请刷新时间轴后重试。`
  if (status === 409) return `状态已变化（${message}）。请刷新时间轴，确认最新状态后再操作。`
  if (status >= 500) return `服务暂时不可用（${message}）。请稍后重试；若持续失败，请联系管理员并提供操作时间。`
  return message
}

function toNodes(rows) {
  const grouped = new Map()
  rows.forEach(row => {
    const key = String(row.assignmentId)
    if (!grouped.has(key)) grouped.set(key, { row, itemCount: 0 })
    if (row.assignmentItemId) grouped.get(key).itemCount += 1
  })
  return [...grouped.values()].map(({ row, itemCount }) => {
    const submitted = Boolean(row.submitted)
    const tags = [`${itemCount} 道题`, submitted ? '已提交' : '待完成']
    if (row.answerVisible) tags.push('反馈已公布')
    return {
      key: `quiz-${row.assignmentId}`,
      kind: 'QUIZ',
      id: row.assignmentId,
      title: row.title || '预习任务',
      subtitle: row.instructions || row.stem || '',
      opensAt: row.opensAt || null,
      closesAt: row.closesAt || null,
      time: row.opensAt || null,
      state: String(row.state || 'OPEN').toUpperCase(),
      tags,
      meta: { ...row, itemCount, submitted }
    }
  })
}

function withSeq(source) {
  const buckets = new Map()
  source.forEach(node => {
    const key = dayKeyOf(node.opensAt || node.time)
    if (!key) return
    if (!buckets.has(key)) buckets.set(key, [])
    buckets.get(key).push(node)
  })
  buckets.forEach(items => {
    items.sort((a, b) => new Date(a.opensAt || a.time).getTime() - new Date(b.opensAt || b.time).getTime())
    items.forEach((node, index) => {
      node.seq = index + 1
      node.total = items.length
    })
  })
  return source
}

function stateOf(opensAt, closesAt, now) {
  const currentTime = now ? new Date(now).getTime() : Date.now()
  const opens = opensAt ? new Date(opensAt).getTime() : null
  const closes = closesAt ? new Date(closesAt).getTime() : null
  if (opens != null && Number.isFinite(opens) && currentTime < opens) return 'UPCOMING'
  if (closes != null && Number.isFinite(closes) && currentTime >= closes) return 'CLOSED'
  return 'OPEN'
}

/** 课前准备发布的资料阅读环节。 */
function toMaterialNode(item) {
  const tags = [`V${item.versionNo || 1}`, `${Number(item.pageCount || 0)} 页`]
  if (item.mediaKind) tags.push(item.mediaKind)
  return {
    key: `prep-material-${item.releaseId || item.versionId}`,
    kind: 'MATERIAL',
    id: item.versionId,
    title: item.title || '资料阅读',
    subtitle: item.closesAt ? `开放至 ${formatTime(item.closesAt)}` : '长期开放',
    opensAt: item.opensAt || null,
    closesAt: item.closesAt || null,
    time: item.opensAt || null,
    state: stateOf(item.opensAt, item.closesAt, item.serverTime),
    tags,
    meta: item
  }
}

/** 课前准备发布的讨论环节。 */
function toDiscussionNode(row) {
  const tags = []
  if (row.category) tags.push(row.category)
  if (row.knowledgePoint) tags.push(row.knowledgePoint)
  tags.push(`${Number(row.replyCount || 0)} 回复`)
  const status = String(row.status || '').toUpperCase()
  return {
    key: `discussion-${row.topicId}`,
    kind: 'DISCUSSION',
    id: row.topicId,
    title: row.title || '讨论话题',
    subtitle: row.content || '',
    opensAt: row.opensAt || null,
    closesAt: row.closesAt || null,
    time: row.opensAt || row.createdAt || null,
    state: status === 'ARCHIVED' ? 'ARCHIVED' : stateOf(row.opensAt, row.closesAt, row.serverTime),
    tags,
    meta: row
  }
}

const nodes = computed(() => withSeq([
  ...toNodes(tasks.value),
  ...materials.value.map(toMaterialNode),
  ...discussions.value.map(toDiscussionNode)
]))

const visibleNodes = computed(() => {
  if (stateFilter.value === 'all') return nodes.value
  if (stateFilter.value === 'todo') {
    return nodes.value.filter(node => node.kind === 'QUIZ' && !node.meta.submitted && node.state !== 'CLOSED')
  }
  if (stateFilter.value === 'submitted') {
    return nodes.value.filter(node => node.kind === 'QUIZ' && node.meta.submitted)
  }
  return nodes.value.filter(node => node.state === 'CLOSED')
})

const stats = computed(() => {
  const quizNodes = nodes.value.filter(node => node.kind === 'QUIZ')
  const total = quizNodes.length
  const submitted = quizNodes.filter(node => node.meta.submitted).length
  return {
    total,
    submitted,
    todo: quizNodes.filter(node => !node.meta.submitted && node.state !== 'CLOSED').length,
    closed: quizNodes.filter(node => node.state === 'CLOSED').length,
    percent: total ? Math.round(submitted / total * 100) : 0
  }
})

const selectedNode = computed(() => nodes.value.find(node => node.key === selectedKey.value) || null)
const items = computed(() => (detail.value && Array.isArray(detail.value.items) ? detail.value.items : []))
const detailState = computed(() => (detail.value && detail.value.state) || (selectedNode.value && selectedNode.value.state) || '')
const canSubmit = computed(() => Boolean(
  detail.value && detail.value.state === 'OPEN' && !detail.value.submitted && items.value.length
))
const blockReason = computed(() => submitBlockReason(detail.value))
const submittedTitle = computed(() => {
  const submission = (detail.value && detail.value.submission) || {}
  const late = submission.late ? '（逾期提交）' : ''
  return `已于 ${formatTime(submission.submittedAt)} 提交${late}`
})

function stateLabel(state) {
  return STATE_LABELS[state] || state || '未知'
}

function stateTagType(state) {
  if (state === 'OPEN') return 'success'
  if (state === 'UPCOMING') return 'warning'
  return 'info'
}

function seqText(node) {
  return node && node.seq ? `第 ${node.seq} / ${node.total} 环节` : '不计入序号'
}

function questionKindLabel(kind) {
  return QUESTION_KIND_LABELS[kind] || '题目'
}

function optionsOf(item) {
  return Array.isArray(item.options) ? item.options : []
}

function formatAnswer(answer, kind) {
  if (answer == null) return '未设置'
  if (kind === 'TRUE_FALSE') return answer.correctBoolean ? '正确' : '错误'
  if (kind === 'SINGLE') return `选项 ${Number(answer.choiceIndex) + 1}`
  if (kind === 'MULTIPLE') return `选项 ${(answer.choiceIndices || []).map(index => Number(index) + 1).join('、')}`
  if (kind === 'SHORT_TEXT') return answer.referenceAnswer || '参考答案'
  return String(answer)
}

function submitBlockReason(data) {
  if (!data) return ''
  if (data.state === 'UPCOMING') return `本环节尚未开放，${formatTime(data.opensAt)} 开放后才能提交。`
  if (data.state === 'CLOSED') {
    return `本环节已于 ${formatTime(data.closesAt)} 截止，服务端不再接收提交；当前填写的内容不会写入完成记录。`
  }
  if (data.submitted) {
    return `本环节已于 ${formatTime(data.submission && data.submission.submittedAt)} 提交，无需重复提交。`
  }
  if (!(data.items || []).length) return '本环节没有可提交的题目。'
  if (data.state !== 'OPEN') return `本环节当前为「${stateLabel(data.state)}」，暂不可提交。`
  return ''
}

function resetAnswers() {
  Object.keys(answers).forEach(key => {
    delete answers[key]
  })
}

function initAnswers() {
  resetAnswers()
  items.value.forEach(item => {
    const id = String(item.assignmentItemId)
    answers[id] = item.questionKind === 'MULTIPLE' ? [] : ''
  })
}

function captureDraft() {
  const data = detail.value
  if (!data || data.submitted) return
  const id = String(data.assignmentId || '')
  if (!id) return
  answerDrafts.set(id, JSON.parse(JSON.stringify(answers)))
}

function restoreDraft() {
  const data = detail.value
  if (!data || data.submitted) return
  const draft = answerDrafts.get(String(data.assignmentId || ''))
  if (!draft) return
  Object.keys(draft).forEach(id => {
    if (Object.prototype.hasOwnProperty.call(answers, id)) {
      const value = draft[id]
      answers[id] = Array.isArray(value) ? value.slice() : value
    }
  })
}

async function loadDetail(node) {
  captureDraft()
  detail.value = null
  detailError.value = ''
  resetAnswers()
  if (!node || !bindingId.value) return
  const token = ++detailToken
  detailLoading.value = true
  try {
    const res = await getStudentPreview(node.id, bindingId.value)
    if (token !== detailToken) return
    detail.value = res.data || null
    initAnswers()
    restoreDraft()
  } catch (err) {
    if (token !== detailToken) return
    detailError.value = actionableMessage(err)
  } finally {
    if (token === detailToken) detailLoading.value = false
  }
}

async function handleSubmit() {
  if (!canSubmit.value || submitting.value) return
  const payload = {}
  for (const item of items.value) {
    const id = String(item.assignmentItemId)
    const value = answers[id]
    if (item.questionKind === 'SINGLE') {
      if (value === '' || value === null || value === undefined) {
        ElMessage.warning('请完成所有题目后再提交')
        return
      }
      payload[id] = { choiceIndex: Number(value) }
    } else if (item.questionKind === 'TRUE_FALSE') {
      if (value === '' || value === null || value === undefined) {
        ElMessage.warning('请完成所有题目后再提交')
        return
      }
      payload[id] = { correctBoolean: Number(value) === 0 }
    } else if (item.questionKind === 'MULTIPLE') {
      if (!Array.isArray(value) || !value.length) {
        ElMessage.warning('请完成所有题目后再提交')
        return
      }
      payload[id] = { choiceIndices: value.map(Number).sort((a, b) => a - b) }
    } else {
      if (!String(value || '').trim()) {
        ElMessage.warning('请完成所有题目后再提交')
        return
      }
      payload[id] = String(value).trim()
    }
  }
  submitting.value = true
  try {
    const assignmentId = (detail.value && detail.value.assignmentId) || (selectedNode.value && selectedNode.value.id)
    await submitStudentPreview(assignmentId, bindingId.value, { answers: payload })
    ElMessage.success('预习已提交')
    const key = selectedKey.value
    await loadTasks()
    const node = nodes.value.find(item => item.key === key)
    if (node) {
      selectedKey.value = key
      await loadDetail(node)
    } else {
      selectedKey.value = ''
      detail.value = null
    }
    answerDrafts.delete(String(assignmentId))
  } catch (err) {
    ElMessage.error(actionableMessage(err))
  } finally {
    submitting.value = false
  }
}

function extensionOf(blob) {
  const type = (blob && blob.type) || ''
  if (type.includes('pdf')) return '.pdf'
  if (type.includes('word')) return '.docx'
  if (type.includes('presentation')) return '.pptx'
  if (type.includes('video')) return '.mp4'
  if (type.startsWith('image/')) return `.${type.split('/')[1].split('+')[0]}`
  return ''
}

async function downloadResource(resource) {
  if (!resource || !bindingId.value || downloadingId.value) return
  downloadingId.value = resource.versionId
  try {
    const blob = await downloadVersion(resource.versionId, bindingId.value)
    if (!(blob instanceof Blob)) throw new Error('接口未返回文件')
    saveAs(blob, `${resource.title || '课程资料'}${extensionOf(blob)}`)
  } catch (err) {
    ElMessage.error(actionableMessage(err))
  } finally {
    downloadingId.value = null
  }
}

function revokeMaterialUrls() {
  materialPages.value.forEach(url => URL.revokeObjectURL(url))
  materialPages.value = []
  if (materialVideoUrl.value) URL.revokeObjectURL(materialVideoUrl.value)
  materialVideoUrl.value = ''
}

/** 打开课前准备发布的资料阅读环节。 */
async function openMaterial(node) {
  if (!node || !bindingId.value) return
  const versionId = node.meta.versionId || node.id
  materialNode.value = node
  materialVisible.value = true
  materialDetail.value = null
  materialError.value = ''
  revokeMaterialUrls()
  const token = ++materialToken
  materialLoading.value = true
  try {
    const res = await getVersion(versionId, bindingId.value)
    if (token !== materialToken) return
    const data = res.data || {}
    materialDetail.value = data
    if (data.mediaKind === 'VIDEO') {
      const blob = await downloadVersion(versionId, bindingId.value)
      if (token !== materialToken) return
      if (blob instanceof Blob) materialVideoUrl.value = URL.createObjectURL(blob)
      return
    }
    const pages = Array.isArray(data.pages) ? data.pages : []
    for (const page of pages) {
      const blob = await getVersionPage(versionId, page.pageNo, bindingId.value)
      if (token !== materialToken) {
        return
      }
      materialPages.value.push(URL.createObjectURL(blob))
    }
  } catch (err) {
    if (token === materialToken) materialError.value = actionableMessage(err)
  } finally {
    if (token === materialToken) materialLoading.value = false
  }
}

function downloadMaterial() {
  const meta = materialNode.value && materialNode.value.meta
  if (meta) downloadResource(meta)
}

/** 课前准备发布的讨论环节，跳转到学生讨论页。 */
function openDiscussion(node) {
  if (!node || !bindingId.value) return
  const { assignmentId, resourceId, ...query } = route.query
  router.push({
    name: 'course-detail',
    params: route.params,
    query: { ...query, courseSpaceId: route.params.courseId, tab: 'discuss', bindingId: bindingId.value, topicId: node.id }
  })
}

watch(materialVisible, visible => {
  if (!visible) {
    materialToken += 1
    revokeMaterialUrls()
  }
})

async function loadTasks() {
  loadError.value = ''
  if (!bindingId.value) return
  const token = ++listToken
  loadingList.value = true
  try {
    const [previewRes, materialRes, discussionRes] = await Promise.all([
      listStudentPreviews(bindingId.value),
      listStudentPrepMaterials(bindingId.value).catch(() => null),
      listDiscussions(bindingId.value).catch(() => null)
    ])
    if (token !== listToken) return
    tasks.value = Array.isArray(previewRes.data) ? previewRes.data : []
    materials.value = materialRes && Array.isArray(materialRes.data) ? materialRes.data : []
    discussions.value = discussionRes && Array.isArray(discussionRes.data) ? discussionRes.data : []
    openTargetFromQuery()
  } catch (err) {
    if (token !== listToken) return
    loadError.value = actionableMessage(err)
  } finally {
    if (token === listToken) loadingList.value = false
  }
}

function handleNodeSelect(node) {
  if (!node) return
  if (node.kind === 'DISCUSSION') {
    openDiscussion(node)
    return
  }
  if (node.kind === 'MATERIAL') {
    if (selectedKey.value !== node.key) {
      selectedKey.value = node.key
    }
    openMaterial(node)
    return
  }
  if (node.key === selectedKey.value) {
    detailVisible.value = true
    return
  }
  captureDraft()
  selectedKey.value = node.key
  indepCurrentNode.value = node
  loadDetail(node)
  detailVisible.value = true
}

function openTargetFromQuery() {
  const assignmentId = route.query.assignmentId
  if (!assignmentId) return
  const node = nodes.value.find(item => item.kind === 'QUIZ' && String(item.id) === String(assignmentId))
  if (node && node.key !== selectedKey.value) handleNodeSelect(node)
}

async function handleSelect(value) {
  captureDraft()
  select(value)
  selectedKey.value = ''
  detailVisible.value = false
  detail.value = null
  detailToken += 1
  await loadTasks()
}

async function refresh() {
  await load()
  await loadTasks()
}

onMounted(async () => {
  await load()
  await loadTasks()
})

onBeforeUnmount(() => {
  listToken += 1
  detailToken += 1
  materialToken += 1
  revokeMaterialUrls()
})
</script>

<style scoped>
.g3-page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.g3-title-block h2 { margin: 0; font-size: 20px; color: #303133; }
.g3-title-block p { margin: 4px 0; color: #606266; }
.g3-head-note { font-size: 12px; color: #909399; }
.g3-head-filter { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #606266; }
.g3-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 14px 0 10px; }
.g3-stat { background: #f5f7fa; border-radius: 6px; padding: 10px 12px; display: flex; flex-direction: column; gap: 2px; }
.g3-stat small { font-size: 12px; color: #909399; }
.g3-stat strong { font-size: 20px; color: #303133; }
.g3-progress { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.g3-progress-bar { flex: 1; max-width: 420px; }
.g3-gap { margin-bottom: 12px; }
.g3-layout { display: block; }
.g3-tl-main,
.g3-tl-detail { min-width: 0; }
.g3-detail-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.g3-kind { font-size: 14px; font-weight: 600; color: #14b8a6; }
.g3-detail-body { min-height: 160px; }
.g3-detail-title { margin: 0 0 6px; font-size: 16px; color: #303133; }
.g3-detail-time { margin: 0 0 12px; }
.g3-question { border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; margin-bottom: 12px; }
.g3-question-stem { display: flex; align-items: flex-start; gap: 6px; font-size: 14px; color: #303133; line-height: 1.6; }
.g3-q-no { flex: none; width: 20px; height: 20px; border-radius: 50%; background: #e8f4ff; color: #409eff; font-size: 12px; display: inline-flex; align-items: center; justify-content: center; margin-top: 2px; }
.g3-options { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; margin-top: 10px; }
.g3-option { margin-right: 0; }
.g3-feedback { margin-top: 10px; padding: 8px 10px; background: #f0f9eb; border-radius: 4px; font-size: 13px; color: #529b2e; line-height: 1.7; }
.g3-submit { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-top: 6px; }
.g3-submitted { margin-top: 12px; }
.g3-resource-links { display: flex; flex-wrap: wrap; gap: 8px; }
.g3-hint { font-size: 12px; color: #909399; }
@media (max-width: 1100px) {
  .g3-layout { grid-template-columns: minmax(0, 1fr); }
  .g3-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
.g3-enter-study { margin-left: auto; }
.g3-study-drawer .el-drawer__body { padding: 0; overflow: hidden; display: flex; flex-direction: column; }
.g3-independent-head {
  display: flex; align-items: center; gap: 10px; padding: 10px 16px;
  background: #f5f7fa; border-bottom: 1px solid #ebeef5; flex-shrink: 0;
}
.g3-independent-title {
  flex: 1; font-size: 15px; font-weight: 600; color: #303133;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0;
}
.g3-independent-body { flex: 1; overflow-y: auto; padding: 16px 24px 32px; }
.g3-material-body { min-height: 120px; }
.g3-material-meta { margin: 0 0 10px; }
.g3-material-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.g3-material-video video { width: 100%; max-height: 60vh; background: #000; border-radius: 6px; }
.g3-material-pages { display: flex; flex-direction: column; gap: 10px; max-height: 60vh; overflow-y: auto; }
.g3-material-pages img { width: 100%; border: 1px solid #ebeef5; border-radius: 4px; }
</style>
