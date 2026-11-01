<template>
  <div class="app-container">
    <el-form ref="queryRef" :inline="true" :model="queryParams">
      <el-form-item label="班级">
        <el-input
          v-model="queryParams.keyword"
          placeholder="班级名称 / 编码"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="学期">
        <el-select v-model="queryParams.termId" placeholder="全部学期" clearable style="width: 200px">
          <el-option v-for="item in termOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery">搜索</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="3">
        <el-button type="primary" plain @click="handleAdd">新建教学班</el-button>
      </el-col>
    </el-row>

    <el-alert
      v-if="errorText"
      :title="errorText"
      type="warning"
      show-icon
      :closable="false"
      class="mb8"
    />

    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="教学班" min-width="200">
        <template #default="{ row }">
          <div class="edu-strong">{{ row.name }}</div>
          <div class="edu-mono">{{ row.code }}</div>
        </template>
      </el-table-column>
      <el-table-column label="课程" prop="courseName" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.courseName || '未绑定课程' }}</template>
      </el-table-column>
      <el-table-column label="学期" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.semesterName || row.termName || '—' }}</template>
      </el-table-column>
      <el-table-column label="任课教师" min-width="120">
        <template #default="{ row }">{{ row.teacherName || '未指定' }}</template>
      </el-table-column>
      <el-table-column label="学生数" width="100" align="center">
        <template #default="{ row }">{{ row.studentCount || 0 }} 人</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)">{{ labelOf(statusOptions, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openMembers(row)">成员</el-button>
          <el-button link type="primary" @click="openTeachers(row)">任课教师</el-button>
          <el-button link type="primary" @click="handleUpdate(row)">编辑</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="edu-empty">
          <p>还没有教学班</p>
          <p class="edu-empty-hint">教学班是「课程 × 学期 × 一批学生」的组合，建班后才能绑定课程。</p>
        </div>
      </template>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="open" :title="form.id ? '编辑教学班' : '新建教学班'" width="560px" append-to-body @open="clearValidate">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="form.name" placeholder="如 2026 秋-软件工程 1 班" maxlength="64" />
        </el-form-item>
        <el-form-item label="班级编码" prop="code">
          <el-input v-model="form.code" placeholder="同一学期内唯一，如 SE-2026-01" maxlength="32" />
        </el-form-item>
        <el-form-item label="所属学期" prop="termId">
          <el-select v-model="form.termId" placeholder="请选择学期" style="width: 100%">
            <el-option v-for="item in termOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="正式课程">
          <el-select v-model="form.officialCourseId" placeholder="可暂不绑定" clearable filterable style="width: 100%">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级类型" prop="classKind">
          <el-select v-model="form.classKind" style="width: 100%">
            <el-option v-for="item in classKindOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <div class="edu-hint">草稿不对学生开放；归档后只读。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 成员 -->
    <el-drawer v-model="memberDrawer" :title="currentClass.name + ' · 成员管理'" size="820px" destroy-on-close>
      <div class="edu-drawer-bar">
        <el-button type="primary" plain @click="openAddMember">添加学生</el-button>
        <span class="edu-hint">退班只标记退出时间，历史记录保留。</span>
      </div>
      <el-table v-loading="memberLoading" :data="members" border size="small">
        <el-table-column label="学号" prop="studentNo" width="140" />
        <el-table-column label="姓名" prop="studentName" min-width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '在班' : '已退出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入班" prop="joinedAt" width="120" />
        <el-table-column label="退出" width="120">
          <template #default="{ row }">{{ row.leftAt || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="handleRemoveMember(row)">
              移出
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="edu-empty"><p>本班还没有学生</p></div>
        </template>
      </el-table>
    </el-drawer>

    <!-- 任课教师授权 -->
    <el-drawer v-model="teacherDrawer" :title="currentClass.name + ' · 任课教师'" size="900px" destroy-on-close>
      <div class="edu-drawer-bar">
        <el-button type="primary" plain @click="openAddTeacher">授权教师</el-button>
        <span class="edu-hint">撤权写有效截止时间，不删除历史授权。</span>
      </div>
      <el-table v-loading="teacherLoading" :data="teachers" border size="small">
        <el-table-column label="教师" min-width="140">
          <template #default="{ row }">{{ row.nickName }}（{{ row.userName }}）</template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">{{ labelOf(teacherRoleOptions, row.teacherRole) }}</template>
        </el-table-column>
        <el-table-column label="发布" width="80" align="center">
          <template #default="{ row }">{{ row.canPublish ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="评分" width="80" align="center">
          <template #default="{ row }">{{ row.canGrade ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="导出" width="80" align="center">
          <template #default="{ row }">{{ row.canExport ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="有效期" width="200">
          <template #default="{ row }">{{ row.validFrom || '—' }} 至 {{ row.validTo || '长期' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="!row.validTo" link type="danger" @click="handleRemoveTeacher(row)">撤权</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="edu-empty"><p>还没有指定任课教师</p></div>
        </template>
      </el-table>
    </el-drawer>

    <!-- 添加学生 -->
    <el-dialog v-model="memberFormOpen" title="添加学生" width="460px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="学生">
          <el-select
            v-model="memberForm.studentId"
            filterable
            remote
            :remote-method="searchStudents"
            :loading="studentLoading"
            placeholder="输入学号或姓名搜索"
            style="width: 100%"
          >
            <el-option
              v-for="item in studentOptions"
              :key="item.userId"
              :label="item.nickName + '（' + item.userName + '）'"
              :value="item.userId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberFormOpen = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitMember">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 授权教师 -->
    <el-dialog v-model="teacherFormOpen" title="授权任课教师" width="520px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="教师">
          <el-select
            v-model="teacherForm.userId"
            filterable
            remote
            :remote-method="searchTeachers"
            :loading="teacherOptionLoading"
            placeholder="输入姓名或账号搜索"
            style="width: 100%"
          >
            <el-option
              v-for="item in teacherOptions"
              :key="item.userId"
              :label="item.nickName + '（' + item.userName + '）'"
              :value="item.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="teacherForm.teacherRole" style="width: 100%">
            <el-option v-for="item in teacherRoleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="权限">
          <el-checkbox v-model="teacherForm.canPublish">发布资源/任务</el-checkbox>
          <el-checkbox v-model="teacherForm.canGrade">批改与确认成绩</el-checkbox>
          <el-checkbox v-model="teacherForm.canExport">导出</el-checkbox>
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker
            v-model="teacherForm.validTo"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="留空为长期有效"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="teacherFormOpen = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTeacher">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="EduClassList">
import { ref, reactive, getCurrentInstance, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  listTeachingClass,
  addTeachingClass,
  updateTeachingClass,
  listClassMembers,
  addClassMember,
  removeClassMember,
  listClassTeachers,
  addClassTeacher,
  removeClassTeacher
} from '@/api/education/teachingClass'
import { listTerm } from '@/api/education/term'
import { listOfficialCourse, listStudent, listTeacher } from '@/api/education/directory'
import { eduErrorText } from '@/api/education/http'

const { proxy } = getCurrentInstance()

const statusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'ACTIVE', label: '有效' },
  { value: 'ARCHIVED', label: '已归档' }
]
const classKindOptions = [
  { value: 'FORMAL', label: '正式班' },
  { value: 'TRIAL', label: '试听班' }
]
const teacherRoleOptions = [
  { value: 'LEAD', label: '主讲' },
  { value: 'ASSIST', label: '助教' },
  { value: 'GUEST', label: '旁听' }
]

const loading = ref(false)
const submitting = ref(false)
const errorText = ref('')
const list = ref([])
const total = ref(0)

const termOptions = ref([])
const courseOptions = ref([])

const queryParams = reactive({ pageNum: 1, pageSize: 10, keyword: '', termId: null, status: '' })
const open = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  name: '',
  code: '',
  termId: null,
  officialCourseId: null,
  classKind: 'FORMAL',
  status: 'DRAFT'
})

const rules = {
  name: [{ required: true, message: '班级名称不能为空', trigger: 'blur' }],
  code: [{ required: true, message: '班级编码不能为空', trigger: 'blur' }],
  termId: [{ required: true, message: '所属学期不能为空', trigger: 'change' }],
  classKind: [{ required: true, message: '班级类型不能为空', trigger: 'change' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
}

function labelOf(options, value) {
  const hit = options.find((item) => item.value === value)
  return hit ? hit.label : value || '—'
}

function clearValidate() {
  formRef.value && formRef.value.clearValidate()
}

function tagType(status) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

async function loadOptions() {
  try {
    const termRes = await listTerm({ pageNum: 1, pageSize: 100 })
    termOptions.value = termRes.rows || []
  } catch (e) {
    termOptions.value = []
  }
  try {
    const courseRes = await listOfficialCourse({ pageNum: 1, pageSize: 200 })
    courseOptions.value = courseRes.rows || []
  } catch (e) {
    courseOptions.value = []
  }
}

async function getList() {
  loading.value = true
  errorText.value = ''
  try {
    const res = await listTeachingClass(queryParams)
    list.value = res.rows || []
    total.value = Number(res.total) || 0
  } catch (e) {
    list.value = []
    total.value = 0
    errorText.value = eduErrorText(e)
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  Object.assign(queryParams, { pageNum: 1, keyword: '', termId: null, status: '' })
  getList()
}

function handleAdd() {
  Object.assign(form, {
    id: null,
    name: '',
    code: '',
    termId: termOptions.value.length === 1 ? termOptions.value[0].id : null,
    officialCourseId: null,
    classKind: 'FORMAL',
    status: 'DRAFT'
  })
  open.value = true
}

function handleUpdate(row) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    code: row.code,
    termId: row.semesterId || row.termId,
    officialCourseId: row.courseId || null,
    classKind: row.classKind || 'FORMAL',
    status: row.status || 'DRAFT'
  })
  open.value = true
}

function submitForm() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        name: form.name.trim(),
        code: form.code.trim(),
        termId: form.termId,
        officialCourseId: form.officialCourseId || null,
        classKind: form.classKind,
        status: form.status
      }
      if (form.id) {
        await updateTeachingClass(form.id, payload)
        proxy.$modal.msgSuccess('已保存')
      } else {
        await addTeachingClass(payload)
        proxy.$modal.msgSuccess('教学班已创建')
      }
      open.value = false
      getList()
    } catch (e) {
      proxy.$modal.msgError(eduErrorText(e))
    } finally {
      submitting.value = false
    }
  })
}

/* ---------- 成员 ---------- */
const memberDrawer = ref(false)
const memberLoading = ref(false)
const members = ref([])
const memberFormOpen = ref(false)
const memberForm = reactive({ studentId: null })
const studentOptions = ref([])
const studentLoading = ref(false)
const currentClass = ref({ id: null, name: '' })

async function openMembers(row) {
  currentClass.value = { id: row.id, name: row.name }
  memberDrawer.value = true
  await loadMembers()
}

async function loadMembers() {
  memberLoading.value = true
  try {
    const res = await listClassMembers(currentClass.value.id)
    members.value = res.rows || []
  } catch (e) {
    members.value = []
    proxy.$modal.msgError(eduErrorText(e))
  } finally {
    memberLoading.value = false
  }
}

async function searchStudents(keyword) {
  if (!keyword) return
  studentLoading.value = true
  try {
    const res = await listStudent({ pageNum: 1, pageSize: 20, keyword })
    studentOptions.value = res.rows || []
  } catch (e) {
    studentOptions.value = []
  } finally {
    studentLoading.value = false
  }
}

function openAddMember() {
  memberForm.studentId = null
  studentOptions.value = []
  memberFormOpen.value = true
}

function submitMember() {
  if (!memberForm.studentId) {
    proxy.$modal.msgWarning('请选择学生')
    return
  }
  submitting.value = true
  addClassMember(currentClass.value.id, { studentId: memberForm.studentId })
    .then(() => {
      proxy.$modal.msgSuccess('已加入本班')
      memberFormOpen.value = false
      loadMembers()
      getList()
    })
    .catch((e) => proxy.$modal.msgError(eduErrorText(e)))
    .finally(() => {
      submitting.value = false
    })
}

function handleRemoveMember(row) {
  ElMessageBox.prompt('移出后该学生立即失去本班课程访问权。请填写原因（会记入审计）：', '移出学生', {
    confirmButtonText: '确定移出',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '原因不能为空',
    type: 'warning'
  })
    .then(({ value }) => removeClassMember(currentClass.value.id, row.studentId, value))
    .then(() => {
      proxy.$modal.msgSuccess('已移出')
      loadMembers()
      getList()
    })
    .catch((e) => {
      if (e !== 'cancel' && e !== 'close') proxy.$modal.msgError(eduErrorText(e))
    })
}

/* ---------- 任课教师 ---------- */
const teacherDrawer = ref(false)
const teacherLoading = ref(false)
const teachers = ref([])
const teacherFormOpen = ref(false)
const teacherForm = reactive({
  userId: null,
  teacherRole: 'LEAD',
  canPublish: true,
  canGrade: true,
  canExport: false,
  validTo: ''
})
const teacherOptions = ref([])
const teacherOptionLoading = ref(false)

async function openTeachers(row) {
  currentClass.value = { id: row.id, name: row.name }
  teacherDrawer.value = true
  await loadTeachers()
}

async function loadTeachers() {
  teacherLoading.value = true
  try {
    const res = await listClassTeachers(currentClass.value.id)
    teachers.value = res.rows || []
  } catch (e) {
    teachers.value = []
    proxy.$modal.msgError(eduErrorText(e))
  } finally {
    teacherLoading.value = false
  }
}

async function searchTeachers(keyword) {
  if (!keyword) return
  teacherOptionLoading.value = true
  try {
    const res = await listTeacher({ pageNum: 1, pageSize: 20, keyword })
    teacherOptions.value = res.rows || []
  } catch (e) {
    teacherOptions.value = []
  } finally {
    teacherOptionLoading.value = false
  }
}

function openAddTeacher() {
  Object.assign(teacherForm, {
    userId: null,
    teacherRole: 'LEAD',
    canPublish: true,
    canGrade: true,
    canExport: false,
    validTo: ''
  })
  teacherOptions.value = []
  teacherFormOpen.value = true
}

function submitTeacher() {
  if (!teacherForm.userId) {
    proxy.$modal.msgWarning('请选择教师')
    return
  }
  submitting.value = true
  addClassTeacher(currentClass.value.id, { ...teacherForm, validTo: teacherForm.validTo || null })
    .then(() => {
      proxy.$modal.msgSuccess('已授权')
      teacherFormOpen.value = false
      loadTeachers()
      getList()
    })
    .catch((e) => proxy.$modal.msgError(eduErrorText(e)))
    .finally(() => {
      submitting.value = false
    })
}

function handleRemoveTeacher(row) {
  proxy.$modal
    .confirm('撤销「' + row.nickName + '」在本班的任课授权？撤权后该教师下一次请求即失去访问权。')
    .then(() => removeClassTeacher(currentClass.value.id, row.userId))
    .then(() => {
      proxy.$modal.msgSuccess('已撤权')
      loadTeachers()
      getList()
    })
    .catch((e) => {
      if (e !== 'cancel' && e !== 'close') proxy.$modal.msgError(eduErrorText(e))
    })
}

onMounted(async () => {
  await loadOptions()
  getList()
})
</script>

<style scoped>
.edu-strong {
  font-weight: 600;
}
.edu-mono {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #909399;
}
.edu-hint {
  font-size: 12px;
  color: #909399;
}
.edu-empty {
  padding: 16px 0;
  color: #606266;
}
.edu-empty-hint {
  font-size: 12px;
  color: #909399;
}
.edu-drawer-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
</style>
