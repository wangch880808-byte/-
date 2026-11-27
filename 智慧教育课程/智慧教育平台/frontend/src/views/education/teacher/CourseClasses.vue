<template>
  <el-card class="class-card" shadow="never">
    <div class="class-layout">
      <aside class="class-sidebar" aria-label="课程教学班">
        <div class="class-sidebar-actions">
          <h3>教学班</h3>
          <el-button v-if="canManage" type="primary" plain :icon="Plus" @click="openCreate">新建教学班</el-button>
        </div>
        <el-input v-model="classKeyword" clearable :prefix-icon="Search" placeholder="搜索教学班" aria-label="搜索教学班" />
        <ul v-if="filteredClasses.length" class="class-list">
          <li v-for="item in filteredClasses" :key="item.classId">
            <button
              type="button"
              class="class-item"
              :class="{ 'is-selected': item.classId === selectedClassId }"
              :aria-current="item.classId === selectedClassId ? 'true' : undefined"
              :disabled="busy || creating"
              @click="selectedClassId = item.classId"
            >
              <span class="class-name">{{ item.name }}</span>
              <small>{{ item.studentCount || 0 }} 人</small>
            </button>
          </li>
        </ul>
        <p v-else class="class-empty">{{ classKeyword.trim() ? '没有匹配的教学班' : '暂无教学班' }}</p>
      </aside>

      <section v-if="selectedClass" v-loading="rosterLoading" class="class-roster" aria-label="学生名单">
        <div class="roster-heading">
          <h3>{{ selectedClass.name }}</h3>
          <div v-if="canManage" class="class-actions">
            <el-button :disabled="busy || creating" @click="openRename">重命名</el-button>
            <el-button type="danger" plain :disabled="busy || creating" @click="deleteClass">删除教学班</el-button>
          </div>
        </div>
        <div class="roster-toolbar">
          <roster-transfer
            v-if="permissions"
            :key="selectedClass.classId"
            :class-id="selectedClass.classId"
            :class-name="selectedClass.name"
            :course-id="courseId"
            :can-import="canManageStudents && permissions.canImport && !busy"
            :can-export="permissions.canExport"
            @imported="refreshRoster"
          />
          <el-input v-model="studentKeyword" clearable :prefix-icon="Search" placeholder="搜索学号或姓名" aria-label="搜索本班学生" />
        </div>
        <div v-if="canEditRoster" class="selection-toolbar">
          <span>已选 {{ selectedMembers.length }} 人</span>
          <el-button :disabled="busy || !selectedMembers.length" @click="openMove(selectedMembers)">调整到其他教学班</el-button>
          <el-button type="danger" plain :disabled="busy || !selectedMembers.length" @click="removeStudents(selectedMembers)">批量移除</el-button>
        </div>
        <el-alert v-if="operationError" :title="operationError" type="error" :closable="false" show-icon />
        <el-alert v-if="rosterError" :title="rosterError" type="warning" :closable="false" show-icon />
        <el-table v-else ref="memberTable" :data="filteredMembers" border row-key="userId"
          @selection-change="selectedMembers = $event">
          <el-table-column v-if="canEditRoster" type="selection" width="48" :selectable="() => !busy" />
          <el-table-column label="学号" min-width="140">
            <template #default="{ row }">{{ row.studentNo || row.userName || '—' }}</template>
          </el-table-column>
          <el-table-column label="姓名" min-width="100">
            <template #default="{ row }">{{ row.studentName || row.nickName || '—' }}</template>
          </el-table-column>
          <el-table-column label="入班时间" prop="joinedAt" min-width="150" />
          <el-table-column v-if="canEditRoster" label="操作" min-width="160">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="busy" @click="openMove([row])">调整班级</el-button>
              <el-button link type="danger" :disabled="busy" @click="removeStudents([row])">移除</el-button>
            </template>
          </el-table-column>
          <template #empty>{{ studentKeyword.trim() ? '没有匹配的学生' : '本班暂无学生' }}</template>
        </el-table>
        <p v-if="!rosterError" class="class-empty" aria-live="polite">
          {{ studentKeyword.trim() ? '匹配 ' + filteredMembers.length + ' 人 · ' : '' }}共 {{ members.length }} 人
        </p>
      </section>
      <el-empty v-else class="class-roster" description="暂无教学班" :image-size="85" />
    </div>
  </el-card>

  <el-dialog
    v-model="createOpen"
    :title="editingClassId ? '重命名教学班' : '新建教学班'"
    width="min(480px, 92vw)"
    append-to-body
    :show-close="!creating"
    :close-on-click-modal="!creating"
    :close-on-press-escape="!creating"
  >
    <el-form label-position="top" @submit.prevent="createClass">
      <el-form-item label="班级名称" required :error="createError">
        <el-input v-model="className" maxlength="160" placeholder="如 2026级软件工程一班" :disabled="creating" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="creating" @click="createOpen = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="createClass">{{ editingClassId ? '保存' : '新建' }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="moveOpen" title="调整教学班" width="min(500px, 92vw)" append-to-body
    :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy">
    <p>将选中的 {{ movingMembers.length }} 名学生从当前教学班调整到</p>
    <el-select v-model="targetClassId" placeholder="请选择本课程的其他教学班" :disabled="busy" style="width: 100%">
      <el-option v-for="item in moveTargets" :key="item.classId" :value="item.classId" :label="item.name" />
    </el-select>
    <el-alert v-if="moveError" :title="moveError" type="error" :closable="false" class="move-error" />
    <template #footer>
      <el-button :disabled="busy" @click="moveOpen = false">取消</el-button>
      <el-button type="primary" :loading="busy" :disabled="!targetClassId" @click="moveStudents">确认调整</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  createCourseTeachingClass, renameCourseTeachingClass, deleteCourseTeachingClass, changeCourseClassStudents
} from '@/api/education/course'
import { api } from '@/api/education/client'
import { eduErrorText } from '@/api/education/http'
import RosterTransfer from '@/components/education/RosterTransfer.vue'

const props = defineProps({
  courseId: { type: [String, Number], required: true },
  classes: { type: Array, required: true },
  canManage: Boolean,
  canManageStudents: Boolean
})
const emit = defineEmits(['changed'])

const classKeyword = ref('')
const selectedClassId = ref(null)
let createdClassId = null
const selectedClass = computed(() => props.classes.find(item => item.classId === selectedClassId.value))
const filteredClasses = computed(() => {
  const keyword = classKeyword.value.trim().toLowerCase()
  return props.classes.filter(item => (item.name || '').toLowerCase().includes(keyword))
})

const rosterLoading = ref(false)
const rosterError = ref('')
const members = ref([])
const permissions = ref(null)
const studentKeyword = ref('')
let rosterRequest = 0

const filteredMembers = computed(() => {
  const keyword = studentKeyword.value.trim().toLowerCase()
  return members.value.filter(member => [member.studentNo, member.userName, member.studentName, member.nickName]
    .some(value => String(value || '').toLowerCase().includes(keyword)))
})

const createOpen = ref(false)
const className = ref('')
const createError = ref('')
const creating = ref(false)
const editingClassId = ref(null)
const busy = ref(false)
const memberTable = ref()
const selectedMembers = ref([])
const operationError = ref('')
const moveOpen = ref(false)
const movingMembers = ref([])
const targetClassId = ref(null)
const moveError = ref('')
const canEditRoster = computed(() => props.canManageStudents && permissions.value?.canImport && !rosterError.value)
const moveTargets = computed(() => props.classes.filter(item => item.classId !== selectedClassId.value))

watch(() => props.classes, classes => {
  const created = classes.find(item => String(item.classId) === String(createdClassId))
  if (created) {
    selectedClassId.value = created.classId
    createdClassId = null
  } else if (!classes.some(item => item.classId === selectedClassId.value)) {
    selectedClassId.value = classes[0]?.classId ?? null
  }
}, { immediate: true })

watch(selectedClassId, () => {
  studentKeyword.value = ''
  members.value = []
  selectedMembers.value = []
  operationError.value = ''
  permissions.value = null
  loadRoster()
}, { immediate: true })

async function loadRoster() {
  const request = ++rosterRequest
  const classId = selectedClassId.value
  memberTable.value?.clearSelection()
  selectedMembers.value = []
  rosterError.value = ''
  rosterLoading.value = !!classId
  if (!classId) return
  const memberUrl = '/education/teaching-class/' + classId + '/members'
  const [roster, access] = await Promise.allSettled([
    api.get(memberUrl, { silent403: true }),
    api.get(memberUrl + '/transfer-permissions', { silent403: true })
  ])
  if (request !== rosterRequest) return
  if (roster.status === 'fulfilled') {
    members.value = (roster.value.rows || []).filter(member => member.status === 'ACTIVE')
  } else {
    rosterError.value = eduErrorText(roster.reason)
  }
  permissions.value = access.status === 'fulfilled' ? access.value.data : null
  if (access.status === 'rejected' && !access.reason.cancelled && !access.reason.unauthorized && !access.reason.forbidden) {
    ElMessage.error(eduErrorText(access.reason))
  }
  rosterLoading.value = false
}

function refreshRoster() {
  loadRoster()
  emit('changed')
}

function openCreate() {
  editingClassId.value = null
  className.value = ''
  createError.value = ''
  createOpen.value = true
}

async function createClass() {
  if (creating.value) return
  const name = className.value.trim()
  if (!name) {
    createError.value = '请输入班级名称'
    return
  }
  createError.value = ''
  creating.value = true
  try {
    if (editingClassId.value) {
      await renameCourseTeachingClass(props.courseId, editingClassId.value, name)
    } else {
      const response = await createCourseTeachingClass(props.courseId, { name })
      createdClassId = response.data?.teachingClassId ?? null
    }
    classKeyword.value = ''
    createOpen.value = false
    ElMessage.success(editingClassId.value ? '教学班名称已保存' : '教学班已创建')
    emit('changed')
  } catch (error) {
    createError.value = eduErrorText(error)
  } finally {
    creating.value = false
  }
}
function openRename() {
  editingClassId.value = selectedClassId.value
  className.value = selectedClass.value.name
  createError.value = ''
  createOpen.value = true
}

async function deleteClass() {
  const classroom = selectedClass.value
  if (!classroom || busy.value) return
  try {
    await ElMessageBox.confirm('删除“' + classroom.name + '”后，本课程将不再向该班学生开放。已有学习记录会保留。', '删除教学班', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch { return }
  busy.value = true
  operationError.value = ''
  try {
    await deleteCourseTeachingClass(props.courseId, classroom.classId)
    ElMessage.success('教学班已删除')
    emit('changed')
  } catch (cause) { operationError.value = eduErrorText(cause) }
  finally { busy.value = false }
}

async function removeStudents(students) {
  const classId = selectedClassId.value
  if (busy.value || !students.length) return
  try {
    await ElMessageBox.confirm('确定将选中的 ' + students.length + ' 名学生移出“' + selectedClass.value.name + '”？', '移除学生', {
      type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消'
    })
  } catch { return }
  busy.value = true
  operationError.value = ''
  try {
    await changeCourseClassStudents(props.courseId, classId, { userIds: students.map(item => item.userId) })
    ElMessage.success('学生已移出教学班')
    refreshRoster()
  } catch (cause) { operationError.value = eduErrorText(cause) }
  finally { busy.value = false }
}

function openMove(students) {
  movingMembers.value = [...students]
  targetClassId.value = null
  moveError.value = ''
  moveOpen.value = true
}

async function moveStudents() {
  if (busy.value || !targetClassId.value) return
  busy.value = true
  moveError.value = ''
  try {
    await changeCourseClassStudents(props.courseId, selectedClassId.value, {
      userIds: movingMembers.value.map(item => item.userId), targetClassId: targetClassId.value
    })
    moveOpen.value = false
    ElMessage.success('学生已调整到目标教学班')
    refreshRoster()
  } catch (cause) { moveError.value = eduErrorText(cause) }
  finally { busy.value = false }
}
</script>

<style scoped>
.class-actions { margin-left: auto; display: flex; gap: 8px; }
.selection-toolbar { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 12px; }
.move-error { margin-top: 12px; }
.class-card :deep(.el-card__body) {
  padding: 0;
}
.class-layout {
  display: grid;
  grid-template-columns: minmax(215px, 260px) minmax(0, 1fr);
  min-height: 390px;
}
.class-sidebar {
  padding: 16px;
  border-right: 1px solid var(--el-border-color-lighter);
}
.class-sidebar-actions,
.roster-heading,
.roster-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.class-sidebar-actions {
  justify-content: space-between;
  margin-bottom: 14px;
}
.class-sidebar h3,
.roster-heading h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
}
.roster-heading span,
.class-empty,
.class-item small {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.class-list {
  display: grid;
  gap: 4px;
  margin: 12px -4px 0;
  padding: 0 4px;
  max-height: 55vh;
  overflow-y: auto;
  list-style: none;
}
.class-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 42px;
  padding: 8px 10px;
  border: 0;
  border-radius: var(--el-border-radius-base);
  background: transparent;
  color: var(--el-text-color-regular);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.class-name {
  overflow-wrap: anywhere;
}
.class-item small {
  flex-shrink: 0;
}
.class-item:hover,
.class-item.is-selected {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.class-roster {
  padding: 16px 20px;
  min-width: 0;
}
.roster-toolbar {
  justify-content: space-between;
  margin: 16px 0 12px;
}
.roster-toolbar > .el-input {
  width: min(240px, 100%);
}
@media (max-width: 740px) {
  .class-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .class-sidebar {
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
  .class-list {
    max-height: 200px;
  }
}
</style>
