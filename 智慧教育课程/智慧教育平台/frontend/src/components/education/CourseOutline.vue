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
          <span>课程目录</span>
          <span v-if="readonly" class="edu-tip">章节由第 3 组（3-03）维护</span>
        </div>
      </template>

      <div v-if="units.length">
        <div v-for="unit in units" :key="unit.id" class="edu-unit">
          <div class="edu-unit-title">{{ unit.title }}</div>
          <div v-for="child in unit.children || []" :key="child.id" class="edu-section">
            {{ child.title }}
          </div>
        </div>
      </div>
      <el-empty v-else description="还没有章节，学生按章节顺序学习" :image-size="60" />

      <div v-if="knowledgePoints.length" class="edu-kp">
        <p class="edu-label">知识点</p>
        <el-tag v-for="point in knowledgePoints" :key="point.id" class="edu-kp-tag" type="info">
          {{ point.code ? point.code + ' ' + point.name : point.name }}
        </el-tag>
      </div>
    </el-card>

    <slot />
  </div>
</template>

<script setup name="CourseOutline">
import { computed } from 'vue'

const props = defineProps({
  course: { type: Object, required: true },
  readonly: { type: Boolean, default: false }
})

const units = computed(() => props.course.units || [])
const knowledgePoints = computed(() => props.course.knowledgePoints || [])
</script>

<style scoped>
.edu-block {
  margin-bottom: 16px;
}
.edu-text {
  color: #606266;
  line-height: 1.8;
  margin: 0 0 12px;
  white-space: pre-wrap;
}
.edu-label {
  font-size: 12px;
  color: #909399;
  margin: 0 0 6px;
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
.edu-unit {
  margin-bottom: 14px;
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
.edu-kp {
  margin-top: 16px;
}
.edu-kp-tag {
  margin: 0 8px 8px 0;
}
</style>
