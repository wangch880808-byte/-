<template>
  <span v-if="termName" class="current-term" title="当前学期">{{ termName }}</span>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { api } from '@/api/education/client'
import { context } from '@/education/context'
import { hasPermission, session } from '@/education/session'

const currentTerm = ref(null)
const loaded = ref(false)
let requestVersion = 0
let controller
const termName = computed(() => loaded.value
  ? currentTerm.value?.name || currentTerm.value?.code || '未设置当前学期'
  : context.term?.termName || context.term?.termCode || '')

async function loadTerm() {
  const version = ++requestVersion
  controller?.abort()
  currentTerm.value = null
  loaded.value = false
  if (!hasPermission('education:scope:read')) return
  controller = new AbortController()
  try {
    const response = await api.get('/education/scope/term/current', {
      scope: 'session', signal: controller.signal, silent403: true
    })
    if (version !== requestVersion) return
    currentTerm.value = response.data
    loaded.value = true
  } catch {
    // The teaching context may still supply the current term if this request is unavailable.
  }
}

watch(() => [session.ready, session.role, session.user?.userId], loadTerm, { immediate: true })
onMounted(() => window.addEventListener('edu:refresh-term', loadTerm))
onUnmounted(() => {
  requestVersion++
  controller?.abort()
  window.removeEventListener('edu:refresh-term', loadTerm)
})
</script>

<style scoped>
.current-term {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border: 1px solid var(--el-border-color-lighter, #e6e9ef);
  border-radius: 16px;
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-regular, #606266);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}
</style>
