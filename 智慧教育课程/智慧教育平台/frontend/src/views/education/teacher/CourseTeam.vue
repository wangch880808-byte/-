<template>
  <el-card shadow="never" v-loading="loading">
    <div class="team-toolbar">
      <h3>教师团队</h3>
      <el-button v-if="canManage" type="primary" @click="openAdd">添加教师</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="team-error" />
    <el-table :data="rows" border>
      <el-table-column prop="nickName" label="姓名" min-width="110" />
      <el-table-column prop="userName" label="账号" min-width="130" />
      <el-table-column prop="deptName" label="所属组织" min-width="160" show-overflow-tooltip />
      <el-table-column label="团队身份" width="120">
        <template #default="{ row }">{{ row.teamRole === 'OWNER' ? '课程负责人' : '协作教师' }}</template>
      </el-table-column>
      <el-table-column label="学生管理" width="100">
        <template #default="{ row }">{{ enabled(row.canManageStudents) ? '允许' : '只读' }}</template>
      </el-table-column>
      <el-table-column label="名单导出" width="100">
        <template #default="{ row }">{{ enabled(row.canExport) ? '允许' : '关闭' }}</template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="150">
        <template #default="{ row }">
          <template v-if="row.teamRole !== 'OWNER'">
            <el-button link type="primary" :disabled="saving" @click="openEdit(row)">设置权限</el-button>
            <el-button link type="danger" :disabled="saving" @click="remove(row)">移除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialog" :title="editing ? '设置教师权限' : '添加教师'" width="min(640px, 94vw)"
    append-to-body :show-close="!saving" :close-on-click-modal="!saving" :close-on-press-escape="!saving">
    <template v-if="!editing">
      <el-form inline @submit.prevent="searchTeachers">
        <el-form-item><el-input v-model="keyword" placeholder="姓名或账号" clearable maxlength="100" /></el-form-item>
        <el-form-item><el-button native-type="submit" :loading="searching">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="candidates" v-loading="searching" border height="230" @row-click="choose">
        <el-table-column width="55">
          <template #default="{ row }"><el-radio :model-value="selectedId" :value="String(row.userId)"
            :aria-label="'选择' + row.nickName" @change="choose(row)">&nbsp;</el-radio></template>
        </el-table-column>
        <el-table-column label="姓名" prop="nickName" />
        <el-table-column label="账号" prop="userName" />
        <el-table-column label="所属组织" prop="deptName" show-overflow-tooltip />
      </el-table>
      <el-pagination v-if="total > pageSize" class="team-pages" v-model:current-page="page" :page-size="pageSize"
        :total="total" layout="prev, pager, next" :disabled="saving || searching" @current-change="loadCandidates" />
    </template>
    <p v-else>{{ editing.nickName }}（{{ editing.userName }}）</p>
    <div class="team-permissions">
      <el-checkbox v-model="manageStudents" :disabled="saving">允许管理教学班学生</el-checkbox>
      <el-checkbox v-model="canExport" :disabled="saving">允许导出学生名单</el-checkbox>
    </div>
    <el-alert v-if="dialogError" :title="dialogError" type="error" :closable="false" />
    <template #footer>
      <el-button :disabled="saving" @click="dialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!selectedId || searching" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCourseTeam, listCourseTeamCandidates, saveCourseTeacher, removeCourseTeacher } from '@/api/education/course'
import { eduErrorText } from '@/api/education/http'

const props = defineProps({ courseId: { type: [String, Number], required: true }, canManage: Boolean })
const rows = ref([]), loading = ref(false), error = ref('')
const dialog = ref(false), editing = ref(null), selectedId = ref('')
const manageStudents = ref(false), canExport = ref(false), saving = ref(false), dialogError = ref('')
const candidates = ref([]), keyword = ref(''), searching = ref(false), page = ref(1), total = ref(0)
const pageSize = 10
let requestVersion = 0
const enabled = value => value === true || Number(value) === 1

async function load() {
  loading.value = true
  error.value = ''
  try { rows.value = (await getCourseTeam(props.courseId)).data || [] }
  catch (cause) { error.value = eduErrorText(cause) }
  finally { loading.value = false }
}

function openAdd() {
  requestVersion++
  editing.value = null
  selectedId.value = ''
  manageStudents.value = false
  canExport.value = false
  dialogError.value = ''
  keyword.value = ''
  page.value = 1
  candidates.value = []
  dialog.value = true
  loadCandidates()
}

function openEdit(row) {
  editing.value = row
  selectedId.value = String(row.userId)
  manageStudents.value = enabled(row.canManageStudents)
  canExport.value = enabled(row.canExport)
  dialogError.value = ''
  dialog.value = true
}

function choose(row) { if (!saving.value) selectedId.value = String(row.userId) }
function searchTeachers() { page.value = 1; loadCandidates() }

async function loadCandidates() {
  const version = ++requestVersion
  searching.value = true
  dialogError.value = ''
  try {
    const result = await listCourseTeamCandidates(props.courseId, { keyword: keyword.value.trim(), pageNum: page.value, pageSize })
    if (version !== requestVersion) return
    candidates.value = result.rows || []
    total.value = Number(result.total) || 0
  } catch (cause) {
    if (version === requestVersion) dialogError.value = eduErrorText(cause)
  } finally { if (version === requestVersion) searching.value = false }
}

async function save() {
  if (saving.value || !selectedId.value) return
  saving.value = true
  dialogError.value = ''
  try {
    await saveCourseTeacher(props.courseId, { userId: selectedId.value,
      canManageStudents: manageStudents.value, canExport: canExport.value })
    dialog.value = false
    ElMessage.success('教师团队已保存')
    await load()
  } catch (cause) { dialogError.value = eduErrorText(cause) }
  finally { saving.value = false }
}

async function remove(row) {
  try { await ElMessageBox.confirm('移除“' + row.nickName + '”后，该教师将失去本课程的团队权限。', '移除教师', { type: 'warning' }) }
  catch { return }
  saving.value = true
  try { await removeCourseTeacher(props.courseId, row.userId); ElMessage.success('教师已移除'); await load() }
  catch (cause) { error.value = eduErrorText(cause) }
  finally { saving.value = false }
}

onMounted(load)
onUnmounted(() => { requestVersion++ })
</script>

<style scoped>
.team-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.team-toolbar h3 { margin: 0; font-size: 16px; }
.team-error { margin-bottom: 12px; }
.team-permissions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 14px; }
.team-pages { justify-content: flex-end; margin-top: 10px; }
</style>
