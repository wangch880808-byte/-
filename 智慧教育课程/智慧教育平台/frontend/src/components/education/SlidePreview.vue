<template>
  <div class="slide-preview">
    <img v-if="objectUrl" :src="objectUrl" alt="课件预览" />
    <div v-else class="fallback">
      <span class="page-no">第 {{ pageNo || '—' }} 页</span>
      <span class="hint">{{ hint }}</span>
    </div>
  </div>
</template>
<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { fetchPreviewObjectUrl } from '@/api/education/classroom'

const props = defineProps({
  previewUrl: { type: String, default: '' },
  pageNo: { type: [Number, String], default: 1 },
  emptyText: { type: String, default: '课件预览暂不可用，页码仍按服务端快照同步' }
})

const objectUrl = ref('')
const hint = ref(props.emptyText)

watch(() => [props.previewUrl, props.pageNo], async () => {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
  hint.value = '正在加载课件预览…'
  const next = await fetchPreviewObjectUrl(props.previewUrl)
  objectUrl.value = next || ''
  hint.value = props.emptyText
}, { immediate: true })

onBeforeUnmount(() => {
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
})
</script>
<style scoped>
.slide-preview {
  min-height: 180px;
  border-radius: 10px;
  background: #f5f7fb;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  margin-bottom: 12px;
}
.slide-preview img {
  max-width: 100%;
  max-height: 360px;
  object-fit: contain;
  display: block;
}
.fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 16px;
  text-align: center;
}
.page-no { font-size: 28px; font-weight: 700; color: #235bdb; }
.hint { font-size: 13px; color: #6b7280; }
</style>
