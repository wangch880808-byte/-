<template>
  <div class="app-container">
    <el-result v-if="fatal" icon="warning" :title="fatalTitle" :sub-title="fatalText">
      <template #extra>
        <el-button type="primary" @click="$router.back()">返回</el-button>
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
            <span>{{ course.semesterName || '未指定学期' }}</span>
            <span>{{ course.classCount || 0 }} 个教学班</span>
            <span>{{ course.studentCount || 0 }} 名学生</span>
          </div>
        </div>
        <div class="edu-hero-actions">
          <el-button @click="openEdit">编辑课程</el-button>
          <el-button :type="isActive ? 'danger' : 'success'" plain @click="toggleStatus">
            {{ isActive ? '撤回发布' : '发布课程' }}
          </el-button>
          <el-button type="primary" @click="openBind">绑定教学班</el-button>
        </div>
      </el-card>

      <el-tabs v-model="activeTab" class="edu-tabs">
        <el-tab-pane label="目录" name="outline">
          <course-outline :course="course">
            <el-card shadow="never">
              <template #header>
                <div class="edu-head">
                  <span>已绑定教学班</span>
                  <span class="edu-tip">没绑定班级时，学生端看不到这门课</span>
                </div>
              </template>
              <el-table v-if="classes.length" :data="classes" border size="small">
                <el-table-column label="教学班" prop="name" min-width="180" />
                <el-table-column label="学期" min-width="150">
                  <template #default="{ row }">{{ row.termName || '—' }}</template>
                </el-table-column>
                <el-table-column label="学生数" width="120" align="center">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openMembers(row)">
                      {{ row.studentCount || 0 }} 人
                    </el-button>
                  </template>
                </el-table-column>
                <el-table-column label="绑定时间" prop="publishedAt" width="130" />
                <el-table-column label="操作" width="160">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openMembers(row)">学生名单</el-button>
                    <el-button link type="danger" @click="handleUnbind(row)">解绑</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="还没有绑定教学班" :image-size="60" />
            </el-card>
          </course-outline>
        </el-tab-pane>

        <el-tab-pane label="资料" name="material">
          <el-card shadow="never">
            <el-empty :description="placeholder('material')" :image-size="80" />
          </el-card>
        </el-tab-pane>
        <el-tab-pane label="作业" name="homework">
          <el-card shadow="never">
            <el-empty :description="placeholder('homework')" :image-size="80" />
          </el-card>
        </el-tab-pane>
        <el-tab-pane label="讨论" name="discuss">
          <el-card shadow="never">
            <el-empty :description="placeholder('discuss')" :image-size="80" />
          </el-card>
        </el-tab-pane>
        <el-tab-pane label="统计" name="stat">
          <el-card shadow="never">
            <el-empty :description="placeholder('stat')" :image-size="80" />
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-drawer v-model="memberOpen" :title="memberClass.name + ' · 学生名单'" size="620px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb8"
        title="只显示当前在班学生"
        description="退班记录保留在「班级管理」里便于追溯；学生入班由管理员维护。"
      />
      <el-table v-loading="memberLoading" :data="members" border size="small">
        <el-table-column label="学号" width="150">
          <template #default="{ row }">{{ row.studentNo || row.userName || '—' }}</template>
        </el-table-column>
        <el-table-column label="姓名" min-width="120">
          <template #default="{ row }">{{ row.studentName || row.nickName || '—' }}</template>
        </el-table-column>
        <el-table-column label="入班时间" width="130">
          <template #default="{ row }">{{ row.joinedAt || '—' }}</template>
        </el-table-column>
        <template #empty>
          <div class="edu-hint">这个班还没有学生。管理员在「班级管理 → 成员」把人加进来后，这里会自动出现。</div>
        </template>
      </el-table>
      <div class="edu-hint">共 {{ members.length }} 名在班学生</div>
    </el-drawer>

    <course-form-dialog v-model="editOpen" :course="course" @saved="load" />

    <el-dialog v-model="bindOpen" title="绑定教学班" width="520px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb8"
        title="只能绑定你被授权的教学班"
        description="绑定后该班学生即可看到这门已发布的课程。解绑不会删除历史作业与成绩。"
      />
      <el-select v-model="bindClassId" placeholder="选择教学班" filterable style="width: 100%">
        <el-option
          v-for="item in bindableClasses"
          :key="item.id || item.classId"
          :label="item.name + '（' + (item.termName || item.semesterName || '未指定学期') + '）'"
          :value="item.id || item.classId"
        />
      </el-select>
      <div v-if="!bindableClasses.length" class="edu-hint">
        没有可绑定的教学班：确认管理员已把你设为任课教师，且该班尚未绑定其他课程。
      </div>
      <template #footer>
        <el-button @click="bindOpen = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBind">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="EduTeacherCourseDetail">
import { ref, computed, getCurrentInstance, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getCourse, changeCourseStatus, bindCourseClass, unbindCourseClass } from '@/api/education/course'
import { listMyTeachingClass, listClassMembers } from '@/api/education/teachingClass'
import { eduErrorText } from '@/api/education/http'
import CourseOutline from '@/components/education/CourseOutline.vue'
import CourseFormDialog from '@/components/education/CourseFormDialog.vue'

const { proxy } = getCurrentInstance()
const route = useRoute()

const courseId = computed(() => route.params.courseId)

const loading = ref(false)
const submitting = ref(false)
const errorText = ref('')
const fatal = ref(false)
const fatalText = ref('')
const course = ref({})
const activeTab = ref('outline')

const editOpen = ref(false)
const bindOpen = ref(false)
const bindClassId = ref(null)
const myClasses = ref([])

const memberOpen = ref(false)
const memberLoading = ref(false)
const members = ref([])
const memberClass = ref({ id: null, name: '' })

const STATUS = { DRAFT: '草稿', ACTIVE: '已发布', ARCHIVED: '已归档' }
const PLACEHOLDER = {
  material: '课程资料由第 3 组（3-03 资源上传与按班发布）实现，接口尚未接入。',
  homework: '作业发布与批改由第 5 组（5-03）实现，接口尚未接入。',
  discuss: '讨论区由第 6 组实现，接口尚未接入。',
  stat: '学情统计由第 5 组按已确认口径提供，接口尚未接入。'
}

const isActive = computed(() => course.value.status === 'ACTIVE')
const classes = computed(() => course.value.classes || [])
const bindableClasses = computed(() => {
  const bound = new Set(classes.value.map((item) => item.classId))
  return myClasses.value.filter((item) => !bound.has(item.id || item.classId))
})

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
      fatalText.value = '你不是这门课的负责人，或课程已被撤回。'
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
        ? '发布后，绑定教学班的学生就能看到这门课。'
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

async function openBind() {
  bindClassId.value = null
  bindOpen.value = true
  if (myClasses.value.length) return
  try {
    const res = await listMyTeachingClass()
    myClasses.value = res.rows || []
  } catch (e) {
    proxy.$modal.msgError(eduErrorText(e))
  }
}

async function submitBind() {
  if (!bindClassId.value) {
    proxy.$modal.msgWarning('请选择教学班')
    return
  }
  submitting.value = true
  try {
    await bindCourseClass(courseId.value, bindClassId.value)
    proxy.$modal.msgSuccess('已绑定')
    bindOpen.value = false
    load()
  } catch (e) {
    proxy.$modal.msgError(eduErrorText(e))
  } finally {
    submitting.value = false
  }
}

async function openMembers(row) {
  memberClass.value = { id: row.classId, name: row.name || row.className || '教学班' }
  memberOpen.value = true
  memberLoading.value = true
  try {
    const res = await listClassMembers(row.classId)
    members.value = (res.rows || []).filter(
      (m) => (m.status || (m.leftAt ? 'LEFT' : 'ACTIVE')) === 'ACTIVE'
    )
  } catch (e) {
    members.value = []
    proxy.$modal.msgError(eduErrorText(e))
  } finally {
    memberLoading.value = false
  }
}

async function handleUnbind(row) {
  try {
    await proxy.$modal.confirm('解绑后该班学生看不到这门课，历史作业与成绩仍保留。')
  } catch (e) {
    return
  }
  try {
    await unbindCourseClass(courseId.value, row.id)
    proxy.$modal.msgSuccess('已解绑')
    load()
  } catch (e) {
    proxy.$modal.msgError(eduErrorText(e))
  }
}

onMounted(load)
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
.edu-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.edu-tip {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}
.edu-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
