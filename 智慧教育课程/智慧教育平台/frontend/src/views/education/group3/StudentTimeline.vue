<script setup name="StudentTimeline">
import { computed } from 'vue'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  selectedKey: { type: String, default: '' },
  serverNow: { type: String, default: '' },
  mode: { type: String, default: 'materials' },
  emptyHint: { type: String, default: '教师发布内容后，环节会出现在这条时间轴上' }
})
const emit = defineEmits(['select'])

const KIND_META = {
  MATERIAL: { label: '资料阅读', tone: 'blue', icon: '▤' },
  QUIZ: { label: '预习习题', tone: 'teal', icon: '✎' },
  PLAN: { label: '备课包', tone: 'amber', icon: '◈' },
  DRAFT: { label: '待编排资料', tone: 'slate', icon: '◌' }
}

const STATE_LABELS = {
  DRAFT: '待编排',
  FAILED: '处理失败',
  UPCOMING: '未开放',
  OPEN: '进行中',
  CLOSED: '已截止',
  ARCHIVED: '已归档',
  PRIVATE: '仅教师可见'
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatClock(value) {
  if (!value) return '--:--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--:--'
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function dayKeyOf(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function dayLabelOf(value, serverNow) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '未安排时间'
  const base = serverNow ? new Date(serverNow) : new Date()
  const startOfDay = item => new Date(item.getFullYear(), item.getMonth(), item.getDate()).getTime()
  const diffDays = Math.round((startOfDay(date) - startOfDay(base)) / 86400000)
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '明天'
  if (diffDays === -1) return '昨天'
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()]
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日 · ${week}`
}

const groups = computed(() => {
  const dated = []
  const undated = []
  props.nodes.forEach(node => {
    if (node.time && dayKeyOf(node.time)) dated.push(node)
    else undated.push(node)
  })
  dated.sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime())
  const index = new Map()
  const result = []
  dated.forEach(node => {
    const key = dayKeyOf(node.time)
    if (!index.has(key)) {
      index.set(key, result.length)
      result.push({ key, label: dayLabelOf(node.time, props.serverNow), nodes: [] })
    }
    result[index.get(key)].nodes.push(node)
  })
  const todayKey = dayKeyOf(props.serverNow || new Date())
  result.forEach(group => {
    const numbered = group.nodes.filter(node => node.seq).length
    group.countText = numbered
      ? `本堂课 ${numbered} 个环节 · 1–${numbered} 号`
      : `${group.nodes.length} 个条目`
    group.isToday = group.key === todayKey
  })
  if (undated.length) {
    result.unshift({
      key: 'unscheduled',
      label: '待编排',
      nodes: undated,
      countText: `${undated.length} 个条目`,
      isToday: false
    })
  }
  return result
})

function metaOf(kind) {
  return KIND_META[kind] || KIND_META.MATERIAL
}

function labelOf(kind) {
  return metaOf(kind).label
}

function toneOf(kind) {
  return metaOf(kind).tone
}

function iconOf(kind) {
  return metaOf(kind).icon
}

function stateLabel(state) {
  return STATE_LABELS[state] || state || '未知'
}

function stateTagType(state) {
  if (state === 'OPEN') return 'success'
  if (state === 'UPCOMING' || state === 'DRAFT') return 'warning'
  return 'info'
}

function seqText(node) {
  return node.seq ? `第 ${node.seq} / ${node.total} 环节` : '不计入序号'
}

function enterLabel(node) {
  if (node.kind === 'QUIZ') return node.meta && node.meta.submitted ? '查看作答' : '进入作答'
  return props.mode === 'prestudy' ? '查看环节' : '进入阅读'
}
</script>

<template>
  <div class="g3-timeline">
    <section v-for="group in groups" :key="group.key" class="g3-tl-day">
      <div class="g3-tl-day-head">
        <span class="g3-tl-day-dot"></span>
        <strong>{{ group.label }}</strong>
        <span class="g3-tl-day-count">{{ group.countText }}</span>
        <span v-if="group.isToday" class="g3-tl-now">现在</span>
      </div>
      <div class="g3-tl-list">
        <div v-for="node in group.nodes" :key="node.key" class="g3-tl-item">
          <div class="g3-tl-time">
            <strong>{{ formatClock(node.time) }}</strong>
            <span>{{ node.closesAt ? `至 ${formatClock(node.closesAt)}` : '开放' }}</span>
          </div>
          <span class="g3-tl-dot" :class="toneOf(node.kind)"></span>
          <div
            class="g3-tl-card"
            :class="[toneOf(node.kind), { active: node.key === selectedKey }]"
            role="button"
            tabindex="0"
            @click="emit('select', node)"
            @keyup.enter="emit('select', node)"
          >
            <div class="g3-tl-card-head">
              <span class="g3-tl-kind" :class="toneOf(node.kind)">
                <i>{{ iconOf(node.kind) }}</i>{{ labelOf(node.kind) }}
              </span>
              <span class="g3-tl-seq" :class="{ muted: !node.seq }">{{ seqText(node) }}</span>
              <el-tag size="small" :type="stateTagType(node.state)" effect="light">
                {{ stateLabel(node.state) }}
              </el-tag>
            </div>
            <div class="g3-tl-title">{{ node.title }}</div>
            <div v-if="node.subtitle" class="g3-tl-subtitle">{{ node.subtitle }}</div>
            <div v-if="node.tags && node.tags.length" class="g3-tl-tags">
              <span v-for="(tag, index) in node.tags" :key="index" class="g3-tl-tag">{{ tag }}</span>
            </div>
            <div class="g3-tl-actions">
              <el-button size="small" type="primary" @click.stop="emit('select', node)">{{ enterLabel(node) }}</el-button>
            </div>
          </div>
        </div>
      </div>
    </section>
    <el-empty v-if="!groups.length" :description="emptyHint" :image-size="90" />
  </div>
</template>

<style scoped>
.g3-timeline { display: flex; flex-direction: column; gap: 14px; }
.g3-tl-day-head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.g3-tl-day-dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; }
.g3-tl-day-head strong { font-size: 14px; color: #303133; }
.g3-tl-day-count { font-size: 12px; color: #909399; }
.g3-tl-now { font-size: 12px; color: #409eff; border: 1px solid #b3d8ff; border-radius: 10px; padding: 0 8px; }
.g3-tl-list { display: flex; flex-direction: column; gap: 10px; }
.g3-tl-item { display: flex; align-items: flex-start; gap: 10px; }
.g3-tl-time { width: 52px; flex: none; text-align: right; display: flex; flex-direction: column; gap: 2px; }
.g3-tl-time strong { font-size: 13px; color: #303133; }
.g3-tl-time span { font-size: 11px; color: #a8abb2; }
.g3-tl-dot { width: 9px; height: 9px; border-radius: 50%; margin-top: 8px; flex: none; background: #c0c4cc; }
.g3-tl-dot.blue { background: #409eff; }
.g3-tl-dot.teal { background: #14b8a6; }
.g3-tl-card {
  flex: 1;
  min-width: 0;
  border: 1px solid #ebeef5;
  border-left: 3px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  padding: 10px 12px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.g3-tl-card:hover { box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08); }
.g3-tl-card.blue { border-left-color: #409eff; }
.g3-tl-card.teal { border-left-color: #14b8a6; }
.g3-tl-card.amber { border-left-color: #e6a23c; }
.g3-tl-card.slate { border-left-color: #909399; }
.g3-tl-card.active { border-color: #409eff; box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.12); }
.g3-tl-card-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.g3-tl-kind { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; font-weight: 600; }
.g3-tl-kind.blue { color: #409eff; }
.g3-tl-kind.teal { color: #14b8a6; }
.g3-tl-kind.amber { color: #e6a23c; }
.g3-tl-kind.slate { color: #909399; }
.g3-tl-seq { font-size: 12px; color: #606266; }
.g3-tl-seq.muted { color: #c0c4cc; }
.g3-tl-title { margin-top: 6px; font-size: 14px; font-weight: 600; color: #303133; }
.g3-tl-subtitle { margin-top: 2px; font-size: 12px; color: #909399; }
.g3-tl-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.g3-tl-tag { font-size: 12px; color: #606266; background: #f4f4f5; border-radius: 4px; padding: 1px 6px; }
.g3-tl-actions { margin-top: 8px; }
</style>
