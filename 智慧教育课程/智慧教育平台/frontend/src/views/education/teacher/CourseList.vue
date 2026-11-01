<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="10">
        <el-input
          v-model="queryParams.keyword"
          placeholder="按课程名称搜索"
          clearable
          style="width: 300px"
          @keyup.enter="handleQuery"
        />
      </el-col>
      <el-col :span="14" style="text-align: right">
        <el-button type="primary" plain @click="openCreate">新建课程</el-button>
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

    <div v-loading="loading">
      <el-row v-if="list.length" :gutter="16">
        <el-col v-for="row in list" :key="row.id" :xs="24" :sm="12" :md="8" :lg="6" class="edu-col">
          <el-card shadow="hover" class="edu-course-card" @click="openDetail(row)">
            <div class="edu-cover" :style="coverStyle(row.id)">
              <el-tag :type="statusTag(row.status)" effect="dark" size="small">{{ statusLabel(row.status) }}</el-tag>
              <div class="edu-cover-title">{{ row.name }}</div>
            </div>
            <div class="edu-course-body">
              <div class="edu-course-name">{{ row.name }}</div>
              <div class="edu-course-meta">
                {{ row.semesterName || '未指定学期' }} · {{ row.classCount || 0 }} 个班 ·
                {{ row.studentCount || 0 }} 名学生
              </div>
              <div class="edu-course-foot">
                <span>{{ row.officialCourseName || '未关联正式课程' }}</span>
                <span>更新 {{ row.updatedAt || '—' }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-empty v-else-if="!loading" description="你还没有创建课程">
        <el-button type="primary" @click="openCreate">新建第一门课程</el-button>
        <p class="edu-empty-hint">建好课程后绑定教学班，学生才能看到。</p>
      </el-empty>
    </div>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <course-form-dialog v-model="formOpen" :course="editing" @saved="getList" />
  </div>
</template>

<script setup name="EduTeacherCourseList">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listCourse } from '@/api/education/course'
import { eduErrorText } from '@/api/education/http'
import CourseFormDialog from '@/components/education/CourseFormDialog.vue'

const router = useRouter()

const loading = ref(false)
const errorText = ref('')
const list = ref([])
const total = ref(0)
const formOpen = ref(false)
const editing = ref(null)

const queryParams = reactive({ pageNum: 1, pageSize: 12, keyword: '' })

const STATUS = { DRAFT: '草稿', ACTIVE: '已发布', ARCHIVED: '已归档' }

function statusLabel(value) {
  return STATUS[value] || value || '未知'
}

function statusTag(value) {
  if (value === 'ACTIVE') return 'success'
  if (value === 'ARCHIVED') return 'info'
  return 'warning'
}

function coverStyle(id) {
  const hue = (Number(id) * 47) % 360
  return {
    background: 'linear-gradient(135deg, hsl(' + hue + ', 62%, 58%), hsl(' + ((hue + 36) % 360) + ', 58%, 46%))'
  }
}

async function getList() {
  loading.value = true
  errorText.value = ''
  try {
    const res = await listCourse(queryParams)
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

function openCreate() {
  editing.value = null
  formOpen.value = true
}

function openDetail(row) {
  router.push({ path: '/education/teacher/course/detail/' + row.id })
}

onMounted(getList)
</script>

<style scoped>
.edu-col {
  margin-bottom: 16px;
}
.edu-course-card {
  cursor: pointer;
  height: 100%;
}
.edu-course-card :deep(.el-card__body) {
  padding: 0;
}
.edu-cover {
  height: 110px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  color: #fff;
  border-radius: 4px 4px 0 0;
}
.edu-cover-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  max-height: 42px;
  overflow: hidden;
}
.edu-course-body {
  padding: 12px;
}
.edu-course-name {
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.edu-course-meta {
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
}
.edu-course-foot {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  gap: 8px;
}
.edu-empty-hint {
  font-size: 12px;
  color: #909399;
}
</style>
