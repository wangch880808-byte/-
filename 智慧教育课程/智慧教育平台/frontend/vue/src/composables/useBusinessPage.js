import { onUnmounted, readonly } from 'vue'
import { api } from '../api/client'
import { context, workspace } from '../stores/workspace'
export function useBusinessPage() {
  const controller = new AbortController()
  const epoch = workspace.epoch
  onUnmounted(() => controller.abort())
  return {
    context: readonly(context),
    signal: controller.signal,
    isCurrent: () => !controller.signal.aborted && epoch === workspace.epoch,
    get: (path, options) => api.get(path, { ...options, signal: controller.signal }),
    getContext: () => context.current ? Object.freeze({ ...context.current }) : null
  }
}
