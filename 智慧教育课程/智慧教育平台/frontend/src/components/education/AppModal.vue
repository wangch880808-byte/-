<script setup>
defineProps({
  title: { type: String, default: '' },
  wide: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  error: { type: String, default: '' },
  okText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  hideCancel: { type: Boolean, default: false },
  danger: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'confirm'])
</script>

<template>
  <div class="modal-mask" @click.self="!busy && emit('close')">
    <div class="modal" :class="{ wide }">
      <div class="modal-head">
        <h3>{{ title }}</h3>
        <button class="modal-close" type="button" aria-label="关闭" :disabled="busy" @click="emit('close')">×</button>
      </div>
      <div class="modal-body">
        <slot />
      </div>
      <div class="modal-foot">
        <span class="modal-error">{{ error }}</span>
        <button v-if="!hideCancel" class="ui-btn" type="button" :disabled="busy" @click="emit('close')">
          {{ cancelText }}
        </button>
        <button class="ui-btn primary" :class="{ danger }" type="button" :disabled="busy" @click="emit('confirm')">
          {{ busy ? '处理中…' : okText }}
        </button>
      </div>
    </div>
  </div>
</template>
