<template>
  <div class="app-container group2-page">
    <el-result v-if="fatal" icon="warning" :title="fatalTitle" :sub-title="fatalText">
      <template #extra>
        <el-button type="primary" @click="$router.push('/teacher/courses')">返回</el-button>
        <el-button @click="load">重试</el-button>
      </template>
    </el-result>

    <template v-else>
      <el-alert
        v-if="errorText && !fatal"
        :title="errorText"
        type="warning"
        show-icon
        :closable="false"
        class="mb8"
      />

      <el-card v-loading="loading" shadow="never" class="edu-hero">
        <div class="edu-hero-main">
          <div class="edu-hero-title">
            <el-tag :type="statusTag(course.status)">{{ statusLabel(course.status) }}</el-tag>
            <h2>{{ course.name || '课程' }}</h2>
          </div>
          <div class="edu-hero-meta">
            <span>{{ course.teacherName || '未指定教师' }}</span>
            <span>{{ course.classCount || 0 }} 个教学班</span>
            <span>{{ course.studentCount || 0 }} 名学生</span>
          </div>
        </div>
        <div v-if="canManage" class="edu-hero-actions">
          <el-button @click="openEdit">编辑课程</el-button>
          <el-button :type="isActive ? 'danger' : 'success'" plain @click="toggleStatus">
            {{ isActive ? '撤回发布' : '发布课程' }}
          </el-button>
        </div>
      </el-card>

      <el-tabs v-model="activeTab" class="edu-tabs">
        <el-tab-pane label="目录" name="outline">
          <course-outline :course="course" :readonly="!canManage" @saved="load" />
        </el-tab-pane>

        <el-tab-pane label="班级管理" name="classes" lazy>
          <course-classes
            v-if="course.id"
            :course-id="courseId"
            :classes="classes"
            :can-manage="canManage"
            :can-manage-students="course.canManageRoster === true && course.status !== 'ARCHIVED'"
            @changed="load"
          />
        </el-tab-pane>

        <el-tab-pane label="教师团队" name="team" lazy>
          <course-team v-if="course.id" :course-id="courseId" :can-manage="canManage" />
        </el-tab-pane>

        <el-tab-pane label="课程建设" name="material" lazy>
          <group3-course-section v-if="course.id && activeTab === 'material'" :course-id="courseId" :empty-message="group3Empty" v-slot="{ bindingId }">
            <teacher-studio :key="bindingId" :context-binding-id="bindingId" :can-manage-outline="canManage" />
          </group3-course-section>
        </el-tab-pane>
        <el-tab-pane label="课前准备" name="prepare" lazy>
          <group3-course-section v-if="course.id && activeTab === 'prepare'" :course-id="courseId" :empty-message="group3Empty" v-slot="{ bindingId }">
            <teacher-prepare :key="bindingId" :context-binding-id="bindingId" />
          </group3-course-section>
        </el-tab-pane>
        <el-tab-pane label="作业" name="homework">
          <el-card shadow="never">
            <el-empty :description="placeholder('homework')" :image-size="80" />
          </el-card>
        </el-tab-pane>
        <el-tab-pane label="统计" name="stat">
          <el-card shadow="never">
            <el-empty :description="placeholder('stat')" :image-size="80" />
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </template>

    <course-form-dialog v-model="editOpen" :course="course" @saved="load" />
  </div>
</template>

<script setup name="EduTeacherCourseDetail">
import { ref, computed, defineAsyncComponent, getCurrentInstance, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCourse, changeCourseStatus } from '@/api/education/course'
import { eduErrorText } from '@/api/education/http'
import CourseOutline from '@/components/education/CourseOutline.vue'
import CourseFormDialog from '@/components/education/CourseFormDialog.vue'
import CourseClasses from './CourseClasses.vue'
import CourseTeam from './CourseTeam.vue'
import Group3CourseSection from '../group3/Group3CourseSection.vue'

const TeacherStudio = defineAsyncComponent(() => import('../group3/TeacherStudio.vue'))
const TeacherPrepare = defineAsyncComponent(() => import('../group3/TeacherPrepare.vue'))

const { proxy } = getCurrentInstance()
const route = useRoute()
const router = useRouter()

const courseId = computed(() => route.params.courseId)

const loading = ref(false)
const errorText = ref('')
const fatal = ref(false)
const fatalText = ref('')
const course = ref({})
const tabs = ['outline', 'classes', 'team', 'material', 'prepare', 'homework', 'stat']
const activeTab = computed({
  get() {
    const tab = { studio: 'material', discuss: 'prepare' }[route.query.tab] || route.query.tab
    return tabs.includes(tab) ? tab : 'outline'
  },
  set(tab) { router.replace({ query: { ...route.query, tab } }) }
})
const group3Empty = '请先添加有效教学班，并确认你拥有该班的发布权限。'

const editOpen = ref(false)

const STATUS = { DRAFT: '草稿', ACTIVE: '已发布', ARCHIVED: '已归档' }
const PLACEHOLDER = {
  homework: '该功能尚未开放。',
  stat: '该功能尚未开放。'
}

const canManage = computed(() => course.value.canManage === true && course.value.status !== 'ARCHIVED')
const isActive = computed(() => course.value.status === 'ACTIVE')
const classes = computed(() => course.value.classes || [])

function statusLabel(value) {
  return STATUS[value] || value || '未知'
}

function statusTag(value) {
  if (value === 'ACTIVE') return 'success'
  if (value === 'ARCHIVED') return 'info'
  return 'warning'
}

function placeholder(tab) {
  return PLACEHOLDER[tab] || '该模块尚未排期。'
}

async function load() {
  loading.value = true
  errorText.value = ''
  fatal.value = false
  try {
    const res = await getCourse(courseId.value)
    course.value = res.data || {}
  } catch (e) {
    course.value = {}
    if (e.kind === 'forbidden') {
      fatal.value = true
      fatalText.value = '你没有这门课的访问权限，或教师团队关系已失效。'
    } else if (e.kind === 'business' && /不存在/.test(e.message)) {
      fatal.value = true
      fatalText.value = '课程不存在，可能已被删除或链接里的编号不对。'
    } else {
      errorText.value = eduErrorText(e)
    }
  } finally {
    loading.value = false
  }
}

const fatalTitle = computed(() => '无法打开课程')

function openEdit() {
  editOpen.value = true
}

async function toggleStatus() {
  const publish = !isActive.value
  try {
    await proxy.$modal.confirm(
      publish
        ? '发布后，本课程教学班的学生就能看到这门课。'
        : '撤回后学生端立刻看不到这门课，已提交的作业与成绩不受影响。'
    )
  } catch (e) {
    return
  }
  try {
    await changeCourseStatus(courseId.value, publish ? 'ACTIVE' : 'DRAFT')
    proxy.$modal.msgSuccess(publish ? '已发布' : '已撤回')
    load()
  } catch (e) {
    proxy.$modal.msgError(eduErrorText(e))
  }
}

watch(courseId, load, { immediate: true })
</script>

<style scoped>
.edu-hero {
  margin-bottom: 16px;
}
.edu-hero :deep(.el-card__body) {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.edu-hero-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.edu-hero-title h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.edu-hero-meta {
  margin-top: 10px;
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #606266;
}
.edu-hero-actions {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
