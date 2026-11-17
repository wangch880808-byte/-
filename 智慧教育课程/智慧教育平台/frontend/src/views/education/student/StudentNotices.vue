<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getUnreadCount, listNotices } from '@/api/education/notice'
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

let listSeq = 0
let unreadSeq = 0
let listController = null
let unreadController = null
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
 * @returns {Promise<void>} 列表加载完成；失败时写入页面状态，不抛出到全局
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
  } catch (error) {
    if (seq !== listSeq || disposed || error?.cancelled) return
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
  }
}

/**
 * 查询未读数。
 *
 * 未读数失败只影响角标/本页提示，不能把列表失败化，也不能显示 0。
 *
 * @returns {Promise<void>} 未读数加载完成
 */
async function loadUnread() {
  if (disposed) return
  const seq = ++unreadSeq
  unreadController?.abort()
  const controller = new AbortController()
  unreadController = controller
  unreadState.value = 'loading'
  unreadMessage.value = ''
  unreadCount.value = null

  try {
    const body = await getUnreadCount(bindingId.value || undefined, { signal: controller.signal })
    if (seq !== unreadSeq || disposed) return
    const count = body?.data?.unreadCount
    if (!Number.isSafeInteger(count) || count < 0) {
      throw new Error('未读数响应不完整')
    }
    unreadCount.value = count
    unreadState.value = 'ready'
  } catch (error) {
    if (seq !== unreadSeq || disposed || error?.cancelled) return
    unreadCount.value = null
    unreadState.value = 'unavailable'
    unreadMessage.value = typeof error?.message === 'string' ? error.message : '未读数暂不可用'
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

// 班级上下文变化后重新加载；pageNum 回到第一页，避免新班级落在旧页码。
watch(bindingId, () => {
  pageNum.value = 1
  loadList()
  loadUnread()
}, { immediate: true })

onBeforeUnmount(() => {
  disposed = true
  listSeq++
  unreadSeq++
  listController?.abort()
  unreadController?.abort()
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
          <div class="notice-main">
            <strong class="notice-title">{{ row.title }}</strong>
            <span class="notice-kind">{{ kindLabel(row.kind) }}</span>
            <span class="notice-read" :class="{ 'is-unread': !row.isRead }">{{ row.isRead ? '已读' : '未读' }}</span>
          </div>
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
.notice-main { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.notice-title { color: #303133; font-size: 15px; }
.notice-kind { padding: 1px 6px; border-radius: 4px; background: #ecf5ff; color: #409eff; font-size: 12px; }
.notice-read { color: #909399; font-size: 12px; }
.notice-read.is-unread { color: #e6a23c; font-weight: 600; }
.notice-meta { margin-top: 6px; color: #909399; font-size: 12px; }
.pager { display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding-top: 16px; color: #606266; font-size: 13px; }
.pager button { padding: 5px 10px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; cursor: pointer; }
.pager button:disabled { color: #c0c4cc; cursor: not-allowed; }
</style>
