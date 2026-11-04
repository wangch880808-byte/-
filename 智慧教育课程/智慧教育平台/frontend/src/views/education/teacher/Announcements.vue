<template>
  <div class="app-container">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb16" />
    <div class="toolbar">
      <el-button type="primary" :disabled="!course.bindingId" @click="openCreate">新建草稿</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">{{ statusText(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="visibilityState" label="可见性" width="120" />
      <el-table-column prop="createdAt" label="创建时间" min-width="180" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="run(row, 'publish')">发布</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link type="primary" @click="run(row, 'archive')">归档</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination :total="total" :page="query.pageNum" :limit="query.pageSize"
      @update:page="query.pageNum = $event" @update:limit="query.pageSize = $event" @pagination="load" />

    <el-dialog v-model="creating" title="新建公告草稿" width="520px">
      <el-form label-position="top">
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="80" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="form.body" type="textarea" :rows="5" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="creating = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { archiveAnnouncement, createAnnouncement, listTeacherAnnouncements, publishAnnouncement } from '@/api/education/announcement'
import { eduErrorText } from '@/utils/request'
import { useCourseStore } from '@/store/course'

const course = useCourseStore()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const creating = ref(false)
const error = ref('')
const query = reactive({ pageNum: 1, pageSize: 10 })
const form = reactive({ title: '', body: '' })
const statusText = status => ({ DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] || status || '—')

async function load() {
  loading.value = true
  error.value = ''
  try {
    await course.ensure('TEACHER')
    if (!course.current || course.current.role !== 'TEACHER') {
      rows.value = []
      total.value = 0
      error.value = '未找到任课教学班绑定'
      return
    }
    const body = await listTeacherAnnouncements({
      bindingId: String(course.current.bindingId),
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    rows.value = Array.isArray(body.rows) ? body.rows : []
    total.value = Number(body.total) || 0
  } catch (err) {
    rows.value = []
    total.value = 0
    error.value = eduErrorText(err)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.title = ''
  form.body = ''
  creating.value = true
}

async function create() {
  if (!form.title.trim() || !form.body.trim()) {
    ElMessage.warning('请填写标题和正文')
    return
  }
  saving.value = true
  try {
    await createAnnouncement({
      bindingId: String(course.current.bindingId),
      title: form.title.trim(),
      body: form.body.trim()
    })
    creating.value = false
    ElMessage.success('草稿已保存')
    await load()
  } catch (err) {
    ElMessage.error(eduErrorText(err))
  } finally {
    saving.value = false
  }
}

async function run(row, action) {
  try {
    if (action === 'publish') await publishAnnouncement(row.id)
    else await archiveAnnouncement(row.id)
    ElMessage.success(action === 'publish' ? '已发布' : '已归档')
    await load()
  } catch (err) {
    ElMessage.error(eduErrorText(err))
  }
}

watch(() => course.bindingId, (id, prev) => { if (prev && id !== prev) { query.pageNum = 1; load() } })
onMounted(load)
</script>
<style scoped>
.toolbar { margin-bottom: 12px; }
</style>
