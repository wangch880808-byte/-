<template>
  <el-dialog
    v-model="open"
    :title="fromClass ? '从课程班级添加' : '从学生库添加'"
    :width="fromClass ? 'min(740px, 94vw)' : 'min(1080px, 96vw)'"
    append-to-body
    :show-close="!submitting"
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
  >
    <div class="picker-layout" :class="{ 'library-layout': !fromClass }" v-loading="submitting">
      <aside v-if="!fromClass" class="picker-org" v-loading="sourceLoading">
        <directory-org-tree
          :rows="organizations"
          :occupied-only="occupiedOnly"
          compact
          @select="selectOrganization"
        />
      </aside>
      <section class="picker-roster" aria-label="可添加学生">
        <el-form class="picker-filters" label-position="top" @submit.prevent="search">
          <el-form-item v-if="fromClass" label="来源课程和教学班">
            <el-select
              v-model="sourceId"
              :loading="sourceLoading"
              :disabled="submitting"
              filterable
              placeholder="请选择课程和教学班"
              no-data-text="暂无可用的课程班级"
              @change="changeSource"
            >
              <el-option
                v-for="source in sources"
                :key="sourceKey(source)"
                :value="sourceKey(source)"
                :label="[source.courseTitle, source.className].filter(Boolean).join(' / ')"
              />
            </el-select>
          </el-form-item>
          <div v-if="!fromClass && department" class="picker-scope">
            <strong>{{ department.name }}</strong>
            <span v-if="!loading">（{{ total }} 名可添加）</span>
          </div>
          <div class="picker-search">
            <el-input
              v-model="keyword"
              clearable
              maxlength="100"
              aria-label="学生学号或姓名"
              placeholder="按学号或姓名搜索"
              :disabled="submitting || !hasSource"
              @clear="search"
            />
            <el-button
              native-type="submit"
              :loading="loading"
              :disabled="submitting || !hasSource"
            >搜索</el-button>
          </div>
        </el-form>

        <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon class="picker-error" />
        <el-table
          ref="table"
          v-loading="loading"
          :data="candidates"
          row-key="userId"
          :row-class-name="() => wholeClass ? 'whole-class-row' : ''"
          border
          height="330"
          @selection-change="selection = $event"
        >
          <el-table-column type="selection" width="50" reserve-selection :selectable="canSelectRow" />
          <el-table-column prop="nickName" label="姓名" min-width="105" />
          <el-table-column prop="userName" label="学号" min-width="125" />
          <el-table-column prop="deptName" label="所属组织" min-width="150" show-overflow-tooltip />
          <template #empty>{{ emptyText }}</template>
        </el-table>
        <el-pagination
          v-if="total > 0"
          class="picker-pages"
          :current-page="page"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 30, 50, 100]"
          :disabled="loading || submitting"
          layout="total, prev, pager, next, sizes"
          @update:current-page="changePage"
          @update:page-size="changePageSize"
        />
      </section>
    </div>
    <template #footer>
      <div class="picker-footer">
        <div class="picker-options">
          <el-checkbox v-if="!fromClass" v-model="includeDescendants"
            :disabled="submitting || isAdministrativeClass" @change="changeFilter">显示下级组织的学生</el-checkbox>
          <el-checkbox v-if="!fromClass" v-model="occupiedOnly"
            :disabled="submitting">只显示有学生的组织</el-checkbox>
          <el-checkbox v-if="isAdministrativeClass" :model-value="wholeClass"
            :disabled="!canSelectWholeClass" @change="chooseWholeClass">选中整班</el-checkbox>
          <span aria-live="polite">{{ wholeClass ? '已选整班' : '已选 ' + selection.length + ' 人' }}</span>
        </div>
        <div>
          <el-button :disabled="submitting" @click="open = false">取消</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="loading || (!wholeClass && selection.length === 0)"
            @click="addStudents"
          >添加到本班</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import DirectoryOrgTree from '@/components/education/DirectoryOrgTree.vue'
import {
  addCourseClassStudents, listCourseClassStudentCandidates, listCourseClassStudentOrganizations, listCourseClassStudentSources
} from '@/api/education/course'
import { eduErrorText } from '@/api/education/http'

const open = defineModel({ type: Boolean, required: true })
const props = defineProps({
  courseId: { type: [String, Number], required: true },
  classId: { type: [String, Number], required: true },
  fromClass: Boolean
})
const emit = defineEmits(['added'])
const table = ref()
const keyword = ref('')
const appliedKeyword = ref('')
const candidates = ref([])
const selection = ref([])
const page = ref(1)
const total = ref(0)
const pageSize = ref(20)
const organizations = ref([])
const department = ref(null)
const includeDescendants = ref(true)
const occupiedOnly = ref(false)
const wholeClass = ref(false)
const sources = ref([])
const sourceId = ref('')
const sourceLoading = ref(false)
const loading = ref(false)
const submitting = ref(false)
const failure = ref('')
let activeRequest = 0

const isAdministrativeClass = computed(() => department.value?.deptType === 'CLASS')
const canSelectWholeClass = computed(() => !loading.value && !submitting.value && !failure.value
  && !keyword.value.trim() && !appliedKeyword.value && total.value > 0 && total.value <= 1000)

function sourceKey(source) {
  return source.courseId + ':' + source.teachingClassId
}

const source = computed(() => sources.value.find(item => sourceKey(item) === sourceId.value))
const hasSource = computed(() => props.fromClass ? Boolean(source.value) : Boolean(department.value))
const emptyText = computed(() => {
  if (!props.fromClass) {
    if (sourceLoading.value) return '正在加载组织架构'
    if (!department.value) return '请选择左侧组织或行政班'
    return '该范围内暂无可添加的学生'
  }
  if (sourceId.value) return '暂无可添加的学生'
  if (!sourceLoading.value && !sources.value.length) return '你其他课程班级中暂无可添加的来源'
  return '请先选择来源课程和教学班'
})

function sourceParams() {
  if (props.fromClass) {
    return source.value ? { sourceCourseId: source.value.courseId, sourceClassId: source.value.teachingClassId } : {}
  }
  return {
    sourceDeptId: department.value?.id,
    includeDescendants: includeDescendants.value && !isAdministrativeClass.value
  }
}

watch(keyword, () => { wholeClass.value = false })

watch(open, visible => {
  if (!visible) {
    activeRequest++
    return
  }
  candidates.value = []
  total.value = 0
  page.value = 1
  keyword.value = ''
  appliedKeyword.value = ''
  department.value = null
  organizations.value = []
  includeDescendants.value = true
  occupiedOnly.value = false
  sourceId.value = ''
  sources.value = []
  failure.value = ''
  loading.value = false
  sourceLoading.value = false
  clearSelection()
  if (props.fromClass) {
    loadSources()
  } else {
    loadOrganizations()
  }
})

async function loadOrganizations() {
  const request = ++activeRequest
  sourceLoading.value = true
  try {
    const res = await listCourseClassStudentOrganizations(props.courseId, props.classId)
    if (request === activeRequest) {
      organizations.value = res.data || []
      if (!organizations.value.length) failure.value = '暂无可用的学校组织'
    }
  } catch (error) {
    if (request === activeRequest) failure.value = eduErrorText(error)
  } finally {
    if (request === activeRequest) sourceLoading.value = false
  }
}

function selectOrganization(_id, node) {
  if (submitting.value || props.fromClass) return
  const next = node || organizations.value.find(item => String(item.parentId) === '0')
  if (!next || (node && next.id === department.value?.id)) return
  department.value = next
  if (!node) includeDescendants.value = true
  changeFilter()
}

async function loadSources() {
  const request = ++activeRequest
  sourceLoading.value = true
  try {
    const res = await listCourseClassStudentSources(props.courseId, props.classId)
    if (request === activeRequest) sources.value = res.data || []
  } catch (error) {
    if (request === activeRequest) failure.value = eduErrorText(error)
  } finally {
    if (request === activeRequest) sourceLoading.value = false
  }
}

function clearSelection() {
  wholeClass.value = false
  table.value?.clearSelection()
  selection.value = []
}

function changeSource() {
  changeFilter()
}

function changeFilter() {
  candidates.value = []
  total.value = 0
  search()
}

function search() {
  clearSelection()
  appliedKeyword.value = keyword.value.trim()
  page.value = 1
  loadCandidates()
}

function chooseWholeClass(checked) {
  clearSelection()
  wholeClass.value = checked
}

function changePageSize(size) {
  pageSize.value = size
  page.value = 1
  loadCandidates()
}

function changePage(nextPage) {
  if (page.value === nextPage) return
  page.value = nextPage
  loadCandidates()
}

async function loadCandidates() {
  if (!hasSource.value) return
  const request = ++activeRequest
  loading.value = true
  failure.value = ''
  try {
    const res = await listCourseClassStudentCandidates(props.courseId, props.classId, {
      ...sourceParams(),
      keyword: appliedKeyword.value,
      pageNum: page.value,
      pageSize: pageSize.value
    })
    if (request !== activeRequest) return
    candidates.value = res.rows || []
    total.value = Number(res.total) || 0
  } catch (error) {
    if (request !== activeRequest) return
    candidates.value = []
    total.value = 0
    failure.value = eduErrorText(error)
  } finally {
    if (request === activeRequest) loading.value = false
  }
}

function canSelectRow() {
  return !loading.value && !submitting.value && !wholeClass.value
}

async function addStudents() {
  if (submitting.value || loading.value || !hasSource.value || (!wholeClass.value && !selection.value.length)) return
  submitting.value = true
  failure.value = ''
  try {
    const res = await addCourseClassStudents(props.courseId, props.classId, {
      ...sourceParams(),
      ...(wholeClass.value ? { wholeClass: true } : { userIds: selection.value.map(item => item.userId) })
    })
    ElMessage.success(res.data || res.msg || '学生已添加')
    open.value = false
    emit('added')
  } catch (error) {
    clearSelection()
    await loadCandidates()
    failure.value = eduErrorText(error)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.library-layout {
  display: grid;
  grid-template-columns: minmax(210px, 30%) minmax(0, 1fr);
  gap: 16px;
}
.picker-roster {
  min-width: 0;
}
.picker-scope {
  margin-bottom: 12px;
}
.picker-scope span {
  color: var(--el-text-color-secondary);
}
.picker-options {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
}
.picker-options :deep(.el-checkbox) {
  margin-right: 0;
}
.picker-footer > div:last-child {
  flex-shrink: 0;
}
.picker-roster :deep(.whole-class-row) {
  background-color: var(--el-color-primary-light-9);
}
@media (max-width: 680px) {
  .library-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .picker-org :deep(.el-card__body) {
    max-height: 165px;
  }
  .picker-footer {
    flex-wrap: wrap;
  }
}
.picker-search,
.picker-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}
.picker-search,
.picker-error {
  margin-bottom: 12px;
}
.picker-pages {
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
