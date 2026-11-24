<template>
  <el-dialog
    :model-value="modelValue"
    title="题目资源库"
    width="1080px"
    append-to-body
    @update:model-value="handleVisibleChange"
    @open="handleOpen"
  >
    <div class="ql-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索题目名称或题干"
        clearable
        style="width: 260px"
        @keyup.enter="loadList"
        @clear="loadList"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select v-model="query.collegeId" placeholder="学院" clearable style="width: 170px" @change="loadList">
        <el-option
          v-for="item in collegeOptions"
          :key="item.collegeId"
          :label="`${item.collegeName}（${item.shareCount}）`"
          :value="item.collegeId"
        />
      </el-select>
      <el-select v-model="query.questionKind" placeholder="题型" clearable style="width: 130px" @change="loadList">
        <el-option v-for="item in kindOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.difficulty" placeholder="难度" clearable style="width: 120px" @change="loadList">
        <el-option v-for="item in difficultyOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="loadList">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <span class="ql-tip">仅显示已共享且未撤回的题目，答案与解析仅教师可见</span>
    </div>

    <el-alert
      v-if="folderName"
      :title="`引入后将直接放入文件夹「${folderName}」`"
      type="info"
      :closable="false"
      show-icon
      class="ql-gap"
    />

    <el-table
      v-loading="loading"
      :data="rows"
      row-key="shareId"
      height="420"
      empty-text="资源库暂无共享题目"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column label="题目" min-width="300">
        <template #default="{ row }">
          <div class="ql-title">{{ row.title }}</div>
          <div class="ql-stem">{{ row.stem }}</div>
        </template>
      </el-table-column>
      <el-table-column label="题型" width="92">
        <template #default="{ row }">
          <el-tag size="small" type="info" effect="plain">{{ kindLabel(row.questionKind) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="难度" width="82">
        <template #default="{ row }">
          <el-tag size="small" :type="difficultyTag(row.difficulty)" effect="plain">
            {{ difficultyLabel(row.difficulty) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="maxScore" label="分值" width="70" />
      <el-table-column prop="knowledgePoints" label="知识点" min-width="130" show-overflow-tooltip />
      <el-table-column prop="sourceCourseTitle" label="来源课程" min-width="150" show-overflow-tooltip />
      <el-table-column prop="collegeName" label="学院" min-width="130" show-overflow-tooltip />
      <el-table-column prop="sharedByName" label="共享人" width="100" />
      <el-table-column prop="importCount" label="引用" width="64" />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openPreview(row)">预览</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <div class="ql-footer">
        <span class="ql-selected">已选 {{ selection.length }} 道</span>
        <div>
          <el-button @click="handleVisibleChange(false)">取消</el-button>
          <el-button type="primary" :loading="importing" :disabled="selection.length === 0" @click="submitImport">
            引入到题库
          </el-button>
        </div>
      </div>
    </template>

    <el-dialog v-model="previewOpen" title="题目预览" width="680px" append-to-body>
      <div v-loading="previewLoading" class="ql-preview">
        <h4>{{ detail?.title }}</h4>
        <p class="ql-preview-stem">{{ detail?.stem }}</p>
        <ol v-if="detailOptions.length" class="ql-options">
          <li v-for="(option, index) in detailOptions" :key="index">{{ option }}</li>
        </ol>
        <p><strong>答案：</strong>{{ detailAnswer }}</p>
        <p v-if="detail?.explanation"><strong>解析：</strong>{{ detail.explanation }}</p>
        <p class="ql-preview-meta">
          {{ kindLabel(detail?.questionKind) }} · {{ difficultyLabel(detail?.difficulty) }} · {{ detail?.maxScore }} 分
          <span v-if="detail?.knowledgePoints"> · 知识点：{{ detail.knowledgePoints }}</span>
        </p>
      </div>
    </el-dialog>
  </el-dialog>
</template>

<script setup name="QuestionLibraryDialog">
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  listQuestionLibrary,
  listQuestionLibraryColleges,
  getQuestionLibraryDetail,
  importQuestionLibrary
} from '@/api/education/group3'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  bindingId: { type: [String, Number], default: '' },
  folderId: { type: [String, Number], default: null },
  folderName: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'imported'])

const kindOptions = [
  { value: 'SINGLE', label: '单选题' },
  { value: 'MULTIPLE', label: '多选题' },
  { value: 'TRUE_FALSE', label: '判断题' },
  { value: 'SHORT_TEXT', label: '简答题' }
]
const difficultyOptions = [
  { value: 'EASY', label: '简单' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '困难' }
]

const loading = ref(false)
const importing = ref(false)
const rows = ref([])
const selection = ref([])
const collegeOptions = ref([])
const query = reactive({ keyword: '', collegeId: '', questionKind: '', difficulty: '' })
const previewOpen = ref(false)
const previewLoading = ref(false)
const detail = ref(null)

const detailOptions = computed(() => (Array.isArray(detail.value?.options) ? detail.value.options : []))
const detailAnswer = computed(() => formatAnswer(detail.value?.answer))

function handleVisibleChange(value) {
  emit('update:modelValue', value)
}

function handleOpen() {
  loadColleges()
  if (!rows.value.length) {
    loadList()
  }
}

function resetQuery() {
  query.keyword = ''
  query.collegeId = ''
  query.questionKind = ''
  query.difficulty = ''
  loadList()
}

async function loadColleges() {
  try {
    const response = await listQuestionLibraryColleges()
    collegeOptions.value = response.data || []
  } catch (error) {
    collegeOptions.value = []
  }
}

async function loadList() {
  loading.value = true
  try {
    const response = await listQuestionLibrary({
      keyword: query.keyword || undefined,
      collegeId: query.collegeId || undefined,
      questionKind: query.questionKind || undefined,
      difficulty: query.difficulty || undefined
    })
    rows.value = response.data || []
  } catch (error) {
    ElMessage.error('资源库加载失败')
  } finally {
    loading.value = false
  }
}

function handleSelectionChange(selected) {
  selection.value = selected
}

async function openPreview(row) {
  previewOpen.value = true
  previewLoading.value = true
  detail.value = null
  try {
    const response = await getQuestionLibraryDetail(row.shareId)
    detail.value = response.data || null
  } catch (error) {
    ElMessage.error('题目详情加载失败')
  } finally {
    previewLoading.value = false
  }
}

async function submitImport() {
  if (!props.bindingId || selection.value.length === 0) {
    return
  }
  importing.value = true
  try {
    const response = await importQuestionLibrary({
      bindingId: Number(props.bindingId),
      folderId: props.folderId ? Number(props.folderId) : null,
      shareIds: selection.value.map(item => item.shareId)
    })
    const result = response.data || {}
    const skipped = result.skipped || 0
    ElMessage.success(`成功引入 ${result.imported || 0} 道题${skipped ? `，跳过 ${skipped} 道（已存在或已下架）` : ''}`)
    emit('imported', result)
    handleVisibleChange(false)
  } catch (error) {
    ElMessage.error('引入失败')
  } finally {
    importing.value = false
  }
}

function kindLabel(kind) {
  return kindOptions.find(item => item.value === kind)?.label || '题目'
}

function difficultyLabel(difficulty) {
  return difficultyOptions.find(item => item.value === difficulty)?.label || '-'
}

function difficultyTag(difficulty) {
  if (difficulty === 'EASY') return 'success'
  if (difficulty === 'HARD') return 'danger'
  return 'warning'
}

function formatAnswer(answer) {
  if (!answer) return '未设置'
  if (answer.choiceIndex !== undefined && answer.choiceIndex !== null) {
    return String.fromCharCode(65 + Number(answer.choiceIndex))
  }
  if (Array.isArray(answer.choiceIndexes)) {
    return answer.choiceIndexes.map(index => String.fromCharCode(65 + Number(index))).join('、')
  }
  if (answer.value !== undefined && answer.value !== null) {
    return answer.value ? '正确' : '错误'
  }
  if (answer.text) return answer.text
  return JSON.stringify(answer)
}
</script>

<style scoped>
.ql-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.ql-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ql-gap {
  margin-top: 12px;
}

.ql-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ql-stem {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ql-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ql-selected {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.ql-preview {
  min-height: 120px;
}

.ql-preview-stem {
  white-space: pre-wrap;
  line-height: 1.7;
}

.ql-options {
  padding-left: 22px;
  line-height: 1.8;
}

.ql-preview-meta {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
