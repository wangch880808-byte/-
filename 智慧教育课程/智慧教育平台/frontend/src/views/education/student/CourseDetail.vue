<template>
  <div class="app-container group2-page">
    <el-result v-if="fatal" icon="warning" :title="fatalTitle" :sub-title="fatalText">
      <template #extra>
        <el-button type="primary" @click="$router.push('/student/courses')">返回</el-button>
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
            <el-tag type="success">在修</el-tag>
            <h2>{{ course.name || '课程' }}</h2>
          </div>
          <div class="edu-hero-meta">
            <span>{{ course.teacherName || '教师' }}</span>
            <span>{{ course.className || '本班' }}</span>
          </div>
        </div>
      </el-card>

      <el-tabs v-model="activeTab" class="edu-tabs">
        <el-tab-pane label="目录" name="outline">
          <course-outline :course="course" readonly />
        </el-tab-pane>
        <el-tab-pane label="课程资料" name="material" lazy>
          <group3-course-section v-if="course.id && activeTab === 'material'" :course-id="courseId" v-slot="{ bindingId }">
            <student-materials :key="bindingId" :context-binding-id="bindingId" />
          </group3-course-section>
        </el-tab-pane>
        <el-tab-pane label="课前学习" name="prepare" lazy>
          <group3-course-section v-if="course.id && activeTab === 'prepare'" :course-id="courseId" v-slot="{ bindingId }">
            <student-prestudy :key="bindingId" :context-binding-id="bindingId" />
          </group3-course-section>
        </el-tab-pane>
        <el-tab-pane label="讨论" name="discuss" lazy>
          <group3-course-section v-if="course.id && activeTab === 'discuss'" :course-id="courseId" v-slot="{ bindingId }">
            <student-discussion :key="bindingId" :context-binding-id="bindingId" />
          </group3-course-section>
        </el-tab-pane>
        <el-tab-pane label="同步课堂" name="classroom">
          <el-card shadow="never">
            <el-empty :description="placeholder('classroom')" :image-size="80" />
          </el-card>
        </el-tab-pane>
        <el-tab-pane label="课后学习" name="homework">
          <el-card shadow="never">
            <el-empty :description="placeholder('homework')" :image-size="80" />
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup name="EduStudentCourseDetail">
import { ref, computed, defineAsyncComponent, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCourse } from '@/api/education/course'
import { eduErrorText } from '@/api/education/http'
import CourseOutline from '@/components/education/CourseOutline.vue'
import Group3CourseSection from '../group3/Group3CourseSection.vue'

const StudentMaterials = defineAsyncComponent(() => import('../group3/StudentMaterials.vue'))
const StudentPrestudy = defineAsyncComponent(() => import('../group3/StudentPrestudy.vue'))
const StudentDiscussion = defineAsyncComponent(() => import('../group3/StudentDiscussion.vue'))

const route = useRoute()
const router = useRouter()
const courseId = computed(() => route.params.courseId)

const loading = ref(false)
const errorText = ref('')
const fatal = ref(false)
const fatalText = ref('')
const fatalTitle = ref('无法打开课程')
const course = ref({})
const tabs = ['outline', 'material', 'prepare', 'discuss', 'classroom', 'homework']
const activeTab = computed({
  get() { return tabs.includes(route.query.tab) ? route.query.tab : 'outline' },
  set(tab) {
    const { topicId, assignmentId, resourceId, ...query } = route.query
    router.replace({ query: { ...query, tab } })
  }
})

const PLACEHOLDER = {
  classroom: '该功能尚未开放。',
  homework: '该功能尚未开放。'
}

function placeholder(tab) {
  return PLACEHOLDER[tab] || '该模块尚未开放。'
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
      fatalTitle.value = '没有访问权限'
      fatalText.value = '你不在本课程任意绑定班级，或课程已撤回发布。'
    } else if (e.kind === 'business' && /不存在/.test(e.message)) {
      fatal.value = true
      fatalText.value = '课程不存在，可能已被删除。'
    } else {
      errorText.value = eduErrorText(e)
    }
  } finally {
    loading.value = false
  }
}

watch(courseId, load, { immediate: true })
</script>

<style scoped>
.edu-hero {
  margin-bottom: 16px;
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
</style>
