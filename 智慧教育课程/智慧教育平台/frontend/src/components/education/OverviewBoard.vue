<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { getStudentTodayTodos, getTeacherOverview, isPositiveLongString } from '@/api/education/overview'

/**
 * 教师/学生总览共享展示组件。
 *
 * 只消费 #24 当前返回的 10 个指标；按 metricCode 映射，绝不按数组下标。
 * 单卡契约违约只降级当前卡片，顶层响应异常才显示整页错误。身份和权限由
 * Vue 路由守卫、canOpen 和服务端 @PreAuthorize 负责，本组件不补发身份、
 * 班级或组织参数。
 */

const props = defineProps({
  /**
   * 页面配置：endpoint、timeFilter、classFilter、groups、metrics。
   * metrics[].code 必须与后端 metricCode 完全一致。
   */
  config: { type: Object, required: true }
})

const STATUS_OK = 'OK'
const STATUS_NO_DATA = 'NO_DATA'
const STATUS_UNAVAILABLE = 'UNAVAILABLE'
const SOURCE_LABEL = {
  course: '课程范围',
  classroom: '课堂',
  assignment: '任务',
  grade: '成绩',
  notice: '通知'
}
const RFC3339 = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$/

const draftFrom = ref('')
const draftTo = ref('')
const draftClassId = ref('')
const appliedFrom = ref('')
const appliedTo = ref('')
const appliedClassId = ref('')
const filterError = ref('')
const pageState = ref('loading')
const pageMessage = ref('')
const loading = ref(false)
const rows = ref([])
const asOf = ref('')
const windowStart = ref('')
const windowEnd = ref('')

let requestSeq = 0
let controller = null
let disposed = false

const loader = props.config.endpoint === 'teacher' ? getTeacherOverview : getStudentTodayTodos

const hasCustomWindow = computed(() => Boolean(appliedFrom.value && appliedTo.value))
const windowHint = computed(() => hasCustomWindow.value
  ? '自定义时间窗只影响课堂、作业指标；班级、学生、成绩、通知为当前快照。'
  : '未填写时间时使用服务端默认 UTC 自然日；时间窗只影响课堂、作业指标，其他指标为当前快照。')
const groupRows = computed(() => (props.config.groups || []).map(group => ({
  ...group,
  displayLabel: hasCustomWindow.value && group.customLabel ? group.customLabel : group.label,
  rows: rows.value.filter(row => row.config.group === group.key)
})).filter(group => group.rows.length > 0))
const summary = computed(() => {
  const ok = rows.value.filter(row => row.status === STATUS_OK).length
  const noData = rows.value.filter(row => row.status === STATUS_NO_DATA).length
  const unavailable = rows.value.filter(row => row.status === STATUS_UNAVAILABLE && !row.contract).length
  const contract = rows.value.filter(row => row.contract).length
  return { ok, noData, unavailable, contract }
})

function isNonNegativeInteger(value) {
  return typeof value === 'number' && Number.isFinite(value)
    && Math.floor(value) === value && value >= 0
}

function isLeapYear(year) {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0
}

function daysInMonth(year, month) {
  const days = [31, isLeapYear(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  return days[month - 1] || 0
}

/**
 * 校验 RFC3339 时间并验证真实日历日期。
 *
 * Date.parse 会把 2026-02-30 归一化为 3 月 2 日，因此必须先按年月日、
 * 时分秒和偏移逐项校验，再交给 Date 做最终解析。支持 Z、合法偏移、
 * 闰日和 0-9 位小数秒；不硬编码三位毫秒。
 *
 * @param {*} value 响应中的时间字符串
 * @returns {boolean} 是否为合法 RFC3339 日历时间
 */
function isRfc3339(value) {
  if (typeof value !== 'string') return false
  const match = RFC3339.exec(value)
  if (!match) return false
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const hour = Number(match[4])
  const minute = Number(match[5])
  const second = Number(match[6])
  const offset = match[8]
  if (month < 1 || month > 12) return false
  if (day < 1 || day > daysInMonth(year, month)) return false
  if (hour > 23 || minute > 59 || second > 59) return false
  if (offset !== 'Z') {
    const offsetHour = Number(offset.slice(1, 3))
    const offsetMinute = Number(offset.slice(4, 6))
    if (offsetHour > 23 || offsetMinute > 59) return false
  }
  return !Number.isNaN(Date.parse(value))
}

/**
 * 将服务端 UTC 时间按本机时区显示。
 *
 * 输入仍按本机时区的 datetime-local 读取，请求前 buildQuery 转成 UTC；
 * 服务端默认窗口口径仍是 UTC 自然日，不在前端改写。
 *
 * @param {string} value 服务端 RFC3339 时间
 * @returns {string} 本机时区的显示时间
 */
function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * 校验筛选草稿。非法输入不发起请求，避免把参数错误送到服务端。
 *
 * @param {string} from datetime-local 开始时间
 * @param {string} to datetime-local 结束时间
 * @param {string} classId 教师教学班筛选
 * @returns {string} 错误文案；合法时为空串
 */
function validateFilters(from, to, classId) {
  if (Boolean(from) !== Boolean(to)) {
    return '开始时间和结束时间必须同时填写，或都留空使用服务端默认窗口。'
  }
  if (from && to) {
    const fromMs = Date.parse(from)
    const toMs = Date.parse(to)
    if (Number.isNaN(fromMs) || Number.isNaN(toMs)) {
      return '时间格式不合法，请重新选择。'
    }
    if (fromMs >= toMs) {
      return '结束时间必须晚于开始时间。'
    }
  }
  if (props.config.classFilter && classId && !isPositiveLongString(classId)) {
    return 'classId 必须是正 Long 字符串，最大 9223372036854775807；它表示教学班 ID，不是 bindingId。'
  }
  return ''
}

function buildQuery() {
  const query = {}
  if (props.config.timeFilter && appliedFrom.value && appliedTo.value) {
    query.from = new Date(appliedFrom.value).toISOString()
    query.to = new Date(appliedTo.value).toISOString()
  }
  if (props.config.classFilter && appliedClassId.value) {
    query.classId = appliedClassId.value
  }
  return query
}

/**
 * 让当前请求失效并清空旧数据。筛选非法、切换筛选或卸载时使用，防止旧响应
 * 覆盖新的页面状态，也避免刷新失败后继续把旧值标成最新。
 */
function invalidateRequest() {
  requestSeq++
  controller?.abort()
  controller = null
  loading.value = false
  rows.value = []
  asOf.value = ''
  windowStart.value = ''
  windowEnd.value = ''
}

function applyFilters() {
  if (disposed) return
  const problem = validateFilters(draftFrom.value, draftTo.value, draftClassId.value)
  filterError.value = problem
  if (problem) {
    invalidateRequest()
    pageState.value = 'param-error'
    pageMessage.value = problem
    return
  }
  appliedFrom.value = draftFrom.value
  appliedTo.value = draftTo.value
  appliedClassId.value = draftClassId.value
  load()
}

function resetFilters() {
  if (disposed) return
  draftFrom.value = ''
  draftTo.value = ''
  draftClassId.value = ''
  appliedFrom.value = ''
  appliedTo.value = ''
  appliedClassId.value = ''
  filterError.value = ''
  load()
}

function refresh() {
  load()
}

/**
 * 读取总览。
 *
 * 每次请求推进 requestSeq 并中止上一请求；真正派发 API 前再次检查序号和
 * 卸载状态，覆盖“已排队但尚未派发”的失效窗口。成功后按 metricCode 组装卡片。
 *
 * @returns {Promise<void>} 请求完成
 */
async function load() {
  if (disposed) return
  const problem = validateFilters(appliedFrom.value, appliedTo.value, appliedClassId.value)
  if (problem) {
    filterError.value = problem
    invalidateRequest()
    pageState.value = 'param-error'
    pageMessage.value = problem
    return
  }

  const seq = ++requestSeq
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  pageState.value = 'loading'
  pageMessage.value = ''
  filterError.value = ''
  rows.value = []
  asOf.value = ''
  windowStart.value = ''
  windowEnd.value = ''

  await nextTick()
  if (disposed || seq !== requestSeq || current.signal.aborted) return

  try {
    const body = await loader(buildQuery(), { signal: current.signal })
    if (disposed || seq !== requestSeq || current.signal.aborted) return
    applyResponse(body)
  } catch (error) {
    if (disposed || seq !== requestSeq || error?.cancelled) return
    handleError(error)
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

/**
 * 校验顶层响应并组装卡片。
 *
 * data、items、asOf 或 window 不合法时整页显示响应异常，不能用空数组或
 * 0 伪装成合法数据；单指标缺项、重复和字段违约在卡片级降级。
 *
 * @param {object} body 统一请求模块响应体
 */
function applyResponse(body) {
  if (!body || typeof body !== 'object' || Number(body.code) !== 200) {
    setContractError('响应缺少成功业务码，无法确认总览数据。')
    return
  }
  const data = body.data
  if (!data || typeof data !== 'object') {
    setContractError('响应缺少 data，页面不会显示 0 或示例数据。')
    return
  }
  if (!Array.isArray(data.items) || data.items.length === 0) {
    setContractError('响应 items 为空；总览契约要求至少返回一个指标项。')
    return
  }
  if (!isRfc3339(data.asOf)) {
    setContractError('响应缺少合法的 asOf 时间。')
    return
  }
  const window = data.window
  if (!window || !isRfc3339(window.startsAt) || !isRfc3339(window.endsAt)
      || Date.parse(window.endsAt) <= Date.parse(window.startsAt)) {
    setContractError('响应时间窗不合法，无法展示统计范围。')
    return
  }

  const byCode = new Map()
  const duplicates = new Set()
  for (const item of data.items) {
    if (!item || typeof item.metricCode !== 'string') continue
    if (byCode.has(item.metricCode) || duplicates.has(item.metricCode)) {
      duplicates.add(item.metricCode)
      byCode.delete(item.metricCode)
    } else {
      byCode.set(item.metricCode, item)
    }
  }

  rows.value = (props.config.metrics || []).map(metric => {
    if (duplicates.has(metric.code)) {
      return contractRow(metric, 'metricCode 重复，不能确定唯一指标值')
    }
    return normalizeMetric(metric, byCode.get(metric.code))
  })
  asOf.value = data.asOf
  windowStart.value = window.startsAt
  windowEnd.value = window.endsAt
  pageState.value = 'ready'
}

function setContractError(message) {
  rows.value = []
  asOf.value = ''
  windowStart.value = ''
  windowEnd.value = ''
  pageState.value = 'contract-error'
  pageMessage.value = message
}

function contractRow(metric, message) {
  return {
    config: metric,
    status: STATUS_UNAVAILABLE,
    contract: true,
    message,
    value: null
  }
}

/**
 * 校验并规范化单个指标。
 *
 * OK 必须有非负整数 value；NO_DATA/UNAVAILABLE 必须严格 value=null；
 * unit 当前只接受 COUNT，source 必须与页面配置一致。任何违约只降级当前卡片。
 *
 * @param {object} metric 页面指标配置
 * @param {object|undefined} raw 服务端指标项
 * @returns {object} 卡片行
 */
function normalizeMetric(metric, raw) {
  if (!raw || typeof raw !== 'object') {
    return contractRow(metric, '接口未返回该指标')
  }
  if (![STATUS_OK, STATUS_NO_DATA, STATUS_UNAVAILABLE].includes(raw.status)) {
    return contractRow(metric, 'status 不在允许枚举内')
  }
  if (raw.status === STATUS_OK) {
    if (!isNonNegativeInteger(raw.value)) {
      return contractRow(metric, 'OK 状态必须有非负整数 value')
    }
  } else if (raw.value !== null) {
    return contractRow(metric, 'NO_DATA/UNAVAILABLE 状态不得有 value')
  }
  if (raw.unit !== 'COUNT') {
    return contractRow(metric, 'unit 必须是 COUNT')
  }
  if (typeof raw.source !== 'string' || raw.source !== metric.source) {
    return contractRow(metric, 'source 与页面配置不一致')
  }
  return {
    config: metric,
    status: raw.status,
    contract: false,
    message: '',
    value: raw.status === STATUS_OK ? raw.value : null
  }
}

/**
 * 处理页面级错误。403 清空旧数据并保留无权限提示；400 提示参数错误；
 * 网络/服务错误不显示 0 或旧缓存。401 由统一请求模块负责跳转登录页。
 *
 * @param {object} error 统一请求模块错误
 */
function handleError(error) {
  rows.value = []
  asOf.value = ''
  windowStart.value = ''
  windowEnd.value = ''
  if (error?.unauthorized) return
  if (error?.forbidden || Number(error?.code) === 403) {
    pageState.value = 'forbidden'
    pageMessage.value = error.message || '没有访问总览的权限。'
    return
  }
  if (Number(error?.code) === 400) {
    pageState.value = 'param-error'
    pageMessage.value = error.message || '查询参数不合法。'
    return
  }
  if (error?.kind === 'network' || error?.code === 'ERR_NETWORK' || error?.code === 'ECONNABORTED') {
    pageState.value = 'error'
    pageMessage.value = '网络异常，请检查连接后重试。'
    return
  }
  if (Number(error?.code) === 503 || Number(error?.status) === 503) {
    pageState.value = 'unavailable'
    pageMessage.value = error.message || '服务暂不可用，请稍后重试。'
    return
  }
  pageState.value = 'error'
  pageMessage.value = error.message || '总览读取失败，请稍后重试。'
}

function badgeClass(row) {
  if (row.contract) return 'badge-error'
  if (row.status === STATUS_OK) return 'badge-ok'
  if (row.status === STATUS_NO_DATA) return 'badge-empty'
  return 'badge-unavailable'
}

function badgeText(row) {
  if (row.contract) return '数据异常'
  if (row.status === STATUS_OK) return '正常'
  if (row.status === STATUS_NO_DATA) return '暂无数据'
  return '暂不可用'
}

function stateText(row) {
  if (row.contract) return '数据异常'
  if (row.status === STATUS_NO_DATA) return row.config.noDataText || '暂无数据'
  return '暂不可用'
}

function sourceLabel(source) {
  return SOURCE_LABEL[source] || source || '来源未知'
}

onMounted(() => {
  load()
})

onBeforeUnmount(() => {
  disposed = true
  invalidateRequest()
})
</script>

<template>
  <section class="overview-board">
    <header class="ov-head">
      <span class="ov-meta">
        <template v-if="pageState === 'ready'">
          响应时间：{{ formatTime(asOf) }}
        </template>
        <template v-else-if="loading">正在读取总览…</template>
        <template v-else>总览</template>
      </span>
      <button class="ov-btn" type="button" :disabled="loading" @click="refresh">刷新</button>
    </header>

    <form v-if="config.timeFilter || config.classFilter" class="ov-filters" @submit.prevent="applyFilters">
      <label v-if="config.timeFilter" class="ov-field">
        <span>开始时间（可选）</span>
        <input id="overview-from" v-model="draftFrom" type="datetime-local">
      </label>
      <label v-if="config.timeFilter" class="ov-field">
        <span>结束时间（可选）</span>
        <input id="overview-to" v-model="draftTo" type="datetime-local">
      </label>
      <label v-if="config.classFilter" class="ov-field">
        <span>教学班 classId（可选）</span>
        <input id="overview-class-id" v-model="draftClassId" inputmode="numeric" placeholder="留空 = 全部授权范围">
      </label>
      <div class="ov-filter-actions">
        <button class="ov-btn primary" type="submit">应用筛选</button>
        <button class="ov-btn" type="button" @click="resetFilters">恢复默认</button>
      </div>
      <p class="ov-hint">{{ windowHint }}</p>
    </form>
    <p v-if="filterError" class="ov-filter-error" role="alert">{{ filterError }}</p>

    <div v-if="pageState === 'loading'" class="ov-state" role="status">正在读取总览…</div>

    <div v-else-if="pageState === 'forbidden'" class="ov-state error" role="alert">
      <strong>无权查看该总览</strong>
      <span>{{ pageMessage }}</span>
    </div>

    <div v-else-if="pageState === 'param-error'" class="ov-state error" role="alert">
      <strong>筛选参数不合法</strong>
      <span>{{ pageMessage }}</span>
    </div>

    <div v-else-if="pageState === 'contract-error'" class="ov-state error" role="alert">
      <strong>总览响应异常</strong>
      <span>{{ pageMessage }}</span>
    </div>

    <div v-else-if="pageState === 'unavailable'" class="ov-state warn" role="alert">
      <strong>总览暂不可用</strong>
      <span>{{ pageMessage }}</span>
    </div>

    <div v-else-if="pageState === 'error'" class="ov-state error" role="alert">
      <strong>总览读取失败</strong>
      <span>{{ pageMessage }}</span>
    </div>

    <template v-else>
      <div class="ov-window">
        <span>统计窗口（服务端返回）：{{ formatTime(windowStart) }} — {{ formatTime(windowEnd) }}</span>
        <span class="ov-window-note">{{ windowHint }}</span>
      </div>
      <p class="ov-summary">
        共 <b>{{ rows.length }}</b> 项指标 · <b>{{ summary.ok }}</b> 正常 ·
        <b>{{ summary.noData }}</b> 暂无数据 · <b>{{ summary.unavailable }}</b> 暂不可用
        <span v-if="summary.contract > 0"> · <b>{{ summary.contract }}</b> 数据异常</span>
      </p>
      <section v-for="group in groupRows" :key="group.key" class="ov-group" :data-group="group.key">
        <header class="ov-group-head">
          <h3>{{ group.displayLabel }}</h3>
          <span>{{ group.rows.some(row => row.config.windowed) ? '按时间窗' : '当前快照' }}</span>
        </header>
        <div class="ov-grid">
          <article
            v-for="row in group.rows"
            :key="row.config.code"
            class="ov-card"
            :data-metric="row.config.code"
          >
            <header class="ov-card-head">
              <h4>{{ row.config.title }}</h4>
              <span class="ov-badge" :class="badgeClass(row)">{{ badgeText(row) }}</span>
            </header>
            <p v-if="row.status === STATUS_OK && !row.contract" class="ov-value">
              <strong>{{ row.value }}</strong><span>{{ row.config.unitLabel }}</span>
            </p>
            <p v-else class="ov-state-text" :class="{ error: row.contract }">{{ stateText(row) }}</p>
            <p class="ov-note">
              {{ row.config.note }}
              <span v-if="row.contract">（该指标响应异常，已降级）</span>
            </p>
            <footer class="ov-card-foot">
              <span>来源：{{ sourceLabel(row.config.source) }}</span>
              <span>{{ row.config.windowed ? '按时间窗' : '当前快照' }}</span>
            </footer>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.overview-board { min-width: 0; color: #303133; }
.ov-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.ov-meta { color: #606266; font-size: 13px; }
.ov-btn { padding: 6px 12px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; cursor: pointer; font: inherit; }
.ov-btn:hover { border-color: #409eff; color: #409eff; }
.ov-btn.primary { border-color: #409eff; background: #409eff; color: #fff; }
.ov-btn:disabled { border-color: #ebeef5; color: #c0c4cc; background: #f5f7fa; cursor: not-allowed; }
.ov-filters { display: flex; align-items: flex-end; gap: 10px; flex-wrap: wrap; padding: 12px; margin-bottom: 12px; border: 1px solid #ebeef5; border-radius: 8px; background: #fafafa; }
.ov-field { display: flex; flex-direction: column; gap: 5px; min-width: 160px; color: #606266; font-size: 12px; }
.ov-field input { box-sizing: border-box; width: 100%; padding: 6px 8px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #303133; font: inherit; }
.ov-filter-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.ov-hint { flex-basis: 100%; margin: 0; color: #909399; font-size: 12px; line-height: 1.5; }
.ov-filter-error { margin: 0 0 12px; padding: 8px 10px; border-radius: 4px; background: #fef0f0; color: #c45656; font-size: 13px; }
.ov-state { display: flex; flex-direction: column; gap: 6px; padding: 28px 20px; border-radius: 8px; background: #fafafa; color: #606266; text-align: center; }
.ov-state.error { background: #fef0f0; color: #c45656; }
.ov-state.warn { background: #fdf6ec; color: #b88230; }
.ov-window { display: flex; flex-direction: column; gap: 4px; min-width: 0; max-width: 100%; padding: 9px 12px; margin-bottom: 10px; border-radius: 6px; background: #f4f8ff; color: #606266; font-size: 12px; }
.ov-window > span { min-width: 0; overflow-wrap: anywhere; word-break: break-word; }
.ov-window-note { color: #909399; }
.ov-summary { margin: 0 0 14px; color: #606266; font-size: 13px; overflow-wrap: anywhere; }
.ov-group { margin-bottom: 18px; min-width: 0; }
.ov-group-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.ov-group-head h3 { margin: 0; font-size: 15px; color: #1f2d3d; }
.ov-group-head > span { color: #909399; font-size: 12px; }
.ov-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 12px; min-width: 0; }
.ov-card { display: flex; flex-direction: column; gap: 8px; min-width: 0; padding: 14px; border: 1px solid #ebeef5; border-radius: 8px; background: #fff; }
.ov-card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.ov-card-head h4 { margin: 0; font-size: 14px; color: #303133; overflow-wrap: anywhere; }
.ov-badge { flex: none; padding: 1px 7px; border-radius: 4px; font-size: 12px; }
.badge-ok { background: #f0f9eb; color: #529b2e; }
.badge-empty { background: #f4f4f5; color: #909399; }
.badge-unavailable { background: #fdf6ec; color: #b88230; }
.badge-error { background: #fef0f0; color: #c45656; }
.ov-value { display: flex; align-items: baseline; gap: 6px; margin: 0; }
.ov-value strong { font-size: 30px; line-height: 1.1; color: #1f2d3d; }
.ov-value span { color: #909399; font-size: 12px; }
.ov-state-text { margin: 0; color: #909399; font-size: 20px; line-height: 1.3; }
.ov-state-text.error { color: #c45656; }
.ov-note { margin: 0; color: #909399; font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
.ov-card-foot { display: flex; justify-content: space-between; gap: 8px; margin-top: auto; padding-top: 8px; border-top: 1px solid #f5f5f5; color: #909399; font-size: 12px; flex-wrap: wrap; }
@media (max-width: 560px) {
  .ov-field { flex: 1 1 100%; min-width: 0; }
  .ov-filter-actions { width: 100%; }
  .ov-filter-actions .ov-btn { flex: 1; }
  .ov-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
