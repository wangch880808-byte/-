<template>
  <div class="app-container group2-page">
    <el-row :gutter="16">
      <el-col :xs="24" :sm="6" :lg="5">
        <DirectoryOrgTree through-major @select="selectOrg" />
      </el-col>
      <el-col :xs="24" :sm="18" :lg="19">
        <el-form :inline="true" @submit.prevent="search">
          <el-form-item label="班级">
            <el-input v-model="query.keyword" clearable placeholder="班级名称" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 130px">
              <el-option label="开启" value="0" />
              <el-option label="停用" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search">搜索</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
          <el-form-item v-if="schoolId && query.orgId === schoolId"><el-tag>首页所选学校</el-tag></el-form-item>
        </el-form>

        <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb8" />
        <el-table v-loading="loading" :data="rows" row-key="deptId" border empty-text="暂无班级">
          <el-table-column label="序号" width="62" align="center">
            <template #default="{ $index }">{{ (query.pageNum - 1) * query.pageSize + $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="班级名称" prop="deptName" min-width="165" show-overflow-tooltip />
          <el-table-column label="所属年级" width="100">
            <template #default="{ row }">{{ row.gradeYear ? row.gradeYear + '级' : '未设置' }}</template>
          </el-table-column>
          <el-table-column label="归属组织" prop="parentName" min-width="135" show-overflow-tooltip />
          <el-table-column label="教育层次" width="100">
            <template #default="{ row }">{{ row.educationLevel || '未设置' }}</template>
          </el-table-column>
          <el-table-column label="学制" width="84">
            <template #default="{ row }">{{ row.studyYears ? row.studyYears + '年' : '未设置' }}</template>
          </el-table-column>
          <el-table-column label="班级状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '开启' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="155">
            <template #default="{ row }">{{ parseTime(row.createTime) || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="148">
            <template #default="{ row }">
              <el-button v-if="canManage" link type="primary" @click="roster = row">学生管理</el-button>
              <el-button v-if="canEdit" link type="primary" @click="openEdit(row)">修改</el-button>
            </template>
          </el-table-column>
        </el-table>
        <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load" />
      </el-col>
    </el-row>

    <AdministrativeClassRoster
      v-if="roster" :key="roster.deptId" :classroom="roster" :organizations="organizations" @close="roster = null"
    />

    <el-dialog v-model="editOpen" title="修改班级" width="min(780px, 94vw)" :close-on-click-modal="!saving">
      <el-form ref="editor" :model="form" :rules="rules" label-width="94px" @submit.prevent="save">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="form.name" maxlength="30" />
        </el-form-item>
        <el-form-item label="所属年级" prop="gradeYear">
          <el-input-number v-model="form.gradeYear" :min="1900" :max="2100" :precision="0" placeholder="例如 2026" />
          <span class="field-unit">级</span>
        </el-form-item>
        <el-form-item label="归属组织" prop="parentId">
          <el-select v-model="form.parentId" filterable placeholder="请选择学院或专业">
            <el-option v-for="item in parents" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教育层次" prop="educationLevel">
          <el-select v-model="form.educationLevel" placeholder="请选择教育层次">
            <el-option v-for="level in levels" :key="level" :label="level" :value="level" />
          </el-select>
        </el-form-item>
        <el-form-item label="学制" prop="studyYears">
          <el-select v-model="form.studyYears" placeholder="请选择学制">
            <el-option v-for="years in 10" :key="years" :label="years + ' 年'" :value="years" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio-button value="0">开启</el-radio-button>
            <el-radio-button value="1">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <el-alert v-if="editError" :title="editError" type="error" :closable="false" />
      <template #footer>
        <el-button :disabled="saving" @click="editOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DirectoryOrgTree from '@/components/education/DirectoryOrgTree.vue'
import AdministrativeClassRoster from '@/components/education/AdministrativeClassRoster.vue'
import { listAdministrativeClasses, updateAdministrativeClass } from '@/api/education/administrativeClass'
import { getOrgTree } from '@/api/education/directory'
import { hasPermission } from '@/education/session'
import { parseTime } from '@/utils/ruoyi'

const route = useRoute()
const router = useRouter()
const requestedSchool = route.query.schoolId
const schoolId = typeof requestedSchool === 'string' && /^[1-9]\d{0,18}$/.test(requestedSchool) ? requestedSchool : null
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', orgId: schoolId, status: '' })
const canManage = computed(() => hasPermission('education:class:member'))
const canEdit = computed(() => hasPermission('education:class:edit'))
const loading = ref(false)
const error = ref('')
const rows = ref([])
const total = ref(0)
const organizations = ref([])
const roster = ref(null)
const editOpen = ref(false)
const saving = ref(false)
const editError = ref('')
const editor = ref()
const form = reactive({})
const levels = ['中专', '专科', '本科', '硕士', '博士', '其他']
const rules = {
  name: [{ required: true, whitespace: true, message: '请输入班级名称', trigger: 'blur' }],
  parentId: [{ required: true, message: '请选择归属组织', trigger: 'change' }],
  status: [{ required: true, message: '请选择班级状态', trigger: 'change' }]
}
const parents = computed(() => {
  const current = organizations.value.find(item => item.id === form.originalParentId)
  const eligible = organizations.value.filter(item => ['COLLEGE', 'MAJOR'].includes(item.type)
    && (!current || item.schoolId === current.schoolId))
  if (!eligible.some(item => item.id === form.originalParentId)) {
    eligible.unshift({ id: form.originalParentId, label: form.originalParentName })
  }
  return eligible
})
let listRequest = 0

async function load() {
  const request = ++listRequest
  loading.value = true
  error.value = ''
  try {
    const result = await listAdministrativeClasses(query)
    if (request !== listRequest) return
    rows.value = result.rows
    total.value = result.total
  } catch (err) {
    if (request === listRequest) error.value = err.message
  } finally {
    if (request === listRequest) loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function reset() {
  if (schoolId) {
    const nextQuery = { ...route.query }
    delete nextQuery.schoolId
    return router.replace({ query: nextQuery })
  }
  Object.assign(query, { keyword: '', status: '' })
  search()
}

function selectOrg(id) {
  query.orgId = id
  search()
}

async function loadOrganizations() {
  try {
    const response = await getOrgTree()
    const source = Array.isArray(response) ? response : response.data || []
    const byId = new Map(source.map(item => [String(item.id), item]))
    organizations.value = source.filter(item => item.deptType !== 'CLASS').map(item => {
      const path = [item.name]
      let ancestor = item
      while (path.length < 5 && byId.has(String(ancestor.parentId))) {
        ancestor = byId.get(String(ancestor.parentId))
        path.unshift(ancestor.name)
      }
      const type = item.deptType || (path.length === 2 && /(?:学院|系)$/.test(item.name.trim()) ? 'COLLEGE' : null)
      return { id: String(item.id), schoolId: String(ancestor.id), label: path.join(' / '), type }
    })
  } catch {
    organizations.value = []
  }
}

function openEdit(row) {
  const parentId = String(row.parentId)
  Object.assign(form, {
    id: row.deptId, name: row.deptName, originalParentId: parentId,
    originalParentName: row.parentName, parentId,
    gradeYear: row.gradeYear ?? undefined, educationLevel: row.educationLevel ?? undefined,
    studyYears: row.studyYears ?? undefined, status: row.status === '0' ? '0' : '1', remark: row.classRemark || ''
  })
  editError.value = ''
  editOpen.value = true
  editor.value?.clearValidate()
}

async function save() {
  if (saving.value || !await editor.value.validate().catch(() => false)) return
  saving.value = true
  editError.value = ''
  const { id, originalParentId, originalParentName, ...changes } = form
  try {
    await updateAdministrativeClass(id, { ...changes, name: changes.name.trim() })
    ElMessage.success('班级信息已保存')
    editOpen.value = false
    search()
  } catch (err) {
    editError.value = err.message
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  load()
  loadOrganizations()
})
</script>

<style scoped>
.field-unit { margin-left: 9px; }
</style>
