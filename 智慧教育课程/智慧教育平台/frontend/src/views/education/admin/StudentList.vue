<template>
  <div class="app-container group2-page">
    <el-row :gutter="16">
      <el-col :span="5">
        <DirectoryOrgTree @select="selectDept" />
      </el-col>
      <el-col :span="19">
        <el-form :inline="true" :model="queryParams" @submit.prevent="handleQuery">
          <el-form-item label="学生">
            <el-input v-model="queryParams.keyword" placeholder="学号 / 姓名" clearable style="width: 240px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit">搜索</el-button>
            <el-button @click="resetQuery">重置</el-button>
          </el-form-item>
          <el-form-item v-if="queryParams.schoolId"><el-tag>首页所选学校</el-tag></el-form-item>
          <el-form-item v-if="queryParams.unassigned"><el-tag type="warning">仅未分班</el-tag></el-form-item>
        </el-form>

        <el-alert v-if="errorText" :title="errorText" type="warning" show-icon :closable="false" class="mb8" />
        <el-table v-loading="loading" :data="list" row-key="userId" border>
          <el-table-column label="序号" width="64" align="center">
            <template #default="{ $index }">{{ (queryParams.pageNum - 1) * queryParams.pageSize + $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="学号" prop="userName" min-width="135" show-overflow-tooltip />
          <el-table-column label="姓名" prop="nickName" min-width="100" show-overflow-tooltip />
          <el-table-column label="班级" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ classLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="手机号" min-width="126">
            <template #default="{ row }">{{ row.phonenumber || '—' }}</template>
          </el-table-column>
          <el-table-column label="邮箱" min-width="165" show-overflow-tooltip>
            <template #default="{ row }">{{ row.email || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="82" align="center">
            <template #default="{ row }">
              <el-tag :type="isEnabled(row) ? 'success' : 'info'" size="small">{{ statusLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="166">
            <template #default="{ row }">{{ row.createTime || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="124" fixed="right">
            <template #default="{ row }">
              <template v-if="canOperate(row)">
                <el-button link type="primary" @click="openEdit(row)">修改</el-button>
                <el-button link type="danger" :disabled="!isEnabled(row)" @click="openDisable(row)">停用</el-button>
              </template>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <template #empty>没有符合条件的学生</template>
        </el-table>

        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </el-col>
    </el-row>

    <el-dialog
      v-model="edit.open"
      title="编辑学生"
      width="min(660px, 94vw)"
      append-to-body
      :close-on-click-modal="!edit.saving"
      :close-on-press-escape="!edit.saving"
      :show-close="!edit.saving"
    >
      <el-alert v-if="edit.error" :title="edit.error" type="warning" :closable="false" class="mb8" />
      <el-form v-if="edit.data" :model="edit.data" label-width="92px" @submit.prevent="saveStudent">
        <el-form-item label="学号"><el-input :model-value="edit.data.userName" readonly /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="edit.data.nickName" maxlength="30" /></el-form-item>
        <el-form-item label="角色"><el-input model-value="学生" readonly /></el-form-item>
        <el-form-item label="所属班级">
          <StudentClassSelect v-model="edit.data.deptId" :options="classOptions" :loading="edit.loading" />
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model="edit.data.phonenumber" maxlength="11" inputmode="tel" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="edit.data.email" maxlength="50" inputmode="email" /></el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="edit.data.sex">
            <el-radio-button value="0">男</el-radio-button>
            <el-radio-button value="1">女</el-radio-button>
            <el-radio-button value="2">未填写</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="账号状态">
          <el-tag :type="isEnabled(edit.data) ? 'success' : 'info'">{{ statusLabel(edit.data) }}</el-tag>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="edit.data.remark" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="edit.saving" @click="edit.open = false">取消</el-button>
        <el-button type="primary" :loading="edit.saving" @click="saveStudent">保存</el-button>
      </template>
    </el-dialog>

    <ConfirmDialog
      v-if="disable.target"
      title="停用学生账号"
      :text="`确定停用 ${disable.target.nickName}（${disable.target.userName}）的账号吗？停用后该学生将无法登录。`"
      :busy="disable.saving"
      :error="disable.error"
      ok-text="停用"
      danger
      @close="disable.target = null"
      @confirm="confirmDisable"
    />
  </div>
</template>

<script setup name="EduStudentList">
import { computed, ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { disableStudentAccount, listStudentAccounts, updateStudentAccount } from '@/api/education/directory'
import { eduErrorText } from '@/api/education/http'
import { api } from '@/api/education/client.js'
import { hasPermission, session } from '@/education/session.js'
import { studentClassOption } from '@/utils/education/studentClass.js'
import ConfirmDialog from '@/components/education/ConfirmDialog.vue'
import DirectoryOrgTree from '@/components/education/DirectoryOrgTree.vue'
import StudentClassSelect from '@/components/education/StudentClassSelect.vue'

const loading = ref(false)
const errorText = ref('')
const list = ref([])
const total = ref(0)
const route = useRoute()
const router = useRouter()
const { schoolId, unassigned } = route.query
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  deptId: null,
  schoolId: typeof schoolId === 'string' && /^[1-9]\d{0,18}$/.test(schoolId) ? schoolId : undefined,
  unassigned: unassigned === 'true' || undefined
})
let latestListRequest = 0

const edit = reactive({ open: false, loading: false, saving: false, error: '', data: null, originalClass: null })
const disable = reactive({ target: null, saving: false, error: '' })
const eligibleClasses = ref([])
const classOptions = computed(() => {
  if (!edit.originalClass || eligibleClasses.value.some(item => item.value === edit.originalClass.value)) {
    return eligibleClasses.value
  }
  return [edit.originalClass, ...eligibleClasses.value]
})

function classLabel(row) {
  if (row.dept?.deptType !== 'CLASS') return '未分班'
  return queryParams.unassigned ? `${row.dept.deptName}（无有效归属）` : row.dept.deptName
}
const isEnabled = row => String(row.status) === '0'
const statusLabel = row => isEnabled(row) ? '正常' : '停用'

function canOperate(row) {
  return hasPermission('system:user:edit')
    && String(row.userId) !== '1'
    && String(row.userId) !== String(session.user?.userId)
}

async function getList() {
  const request = ++latestListRequest
  loading.value = true
  errorText.value = ''
  try {
    const res = await listStudentAccounts(queryParams)
    if (request !== latestListRequest) return
    list.value = res.rows || []
    total.value = Number(res.total) || 0
  } catch (error) {
    if (request !== latestListRequest) return
    list.value = []
    total.value = 0
    errorText.value = eduErrorText(error)
  } finally {
    if (request === latestListRequest) loading.value = false
  }
}

function selectDept(deptId) {
  queryParams.deptId = deptId
  handleQuery()
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  if (queryParams.schoolId || queryParams.unassigned) {
    const nextQuery = { ...route.query }
    delete nextQuery.schoolId
    delete nextQuery.unassigned
    return router.replace({ query: nextQuery })
  }
  queryParams.keyword = ''
  handleQuery()
}

async function openEdit(row) {
  if (!canOperate(row)) return
  edit.error = ''
  edit.loading = false
  edit.data = {
    userId: row.userId,
    userName: row.userName,
    nickName: row.nickName || '',
    deptId: row.deptId == null ? '' : String(row.deptId),
    phonenumber: row.phonenumber || '',
    email: row.email || '',
    sex: ['0', '1', '2'].includes(String(row.sex)) ? String(row.sex) : '2',
    status: row.status,
    remark: row.remark || ''
  }
  const originalName = classLabel(row)
  edit.originalClass = { value: edit.data.deptId, name: originalName, path: originalName, original: true }
  eligibleClasses.value = []
  edit.open = true
  if (!hasPermission('system:dept:list')) return
  edit.loading = true
  const current = edit.data
  try {
    const { data } = await api.get('/system/dept/list')
    if (current !== edit.data || !edit.open) return
    const depts = Array.isArray(data) ? data : []
    const byId = new Map(depts.map(dept => [String(dept.deptId), dept]))
    eligibleClasses.value = depts.map(dept => studentClassOption(dept, byId)).filter(Boolean)
  } catch (error) {
    if (current === edit.data && edit.open) edit.error = eduErrorText(error)
  } finally {
    if (current === edit.data) edit.loading = false
  }
}

async function saveStudent() {
  if (edit.saving || !edit.data) return
  const { userId, nickName, deptId, phonenumber, email, sex, remark } = edit.data
  edit.error = ''
  if (!nickName.trim()) {
    edit.error = '请填写姓名'
    return
  }
  if (phonenumber.trim() && !/^1[3-9][0-9]{9}$/.test(phonenumber.trim())) {
    edit.error = '手机号格式不正确'
    return
  }
  edit.saving = true
  try {
    await updateStudentAccount(userId, {
      nickName: nickName.trim(),
      deptId: deptId || null,
      phonenumber: phonenumber.trim(),
      email: email.trim(),
      sex,
      remark: remark.trim()
    })
    edit.open = false
    ElMessage.success('学生信息已保存')
    await getList()
  } catch (error) {
    edit.error = eduErrorText(error)
  } finally {
    edit.saving = false
  }
}

function openDisable(row) {
  if (!canOperate(row) || !isEnabled(row)) return
  disable.error = ''
  disable.target = row
}

async function confirmDisable() {
  if (disable.saving || !disable.target) return
  disable.saving = true
  disable.error = ''
  try {
    await disableStudentAccount(disable.target.userId)
    disable.target = null
    ElMessage.success('学生账号已停用')
    await getList()
  } catch (error) {
    disable.error = eduErrorText(error)
  } finally {
    disable.saving = false
  }
}

onMounted(getList)
</script>
