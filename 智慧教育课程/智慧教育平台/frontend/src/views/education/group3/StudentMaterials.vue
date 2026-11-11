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
          <h2>课程资料</h2>
          <p>{{ current ? `${current.courseTitle} · ${current.className}` : '—' }}</p>
          <span class="g3-head-note">
            这里只显示本人教学班、当前已开放的内容；未开放、已撤回或不属于本班的内容不会出现，旧链接也不会继续有效。
          </span>
        </div>
        <div class="g3-head-filter">
          <span>筛选</span>
          <el-select v-model="stateFilter" style="width: 150px">
            <el-option v-for="item in STATE_FILTERS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
      </div>

      <div class="g3-stats">
        <div class="g3-stat"><small>资料总数</small><strong>{{ stats.total }}</strong></div>
        <div class="g3-stat"><small>进行中</small><strong>{{ stats.open }}</strong></div>
        <div class="g3-stat"><small>未开放</small><strong>{{ stats.upcoming }}</strong></div>
        <div class="g3-stat"><small>已截止</small><strong>{{ stats.closed }}</strong></div>
      </div>

      <el-alert v-if="loadError" :title="loadError" type="warning" show-icon :closable="false" class="g3-gap" />

      <div class="g3-layout">
        <div v-loading="loadingList" class="g3-tl-main">
          <StudentTimeline
            :nodes="visibleNodes"
            :selected-key="selectedKey"
            :server-now="serverNow"
            mode="materials"
            empty-hint="教师发布资料后，资料环节会出现在这条时间轴上"
            @select="handleNodeSelect"
          />
        </div>

        <div class="g3-tl-detail">
          <el-card v-if="detailNode" shadow="never" class="g3-detail">
            <template #header>
              <div class="g3-detail-head">
                <span class="g3-kind">资料阅读</span>
                <el-tag size="small" effect="plain">{{ seqText(detailNode) }}</el-tag>
                <el-tag size="small" :type="stateTagType(detailNode.state)">{{ stateLabel(detailNode.state) }}</el-tag>
                <el-button
                  class="g3-download"
                  size="small"
                  :loading="downloading"
                  @click="handleDownload(detailNode)"
                >
                  下载
                </el-button>
                <el-button
                  class="g3-enter-reader"
                  size="small"
                  type="primary"
                  plain
                  @click="showIndependent = true"
                >
                  进入阅读
                </el-button>
              </div>
            </template>

            <h3 class="g3-detail-title">{{ detailNode.title }}</h3>

            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="环节序号">{{ seqText(detailNode) }}</el-descriptions-item>
              <el-descriptions-item label="版本">V{{ detailNode.meta.versionNo || 1 }}</el-descriptions-item>
              <el-descriptions-item label="开放时间">{{ formatTime(detailNode.opensAt) }}</el-descriptions-item>
              <el-descriptions-item label="开放至">{{ formatTime(detailNode.closesAt) }}</el-descriptions-item>
              <el-descriptions-item label="预览页">{{ Number(detailNode.meta.pageCount || 0) }} 页</el-descriptions-item>
              <el-descriptions-item label="媒体类型">{{ detailNode.meta.mediaKind || '文件' }}</el-descriptions-item>
            </el-descriptions>

            <el-divider content-position="left">资料预览</el-divider>

            <div v-loading="readerLoading" class="g3-reader">
              <el-alert v-if="readerError" :title="readerError" type="error" show-icon :closable="false">
                <el-button size="small" @click="loadReader(detailNode)">重试</el-button>
              </el-alert>

              <template v-else-if="versionDetail">
                <div v-if="versionDetail.mediaKind === 'LINK' && versionDetail.externalUri" class="g3-reader-link">
                  <el-link type="primary" :href="versionDetail.externalUri" target="_blank" rel="noopener noreferrer">
                    打开外部链接
                  </el-link>
                </div>

                <div v-else-if="videoUrl" class="g3-reader-box">
                  <video :src="videoUrl" controls class="g3-reader-video"></video>
                </div>

                <template v-else-if="pageUrls.length">
                  <div class="g3-reader-box">
                    <el-image
                      :src="pageUrls[pageIndex]"
                      fit="contain"
                      class="g3-reader-image"
                      :preview-src-list="pageUrls"
                      :initial-index="pageIndex"
                      preview-teleported
                    />
                  </div>
                  <div class="g3-page-nav">
                    <el-button size="small" :disabled="pageIndex === 0" @click="pageIndex -= 1">上一页</el-button>
                    <span class="g3-hint">{{ pageIndex + 1 }} / {{ pageUrls.length }}</span>
                    <el-button
                      size="small"
                      :disabled="pageIndex === pageUrls.length - 1"
                      @click="pageIndex += 1"
                    >
                      下一页
                    </el-button>
                  </div>
                </template>

                <el-empty v-else description="该资料没有分页预览，可直接下载原文件阅读" :image-size="80">
                  <el-button size="small" @click="handleDownload(detailNode)">下载原文件</el-button>
                </el-empty>
              </template>

              <el-empty v-else description="选择资料后显示预览" :image-size="80" />
            </div>

            <p class="g3-hint g3-reader-note">
              每次预览与下载都会重新校验身份与发布范围；撤回后旧地址会立即失效。资料阅读不产生服务端已读记录。
            </p>
          </el-card>

          <el-card v-else shadow="never" class="g3-detail">
            <template #header><span>环节详情</span></template>
            <el-empty description="选择一个环节" :image-size="90">
              <p class="g3-hint">资料环节可预览或下载；预习环节请在「预习习题」页面作答。</p>
            </el-empty>
          </el-card>
        </div>
      </div>
    </template>

    <el-drawer
      v-model="showIndependent"
      size="92%"
      direction="rtl"
      :with-header="false"
      :modal="false"
      append-to-body
      class="g3-reader-drawer"
    >
      <div class="g3-independent-head">
        <el-button size="small" @click="showIndependent = false">← 返回</el-button>
        <el-button
          size="small"
          :disabled="!prevNode"
          @click="jumpNode(prevNode)"
        >上一个环节</el-button>
        <el-tag size="default" effect="plain">
          {{ seqText(detailNode || currentIndependentNode) }}
        </el-tag>
        <span class="g3-independent-title">
          {{ (detailNode || currentIndependentNode) ? (detailNode || currentIndependentNode).title : '—' }}
        </span>
        <el-button
          size="small"
          :disabled="!nextNode"
          @click="jumpNode(nextNode)"
        >下一个环节</el-button>
        <el-button
          size="small"
          :loading="downloading"
          :disabled="!detailNode"
          @click="handleDownload(detailNode)"
        >下载</el-button>
      </div>

      <div v-if="detailNode" class="g3-independent-body">
        <div v-loading="readerLoading" class="g3-reader">
          <el-alert v-if="readerError" :title="readerError" type="error" show-icon :closable="false">
            <el-button size="small" @click="loadReader(detailNode)">重试</el-button>
          </el-alert>

          <template v-else-if="versionDetail">
            <div v-if="versionDetail.mediaKind === 'LINK' && versionDetail.externalUri" class="g3-reader-link">
              <el-link type="primary" :href="versionDetail.externalUri" target="_blank" rel="noopener noreferrer">
                打开外部链接
              </el-link>
            </div>

            <div v-else-if="videoUrl" class="g3-reader-box">
              <video :src="videoUrl" controls class="g3-reader-video"></video>
            </div>

            <template v-else-if="pageUrls.length">
              <div class="g3-reader-box">
                <el-image
                  :src="pageUrls[pageIndex]"
                  fit="contain"
                  class="g3-reader-image"
                  :preview-src-list="pageUrls"
                  :initial-index="pageIndex"
                  preview-teleported
                />
              </div>
              <div class="g3-page-nav">
                <el-button size="small" :disabled="pageIndex === 0" @click="pageIndex -= 1">上一页</el-button>
                <span class="g3-hint">{{ pageIndex + 1 }} / {{ pageUrls.length }}</span>
                <el-button
                  size="small"
                  :disabled="pageIndex === pageUrls.length - 1"
                  @click="pageIndex += 1"
                >
                  下一页
                </el-button>
              </div>
            </template>

            <el-empty v-else description="该资料没有分页预览，可直接下载原文件阅读" :image-size="80">
              <el-button size="small" @click="handleDownload(detailNode)">下载原文件</el-button>
            </el-empty>
          </template>

          <el-empty v-else description="选择资料后显示预览" :image-size="80" />
        </div>

        <div class="g3-independent-meta">
          <el-descriptions :column="4" size="small" border>
            <el-descriptions-item label="开放时间">{{ formatTime(detailNode.opensAt) }}</el-descriptions-item>
            <el-descriptions-item label="开放至">{{ formatTime(detailNode.closesAt) }}</el-descriptions-item>
            <el-descriptions-item label="预览页">{{ Number(detailNode.meta.pageCount || 0) }} 页</el-descriptions-item>
            <el-descriptions-item label="媒体类型">{{ detailNode.meta.mediaKind || '文件' }}</el-descriptions-item>
          </el-descriptions>
          <p class="g3-hint">每次预览与下载都会重新校验身份；撤回后旧地址立即失效。</p>
        </div>
      </div>

      <el-empty v-else description="选择环节后进入阅读" :image-size="90" />
    </el-drawer>
  </div>
</template>

<script setup name="StudentMaterials">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { saveAs } from 'file-saver'
import { listStudentMaterials, getVersion, getVersionPage, downloadVersion } from '@/api/education/group3'
import Group3ContextBar from './Group3ContextBar.vue'
import StudentTimeline from './StudentTimeline.vue'
import { useGroup3Binding } from './useGroup3Binding.js'

const props = defineProps({ contextBindingId: { type: String, default: '' } })
const { list, bindingId, current, loading, error, load, select } = useGroup3Binding(() => props.contextBindingId)

const STATE_FILTERS = [
  { value: 'all', label: '全部资料' },
  { value: 'OPEN', label: '进行中' },
  { value: 'UPCOMING', label: '未开放' },
  { value: 'CLOSED', label: '已截止' }
]

const STATE_LABELS = {
  UPCOMING: '未开放',
  OPEN: '进行中',
  CLOSED: '已截止',
  ARCHIVED: '已归档'
}

const materials = ref([])
const stateFilter = ref('all')
const selectedKey = ref('')
const loadingList = ref(false)
const loadError = ref('')
const serverNow = ref('')

const versionDetail = ref(null)
const pageUrls = ref([])
const pageIndex = ref(0)
const videoUrl = ref('')
const readerLoading = ref(false)
const readerError = ref('')
const downloading = ref(false)

let listToken = 0
let readerToken = 0

// 独立模式
const showIndependent = ref(false)
const currentIndependentNode = ref(null)

const currentIndex = computed(() => {
  if (!selectedKey.value) return -1
  return visibleNodes.value.findIndex(n => n.key === selectedKey.value)
})
const prevNode = computed(() => {
  const idx = currentIndex.value
  return idx > 0 ? visibleNodes.value[idx - 1] : null
})
const nextNode = computed(() => {
  const idx = currentIndex.value
  if (idx < 0) return null
  return idx < visibleNodes.value.length - 1 ? visibleNodes.value[idx + 1] : null
})

function jumpNode(target) {
  if (!target) return
  handleNodeSelect(target)
}

// 独立模式打开时自动聚焦当前环节（若主面板有选中）
watch(showIndependent, (v) => {
  if (v && selectedKey.value) {
    currentIndependentNode.value = detailNode.value
    // 确保阅读器已加载
    if (!versionDetail.value) loadReader(detailNode.value)
  } else if (v) {
    // 没有选中，默认选第一个可导航环节
    const firstNavigable = visibleNodes.value.find(n => n.state === 'OPEN' || n.state === 'UPCOMING') || visibleNodes.value[0]
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

function timeState(opensAt, closesAt, now) {
  const currentTime = now ? new Date(now).getTime() : Date.now()
  const opens = opensAt ? new Date(opensAt).getTime() : null
  const closes = closesAt ? new Date(closesAt).getTime() : null
  if (opens != null && Number.isFinite(opens) && currentTime < opens) return 'UPCOMING'
  if (closes != null && Number.isFinite(closes) && currentTime >= closes) return 'CLOSED'
  return 'OPEN'
}

function toNode(item) {
  const tags = [`V${item.versionNo || 1}`, `${Number(item.pageCount || 0)} 页`]
  if (item.mediaKind) tags.push(item.mediaKind)
  return {
    key: `material-${item.releaseId || item.versionId}`,
    kind: 'MATERIAL',
    id: item.versionId,
    title: item.title || '课程资料',
    subtitle: item.closesAt ? `开放至 ${formatTime(item.closesAt)}` : '长期开放',
    opensAt: item.opensAt || null,
    closesAt: item.closesAt || null,
    time: item.opensAt || null,
    state: timeState(item.opensAt, item.closesAt, item.serverTime),
    tags,
    meta: item
  }
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

const nodes = computed(() => withSeq(materials.value.map(toNode)))

const visibleNodes = computed(() => {
  if (stateFilter.value === 'all') return nodes.value
  return nodes.value.filter(node => node.state === stateFilter.value)
})

const stats = computed(() => ({
  total: nodes.value.length,
  open: nodes.value.filter(node => node.state === 'OPEN').length,
  upcoming: nodes.value.filter(node => node.state === 'UPCOMING').length,
  closed: nodes.value.filter(node => node.state === 'CLOSED').length
}))

const detailNode = computed(() => nodes.value.find(node => node.key === selectedKey.value) || null)

function stateLabel(state) {
  return STATE_LABELS[state] || state || '未知'
}

function stateTagType(state) {
  if (state === 'OPEN') return 'success'
  if (state === 'UPCOMING') return 'warning'
  return 'info'
}

function seqText(node) {
  return node.seq ? `第 ${node.seq} / ${node.total} 环节` : '不计入序号'
}

function revokeReaderUrls() {
  pageUrls.value.forEach(url => URL.revokeObjectURL(url))
  pageUrls.value = []
  if (videoUrl.value) URL.revokeObjectURL(videoUrl.value)
  videoUrl.value = ''
}

async function loadReader(node) {
  revokeReaderUrls()
  versionDetail.value = null
  readerError.value = ''
  pageIndex.value = 0
  if (!node || !bindingId.value) return
  const versionId = node.meta.versionId || node.id
  const token = ++readerToken
  readerLoading.value = true
  try {
    const res = await getVersion(versionId, bindingId.value)
    if (token !== readerToken) return
    const detail = res.data || {}
    versionDetail.value = detail
    if (detail.mediaKind === 'VIDEO') {
      const blob = await downloadVersion(versionId, bindingId.value)
      if (token !== readerToken) return
      if (blob instanceof Blob) videoUrl.value = URL.createObjectURL(blob)
      return
    }
    const pages = Array.isArray(detail.pages) ? detail.pages : []
    if (!pages.length) return
    const urls = []
    for (const page of pages) {
      const blob = await getVersionPage(versionId, page.pageNo, bindingId.value)
      if (token !== readerToken) {
        urls.forEach(url => URL.revokeObjectURL(url))
        return
      }
      urls.push(URL.createObjectURL(blob))
      pageUrls.value = urls.slice()
    }
  } catch (err) {
    if (token === readerToken) readerError.value = actionableMessage(err)
  } finally {
    if (token === readerToken) readerLoading.value = false
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

async function handleDownload(node) {
  if (!node || !bindingId.value || downloading.value) return
  const versionId = node.meta.versionId || node.id
  downloading.value = true
  try {
    const blob = await downloadVersion(versionId, bindingId.value)
    if (!(blob instanceof Blob)) throw new Error('接口未返回文件')
    saveAs(blob, `${node.title || '课程资料'}${extensionOf(blob)}`)
  } catch (err) {
    ElMessage.error(actionableMessage(err))
  } finally {
    downloading.value = false
  }
}

async function loadMaterials() {
  revokeReaderUrls()
  readerToken += 1
  versionDetail.value = null
  readerError.value = ''
  readerLoading.value = false
  materials.value = []
  selectedKey.value = ''
  loadError.value = ''
  if (!bindingId.value) return
  const token = ++listToken
  loadingList.value = true
  try {
    const res = await listStudentMaterials(bindingId.value)
    if (token !== listToken) return
    const rows = Array.isArray(res.data) ? res.data : []
    serverNow.value = rows.length && rows[0].serverTime ? rows[0].serverTime : ''
    materials.value = rows
  } catch (err) {
    if (token !== listToken) return
    loadError.value = actionableMessage(err)
  } finally {
    if (token === listToken) loadingList.value = false
  }
}

function handleNodeSelect(node) {
  if (!node || node.key === selectedKey.value) return
  selectedKey.value = node.key
  currentIndependentNode.value = node
  loadReader(node)
}

async function handleSelect(value) {
  select(value)
  await loadMaterials()
}

async function refresh() {
  await load()
  await loadMaterials()
}

onMounted(async () => {
  await load()
  await loadMaterials()
})

onBeforeUnmount(() => {
  readerToken += 1
  listToken += 1
  revokeReaderUrls()
})
</script>

<style scoped>
.g3-page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.g3-title-block h2 { margin: 0; font-size: 20px; color: #303133; }
.g3-title-block p { margin: 4px 0; color: #606266; }
.g3-head-note { font-size: 12px; color: #909399; }
.g3-head-filter { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #606266; }
.g3-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 14px 0; }
.g3-stat { background: #f5f7fa; border-radius: 6px; padding: 10px 12px; display: flex; flex-direction: column; gap: 2px; }
.g3-stat small { font-size: 12px; color: #909399; }
.g3-stat strong { font-size: 20px; color: #303133; }
.g3-gap { margin-bottom: 12px; }
.g3-layout { display: grid; grid-template-columns: minmax(300px, 5fr) minmax(340px, 7fr); gap: 16px; align-items: start; }
.g3-tl-main { min-width: 0; }
.g3-tl-detail { min-width: 0; }
.g3-detail-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.g3-kind { font-size: 14px; font-weight: 600; color: #409eff; }
.g3-download { margin-left: auto; }
.g3-detail-title { margin: 0 0 12px; font-size: 16px; color: #303133; }
.g3-reader { min-height: 160px; }
.g3-reader-box { background: #f5f7fa; border-radius: 6px; padding: 8px; display: flex; justify-content: center; }
.g3-reader-image { max-height: 520px; width: 100%; }
.g3-reader-image :deep(img) { max-height: 520px; }
.g3-reader-video { width: 100%; max-height: 480px; border-radius: 4px; }
.g3-reader-link { padding: 16px 0; }
.g3-page-nav { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 10px; }
.g3-reader-note { margin-top: 12px; }
.g3-hint { font-size: 12px; color: #909399; }
@media (max-width: 1100px) {
  .g3-layout { grid-template-columns: minmax(0, 1fr); }
  .g3-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
.g3-enter-reader { margin-left: auto; }
.g3-reader-drawer .el-drawer__body { padding: 0; overflow: hidden; display: flex; flex-direction: column; }
.g3-independent-head {
  display: flex; align-items: center; gap: 10px; padding: 10px 16px;
  background: #f5f7fa; border-bottom: 1px solid #ebeef5; flex-shrink: 0;
}
.g3-independent-title {
  flex: 1; font-size: 15px; font-weight: 600; color: #303133;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0;
}
.g3-independent-body { flex: 1; overflow-y: auto; padding: 16px 24px 32px; }
.g3-independent-body .g3-reader { min-height: 240px; margin-bottom: 16px; }
.g3-independent-meta { margin-top: 16px; }
</style>
