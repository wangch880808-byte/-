<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  options: { type: Array, required: true }
})
const model = defineModel({ type: String, required: true })
const picker = ref(null)

const multipleSchools = computed(() => new Set(
  props.options.map(option => option.schoolId).filter(Boolean)
).size > 1)

const choices = computed(() => {
  const pathCounts = new Map()
  for (const option of props.options) {
    pathCounts.set(option.path, (pathCounts.get(option.path) || 0) + 1)
  }
  return props.options.map(option => {
    const number = pathCounts.get(option.path) > 1 ? '编号 ' + option.value : ''
    const school = multipleSchools.value || !option.collegeName ? option.schoolName : ''
    return {
      ...option,
      label: [option.path, number].filter(Boolean).join(' · '),
      detail: [option.majorName, number].filter(Boolean).join(' · '),
      context: [school, option.collegeName, option.majorName, number].filter(Boolean).join(' · ')
    }
  })
})

const groups = computed(() => {
  const byOrganization = new Map()
  for (const option of choices.value) {
    let key = 'other'
    let label = '其他班级'
    if (option.original) {
      key = 'original'
      label = '当前归属'
    } else if (option.collegeName) {
      key = 'college-' + option.collegeId
      label = [option.collegeName, multipleSchools.value && option.schoolName].filter(Boolean).join(' · ')
    } else if (option.schoolName) {
      key = 'school-' + option.schoolId
      label = option.schoolName
    }

    if (!byOrganization.has(key)) byOrganization.set(key, { value: key, label, children: [] })
    byOrganization.get(key).children.push(option)
  }
  return [...byOrganization.values()]
})

const selection = computed(() => choices.value.find(option => option.value === model.value))

function selectClass(value) {
  const node = picker.value?.getNode(value)
  if (node?.isLeaf && node.visible && node.parent?.expanded) model.value = value
}

function toggleGroup(_data, node) {
  if (node.isLeaf) return
  if (node.expanded) node.collapse()
  else node.expand()
}

function activateTreeItem(event) {
  if (event.target.getAttribute('role') !== 'treeitem') return
  event.preventDefault()
  event.target.click()
}
</script>

<template>
  <el-tree-select
    ref="picker"
    :model-value="model"
    :data="groups"
    :expand-on-click-node="false"
    class="student-class-select"
    popper-class="student-class-select-popper"
    placeholder="选择或搜索班级"
    no-match-text="没有匹配的班级"
    no-data-text="暂无可选班级"
    filterable
    fit-input-width
    @update:model-value="selectClass"
    @node-click="toggleGroup"
    @keydown.enter="activateTreeItem"
  >
    <template #label="{ label }">
      <span class="selected-class" :title="label">
        {{ selection?.name || label }}<span v-if="selection?.context" class="class-detail"> · {{ selection.context }}</span>
      </span>
    </template>

    <template #default="{ node, data }">
      <div :title="data.label">
        <span v-if="!node.isLeaf" class="class-group">{{ data.label }}</span>
        <template v-else>
          <span class="class-name">{{ data.name }}</span>
          <span v-if="data.detail" class="class-detail">{{ data.detail }}</span>
        </template>
      </div>
    </template>
  </el-tree-select>
</template>

<style scoped>
.student-class-select {
  width: 100%;
  min-width: 0;
}

.selected-class,
.class-group,
.class-name,
.class-detail {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.class-detail {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}

.selected-class .class-detail {
  display: inline;
}

:global(.student-class-select-popper .el-tree-node__content) {
  height: auto;
  min-height: 34px;
}

:global(.student-class-select-popper .el-select-dropdown__item) {
  height: auto;
  min-height: 34px;
  padding-block: 7px;
  line-height: 19px;
}

.class-group {
  color: var(--el-text-color-regular);
  font-weight: 600;
}
</style>
