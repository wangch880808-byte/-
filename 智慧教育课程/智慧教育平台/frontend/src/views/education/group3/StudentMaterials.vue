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
      <div class="g3-file-toolbar">
        <div class="g3-crumb">
          <el-icon class="g3-crumb-icon"><Folder /></el-icon>
          <template v-if="activeFolder">
            <span class="g3-crumb-link" @click="leaveFolder">全部文件</span>
            <span class="g3-crumb-sep">›</span>
            <span class="g3-crumb-current">{{ activeFolder.name }}</span>
          </template>
          <span v-else class="g3-crumb-current">全部文件</span>
          <span class="g3-file-count">当前页共 {{ viewMaterials.length }} 个</span>
        </div>
        <div class="g3-file-actions">
          <el-input
            v-model="keyword"
            placeholder="搜索文件名"
            clearable
            style="width: 220px"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button :loading="loadingList" @click="refresh">刷新</el-button>
        </div>
      </div>

      <p v-if="current" class="g3-file-subtitle">{{ current.courseTitle }} · {{ current.className }}</p>

      <StudentProgressBoard
        ref="boardRef"
        :binding-id="bindingId"
        compact
      />

      <el-alert v-if="loadError" :title="loadError" type="warning" show-icon :closable="false" class="g3-gap" />

      <el-table
        ref="tableRef"
        v-loading="loadingList"
        :data="tableRows"
        row-key="rowKey"
        class="g3-file-table"
        empty-text="暂无课程资料，教师发布后会显示在这里"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column label="文件名" min-width="360">
          <template #default="{ row }">
            <div
              class="g3-file-name"
              :class="{ 'is-folder': row.rowType === 'folder' }"
              @click="row.rowType === 'folder' ? enterFolder(row) : openPreview(row)"
            >
              <el-icon v-if="row.rowType === 'folder'" class="g3-file-icon is-folder">
                <Folder />
              </el-icon>
              <el-icon v-else class="g3-file-icon" :class="`is-${fileKind(row)}`">
                <component :is="fileIcon(row)" />
              </el-icon>
              <span class="g3-file-title">{{ row.rowType === 'folder' ? row.folderName : (row.title || '课程资料') }}</span>
              <span v-if="row.rowType === 'folder'" class="g3-file-ext">{{ row.fileCount }} 个文件</span>
              <template v-else>
                <span v-if="fileExtension(row)" class="g3-file-ext">{{ fileExtension(row) }}</span>
                <el-tag v-if="row.taskEnabled" size="small" type="warning" effect="plain">任务点</el-tag>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="学习进度" width="120">
          <template #default="{ row }">
            <span v-if="row.rowType === 'folder'">—</span>
            <template v-else>
              <el-tag v-if="row.taskEnabled && isCompleted(row)" size="small" type="success">已完成</el-tag>
              <el-tag v-else-if="row.taskEnabled" size="small" type="info">未完成</el-tag>
              <span v-else>-</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">
            <span v-if="row.rowType === 'folder'">—</span>
            <span v-else>{{ formatSize(row.byteSize) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建者" width="150">
          <template #default="{ row }">
            <span v-if="row.rowType === 'folder'">—</span>
            <span v-else>{{ row.creatorName || '授课教师' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建日期" width="160">
          <template #default="{ row }">
            <span v-if="row.rowType === 'folder'">—</span>
            <span v-else>{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="right">
          <template #default="{ row }">
            <template v-if="row.rowType !== 'folder'">
              <el-button link type="primary" @click.stop="openPreview(row)">预览</el-button>
              <el-button
                v-if="row.downloadEnabled !== 0"
                link
                type="primary"
                :loading="downloadingId === row.versionId"
                @click.stop="handleDownload(row)"
              >
                下载
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <el-dialog
      v-model="previewVisible"
      :title="previewRow ? displayName(previewRow) : '资料预览'"
      width="900px"
      append-to-body
      class="g3-preview-dialog"
    >
      <div v-loading="readerLoading" class="g3-reader">
        <el-alert v-if="readerError" :title="readerError" type="error" show-icon :closable="false">
          <el-button size="small" @click="loadReader(previewRow)">重试</el-button>
        </el-alert>

        <template v-else-if="versionDetail">
          <div v-if="versionDetail.mediaKind === 'LINK' && versionDetail.externalUri" class="g3-reader-link">
            <el-link type="primary" :href="versionDetail.externalUri" target="_blank" rel="noopener noreferrer">
              打开外部链接
            </el-link>
          </div>

          <div v-else-if="videoUrl" class="g3-reader-box g3-watermark-host">
            <video
              ref="videoRef"
              :src="videoUrl"
              controls
              class="g3-reader-video"
              @timeupdate="onVideoTimeUpdate"
              @ended="onVideoEnded"
            ></video>
            <div v-if="watermarkText" class="g3-watermark">{{ watermarkText }}</div>
          </div>

          <template v-else-if="pageUrls.length">
            <div class="g3-reader-box g3-watermark-host">
              <el-image
                :src="pageUrls[pageIndex]"
                fit="contain"
                class="g3-reader-image"
                :preview-src-list="pageUrls"
                :initial-index="pageIndex"
                preview-teleported
              />
              <div v-if="watermarkText" class="g3-watermark">{{ watermarkText }}</div>
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

          <el-empty v-else :description="downloadAllowed(previewRow) ? '该资料没有分页预览，可直接下载原文件阅读' : '该资料没有分页预览，且教师已关闭下载'" :image-size="80">
            <el-button v-if="downloadAllowed(previewRow)" size="small" @click="handleDownload(previewRow)">下载原文件</el-button>
          </el-empty>
        </template>

        <el-empty v-else description="正在加载资料预览" :image-size="80" />
      </div>

      <template #footer>
        <span v-if="previewRow" class="g3-hint g3-preview-meta">
          开放：{{ formatDate(previewRow.opensAt) }} 至 {{ previewRow.closesAt ? formatDate(previewRow.closesAt) : '长期' }}
        </span>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button
          v-if="downloadAllowed(previewRow)"
          type="primary"
          :loading="downloadingId === (previewRow && previewRow.versionId)"
          @click="handleDownload(previewRow)"
        >
          下载
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="StudentMaterials">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { saveAs } from 'file-saver'
import {
  downloadVersion,
  getVersion,
  getVersionMedia,
  getVersionPage,
  listStudentMaterials,
  listWatchProgress,
  reportWatchProgress
} from '@/api/education/group3'
import Group3ContextBar from './Group3ContextBar.vue'
import StudentProgressBoard from './StudentProgressBoard.vue'
import { useGroup3Binding } from './useGroup3Binding.js'

const props = defineProps({ contextBindingId: { type: String, default: '' } })
const { list, bindingId, current, loading, error, load, select } = useGroup3Binding(() => props.contextBindingId)
const route = useRoute()

const materials = ref([])
const boardRef = ref(null)
const keyword = ref('')
const loadingList = ref(false)
const loadError = ref('')

const previewVisible = ref(false)
const previewRow = ref(null)
const versionDetail = ref(null)
const pageUrls = ref([])
const pageIndex = ref(0)
const videoUrl = ref('')
const readerLoading = ref(false)
const readerError = ref('')
const downloadingId = ref(null)
const videoRef = ref(null)
const watermarkText = ref('')
const progressMap = ref({})
const reportedSeconds = ref({})

let listToken = 0
let readerToken = 0

const tableRef = ref(null)
const activeFolder = ref(null)

// 按文件夹分组统计，供根目录展示
const folders = computed(() => {
  const map = new Map()
  materials.value.forEach(item => {
    const key = item.folderId === null || item.folderId === undefined || item.folderId === ''
      ? ''
      : String(item.folderId)
    if (!key) return
    if (!map.has(key)) {
      map.set(key, { id: key, name: item.folderName || '未命名文件夹', count: 0 })
    }
    map.get(key).count += 1
  })
  return [...map.values()]
})

// 当前层级下的文件（根目录只看未归档文件，进入文件夹后只看该文件夹内文件）
const viewMaterials = computed(() => {
  const scoped = activeFolder.value
    ? materials.value.filter(item => String(item.folderId || '') === String(activeFolder.value.id))
    : materials.value.filter(item => !item.folderId)
  const text = keyword.value.trim().toLowerCase()
  if (!text) return scoped
  return scoped.filter(item => String(item.title || '').toLowerCase().includes(text))
})

// 根目录展示文件夹行 + 未归档文件行；文件夹内只展示文件行
const tableRows = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  const folderRows = activeFolder.value
    ? []
    : folders.value
        .filter(folder => !text || folder.name.toLowerCase().includes(text))
        .map(folder => ({
          rowKey: `f-${folder.id}`,
          rowType: 'folder',
          folderId: folder.id,
          folderName: folder.name,
          fileCount: folder.count
        }))
  const fileRows = viewMaterials.value.map(item => ({ ...item, rowKey: `r-${item.resourceId}` }))
  return [...folderRows, ...fileRows]
})

function enterFolder(row) {
  if (row?.rowType !== 'folder') return
  activeFolder.value = { id: row.folderId, name: row.folderName }
  keyword.value = ''
}

function leaveFolder() {
  activeFolder.value = null
  keyword.value = ''
}

function downloadAllowed(row) {
  return !!row && row.downloadEnabled !== 0
}

function isCompleted(row) {
  const progress = progressMap.value[String(row.resourceId)]
  return !!(progress && progress.completed)
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatSize(bytes) {
  const size = Number(bytes)
  if (!Number.isFinite(size) || size <= 0) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function fileKind(row) {
  const mime = String((row && row.mimeType) || '')
  if (row && row.resourceKind === 'LINK') return 'link'
  if (mime.startsWith('video/')) return 'video'
  if (mime.startsWith('image/')) return 'image'
  if (mime.includes('pdf')) return 'pdf'
  if (mime.includes('word') || mime.includes('document')) return 'word'
  if (mime.includes('presentation') || mime.includes('powerpoint')) return 'ppt'
  return 'file'
}

function fileIcon(row) {
  const kind = fileKind(row)
  if (kind === 'link') return 'Link'
  if (kind === 'video') return 'VideoCamera'
  if (kind === 'image') return 'Picture'
  return 'Document'
}

function fileExtension(row) {
  const key = String((row && row.storageKey) || '')
  const matched = key.match(/\.([A-Za-z0-9]{1,8})$/)
  if (matched) return `.${matched[1].toLowerCase()}`
  const mime = String((row && row.mimeType) || '')
  if (mime.includes('pdf')) return '.pdf'
  if (mime.includes('word')) return '.docx'
  if (mime.includes('presentation')) return '.pptx'
  if (mime.includes('video')) return '.mp4'
  if (mime.startsWith('image/')) return `.${mime.split('/')[1].split('+')[0]}`
  return ''
}

function displayName(row) {
  if (!row) return '课程资料'
  return `${row.title || '课程资料'}${fileExtension(row)}`
}

function actionableMessage(err) {
  const message = err && err.message ? err.message : String(err || '未知错误')
  const status = err && err.status
  if (status === 401) return '登录已失效，请重新登录后再操作。'
  if (status === 403) return `无权访问该内容（${message}）。请确认页面顶部选中的课程 / 教学班；若已被移出班级，旧链接会立即失效。`
  if (status === 404) return `内容不存在或已被撤回（${message}）。请刷新后重试。`
  if (status === 409) return `状态已变化（${message}）。请刷新后确认最新状态。`
  if (status >= 500) return `服务暂时不可用（${message}）。请稍后重试；若持续失败，请联系管理员并提供操作时间。`
  return message
}

function revokeReaderUrls() {
  pageUrls.value.forEach(url => URL.revokeObjectURL(url))
  pageUrls.value = []
  if (videoUrl.value) URL.revokeObjectURL(videoUrl.value)
  videoUrl.value = ''
}

async function loadReader(row) {
  revokeReaderUrls()
  versionDetail.value = null
  readerError.value = ''
  pageIndex.value = 0
  watermarkText.value = ''
  if (!row || !bindingId.value) return
  const token = ++readerToken
  readerLoading.value = true
  try {
    const res = await getVersion(row.versionId, bindingId.value)
    if (token !== readerToken) return
    const detail = res.data || {}
    versionDetail.value = detail
    watermarkText.value = detail.watermarkText || ''
    if (detail.mediaKind === 'VIDEO') {
      const blob = await getVersionMedia(row.versionId, bindingId.value)
      if (token !== readerToken) return
      if (blob instanceof Blob) videoUrl.value = URL.createObjectURL(blob)
      return
    }
    const pages = Array.isArray(detail.pages) ? detail.pages : []
    if (!pages.length) return
    const urls = []
    for (const page of pages) {
      const blob = await getVersionPage(row.versionId, page.pageNo, bindingId.value)
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

function openPreview(row) {
  if (!row) return
  previewRow.value = row
  reportedSeconds.value = {}
  previewVisible.value = true
  loadReader(row)
}

function onVideoTimeUpdate(event) {
  const row = previewRow.value
  if (!row || !row.taskEnabled) return
  const seconds = Math.floor(Number(event.target.currentTime) || 0)
  const last = reportedSeconds.value[String(row.resourceId)] || 0
  // 每满 5 秒上报一次，避免高频请求
  if (seconds < last + 5) return
  reportedSeconds.value[String(row.resourceId)] = seconds
  reportProgress(row, seconds)
}

function onVideoEnded() {
  const row = previewRow.value
  if (!row || !row.taskEnabled) return
  const video = videoRef.value
  const seconds = video ? Math.floor(Number(video.duration) || 0) : (reportedSeconds.value[String(row.resourceId)] || 0)
  reportedSeconds.value[String(row.resourceId)] = seconds
  reportProgress(row, seconds)
}

async function reportProgress(row, seconds) {
  try {
    const res = await reportWatchProgress(row.resourceId, {
      bindingId: Number(bindingId.value),
      watchedSeconds: seconds
    })
    const data = res.data || {}
    if (data.completed) {
      progressMap.value = {
        ...progressMap.value,
        [String(row.resourceId)]: { completed: true, watchedSeconds: data.watchedSeconds }
      }
      if (boardRef.value && typeof boardRef.value.refresh === 'function') boardRef.value.refresh()
    }
  } catch (err) {
    // 进度上报失败不打断观看，静默处理
  }
}

async function loadProgress() {
  if (!bindingId.value) return
  try {
    const res = await listWatchProgress(bindingId.value)
    const rows = Array.isArray(res.data?.progress) ? res.data.progress : []
    const map = {}
    rows.forEach(item => { map[String(item.resourceId)] = item })
    progressMap.value = map
  } catch (err) {
    progressMap.value = {}
  }
}

function openTargetFromQuery() {
  const resourceId = route.query.resourceId
  if (!resourceId) return
  const row = materials.value.find(item => String(item.resourceId) === String(resourceId))
  if (row) openPreview(row)
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

async function handleDownload(row) {
  if (!row || !bindingId.value || downloadingId.value) return
  downloadingId.value = row.versionId
  try {
    const blob = await downloadVersion(row.versionId, bindingId.value)
    if (!(blob instanceof Blob)) throw new Error('接口未返回文件')
    saveAs(blob, `${row.title || '课程资料'}${extensionOf(blob)}`)
  } catch (err) {
    ElMessage.error(actionableMessage(err))
  } finally {
    downloadingId.value = null
  }
}

async function loadMaterials() {
  revokeReaderUrls()
  readerToken += 1
  previewVisible.value = false
  previewRow.value = null
  versionDetail.value = null
  readerError.value = ''
  readerLoading.value = false
  materials.value = []
  loadError.value = ''
  if (!bindingId.value) return
  const token = ++listToken
  loadingList.value = true
  try {
    const res = await listStudentMaterials(bindingId.value)
    if (token !== listToken) return
    materials.value = Array.isArray(res.data) ? res.data : []
    await loadProgress()
    openTargetFromQuery()
  } catch (err) {
    if (token !== listToken) return
    loadError.value = actionableMessage(err)
  } finally {
    if (token === listToken) loadingList.value = false
  }
}

async function handleSelect(value) {
  select(value)
  await loadMaterials()
}

async function refresh() {
  await load()
  await loadMaterials()
}

watch(previewVisible, visible => {
  if (!visible) {
    readerToken += 1
    revokeReaderUrls()
  }
})

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
.g3-student-page { padding: 4px 2px; }
.g3-file-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  gap: 16px; flex-wrap: wrap; margin-bottom: 8px;
}
.g3-crumb { display: flex; align-items: center; gap: 8px; color: #303133; font-size: 15px; }
.g3-crumb-icon { color: #67c23a; font-size: 18px; }
.g3-crumb-current { font-weight: 600; }
.g3-crumb-link { color: #409eff; cursor: pointer; }
.g3-crumb-link:hover { text-decoration: underline; }
.g3-crumb-sep { color: #c0c4cc; }
.g3-file-count { font-size: 12px; color: #909399; }
.g3-file-actions { display: flex; align-items: center; gap: 8px; }
.g3-file-subtitle { margin: 0 0 12px; font-size: 12px; color: #909399; }
.g3-gap { margin-bottom: 12px; }
.g3-file-table {
  border: 1px solid #ebeef5; border-radius: 6px; overflow: hidden;
}
.g3-file-table :deep(th.el-table__cell) { background: #f7f8fa; color: #606266; font-weight: 600; }
.g3-file-table :deep(td.el-table__cell) { padding: 10px 0; }
.g3-file-table :deep(.el-table__row:hover > td.el-table__cell) { background: #f5f9ff; }
.g3-file-name { display: flex; align-items: center; gap: 8px; cursor: pointer; min-width: 0; }
.g3-file-icon { font-size: 18px; color: #909399; flex-shrink: 0; }
.g3-file-icon.is-folder { color: #e6a23c; }
.g3-file-icon.is-pdf { color: #f56c6c; }
.g3-file-icon.is-word { color: #409eff; }
.g3-file-icon.is-ppt { color: #e6a23c; }
.g3-file-icon.is-image { color: #67c23a; }
.g3-file-icon.is-video { color: #8e44ad; }
.g3-file-icon.is-link { color: #409eff; }
.g3-file-title {
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  color: #303133;
}
.g3-file-name:hover .g3-file-title { color: #409eff; }
.g3-file-ext { font-size: 12px; color: #909399; flex-shrink: 0; }
.g3-reader { min-height: 200px; }
.g3-reader-box { background: #f5f7fa; border-radius: 6px; padding: 8px; display: flex; justify-content: center; }
.g3-reader-image { max-height: 560px; width: 100%; }
.g3-reader-image :deep(img) { max-height: 560px; }
.g3-reader-video { width: 100%; max-height: 520px; border-radius: 4px; }
.g3-reader-link { padding: 16px 0; }
.g3-page-nav { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 10px; }
.g3-hint { font-size: 12px; color: #909399; }
.g3-preview-meta { margin-right: 12px; }
.g3-watermark-host { position: relative; overflow: hidden; }
.g3-watermark {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  pointer-events: none; user-select: none; z-index: 5;
  font-size: 42px; font-weight: 600; color: rgba(0, 0, 0, 0.12);
  white-space: nowrap; transform: rotate(-24deg);
  text-shadow: 0 0 1px rgba(255, 255, 255, 0.4);
  word-break: break-all; max-width: 100%; overflow: hidden;
}
</style>
