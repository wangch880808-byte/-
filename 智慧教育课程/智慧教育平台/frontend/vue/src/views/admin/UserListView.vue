<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import { hasPermission } from '../../stores/session'

const PAGE_SIZE = 10

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref('')
const filters = reactive({ userName: '', phonenumber: '', status: '' })

const roles = ref([])
const depts = ref([])
let controller = null
let disposed = false

const form = reactive({
  open: false,
  mode: 'add',
  saving: false,
  error: '',
  data: {}
})

const resetDialog = reactive({ open: false, user: null, password: '', error: '', saving: false })
const confirmDialog = reactive({ open: false, text: '', danger: false, action: null, saving: false })

const canAdd = computed(() => hasPermission('system:user:add'))
const canEdit = computed(() => hasPermission('system:user:edit'))
const canRemove = computed(() => hasPermission('system:user:remove'))
const canReset = computed(() => hasPermission('system:user:resetPwd'))

const pages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

function normalizeError(err) {
  if (!err || err.forbidden || err.unauthorized || err.cancelled) return ''
  return err.message || '请求失败'
}

async function load(targetPage = page.value) {
  if (controller) controller.abort()
  controller = new AbortController()
  const signal = controller.signal
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ pageNum: targetPage, pageSize: PAGE_SIZE })
    if (filters.userName) params.set('userName', filters.userName)
    if (filters.phonenumber) params.set('phonenumber', filters.phonenumber)
    if (filters.status !== '') params.set('status', filters.status)
    const data = await api.get(`/system/user/list?${params}`, { signal })
    if (disposed) return
    rows.value = data.rows || []
    total.value = Number(data.total || 0)
    page.value = targetPage
  } catch (err) {
    if (disposed || err.cancelled) return
    error.value = normalizeError(err)
  } finally {
    loading.value = false
  }
}

function search() {
  load(1)
}

function resetFilters() {
  filters.userName = ''
  filters.phonenumber = ''
  filters.status = ''
  load(1)
}

function flattenDepts(list) {
  const byParent = new Map()
  list.forEach((dept) => {
    const key = String(dept.parentId || 0)
    if (!byParent.has(key)) byParent.set(key, [])
    byParent.get(key).push(dept)
  })
  const result = []
  const visit = (parentId, depth) => {
    ;(byParent.get(String(parentId)) || []).forEach((dept) => {
      result.push({ value: String(dept.deptId), label: '　'.repeat(depth) + dept.deptName })
      visit(dept.deptId, depth + 1)
    })
  }
  visit(0, 0)
  return result
}

async function ensureLookups() {
  if (roles.value.length && depts.value.length) return
  const [roleResult, deptResult] = await Promise.all([api.get('/system/user/'), api.get('/system/dept/list')])
  roles.value = roleResult.roles || []
  depts.value = flattenDepts(deptResult.data || [])
}

function emptyForm() {
  return {
    userId: null,
    userName: '',
    nickName: '',
    password: '',
    deptId: depts.value[0]?.value || '',
    phonenumber: '',
    email: '',
    sex: '0',
    status: '0',
    remark: '',
    roleIds: [],
    postIds: []
  }
}

async function openAdd() {
  try {
    await ensureLookups()
    form.mode = 'add'
    form.data = emptyForm()
    form.error = ''
    form.open = true
  } catch (err) {
    alert(normalizeError(err) || '加载表单选项失败')
  }
}

async function openEdit(user) {
  try {
    await ensureLookups()
    const detail = await api.get(`/system/user/${user.userId}`)
    const info = detail.data || {}
    form.mode = 'edit'
    form.data = {
      userId: user.userId,
      userName: info.userName || user.userName,
      nickName: info.nickName || '',
      password: '',
      deptId: info.deptId ? String(info.deptId) : '',
      phonenumber: info.phonenumber || '',
      email: info.email || '',
      sex: info.sex || '0',
      status: String(info.status ?? '0'),
      remark: info.remark || '',
      roleIds: (detail.roleIds || []).map(String),
      postIds: detail.postIds || []
    }
    form.error = ''
    form.open = true
  } catch (err) {
    alert(normalizeError(err) || '加载用户失败')
  }
}

function toggleRole(roleId) {
  const value = String(roleId)
  const index = form.data.roleIds.indexOf(value)
  if (index >= 0) form.data.roleIds.splice(index, 1)
  else form.data.roleIds.push(value)
}

function validate(data, editing) {
  if (!data.userName.trim() || data.userName.trim().length < 2) return '账号至少 2 个字符'
  if (!data.nickName.trim()) return '请填写昵称'
  if (!editing && (!data.password || data.password.length < 5 || data.password.length > 20)) return '密码需为 5-20 个字符'
  if (data.phonenumber && !/^1[3-9]\d{9}$/.test(data.phonenumber)) return '手机号格式不正确'
  if (data.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) return '邮箱格式不正确'
  if (!data.roleIds.length) return '请至少选择一个角色'
  return ''
}

async function save() {
  if (form.saving) return
  const editing = form.mode === 'edit'
  const message = validate(form.data, editing)
  if (message) {
    form.error = message
    return
  }
  form.saving = true
  form.error = ''
  try {
    const payload = {
      userName: form.data.userName.trim(),
      nickName: form.data.nickName.trim(),
      deptId: form.data.deptId ? Number(form.data.deptId) : null,
      phonenumber: form.data.phonenumber.trim(),
      email: form.data.email.trim(),
      sex: form.data.sex,
      status: form.data.status,
      remark: form.data.remark.trim(),
      roleIds: form.data.roleIds.map(Number),
      postIds: form.data.postIds || []
    }
    if (editing) {
      payload.userId = form.data.userId
      await api.put('/system/user', payload)
    } else {
      payload.password = form.data.password
      await api.post('/system/user', payload)
    }
    form.open = false
    await load(editing ? page.value : 1)
  } catch (err) {
    form.error = normalizeError(err) || '保存失败'
  } finally {
    form.saving = false
  }
}

function askReset(user) {
  resetDialog.user = user
  resetDialog.password = ''
  resetDialog.error = ''
  resetDialog.open = true
}

async function confirmReset() {
  if (resetDialog.saving) return
  if (!resetDialog.password || resetDialog.password.length < 5 || resetDialog.password.length > 20) {
    resetDialog.error = '密码需为 5-20 个字符'
    return
  }
  resetDialog.saving = true
  resetDialog.error = ''
  try {
    await api.put('/system/user/resetPwd', { userId: resetDialog.user.userId, password: resetDialog.password })
    resetDialog.open = false
  } catch (err) {
    resetDialog.error = normalizeError(err) || '重置失败'
  } finally {
    resetDialog.saving = false
  }
}

function toggleStatus(user) {
  const enable = String(user.status) !== '0'
  confirmDialog.text = enable ? `确定启用账号 ${user.userName} 吗？` : `确定停用账号 ${user.userName} 吗？停用后该账号将无法登录。`
  confirmDialog.danger = !enable
  confirmDialog.action = async () => {
    await api.put('/system/user/changeStatus', { userId: user.userId, status: enable ? '0' : '1' })
    await load()
  }
  confirmDialog.open = true
}

function askRemove(user) {
  confirmDialog.text = `确定删除账号 ${user.userName} 吗？此操作不可恢复。`
  confirmDialog.danger = true
  confirmDialog.action = async () => {
    await api.del(`/system/user/${user.userId}`)
    await load(page.value)
  }
  confirmDialog.open = true
}

async function runConfirm() {
  if (confirmDialog.saving) return
  confirmDialog.saving = true
  try {
    await confirmDialog.action()
    confirmDialog.open = false
  } catch (err) {
    if (!err.forbidden && !err.unauthorized) alert(normalizeError(err) || '操作失败')
  } finally {
    confirmDialog.saving = false
  }
}

onMounted(() => load(1))
onUnmounted(() => {
  disposed = true
  if (controller) controller.abort()
})
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <input v-model="filters.userName" class="ui-input" placeholder="按账号搜索" @keyup.enter="search" />
      <input v-model="filters.phonenumber" class="ui-input" placeholder="按手机号搜索" @keyup.enter="search" />
      <select v-model="filters.status" class="ui-select">
        <option value="">全部状态</option>
        <option value="0">正常</option>
        <option value="1">停用</option>
      </select>
      <button class="ui-btn primary" type="button" @click="search">查询</button>
      <button class="ui-btn" type="button" @click="resetFilters">重置</button>
      <span class="spacer"></span>
      <button v-if="canAdd" class="ui-btn primary" type="button" @click="openAdd">新增用户</button>
    </div>

    <div class="table-wrap">
      <table class="ui-table">
        <thead>
          <tr>
            <th>账号</th><th>昵称</th><th>部门</th><th>手机</th><th>状态</th><th>创建时间</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in rows" :key="user.userId">
            <td>{{ user.userName }}</td>
            <td>{{ user.nickName }}</td>
            <td>{{ user.dept?.deptName || '—' }}</td>
            <td>{{ user.phonenumber || '—' }}</td>
            <td>
              <span class="tag" :class="String(user.status) === '0' ? 'ok' : 'off'">
                {{ String(user.status) === '0' ? '正常' : '停用' }}
              </span>
            </td>
            <td>{{ user.createTime || '—' }}</td>
            <td class="actions">
              <button v-if="canEdit" class="ui-btn link" type="button" @click="openEdit(user)">编辑</button>
              <button v-if="canReset" class="ui-btn link" type="button" @click="askReset(user)">重置密码</button>
              <button v-if="canEdit" class="ui-btn link" type="button" @click="toggleStatus(user)">
                {{ String(user.status) === '0' ? '停用' : '启用' }}
              </button>
              <button v-if="canRemove" class="ui-btn link danger" type="button" @click="askRemove(user)">删除</button>
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

    <!-- 新增 / 编辑 -->
    <div v-if="form.open" class="modal-mask" @click.self="form.open = false">
      <div class="modal wide">
        <div class="modal-head">
          <h3>{{ form.mode === 'edit' ? '编辑用户' : '新增用户' }}</h3>
          <button class="modal-close" type="button" aria-label="关闭" @click="form.open = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-grid">
            <div class="field">
              <label class="req" for="f-userName">账号</label>
              <input id="f-userName" v-model="form.data.userName" class="ui-input" :disabled="form.mode === 'edit'" maxlength="30" />
            </div>
            <div class="field">
              <label class="req" for="f-nickName">昵称</label>
              <input id="f-nickName" v-model="form.data.nickName" class="ui-input" maxlength="30" />
            </div>
            <div class="field">
              <label for="f-dept">部门</label>
              <select id="f-dept" v-model="form.data.deptId" class="ui-select">
                <option v-for="dept in depts" :key="dept.value" :value="dept.value">{{ dept.label }}</option>
              </select>
            </div>
            <div class="field">
              <label for="f-phone">手机号</label>
              <input id="f-phone" v-model="form.data.phonenumber" class="ui-input" maxlength="11" />
            </div>
            <div class="field">
              <label for="f-email">邮箱</label>
              <input id="f-email" v-model="form.data.email" class="ui-input" maxlength="50" />
            </div>
            <div class="field">
              <label for="f-sex">性别</label>
              <select id="f-sex" v-model="form.data.sex" class="ui-select">
                <option value="0">男</option><option value="1">女</option><option value="2">未知</option>
              </select>
            </div>
            <div class="field">
              <label for="f-status">状态</label>
              <select id="f-status" v-model="form.data.status" class="ui-select">
                <option value="0">正常</option><option value="1">停用</option>
              </select>
            </div>
            <div v-if="form.mode === 'add'" class="field">
              <label class="req" for="f-password">初始密码</label>
              <input id="f-password" v-model="form.data.password" class="ui-input" type="password" maxlength="20" placeholder="5-20 个字符" />
            </div>
            <div class="field full">
              <label class="req">角色</label>
              <div class="check-list">
                <label v-for="role in roles" :key="role.roleId" class="check">
                  <input type="checkbox" :checked="form.data.roleIds.includes(String(role.roleId))" @change="toggleRole(role.roleId)" />
                  {{ role.roleName }}（{{ role.roleKey }}）
                </label>
              </div>
            </div>
            <div class="field full">
              <label for="f-remark">备注</label>
              <textarea id="f-remark" v-model="form.data.remark" class="ui-textarea" maxlength="500"></textarea>
            </div>
          </div>
        </div>
        <div class="modal-foot">
          <span class="modal-error">{{ form.error }}</span>
          <button class="ui-btn" type="button" @click="form.open = false">取消</button>
          <button class="ui-btn primary" type="button" :disabled="form.saving" @click="save">
            {{ form.saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 重置密码 -->
    <div v-if="resetDialog.open" class="modal-mask" @click.self="resetDialog.open = false">
      <div class="modal">
        <div class="modal-head">
          <h3>重置密码</h3>
          <button class="modal-close" type="button" aria-label="关闭" @click="resetDialog.open = false">×</button>
        </div>
        <div class="modal-body">
          <p style="margin-top: 0; color: #8c8c8c; font-size: 13px">
            将重置账号 {{ resetDialog.user?.userName }} 的登录密码。
          </p>
          <div class="field">
            <label class="req" for="f-newpwd">新密码（5-20 个字符）</label>
            <input id="f-newpwd" v-model="resetDialog.password" class="ui-input" type="password" maxlength="20" />
          </div>
        </div>
        <div class="modal-foot">
          <span class="modal-error">{{ resetDialog.error }}</span>
          <button class="ui-btn" type="button" @click="resetDialog.open = false">取消</button>
          <button class="ui-btn primary" type="button" :disabled="resetDialog.saving" @click="confirmReset">重置</button>
        </div>
      </div>
    </div>

    <!-- 确认 -->
    <div v-if="confirmDialog.open" class="modal-mask" @click.self="confirmDialog.open = false">
      <div class="modal">
        <div class="modal-head">
          <h3>请确认</h3>
          <button class="modal-close" type="button" aria-label="关闭" @click="confirmDialog.open = false">×</button>
        </div>
        <div class="modal-body">{{ confirmDialog.text }}</div>
        <div class="modal-foot">
          <button class="ui-btn" type="button" @click="confirmDialog.open = false">取消</button>
          <button
            class="ui-btn primary"
            :class="{ danger: confirmDialog.danger }"
            type="button"
            :disabled="confirmDialog.saving"
            @click="runConfirm"
          >确定</button>
        </div>
      </div>
    </div>
  </div>
</template>
