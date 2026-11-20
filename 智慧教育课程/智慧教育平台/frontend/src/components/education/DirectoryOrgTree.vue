<template>
  <el-card shadow="never" class="edu-tree-card" :class="{ compact }">
    <template #header>
      <div class="edu-card-title">
        <span>组织架构</span>
        <el-button link type="primary" @click="clearDept">全部</el-button>
      </div>
    </template>
    <el-tree
      ref="treeRef"
      :data="tree"
      :props="{ label: 'name', children: 'children' }"
      node-key="id"
      :default-expand-all="!compact"
      :default-expanded-keys="expandedKeys"
      highlight-current
      :expand-on-click-node="false"
      @node-click="(node) => emit('select', node.id, node)"
    />
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOrgTree } from '@/api/education/directory'

const props = defineProps({
  teacherOnly: Boolean,
  throughMajor: Boolean,
  rows: Array,
  compact: Boolean,
  occupiedOnly: Boolean
})
const emit = defineEmits(['select'])
const directory = ref([])
const treeRef = ref()

function clearDept() {
  treeRef.value?.setCurrentKey(null)
  emit('select', null)
}

function schoolBranches(rows) {
  const schools = new Set(rows
    .filter(row => String(row.parentId) === '0' && (!row.deptType || row.deptType === 'SCHOOL'))
    .map(row => String(row.id)))
  const colleges = new Set(rows
    .filter(row => schools.has(String(row.parentId))
      && (row.deptType === 'COLLEGE' || (!row.deptType && /(?:学院|系)$/.test(row.name.trim()))))
    .map(row => String(row.id)))
  return rows.flatMap(row => {
    if (schools.has(String(row.id)) || colleges.has(String(row.id))) return [row]
    if (colleges.has(String(row.parentId)) && (!row.deptType || row.deptType === 'MAJOR')) {
      return [{ ...row, deptType: 'MAJOR' }]
    }
    return []
  })
}

const tree = computed(() => {
  const rows = props.rows ?? directory.value
  const visible = props.throughMajor ? schoolBranches(rows) : rows
  const nodes = new Map(visible.map(row => [String(row.id), { ...row, children: [] }]))
  const roots = []
  for (const node of nodes.values()) {
    const parent = nodes.get(String(node.parentId))
    if (parent && parent !== node) {
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  }
  if (!props.occupiedOnly) return roots

  function keepOccupied(node) {
    node.children = node.children.filter(keepOccupied)
    return Number(node.studentCount) > 0 || node.children.length > 0
  }

  return roots.filter(keepOccupied)
})
const expandedKeys = computed(() => props.compact ? tree.value.map(node => node.id) : [])

onMounted(async () => {
  if (props.rows !== undefined) return
  try {
    const response = await getOrgTree(props.teacherOnly ? { teacherOnly: true } : undefined)
    directory.value = Array.isArray(response) ? response : response.data || []
  } catch {
    directory.value = []
  }
})
</script>

<style scoped>
.edu-card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.edu-tree-card {
  min-height: 420px;
}
.edu-tree-card.compact {
  min-height: 0;
}
.compact :deep(.el-card__header),
.compact :deep(.el-card__body) {
  padding: 10px;
}
.compact :deep(.el-card__body) {
  max-height: 412px;
  overflow: auto;
}
.compact :deep(.el-tree-node__content) {
  height: auto;
  min-height: 32px;
}
.compact :deep(.el-tree-node__label) {
  white-space: normal;
  line-height: 1.4;
  padding-block: 5px;
}
</style>
