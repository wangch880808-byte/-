<template>
  <div class="edu-outline">
    <el-card shadow="never" class="edu-block">
      <template #header><span>课程简介</span></template>
      <p class="edu-text">{{ course.summary || '教师还没有填写课程简介。' }}</p>
      <p class="edu-label">教学目标</p>
      <p class="edu-text">{{ course.learningObjectives || '教师还没有填写教学目标。' }}</p>
    </el-card>

    <el-card shadow="never" class="edu-block">
      <template #header>
        <div class="edu-head">
          <span>课程目录<span v-if="readonly" class="edu-tip">已学 {{ learnedCount }}/{{ totalCount }}</span></span>
          <course-outline-editor
            v-if="!readonly && (course.canManageOutline || course.canManagePoints) && course.status !== 'ARCHIVED'"
            :course="course"
            :can-outline="course.canManageOutline"
            :can-points="course.canManagePoints"
            @saved="$emit('saved')"
          />
        </div>
      </template>

      <div v-if="readonly && lastUnit" class="edu-last-learn">
        <span>上次学到：{{ lastUnit.title }}</span>
        <el-button link type="primary" @click="showContent(lastUnit, 'units')">继续学习</el-button>
      </div>

      <div v-if="units.length">
        <div v-for="unit in units" :key="unit.id" class="edu-unit">
          <div class="edu-unit-line">
            <button type="button" class="outline-link edu-unit-title" @click="showContent(unit, 'units')">{{ unit.title }}</button>
            <span v-if="readonly" class="edu-mark" :class="{ learned: isLearned(unit) }" @click.stop="toggleLearned(unit)">
              {{ isLearned(unit) ? '已学' : '未学' }}
            </span>
          </div>
          <div v-for="child in unit.children || []" :key="child.id" class="edu-section">
            <div class="edu-section-line">
              <button type="button" class="outline-link" @click="showContent(child, 'units')">{{ child.title }}</button>
              <span v-if="readonly" class="edu-mark" :class="{ learned: isLearned(child) }" @click.stop="toggleLearned(child)">
                {{ isLearned(child) ? '已学' : '未学' }}
              </span>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else description="还没有章节，学生按章节顺序学习" :image-size="60" />

      <div v-if="knowledgePoints.length" class="edu-kp">
        <p class="edu-label">知识点</p>
        <el-button v-for="point in knowledgePoints" :key="point.id" class="edu-kp-tag" size="small" @click="showContent(point, 'points')">
          {{ point.code ? point.code + ' ' + point.name : point.name }}
        </el-button>
      </div>
    </el-card>

    <el-drawer v-model="contentOpen" :title="selectedNode?.title || '学习内容'" size="min(720px, 94vw)"
      append-to-body destroy-on-close>
      <outline-content v-if="contentOpen && selectedNode" :key="`${selectedNode.kind}-${selectedNode.id}`"
        :course-id="course.id" :node="selectedNode" :editable="!readonly && (course.canManageOutline || course.canManagePoints) && course.status !== 'ARCHIVED'" />
    </el-drawer>
    <slot />
  </div>
</template>

<script setup name="CourseOutline">
import { computed, ref, watch } from 'vue'
import OutlineContent from './OutlineContent.vue'
import CourseOutlineEditor from './CourseOutlineEditor.vue'
import { markCourseUnit, viewCourseUnit } from '@/api/education/course'
defineEmits(['saved'])

const props = defineProps({
  course: { type: Object, required: true },
  readonly: { type: Boolean, default: false }
})

const units = computed(() => props.course.units || [])
const knowledgePoints = computed(() => props.course.knowledgePoints || [])
const contentOpen = ref(false)
const selectedNode = ref(null)
const learnedIds = ref(new Set((props.course.learnedUnitIds || []).map(String)))
watch(() => props.course.learnedUnitIds, value => {
  learnedIds.value = new Set((value || []).map(String))
})

const flatUnits = computed(() => units.value.flatMap(u => [u, ...(u.children || [])]))
const learnedCount = computed(() => flatUnits.value.filter(u => learnedIds.value.has(String(u.id))).length)
const totalCount = computed(() => flatUnits.value.length)
const isLearned = unit => learnedIds.value.has(String(unit.id))

const lastUnit = computed(() => {
  const id = props.course.lastUnitId
  if (id == null) return null
  for (const unit of flatUnits.value) {
    if (String(unit.id) === String(id)) return unit
  }
  return null
})

function applyProgress(data) {
  if (data && Array.isArray(data.learnedUnitIds)) {
    learnedIds.value = new Set(data.learnedUnitIds.map(String))
  }
}

async function recordView(unitId) {
  try {
    const res = await viewCourseUnit(props.course.id, unitId)
    applyProgress(res.data)
  } catch (error) {
    /* 未登录态或已失效时忽略，不影响查看 */
  }
}

async function toggleLearned(unit) {
  if (!props.readonly) return
  try {
    const res = await markCourseUnit(props.course.id, unit.id, !isLearned(unit))
    applyProgress(res.data)
  } catch (error) {
    /* 失败时保持原状态 */
  }
}

function showContent(node, kind) {
  selectedNode.value = { id: node.id, kind, title: node.title || node.name }
  contentOpen.value = true
  if (props.readonly && kind === 'units') recordView(node.id)
}
</script>

<style scoped>
.edu-block {
  margin-bottom: 0;
  border-bottom: 1px solid #f0f0f0;
}
.edu-block:last-of-type {
  border-bottom: none;
}
.edu-block :deep(.el-card__header) {
  padding: 0 0 10px;
  border-bottom: none;
  font-weight: 600;
}
.edu-block :deep(.el-card__body) {
  padding: 14px 0 16px;
}
.edu-text {
  color: #606266;
  line-height: 1.8;
  margin: 0 0 12px;
  white-space: pre-wrap;
}
.edu-label {
  font-size: 13px;
  color: #909399;
  margin: 0 0 6px;
}
.edu-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.edu-tip {
  font-size: 13px;
  color: #909399;
  font-weight: 400;
  margin-left: 10px;
}
.edu-last-learn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin-bottom: 14px;
  background: #f0f9ff;
  border: 1px solid #bae7ff;
  border-radius: 6px;
  font-size: 14px;
  color: #0958d9;
}
.edu-unit {
  margin-bottom: 14px;
}
.outline-link { background: transparent; border: 0; padding: 0; text-align: left; color: var(--el-color-primary); cursor: pointer; }
.outline-link:hover { text-decoration: underline; }
.edu-unit-line {
  display: flex;
  align-items: center;
  gap: 10px;
}
.edu-unit-title {
  font-weight: 600;
  color: #303133;
  padding-left: 10px;
  border-left: 3px solid #409eff;
  line-height: 1.4;
  margin-bottom: 8px;
}
.edu-section {
  color: #606266;
  padding: 6px 0 6px 16px;
  border-bottom: 1px dashed #ebeef5;
}
.edu-section:last-child {
  border-bottom: none;
}
.edu-section-line {
  display: flex;
  align-items: center;
  gap: 10px;
}
.edu-mark {
  flex-shrink: 0;
  font-size: 13px;
  line-height: 1;
  padding: 2px 8px;
  border-radius: 10px;
  cursor: pointer;
  user-select: none;
  color: #909399;
  border: 1px solid #dcdfe6;
  background: #fff;
}
.edu-mark.learned {
  color: #389e0d;
  border-color: #b7eb8f;
  background: #f6ffed;
}
.edu-kp {
  margin-top: 16px;
}
.edu-kp-tag {
  margin: 0 8px 8px 0;
}
</style>
