<script setup>
import AppModal from './AppModal.vue'

defineProps({
  text: { type: String, default: '' },
  title: { type: String, default: '请确认' },
  busy: { type: Boolean, default: false },
  error: { type: String, default: '' },
  danger: { type: Boolean, default: false },
  okText: { type: String, default: '确定' }
})
const emit = defineEmits(['close', 'confirm'])
</script>

<template>
  <AppModal
    :title="title"
    :busy="busy"
    :error="error"
    :danger="danger"
    :ok-text="okText"
    @close="emit('close')"
    @confirm="emit('confirm')"
  >
    <p class="confirm-text">{{ text }}</p>
  </AppModal>
</template>
