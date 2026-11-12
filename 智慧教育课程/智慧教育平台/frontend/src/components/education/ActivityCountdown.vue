<template>
  <span v-if="label" class="countdown" :class="{ due: seconds != null && seconds <= 0 }">{{ label }}</span>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { formatCountdown, remainingSeconds } from '@/utils/classroom'

const props = defineProps({
  closesAt: { type: String, default: '' },
  serverTime: { type: String, default: '' }
})

const now = ref(Date.now())
let timer = null
const seconds = computed(() => remainingSeconds(props.closesAt, props.serverTime, now.value))
const label = computed(() => {
  if (!props.closesAt) return ''
  const text = formatCountdown(seconds.value)
  return text === '已截止' ? '已截止' : `剩余 ${text}`
})

onMounted(() => {
  timer = window.setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>
<style scoped>
.countdown { font-size: 12px; color: #b45309; margin-left: 8px; }
.countdown.due { color: #b91c1c; }
</style>
