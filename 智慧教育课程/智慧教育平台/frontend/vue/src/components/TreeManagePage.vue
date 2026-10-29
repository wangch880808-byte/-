<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { api } from '../api/client'
import { hasPermission } from '../stores/session'
import { toast } from '../utils/toast'
import AppModal from './AppModal.vue'
import ConfirmDialog from './ConfirmDialog.vue'

const props = defineProps({
  config: { type: Object, required: true }
})

const cfg = props.config
const rows = ref([])
const keyword = ref('')
const status = ref('')
const expanded = ref(true)
const loading = ref(false)
const error = ref('')
let disposed = false

const form = reactive({ open: false, mode: 'add', id: null, data: {}, error: '', saving: false, parentChoices: [] })
const confirmState = reactive({ open: false, text: '', action: null, busy: false, error: '', danger: true })

const ordered = computed(() => orderedRowsOf(rows.value, cfg.idKey))

const visibleRows = computed(() => (expanded.value ? ordered.value : ordered.value.filter((item) => item.depth === 0)))

function can(action) {
  return hasPermission(`${cfg.permission}:${action}`)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    if (keyword.value.trim()) params.set(cfg.labelKey, keyword.value.trim())
    if (status.value !== '') params.set('status', status.value)
    const data = await api.get(`${cfg.base}/list?${params}`)
    if (disposed) return
    rows.value = Array.isArray(data.data) ? data.data : []
  } catch (err) {
    if (!disposed && !err.forbidden && !err.unauthorized) error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  status.value = ''
  load()
}

function parentChoices(list, excludeId, currentParent, editing) {
  const blocked = new Set(excludeId ? [String(excludeId)] : [])
  let changed = true
  while (changed) {
    changed = false
    list.forEach((row) => {
      if (blocked.has(String(row.parentId)) && !blocked.has(String(row[cfg.idKey]))) {
        blocked.add(String(row[cfg.idKey]))
        changed = true
      }
    })
  }
  const choices = []
  if (cfg.rootAllowed) choices.push({ value: '0', label: '顶级' })
  else if (editing && String(currentParent) === '0') choices.push({ value: '0', label: '顶级（不可修改）', disabled: true })
  const orderedList = orderedRowsOf(list, cfg.idKey)
  orderedList.forEach((item) => {
    if (blocked.has(String(item.row[cfg.idKey]))) return
    if (cfg.kind === 'menu' && item.row.menuType === 'F') return
    choices.push({
      value: String(item.row[cfg.idKey]),
      label: '　'.repeat(item.depth) + item.row[cfg.labelKey]
    })
  })
  return choices
}

function orderedRowsOf(list, idKey) {
  const byId = new Map(list.map((row) => [String(row[idKey]), row]))
  const children = new Map()
  list.forEach((row) => {
    const parent = String(row.parentId || 0)
    if (!children.has(parent)) children.set(parent, [])
    children.get(parent).push(row)
  })
  const result = []
  const seen = new Set()
  const visit = (row, depth) => {
    const id = String(row[idKey])
    if (seen.has(id)) return
    seen.add(id)
    result.push({ row, depth })
    ;(children.get(id) || []).forEach((child) => visit(child, depth + 1))
  }
  list.filter((row) => !byId.has(String(row.parentId || 0))).forEach((row) => visit(row, 0))
  list.forEach((row) => visit(row, 0))
  return result
}

async function openForm(id = null, parentId = undefined) {
  if (!can(id === null ? 'add' : 'edit')) return
  try {
    const detail = id === null
      ? Promise.resolve({ data: { ...cfg.defaults, parentId: parentId === undefined ? cfg.defaultParent : parentId } })
      : api.get(`${cfg.base}/${id}`)
    const [listResult, detailResult] = await Promise.all([api.get(`${cfg.base}/list`), detail])
    if (!Array.isArray(listResult.data)) throw new Error('上级列表响应格式不正确')
    if (!detailResult.data) throw new Error('对象不存在或已不可用')
    const data = {}
    Object.keys(detailResult.data).forEach((key) => {
      const value = detailResult.data[key]
      data[key] = value === null || value === undefined ? '' : String(value)
    })
    cfg.fields.forEach((field) => {
      if (data[field.key] === undefined || data[field.key] === '') {
        if (cfg.defaults[field.key] !== undefined) data[field.key] = String(cfg.defaults[field.key])
      }
    })
    form.mode = id === null ? 'add' : 'edit'
    form.id = id
    form.data = data
    form.error = ''
    form.parentChoices = parentChoices(listResult.data, id, data.parentId, id !== null)
    if ((data.parentId === undefined || data.parentId === '') && form.parentChoices.length) {
      form.data.parentId = form.parentChoices[0].value
    }
    form.open = true
  } catch (err) {
    toast(err.message || '加载表单失败', 'error')
  }
}

function fieldVisible(field) {
  return !field.types || field.types.includes(form.data.menuType)
}

async function save() {
  if (form.saving) return
  form.saving = true
  form.error = ''
  try {
    const payload = {}
    cfg.fields.forEach((field) => {
      if (!fieldVisible(field)) {
        payload[field.key] = ''
        return
      }
      let value = form.data[field.key]
      if (field.type === 'number') {
        if (!/^\d+$/.test(String(value)) || !Number.isSafeInteger(Number(value))) throw new Error(`${field.title}需为非负整数`)
        payload[field.key] = Number(value)
      } else {
        payload[field.key] = String(value ?? '').trim()
      }
      if (field.required && (payload[field.key] === '' || payload[field.key] === undefined)) {
        throw new Error(`请填写${field.title}`)
      }
    })

    if (!payload.parentId && payload.parentId !== 0) throw new Error(`没有可用的上级${cfg.title}`)
    if (form.mode === 'edit') payload[cfg.idKey] = form.id

    if (cfg.kind === 'menu') {
      if (payload.menuType === 'F') {
        Object.assign(payload, { path: '', component: '', isFrame: '1', isCache: '1', visible: '0' })
      } else {
        if (!payload.path) throw new Error('请填写路由地址')
        if (payload.isFrame === '0' && !/^https?:\/\//i.test(payload.path)) throw new Error('外链地址需以 http:// 或 https:// 开头')
        if (payload.menuType === 'C' && payload.isFrame !== '0' && !payload.component) throw new Error('请填写组件路径')
        if (payload.menuType === 'M') Object.assign(payload, { component: '', query: '', isCache: '1' })
        if (payload.query) {
          try { JSON.parse(payload.query) } catch { throw new Error('路由参数需为 JSON 格式') }
        }
      }
    } else {
      if (payload.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email)) throw new Error('邮箱格式不正确')
      if (payload.phone && !/^[\d+\-]{1,11}$/.test(payload.phone)) throw new Error('联系电话格式不正确')
    }

    if (form.mode === 'edit') await api.put(cfg.base, payload)
    else await api.post(cfg.base, payload)

    form.open = false
    toast(form.mode === 'edit' ? '已保存' : '已新增')
    await load()
  } catch (err) {
    form.error = err.message || '保存失败'
  } finally {
    form.saving = false
  }
}

function askRemove(item) {
  confirmState.text = `确定删除${cfg.title}“${item[cfg.labelKey]}”吗？`
  confirmState.danger = true
  confirmState.error = ''
  confirmState.action = async () => {
    await api.del(`${cfg.base}/${item[cfg.idKey]}`)
    await load()
  }
  confirmState.open = true
}

async function runConfirm() {
  if (confirmState.busy) return
  confirmState.busy = true
  confirmState.error = ''
  try {
    await confirmState.action()
    confirmState.open = false
  } catch (err) {
    confirmState.error = err.forbidden || err.unauthorized ? '' : err.message || '操作失败'
  } finally {
    confirmState.busy = false
  }
}

onMounted(load)
onUnmounted(() => { disposed = true })
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <input v-model="keyword" class="ui-input" :placeholder="`按${cfg.title}名称搜索`" @keyup.enter="load" />
      <select v-model="status" class="ui-select">
        <option value="">全部状态</option>
        <option value="0">正常</option>
        <option value="1">停用</option>
      </select>
      <button class="ui-btn primary" type="button" @click="load">查询</button>
      <button class="ui-btn" type="button" @click="resetFilters">重置</button>
      <button class="ui-btn" type="button" @click="expanded = !expanded">
        {{ expanded ? '折叠全部' : '展开全部' }}
      </button>
      <span class="spacer"></span>
      <button v-if="can('add')" class="ui-btn primary" type="button" @click="openForm()">新增{{ cfg.title }}</button>
    </div>

    <div class="table-wrap">
      <table class="ui-table">
        <thead>
          <tr>
            <th v-for="column in cfg.columns" :key="column.key">{{ column.title }}</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in visibleRows" :key="item.row[cfg.idKey]">
            <td
              v-for="(column, index) in cfg.columns"
              :key="column.key"
              :style="index === 0 ? { paddingLeft: 12 + item.depth * 22 + 'px' } : undefined"
            >
              {{ column.format ? column.format(item.row[column.key], item.row) : (item.row[column.key] ?? '—') }}
            </td>
            <td class="actions">
              <button
                v-if="can('add') && item.row.menuType !== 'F'"
                class="ui-btn link"
                type="button"
                @click="openForm(null, item.row[cfg.idKey])"
              >新增下级</button>
              <button v-if="can('edit')" class="ui-btn link" type="button" @click="openForm(item.row[cfg.idKey])">编辑</button>
              <button v-if="can('remove')" class="ui-btn link danger" type="button" @click="askRemove(item.row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="loading" class="state">加载中…</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="!visibleRows.length" class="state">暂无数据</div>
    </div>

    <div class="pager">
      <span>共 {{ rows.length }} 条</span>
    </div>

    <AppModal
      v-if="form.open"
      :title="(form.mode === 'edit' ? '编辑' : '新增') + cfg.title"
      wide
      :busy="form.saving"
      :error="form.error"
      ok-text="保存"
      @close="form.open = false"
      @confirm="save"
    >
      <div class="form-grid">
        <div
          v-for="field in cfg.fields"
          v-show="fieldVisible(field)"
          :key="field.key"
          class="field"
          :class="{ full: field.full }"
        >
          <label :class="{ req: field.required }" :for="`${cfg.kind}-${field.key}`">{{ field.title }}</label>
          <select
            v-if="field.parent"
            :id="`${cfg.kind}-${field.key}`"
            v-model="form.data[field.key]"
            class="ui-select"
          >
            <option
              v-for="choice in form.parentChoices"
              :key="choice.value"
              :value="choice.value"
              :disabled="choice.disabled"
            >
              {{ choice.label }}
            </option>
          </select>
          <select
            v-else-if="field.options"
            :id="`${cfg.kind}-${field.key}`"
            v-model="form.data[field.key]"
            class="ui-select"
          >
            <option v-for="option in field.options" :key="option.value" :value="String(option.value)">
              {{ option.label }}
            </option>
          </select>
          <input
            v-else
            :id="`${cfg.kind}-${field.key}`"
            v-model="form.data[field.key]"
            class="ui-input"
            :type="field.type === 'number' ? 'number' : 'text'"
            :maxlength="field.max"
            min="0"
          />
        </div>
      </div>
    </AppModal>

    <ConfirmDialog
      v-if="confirmState.open"
      :text="confirmState.text"
      :busy="confirmState.busy"
      :error="confirmState.error"
      :danger="confirmState.danger"
      ok-text="删除"
      @close="confirmState.open = false"
      @confirm="runConfirm"
    />
  </div>
</template>
