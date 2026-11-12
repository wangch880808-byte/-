<template>
  <div class="app-container group2-page">
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
        <span class="edu-hint">{{ currentClassText }}</span>
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
      <el-row v-if="visibleList.length" :gutter="16">
        <el-col v-for="row in visibleList" :key="row.id" :xs="24" :sm="12" :md="8" :lg="6" class="edu-col">
          <el-card shadow="hover" class="edu-course-card" @click="openDetail(row)">
            <div class="edu-cover" :style="coverStyle(row.id)">
              <span class="edu-cover-badge">{{ row.semesterName || '本学期' }}</span>
              <div class="edu-cover-title">{{ row.name }}</div>
            </div>
            <div class="edu-course-body">
              <div class="edu-course-name">{{ row.name }}</div>
              <div class="edu-course-meta">{{ row.teacherName || '教师' }} · {{ row.className || '在修课程' }}</div>
              <div class="edu-course-foot">
                <span>{{ row.className || '本班' }}</span>
                <span>更新 {{ row.updatedAt || '—' }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-empty v-else-if="!loading" :description="currentClassId ? '当前班级暂时没有在修课程' : '暂时没有在修课程'">
        <p class="edu-empty-hint">{{ currentClassId ? '可在顶部切换其他班级查看。' : '课程由教师发布并绑定到你所在的教学班后才会出现。若刚被加入班级，请联系任课教师确认课程已发布。' }}</p>
      </el-empty>
    </div>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script setup name="EduStudentCourseList">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listCourse } from '@/api/education/course'
import { eduErrorText } from '@/api/education/http'
import { context, contexts } from '@/education/workspace'

const router = useRouter()

const loading = ref(false)
const errorText = ref('')
const list = ref([])
const total = ref(0)
const queryParams = reactive({ pageNum: 1, pageSize: 12, keyword: '' })

// 顶部选定班级后，只展示绑定在该班级下的课程。
const currentClassId = computed(() => context.current?.classId || '')
const currentClassText = computed(() => context.current?.className
  ? '当前班级：' + context.current.className
  : '只显示你所在教学班已绑定的课程')
const classCourseIds = computed(() => {
  if (!currentClassId.value) return null
  return new Set(context.courses
    .filter(course => course.classes.some(item => item.classId === currentClassId.value))
    .map(course => String(course.courseSpaceId)))
})
const visibleList = computed(() => classCourseIds.value
  ? list.value.filter(row => classCourseIds.value.has(String(row.id)))
  : list.value)

function coverStyle(id) {
  const hue = (Number(id) * 47) % 360
  return {
    background: 'linear-gradient(135deg, hsl(' + hue + ', 55%, 55%), hsl(' + ((hue + 36) % 360) + ', 52%, 42%))'
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

async function openDetail(row) {
  const course = context.courses.find(item => String(item.courseSpaceId) === String(row.id))
  const sameCourse = context.current && String(context.current.courseSpaceId) === String(row.id)
  // 未选班级时，若该课程只有自己所在的一个班级，直接带入该班级。
  if (!sameCourse && course?.classes.length === 1) {
    const target = course.classes[0]
    await contexts.select(course.courseSpaceId, target.classId, target.bindingId)
  }
  const current = context.current
  const query = current && String(current.courseSpaceId) === String(row.id) && current.classId
    ? { courseSpaceId: current.courseSpaceId, classId: current.classId, ...(current.bindingId ? { bindingId: current.bindingId } : {}) }
    : {}
  router.push({ path: '/student/courses/' + row.id, query })
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
}
.edu-cover-badge {
  font-size: 12px;
  opacity: 0.9;
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
.edu-hint {
  font-size: 12px;
  color: #909399;
}
.edu-empty-hint {
  font-size: 12px;
  color: #909399;
  max-width: 460px;
  line-height: 1.7;
}
</style>
