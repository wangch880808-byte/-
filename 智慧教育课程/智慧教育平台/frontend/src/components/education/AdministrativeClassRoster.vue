<template>
  <el-dialog
    :model-value="true" :title="'学生管理：' + classroom.deptName"
    width="min(1440px, 96vw)" :close-on-click-modal="false" :close-on-press-escape="!busy"
    :show-close="!busy" @update:model-value="value => { if (!value) emit('close') }"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb8" />
    <div class="roster">
      <el-card v-for="side in panels" :key="side.title" shadow="never" class="roster-panel">
        <template #header>
          <div class="panel-header">
            <strong>{{ side.title }}</strong>
            <span>共 {{ side.total }} 人</span>
          </div>
        </template>
        <el-form :inline="true" class="roster-filters" @submit.prevent="search(side)">
          <el-input v-model="side.query.keyword" placeholder="学号、姓名或手机号" clearable aria-label="搜索学生" />
          <template v-if="!side.members">
            <el-select v-model="side.query.orgId" clearable filterable placeholder="全部组织" aria-label="归属组织">
              <el-option v-for="item in candidateOrganizations" :key="item.id" :label="item.label" :value="item.id" />
            </el-select>
            <el-input-number v-model="side.query.grade" :controls="false" :min="1900" :max="2100" :precision="0"
              placeholder="全部年级" aria-label="年级，按学号前四位匹配" />
          </template>
          <div class="filter-actions">
            <el-button @click="reset(side)">重置</el-button>
            <el-button type="primary" plain @click="search(side)">查询</el-button>
          </div>
        </el-form>
        <el-table v-loading="side.loading" :data="side.rows" row-key="userId" height="390"
          :empty-text="side.members ? '暂无在班学生' : '没有符合条件的学生'"
          @selection-change="selection => side.selection = selection">
          <el-table-column type="selection" width="40" />
          <el-table-column prop="userName" label="学生学号" min-width="114" show-overflow-tooltip />
          <el-table-column prop="nickName" label="姓名" min-width="75" show-overflow-tooltip />
          <el-table-column prop="phonenumber" label="手机号" min-width="105" show-overflow-tooltip />
          <el-table-column label="归属组织" min-width="112" show-overflow-tooltip>
            <template #default="{ row }">{{ (side.members ? row.dept?.parentName : row.dept?.deptName) || '—' }}</template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="side.total > side.query.pageSize" v-model:current-page="side.query.pageNum"
          class="roster-pages" :page-size="side.query.pageSize" :total="side.total" small layout="prev, pager, next"
          @current-change="load(side)" />
      </el-card>
      <div class="roster-actions">
        <el-button type="primary" plain :disabled="busy || candidates.loading || !activeClass || !candidates.selection.length"
          @click="move(candidates)">添加 →</el-button>
        <el-button :disabled="busy || members.loading || !members.selection.length" @click="move(members)">← 移除</el-button>
      </div>
    </div>
    <template #footer>
      <div class="roster-footer">
        <div>
          <el-button :disabled="busy" @click="download">导出花名册</el-button>
          <el-button :disabled="busy || !activeClass" @click="filePicker.click()">导入花名册</el-button>
          <input ref="filePicker" type="file" accept=".xls,.xlsx" hidden @change="upload" />
          <small class="import-hint">Excel 第一列填写已有学生的学号</small>
        </div>
        <el-button :disabled="busy" @click="emit('close')">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportClassRoster, importClassRoster, listClassRoster, moveClassStudents } from '@/api/education/administrativeClass'
import { saveExcel, validateExcel } from '@/utils/education/excel'

const props = defineProps({ classroom: { type: Object, required: true }, organizations: { type: Array, required: true } })
const emit = defineEmits(['close'])
const filePicker = ref()
const busy = ref(false)
const error = ref('')
const activeClass = computed(() => props.classroom.status === '0')
const candidateOrganizations = computed(() => {
  const parent = props.organizations.find(item => item.id === String(props.classroom.parentId))
  return parent ? props.organizations.filter(item => item.schoolId === parent.schoolId) : props.organizations
})

function panel(title, members) {
  return reactive({
    title, members, rows: [], selection: [], total: 0, loading: false, request: 0,
    query: { keyword: '', orgId: null, grade: undefined, pageNum: 1, pageSize: 20 }
  })
}
const candidates = panel('可添加的学生', false)
const members = panel('已在本班的学生', true)
const panels = [candidates, members]

async function load(side) {
  const request = ++side.request
  side.loading = true
  side.selection = []
  try {
    const response = await listClassRoster(props.classroom.deptId, { members: side.members, ...side.query })
    if (request !== side.request) return
    side.rows = response.rows
    side.total = response.total
  } catch (err) {
    if (request === side.request) error.value = err.message
  } finally {
    if (request === side.request) side.loading = false
  }
}

function search(side) {
  error.value = ''
  side.query.pageNum = 1
  return load(side)
}

function reset(side) {
  Object.assign(side.query, { keyword: '', orgId: null, grade: undefined })
  search(side)
}

async function refresh() {
  await Promise.all(panels.map(search))
}

async function move(side) {
  if (busy.value || !side.selection.length) return
  const ids = side.selection.map(row => row.userId)
  if (side.members) {
    try {
      await ElMessageBox.confirm('确定移除选中的 ' + ids.length + ' 名学生？学生的归属将调整到班级的上级组织。',
        '移除学生', { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' })
    } catch {
      return
    }
  }
  await perform(() => moveClassStudents(props.classroom.deptId, ids, !side.members))
}

async function perform(action) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const result = await action()
    ElMessage.success(result.msg)
    await refresh()
  } catch (err) {
    error.value = err.message
  } finally {
    busy.value = false
  }
}

async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  error.value = validateExcel(file)
  if (!error.value) await perform(() => importClassRoster(props.classroom.deptId, file))
}

async function download() {
  busy.value = true
  error.value = ''
  try {
    const blob = await exportClassRoster(props.classroom.deptId)
    if (!(blob instanceof Blob)) throw new Error('未收到 Excel 文件')
    if (blob.type.includes('json')) {
      const result = JSON.parse(await blob.text())
      throw new Error(result.msg || '导出失败')
    }
    saveExcel(blob, props.classroom.deptName + '-花名册.xlsx')
  } catch (err) {
    error.value = err.message
  } finally {
    busy.value = false
  }
}

onMounted(refresh)
</script>

<style scoped>
.roster { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); gap: 14px; }
.roster-panel { min-width: 0; }
.roster-panel:nth-child(2) { grid-column: 3; grid-row: 1; }
.roster-actions { grid-column: 2; grid-row: 1; display: flex; flex-direction: column; justify-content: center; gap: 12px; }
.roster-actions .el-button { margin: 0; }
.panel-header, .roster-footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.roster-filters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-bottom: 12px; }
.roster-panel:nth-child(2) .roster-filters { min-height: 72px; align-content: start; }
.roster-filters :deep(.el-input-number) { width: 100%; }
.filter-actions { display: flex; justify-content: flex-end; gap: 8px; }
.filter-actions .el-button { margin: 0; }
.roster-pages { justify-content: flex-end; margin-top: 12px; }
.import-hint { margin-left: 10px; color: var(--el-text-color-secondary); }
@media (max-width: 900px) {
  .roster { grid-template-columns: minmax(0, 1fr); }
  .roster-panel:nth-child(2), .roster-actions { grid-area: auto; }
  .roster-actions { grid-row: 2; flex-direction: row; }
  .roster-footer { align-items: flex-end; }
  .import-hint { display: block; margin: 8px 0 0; }
}
</style>
