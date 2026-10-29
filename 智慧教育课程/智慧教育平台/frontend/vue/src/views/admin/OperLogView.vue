<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import AppModal from '../../components/AppModal.vue'

const PAGE_SIZE = 10
const BUSINESS_TYPES = {
  0: '其它', 1: '新增', 2: '修改', 3: '删除', 4: '授权',
  5: '导出', 6: '导入', 7: '强退', 8: '生成代码', 9: '清空数据'
}

const rows = ref([])
const total = ref(0)
const page = ref(1)
const filters = reactive({ title: '', operName: '', status: '', begin: '', end: '' })
const loading = ref(false)
const error = ref('')
const detail = reactive({ open: false, row: null })
let disposed = false

const pages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

function typeLabel(type) {
  return BUSINESS_TYPES[type] ?? `类型 ${type}`
}

async function load(target = page.value) {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ pageNum: target, pageSize: PAGE_SIZE })
    if (filters.title) params.set('title', filters.title)
    if (filters.operName) params.set('operName', filters.operName)
    if (filters.status !== '') params.set('status', filters.status)
    if (filters.begin) params.set('params[beginTime]', `${filters.begin} 00:00:00`)
    if (filters.end) params.set('params[endTime]', `${filters.end} 23:59:59`)
    const data = await api.get(`/monitor/operlog/list?${params}`)
    if (disposed) return
    rows.value = data.rows || []
    total.value = Number(data.total || 0)
    page.value = target
  } catch (err) {
    if (!disposed && !err.forbidden && !err.unauthorized) error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  if (filters.begin && filters.end && filters.begin > filters.end) {
    error.value = '开始日期不能晚于结束日期'
    return
  }
  load(1)
}

function resetFilters() {
  filters.title = ''
  filters.operName = ''
  filters.status = ''
  filters.begin = ''
  filters.end = ''
  load(1)
}

function showDetail(row) {
  detail.row = row
  detail.open = true
}

onMounted(() => load(1))
onUnmounted(() => { disposed = true })
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <input v-model="filters.title" class="ui-input" placeholder="按系统模块搜索" @keyup.enter="search" />
      <input v-model="filters.operName" class="ui-input" placeholder="按操作人员搜索" @keyup.enter="search" />
      <select v-model="filters.status" class="ui-select">
        <option value="">全部状态</option>
        <option value="0">成功</option>
        <option value="1">失败</option>
      </select>
      <input v-model="filters.begin" class="ui-input" type="date" aria-label="开始日期" />
      <span class="hint">至</span>
      <input v-model="filters.end" class="ui-input" type="date" aria-label="结束日期" />
      <button class="ui-btn primary" type="button" @click="search">查询</button>
      <button class="ui-btn" type="button" @click="resetFilters">重置</button>
    </div>

    <div class="table-wrap">
      <table class="ui-table">
        <thead>
          <tr><th>系统模块</th><th>操作类型</th><th>请求方式</th><th>操作人</th><th>状态</th><th>操作时间</th><th>耗时</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.operId">
            <td>{{ row.title || '—' }}</td>
            <td>{{ typeLabel(row.businessType) }}</td>
            <td>{{ row.requestMethod || '—' }}</td>
            <td>{{ row.operName || '—' }}</td>
            <td>
              <span class="tag" :class="String(row.status) === '0' ? 'ok' : 'fail'">
                {{ String(row.status) === '0' ? '成功' : '失败' }}
              </span>
            </td>
            <td>{{ row.operTime || '—' }}</td>
            <td>{{ row.costTime === null || row.costTime === undefined ? '—' : row.costTime + ' ms' }}</td>
            <td class="actions">
              <button class="ui-btn link" type="button" @click="showDetail(row)">详情</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="loading" class="state">加载中…</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="!rows.length" class="state">暂无数据</div>
    </div>

    <div class="pager">
      <span>第 {{ page }} / {{ pages }} 页 · 共 {{ total }} 条</span>
      <span>
        <button class="ui-btn" type="button" :disabled="page <= 1" @click="load(page - 1)">上一页</button>
        <button class="ui-btn" type="button" :disabled="page >= pages" @click="load(page + 1)">下一页</button>
      </span>
    </div>

    <AppModal
      v-if="detail.open"
      title="操作日志详情"
      wide
      ok-text="关闭"
      hide-cancel
      @close="detail.open = false"
      @confirm="detail.open = false"
    >
      <dl class="detail-grid">
        <dt>系统模块</dt><dd>{{ detail.row.title || '—' }}</dd>
        <dt>操作类型</dt><dd>{{ typeLabel(detail.row.businessType) }}</dd>
        <dt>请求方式</dt><dd>{{ detail.row.requestMethod || '—' }}</dd>
        <dt>请求地址</dt><dd>{{ detail.row.operUrl || '—' }}</dd>
        <dt>操作方法</dt><dd>{{ detail.row.method || '—' }}</dd>
        <dt>操作人员</dt><dd>{{ detail.row.operName || '—' }}</dd>
        <dt>所属部门</dt><dd>{{ detail.row.deptName || '—' }}</dd>
        <dt>主机</dt><dd>{{ detail.row.operIp || '—' }}</dd>
        <dt>操作地点</dt><dd>{{ detail.row.operLocation || '—' }}</dd>
        <dt>操作时间</dt><dd>{{ detail.row.operTime || '—' }}</dd>
        <dt>消耗时间</dt><dd>{{ detail.row.costTime ?? '—' }} ms</dd>
        <dt>状态</dt><dd>{{ String(detail.row.status) === '0' ? '成功' : '失败' }}</dd>
      </dl>

      <div v-if="detail.row.errorMsg" class="detail-block">
        <label>错误信息</label>
        <pre class="code-block">{{ detail.row.errorMsg }}</pre>
      </div>
      <div class="detail-block">
        <label>请求参数</label>
        <pre class="code-block">{{ detail.row.operParam || '—' }}</pre>
      </div>
      <div class="detail-block">
        <label>返回结果</label>
        <pre class="code-block">{{ detail.row.jsonResult || '—' }}</pre>
      </div>
    </AppModal>
  </div>
</template>
