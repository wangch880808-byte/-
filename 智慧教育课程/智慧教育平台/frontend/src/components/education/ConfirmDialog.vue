<script setup>
import AppModal from '@/components/education/AppModal.vue'

defineProps({
  text: { type: String, default: '' },
  warnings: { type: Array, default: () => [] },
  title: { type: String, default: '请确认' },
  busy: { type: Boolean, default: false },
  confirmDisabled: { type: Boolean, default: false },
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
    :confirm-disabled="confirmDisabled"
    :error="error"
    :danger="danger"
    :ok-text="okText"
    @close="emit('close')"
    @confirm="emit('confirm')"
  >
    <p class="confirm-text">{{ text }}</p>
    <ul v-if="warnings.length" class="confirm-warnings">
      <li v-for="warning in warnings" :key="warning">{{ warning }}</li>
    </ul>
  </AppModal>
</template>

<style scoped>
.confirm-warnings {
  margin: 12px 0 0;
  padding: 10px 12px 10px 30px;
  border-radius: 6px;
  background: #fff5ed;
  color: #8d3314;
  line-height: 1.6;
}
</style>
