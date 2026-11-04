<template>
  <div class="app-container">
    <el-form :inline="true" :model="query" class="mb16" @submit.prevent>
      <el-form-item label="账号"><el-input v-model="query.userName" clearable placeholder="登录账号" @keyup.enter="search" /></el-form-item>
      <el-form-item label="手机"><el-input v-model="query.phonenumber" clearable placeholder="手机号" @keyup.enter="search" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width:120px">
          <el-option label="正常" value="0" /><el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">搜索</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar mb16">
      <el-button type="primary" @click="openForm()">新增</el-button>
      <el-button type="danger" :disabled="!selected.length" @click="removeBatch">删除</el-button>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb16" />

    <el-table v-loading="loading" :data="rows" border @selection-change="selected = $event">
      <el-table-column type="selection" width="48" :selectable="row => row.userId !== 1" />
      <el-table-column prop="userId" label="编号" width="80" />
      <el-table-column prop="userName" label="账号" min-width="120" />
      <el-table-column prop="nickName" label="姓名" min-width="120" />
      <el-table-column label="部门" min-width="120"><template #default="{ row }">{{ row.dept?.deptName || '—' }}</template></el-table-column>
      <el-table-column prop="phonenumber" label="手机" min-width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === '0'"
            :disabled="row.userId === 1"
            inline-prompt
            active-text="正常"
            inactive-text="停用"
            @change="val => onStatus(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.userId === 1" @click="openForm(row)">编辑</el-button>
          <el-button link type="primary" :disabled="row.userId === 1" @click="openPwd(row)">重置密码</el-button>
          <el-button link type="danger" :disabled="row.userId === 1" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination :total="total" :page="query.pageNum" :limit="query.pageSize"
      @update:page="query.pageNum = $event" @update:limit="query.pageSize = $event" @pagination="load" />

    <el-dialog v-model="formOpen" :title="form.userId ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="姓名" prop="nickName"><el-input v-model="form.nickName" maxlength="30" /></el-form-item>
        <el-form-item label="账号" prop="userName"><el-input v-model="form.userName" :disabled="!!form.userId" maxlength="30" /></el-form-item>
        <el-form-item v-if="!form.userId" label="密码" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="20" /></el-form-item>
        <el-form-item label="手机" prop="phonenumber"><el-input v-model="form.phonenumber" maxlength="11" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="50" /></el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.sex">
            <el-radio value="0">男</el-radio><el-radio value="1">女</el-radio><el-radio value="2">未知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="部门" prop="deptId">
          <el-tree-select
            v-model="form.deptId"
            :data="deptOptions"
            :props="{ value: 'id', label: 'label', children: 'children' }"
            check-strictly
            clearable
            filterable
            style="width:100%"
            placeholder="选择部门"
          />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple filterable style="width:100%" placeholder="选择角色">
            <el-option v-for="role in roleOptions" :key="role.roleId" :label="role.roleName" :value="role.roleId" :disabled="role.status === '1'" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio><el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdOpen" title="重置密码" width="420px">
      <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-width="88px">
        <el-form-item label="账号"><el-input :model-value="pwdForm.userName" disabled /></el-form-item>
        <el-form-item label="新密码" prop="password"><el-input v-model="pwdForm.password" type="password" show-password maxlength="20" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api/system/user'
import { eduErrorText } from '@/utils/request'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const selected = ref([])
const formOpen = ref(false)
const pwdOpen = ref(false)
const formRef = ref()
const pwdRef = ref()
const deptOptions = ref([])
const roleOptions = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, userName: '', phonenumber: '', status: '' })
const form = reactive(emptyForm())
const pwdForm = reactive({ userId: null, userName: '', password: '' })

const rules = {
  nickName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  userName: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 5, max: 20, message: '密码长度 5-20', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择部门', trigger: 'change' }],
  roleIds: [{ type: 'array', required: true, message: '请选择角色', trigger: 'change' }]
}
const pwdRules = {
  password: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 5, max: 20, message: '密码长度 5-20', trigger: 'blur' }]
}

function emptyForm() {
  return { userId: undefined, userName: '', nickName: '', password: '', phonenumber: '', email: '', sex: '2', deptId: undefined, roleIds: [], status: '0', remark: '' }
}

function assignForm(target, source) {
  Object.keys(target).forEach(key => { target[key] = source[key] })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const body = await api.listUser({ ...query })
    rows.value = Array.isArray(body.rows) ? body.rows : []
    total.value = Number(body.total) || 0
  } catch (err) {
    rows.value = []
    total.value = 0
    error.value = eduErrorText(err)
  } finally {
    loading.value = false
  }
}

function search() { query.pageNum = 1; load() }
function resetQuery() {
  query.userName = ''
  query.phonenumber = ''
  query.status = ''
  search()
}

async function openForm(row) {
  assignForm(form, emptyForm())
  try {
    const info = await api.getUser(row?.userId)
    roleOptions.value = Array.isArray(info.roles) ? info.roles : []
    if (!deptOptions.value.length) deptOptions.value = await api.deptTreeSelect()
    if (row?.userId) {
      const data = info.data || {}
      assignForm(form, {
        userId: data.userId,
        userName: data.userName,
        nickName: data.nickName,
        password: '',
        phonenumber: data.phonenumber || '',
        email: data.email || '',
        sex: data.sex || '2',
        deptId: data.deptId,
        roleIds: Array.isArray(info.roleIds) ? info.roleIds : [],
        status: data.status || '0',
        remark: data.remark || ''
      })
    }
    formOpen.value = true
  } catch (err) {
    ElMessage.error(eduErrorText(err))
  }
}

async function submit() {
  if (!await formRef.value.validate().catch(() => false)) return
  saving.value = true
  try {
    const payload = { ...form, roleIds: form.roleIds }
    if (form.userId) {
      delete payload.password
      await api.updateUser(payload)
      ElMessage.success('已保存')
    } else {
      await api.addUser(payload)
      ElMessage.success('已新增')
    }
    formOpen.value = false
    await load()
  } catch (err) {
    ElMessage.error(eduErrorText(err))
  } finally {
    saving.value = false
  }
}

function openPwd(row) {
  pwdForm.userId = row.userId
  pwdForm.userName = row.userName
  pwdForm.password = ''
  pwdOpen.value = true
}

async function submitPwd() {
  if (!await pwdRef.value.validate().catch(() => false)) return
  saving.value = true
  try {
    await api.resetUserPwd({ userId: pwdForm.userId, password: pwdForm.password })
    ElMessage.success('密码已重置')
    pwdOpen.value = false
  } catch (err) {
    ElMessage.error(eduErrorText(err))
  } finally {
    saving.value = false
  }
}

async function onStatus(row, enabled) {
  const status = enabled ? '0' : '1'
  try {
    await api.changeUserStatus({ userId: row.userId, status })
    row.status = status
    ElMessage.success(status === '0' ? '已启用' : '已停用')
  } catch (err) {
    ElMessage.error(eduErrorText(err))
  }
}

async function remove(row) {
  await removeIds([row.userId], `确认删除用户「${row.userName}」？`)
}

async function removeBatch() {
  const ids = selected.value.map(item => item.userId)
  await removeIds(ids, `确认删除选中的 ${ids.length} 个用户？`)
}

async function removeIds(ids, message) {
  try {
    await ElMessageBox.confirm(message, '提示', { type: 'warning' })
    await api.delUser(ids.join(','))
    ElMessage.success('已删除')
    await load()
  } catch (err) {
    if (err !== 'cancel' && err?.toString?.() !== 'cancel') ElMessage.error(eduErrorText(err))
  }
}

onMounted(async () => {
  try { deptOptions.value = await api.deptTreeSelect() } catch { /* 打开表单时再试 */ }
  load()
})
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; }
</style>
