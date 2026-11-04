<template>
  <div class="app-container">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb16" />
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column v-for="col in columns" :key="col.prop" :label="col.label" :min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ cell(row, col.prop) }}</template>
      </el-table-column>
    </el-table>
    <pagination v-if="paged" :total="total" :page="query.pageNum" :limit="query.pageSize"
      @update:page="query.pageNum = $event" @update:limit="query.pageSize = $event" @pagination="load" />
  </div>
</template>
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'
import { eduErrorText } from '@/utils/request'

const route = useRoute()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ pageNum: 1, pageSize: 10 })
const spec = computed(() => route.meta.list || { url: '', columns: [], shape: 'rows' })
const columns = computed(() => spec.value.columns || [])
const paged = computed(() => spec.value.shape !== 'tree')

function cell(row, prop) {
  const value = prop.split('.').reduce((current, key) => current == null ? current : current[key], row)
  if (prop === 'status' && (value === '0' || value === '1' || value === 0 || value === 1)) return String(value) === '0' ? '正常' : '停用'
  if (value && typeof value === 'object') return ''
  return value === undefined || value === null || value === '' ? '—' : value
}

function flatten(nodes, acc = []) {
  for (const node of nodes || []) {
    acc.push(node)
    flatten(node.children, acc)
  }
  return acc
}

async function load() {
  if (!spec.value.url) return
  loading.value = true
  error.value = ''
  try {
    const params = paged.value ? { pageNum: query.pageNum, pageSize: query.pageSize } : undefined
    const body = await request({ url: spec.value.url, params })
    if (spec.value.shape === 'tree') {
      const data = Array.isArray(body.data) ? body.data : []
      rows.value = flatten(data)
      total.value = rows.value.length
    } else {
      rows.value = Array.isArray(body.rows) ? body.rows : []
      total.value = Number(body.total) || 0
    }
  } catch (err) {
    rows.value = []
    total.value = 0
    error.value = eduErrorText(err)
  } finally {
    loading.value = false
  }
}

watch(() => route.path, () => { query.pageNum = 1; load() }, { immediate: true })
</script>
