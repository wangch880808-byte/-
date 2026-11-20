<script setup>
defineProps({
  list: { type: Array, default: () => [] },
  bindingId: { type: [String, Number], default: '' },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})
defineEmits(['select', 'refresh'])
</script>

<template>
  <div class="g3-context">
    <span class="g3-context-label">当前课程</span>
    <el-select :model-value="bindingId" :loading="loading" placeholder="请选择课程与教学班"
      style="width: 320px" @update:model-value="$emit('select', $event)">
      <el-option v-for="item in list" :key="item.bindingId"
        :label="`${item.courseTitle} / ${item.className}${item.termName ? ' / ' + item.termName : ''}`"
        :value="String(item.bindingId)" />
    </el-select>
    <el-button :loading="loading" @click="$emit('refresh')">刷新</el-button>
    <span v-if="error" class="g3-context-error">{{ error }}</span>
  </div>
</template>

<style scoped>
.g3-context { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; }
.g3-context-label { color: #606266; font-size: 14px; }
.g3-context-error { color: #f56c6c; font-size: 13px; }
</style>
