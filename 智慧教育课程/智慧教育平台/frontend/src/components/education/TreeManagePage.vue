<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Delete, EditPen, Plus } from '@element-plus/icons-vue'
import { api } from '@/api/education/client.js'
import { hasPermission } from '@/education/session.js'
import { toast } from '@/utils/education/toast.js'
import AppModal from '@/components/education/AppModal.vue'
import ConfirmDialog from '@/components/education/ConfirmDialog.vue'
import OrganizationTransferActions from '@/components/education/OrganizationTransferActions.vue'

const props = defineProps({
  config: { type: Object, required: true },
  openImportOnMount: Boolean
})

const cfg = props.config
const isOrganization = cfg.kind === 'dept'
const rows = ref([])
const keyword = ref('')
const status = ref('')
const appliedFilters = ref({})
const filtersOpen = ref(false)
const expanded = ref(true)
const collapsedIds = ref(new Set())
const loading = ref(false)
const error = ref('')
let disposed = false

const form = reactive({ open: false, mode: 'add', id: null, data: {}, error: '', saving: false, parentChoices: [] })
const confirmState = reactive({ open: false, text: '', warnings: [], force: false, action: null, busy: false, error: '' })
const removalPreviewId = ref(null)

const ordered = computed(() => orderedRowsOf(rows.value, cfg.idKey))

const branchIds = computed(() => new Set(ordered.value
  .filter((item) => item.depth > 0)
  .map((item) => String(item.row.parentId))))
const hasExpandedBranches = computed(() => cfg.collapsible
  ? !branchIds.value.size || [...branchIds.value].some((id) => !collapsedIds.value.has(id))
  : expanded.value)

const visibleRows = computed(() => {
  if (!cfg.collapsible) return expanded.value ? ordered.value : ordered.value.filter((item) => item.depth === 0)
  let hiddenBelow = -1
  return ordered.value.filter((item) => {
    if (hiddenBelow >= 0 && item.depth > hiddenBelow) return false
    hiddenBelow = collapsedIds.value.has(String(item.row[cfg.idKey])) ? item.depth : -1
    return true
  })
})

function toggleBranch(id) {
  const next = new Set(collapsedIds.value)
  const key = String(id)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  collapsedIds.value = next
}

function toggleAll() {
  if (!cfg.collapsible) expanded.value = !expanded.value
  else collapsedIds.value = hasExpandedBranches.value ? new Set(branchIds.value) : new Set()
}

function can(action) {
  return hasPermission(`${cfg.permission}:${action}`)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const filters = { [cfg.labelKey]: keyword.value.trim(), status: status.value }
    const params = new URLSearchParams()
    if (filters[cfg.labelKey]) params.set(cfg.labelKey, filters[cfg.labelKey])
    if (filters.status !== '') params.set('status', filters.status)
    const data = await api.get(`${cfg.base}/list?${params}`)
    if (disposed) return
    rows.value = Array.isArray(data.data) ? data.data : []
    if (isOrganization) appliedFilters.value = filters
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
  const rootAllowed = typeof cfg.rootAllowed === 'function' ? cfg.rootAllowed() : cfg.rootAllowed
  if (rootAllowed) choices.push({ value: '0', label: '顶级' })
  else if (editing && String(currentParent) === '0') choices.push({ value: '0', label: '顶级（不可修改）', disabled: true })
  const orderedList = orderedRowsOf(list, cfg.idKey)
  orderedList.forEach((item) => {
    if (blocked.has(String(item.row[cfg.idKey]))) return
    if (cfg.kind === 'menu' && item.row.menuType === 'F') return
    if (isOrganization && item.row.deptType === 'CLASS') return
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
    if (disposed) return
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
    if (isOrganization && !data.deptType) data.deptType = ''
    form.mode = id === null ? 'add' : 'edit'
    form.id = id
    form.data = data
    form.error = ''
    form.parentChoices = parentChoices(listResult.data, id, data.parentId, id !== null)
    const needsParent = data.parentId === undefined || data.parentId === ''
      || (isOrganization && id === null && !form.parentChoices.some((choice) => choice.value === data.parentId))
    if (needsParent && form.parentChoices.length) {
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

async function askRemove(item) {
  if (removalPreviewId.value !== null || confirmState.open) return
  const id = item[cfg.idKey]
  const name = item[cfg.labelKey]
  let endpoint = `${cfg.base}/${id}`
  let message = `确定删除${cfg.title}“${name}”吗？`
  let force = false
  const warnings = []
  if (isOrganization) {
    removalPreviewId.value = id
    try {
      const response = await api.get(`${endpoint}/delete-preview`, { silent403: true })
      if (disposed) return
      const { count, scope, forceRequired, userCount, roleCount, teachingRecords } = response.data || {}
      if (!Number.isInteger(count) || count < 1 || typeof scope !== 'string' || !scope
        || typeof forceRequired !== 'boolean') {
        throw new Error('无法确定组织删除范围')
      }
      force = forceRequired
      message = count > 1
        ? `将删除“${name}”及其全部下级，共 ${count} 个组织。`
        : `确定删除组织“${name}”吗？`
      endpoint += `?expectedScope=${encodeURIComponent(scope)}`
      if (force) {
        endpoint += '&force=true'
        if (count > 1) warnings.push('此分支中的班级会从班级管理中移除。')
        if (userCount) warnings.push(`${userCount} 个账号将失去原组织归属，学生可能需要重新分班，在线用户可能需要重新登录。`)
        if (roleCount) warnings.push(`${roleCount} 个角色将移除这些组织的数据范围授权。`)
        if (teachingRecords) warnings.push('已有学期、课程和教学班记录会保留，依赖原组织的查询或绑定可能受影响。')
      }
    } catch (err) {
      if (!disposed && !err.cancelled && !err.unauthorized) toast(err.message || '无法查询删除范围', 'error')
      return
    } finally {
      removalPreviewId.value = null
    }
  }
  confirmState.text = message
  confirmState.force = force
  confirmState.warnings = warnings
  confirmState.error = ''
  confirmState.action = async () => {
    const result = await api.del(endpoint, { silent403: isOrganization })
    if (isOrganization) toast(result.msg || '组织已删除')
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
    confirmState.error = err.unauthorized || (!isOrganization && err.forbidden) ? '' : err.message || '操作失败'
  } finally {
    confirmState.busy = false
  }
}

onMounted(load)
onUnmounted(() => { disposed = true })
</script>

<template>
  <div class="panel" :class="{ 'org-list': isOrganization }">
    <div v-if="isOrganization" class="org-heading">
      <h2>组织列表</h2>
      <div class="org-actions">
        <button v-if="can('add')" class="ui-btn primary" type="button" @click="openForm()">
          <Plus class="org-icon" aria-hidden="true" />新建组织
        </button>
        <OrganizationTransferActions
          :filters="appliedFilters"
          :open-import-on-mount="props.openImportOnMount"
          @imported="load"
        />
        <button class="ui-btn" type="button" :disabled="!branchIds.size" @click="toggleAll">
          {{ hasExpandedBranches ? '收起全部' : '展开全部' }}
        </button>
        <button
          class="ui-btn"
          :class="{ 'filter-active': keyword.trim() || status !== '' }"
          type="button"
          :aria-expanded="filtersOpen"
          aria-controls="organization-filters"
          @click="filtersOpen = !filtersOpen"
        >筛选</button>
      </div>
    </div>

    <div v-show="!isOrganization || filtersOpen" :id="isOrganization ? 'organization-filters' : undefined" class="toolbar">
      <input v-model="keyword" class="ui-input" :placeholder="`按${cfg.title}名称搜索`" @keyup.enter="load" />
      <select v-model="status" class="ui-select" aria-label="状态">
        <option value="">全部状态</option>
        <option value="0">{{ isOrganization ? '开启' : '正常' }}</option>
        <option value="1">停用</option>
      </select>
      <button class="ui-btn primary" type="button" @click="load">查询</button>
      <button class="ui-btn" type="button" @click="resetFilters">重置</button>
      <template v-if="!isOrganization">
        <button class="ui-btn" type="button" :disabled="cfg.collapsible && !branchIds.size" @click="toggleAll">
          {{ hasExpandedBranches ? '折叠全部' : '展开全部' }}
        </button>
        <span class="spacer"></span>
        <button v-if="can('add')" class="ui-btn primary" type="button" @click="openForm()">新增{{ cfg.title }}</button>
      </template>
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
              <span v-if="index === 0 && cfg.collapsible" class="tree-label">
                <button
                  v-if="branchIds.has(String(item.row[cfg.idKey]))"
                  class="tree-toggle"
                  type="button"
                  :aria-expanded="!collapsedIds.has(String(item.row[cfg.idKey]))"
                  :aria-label="(collapsedIds.has(String(item.row[cfg.idKey])) ? '展开' : '收起') + item.row[cfg.labelKey]"
                  :title="collapsedIds.has(String(item.row[cfg.idKey])) ? '展开下级' : '收起下级'"
                  @click="toggleBranch(item.row[cfg.idKey])"
                ><span aria-hidden="true">{{ collapsedIds.has(String(item.row[cfg.idKey])) ? '▸' : '▾' }}</span></button>
                <span v-else class="tree-spacer" aria-hidden="true"></span>
                <span>{{ column.format ? column.format(item.row[column.key], item.row) : (item.row[column.key] ?? '—') }}</span>
              </span>
              <span v-else-if="isOrganization && column.key === 'status'" class="org-status">
                <span class="org-status-dot" :class="{ active: String(item.row.status) === '0' }" aria-hidden="true"></span>
                {{ column.format(item.row.status) }}
              </span>
              <template v-else>{{ column.format ? column.format(item.row[column.key], item.row) : (item.row[column.key] ?? '—') }}</template>
            </td>
            <td class="actions">
              <button
                v-if="can('add') && item.row.menuType !== 'F' && (!isOrganization || item.row.deptType !== 'CLASS')"
                class="ui-btn link"
                type="button"
                @click="openForm(null, item.row[cfg.idKey])"
              ><Plus v-if="isOrganization" class="org-icon" aria-hidden="true" />{{ isOrganization ? '新建下级' : '新增下级' }}</button>
              <button v-if="can('edit')" class="ui-btn link" type="button" @click="openForm(item.row[cfg.idKey])">
                <EditPen v-if="isOrganization" class="org-icon" aria-hidden="true" />{{ isOrganization ? '修改' : '编辑' }}
              </button>
              <button v-if="can('remove')" class="ui-btn link danger" type="button"
                :disabled="removalPreviewId !== null" @click="askRemove(item.row)">
                <Delete v-if="isOrganization" class="org-icon" aria-hidden="true" />删除
              </button>
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
      :title="(form.mode === 'edit' ? '编辑' : isOrganization ? '新建' : '新增') + cfg.title"
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
      :title="confirmState.force ? '确认强制删除' : '请确认'"
      :text="confirmState.text"
      :warnings="confirmState.warnings"
      :busy="confirmState.busy"
      :error="confirmState.error"
      danger
      :ok-text="confirmState.force ? '强制删除' : '删除'"
      @close="confirmState.open = false"
      @confirm="runConfirm"
    />
  </div>
</template>

<style scoped>
.tree-label { display: inline-flex; align-items: center; gap: 4px; }
.tree-toggle, .tree-spacer { width: 18px; flex: 0 0 18px; }
.tree-toggle { display: inline-flex; align-items: center; justify-content: center; height: 22px; padding: 0; border: 0; border-radius: 3px; background: transparent; color: inherit; font: inherit; cursor: pointer; }
.tree-toggle:hover { color: var(--primary); background: #e6f7ff; }
.tree-toggle:focus-visible { outline: 2px solid var(--primary); outline-offset: 1px; }

.org-list {
  --org-primary: #0866cf;
  padding: 12px;
}
.org-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 16px;
  margin-bottom: 8px;
}
.org-heading h2 {
  margin: 0;
  color: #17191c;
  font-size: 16px;
  font-weight: 600;
}
.org-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.org-actions .ui-btn,
.org-list .actions .ui-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}
.org-actions .ui-btn:not(.primary) {
  box-shadow: 0 2px 0 #eff0f2;
}
.org-actions .ui-btn.primary {
  background: var(--org-primary);
  border-color: var(--org-primary);
}
.org-actions .ui-btn.primary:hover {
  background: #0758b4;
  border-color: #0758b4;
}
.org-actions .filter-active {
  color: var(--org-primary);
  border-color: var(--org-primary);
}
.org-icon {
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
}
.org-list .table-wrap {
  border-color: #eaebee;
}
.org-list .ui-table {
  min-width: 740px;
  color: #22262b;
}
.org-list .ui-table th {
  padding-block: 12px;
  background: #f0f0f2;
  border-bottom-color: #e8e9ec;
  color: #555b64;
  font-weight: 500;
}
.org-list .ui-table th + th::before {
  content: '';
  position: absolute;
  inset: 10px auto 10px 0;
  width: 1px;
  background: #dcdde1;
}
.org-list .ui-table td {
  padding-block: 9px;
  border-bottom-color: #eeeff2;
}
.org-list .ui-table th:first-child { width: 24%; }
.org-list .ui-table th:nth-child(2),
.org-list .ui-table th:nth-child(4),
.org-list .ui-table th:nth-child(5) { width: 10%; }
.org-list .ui-table th:nth-child(3) { width: 12%; }
.org-list .ui-table th:nth-child(6) { width: 16%; }
.org-list .ui-table tbody tr:last-child td { border-bottom: 0; }
.org-list .tree-toggle { color: #646a73; }
.org-status { display: inline-flex; align-items: center; gap: 6px; }
.org-status-dot { width: 6px; height: 6px; border-radius: 50%; background: #9298a1; }
.org-status-dot.active { background: #1d7846; }
.org-list .actions .ui-btn.link:not(.danger) { color: var(--org-primary); }
</style>
