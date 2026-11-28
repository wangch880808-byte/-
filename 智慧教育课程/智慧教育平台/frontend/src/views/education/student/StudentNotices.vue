<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getNoticeDetail, getUnreadCount, listNotices, markAllNoticesRead, markNoticeRead } from '@/api/education/notice'
import ConfirmDialog from '@/components/education/ConfirmDialog.vue'
import TeachingContext from '@/components/education/TeachingContext.vue'
import StudentAnnouncements from './StudentAnnouncements.vue'
import { context } from '@/education/context'
import { classifyNotice503, notice503Detail } from './notice-error'

/**
 * 学生通知列表、详情与已读（6-04 原型）。
 *
 * 已接通知列表、未读数、详情、单条/全部已读、课程公告页签，以及
 * ANNOUNCEMENT 通知按 sourceId 跳转公告。非公告来源路由后续接入。
 * 班级上下文来自 #27 的课程—教学班选择器；未选班级时省略 bindingId，
 * 由服务端返回本人全部当前可见通知。
 */

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const kindFilter = ref('')
const unreadOnly = ref(false)
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
const confirmAllOpen = ref(false)
const readAllSubmitting = ref(false)
const readAllError = ref('')
const readAllMessage = ref('')
const readAllWarnings = ref([])
const route = useRoute()
const router = useRouter()

/**
 * 当前页签由显式路由参数表达，避免 sessionStorage 旧选择盖过本次入口意图。
 * 顶栏「通知」固定带 tab=notices；通知来源跳转带 tab=announcements。
 *
 * announcementId 是一次性打开意图，不放进路由：页面在路由守卫/切班时会重挂载，
 * 放进路由会导致关闭后再次打开。用 sessionStorage 只跨一次重挂载传递，公告页
 * 打开后立即清除；关闭、切班也会清除。
 */
const PENDING_ANNOUNCEMENT_KEY = 'zhijiao.pendingAnnouncementId'
function readPendingAnnouncementId() {
  try {
    return sessionStorage.getItem(PENDING_ANNOUNCEMENT_KEY) || ''
  } catch {
    return ''
  }
}
function setPendingAnnouncementId(announcementId) {
  requestedAnnouncementId.value = announcementId
  try {
    sessionStorage.setItem(PENDING_ANNOUNCEMENT_KEY, announcementId)
  } catch {
    /* 存储不可用时仍可用当前页面的 ref 打开一次 */
  }
}
function clearPendingAnnouncementId() {
  requestedAnnouncementId.value = ''
  try {
    sessionStorage.removeItem(PENDING_ANNOUNCEMENT_KEY)
  } catch {
    /* 存储不可用只影响一次跳转，不阻断关闭 */
  }
}
const activeTab = computed(() => route.query.tab === 'announcements' ? 'announcements' : 'notices')
// 从总览未读通知入口进入时固定本人全部范围；不沿用之前选择的教学班。
const allScope = computed(() => route.query.scope === 'all')
const requestedAnnouncementId = ref(readPendingAnnouncementId())

let listSeq = 0
let unreadSeq = 0
let detailSeq = 0
let readAllSeq = 0
let listController = null
let unreadController = null
let detailController = null
let readController = null
let readAllController = null
let detailTrigger = null
let disposed = false

/**
 * 当前教学班绑定 ID。空字符串表示未选班级。
 * 保持字符串，不经过 Number()，避免 19 位 ID 精度丢失。
 */
const bindingId = computed(() => {
  if (allScope.value) return ''
  const value = context.current?.bindingId
  return value === undefined || value === null || value === '' ? '' : String(value)
})

/** 当前范围名称；无选择或全部范围时明确表示全部当前可见范围。 */
const scopeLabel = computed(() => {
  if (allScope.value) return '全部当前可见通知（不按教学班）'
  if (!bindingId.value) return '全部当前可见通知'
  const course = context.current?.courseTitle
  const className = context.current?.className
  return course && className ? `${course} · ${className}` : (className || course || '当前教学班')
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
      {
        bindingId: bindingId.value || undefined,
        kind: kindFilter.value || undefined,
        unreadOnly: unreadOnly.value,
        pageNum: pageNum.value,
        pageSize
      },
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
 * 写入成功后刷新顶栏、当前列表和未读数。
 *
 * 顶栏刷新必须在写结果仍属于当前页面/班级时立即触发，不能等列表和未读数
 * 都返回后再由可能已离开的旧页面发请求。列表与未读数分别记录失败，
 * 不能用其中一个成功掩盖另一个失败。
 *
 * @param {Function} isCurrent 返回当前页面/班级是否仍属于本次写入
 * @param {Function} setWarnings 接收分项刷新失败文案
 * @returns {Promise<void>} 刷新完成
 */
async function refreshAfterWrite(isCurrent, setWarnings) {
  if (!isCurrent()) return
  window.EduVue?.refreshHeader?.()
  const [listOk, unreadOk] = await Promise.all([loadList(), loadUnread()])
  if (!isCurrent()) return
  const warnings = []
  if (!listOk) warnings.push('通知列表刷新失败，当前列表可能不是最新。')
  if (!unreadOk) warnings.push('未读数刷新失败，顶栏角标可能不是最新。')
  setWarnings(warnings)
}

/**
 * 单条已读后的刷新；详情序号、打开状态或班级变化后不再写提示。
 *
 * @param {number} seq 发起刷新时的详情序号
 * @returns {Promise<void>} 刷新完成
 */
async function refreshAfterRead(seq) {
  return refreshAfterWrite(
    () => !disposed && seq === detailSeq && detailOpen.value,
    warnings => { readWarnings.value = warnings }
  )
}

/**
 * 全部已读后的刷新；切班/离开页面后不再写提示。
 *
 * @param {number} seq 发起刷新时的全部已读序号
 * @returns {Promise<void>} 刷新完成
 */
async function refreshAfterReadAll(seq) {
  return refreshAfterWrite(
    () => !disposed && seq === readAllSeq,
    warnings => { readAllWarnings.value = warnings }
  )
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

const READ_ALL_CONFIRM_TEXT = '将标记本人所有教学班当前可见的未读通知，包括其他教学班，不受当前班级和分页限制。'

/**
 * 全部已读错误文案，沿用现有 403 / 503 分类。
 *
 * @param {object} error 统一请求模块抛出的错误对象
 * @returns {string} 确认弹窗内的错误文案
 */
function readAllErrorText(error) {
  const message = typeof error?.message === 'string' ? error.message.trim() : ''
  if (error?.code === 403 || error?.forbidden) {
    return message ? '没有权限执行全部已读：' + message : '没有权限执行全部已读'
  }
  if (error?.code === 503) {
    const title = classifyNotice503(error) === 'authorization'
      ? '教学班授权服务暂不可用'
      : '服务暂不可用'
    return title + '：' + notice503Detail(error)
  }
  return message || '全部已读失败，请稍后重试。'
}

/**
 * 打开全部已读确认框。此时不写请求；取消或切班/离开都不会发送 PUT。
 *
 * @returns {void}
 */
function openReadAllConfirm() {
  if (disposed || readAllSubmitting.value) return
  readAllError.value = ''
  confirmAllOpen.value = true
}

/**
 * 取消全部已读确认，并使确认前已排队的结果失效。
 *
 * @returns {void}
 */
function closeReadAllConfirm() {
  readAllSeq++
  readAllController?.abort()
  readAllController = null
  readAllSubmitting.value = false
  confirmAllOpen.value = false
  readAllError.value = ''
}

/**
 * 切班/离开页面时清空全部已读状态；递增序号使迟到响应失效。
 *
 * @returns {void}
 */
function resetReadAllState() {
  readAllSeq++
  readAllController?.abort()
  readAllController = null
  readAllSubmitting.value = false
  confirmAllOpen.value = false
  readAllError.value = ''
  readAllMessage.value = ''
  readAllWarnings.value = []
}

/**
 * 确认全部已读。
 *
 * 请求不带 bindingId、用户 ID 或请求体；服务端按当前登录人和可见性裁剪范围。
 * 写结果与序号和班级绑定，切班/离开后迟到响应不能更新新状态。markedCount=0
 * 也是成功，前端只按服务端数量展示，不本地推算。
 *
 * @returns {Promise<void>} 全部已读处理完成
 */
async function confirmReadAll() {
  if (disposed || !confirmAllOpen.value || readAllSubmitting.value) return
  const seq = ++readAllSeq
  const bindingAtStart = bindingId.value
  readAllController?.abort()
  const controller = new AbortController()
  readAllController = controller
  readAllSubmitting.value = true
  readAllError.value = ''
  readAllMessage.value = ''
  readAllWarnings.value = []

  try {
    const body = await markAllNoticesRead({ signal: controller.signal })
    if (seq !== readAllSeq || disposed || bindingId.value !== bindingAtStart) return
    const data = body?.data
    if (!data || !Number.isSafeInteger(data.markedCount) || data.markedCount < 0) {
      throw Object.assign(new Error('全部已读响应格式不正确'), { code: 500 })
    }
    readAllSubmitting.value = false
    confirmAllOpen.value = false
    readAllMessage.value = data.markedCount > 0
      ? '已标记 ' + data.markedCount + ' 条为已读'
      : '没有需要标记的通知'
    await refreshAfterReadAll(seq)
  } catch (error) {
    if (seq !== readAllSeq || disposed || bindingId.value !== bindingAtStart || error?.cancelled) return
    readAllSubmitting.value = false
    readAllError.value = readAllErrorText(error)
  }
}

/**
 * 使当前通知列表/未读数请求失效；切到公告页签时调用。
 *
 * @returns {void}
 */
function cancelNoticeRequests() {
  listSeq++
  unreadSeq++
  listController?.abort()
  unreadController?.abort()
  listController = null
  unreadController = null
}

/**
 * 用当前已核验的上下文生成页签/跳转查询参数。
 *
 * contexts 通过 history.replaceState 直接同步地址栏，vue-router 的 route.query
 * 不会随之更新。若直接展开 route.query，就会把清空前残留的旧班级参数写回
 * 路由，路由守卫再按旧参数重新核验并恢复旧范围。因此先移除三个上下文参数，
 * 只从 context.current 写回已核验的值；当前上下文为空时不写回，保持清空后
 * 的全部可见范围。其余合法查询参数（如 tab）原样保留。
 *
 * @returns {object} 供 router.replace 使用的查询对象
 */
function contextQuery() {
  const query = { ...route.query }
  for (const key of ['courseSpaceId', 'classId', 'bindingId']) delete query[key]
  if (allScope.value) {
    // 全部范围是入口的显式意图，页签往返和来源跳转不得恢复旧教学班参数。
    query.scope = 'all'
    return query
  }
  for (const key of ['courseSpaceId', 'classId', 'bindingId']) {
    if (context.current?.[key]) query[key] = String(context.current[key])
  }
  return query
}

/**
 * 切换「学习通知 / 课程公告」页签。
 *
 * 切页签会关闭通知详情和全部已读确认，并中止当前页签在途请求；公告子页签
 * 卸载时由自身 cleanup 中止公告请求，避免迟到响应写入新页签。
 *
 * @param {'notices'|'announcements'} tab 目标页签
 * @returns {void}
 */
async function switchTab(tab) {
  if (tab === activeTab.value) return
  closeDetail()
  resetReadAllState()
  if (tab === 'announcements') {
    cancelNoticeRequests()
  }
  clearPendingAnnouncementId()
  const query = contextQuery()
  query.tab = tab
  delete query.announcementId
  await router.replace({ path: route.path, query })
}

/**
 * 从 ANNOUNCEMENT 通知详情跳转到课程公告页签，并按 sourceId 打开公告。
 *
 * 只使用 sourceId 请求公告详情；公告可见性由服务端重新校验，通知行本身
 * 不是访问凭证。其他通知来源不在这里自造路由。
 *
 * @returns {Promise<void>} 页签切换和公告详情打开完成
 */
async function openAnnouncementFromNotice() {
  if (disposed || !detail.value || detail.value.kind !== 'ANNOUNCEMENT') return
  const sourceId = String(detail.value.sourceId ?? '')
  if (!sourceId) return
  closeDetail()
  resetReadAllState()
  cancelNoticeRequests()
  setPendingAnnouncementId(sourceId)
  // 来源跳转同样只写已核验的上下文，清空后的旧参数不得随路由恢复。
  const query = contextQuery()
  query.tab = 'announcements'
  delete query.announcementId
  await router.replace({ path: route.path, query })
}

/**
 * 应用通知筛选：回到第一页并重新请求列表。
 *
 * 空 kind 在 API 层省略；unreadOnly=true 按契约发送字符串 "true"。
 * 未读数和顶栏仍按当前班级范围查询，不受筛选影响；全部已读也仍覆盖
 * 本人所有教学班当前可见通知。
 *
 * @returns {void}
 */
function applyFilters() {
  pageNum.value = 1
  loadList()
}

function changeKind(event) {
  kindFilter.value = event.target.value
  applyFilters()
}

function changeUnreadOnly(event) {
  unreadOnly.value = event.target.checked
  applyFilters()
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
let bindingWatchInitialized = false
watch(bindingId, () => {
  // 首次挂载可能正是通知来源跳转的落地页，不能清掉一次性 announcementId；
  // 只有后续班级变化才使旧公告打开意图失效。
  if (bindingWatchInitialized) clearPendingAnnouncementId()
  bindingWatchInitialized = true
  closeDetail()
  resetReadAllState()
  pageNum.value = 1
  if (activeTab.value === 'notices') {
    loadList()
    loadUnread()
  }
}, { immediate: true })

onBeforeUnmount(() => {
  disposed = true
  listSeq++
  unreadSeq++
  detailSeq++
  readAllSeq++
  listController?.abort()
  unreadController?.abort()
  detailController?.abort()
  readController?.abort()
  readAllController?.abort()
  detailTrigger = null
})
</script>

<template>
  <section class="student-notices" aria-label="学习通知与课程公告">
    <TeachingContext v-if="!allScope" />
    <p v-else class="all-scope-note" role="status">全部当前可见通知：不按之前选择的教学班收窄。</p>
    <div class="notice-tabs" role="tablist" aria-label="学习通知与课程公告">
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'notices'"
        :class="{ active: activeTab === 'notices' }"
        @click="switchTab('notices')"
      >学习通知</button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'announcements'"
        :class="{ active: activeTab === 'announcements' }"
        @click="switchTab('announcements')"
      >课程公告</button>
    </div>

    <template v-if="activeTab === 'notices'">
    <header class="notice-head">
      <div>
        <h2>学习通知</h2>
        <p class="sub">通知列表与详情；已读操作以服务端结果为准。</p>
      </div>
      <div class="notice-head-actions">
        <p class="unread" :class="unreadTone" :title="unreadMessage" aria-live="polite">{{ unreadText }}</p>
        <button type="button" class="detail-read" :disabled="readAllSubmitting" @click="openReadAllConfirm">
          {{ readAllSubmitting ? '处理中…' : '全部标为已读' }}
        </button>
      </div>
    </header>

    <p v-if="readAllMessage" class="read-all-message" role="status">{{ readAllMessage }}</p>
    <ul v-if="readAllWarnings.length" class="detail-warnings" role="status">
      <li v-for="warning in readAllWarnings" :key="warning">{{ warning }}</li>
    </ul>

    <p class="context-note">
      {{ scopeLabel }}
    </p>

    <div class="notice-filters">
      <select class="notice-kind-filter" :value="kindFilter" aria-label="通知类型" @change="changeKind">
        <option value="">全部类型</option>
        <option value="ANNOUNCEMENT">公告</option>
        <option value="PREVIEW">预习任务</option>
        <option value="ACTIVITY">课堂活动</option>
        <option value="ASSIGNMENT">作业</option>
        <option value="GRADE">成绩</option>
      </select>
      <label class="notice-unread-filter">
        <input type="checkbox" :checked="unreadOnly" @change="changeUnreadOnly" />
        只看未读
      </label>
    </div>

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
          <div v-if="detail.kind === 'ANNOUNCEMENT'" class="detail-announcement">
            <button type="button" class="detail-link" @click="openAnnouncementFromNotice">查看公告</button>
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

    <ConfirmDialog
      v-if="confirmAllOpen"
      title="全部标为已读"
      :text="READ_ALL_CONFIRM_TEXT"
      :busy="readAllSubmitting"
      :error="readAllError"
      ok-text="确认"
      @close="closeReadAllConfirm"
      @confirm="confirmReadAll"
    />
    </template>

    <StudentAnnouncements
      v-else
      :binding-id="bindingId"
      :open-announcement-id="requestedAnnouncementId"
      @opened="clearPendingAnnouncementId"
    />
  </section>
</template>

<style scoped>
.student-notices { padding: 20px; background: #fff; border-radius: 8px; }
.all-scope-note { margin: 0 0 14px; padding: 9px 12px; border-radius: 6px; background: #f4f8ff; color: #536780; font-size: 13px; }
/* 通知页签的既有 z-index 高于默认弹窗；局部抬高确认/详情弹窗，保证可操作。 */
.student-notices :deep(.modal-mask) { z-index: 4000; }
.notice-tabs { position: relative; z-index: 3100; display: flex; gap: 8px; margin-bottom: 18px; border-bottom: 1px solid #ebeef5; }
.notice-tabs button {
  padding: 8px 14px; border: 0; border-bottom: 2px solid transparent;
  background: transparent; color: #606266; font: inherit; cursor: pointer;
}
.notice-tabs button.active { color: #409eff; border-bottom-color: #409eff; font-weight: 600; }
.notice-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.notice-head h2 { margin: 0 0 6px; font-size: 20px; color: #1f2d3d; }
.sub { margin: 0; color: #909399; font-size: 13px; }
.notice-head-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; flex-wrap: wrap; }
.unread { margin: 0; padding: 6px 12px; border-radius: 999px; background: #f0f2f5; color: #606266; white-space: nowrap; }
.unread.is-loading { color: #909399; }
.unread.is-unavailable { background: #fdf6ec; color: #b88230; }
.read-all-message { margin: 12px 0 0; color: #67c23a; font-size: 13px; }
.context-note { margin: 14px 0; color: #606266; font-size: 13px; }
.notice-filters { display: flex; align-items: center; gap: 16px; margin: 0 0 14px; color: #606266; font-size: 13px; }
.notice-kind-filter { padding: 5px 8px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; }
.notice-unread-filter { display: inline-flex; align-items: center; gap: 6px; cursor: pointer; }
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
  position: fixed; inset: 0; z-index: 4000;
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
.detail-announcement { padding-top: 10px; }
.detail-link { padding: 0; border: 0; background: transparent; color: #409eff; font: inherit; cursor: pointer; }
.detail-link:hover { text-decoration: underline; }
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
