<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import { hasPermission } from '../../stores/session'
import { toast } from '../../utils/toast'
import AppModal from '../../components/AppModal.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'
import PermissionTree from '../../components/PermissionTree.vue'

const PAGE_SIZE = 10
const DATA_SCOPES = [
  { value: '1', label: '全部数据权限' },
  { value: '2', label: '自定义数据权限' },
  { value: '3', label: '本部门数据权限' },
  { value: '4', label: '本部门及以下数据权限' },
  { value: '5', label: '仅本人数据权限' }
]

const rows = ref([])
const total = ref(0)
const page = ref(1)
const filters = reactive({ roleName: '', roleKey: '', status: '' })
const loading = ref(false)
const error = ref('')
let disposed = false

const form = reactive({
  open: false, mode: 'add', id: null, saving: false, error: '',
  data: { roleName: '', roleKey: '', roleSort: 0, status: '0', dataScope: '5', remark: '' },
  menuNodes: [], menuChecked: []
})
const formTree = ref(null)

const scopeDialog = reactive({
  open: false, roleId: null, roleName: '', scope: '5', saving: false, error: '',
  deptNodes: [], deptChecked: []
})
const scopeTree = ref(null)

const confirmState = reactive({ open: false, text: '', danger: true, busy: false, error: '', action: null })

const canAdd = computed(() => hasPermission('system:role:add'))
const canEdit = computed(() => hasPermission('system:role:edit'))
const canRemove = computed(() => hasPermission('system:role:remove'))
const pages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const scopeOptions = computed(() =>
  DATA_SCOPES.map((item) => ({ ...item, disabled: item.value === '2' && form.data.dataScope !== '2' }))
)
const dialogScopeOptions = computed(() =>
  DATA_SCOPES.map((item) => ({ ...item, disabled: item.value === '2' && scopeDialog.scope !== '2' }))
)

async function load(target = page.value) {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ pageNum: target, pageSize: PAGE_SIZE })
    if (filters.roleName) params.set('roleName', filters.roleName)
    if (filters.roleKey) params.set('roleKey', filters.roleKey)
    if (filters.status !== '') params.set('status', filters.status)
    const data = await api.get(`/system/role/list?${params}`)
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

function search() { load(1) }

function resetFilters() {
  filters.roleName = ''
  filters.roleKey = ''
  filters.status = ''
  load(1)
}

async function openForm(roleId = null) {
  if (!hasPermission(roleId ? 'system:role:edit' : 'system:role:add')) return
  try {
    const rolePromise = roleId ? api.get(`/system/role/${roleId}`) : Promise.resolve({ data: { roleSort: 0, status: '0', dataScope: '5' } })
    const menuPromise = roleId
      ? api.get(`/system/menu/roleMenuTreeselect/${roleId}`)
      : api.get('/system/menu/treeselect').then((data) => ({ menus: data.data || [], checkedKeys: [] }))
    const [roleResult, menuResult] = await Promise.all([rolePromise, menuPromise])
    const role = roleResult.data || {}
    form.mode = roleId ? 'edit' : 'add'
    form.id = roleId
    form.data = {
      roleName: role.roleName || '',
      roleKey: role.roleKey || '',
      roleSort: role.roleSort ?? 0,
      status: String(role.status ?? '0'),
      dataScope: String(role.dataScope ?? '5'),
      remark: role.remark || ''
    }
    form.menuNodes = menuResult.menus || []
    form.menuChecked = (menuResult.checkedKeys || []).map(Number)
    form.error = ''
    form.open = true
  } catch (err) {
    toast(err.message || '加载角色失败', 'error')
  }
}

async function saveRole() {
  if (form.saving) return
  form.saving = true
  form.error = ''
  try {
    if (!form.data.roleName.trim()) throw new Error('请填写角色名称')
    if (!form.data.roleKey.trim()) throw new Error('请填写权限字符')
    const sort = Number(form.data.roleSort)
    if (!Number.isSafeInteger(sort) || sort < 0) throw new Error('显示顺序需为非负整数')
    const menuIds = formTree.value ? formTree.value.getCheckedIds() : []
    const payload = {
      roleName: form.data.roleName.trim(),
      roleKey: form.data.roleKey.trim(),
      roleSort: sort,
      status: form.data.status,
      dataScope: form.data.dataScope,
      remark: form.data.remark.trim(),
      menuIds,
      menuCheckStrictly: true,
      deptCheckStrictly: true
    }
    if (form.mode === 'edit') {
      payload.roleId = form.id
      await api.put('/system/role', payload)
    } else {
      await api.post('/system/role', payload)
    }
    form.open = false
    toast(form.mode === 'edit' ? '已保存' : '已新增角色')
    await load(form.mode === 'edit' ? page.value : 1)
  } catch (err) {
    form.error = err.message || '保存失败'
  } finally {
    form.saving = false
  }
}

async function openDataScope(role) {
  if (!hasPermission('system:role:edit')) return
  try {
    const [roleResult, deptResult] = await Promise.all([
      api.get(`/system/role/${role.roleId}`),
      api.get(`/system/role/deptTree/${role.roleId}`)
    ])
    const detail = roleResult.data || {}
    scopeDialog.roleId = role.roleId
    scopeDialog.roleName = detail.roleName || role.roleName
    scopeDialog.scope = String(detail.dataScope ?? '5')
    scopeDialog.deptNodes = deptResult.depts || []
    scopeDialog.deptChecked = (deptResult.checkedKeys || []).map(Number)
    scopeDialog.error = ''
    scopeDialog.open = true
  } catch (err) {
    toast(err.message || '加载数据权限失败', 'error')
  }
}

async function saveDataScope() {
  if (scopeDialog.saving) return
  scopeDialog.saving = true
  scopeDialog.error = ''
  try {
    const deptIds = scopeDialog.scope === '2' && scopeTree.value ? scopeTree.value.getCheckedIds() : []
    if (scopeDialog.scope === '2' && !deptIds.length) throw new Error('请至少选择一个授权部门')
    await api.put('/system/role/dataScope', {
      roleId: scopeDialog.roleId,
      dataScope: scopeDialog.scope,
      deptIds,
      deptCheckStrictly: false
    })
    scopeDialog.open = false
    toast('数据权限已保存')
    await load()
  } catch (err) {
    scopeDialog.error = err.message || '保存失败'
  } finally {
    scopeDialog.saving = false
  }
}

function toggleStatus(role) {
  const enable = String(role.status) !== '0'
  confirmState.text = enable
    ? `确定启用角色 ${role.roleName} 吗？`
    : `确定停用角色 ${role.roleName} 吗？停用后该角色下的用户将失去对应权限。`
  confirmState.danger = !enable
  confirmState.error = ''
  confirmState.action = async () => {
    await api.put('/system/role/changeStatus', { roleId: role.roleId, status: enable ? '0' : '1' })
    await load()
  }
  confirmState.open = true
}

function askRemove(role) {
  confirmState.text = `确定删除角色 ${role.roleName} 吗？此操作不可恢复。`
  confirmState.danger = true
  confirmState.error = ''
  confirmState.action = async () => {
    await api.del(`/system/role/${role.roleId}`)
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

onMounted(() => load(1))
onUnmounted(() => { disposed = true })
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <input v-model="filters.roleName" class="ui-input" placeholder="按角色名称搜索" @keyup.enter="search" />
      <input v-model="filters.roleKey" class="ui-input" placeholder="按权限字符搜索" @keyup.enter="search" />
      <select v-model="filters.status" class="ui-select">
        <option value="">全部状态</option>
        <option value="0">正常</option>
        <option value="1">停用</option>
      </select>
      <button class="ui-btn primary" type="button" @click="search">查询</button>
      <button class="ui-btn" type="button" @click="resetFilters">重置</button>
      <span class="spacer"></span>
      <button v-if="canAdd" class="ui-btn primary" type="button" @click="openForm()">新增角色</button>
    </div>

    <div class="table-wrap">
      <table class="ui-table">
        <thead>
          <tr><th>角色名称</th><th>权限字符</th><th>显示顺序</th><th>状态</th><th>创建时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="role in rows" :key="role.roleId">
            <td>{{ role.roleName }}</td>
            <td>{{ role.roleKey }}</td>
            <td>{{ role.roleSort }}</td>
            <td>
              <span class="tag" :class="String(role.status) === '0' ? 'ok' : 'off'">
                {{ String(role.status) === '0' ? '正常' : '停用' }}
              </span>
            </td>
            <td>{{ role.createTime || '—' }}</td>
            <td class="actions">
              <button v-if="canEdit" class="ui-btn link" type="button" @click="openForm(role.roleId)">编辑</button>
              <button v-if="canEdit" class="ui-btn link" type="button" @click="openDataScope(role)">数据权限</button>
              <button v-if="canEdit" class="ui-btn link" type="button" @click="toggleStatus(role)">
                {{ String(role.status) === '0' ? '停用' : '启用' }}
              </button>
              <button v-if="canRemove" class="ui-btn link danger" type="button" @click="askRemove(role)">删除</button>
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
      v-if="form.open"
      :title="form.mode === 'edit' ? '编辑角色' : '新增角色'"
      wide
      :busy="form.saving"
      :error="form.error"
      ok-text="保存"
      @close="form.open = false"
      @confirm="saveRole"
    >
      <div class="form-grid">
        <div class="field">
          <label class="req" for="rf-name">角色名称</label>
          <input id="rf-name" v-model="form.data.roleName" class="ui-input" maxlength="30" />
        </div>
        <div class="field">
          <label class="req" for="rf-key">权限字符</label>
          <input id="rf-key" v-model="form.data.roleKey" class="ui-input" maxlength="100" />
        </div>
        <div class="field">
          <label for="rf-sort">显示顺序</label>
          <input id="rf-sort" v-model="form.data.roleSort" class="ui-input" type="number" min="0" />
        </div>
        <div class="field">
          <label for="rf-status">状态</label>
          <select id="rf-status" v-model="form.data.status" class="ui-select">
            <option value="0">正常</option><option value="1">停用</option>
          </select>
        </div>
        <div class="field full">
          <label for="rf-scope">数据范围</label>
          <select id="rf-scope" v-model="form.data.dataScope" class="ui-select">
            <option v-for="option in scopeOptions" :key="option.value" :value="option.value" :disabled="option.disabled">
              {{ option.label }}
            </option>
          </select>
        </div>
        <div class="field full">
          <label>菜单权限</label>
          <PermissionTree ref="formTree" v-model="form.menuChecked" :nodes="form.menuNodes" />
        </div>
        <div class="field full">
          <label for="rf-remark">备注</label>
          <textarea id="rf-remark" v-model="form.data.remark" class="ui-textarea" maxlength="500"></textarea>
        </div>
      </div>
    </AppModal>

    <AppModal
      v-if="scopeDialog.open"
      title="角色数据权限"
      wide
      :busy="scopeDialog.saving"
      :error="scopeDialog.error"
      ok-text="保存"
      @close="scopeDialog.open = false"
      @confirm="saveDataScope"
    >
      <div class="form-grid">
        <div class="field full">
          <label for="role-scope">数据范围</label>
          <select id="role-scope" v-model="scopeDialog.scope" class="ui-select">
            <option v-for="option in dialogScopeOptions" :key="option.value" :value="option.value" :disabled="option.disabled">
              {{ option.label }}
            </option>
          </select>
        </div>
        <div v-show="scopeDialog.scope === '2'" class="field full">
          <label>授权部门</label>
          <PermissionTree
            ref="scopeTree"
            v-model="scopeDialog.deptChecked"
            :nodes="scopeDialog.deptNodes"
            :include-ancestors="false"
          />
          <p class="hint">自定义范围只提交勾选的部门，不自动增加上级部门。</p>
        </div>
      </div>
    </AppModal>

    <ConfirmDialog
      v-if="confirmState.open"
      :text="confirmState.text"
      :busy="confirmState.busy"
      :error="confirmState.error"
      :danger="confirmState.danger"
      :ok-text="confirmState.danger ? '删除' : '确定'"
      @close="confirmState.open = false"
      @confirm="runConfirm"
    />
  </div>
</template>
