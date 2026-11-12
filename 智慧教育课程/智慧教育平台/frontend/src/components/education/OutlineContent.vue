<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api/education/client'
import { saveAs } from 'file-saver'

const props = defineProps({
  courseId: { type: [Number, String], required: true },
  node: { type: Object, required: true },
  editable: Boolean
})
const url = `/education/course/${props.courseId}/outline/${props.node.kind}/${props.node.id}/content`
const attachmentsUrl = url + '/attachments'
const record = ref(null)
const content = ref('')
const busy = ref(false)
const errorText = ref('')
const fileInput = ref()
const preview = ref(null)
const mediaTypes = {
  jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', gif: 'image/gif', webp: 'image/webp',
  mp4: 'video/mp4', webm: 'video/webm', mp3: 'audio/mpeg', wav: 'audio/wav'
}

function clearPreview() {
  if (preview.value) URL.revokeObjectURL(preview.value.url)
  preview.value = null
}

async function run(action) {
  if (busy.value) return
  busy.value = true
  errorText.value = ''
  try { await action() }
  catch (error) { if (!error.cancelled && !error.unauthorized) errorText.value = error.message }
  finally { busy.value = false }
}

async function load(resetContent = false) {
  const response = await api.get(url, { silent403: true })
  record.value = response.data
  if (resetContent) content.value = record.value.contentText || ''
}

function save() {
  return run(async () => {
    await api.put(url, { contentText: content.value }, { silent403: true })
    ElMessage.success('正文已保存')
  })
}

function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  return run(async () => {
    if (!file.size || file.size > 10 * 1024 * 1024) throw new Error('请选择不超过 10 MB 的非空文件')
    const data = new FormData()
    data.append('file', file)
    await api.post(attachmentsUrl, data, { timeout: 120000, silent403: true })
    await load()
    ElMessage.success('附件已上传')
  })
}

function mediaType(file) {
  return mediaTypes[file.fileName.split('.').pop().toLowerCase()]
}

function openFile(file, inline = false) {
  return run(async () => {
    const blob = await api.get(`${attachmentsUrl}/${file.id}`, { responseType: 'blob', silent403: true })
    if (!inline) {
      saveAs(blob, file.fileName)
      return
    }
    const type = mediaType(file)
    clearPreview()
    preview.value = { type, url: URL.createObjectURL(new Blob([blob], { type })), name: file.fileName }
  })
}

async function removeFile(file) {
  if (busy.value) return
  try { await ElMessageBox.confirm(`移除附件「${file.fileName}」？`, '移除附件', { type: 'warning' }) }
  catch { return }
  await run(async () => {
    await api.del(`${attachmentsUrl}/${file.id}`, { silent403: true })
    clearPreview()
    await load()
  })
}

onMounted(() => run(() => load(true)))
onUnmounted(clearPreview)
</script>

<template>
  <section class="outline-content" v-loading="busy">
    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />
    <template v-if="record">
      <h3>学习内容</h3>
      <template v-if="editable">
        <el-input v-model="content" type="textarea" :rows="7" maxlength="12000" show-word-limit
          aria-label="学习正文" placeholder="填写本节讲解、学习要求或补充说明" />
        <el-button type="primary" class="content-action" :disabled="busy" @click="save">保存正文</el-button>
      </template>
      <p v-else class="content-text">{{ record.contentText || '暂无学习正文' }}</p>
      <div class="attachment-header">
        <h3>学习附件</h3>
        <template v-if="editable">
          <input ref="fileInput" type="file" hidden accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.jpg,.jpeg,.png,.gif,.webp,.mp4,.webm,.mp3,.wav" @change="upload" />
          <el-button :disabled="busy" @click="fileInput.click()">上传附件</el-button>
        </template>
      </div>
      <p v-if="!record.attachments.length" class="content-muted">暂无附件</p>
      <ul v-else class="attachments">
        <li v-for="file in record.attachments" :key="file.id">
          <span>{{ file.fileName }}</span>
          <div>
            <el-button v-if="mediaType(file)" link type="primary" :disabled="busy" @click="openFile(file, true)">预览</el-button>
            <el-button link type="primary" :disabled="busy" @click="openFile(file)">下载</el-button>
            <el-button v-if="editable" link type="danger" :disabled="busy" @click="removeFile(file)">移除</el-button>
          </div>
        </li>
      </ul>
      <div v-if="preview" class="attachment-preview">
        <el-button link @click="clearPreview">关闭预览</el-button>
        <img v-if="preview.type.startsWith('image/')" :src="preview.url" :alt="preview.name" />
        <video v-else-if="preview.type.startsWith('video/')" :src="preview.url" controls />
        <audio v-else :src="preview.url" controls />
      </div>
    </template>
    <el-button v-else-if="errorText" @click="run(() => load(true))">重试</el-button>
  </section>
</template>

<style scoped>
.outline-content { min-height: 140px; }
.content-text { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
.content-action { margin-top: 10px; }
.attachment-header, .attachments li { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.attachments { padding: 0; list-style: none; }
.attachments li { padding: 8px 0; border-bottom: 1px solid var(--el-border-color-light); overflow-wrap: anywhere; }
.content-muted { color: var(--el-text-color-secondary); }
.attachment-preview { display: grid; gap: 8px; justify-items: start; }
.attachment-preview img, .attachment-preview video, .attachment-preview audio { max-width: 100%; }
</style>
