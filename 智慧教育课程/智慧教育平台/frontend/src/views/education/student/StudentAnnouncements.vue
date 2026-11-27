<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { getStudentAnnouncement, listStudentAnnouncements } from '@/api/education/announcement'
import { classifyNotice503, notice503Detail } from './notice-error'

/**
 * 学生课程公告页签（6-04 原型）。
 *
 * 只接列表和详情两个 GET。列表按当前教学班 bindingId 过滤，无班级时省略；
 * 详情由服务端重新校验可见性。切班、关闭详情和卸载都会使旧请求失效。
 */

const props = defineProps({
  bindingId: { type: String, default: '' },
  openAnnouncementId: { type: String, default: '' }
})
const emit = defineEmits(['opened'])

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const listState = ref('idle')
const listMessage = ref('')
const detailOpen = ref(false)
const detailState = ref('idle')
const detail = ref(null)
const detailMessage = ref('')
const detailCloseButton = ref(null)

let listSeq = 0
let detailSeq = 0
let listController = null
let detailController = null
let detailTrigger = null
let disposed = false

/**
 * 查询公告列表。切班时先 abort 旧请求，再用序号丢弃迟到响应。
 *
 * @returns {Promise<void>} 列表加载完成
 */
async function loadList() {
  if (disposed) return
  const seq = ++listSeq
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  listState.value = 'loading'
  listMessage.value = ''
  rows.value = []
  total.value = 0

  try {
    const body = await listStudentAnnouncements({
      bindingId: props.bindingId || undefined,
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
 * 打开公告详情。
 *
 * 详情请求与当前序号绑定；关闭、切班或卸载后迟到响应不能重新打开或覆盖。
 *
 * @param {string} announcementId 公告 ID 字符串
 * @param {Event} [event] 触发按钮事件；关闭后焦点归还仍存在的按钮
 * @returns {Promise<void>} 详情加载完成
 */
async function openDetail(announcementId, event) {
  if (disposed) return
  const id = String(announcementId ?? '')
  if (!id) return
  const seq = ++detailSeq
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  detailTrigger = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null
  detailOpen.value = true
  detailState.value = 'loading'
  detail.value = null
  detailMessage.value = ''
  emit('opened')
  await nextTick()
  detailCloseButton.value?.focus()

  try {
    const body = await getStudentAnnouncement(id, { signal: controller.signal })
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

/**
 * 关闭公告详情并使在途请求失效。
 *
 * @returns {void}
 */
function closeDetail() {
  detailSeq++
  detailController?.abort()
  detailController = null
  detailOpen.value = false
  detailState.value = 'idle'
  detail.value = null
  detailMessage.value = ''
  const trigger = detailTrigger
  detailTrigger = null
  nextTick(() => {
    if (trigger?.isConnected) trigger.focus()
  })
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

// 切班后关闭旧详情并重新加载；列表、详情序号均递增。
watch(() => props.bindingId, () => {
  closeDetail()
  pageNum.value = 1
  loadList()
}, { immediate: true })

// 从通知详情跳转时由 route query 携带 announcementId；服务端仍重新校验可见性。
watch(() => props.openAnnouncementId, id => {
  if (id) openDetail(id)
}, { immediate: true })

onBeforeUnmount(() => {
  disposed = true
  listSeq++
  detailSeq++
  listController?.abort()
  detailController?.abort()
  detailTrigger = null
})

</script>

<template>
  <div class="student-announcements">
    <header class="ann-head">
      <div>
        <h2>课程公告</h2>
        <p class="sub">只显示服务端判定为当前可见的公告；详情会重新校验可见性。</p>
      </div>
      <span class="scope">{{ bindingId ? `当前教学班：${bindingId}` : '全部在修班级' }}</span>
    </header>

    <div v-if="listState === 'loading'" class="state" role="status">正在读取公告…</div>

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
      <strong>当前没有可见公告</strong>
      <span>{{ bindingId ? '该教学班暂未发布处于开放时间的公告。' : '你的在修班级暂未发布处于开放时间的公告。' }}</span>
    </div>

    <template v-else-if="listState === 'ready'">
      <ul class="ann-list">
        <li v-for="row in rows" :key="row.id" class="ann-item">
          <button type="button" class="ann-main" :data-announcement-id="row.id" @click="openDetail(row.id, $event)">
            <span class="ann-title">{{ row.title }}</span>
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

    <div v-if="detailOpen" class="ann-overlay" @click.self="closeDetail" @keydown.esc.stop.prevent="closeDetail">
      <section class="ann-dialog" role="dialog" aria-modal="true" aria-labelledby="ann-detail-title">
        <header class="ann-dialog-head">
          <h3 id="ann-detail-title">公告详情</h3>
          <button ref="detailCloseButton" type="button" class="ann-close" @click="closeDetail">关闭</button>
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
          <p class="ann-detail-meta">开放：{{ formatTime(detail.opensAt) }} · 关闭：{{ formatTime(detail.closesAt) }}</p>
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
.student-announcements { padding: 20px; background: #fff; border-radius: 8px; }
.ann-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.ann-head h2 { margin: 0 0 6px; font-size: 20px; color: #1f2d3d; }
.sub { margin: 0; color: #909399; font-size: 13px; }
.scope { padding: 6px 12px; border-radius: 999px; background: #f0f2f5; color: #606266; white-space: nowrap; }
.state { display: flex; flex-direction: column; gap: 6px; padding: 28px 20px; text-align: center; color: #606266; background: #fafafa; border-radius: 8px; }
.state-error { background: #fef0f0; color: #c45656; }
.state-warn { background: #fdf6ec; color: #b88230; }
.ann-list { list-style: none; margin: 18px 0 0; padding: 0; border-top: 1px solid #ebeef5; }
.ann-item { padding: 14px 4px; border-bottom: 1px solid #ebeef5; }
.ann-main { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; padding: 0; border: 0; background: transparent; font: inherit; text-align: left; cursor: pointer; }
.ann-main:hover .ann-title { color: #409eff; }
.ann-title { color: #303133; font-size: 15px; }
.ann-meta { color: #909399; font-size: 12px; white-space: nowrap; }
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
.ann-detail-meta { margin: 0 0 14px; color: #909399; font-size: 12px; }
.ann-body { white-space: pre-wrap; line-height: 1.7; color: #303133; font-size: 14px; }
.ann-actions { display: flex; justify-content: flex-end; padding-top: 18px; }
</style>
