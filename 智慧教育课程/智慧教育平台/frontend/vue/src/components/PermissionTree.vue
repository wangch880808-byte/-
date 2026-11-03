<script setup>
import { computed, reactive, watch } from 'vue'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  modelValue: { type: Array, default: () => [] },
  includeAncestors: { type: Boolean, default: true },
  actions: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue'])

const checked = reactive({})
const flat = computed(() => {
  const result = []
  const visit = (items, depth, parentId) => {
    ;(items || []).forEach((node) => {
      result.push({ id: String(node.id), label: node.label, depth, parentId: parentId ? String(parentId) : null })
      if (node.children && node.children.length) visit(node.children, depth + 1, node.id)
    })
  }
  visit(props.nodes, 0, null)
  return result
})
const nodeMap = computed(() => {
  const map = new Map()
  flat.value.forEach((node) => map.set(node.id, node))
  return map
})

watch(
  () => props.modelValue,
  (value) => {
    Object.keys(checked).forEach((key) => delete checked[key])
    ;(value || []).forEach((id) => (checked[String(id)] = true))
  },
  { immediate: true, deep: true }
)

function isChecked(id) {
  return checked[String(id)] === true
}

function toggle(id) {
  const key = String(id)
  if (checked[key]) delete checked[key]
  else checked[key] = true
  emit('update:modelValue', Object.keys(checked).map(Number))
}

function getCheckedIds() {
  const ids = new Set(Object.keys(checked))
  if (props.includeAncestors) {
    Array.from(ids).forEach((id) => {
      let node = nodeMap.value.get(id)
      while (node && node.parentId) {
        ids.add(node.parentId)
        node = nodeMap.value.get(node.parentId)
      }
    })
  }
  return Array.from(ids).map(Number)
}

function setAll(value) {
  const next = {}
  if (value) flat.value.forEach((node) => (next[node.id] = true))
  Object.keys(checked).forEach((key) => delete checked[key])
  Object.assign(checked, next)
  emit('update:modelValue', Object.keys(checked).map(Number))
}

defineExpose({ getCheckedIds, setAll })
</script>

<template>
  <div class="perm-tree">
    <div v-if="actions" class="perm-actions">
      <button class="ui-btn" type="button" @click="setAll(true)">全选</button>
      <button class="ui-btn" type="button" @click="setAll(false)">清空</button>
    </div>
    <div class="perm-list">
      <label
        v-for="node in flat"
        :key="node.id"
        class="perm-row"
        :style="{ paddingLeft: node.depth * 18 + 6 + 'px' }"
      >
        <input type="checkbox" :checked="isChecked(node.id)" @change="toggle(node.id)" />
        <span>{{ node.label }}</span>
      </label>
      <p v-if="!flat.length" class="state">暂无数据</p>
    </div>
  </div>
</template>
