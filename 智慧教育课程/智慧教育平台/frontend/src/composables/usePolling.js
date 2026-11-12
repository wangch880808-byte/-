import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 课堂轮询与网络状态（4-04：断线后重连恢复，不重复提交）。
 *
 * 断网时把 offline 置 true 供页面提示；恢复联网立即补拉一次快照。
 */
export function usePolling(refresh, interval = 3000) {
  const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine !== false)
  let timer = null

  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function start() {
    stop()
    if (typeof window === 'undefined') return
    timer = window.setInterval(() => {
      refresh().catch(() => {})
    }, interval)
  }

  function handleOnline() {
    online.value = true
    refresh().catch(() => {})
  }

  function handleOffline() {
    online.value = false
  }

  onMounted(() => {
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
  })

  onBeforeUnmount(() => {
    stop()
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  })

  return { start, stop, online }
}
