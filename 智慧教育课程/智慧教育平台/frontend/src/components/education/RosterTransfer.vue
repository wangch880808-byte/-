<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api/education/client'
import { saveExcel, validateExcel } from '@/utils/education/excel'

const props = defineProps({
  classId: { type: [String, Number], required: true },
  className: { type: String, required: true },
  canImport: Boolean,
  canExport: Boolean
})
const emit = defineEmits(['imported'])
const open = ref(false)
const addOpen = ref(false)
const studentAccount = ref('')
const addError = ref('')
const file = ref(null)
const busy = ref('')
const result = ref('')
const failure = ref('')

function endpoint(action) {
  return `/education/teaching-class/${props.classId}/members/${action}`
}

function openAdd() {
  studentAccount.value = ''
  addError.value = ''
  addOpen.value = true
}

async function addStudent() {
  if (busy.value) return
  if (!studentAccount.value.trim()) {
    addError.value = '请输入学生的学号或账号'
    return
  }
  busy.value = 'add'
  addError.value = ''
  try {
    const response = await api.post(endpoint('add'), { account: studentAccount.value.trim() }, { silent403: true })
    addOpen.value = false
    ElMessage.success(response.msg)
    emit('imported')
  } catch (error) {
    if (!error.cancelled && !error.unauthorized) addError.value = error.message
  } finally {
    busy.value = ''
  }
}

function openImport() {
  file.value = null
  result.value = ''
  failure.value = ''
  open.value = true
}

function chooseFile(event) {
  file.value = event.target.files?.[0] || null
  failure.value = file.value ? validateExcel(file.value) : ''
  result.value = ''
}

async function download(action) {
  if (busy.value) return
  busy.value = action
  failure.value = ''
  try {
    const blob = await api.post(endpoint(action), undefined, { responseType: 'blob', timeout: 120000, silent403: true })
    const label = action === 'export' ? `${props.className}学生名单` : '学生名单导入模板'
    saveExcel(blob, label + '.xlsx')
  } catch (error) {
    if (!error.cancelled && !error.unauthorized) ElMessage.error(error.message)
  } finally {
    busy.value = ''
  }
}

async function importRoster() {
  if (busy.value) return
  failure.value = validateExcel(file.value)
  if (failure.value) return
  busy.value = 'import'
  const data = new FormData()
  data.append('file', file.value)
  try {
    const response = await api.post(endpoint('import'), data, { timeout: 120000, silent403: true })
    result.value = response.msg
    emit('imported')
  } catch (error) {
    if (!error.cancelled && !error.unauthorized) failure.value = error.message
  } finally {
    busy.value = ''
  }
}
</script>

<template>
  <div v-if="canImport || canExport" class="roster-actions">
    <el-button v-if="canImport" type="primary" :disabled="!!busy" @click="openAdd">添加学生</el-button>
    <el-button v-if="canImport" :disabled="!!busy" @click="openImport">批量导入学生</el-button>
    <el-button v-if="canExport" :loading="busy === 'export'" :disabled="!!busy" @click="download('export')">导出学生名单</el-button>
  </div>
  <el-dialog v-model="addOpen" title="添加学生" width="min(460px, 92vw)" append-to-body
    :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy">
    <el-input v-model="studentAccount" maxlength="30" aria-label="学生学号或账号"
      placeholder="输入本组织已有学生的学号或账号" :disabled="!!busy" @keyup.enter="addStudent" />
    <el-alert v-if="addError" :title="addError" type="error" :closable="false" show-icon />
    <template #footer>
      <el-button :disabled="!!busy" @click="addOpen = false">取消</el-button>
      <el-button type="primary" :loading="busy === 'add'" @click="addStudent">添加到本班</el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="open" :title="`${className} · 批量导入学生`" width="min(540px, 92vw)" append-to-body destroy-on-close
    :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy">
    <p class="roster-hint">请下载模板，按文本格式填写已有学生的学号或账号。每批最多 1000 条，已在班的学生会跳过。</p>
    <el-button :disabled="!!busy" :loading="busy === 'template'" @click="download('template')">下载导入模板</el-button>
    <p><input type="file" aria-label="学生名单 Excel 文件" accept=".xls,.xlsx" :disabled="!!busy || !!result" @change="chooseFile" /></p>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon />
    <el-alert v-if="result" :title="result" type="success" :closable="false" show-icon />
    <template #footer>
      <el-button :disabled="!!busy" @click="open = false">{{ result ? '完成' : '取消' }}</el-button>
      <el-button v-if="!result" type="primary" :disabled="!!busy" :loading="busy === 'import'" @click="importRoster">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.roster-actions { display: flex; gap: 10px; flex-wrap: wrap; margin: 12px 0; }
.roster-actions .el-button { margin-left: 0; }
.roster-hint { color: var(--muted); margin: 0 0 12px; line-height: 1.6; }
</style>
