import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as api from '@/api/education/classroom'

/**
 * 课堂同步：3 秒轮询快照；断线恢复时先 snapshot 再 events?afterSeq= 补查。
 * 不把「已连接」当作状态一致性依据，页码与提交状态以服务端快照为准。
 */
export function useClassroomSync({ getSessionId, view, onSnapshot, interval = 3000 }) {
  const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine !== false)
  const stateSeq = ref(0)
  const syncing = ref(false)
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
      pull('poll').catch(() => {})
    }, interval)
  }

  async function pull(reason = 'poll') {
    const sessionId = getSessionId()
    if (!sessionId || syncing.value) return null
    syncing.value = true
    try {
      const snapshot = await api.getSnapshot(sessionId, view)
      const previous = stateSeq.value
      stateSeq.value = Number(snapshot?.stateSeq) || 0
      if (onSnapshot) await onSnapshot(snapshot, reason)

      // 重连时按契约补查增量事件。公开投影页的 events 接口需登录，匿名投影只依赖 snapshot。
      if ((reason === 'reconnect' || reason === 'resume') && view !== 'public') {
        try {
          const events = await api.getEvents(sessionId, previous, view)
          const latest = Array.isArray(events) && events.length
            ? Number(events[events.length - 1].seqNo || events[events.length - 1].stateSeq || 0)
            : 0
          if (latest > stateSeq.value) {
            const again = await api.getSnapshot(sessionId, view)
            stateSeq.value = Number(again?.stateSeq) || latest
            if (onSnapshot) await onSnapshot(again, 'events')
            return again
          }
        } catch {
          /* 事件补查失败时仍保留已拉到的全量快照 */
        }
      }
      return snapshot
    } finally {
      syncing.value = false
    }
  }

  function handleOnline() {
    online.value = true
    pull('reconnect').catch(() => {})
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

  return { online, stateSeq, syncing, start, stop, pull }
}
