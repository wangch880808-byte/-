<script setup>
import { computed, onUnmounted, reactive } from 'vue'
import { api } from '@/api/education/client.js'
import { hasPermission } from '@/education/session.js'
import { fetchExcel, plainImportMessage, saveExcel, validateExcel } from '@/utils/education/excel.js'
import AppModal from '@/components/education/AppModal.vue'
const props = defineProps({ resource: { type: String, required: true }, filters: { type: Object, default: () => ({}) } })
const emit = defineEmits(['imported'])
const label = computed(() => props.resource === 'user' ? '用户' : '角色')
const canImport = computed(() => hasPermission(props.resource === 'role' ? 'system:role:add' : 'system:user:import'))
const canExport = computed(() => hasPermission('system:' + props.resource + ':export'))
const state = reactive({ open: false, busy: false, downloading: false, file: null, error: '', result: '', done: false })
const controller = new AbortController()
let active = true
onUnmounted(() => { active = false; controller.abort() })
function openImport() {
  Object.assign(state, { open: true, file: null, error: '', result: '', done: false })
}
function choose(event) {
  state.file = event.target.files?.[0] || null
  state.error = validateExcel(state.file); state.result = ''; state.done = false
}
async function download(action) {
  if (state.downloading) return
  state.downloading = true; state.error = ''
  try {
    const blob = await fetchExcel(props.resource, action, action === 'export' ? props.filters : {}, controller.signal)
    if (!active) return
    saveExcel(blob, label.value + (action === 'export' ? '数据' : '导入模板') + '.xlsx')
  } catch (error) { if (active && !error.cancelled && !error.forbidden && !error.unauthorized) state.error = plainImportMessage(error.message) }
  finally { if (active) state.downloading = false }
}
async function submit() {
  if (state.busy) return
  if (state.done) { state.open = false; return }
  state.error = validateExcel(state.file)
  if (state.error) return
  state.busy = true; state.result = ''
  const body = new FormData(); body.append('file', state.file)
  body.append('updateSupport', 'false')
  try {
    const data = await api.post('/system/' + props.resource + '/importData', body,
      { signal: controller.signal, timeout: 120000 })
    if (!active) return
    state.result = plainImportMessage(data.msg || '导入成功'); state.done = true
    emit('imported')
  } catch (error) {
    if (active && !error.cancelled && !error.forbidden && !error.unauthorized) {
      state.error = plainImportMessage(error.message)
      // 用户导入沿用若依逐行处理，错误批次也可能已有成功行。
      if (props.resource === 'user') emit('imported')
    }
  } finally { if (active) state.busy = false }
}
</script>
<template>
  <button v-if="canImport" class="ui-btn" type="button" @click="openImport">导入{{ label }}</button>
  <button v-if="canExport" class="ui-btn" type="button" :disabled="state.downloading" @click="download('export')">{{ state.downloading ? '下载中…' : '导出' + label }}</button>
  <span v-if="state.error && !state.open" class="transfer-error" role="alert">{{ state.error }}</span>
  <AppModal v-if="state.open" :title="'导入' + label" :busy="state.busy" :ok-text="state.done ? '完成' : '开始导入'"
    @close="state.open = false" @confirm="submit">
    <p>请使用模板填写数据，支持 .xls / .xlsx，单个文件不超过 10MB、最多 1000 条。</p>
    <button class="ui-btn" type="button" :disabled="state.downloading || state.busy" @click="download('importTemplate')">下载{{ label }}导入模板</button>
    <p><input type="file" :aria-label="label + '导入文件'" accept=".xls,.xlsx" :disabled="state.busy" @change="choose" /></p>
    <p v-if="resource === 'user'" class="hint">仅新增账号，不覆盖已有用户。初始密码取自系统参数；导入后可在编辑用户中分配角色。用户导入逐行处理，失败时请按结果核对已成功的行。</p>
    <p v-else class="hint">仅新增角色，重名或重复权限字符会拒绝整批导入。新角色默认为仅本人数据权限；菜单权限在编辑角色中配置。</p>
    <pre v-if="state.error" class="transfer-result error" role="alert">{{ state.error }}</pre>
    <pre v-if="state.result" class="transfer-result" role="status">{{ state.result }}</pre>
  </AppModal>
</template>
<style scoped>
.transfer-result { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; max-height: 240px; overflow: auto; }
.transfer-result.error, .transfer-error { color: var(--danger); }
</style>
