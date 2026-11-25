<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Coin, Monitor, Refresh, Timer } from '@element-plus/icons-vue'
import { api } from '@/api/education/client.js'
import { canOpen } from '@/education/session.js'

const sections = [
  {
    key: 'server', title: '应用服务器', icon: Monitor,
    permission: 'monitor:server:list', serviceRoute: 'monitor/server',
    metrics: [
      { key: 'cpuUsage', label: 'CPU 使用率', percent: true },
      { key: 'memoryUsage', label: '系统内存使用率', percent: true },
      { key: 'jvmUsage', label: 'JVM 内存使用率', percent: true },
      { key: 'diskUsage', label: '最高磁盘使用率', percent: true },
      { key: 'coreCount', label: '逻辑处理器' },
      { key: 'uptime', label: '应用运行时长' }
    ]
  },
  {
    key: 'cache', title: 'Redis 缓存', icon: Coin,
    permission: 'monitor:cache:list', serviceRoute: 'monitor/cache',
    metrics: [
      { key: 'memory', label: '已用内存' },
      { key: 'clients', label: '连接客户端' },
      { key: 'keyCount', label: '当前库键数量' },
      { key: 'uptimeDays', label: '运行天数' }
    ]
  },
  {
    key: 'jobs', title: '定时任务', icon: Timer,
    permission: 'monitor:job:list', serviceRoute: 'monitor/job',
    metrics: [
      { key: 'total', label: '已配置任务' },
      { key: 'active', label: '启用任务' }
    ]
  }
]
const visibleSections = computed(() => sections.filter(({ permission, serviceRoute }) =>
  canOpen({ roles: ['admin'], permission, serviceRoute })
))
const overview = ref(null)
const loading = ref(false)
const requestFailed = ref(false)
const lastFetched = ref(null)
const numberFormat = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1 })
const timeFormat = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium' })
const controller = new AbortController()

function isPercent(value) {
  return Number.isFinite(value) && value >= 0 && value <= 100
}

function formatMetric(value, metric) {
  if (metric.percent) return isPercent(value) ? numberFormat.format(value) + '%' : '—'
  if (Number.isFinite(value)) return numberFormat.format(value)
  return typeof value === 'string' && value.trim() ? value : '—'
}

function sectionStatus(key) {
  if (loading.value) return '正在采集…'
  return overview.value?.[key] === null ? '本次采集失败，可刷新重试。' : '暂未获取监控数据'
}

async function load() {
  if (loading.value || !visibleSections.value.length) return
  loading.value = true
  requestFailed.value = false
  try {
    const { data } = await api.get('/monitor/operations', { signal: controller.signal, silent403: true })
    if (controller.signal.aborted) return
    if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error('监控数据格式异常')
    overview.value = data
    lastFetched.value = new Date()
  } catch (error) {
    if (!controller.signal.aborted && !error.cancelled) requestFailed.value = true
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}

onMounted(load)
onUnmounted(() => controller.abort())
</script>

<template>
  <div class="operations">
    <header class="operations-heading">
      <div>
        <h1>运行监控</h1>
        <p>平台级运行指标</p>
      </div>
      <div v-if="visibleSections.length" class="operations-controls">
        <time v-if="lastFetched" :datetime="lastFetched.toISOString()">最近获取 {{ timeFormat.format(lastFetched) }}</time>
        <button type="button" class="ui-btn" :disabled="loading" @click="load">
          <Refresh class="operations-icon" aria-hidden="true" />
          {{ loading ? '正在刷新' : '刷新' }}
        </button>
      </div>
    </header>

    <p v-if="!visibleSections.length" class="panel operations-state" role="status">当前账号暂无运行监控权限。</p>
    <p v-if="requestFailed" class="panel operations-state request-error" role="alert">
      {{ overview ? '刷新失败，仍显示上次获取的监控数据。' : '获取监控数据失败，请刷新重试。' }}
    </p>

    <div v-if="visibleSections.length" class="operations-grid" :aria-busy="loading">
      <section
        v-for="section in visibleSections" :key="section.key"
        class="panel operations-card" :class="{ 'operations-card-wide': section.key === 'server' }"
        :aria-labelledby="'operations-' + section.key"
      >
        <h2 :id="'operations-' + section.key">
          <span class="operations-symbol"><component :is="section.icon" aria-hidden="true" /></span>
          {{ section.title }}
        </h2>
        <dl v-if="overview?.[section.key]" class="operations-metrics">
          <div v-for="metric in section.metrics" :key="metric.key" class="operations-metric">
            <dt>{{ metric.label }}</dt>
            <dd>
              {{ formatMetric(overview[section.key][metric.key], metric) }}
              <meter
                v-if="metric.percent && isPercent(overview[section.key][metric.key])"
                min="0" max="100" :value="overview[section.key][metric.key]" :aria-label="metric.label"
              />
            </dd>
          </div>
        </dl>
        <p v-else class="operations-card-state">
          {{ sectionStatus(section.key) }}
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.operations {
  --ink: #162841;
  --muted: #536780;
  --border: #dce5ef;
  --accent: #25608f;
  color: var(--ink);
  display: grid;
  gap: 18px;
  width: min(100%, 1232px);
  margin: 0 auto;
}

.operations-heading, .operations-controls, .operations-controls .ui-btn, .operations-card h2 {
  display: flex;
  align-items: center;
  gap: 10px;
}

.operations-heading {
  justify-content: space-between;
  flex-wrap: wrap;
}

.operations-heading h1 {
  margin: 0;
  font-size: clamp(23px, 3vw, 30px);
  line-height: 1.35;
}

.operations-heading p, .operations-controls time {
  color: var(--muted);
  font-size: 13px;
}

.operations-heading p { margin: 5px 0 0; }
.operations-controls { flex-wrap: wrap; }
.operations-icon { width: 16px; height: 16px; }

.operations-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 14px;
}

.operations-card, .operations-state {
  margin: 0;
  padding: 17px;
  border: 1px solid var(--border);
  border-radius: 12px;
}

.operations-card { min-width: 0; }
.operations-card-wide { grid-column: 1 / -1; }

.operations-card h2 {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 650;
}

.operations-symbol {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: none;
  color: var(--accent);
  background: #eaf3fb;
  border-radius: 8px;
}

.operations-symbol svg { width: 19px; height: 19px; }

.operations-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.operations-card-wide .operations-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }

.operations-metric {
  min-width: 0;
  padding: 12px;
  border-radius: 8px;
  background: #f4f7fb;
}

.operations-metric dt, .operations-card-state {
  color: var(--muted);
  font-size: 13px;
}

.operations-metric dd {
  margin: 4px 0 0;
  font-size: 20px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.operations-metric meter {
  width: 100%;
  height: 8px;
  margin: 7px 0 2px;
  display: block;
  appearance: none;
  border: 0;
  border-radius: 4px;
  background: #dbe5f1;
}

.operations-metric meter::-webkit-meter-bar { background: #dbe5f1; border: 0; border-radius: 4px; }
.operations-metric meter::-webkit-meter-optimum-value { background: var(--accent); border-radius: 4px; }
.operations-metric meter::-moz-meter-bar { background: var(--accent); border-radius: 4px; }
.operations-card-state { margin: 0; }

.request-error {
  color: #894717;
  background: #fff8ee;
  border-color: #ead2ad;
}

@media (max-width: 640px) {
  .operations-grid { grid-template-columns: minmax(0, 1fr); }
  .operations-card-wide .operations-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
