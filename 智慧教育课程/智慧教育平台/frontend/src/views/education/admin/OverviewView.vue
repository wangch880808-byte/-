<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ArrowRight, Avatar, Calendar, CircleCheck, Collection, OfficeBuilding, Plus, Upload, User } from '@element-plus/icons-vue'
import { api } from '@/api/education/client.js'
import { eduErrorText } from '@/api/education/http.js'
import { canOpen, hasPermission } from '@/education/session.js'
import { pages } from '@/education/ui-pages.js'

const overview = ref(null)
const route = useRoute()
const schoolId = ref('')
const loading = ref(true)
const error = ref('')
let request = null

const numberFormat = new Intl.NumberFormat('zh-CN')
const metricDefinitions = [
  { key: 'studentCount', label: '学生', path: 'students', icon: User, color: 'blue' },
  { key: 'teacherCount', label: '教师', path: 'teachers', icon: Avatar, color: 'violet' },
  { key: 'collegeCount', label: '学院', path: 'depts', icon: OfficeBuilding, color: 'teal' },
  { key: 'administrativeClassCount', label: '行政班', path: 'classes', icon: Collection, color: 'amber' }
]
const taskDefinitions = [
  { key: 'unassignedStudentCount', label: '未分班学生', path: 'students', icon: User },
  { key: 'unassignedTeacherCount', label: '未归属学院的教师', path: 'teachers', icon: Avatar }
]

const schools = computed(() => overview.value?.schools || [])
const hasSeveralSchools = computed(() => schools.value.length > 1)
const allSchoolsSelected = computed(() => hasSeveralSchools.value && !schoolId.value)
const term = computed(() => overview.value?.currentTerm)
const showTerm = computed(() => term.value !== undefined
  || (allSchoolsSelected.value && hasPermission('education:term:list')))

function target(path, query = {}) {
  const page = pages.find(item => item.path === path && item.roles.includes('admin'))
  if (!canOpen(page)) return null
  return { path: '/admin/' + path, query }
}

function scopedTarget(path, query = {}) {
  return target(path, { ...query, ...(schoolId.value ? { schoolId: schoolId.value } : {}) })
}

function withCounts(definitions, query) {
  const totals = overview.value?.totals || {}
  return definitions.flatMap(definition => {
    const count = totals[definition.key]
    if (count == null || !Number.isSafeInteger(Number(count)) || Number(count) < 0) return []
    const to = definition.path === 'depts' ? target('depts') : scopedTarget(definition.path, query)
    return [{ ...definition, count: Number(count), to }]
  })
}

const metrics = computed(() => withCounts(metricDefinitions))
const tasks = computed(() => withCounts(taskDefinitions, { unassigned: 'true' }))
const outstanding = computed(() => tasks.value.filter(item => item.count > 0))
const termTarget = computed(() => term.value === undefined ? target('terms') : scopedTarget('terms'))
const canSetTerm = computed(() => termTarget.value &&
  (hasPermission('education:term:current') || hasPermission('education:term:add')))
const actions = computed(() => [
  { label: '新增用户', icon: Plus, to: hasPermission('system:user:add') && target('users', { action: 'add' }) },
  { label: '导入组织', icon: Upload, to: hasPermission('system:dept:add') && hasPermission('system:dept:list') && target('depts', { action: 'import' }) },
  { label: '管理学期', icon: Calendar, to: termTarget.value }
].filter(action => action.to))

async function load() {
  request?.abort()
  const controller = new AbortController()
  request = controller
  loading.value = true
  error.value = ''
  try {
    const response = await api.get('/education/overview', {
      params: { schoolId: schoolId.value || undefined },
      signal: controller.signal,
      silent403: true
    })
    if (controller.signal.aborted) return
    const data = response.data
    if (!Array.isArray(data?.schools)) throw new Error('管理数据响应不完整')
    overview.value = data
    schoolId.value = String(data.schoolId ?? '')
  } catch (cause) {
    if (!controller.signal.aborted) error.value = eduErrorText(cause) || '暂时无法读取管理数据'
  } finally {
    if (request === controller) loading.value = false
  }
}

onMounted(load)
onUnmounted(() => request?.abort())
watch(() => [route.hash, loading.value], ([hash, pending]) => {
  if (!pending && hash === '#overview-tasks') {
    document.getElementById('overview-tasks')?.scrollIntoView({ block: 'nearest' })
  }
}, { flush: 'post' })
</script>

<template>
  <div class="admin-overview" :aria-busy="loading">
    <header class="overview-heading">
      <h1>管理总览</h1>
      <label v-if="hasSeveralSchools" class="school-filter">
        学校
        <select v-model="schoolId" class="ui-select" @change="load">
          <option value="">全部可管理学校</option>
          <option v-for="school in schools" :key="school.deptId" :value="String(school.deptId)">
            {{ school.deptName }}
          </option>
        </select>
      </label>
      <span v-else-if="schools.length === 1" class="school-name">
        <OfficeBuilding aria-hidden="true" />
        {{ schools[0].deptName }}
      </span>
    </header>

    <div v-if="loading" class="overview-state" role="status">正在读取管理数据…</div>
    <div v-else-if="error" class="overview-state" role="alert">
      <span>{{ error }}</span>
      <button type="button" class="ui-btn" @click="load">重试</button>
    </div>
    <template v-else>
      <section v-if="showTerm" class="term-band" aria-labelledby="overview-term">
        <div class="term-symbol"><Calendar aria-hidden="true" /></div>
        <div class="term-copy">
          <h2 id="overview-term">当前学期</h2>
          <p class="term-name">
            {{ allSchoolsSelected ? '选择学校查看当前学期' : (term?.name || '尚未设置当前学期') }}
          </p>
          <p v-if="term?.startDate && term?.endDate && !allSchoolsSelected" class="term-dates">
            {{ term.startDate }} 至 {{ term.endDate }}
          </p>
        </div>
        <RouterLink v-if="!term && !allSchoolsSelected && canSetTerm" :to="termTarget" class="term-link">
          去设置 <ArrowRight aria-hidden="true" />
        </RouterLink>
      </section>

      <section v-if="metrics.length" class="metrics" aria-label="数据概况">
        <component
          :is="item.to ? RouterLink : 'div'"
          v-for="item in metrics"
          :key="item.key"
          :to="item.to || undefined"
          class="overview-card metric-card"
          :class="{ 'metric-link': item.to }"
        >
          <span class="metric-icon" :class="item.color"><component :is="item.icon" aria-hidden="true" /></span>
          <span class="metric-label">{{ item.label }}</span>
          <strong class="metric-count">{{ numberFormat.format(item.count) }}</strong>
          <ArrowRight v-if="item.to" class="metric-arrow" aria-hidden="true" />
        </component>
      </section>
      <p v-if="!showTerm && !metrics.length && !actions.length" class="overview-state">暂无可查看的管理数据</p>
    </template>

    <div v-if="(!loading && !error && tasks.length) || actions.length" class="overview-columns">
      <section v-if="!loading && !error && tasks.length" class="overview-card overview-panel" aria-labelledby="overview-tasks">
        <h2 id="overview-tasks">待处理事项</h2>
        <p v-if="!outstanding.length" class="tasks-empty"><CircleCheck aria-hidden="true" />当前没有组织归属待处理事项</p>
        <ul v-else class="overview-list">
          <li v-for="item in outstanding" :key="item.key">
            <component :is="item.to ? RouterLink : 'div'" :to="item.to || undefined" class="overview-row task-row">
              <component :is="item.icon" class="row-icon" aria-hidden="true" />
              <span class="row-label">{{ item.label }}</span>
              <span class="task-count">{{ numberFormat.format(item.count) }} 人</span>
              <ArrowRight v-if="item.to" class="row-arrow" aria-hidden="true" />
            </component>
          </li>
        </ul>
      </section>

      <section v-if="actions.length" class="overview-card overview-panel" aria-labelledby="overview-actions">
        <h2 id="overview-actions">快捷操作</h2>
        <ul class="overview-list">
          <li v-for="action in actions" :key="action.label">
            <RouterLink :to="action.to" class="overview-row action-row">
              <component :is="action.icon" class="row-icon" aria-hidden="true" />
              <span class="row-label">{{ action.label }}</span>
              <ArrowRight class="row-arrow" aria-hidden="true" />
            </RouterLink>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-overview {
  --ink: #162841;
  --muted-text: #536780;
  --surface-border: #dce5ef;
  color: var(--ink);
  display: grid;
  gap: 18px;
  width: min(100%, 1232px);
  margin: 0 auto;
}

.overview-heading, .term-band, .school-filter, .school-name, .term-link, .overview-row, .tasks-empty, .overview-state {
  display: flex;
  align-items: center;
}

.overview-heading {
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px 18px;
}

.overview-heading h1 {
  margin: 0;
  font-size: clamp(23px, 3vw, 30px);
  line-height: 1.35;
  color: var(--ink);
}

.school-filter, .school-name {
  gap: 8px;
  color: var(--muted-text);
  font-weight: 500;
}

.school-filter select { max-width: min(240px, 65vw); }
.school-name svg, .term-link svg, .row-arrow, .metric-arrow { width: 16px; height: 16px; flex: none; }

.term-band {
  gap: 16px;
  padding: 22px 24px;
  border-radius: 12px;
  background: linear-gradient(105deg, #153455, #1c5375);
  color: #fff;
}

.term-symbol, .metric-icon {
  display: grid;
  place-items: center;
  flex: none;
  border-radius: 10px;
}

.term-symbol {
  width: 48px;
  height: 48px;
  background: #ffffff21;
}

.term-symbol svg { width: 24px; height: 24px; }
.term-copy h2 { color: #c9e3f7; font-size: 13px; font-weight: 500; margin: 0 0 4px; }
.term-name { font-size: clamp(17px, 2vw, 22px); font-weight: 650; line-height: 1.4; margin: 0; }
.term-dates { color: #dcefff; font-size: 13px; margin: 6px 0 0; }

.term-link {
  margin-left: auto;
  gap: 4px;
  padding: 7px 10px;
  border: 1px solid #ffffff61;
  border-radius: 6px;
  white-space: nowrap;
  color: #fff;
}

.term-link:hover { background: #ffffff21; }

.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.overview-card {
  background: #fff;
  border: 1px solid var(--surface-border);
  border-radius: 12px;
  min-width: 0;
}

.metric-card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  grid-template-areas: 'icon label arrow' 'icon count arrow';
  align-items: center;
  column-gap: 12px;
  row-gap: 3px;
  color: var(--ink);
  padding: 15px 16px;
}

.metric-link:hover { border-color: #87b8e1; background: #fafdff; }

.metric-icon {
  grid-area: icon;
  width: 41px;
  height: 41px;
}

.metric-icon svg { width: 21px; height: 21px; }
.metric-icon.blue { color: #1260ac; background: #e9f3ff; }
.metric-icon.violet { color: #6541ad; background: #f1ebff; }
.metric-icon.teal { color: #0e7466; background: #e5f5f1; }
.metric-icon.amber { color: #935711; background: #fff4e3; }
.metric-label { grid-area: label; color: var(--muted-text); }
.metric-count { grid-area: count; font-size: clamp(20px, 2.8vw, 28px); line-height: 1.1; font-variant-numeric: tabular-nums; }
.metric-arrow { grid-area: arrow; color: #537a9c; }

.overview-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 14px;
}

.overview-columns > :only-child { grid-column: 1 / -1; }
.overview-panel { padding: 16px 18px; }
.overview-panel h2 { margin: 0 0 8px; font-size: 16px; color: var(--ink); }
.overview-list { list-style: none; margin: 0; padding: 0; }
.overview-list li + li { border-top: 1px solid #edf1f5; }

.overview-row {
  min-height: 44px;
  gap: 9px;
  border-radius: 6px;
  color: var(--ink);
  padding: 4px 6px;
}

a.overview-row:hover { background: #f0f7ff; color: #155a9a; }
.row-icon { width: 17px; height: 17px; flex: none; color: #426e93; }
.row-label { flex: 1; }
.row-arrow { color: #537a9c; }

.task-count {
  flex: none;
  font-size: 12px;
  font-weight: 600;
  color: #8b470e;
  background: #fff2da;
  border-radius: 12px;
  padding: 3px 8px;
}

.tasks-empty { gap: 8px; color: var(--muted-text); margin: 14px 0 6px; }
.tasks-empty svg { width: 18px; height: 18px; flex: none; color: #218171; }

.overview-state {
  gap: 12px;
  color: var(--muted-text);
  background: #fff;
  border: 1px solid var(--surface-border);
  border-radius: 10px;
  margin: 0;
  padding: 16px;
}

.admin-overview :is(a, button, select):focus-visible { outline: 2px solid #1d75bb; outline-offset: 3px; }
.term-link:focus-visible { outline-color: #fff; }

@media (max-width: 1050px) {
  .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 640px) {
  .admin-overview { gap: 12px; }
  .overview-columns { grid-template-columns: 1fr; gap: 12px; }
  .metrics { gap: 10px; }
  .term-band { padding: 15px 12px; gap: 10px; }
  .term-symbol { width: 34px; height: 34px; }
  .term-symbol svg { width: 19px; height: 19px; }
  .metric-card { padding: 11px 9px; column-gap: 7px; }
  .metric-icon { width: 31px; height: 31px; }
  .metric-icon svg { width: 17px; height: 17px; }
}
</style>
