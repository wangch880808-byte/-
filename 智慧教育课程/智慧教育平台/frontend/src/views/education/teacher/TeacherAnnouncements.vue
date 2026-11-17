<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getTeacherAnnouncementDetail, listTeacherAnnouncements } from '@/api/education/announcement'
import { context } from '@/education/context'
import { classifyNotice503, notice503Detail } from '@/views/education/student/notice-error'

/**
 * 教师工作台「班级消息 / 公告」只读视图（6-04 原型）。
 *
 * 只接列表和详情两个 GET。列表必须带有效教学班 bindingId；未选班时不发请求。
 * 详情只读展示服务端计算的 visibilityState，不提供新建、编辑、发布或归档。
 */

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const statusFilter = ref('')
const listState = ref('idle')
const listMessage = ref('')
const detailOpen = ref(false)
const detailState = ref('idle')
const detail = ref(null)
const detailMessage = ref('')

let listSeq = 0
let detailSeq = 0
let listController = null
let detailController = null
let disposed = false

/**
 * 当前教学班绑定 ID。空字符串表示未选有效教学班。
 * 保持字符串，避免 19 位 ID 精度丢失。
 */
const bindingId = computed(() => {
  const value = context.current?.bindingId
  return value === undefined || value === null || value === '' ? '' : String(value)
})

const VISIBILITY_LABEL = {
  DRAFT: '草稿',
  NOT_YET_OPEN: '未开放',
  OPEN: '展示中',
  CLOSED: '已关闭',
  ARCHIVED: '已归档'
}

/**
 * 按服务端 visibilityState 显示状态；缺失时明确显示未知，不用 status 推算。
 *
 * @param {object} item 公告行或详情
 * @returns {string} 可见状态文案
 */
function visibilityLabel(item) {
  const state = item?.visibilityState
  if (!state) {
    if (typeof console !== 'undefined' && console.error) {
      console.error('[TeacherAnnouncements] 公告响应缺少 visibilityState', item)
    }
    return '未知状态'
  }
  return VISIBILITY_LABEL[state] || ('未知状态（' + state + '）')
}

/**
 * 读取教师公告列表。未选有效教学班时不发请求。
 *
 * @returns {Promise<void>} 列表加载完成
 */
async function loadList() {
  if (disposed) return
  const seq = ++listSeq
  listController?.abort()
  listController = null
  rows.value = []
  total.value = 0
  listMessage.value = ''
  if (!bindingId.value) {
    listState.value = 'need-binding'
    return
  }
  const controller = new AbortController()
  listController = controller
  listState.value = 'loading'

  try {
    const body = await listTeacherAnnouncements({
      bindingId: bindingId.value,
      status: statusFilter.value || undefined,
      pageNum: pageNum.value,
      pageSize
    }, { signal: controller.signal })
    if (seq !== listSeq || disposed) return
    if (!body || !Array.isArray(body.rows) || !Number.isSafeInteger(body.total) || body.total < 0) {
      throw Object.assign(new Error('公告列表响应格式不正确'), { code: 500 })
    }
    rows.value = body.rows
    total.value = body.total
    listState.value = body.rows.length === 0 ? 'empty' : 'ready'
  } catch (error) {
    if (seq !== listSeq || disposed || error?.cancelled) return
    listMessage.value = typeof error?.message === 'string' ? error.message : ''
    if (error?.forbidden || error?.code === 403) {
      listState.value = 'forbidden'
    } else if (error?.code === 503) {
      listState.value = classifyNotice503(error) === 'authorization'
        ? 'unavailable-auth'
        : 'unavailable'
      listMessage.value = notice503Detail(error)
    } else {
      listState.value = 'error'
    }
  }
}

/**
 * 打开教师公告详情。关闭、切班、筛选或卸载后迟到响应不写页面。
 *
 * @param {string} announcementId 公告 ID 字符串
 * @returns {Promise<void>} 详情加载完成
 */
async function openDetail(announcementId) {
  if (disposed) return
  const id = String(announcementId ?? '')
  if (!id) return
  const seq = ++detailSeq
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  detailOpen.value = true
  detailState.value = 'loading'
  detail.value = null
  detailMessage.value = ''

  try {
    const body = await getTeacherAnnouncementDetail(id, { signal: controller.signal })
    if (seq !== detailSeq || disposed || !detailOpen.value) return
    const item = body?.data
    if (!item || item.id !== id || typeof item.title !== 'string' || typeof item.body !== 'string') {
      throw Object.assign(new Error('公告详情响应格式不正确'), { code: 500 })
    }
    detail.value = item
    detailState.value = 'ready'
  } catch (error) {
    if (seq !== detailSeq || disposed || !detailOpen.value || error?.cancelled) return
    detailMessage.value = typeof error?.message === 'string' ? error.message : ''
    if (error?.code === 404) {
      detailState.value = 'notfound'
      detailMessage.value = '公告不存在或不可访问'
    } else if (error?.code === 503) {
      detailState.value = classifyNotice503(error) === 'authorization'
        ? 'unavailable-auth'
        : 'unavailable'
      detailMessage.value = notice503Detail(error)
    } else {
      detailState.value = 'error'
    }
  }
}

function closeDetail() {
  detailSeq++
  detailController?.abort()
  detailController = null
  detailOpen.value = false
  detailState.value = 'idle'
  detail.value = null
  detailMessage.value = ''
}

function changeStatus(event) {
  statusFilter.value = event.target.value
  pageNum.value = 1
  loadList()
}

function changePage(next) {
  if (next < 1 || next === pageNum.value || listState.value === 'loading') return
  pageNum.value = next
  loadList()
}

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

watch(bindingId, () => {
  closeDetail()
  pageNum.value = 1
  loadList()
}, { immediate: true })

onBeforeUnmount(() => {
  disposed = true
  listSeq++
  detailSeq++
  listController?.abort()
  detailController?.abort()
})
</script>

<template>
  <div class="teacher-announcements">
    <header class="ann-head">
      <div>
        <h2>班级消息 / 公告</h2>
        <p class="sub">只读查看本教学班公告；发布、编辑和归档不在本轮范围。</p>
      </div>
      <span class="scope">{{ bindingId ? `当前教学班：${bindingId}` : '未选择教学班' }}</span>
    </header>

    <div class="ann-toolbar">
      <select class="status-filter" :value="statusFilter" aria-label="公告状态" @change="changeStatus">
        <option value="">全部状态</option>
        <option value="DRAFT">草稿</option>
        <option value="PUBLISHED">已发布</option>
        <option value="ARCHIVED">已归档</option>
      </select>
    </div>

    <div v-if="listState === 'need-binding'" class="state state-info" role="status">
      <strong>请先选择有效教学班</strong>
      <span>教师公告按教学班授权；选择班级后再读取列表。</span>
    </div>

    <div v-else-if="listState === 'loading'" class="state" role="status">正在读取公告…</div>

    <div v-else-if="listState === 'forbidden'" class="state state-error" role="alert">
      <strong>没有该教学班公告的访问权限</strong>
      <span>{{ listMessage }}</span>
    </div>

    <div v-else-if="listState === 'unavailable-auth'" class="state state-warn" role="alert">
      <strong>教学班授权服务暂不可用</strong>
      <span>{{ listMessage }}</span>
    </div>

    <div v-else-if="listState === 'unavailable'" class="state state-warn" role="alert">
      <strong>服务暂不可用</strong>
      <span>{{ listMessage }}</span>
    </div>

    <div v-else-if="listState === 'error'" class="state state-error" role="alert">
      <strong>公告读取失败</strong>
      <span>{{ listMessage || '请稍后重试。' }}</span>
    </div>

    <div v-else-if="listState === 'empty'" class="state" role="status">
      <strong>本班还没有公告</strong>
      <span>{{ statusFilter ? '当前筛选条件下没有公告。' : '该教学班暂未发布公告。' }}</span>
    </div>

    <template v-else-if="listState === 'ready'">
      <ul class="ann-list">
        <li v-for="row in rows" :key="row.id" class="ann-item">
          <button type="button" class="ann-main" @click="openDetail(row.id)">
            <span class="ann-title">{{ row.title }}</span>
            <span class="ann-status">{{ visibilityLabel(row) }}</span>
            <span class="ann-meta">开放：{{ formatTime(row.opensAt) }} · 关闭：{{ formatTime(row.closesAt) }}</span>
          </button>
        </li>
      </ul>

      <div v-if="total > pageSize" class="pager">
        <button type="button" :disabled="pageNum <= 1" @click="changePage(pageNum - 1)">上一页</button>
        <span>第 {{ pageNum }} 页 · 共 {{ total }} 条</span>
        <button type="button" :disabled="pageNum * pageSize >= total" @click="changePage(pageNum + 1)">下一页</button>
      </div>
    </template>

    <div v-if="detailOpen" class="ann-overlay" @click.self="closeDetail">
      <section class="ann-dialog" role="dialog" aria-modal="true" aria-labelledby="teacher-ann-detail-title">
        <header class="ann-dialog-head">
          <h3 id="teacher-ann-detail-title">公告详情</h3>
          <button type="button" class="ann-close" @click="closeDetail">关闭</button>
        </header>

        <div v-if="detailState === 'loading'" class="state" role="status">正在读取公告详情…</div>

        <div v-else-if="detailState === 'notfound'" class="state state-error" role="alert">
          <strong>公告不存在或不可访问</strong>
          <span>{{ detailMessage }}</span>
        </div>

        <div v-else-if="detailState === 'unavailable-auth'" class="state state-warn" role="alert">
          <strong>教学班授权服务暂不可用</strong>
          <span>{{ detailMessage }}</span>
        </div>

        <div v-else-if="detailState === 'unavailable'" class="state state-warn" role="alert">
          <strong>服务暂不可用</strong>
          <span>{{ detailMessage }}</span>
        </div>

        <div v-else-if="detailState === 'error'" class="state state-error" role="alert">
          <strong>公告读取失败</strong>
          <span>{{ detailMessage || '请稍后重试。' }}</span>
        </div>

        <div v-else-if="detailState === 'ready' && detail" class="ann-detail">
          <h4 class="ann-detail-title">{{ detail.title }}</h4>
          <p class="ann-detail-meta">
            <span class="ann-status">{{ visibilityLabel(detail) }}</span>
            <span>开放：{{ formatTime(detail.opensAt) }}</span>
            <span>关闭：{{ formatTime(detail.closesAt) }}</span>
          </p>
          <div class="ann-body">{{ detail.body }}</div>
        </div>

        <footer class="ann-actions">
          <button type="button" class="ann-close" @click="closeDetail">关闭</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.teacher-announcements { padding: 20px; background: #fff; border-radius: 8px; }
.ann-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.ann-head h2 { margin: 0 0 6px; font-size: 20px; color: #1f2d3d; }
.sub { margin: 0; color: #909399; font-size: 13px; }
.scope { padding: 6px 12px; border-radius: 999px; background: #f0f2f5; color: #606266; white-space: nowrap; }
.ann-toolbar { margin: 16px 0; }
.status-filter { padding: 5px 8px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; }
.state { display: flex; flex-direction: column; gap: 6px; padding: 28px 20px; text-align: center; color: #606266; background: #fafafa; border-radius: 8px; }
.state-info { background: #f4f4f5; }
.state-error { background: #fef0f0; color: #c45656; }
.state-warn { background: #fdf6ec; color: #b88230; }
.ann-list { list-style: none; margin: 0; padding: 0; border-top: 1px solid #ebeef5; }
.ann-item { padding: 14px 4px; border-bottom: 1px solid #ebeef5; }
.ann-main { display: flex; align-items: center; gap: 10px; width: 100%; padding: 0; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; flex-wrap: wrap; }
.ann-main:hover .ann-title { color: #409eff; }
.ann-title { color: #303133; font-size: 15px; }
.ann-status { padding: 1px 7px; border-radius: 4px; background: #ecf5ff; color: #409eff; font-size: 12px; }
.ann-meta { margin-left: auto; color: #909399; font-size: 12px; }
.pager { display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding-top: 16px; color: #606266; font-size: 13px; }
.pager button { padding: 5px 10px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; cursor: pointer; }
.pager button:disabled { color: #c0c4cc; cursor: not-allowed; }
.ann-overlay { position: fixed; inset: 0; z-index: 3000; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(0, 0, 0, .45); }
.ann-dialog { width: min(640px, 100%); max-height: calc(100vh - 40px); overflow: auto; padding: 20px; border-radius: 10px; background: #fff; box-shadow: 0 12px 40px rgba(0, 0, 0, .2); }
.ann-dialog-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.ann-dialog-head h3 { margin: 0; font-size: 18px; color: #1f2d3d; }
.ann-close { padding: 5px 12px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; cursor: pointer; }
.ann-close:hover { color: #409eff; border-color: #409eff; }
.ann-detail-title { margin: 0 0 8px; font-size: 17px; color: #303133; }
.ann-detail-meta { display: flex; align-items: center; gap: 12px; margin: 0 0 14px; color: #909399; font-size: 12px; flex-wrap: wrap; }
.ann-body { white-space: pre-wrap; line-height: 1.7; color: #303133; font-size: 14px; }
.ann-actions { display: flex; justify-content: flex-end; padding-top: 18px; }
</style>
