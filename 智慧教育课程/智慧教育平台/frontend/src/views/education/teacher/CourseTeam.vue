<template>
  <el-card shadow="never" v-loading="loading">
    <div class="team-toolbar">
      <h3>教师团队</h3>
      <el-button v-if="canManage" type="primary" @click="openAdd">添加教师</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="team-error" />
    <el-table :data="rows" border>
      <el-table-column label="角色" width="110">
        <template #default="{ row }">{{ roleLabel(row.teamRole) }}</template>
      </el-table-column>
      <el-table-column label="姓名" prop="nickName" min-width="110" />
      <el-table-column label="学号/工号" prop="userName" min-width="130" />
      <el-table-column label="院系" prop="deptName" min-width="160" show-overflow-tooltip />
      <el-table-column label="加入时间" prop="joinedAt" width="120" />
      <el-table-column v-if="canManage" label="操作" width="150">
        <template #default="{ row }">
          <template v-if="row.teamRole !== 'OWNER'">
            <el-button link type="primary" :disabled="saving" @click="openPermissions(row)">设置权限</el-button>
            <el-button link type="danger" :disabled="saving" @click="remove(row)">移除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialog" title="添加教师" width="min(640px, 94vw)"
    append-to-body :show-close="!saving" :close-on-click-modal="!saving" :close-on-press-escape="!saving">
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
      <el-table-column label="学号/工号" prop="userName" />
      <el-table-column label="所属组织" prop="deptName" show-overflow-tooltip />
    </el-table>
    <el-pagination v-if="total > pageSize" class="team-pages" v-model:current-page="page" :page-size="pageSize"
      :total="total" layout="prev, pager, next" :disabled="saving || searching" @current-change="loadCandidates" />
    <el-alert v-if="dialogError" :title="dialogError" type="error" :closable="false" />
    <template #footer>
      <el-button :disabled="saving" @click="dialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!selectedId || searching" @click="confirmPick">确定</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="permOpen" title="权限设置" width="min(520px, 94vw)"
    append-to-body :show-close="!saving" :close-on-click-modal="!saving" :close-on-press-escape="!saving">
    <p class="perm-target">为教师 {{ permTargetName }} 设置本课程的管理权限</p>
    <div v-for="perm in permissionItems" :key="perm.key" class="perm-row">
      <el-checkbox :model-value="permForm[perm.key]" :disabled="saving" @update:model-value="v => permForm[perm.key] = v">
        {{ perm.label }}
      </el-checkbox>
    </div>
    <el-alert v-if="dialogError" :title="dialogError" type="error" :closable="false" />
    <template #footer>
      <el-checkbox :model-value="allChecked" :indeterminate="someChecked" :disabled="saving" @change="toggleAll">全选</el-checkbox>
      <el-button :disabled="saving" @click="permOpen = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCourseTeam, listCourseTeamCandidates, saveCourseTeacher, removeCourseTeacher } from '@/api/education/course'
import { eduErrorText } from '@/api/education/http'

const props = defineProps({ courseId: { type: [String, Number], required: true }, canManage: Boolean })
const rows = ref([]), loading = ref(false), error = ref('')
const dialog = ref(false), selectedId = ref('')
const permOpen = ref(false), saving = ref(false), dialogError = ref('')
const candidates = ref([]), keyword = ref(''), searching = ref(false), page = ref(1), total = ref(0)
const pageSize = 10
let requestVersion = 0
const enabled = value => value === true || Number(value) === 1
const roleLabel = value => value === 'OWNER' ? '课程创建者' : '教师'

const permissionItems = [
  { key: 'activity', label: '允许管理"活动"模块' },
  { key: 'knowledgeGraph', label: '允许编辑知识图谱' },
  { key: 'courseware', label: '允许管理"课件"模块' },
  { key: 'lessonPlan', label: '允许管理"教案"模块' },
  { key: 'outline', label: '允许编辑课程章节' },
  { key: 'quizAnswers', label: '章节详情页允许查看章节测验答案' },
  { key: 'chapterOpen', label: '允许设置章节开放状态' },
  { key: 'material', label: '允许管理"资料"模块' },
  { key: 'students', label: '允许管理教学班学生' },
  { key: 'export', label: '允许导出学生名单' }
]
const permForm = reactive({})
const permTargetName = ref('')

const allChecked = computed(() => permissionItems.every(item => !!permForm[item.key]))
const someChecked = computed(() => permissionItems.some(item => !!permForm[item.key]))

async function load() {
  loading.value = true
  error.value = ''
  try { rows.value = (await getCourseTeam(props.courseId)).data || [] }
  catch (cause) { error.value = eduErrorText(cause) }
  finally { loading.value = false }
}

function openAdd() {
  requestVersion++
  selectedId.value = ''
  dialogError.value = ''
  keyword.value = ''
  page.value = 1
  candidates.value = []
  dialog.value = true
  loadCandidates()
}

function loadForm(row) {
  Object.keys(permForm).forEach(key => delete permForm[key])
  const perms = String(row?.permissions || '').split(',').filter(Boolean)
  for (const item of permissionItems) {
    if (item.key === 'students') permForm[item.key] = row == null || enabled(row.canManageStudents)
    else if (item.key === 'export') permForm[item.key] = row == null || enabled(row.canExport)
    else permForm[item.key] = row == null ? true : perms.includes(item.key)
  }
  permTargetName.value = row ? `${row.nickName || ''}（${row.userName}）` : ''
}

function openPermissions(row) {
  loadForm(row)
  dialogError.value = ''
  permOpen.value = true
}

function confirmPick() {
  const row = candidates.value.find(item => String(item.userId) === selectedId.value)
  if (!row) return
  loadForm(null)
  permTargetName.value = `${row.nickName || ''}（${row.userName}）`
  dialog.value = false
  dialogError.value = ''
  permOpen.value = true
}

function toggleAll(value) {
  for (const item of permissionItems) permForm[item.key] = !!value
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
    const perms = permissionItems.filter(item => item.key !== 'students' && item.key !== 'export' && permForm[item.key]).map(item => item.key)
    await saveCourseTeacher(props.courseId, { userId: selectedId.value,
      canManageStudents: !!permForm.students, canExport: !!permForm.export, permissions: perms.join(',') })
    permOpen.value = false
    ElMessage.success('教师权限已保存')
    await load()
  } catch (cause) { dialogError.value = eduErrorText(cause) }
  finally { saving.value = false }
}

async function remove(row) {
  try { await ElMessageBox.confirm('移除"' + row.nickName + '"后，该教师将失去本课程的团队权限。', '移除教师', { type: 'warning' }) }
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
.team-pages { justify-content: flex-end; margin-top: 10px; }
.perm-target { margin: 0 0 14px; color: #5b6b80; font-size: 14px; }
.perm-row { padding: 6px 0; }
</style>
