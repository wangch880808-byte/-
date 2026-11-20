<template>
  <section v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-button v-if="error" class="mt10" @click="load">重试</el-button>
    <template v-else-if="bindingId">
      <div v-if="bindings.length > 1" class="group3-class">
        <label for="group3-class-select">教学班</label>
        <el-select id="group3-class-select" :model-value="bindingId" @change="changeBinding">
          <el-option v-for="item in bindings" :key="item.bindingId" :value="String(item.bindingId)" :label="item.className" />
        </el-select>
      </div>
      <slot :binding-id="bindingId" />
    </template>
    <el-empty v-else-if="!loading" :description="emptyMessage" :image-size="80" />
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listContexts } from '@/api/education/group3'
import { eduErrorText } from '@/api/education/http'

const props = defineProps({
  courseId: { type: [String, Number], required: true },
  emptyMessage: { type: String, default: '此课程没有可访问的教学班。' }
})
const route = useRoute()
const router = useRouter()
const bindings = ref([])
const loading = ref(false)
const error = ref('')
let requestId = 0

const bindingId = computed(() => {
  const requested = String(route.query.bindingId || '')
  const requestedClass = String(route.query.classId || '')
  const binding = bindings.value.find(item => String(item.bindingId) === requested)
    || bindings.value.find(item => String(item.teachingClassId) === requestedClass)
    || bindings.value[0]
  return binding ? String(binding.bindingId) : ''
})

async function load() {
  const id = ++requestId
  const courseId = String(props.courseId)
  bindings.value = []
  error.value = ''
  loading.value = true
  try {
    const result = await listContexts()
    if (id === requestId) {
      bindings.value = (result.data || []).filter(item => String(item.courseSpaceId) === courseId)
    }
  } catch (e) {
    if (id === requestId) error.value = eduErrorText(e)
  } finally {
    if (id === requestId) loading.value = false
  }
}

function changeBinding(value) {
  const selected = bindings.value.find(item => String(item.bindingId) === value)
  if (!selected) return
  const { topicId, assignmentId, resourceId, ...query } = route.query
  router.replace({ query: { ...query, classId: String(selected.teachingClassId), bindingId: value } })
}

watch(() => props.courseId, load, { immediate: true })
</script>

<style scoped>
.group3-class {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.group3-class .el-select {
  max-width: 280px;
}
</style>
