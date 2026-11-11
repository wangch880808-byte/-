import { reactive } from 'vue'
export const educationRuntime = reactive({ epoch: 0, blocked: '', pending: false })
export function resetView(message='') { educationRuntime.epoch++;educationRuntime.blocked=message }
