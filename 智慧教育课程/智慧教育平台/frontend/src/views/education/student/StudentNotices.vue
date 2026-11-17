<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { getNoticeDetail, getUnreadCount, listNotices, markNoticeRead } from '@/api/education/notice'
import { context } from '@/education/context'

/**
 * 学生通知只读列表（6-04 最小接入）。
 *
 * 只调用两个 GET：通知列表、未读数。已读、全部已读、详情和公告页签
 * 不在本轮范围。班级上下文来自 #27 的课程—教学班选择器；
 * 未选班级时省略 bindingId，由服务端返回本人全部当前可见通知。
 */

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const listState = ref('idle')
const listMessage = ref('')
const unreadState = ref('idle')
const unreadCount = ref(null)
const unreadMessage = ref('')
const detailOpen = ref(false)
const detailState = ref('idle')
const detail = ref(null)
const detailMessage = ref('')
const detailCloseButton = ref(null)
const readSubmitting = ref(false)
const readMessage = ref('')
const readError = ref('')
const readWarnings = ref([])

let listSeq = 0
let unreadSeq = 0
let detailSeq = 0
let listController = null
let unreadController = null
let detailController = null
let readController = null
let detailTrigger = null
let disposed = false

/**
 * 当前教学班绑定 ID。空字符串表示未选班级。
 * 保持字符串，不经过 Number()，避免 19 位 ID 精度丢失。
 */
const bindingId = computed(() => {
  const value = context.current?.bindingId
  return value === undefined || value === null || value === '' ? '' : String(value)
})

const unreadText = computed(() => {
  if (unreadState.value === 'loading') return '未读：…'
  if (unreadState.value === 'ready') return `未读：${unreadCount.value}`
  if (unreadState.value === 'unavailable') return '未读：暂不可用'
  return '未读：—'
})

const unreadTone = computed(() => ({
  'is-loading': unreadState.value === 'loading',
  'is-unavailable': unreadState.value === 'unavailable'
}))

/**
 * 查询通知列表。
 *
 * 切班时先 abort 旧请求，再用序号丢弃迟到响应；两者同时存在，
 * 是因为 axios 取消和响应到达可能落在同一事件循环窗口。
 *
 * @returns {Promise<boolean>} 是否完成当前列表刷新；失败或已被更新的请求取代返回 false
 */
async function loadList() {
  if (disposed) return false
  const seq = ++listSeq
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  listState.value = 'loading'
  listMessage.value = ''
  rows.value = []
  total.value = 0

  try {
    const body = await listNotices(
      { bindingId: bindingId.value || undefined, pageNum: pageNum.value, pageSize },
      { signal: controller.signal }
    )
    if (seq !== listSeq || disposed) return
    if (!body || !Array.isArray(body.rows) || !Number.isSafeInteger(body.total) || body.total < 0) {
      throw Object.assign(new Error('通知列表响应格式不正确'), { code: 500 })
    }
    if (body.total === 0 && body.rows.length > 0) {
      throw Object.assign(new Error('通知列表响应不一致'), { code: 500 })
    }
    rows.value = body.rows
    total.value = body.total
    listState.value = body.rows.length === 0 ? 'empty' : 'ready'
    return true
  } catch (error) {
    if (seq !== listSeq || disposed || error?.cancelled) return false
    rows.value = []
    total.value = 0
    listMessage.value = typeof error?.message === 'string' ? error.message : ''
    if (error?.forbidden || error?.code === 403) {
      listState.value = 'forbidden'
    } else if (error?.code === 503) {
      // 6-02 契约：HTTP 200 + 业务 503 + msg 明确含“授权”才是授权不可用；
      // 网关 HTTP 503 即使正文含“授权”也按通用服务故障展示，避免误报。
      listState.value = classifyNotice503(error) === 'authorization'
        ? 'unavailable-auth'
        : 'unavailable'
      listMessage.value = notice503Detail(error)
    } else {
      listState.value = 'error'
    }
    return false
  }
}

/**
 * 查询未读数。
 *
 * 未读数失败只影响角标/本页提示，不能把列表失败化，也不能显示 0。
 *
 * @returns {Promise<boolean>} 是否完成当前未读数刷新；失败或已被更新的请求取代返回 false
 */
async function loadUnread() {
  if (disposed) return false
  const seq = ++unreadSeq
  unreadController?.abort()
  const controller = new AbortController()
  unreadController = controller
  unreadState.value = 'loading'
  unreadMessage.value = ''
  unreadCount.value = null

  try {
    const body = await getUnreadCount(bindingId.value || undefined, { signal: controller.signal })
    if (seq !== unreadSeq || disposed) return false
    const count = body?.data?.unreadCount
    if (!Number.isSafeInteger(count) || count < 0) {
      throw new Error('未读数响应不完整')
    }
    unreadCount.value = count
    unreadState.value = 'ready'
    return true
  } catch (error) {
    if (seq !== unreadSeq || disposed || error?.cancelled) return false
    unreadCount.value = null
    unreadState.value = 'unavailable'
    unreadMessage.value = typeof error?.message === 'string' ? error.message : '未读数暂不可用'
    return false
  }
}

/**
 * 判定 503 是否来自教学班授权服务。
 *
 * 6-02 契约：HTTP 200 + 业务 code=503 + 服务端 msg 明确指向授权，才归为授权不可用。
 * 网关 HTTP 503 即使正文含糊提到“授权”也按通用服务故障处理，避免把基础设施
 * 故障误报成第 2 组授权契约问题。
 *
 * @param {object} error 统一请求模块抛出的错误对象
 * @returns {'authorization'|'generic'} 授权 503 或通用 503
 */
function classifyNotice503(error) {
  const httpStatus = Number(error?.status)
  const message = String(error?.message || '')
  return httpStatus === 200 && message.includes('授权') ? 'authorization' : 'generic'
}

/**
 * 503 详情文案。
 *
 * 优先保留服务端 msg；网关 HTML 503 没有业务 msg 时补充 HTTP 状态，
 * 方便定位是网关/容器故障而不是通知空数据。
 *
 * @param {object} error 统一请求模块抛出的错误对象
 * @returns {string} 展示给用户的详情
 */
function notice503Detail(error) {
  const message = typeof error?.message === 'string' ? error.message.trim() : ''
  if (message && message !== '请求失败') return message
  if (Number(error?.status) === 503) return '请求失败（HTTP 503）'
  return message || '请稍后重试。'
}

/**
 * 打开通知详情。
 *
 * 每次打开/切换通知都递增 detailSeq 并 abort 上一次详情/已读请求；关闭、切班和
 * 卸载也会递增序号。只有同时满足“序号未变、未卸载、详情仍打开”的响应才能写入，
 * 防止迟到响应重新打开已关闭详情或覆盖新通知。
 *
 * @param {string} noticeId 通知 ID 字符串
 * @param {Event} [event] 触发按钮事件；用于关闭后把焦点还给仍存在的按钮
 * @returns {Promise<void>} 详情加载完成；失败写入详情错误态
 */
async function openDetail(noticeId, event) {
  if (disposed) return
  const id = String(noticeId ?? '')
  if (!id) return
  const seq = ++detailSeq
  detailController?.abort()
  detailController = null
  readController?.abort()
  readController = null
  readSubmitting.value = false
  readMessage.value = ''
  readError.value = ''
  readWarnings.value = []
  detailTrigger = event?.currentTarget instanceof HTMLElement
    ? event.currentTarget
    : (document.activeElement instanceof HTMLElement ? document.activeElement : null)
  const controller = new AbortController()
  detailController = controller
  detailOpen.value = true
  detailState.value = 'loading'
  detailMessage.value = ''
  detail.value = null
  await nextTick()
  detailCloseButton.value?.focus()

  try {
    const body = await getNoticeDetail(id, { signal: controller.signal })
    if (seq !== detailSeq || disposed || !detailOpen.value) return
    const item = body?.data
    if (!item || item.id !== id || typeof item.title !== 'string') {
      throw Object.assign(new Error('通知详情响应格式不正确'), { code: 500 })
    }
    detail.value = item
    detailState.value = 'ready'
  } catch (error) {
    if (seq !== detailSeq || disposed || !detailOpen.value || error?.cancelled) return
    detailMessage.value = typeof error?.message === 'string' ? error.message : ''
    if (error?.code === 404) {
      detailState.value = 'notfound'
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
 * 关闭详情并使在途详情/已读响应失效，然后把焦点还给仍存在的触发按钮。
 *
 * @returns {void}
 */
function closeDetail() {
  detailSeq++
  detailController?.abort()
  detailController = null
  readController?.abort()
  readController = null
  readSubmitting.value = false
  readMessage.value = ''
  readError.value = ''
  readWarnings.value = []
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

/**
 * 写入成功后刷新当前列表、未读数和顶栏角标。
 *
 * 列表与未读数分别记录失败，详情保持打开并逐项提示；不能用其中一个成功
 * 掩盖另一个失败。顶栏通过现有 EduVue.refreshHeader() 统一刷新。
 *
 * @param {number} seq 发起刷新时的详情序号；关闭/切换后不再写入提示
 * @returns {Promise<void>} 刷新完成
 */
async function refreshAfterRead(seq) {
  const [listOk, unreadOk] = await Promise.all([loadList(), loadUnread()])
  window.EduVue?.refreshHeader?.()
  if (disposed || seq !== detailSeq || !detailOpen.value) return
  const warnings = []
  if (!listOk) warnings.push('通知列表刷新失败，当前列表可能不是最新。')
  if (!unreadOk) warnings.push('未读数刷新失败，顶栏角标可能不是最新。')
  readWarnings.value = warnings
}

/**
 * 标记当前详情为已读。
 *
 * 写请求与当前详情序号、通知 ID、班级绑定三者绑定；关闭、切换通知或切班后
 * 即使服务端已处理，迟到响应也不能更新新详情或新班级状态。成功不本地减一，
 * 统一重拉列表和未读数；alreadyRead=true 按成功处理。
 *
 * @returns {Promise<void>} 已读处理完成
 */
async function markDetailRead() {
  if (disposed || !detailOpen.value || readSubmitting.value || !detail.value || detail.value.isRead) return
  const id = String(detail.value.id ?? '')
  if (!id) return
  const seq = detailSeq
  const bindingAtStart = bindingId.value
  readController?.abort()
  const controller = new AbortController()
  readController = controller
  readSubmitting.value = true
  readMessage.value = ''
  readError.value = ''
  readWarnings.value = []

  try {
    const body = await markNoticeRead(id, { signal: controller.signal })
    if (seq !== detailSeq || disposed || !detailOpen.value
        || bindingId.value !== bindingAtStart || detail.value?.id !== id) return
    const data = body?.data
    if (!data || data.id !== id || typeof data.readAt !== 'string'
        || typeof data.alreadyRead !== 'boolean') {
      throw Object.assign(new Error('已读响应格式不正确'), { code: 500 })
    }
    detail.value = { ...detail.value, isRead: true, readAt: data.readAt }
    readSubmitting.value = false
    readMessage.value = '已标记为已读'
    await refreshAfterRead(seq)
  } catch (error) {
    if (seq !== detailSeq || disposed || !detailOpen.value
        || bindingId.value !== bindingAtStart || error?.cancelled) return
    readSubmitting.value = false
    if (error?.code === 404) {
      detail.value = null
      detailState.value = 'notfound'
      detailMessage.value = '通知不存在或不可访问'
      await refreshAfterRead(seq)
    } else if (error?.code === 503) {
      detail.value = null
      detailState.value = classifyNotice503(error) === 'authorization'
        ? 'unavailable-auth'
        : 'unavailable'
      detailMessage.value = notice503Detail(error)
    } else {
      readError.value = typeof error?.message === 'string' ? error.message : '标记已读失败，请稍后重试。'
    }
  }
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

function kindLabel(kind) {
  return {
    ANNOUNCEMENT: '公告',
    PREVIEW: '预习任务',
    ACTIVITY: '课堂活动',
    ASSIGNMENT: '作业',
    GRADE: '成绩'
  }[kind] || kind || '通知'
}

// 班级上下文变化后关闭旧详情并重新加载；pageNum 回到第一页，避免新班级落在旧页码。
watch(bindingId, () => {
  closeDetail()
  pageNum.value = 1
  loadList()
  loadUnread()
}, { immediate: true })

onBeforeUnmount(() => {
  disposed = true
  listSeq++
  unreadSeq++
  detailSeq++
  listController?.abort()
  unreadController?.abort()
  detailController?.abort()
  readController?.abort()
  detailTrigger = null
})
</script>

<template>
  <section class="student-notices" aria-label="学习通知">
    <header class="notice-head">
      <div>
        <h2>学习通知</h2>
        <p class="sub">只读通知列表；已读操作将在后续任务接入。</p>
      </div>
      <p class="unread" :class="unreadTone" :title="unreadMessage" aria-live="polite">{{ unreadText }}</p>
    </header>

    <p class="context-note">
      {{ bindingId ? `当前教学班：${bindingId}` : '未选择教学班，显示本人全部当前可见通知。' }}
    </p>

    <div v-if="listState === 'loading'" class="state" role="status">正在读取通知…</div>

    <div v-else-if="listState === 'forbidden'" class="state state-error" role="alert">
      <strong>没有该教学班通知的访问权限</strong>
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
      <strong>通知读取失败</strong>
      <span>{{ listMessage || '请稍后重试。' }}</span>
    </div>

    <div v-else-if="listState === 'empty'" class="state" role="status">
      <strong>暂无通知</strong>
      <span>当前范围内没有可见通知。</span>
    </div>

    <template v-else-if="listState === 'ready'">
      <ul class="notice-list">
        <li v-for="row in rows" :key="row.id" class="notice-item">
          <button type="button" class="notice-main" :data-notice-id="row.id" @click="openDetail(row.id, $event)">
            <strong class="notice-title">{{ row.title }}</strong>
            <span class="notice-kind">{{ kindLabel(row.kind) }}</span>
            <span class="notice-read" :class="{ 'is-unread': !row.isRead }">{{ row.isRead ? '已读' : '未读' }}</span>
          </button>
          <div class="notice-meta">
            可见时间：{{ formatTime(row.visibleAt) }}
            <span v-if="row.bindingId"> · 班级 {{ row.bindingId }}</span>
          </div>
        </li>
      </ul>

      <div v-if="total > pageSize" class="pager">
        <button type="button" :disabled="pageNum <= 1 || listState === 'loading'" @click="changePage(pageNum - 1)">上一页</button>
        <span>第 {{ pageNum }} 页 · 共 {{ total }} 条</span>
        <button type="button" :disabled="pageNum * pageSize >= total || listState === 'loading'" @click="changePage(pageNum + 1)">下一页</button>
      </div>
    </template>

    <div v-if="detailOpen" class="detail-overlay" @click.self="closeDetail" @keydown.esc.stop.prevent="closeDetail">
      <section class="detail-dialog" role="dialog" aria-modal="true" aria-labelledby="notice-detail-title">
        <header class="detail-head">
          <h3 id="notice-detail-title">通知详情</h3>
          <button ref="detailCloseButton" type="button" class="detail-close" aria-label="关闭通知详情" @click="closeDetail">关闭</button>
        </header>

        <ul v-if="readWarnings.length" class="detail-warnings" role="status">
          <li v-for="warning in readWarnings" :key="warning">{{ warning }}</li>
        </ul>

        <div v-if="detailState === 'loading'" class="state" role="status">正在读取通知详情…</div>

        <div v-else-if="detailState === 'notfound'" class="state state-error" role="alert">
          <strong>通知不存在或不可访问</strong>
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
          <strong>通知读取失败</strong>
          <span>{{ detailMessage || '请稍后重试。' }}</span>
        </div>

        <div v-else-if="detailState === 'ready' && detail" class="detail-content">
          <h4 class="detail-title">{{ detail.title }}</h4>
          <dl class="detail-meta">
            <div>
              <dt>类型</dt>
              <dd>{{ kindLabel(detail.kind) }}</dd>
            </div>
            <div>
              <dt>可见时间</dt>
              <dd>{{ formatTime(detail.visibleAt) }}</dd>
            </div>
            <div>
              <dt>已读状态</dt>
              <dd>
                {{ detail.isRead ? '已读' : '未读' }}
                <span v-if="detail.isRead && detail.readAt"> · {{ formatTime(detail.readAt) }}</span>
              </dd>
            </div>
          </dl>
          <div v-if="!detail.isRead" class="detail-read-actions">
            <button type="button" class="detail-read" :disabled="readSubmitting" @click="markDetailRead">
              {{ readSubmitting ? '处理中…' : '标为已读' }}
            </button>
          </div>
          <p v-if="readMessage" class="read-success" role="status">{{ readMessage }}</p>
          <p v-if="readError" class="read-error" role="alert">{{ readError }}</p>
          <p class="detail-note">通知详情只包含元数据，正文由来源页面提供。</p>
        </div>

        <footer class="detail-actions">
          <button type="button" class="detail-close" @click="closeDetail">关闭</button>
        </footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.student-notices { padding: 20px; background: #fff; border-radius: 8px; }
.notice-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.notice-head h2 { margin: 0 0 6px; font-size: 20px; color: #1f2d3d; }
.sub { margin: 0; color: #909399; font-size: 13px; }
.unread { margin: 0; padding: 6px 12px; border-radius: 999px; background: #f0f2f5; color: #606266; white-space: nowrap; }
.unread.is-loading { color: #909399; }
.unread.is-unavailable { background: #fdf6ec; color: #b88230; }
.context-note { margin: 14px 0; color: #606266; font-size: 13px; }
.state { display: flex; flex-direction: column; gap: 6px; padding: 28px 20px; text-align: center; color: #606266; background: #fafafa; border-radius: 8px; }
.state-error { background: #fef0f0; color: #c45656; }
.state-warn { background: #fdf6ec; color: #b88230; }
.notice-list { list-style: none; margin: 0; padding: 0; border-top: 1px solid #ebeef5; }
.notice-item { padding: 14px 4px; border-bottom: 1px solid #ebeef5; }
.notice-main {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  width: 100%; padding: 0; border: 0; background: transparent; color: inherit;
  font: inherit; text-align: left; cursor: pointer;
}
.notice-main:hover .notice-title { color: #409eff; }
.detail-overlay {
  position: fixed; inset: 0; z-index: 3000;
  display: flex; align-items: center; justify-content: center;
  padding: 20px; background: rgba(0, 0, 0, .45);
}
.detail-dialog {
  width: min(560px, 100%); max-height: calc(100vh - 40px); overflow: auto;
  padding: 20px; border-radius: 10px; background: #fff; box-shadow: 0 12px 40px rgba(0, 0, 0, .2);
}
.detail-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.detail-head h3 { margin: 0; font-size: 18px; color: #1f2d3d; }
.detail-close {
  padding: 5px 12px; border: 1px solid #dcdfe6; border-radius: 4px;
  background: #fff; color: #606266; cursor: pointer;
}
.detail-close:hover { color: #409eff; border-color: #409eff; }
.detail-title { margin: 0 0 14px; font-size: 17px; color: #303133; }
.detail-meta { margin: 0; }
.detail-meta > div { display: flex; gap: 12px; padding: 8px 0; border-bottom: 1px solid #f2f3f5; }
.detail-meta dt { min-width: 72px; color: #909399; }
.detail-meta dd { margin: 0; color: #303133; }
.detail-warnings { margin: 0 0 12px; padding: 10px 12px; border-radius: 6px; background: #fdf6ec; color: #b88230; font-size: 13px; list-style: disc inside; }
.detail-read-actions { display: flex; justify-content: flex-start; padding-top: 14px; }
.detail-read {
  padding: 6px 14px; border: 0; border-radius: 4px; background: #409eff; color: #fff;
  cursor: pointer;
}
.detail-read:disabled { background: #a0cfff; cursor: not-allowed; }
.read-success { margin: 12px 0 0; color: #67c23a; font-size: 13px; }
.read-error { margin: 12px 0 0; color: #c45656; font-size: 13px; }
.detail-note { margin: 14px 0 0; color: #909399; font-size: 12px; }
.detail-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 18px; }
.notice-title { color: #303133; font-size: 15px; }
.notice-kind { padding: 1px 6px; border-radius: 4px; background: #ecf5ff; color: #409eff; font-size: 12px; }
.notice-read { color: #909399; font-size: 12px; }
.notice-read.is-unread { color: #e6a23c; font-weight: 600; }
.notice-meta { margin-top: 6px; color: #909399; font-size: 12px; }
.pager { display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding-top: 16px; color: #606266; font-size: 13px; }
.pager button { padding: 5px 10px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; cursor: pointer; }
.pager button:disabled { color: #c0c4cc; cursor: not-allowed; }
</style>
