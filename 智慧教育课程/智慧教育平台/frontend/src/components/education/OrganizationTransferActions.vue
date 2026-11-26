<script setup>
import { computed, onMounted, onUnmounted, reactive } from 'vue'
import { Download, Upload } from '@element-plus/icons-vue'
import { api } from '@/api/education/client.js'
import { hasPermission } from '@/education/session.js'
import { fetchExcel, plainImportMessage, saveExcel, validateExcel } from '@/utils/education/excel.js'
import AppModal from '@/components/education/AppModal.vue'

const props = defineProps({
  filters: { type: Object, default: () => ({}) },
  openImportOnMount: Boolean
})
const emit = defineEmits(['imported'])
const canExport = computed(() => hasPermission('system:dept:list'))
const canImport = computed(() => canExport.value && hasPermission('system:dept:add'))
const canUpdate = computed(() => hasPermission('system:dept:edit'))
const state = reactive({
  open: false, busy: false, downloading: '', file: null, updateSupport: false,
  error: '', result: '', done: false
})
const controller = new AbortController()
let active = true
onUnmounted(() => { active = false; controller.abort() })

function openImport() {
  if (state.busy || state.downloading || !canImport.value) return
  Object.assign(state, { open: true, file: null, updateSupport: false, error: '', result: '', done: false })
}

onMounted(() => {
  if (props.openImportOnMount) openImport()
})

function choose(event) {
  state.file = event.target.files?.[0] || null
  state.error = validateExcel(state.file)
  state.result = ''
  state.done = false
}

async function download(action) {
  if (state.busy || state.downloading) return
  if (action === 'export' ? !canExport.value : !canImport.value) return
  state.downloading = action
  state.error = ''
  try {
    const blob = await fetchExcel('dept', action, action === 'export' ? props.filters : {}, controller.signal)
    if (!active) return
    if (!(blob instanceof Blob)) throw new Error('接口未返回 Excel 文件，请重试')
    saveExcel(blob, action === 'export' ? '组织数据.xlsx' : '组织导入模板.xlsx')
  } catch (error) {
    if (active && !error.cancelled && !error.forbidden && !error.unauthorized) {
      state.error = plainImportMessage(error.message || '文件下载失败')
    }
  } finally {
    if (active) state.downloading = ''
  }
}

async function submit() {
  if (state.busy || state.downloading) return
  if (state.done) { state.open = false; return }
  state.error = validateExcel(state.file)
  if (state.error || !canImport.value) return
  state.busy = true
  state.result = ''
  const body = new FormData()
  body.append('file', state.file)
  body.append('updateSupport', String(state.updateSupport && canUpdate.value))
  try {
    const data = await api.post('/system/dept/importData', body, { signal: controller.signal, timeout: 120000 })
    if (!active) return
    state.result = plainImportMessage(data.msg || '组织导入成功')
    state.done = true
    emit('imported')
  } catch (error) {
    if (active && !error.cancelled && !error.forbidden && !error.unauthorized) {
      state.error = plainImportMessage(error.message || '导入失败，请检查文件后重试')
    }
  } finally {
    if (active) state.busy = false
  }
}
</script>

<template>
  <div class="org-transfer">
    <button v-if="canExport" class="ui-btn" type="button" :disabled="state.busy || !!state.downloading"
      title="按当前名称和状态筛选条件导出，不受展开或收起影响" @click="download('export')">
      <Download class="transfer-icon" aria-hidden="true" />{{ state.downloading === 'export' ? '导出中…' : '导出组织' }}
    </button>
    <button v-if="canImport" class="ui-btn" type="button" :disabled="state.busy || !!state.downloading" @click="openImport">
      <Upload class="transfer-icon" aria-hidden="true" />导入组织
    </button>
    <span v-if="state.error && !state.open" class="transfer-error" role="alert">{{ state.error }}</span>

    <AppModal v-if="state.open" title="导入组织" wide :busy="state.busy || !!state.downloading"
      :ok-text="state.done ? '完成' : '开始导入'" @close="state.open = false" @confirm="submit">
      <div class="import-help">
        <p>请下载模板。学校、学院和班级分别填写一行，「组织类型」分别填学校、学院和班级。</p>
        <p>组织路径用 / 分隔层级，例如「演示学校/人工智能学院/2026级1班」。班级可放在学院或专业下面，导入后会显示在组织树中。</p>
        <p>上级组织须已存在，或在本次文件中一并填写，行的先后顺序不影响上下级关系。支持 .xls / .xlsx，每次最多 1000 条、文件不超过 10MB。</p>
        <p>显示顺序填非负整数，组织状态填「开启」或「停用」。原六列模板仍可导入，新增行政班时请填写「组织类型」。</p>
      </div>
      <button class="ui-btn template-button" type="button" :disabled="state.busy || !!state.downloading" @click="download('importTemplate')">
        <Download class="transfer-icon" aria-hidden="true" />{{ state.downloading === 'importTemplate' ? '下载中…' : '下载组织导入模板' }}
      </button>
      <p class="file-picker">
        <label for="organization-import-file">选择 Excel 文件</label>
        <input id="organization-import-file" type="file" accept=".xls,.xlsx" :disabled="state.busy || !!state.downloading" @change="choose" />
        <p>班级行可填写所属年级、教育层次、学制和备注，导入后可在班级管理中查看。</p>
      </p>
      <label v-if="canUpdate" class="update-option">
        <input v-model="state.updateSupport" type="checkbox" :disabled="state.busy || !!state.downloading || state.done" />
        更新组织路径相同的已有组织
      </label>
      <p class="import-help">默认只新增，跳过已存在的组织。{{ canUpdate ? '勾选更新后，会修改同路径组织的资料，留空的组织类型沿用原值。' : '' }}任意一条校验失败时整批撤销，并显示错误原因。</p>
      <p v-if="state.updateSupport" class="import-update-warning">更新时，空白负责人、联系电话和邮箱会清空原值；空白显示顺序按 0、空白组织状态按开启处理。建议先导出，保留需要沿用的数据后再修改导入。</p>
      <pre v-if="state.error" class="transfer-result transfer-error" role="alert">{{ state.error }}</pre>
      <pre v-if="state.result" class="transfer-result transfer-success" role="status">{{ state.result }}</pre>
    </AppModal>
  </div>
</template>

<style scoped>
.org-transfer { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.org-transfer > .ui-btn, .template-button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; }
.org-transfer > .ui-btn { box-shadow: 0 2px 0 #eff0f2; }
.transfer-icon { width: 15px; height: 15px; flex: 0 0 15px; }
.import-help { color: #606773; line-height: 1.75; }
.import-help p:first-child { margin-top: 0; }
.import-update-warning { padding: 10px 12px; background: #fdf6ec; color: #8a5b16; line-height: 1.75; border-radius: 4px; }
.file-picker { display: flex; flex-direction: column; gap: 8px; }
.file-picker input { max-width: 100%; }
.update-option { display: flex; align-items: center; gap: 7px; }
.transfer-result { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; line-height: 1.65; max-height: 240px; overflow: auto; }
.transfer-error { color: var(--danger); }
.transfer-success { padding: 10px 12px; background: #f0f9eb; color: #35751d; border-radius: 4px; }
</style>
