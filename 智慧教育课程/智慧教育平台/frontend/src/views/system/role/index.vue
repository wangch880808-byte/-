<template>
  <div class="app-container">
    <el-form :inline="true" :model="query" class="mb16" @submit.prevent>
      <el-form-item label="角色名称"><el-input v-model="query.roleName" clearable @keyup.enter="search" /></el-form-item>
      <el-form-item label="权限字符"><el-input v-model="query.roleKey" clearable placeholder="如 teacher" @keyup.enter="search" /></el-form-item>
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
      <el-table-column type="selection" width="48" :selectable="row => row.roleId !== 1" />
      <el-table-column prop="roleId" label="编号" width="80" />
      <el-table-column prop="roleName" label="角色名称" min-width="140" />
      <el-table-column prop="roleKey" label="权限字符" min-width="120" />
      <el-table-column prop="roleSort" label="排序" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === '0'"
            :disabled="row.roleId === 1"
            inline-prompt
            active-text="正常"
            inactive-text="停用"
            @change="val => onStatus(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.roleId === 1" @click="openForm(row)">编辑</el-button>
          <el-button link type="danger" :disabled="row.roleId === 1" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination :total="total" :page="query.pageNum" :limit="query.pageSize"
      @update:page="query.pageNum = $event" @update:limit="query.pageSize = $event" @pagination="load" />

    <el-dialog v-model="formOpen" :title="form.roleId ? '编辑角色' : '新增角色'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="角色名称" prop="roleName"><el-input v-model="form.roleName" maxlength="30" /></el-form-item>
        <el-form-item label="权限字符" prop="roleKey">
          <el-input v-model="form.roleKey" maxlength="100" placeholder="登录入口匹配用，如 admin / teacher / student" />
        </el-form-item>
        <el-form-item label="显示顺序" prop="roleSort"><el-input-number v-model="form.roleSort" :min="0" :max="999" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio><el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单权限">
          <div class="menu-tools">
            <el-checkbox v-model="menuExpand" @change="toggleExpand">展开/折叠</el-checkbox>
            <el-checkbox v-model="menuNodeAll" @change="toggleCheckAll">全选/全不选</el-checkbox>
            <el-checkbox v-model="form.menuCheckStrictly">父子联动</el-checkbox>
          </div>
          <el-tree
            ref="menuRef"
            class="menu-tree"
            :data="menuOptions"
            show-checkbox
            node-key="id"
            empty-text="加载中，或暂无菜单"
            :props="{ label: 'label', children: 'children' }"
            :check-strictly="!form.menuCheckStrictly"
          />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api/system/role'
import { eduErrorText } from '@/utils/request'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const selected = ref([])
const formOpen = ref(false)
const formRef = ref()
const menuRef = ref()
const menuOptions = ref([])
const menuExpand = ref(false)
const menuNodeAll = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, roleName: '', roleKey: '', status: '' })
const form = reactive(emptyForm())

const rules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请输入权限字符', trigger: 'blur' }],
  roleSort: [{ required: true, message: '请输入显示顺序', trigger: 'change' }]
}

function emptyForm() {
  return {
    roleId: undefined,
    roleName: '',
    roleKey: '',
    roleSort: 0,
    status: '0',
    menuCheckStrictly: true,
    remark: '',
    menuIds: []
  }
}

function assignForm(target, source) {
  Object.keys(target).forEach(key => { target[key] = source[key] })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const body = await api.listRole({ ...query })
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
  query.roleName = ''
  query.roleKey = ''
  query.status = ''
  search()
}

function getMenuAllKeys(nodes, keys = []) {
  for (const node of nodes || []) {
    keys.push(node.id)
    getMenuAllKeys(node.children, keys)
  }
  return keys
}

function toggleExpand(expand) {
  const nodes = menuRef.value?.store?.nodesMap || {}
  Object.values(nodes).forEach(node => { node.expanded = expand })
}

function toggleCheckAll(checked) {
  if (!menuRef.value) return
  if (checked) menuRef.value.setCheckedKeys(getMenuAllKeys(menuOptions.value))
  else menuRef.value.setCheckedKeys([])
}

function checkedMenuIds() {
  const tree = menuRef.value
  if (!tree) return []
  if (form.menuCheckStrictly) {
    return [...tree.getCheckedKeys(), ...tree.getHalfCheckedKeys()]
  }
  return tree.getCheckedKeys()
}

async function openForm(row) {
  assignForm(form, emptyForm())
  menuExpand.value = false
  menuNodeAll.value = false
  try {
    if (row?.roleId) {
      const detail = await api.getRole(row.roleId)
      const menuInfo = await api.roleMenuTreeselect(row.roleId)
      menuOptions.value = Array.isArray(menuInfo.menus) ? menuInfo.menus : []
      assignForm(form, {
        roleId: detail.roleId,
        roleName: detail.roleName,
        roleKey: detail.roleKey,
        roleSort: detail.roleSort ?? 0,
        status: detail.status || '0',
        menuCheckStrictly: detail.menuCheckStrictly !== false,
        remark: detail.remark || '',
        menuIds: []
      })
      formOpen.value = true
      await nextTick()
      menuRef.value?.setCheckedKeys(Array.isArray(menuInfo.checkedKeys) ? menuInfo.checkedKeys : [])
    } else {
      menuOptions.value = await api.menuTreeselect()
      formOpen.value = true
      await nextTick()
      menuRef.value?.setCheckedKeys([])
    }
  } catch (err) {
    ElMessage.error(eduErrorText(err))
  }
}

async function submit() {
  if (!await formRef.value.validate().catch(() => false)) return
  saving.value = true
  try {
    const payload = { ...form, menuIds: checkedMenuIds() }
    if (form.roleId) {
      await api.updateRole(payload)
      ElMessage.success('已保存')
    } else {
      await api.addRole(payload)
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

async function onStatus(row, enabled) {
  const status = enabled ? '0' : '1'
  try {
    await api.changeRoleStatus({ roleId: row.roleId, status })
    row.status = status
    ElMessage.success(status === '0' ? '已启用' : '已停用')
  } catch (err) {
    ElMessage.error(eduErrorText(err))
  }
}

async function remove(row) {
  await removeIds([row.roleId], `确认删除角色「${row.roleName}」？`)
}

async function removeBatch() {
  const ids = selected.value.map(item => item.roleId)
  await removeIds(ids, `确认删除选中的 ${ids.length} 个角色？`)
}

async function removeIds(ids, message) {
  try {
    await ElMessageBox.confirm(message, '提示', { type: 'warning' })
    await api.delRole(ids.join(','))
    ElMessage.success('已删除')
    await load()
  } catch (err) {
    if (err !== 'cancel' && err?.toString?.() !== 'cancel') ElMessage.error(eduErrorText(err))
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; }
.menu-tools { display: flex; gap: 16px; margin-bottom: 8px; }
.menu-tree {
  width: 100%;
  max-height: 280px;
  overflow: auto;
  border: 1px solid #e8edf4;
  border-radius: 8px;
  padding: 8px;
}
</style>
