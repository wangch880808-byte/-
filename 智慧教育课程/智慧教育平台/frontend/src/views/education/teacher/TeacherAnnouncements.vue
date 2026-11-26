<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  archiveTeacherAnnouncement,
  createTeacherAnnouncement,
  getTeacherAnnouncementDetail,
  listTeacherAnnouncements,
  publishTeacherAnnouncement,
  updateTeacherAnnouncement
} from '@/api/education/announcement'
import ConfirmDialog from '@/components/education/ConfirmDialog.vue'
import { context } from '@/education/context'
import { hasPermission, session } from '@/education/session'
import { classifyNotice503, notice503Detail } from '@/views/education/student/notice-error'

/**
 * 教师工作台「班级消息 / 公告」视图（6-04 原型）。
 *
 * 列表必须带有效教学班 bindingId；未选班时不发请求。新建要求当前班级
 * canPublish 且具备 education:announcement:add；编辑要求
 * education:announcement:edit、公告为 DRAFT，并满足“本人创建或本班可发布”
 * 的后端对象权限规则；发布要求 DRAFT + education:announcement:publish +
 * canPublish；归档要求 PUBLISHED + education:announcement:archive + canPublish。
 * 入口隐藏只是交互约束，服务端仍会逐请求复核对象权限和状态。
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

const pageNotice = ref('')
const formOpen = ref(false)
const formMode = ref('create')
const formId = ref('')
const formTitle = ref('')
const formBody = ref('')
const formOpensAt = ref('')
const formClosesAt = ref('')
const formOpensOriginal = ref('')
const formClosesOriginal = ref('')
const formOpensDirty = ref(false)
const formClosesDirty = ref(false)
const formError = ref('')
const formCheckList = ref(false)
const formLoading = ref(false)
const formLoaded = ref(false)
const submitting = ref(false)
const formTitleInput = ref(null)
const formDialog = ref(null)

const actionConfirmOpen = ref(false)
const actionKind = ref('publish')
const actionRow = ref(null)
const actionBusy = ref(false)
const actionError = ref('')
const actionText = computed(() => {
  const title = actionRow.value?.title ? `《${actionRow.value.title}》` : '该公告'
  if (actionKind.value === 'publish') {
    return `${title}发布后将按开放时间对学生生效；开放时间为空时取服务端发布时间。确定发布吗？`
  }
  return `${title}归档后学生将无法再查看，且已发布状态不可恢复。确定归档吗？`
})
const actionTitle = computed(() => actionKind.value === 'publish' ? '发布公告' : '归档公告')
const actionOkText = computed(() => actionKind.value === 'publish' ? '发布' : '归档')

let listSeq = 0
let detailSeq = 0
let formSeq = 0
let actionSeq = 0
let listController = null
let detailController = null
let detailTargetId = ''
let formBindingId = ''
let actionBindingId = ''
let disposed = false

/**
 * 当前教学班绑定 ID。空字符串表示未选有效教学班。
 * 保持字符串，避免 19 位 ID 精度丢失。
 */
const bindingId = computed(() => {
  const value = context.current?.bindingId
  return value === undefined || value === null || value === '' ? '' : String(value)
})

/**
 * 当前登录用户 ID。后端以字符串返回 ID，比较时统一转字符串，避免精度丢失。
 */
const currentUserId = computed(() => {
  const value = session.user?.userId
  return value === undefined || value === null ? '' : String(value)
})

/**
 * 当前教学班是否允许创建和编辑公告。该值来自上下文核验响应，不是前端推测。
 */
const canPublishInCurrentClass = computed(() => context.current?.canPublish === true)

/**
 * 新建草稿入口条件：有效教学班 + 当前班级允许发布 + add 权限。
 * 服务端创建接口还会再次校验当前任课、绑定有效性和 canPublish。
 */
const canCreateDraft = computed(() => Boolean(bindingId.value)
  && canPublishInCurrentClass.value
  && hasPermission('education:announcement:add'))

const titleCodePoints = computed(() => codePointLength(formTitle.value))
const bodyCodePoints = computed(() => codePointLength(formBody.value))
const bodyBytes = computed(() => utf8Length(formBody.value))
const formCanSave = computed(() => formOpen.value && formLoaded.value && !submitting.value)

/**
 * 是否显示草稿编辑入口。
 *
 * 后端先要求 education:announcement:edit，再按“创建者本人或本班 canPublish”
 * 判断对象权限；只显示 DRAFT 编辑入口。其他教师的草稿在缺少发布权限时
 * 不显示编辑，但服务端仍是最终裁决方。
 *
 * @param {object} row 公告列表行
 * @returns {boolean} 是否显示编辑入口
 */
function canEditDraft(row) {
  if (!row || row.status !== 'DRAFT' || !hasPermission('education:announcement:edit')) return false
  if (canPublishInCurrentClass.value) return true
  return Boolean(currentUserId.value) && String(row.createdByUserId ?? '') === currentUserId.value
}

/**
 * 是否显示发布入口。
 *
 * 服务端发布接口要求当前任课教师具备 canPublish；前端同时要求 DRAFT 和
 * education:announcement:publish。已归档公告不能再发布，重复发布已发布公告
 * 由服务端幂等处理，不会出现在本入口。
 *
 * @param {object} row 公告行或详情
 * @returns {boolean} 是否显示发布入口
 */
function canPublishDraft(row) {
  return Boolean(row) && row.status === 'DRAFT' && canPublishInCurrentClass.value
    && hasPermission('education:announcement:publish')
}

/**
 * 是否显示归档入口。
 *
 * 仅 PUBLISHED 可归档；草稿归档会触发服务端 409。归档不可恢复，确认文案必须
 * 明确告知学生不再可见。服务端仍会复核当前任课与 canPublish。
 *
 * @param {object} row 公告行或详情
 * @returns {boolean} 是否显示归档入口
 */
function canArchiveRow(row) {
  return Boolean(row) && row.status === 'PUBLISHED' && canPublishInCurrentClass.value
    && hasPermission('education:announcement:archive')
}

/**
 * 统计 Unicode 码点数量，与后端 codePointCount 对齐。
 *
 * @param {string} value 文本
 * @returns {number} 码点数
 */
function codePointLength(value) {
  return value ? Array.from(value).length : 0
}

/**
 * 统计 UTF-8 编码字节数，与后端 65,535 字节上限对齐。
 *
 * @param {string} value 文本
 * @returns {number} UTF-8 字节数
 */
function utf8Length(value) {
  return value ? new TextEncoder().encode(value).length : 0
}

function pad2(value) {
  return String(value).padStart(2, '0')
}

/**
 * datetime-local 值转 UTC ISO 字符串；空值返回 null。
 *
 * 后端要求 RFC3339 且毫秒精度，toISOString 正好输出毫秒 UTC。
 *
 * @param {string} localValue datetime-local 输入值
 * @returns {string|null} UTC 时间字符串
 */
function toUtcIso(localValue) {
  if (!localValue) return null
  const date = new Date(localValue)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

/**
 * UTC ISO 字符串转 datetime-local 输入值；空值或非法值返回空串。
 *
 * @param {string} iso UTC 时间字符串
 * @returns {string} 本机时区的 datetime-local 值
 */
function toLocalInput(iso) {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
    + `T${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

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
  detailTargetId = id
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
  detailTargetId = ''
  detailOpen.value = false
  detailState.value = 'idle'
  detail.value = null
  detailMessage.value = ''
}

function resetFormFields() {
  formTitle.value = ''
  formBody.value = ''
  formOpensAt.value = ''
  formClosesAt.value = ''
  formOpensOriginal.value = ''
  formClosesOriginal.value = ''
  formOpensDirty.value = false
  formClosesDirty.value = false
  formError.value = ''
  formCheckList.value = false
  formLoading.value = false
  formLoaded.value = false
  submitting.value = false
}

/**
 * 关闭表单并作废所有旧响应。
 *
 * formSeq 前进后，迟到的详情加载或保存响应不能再填充或重新打开表单；
 * 切班和卸载也复用该方法。
 */
function closeForm() {
  formSeq++
  formOpen.value = false
  formMode.value = 'create'
  formId.value = ''
  formBindingId = ''
  resetFormFields()
}

/**
 * 打开新建草稿表单。入口条件在点击时再检查一次，避免权限或班级变化后误用。
 */
async function openCreateForm() {
  if (disposed || submitting.value || !canCreateDraft.value) return
  formSeq++
  formMode.value = 'create'
  formId.value = ''
  formBindingId = bindingId.value
  pageNotice.value = ''
  resetFormFields()
  formLoaded.value = true
  formOpen.value = true
  await nextTick()
  formTitleInput.value?.focus()
}

/**
 * 打开编辑草稿表单，先读取详情正文。
 *
 * 入口只按列表行判断，列表不返回正文，因此必须等服务端详情。详情迟到、
 * 用户关闭表单或切班后，formSeq 与绑定快照共同阻止旧响应写入新表单。
 *
 * @param {object} row 公告列表行
 * @returns {Promise<void>} 详情加载完成
 */
async function openEditForm(row) {
  if (disposed || submitting.value || !canEditDraft(row)) return
  const id = String(row?.id ?? '')
  if (!id) return
  const seq = ++formSeq
  const atBinding = bindingId.value
  formMode.value = 'edit'
  formId.value = id
  formBindingId = atBinding
  pageNotice.value = ''
  resetFormFields()
  formLoading.value = true
  formOpen.value = true
  await nextTick()
  if (seq !== formSeq || disposed || !formOpen.value) return
  formDialog.value?.focus()

  try {
    const body = await getTeacherAnnouncementDetail(id)
    if (seq !== formSeq || disposed || !formOpen.value || formMode.value !== 'edit'
        || bindingId.value !== atBinding) return
    const item = body?.data
    if (!item || String(item.id) !== id
        || typeof item.title !== 'string' || typeof item.body !== 'string') {
      throw Object.assign(new Error('公告详情响应格式不正确'), { code: 500 })
    }
    if (item.status !== 'DRAFT') {
      closeForm()
      pageNotice.value = '该公告已发布或已归档，不能编辑；列表已刷新。'
      await loadList()
      return
    }
    formTitle.value = item.title
    formBody.value = item.body
    formOpensAt.value = toLocalInput(item.opensAt)
    formClosesAt.value = toLocalInput(item.closesAt)
    formOpensOriginal.value = item.opensAt || ''
    formClosesOriginal.value = item.closesAt || ''
    formOpensDirty.value = false
    formClosesDirty.value = false
    formLoaded.value = true
    await nextTick()
    formTitleInput.value?.focus()
  } catch (error) {
    if (seq !== formSeq || disposed || !formOpen.value || error?.cancelled) return
    formError.value = formErrorMessage(error, 'load')
  } finally {
    if (seq === formSeq) formLoading.value = false
  }
}

/**
 * 表单失败的分类文案。
 *
 * 400 保留字段级服务端消息；403/404/409 与 503 分开说明；创建请求超时
 * 或网络中断时无法确认服务端是否已经落库，只提示先查列表，不自动重试。
 *
 * @param {object} error 统一请求模块错误
 * @param {'create'|'edit'|'load'} phase 当前操作阶段
 * @returns {string} 表单内展示的错误文案
 */
function formErrorMessage(error, phase) {
  const rawCode = error?.code
  const code = Number(rawCode)
  const message = typeof error?.message === 'string' ? error.message.trim() : ''
  const networkFailure = rawCode === 'ECONNABORTED' || rawCode === 'ERR_NETWORK'
    || error?.kind === 'network'
  const ambiguousCreateFailure = phase === 'create'
    && (networkFailure || code === 504 || code === 408)
  if (ambiguousCreateFailure) {
    formCheckList.value = true
    return '请求超时或网络中断，无法确认草稿是否已保存。请先刷新列表确认，再决定是否重试；不要直接重复提交。'
  }
  if (networkFailure) {
    return '请求超时或网络中断，尚未确认保存结果，请稍后重试。'
  }
  if (error?.forbidden || code === 403) {
    return '没有该公告操作的权限，请联系管理员。' + (message ? `（${message}）` : '')
  }
  if (code === 404) {
    return '公告或教学班不存在，或当前不可访问。' + (message ? `（${message}）` : '')
  }
  if (code === 409) {
    return '公告状态已变化，不能保存。' + (message ? `（${message}）` : '')
  }
  if (code === 503) {
    const prefix = classifyNotice503(error) === 'authorization'
      ? '教学班授权服务暂不可用：'
      : '服务暂不可用：'
    return prefix + notice503Detail(error)
  }
  if (code === 400) return message || '标题、正文或时间不符合要求。'
  return message || '保存失败，请稍后重试。'
}

/**
 * 提交新建或编辑草稿。
 *
 * 提交期间禁用保存按钮并忽略重复点击。成功后才关闭表单并重拉当前列表；
 * 请求在切班或关闭表单后返回时，序号检查保证不写旧表单、不影响新班级列表。
 *
 * TODO(6-04-create-idempotency)：创建接口暂无幂等键。解除条件：后端接受
 * Idempotency-Key 并为同一教师/班级建立唯一约束后，可改为自动重试。
 *
 * @returns {Promise<void>} 提交完成
 */
async function submitForm() {
  if (!formCanSave.value || disposed) return
  const seq = formSeq
  const mode = formMode.value
  const id = formId.value
  const atBinding = formBindingId
  const title = formTitle.value.trim()
  const bodyText = formBody.value
  const opensAt = mode === 'edit' && !formOpensDirty.value
    ? (formOpensOriginal.value || null)
    : toUtcIso(formOpensAt.value)
  const closesAt = mode === 'edit' && !formClosesDirty.value
    ? (formClosesOriginal.value || null)
    : toUtcIso(formClosesAt.value)
  let problem = ''

  if (!title) {
    problem = '标题不能为空'
  } else if (codePointLength(title) > 200) {
    problem = '标题最多 200 字'
  } else if (!bodyText.trim()) {
    problem = '正文不能为空'
  } else if (codePointLength(bodyText) > 20000) {
    problem = '正文超过 20000 字'
  } else if (utf8Length(bodyText) > 65535) {
    problem = '正文 UTF-8 编码后超过 65535 字节'
  } else if (opensAt && closesAt && new Date(closesAt) <= new Date(opensAt)) {
    problem = '关闭时间必须晚于开放时间'
  }

  if (problem) {
    formError.value = problem
    return
  }

  formError.value = ''
  formCheckList.value = false
  submitting.value = true
  const payload = { title, body: bodyText, opensAt, closesAt }

  try {
    const response = mode === 'edit'
      ? await updateTeacherAnnouncement(id, payload)
      : await createTeacherAnnouncement({ bindingId: atBinding, ...payload })
    if (seq !== formSeq || disposed) return
    const saved = response?.data
    if (!saved || typeof saved !== 'object' || saved.id === undefined || saved.id === null
        || String(saved.id) === '' || saved.status !== 'DRAFT'
        || (mode === 'edit' && String(saved.id) !== id)
        || (mode === 'create' && String(saved.bindingId ?? '') !== atBinding)) {
      throw Object.assign(new Error('公告保存响应格式不正确'), { code: 500 })
    }
    closeForm()
    pageNotice.value = mode === 'edit' ? '公告草稿已保存。' : '公告草稿已创建。'
    if (mode === 'create') pageNum.value = 1
    await loadList()
  } catch (error) {
    if (seq !== formSeq || disposed || error?.cancelled) return
    submitting.value = false
    formError.value = formErrorMessage(error, mode)
    if (Number(error?.code) === 409) {
      await loadList()
    }
  }
}

/**
 * 创建超时后由用户确认列表，再决定是否重试；不自动重发没有幂等键的创建请求。
 *
 * @returns {Promise<void>} 列表刷新完成
 */
async function refreshListAfterCreateTimeout() {
  if (submitting.value) return
  closeForm()
  pageNotice.value = ''
  await loadList()
}

function handleFormEscape() {
  if (submitting.value) return
  closeForm()
}

/**
 * 关闭发布/归档确认框并作废迟到响应。
 *
 * actionSeq 前进后，已发出的写请求即使返回 200/403/409/503，也不能再刷新
 * 当前班级列表、详情或页面提示。切班和卸载复用本函数。
 */
function resetActionConfirm() {
  actionSeq++
  actionConfirmOpen.value = false
  actionBusy.value = false
  actionKind.value = 'publish'
  actionRow.value = null
  actionBindingId = ''
  actionError.value = ''
}

/**
 * 用户关闭确认框。
 *
 * 提交中允许关闭，但只关闭 UI 并作废响应处理；已发出的写请求仍可能在服务端
 * 完成。因此关闭后不自动重试，也不根据迟到响应补写提示或列表，教师需重新
 * 读取公告状态确认结果。
 */
function closeActionConfirm() {
  resetActionConfirm()
}

/**
 * 打开发布/归档确认框。点击时重新检查状态、权限和当前班级。
 *
 * @param {object} row 公告行或详情
 * @param {'publish'|'archive'} kind 操作类型
 */
function openActionConfirm(row, kind) {
  if (disposed || actionBusy.value) return
  if (!bindingId.value) return
  if (kind === 'publish' && !canPublishDraft(row)) return
  if (kind === 'archive' && !canArchiveRow(row)) return
  const id = String(row?.id ?? '')
  if (!id) return
  actionSeq++
  actionKind.value = kind
  actionRow.value = {
    id,
    title: typeof row.title === 'string' ? row.title : '',
    status: row.status
  }
  actionBindingId = bindingId.value
  actionBusy.value = false
  actionError.value = ''
  actionConfirmOpen.value = true
  pageNotice.value = ''
}

function isNetworkFailure(error) {
  return error?.code === 'ECONNABORTED' || error?.code === 'ERR_NETWORK'
    || error?.kind === 'network'
}

/**
 * 发布/归档失败的分类文案。
 *
 * 403 保留对象权限提示；404、409 与 503 分开说明；503 必须保留服务端消息，
 * 不能把授权故障显示成成功。网络超时或中断无法确认服务端是否已提交，提示
 * 重新读取列表和详情后再决定，不自动重试。
 *
 * @param {object} error 统一请求模块错误
 * @param {'publish'|'archive'} kind 操作类型
 * @returns {string} 确认框内错误文案
 */
function actionErrorMessage(error, kind) {
  const code = Number(error?.code)
  const message = typeof error?.message === 'string' ? error.message.trim() : ''
  const label = kind === 'publish' ? '发布' : '归档'
  if (isNetworkFailure(error)) {
    return `请求超时或网络中断，未确认${label}结果；请刷新列表和详情后确认公告状态。`
  }
  if (error?.forbidden || code === 403) {
    return `没有${label}公告的权限，请联系管理员。` + (message ? `（${message}）` : '')
  }
  if (code === 404) {
    return '公告不存在或不属于当前班级，或当前不可访问。' + (message ? `（${message}）` : '')
  }
  if (code === 409) {
    return `公告状态已变化，不能${label}。` + (message ? `（${message}）` : '')
  }
  if (code === 503) {
    const prefix = classifyNotice503(error) === 'authorization'
      ? '教学班授权服务暂不可用：'
      : '服务暂不可用：'
    return prefix + notice503Detail(error)
  }
  return message || `${label}失败，请稍后重试。`
}

/**
 * 判断失败后是否需要重新读取公告状态。
 *
 * 404/409/503、网络中断和响应形状不一致都可能意味着服务端状态与页面快照
 * 不同；403 没有对象状态变化，不额外刷新。发布 503 时保留确认框错误，
 * 同时重读列表/详情，让教师看到事务回滚后的真实状态。
 *
 * @param {object} error 统一请求模块错误
 * @returns {boolean} 是否重读
 */
function shouldRefreshAction(error) {
  const code = Number(error?.code)
  if (error?.forbidden || code === 403) return false
  return [404, 409, 503].includes(code) || isNetworkFailure(error) || error?.refresh === true
}

/**
 * 用发布/归档响应刷新仍打开的同一公告详情。
 *
 * 写入前推进 detailSeq 并中止旧详情 GET：发布 503 后的状态重读可能仍在
 * 飞行，重试成功响应必须先作废旧读，否则旧快照会在稍后覆盖 PUBLISHED/
 * ARCHIVED 状态。详情仍在加载（detail.value 为空）时也允许按操作响应写入。
 *
 * @param {object} item 服务端返回的公告详情
 * @param {string} rowId 操作前的公告 ID
 * @returns {boolean} 是否已用响应更新详情
 */
function applyActionDetail(item, rowId) {
  if (!detailOpen.value || detailTargetId !== rowId) return false
  if (typeof item.title !== 'string' || typeof item.body !== 'string') return false
  detailSeq++
  detailController?.abort()
  detailController = null
  detail.value = item
  detailState.value = 'ready'
  detailMessage.value = ''
  return true
}

/**
 * 失败后重读当前列表，并在同一公告详情仍打开时重读详情。
 *
 * @param {string} rowId 公告 ID
 * @param {number} seq 操作代际
 * @param {string} atBinding 操作时的班级 bindingId
 * @returns {Promise<void>} 重读完成
 */
async function refreshActionStateAfterFailure(rowId, seq, atBinding) {
  await loadList()
  if (disposed || seq !== actionSeq || bindingId.value !== atBinding) return
  if (!detailOpen.value || detailTargetId !== rowId) return
  await openDetail(rowId)
}

/**
 * 确认发布或归档。
 *
 * 请求发出前再次校验班级、状态和权限；请求发出后切换班级或卸载会推进
 * actionSeq，迟到响应不得刷新新班级列表、详情或提示。成功时先按服务端对象
 * 更新仍打开的详情，再关闭确认框并重拉列表；已发布/已归档的重复操作由
 * 服务端幂等返回对象，前端按同一成功路径处理。
 *
 * @returns {Promise<void>} 操作完成
 */
async function confirmAnnouncementAction() {
  if (disposed || actionBusy.value || !actionConfirmOpen.value || !actionRow.value) return
  const kind = actionKind.value
  const row = { ...actionRow.value }
  const atBinding = actionBindingId
  if (!atBinding || bindingId.value !== atBinding) {
    resetActionConfirm()
    return
  }
  const allowed = kind === 'publish' ? canPublishDraft(row) : canArchiveRow(row)
  if (!allowed) {
    actionError.value = '当前班级、公告状态或权限已变化，请关闭确认框并刷新列表。'
    return
  }

  // 每次真正发请求都推进代际：上一次失败后的状态重读若仍在飞行，不能再
  // 启动详情读取或写页面；本次响应以新 seq 为准。
  const seq = ++actionSeq
  actionBusy.value = true
  actionError.value = ''
  try {
    const response = kind === 'publish'
      ? await publishTeacherAnnouncement(row.id)
      : await archiveTeacherAnnouncement(row.id)
    if (seq !== actionSeq || disposed || bindingId.value !== atBinding) return
    const expectedStatus = kind === 'publish' ? 'PUBLISHED' : 'ARCHIVED'
    const item = response?.data
    if (!item || typeof item !== 'object' || String(item.id) !== row.id
        || item.status !== expectedStatus) {
      throw Object.assign(new Error('公告操作响应格式不正确'), { code: 500, refresh: true })
    }
    if (!applyActionDetail(item, row.id)
        && detailOpen.value && detailTargetId === row.id) {
      await openDetail(row.id)
      if (seq !== actionSeq || disposed || bindingId.value !== atBinding) return
    }
    resetActionConfirm()
    pageNotice.value = kind === 'publish'
      ? '公告已发布，开放时间按公告设置生效。'
      : '公告已归档，学生将无法查看。'
    await loadList()
  } catch (error) {
    if (seq !== actionSeq || disposed || bindingId.value !== atBinding || error?.cancelled) return
    actionBusy.value = false
    actionError.value = actionErrorMessage(error, kind)
    if (shouldRefreshAction(error)) {
      await refreshActionStateAfterFailure(row.id, seq, atBinding)
    }
  }
}

function changeStatus(event) {
  statusFilter.value = event.target.value
  pageNum.value = 1
  pageNotice.value = ''
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
  closeForm()
  resetActionConfirm()
  pageNotice.value = ''
  pageNum.value = 1
  loadList()
}, { immediate: true })

onBeforeUnmount(() => {
  disposed = true
  listSeq++
  detailSeq++
  formSeq++
  actionSeq++
  listController?.abort()
  detailController?.abort()
})
</script>

<template>
  <div class="teacher-announcements">
    <header class="ann-head">
      <div>
        <h2>班级消息 / 公告</h2>
        <p class="sub">本班公告按教学班授权；支持草稿新建、编辑以及发布、归档。</p>
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
      <button v-if="canCreateDraft" type="button" class="ann-create" @click="openCreateForm">
        新建公告草稿
      </button>
    </div>

    <p v-if="pageNotice" class="ann-notice" role="status">{{ pageNotice }}</p>

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
          <button
            v-if="canEditDraft(row)"
            type="button"
            class="ann-edit"
            :data-id="row.id"
            @click="openEditForm(row)"
          >
            编辑草稿
          </button>
          <button
            v-if="canPublishDraft(row)"
            type="button"
            class="ann-publish"
            :data-id="row.id"
            @click="openActionConfirm(row, 'publish')"
          >
            发布
          </button>
          <button
            v-if="canArchiveRow(row)"
            type="button"
            class="ann-archive"
            :data-id="row.id"
            @click="openActionConfirm(row, 'archive')"
          >
            归档
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
          <button
            v-if="detailState === 'ready' && detail && canPublishDraft(detail)"
            type="button"
            class="ann-publish"
            :data-id="detail.id"
            @click="openActionConfirm(detail, 'publish')"
          >
            发布
          </button>
          <button
            v-if="detailState === 'ready' && detail && canArchiveRow(detail)"
            type="button"
            class="ann-archive"
            :data-id="detail.id"
            @click="openActionConfirm(detail, 'archive')"
          >
            归档
          </button>
          <button type="button" class="ann-close" @click="closeDetail">关闭</button>
        </footer>
      </section>
    </div>

    <div
      v-if="formOpen"
      class="ann-overlay"
      @click.self="!submitting && closeForm()"
      @keydown.esc.stop.prevent="handleFormEscape"
    >
      <section
        ref="formDialog"
        class="ann-dialog ann-form-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="teacher-ann-form-title"
        tabindex="-1"
      >
        <header class="ann-dialog-head">
          <h3 id="teacher-ann-form-title">{{ formMode === 'edit' ? '编辑公告草稿' : '新建公告草稿' }}</h3>
          <button type="button" class="ann-close" :disabled="submitting" @click="closeForm">关闭</button>
        </header>

        <div v-if="formLoading" class="state" role="status">正在读取公告草稿…</div>

        <div v-if="formError" class="ann-form-error" role="alert">{{ formError }}</div>
        <button
          v-if="formCheckList"
          type="button"
          class="ann-check-list"
          @click="refreshListAfterCreateTimeout"
        >
          刷新列表确认
        </button>

        <div v-if="formLoaded" class="ann-form">
          <label class="ann-field" for="ann-form-title">
            <span>标题</span>
            <input
              id="ann-form-title"
              ref="formTitleInput"
              v-model="formTitle"
              type="text"
              placeholder="1–200 字"
              :disabled="submitting"
            >
            <em class="ann-count" :class="{ over: titleCodePoints > 200 }">
              {{ titleCodePoints }} / 200 字
            </em>
          </label>

          <label class="ann-field" for="ann-form-body">
            <span>正文</span>
            <textarea
              id="ann-form-body"
              v-model="formBody"
              rows="8"
              placeholder="公告正文"
              :disabled="submitting"
            ></textarea>
            <em class="ann-count" :class="{ over: bodyCodePoints > 20000 || bodyBytes > 65535 }">
              {{ bodyCodePoints }} / 20000 字 · {{ bodyBytes }} / 65535 字节
            </em>
          </label>

          <div class="ann-field-row">
            <label class="ann-field" for="ann-form-opens">
              <span>开放时间（可空）</span>
              <input id="ann-form-opens" v-model="formOpensAt" type="datetime-local" step="1"
                :disabled="submitting" @input="formOpensDirty = true">
            </label>
            <label class="ann-field" for="ann-form-closes">
              <span>关闭时间（可空）</span>
              <input id="ann-form-closes" v-model="formClosesAt" type="datetime-local" step="1"
                :disabled="submitting" @input="formClosesDirty = true">
            </label>
          </div>

          <p class="ann-form-note">
            时间为本机时区，提交时转换为 UTC；开放时间留空表示发布时取服务端当前时间，关闭时间留空表示不关闭。
          </p>
        </div>

        <footer class="ann-actions">
          <button type="button" class="ann-close" :disabled="submitting" @click="closeForm">取消</button>
          <button type="button" class="ann-save" :disabled="!formCanSave" @click="submitForm">
            {{ submitting ? '提交中…' : '保存草稿' }}
          </button>
        </footer>
      </section>
    </div>

    <ConfirmDialog
      v-if="actionConfirmOpen"
      style="z-index: 3100"
      :title="actionTitle"
      :text="actionText"
      :confirm-disabled="actionBusy"
      :error="actionError"
      :danger="actionKind === 'archive'"
      :ok-text="actionBusy ? '提交中…' : actionOkText"
      @close="closeActionConfirm"
      @confirm="confirmAnnouncementAction"
    />
  </div>
</template>

<style scoped>
.teacher-announcements { padding: 20px; background: #fff; border-radius: 8px; }
.ann-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.ann-head h2 { margin: 0 0 6px; font-size: 20px; color: #1f2d3d; }
.sub { margin: 0; color: #909399; font-size: 13px; }
.scope { padding: 6px 12px; border-radius: 999px; background: #f0f2f5; color: #606266; white-space: nowrap; }
.ann-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 16px 0; }
.status-filter { padding: 5px 8px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; }
.ann-create { padding: 6px 14px; border: 1px solid #409eff; border-radius: 4px; background: #409eff; color: #fff; cursor: pointer; }
.ann-create:hover { background: #66b1ff; border-color: #66b1ff; }
.ann-notice { margin: 0 0 14px; padding: 9px 12px; border-radius: 4px; background: #f4f4f5; color: #606266; font-size: 13px; }
.state { display: flex; flex-direction: column; gap: 6px; padding: 28px 20px; text-align: center; color: #606266; background: #fafafa; border-radius: 8px; }
.state-info { background: #f4f4f5; }
.state-error { background: #fef0f0; color: #c45656; }
.state-warn { background: #fdf6ec; color: #b88230; }
.ann-list { list-style: none; margin: 0; padding: 0; border-top: 1px solid #ebeef5; }
.ann-item { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 14px 4px; border-bottom: 1px solid #ebeef5; }
.ann-main { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; padding: 0; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; flex-wrap: wrap; }
.ann-edit, .ann-publish, .ann-archive { flex: none; padding: 5px 10px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; cursor: pointer; }
.ann-edit, .ann-publish { color: #409eff; }
.ann-archive { color: #d4380d; }
.ann-edit:hover, .ann-publish:hover { border-color: #409eff; }
.ann-archive:hover { border-color: #d4380d; }
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
.ann-close:disabled { color: #c0c4cc; border-color: #ebeef5; cursor: not-allowed; }
.ann-detail-title { margin: 0 0 8px; font-size: 17px; color: #303133; }
.ann-detail-meta { display: flex; align-items: center; gap: 12px; margin: 0 0 14px; color: #909399; font-size: 12px; flex-wrap: wrap; }
.ann-body { white-space: pre-wrap; line-height: 1.7; color: #303133; font-size: 14px; }
.ann-actions { display: flex; justify-content: flex-end; gap: 8px; padding-top: 18px; }
.ann-form-dialog:focus { outline: none; }
.ann-form { display: flex; flex-direction: column; gap: 14px; }
.ann-field { display: flex; flex-direction: column; gap: 6px; color: #606266; font-size: 13px; }
.ann-field input, .ann-field textarea { width: 100%; box-sizing: border-box; padding: 7px 9px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #303133; font: inherit; }
.ann-field input:disabled, .ann-field textarea:disabled { background: #f5f7fa; cursor: not-allowed; }
.ann-field textarea { resize: vertical; line-height: 1.6; }
.ann-count { align-self: flex-end; color: #909399; font-size: 12px; font-style: normal; }
.ann-count.over { color: #c45656; }
.ann-field-row { display: flex; gap: 14px; }
.ann-field-row .ann-field { flex: 1; min-width: 0; }
.ann-form-note { margin: 0; color: #909399; font-size: 12px; line-height: 1.6; }
.ann-form-error { margin: 0 0 12px; padding: 9px 12px; border-radius: 4px; background: #fef0f0; color: #c45656; font-size: 13px; line-height: 1.5; }
.ann-check-list { margin: 0 0 12px; padding: 5px 10px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #409eff; cursor: pointer; }
.ann-save { padding: 5px 14px; border: 1px solid #409eff; border-radius: 4px; background: #409eff; color: #fff; cursor: pointer; }
.ann-save:disabled { border-color: #a0cfff; background: #a0cfff; cursor: not-allowed; }
</style>
