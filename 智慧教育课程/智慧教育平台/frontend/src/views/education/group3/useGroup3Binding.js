import { computed, ref, watch } from 'vue'
import { listContexts } from '@/api/education/group3'
import { getRole } from '@/utils/auth'

const storageKey = role => `zhijiao.group3.binding.${role}`

/**
 * group3 业务共用的“课程 + 教学班”上下文。
 * 单独打开页面时由选择器切换并写入 sessionStorage；嵌入课程详情时传入固定 bindingId。
 */
export function useGroup3Binding(fixedBindingId) {
  const role = getRole()
  const fixed = () => {
    const value = typeof fixedBindingId === 'function' ? fixedBindingId() : fixedBindingId?.value
    return value ? String(value) : ''
  }
  const list = ref([])
  const bindingId = ref(fixed() || sessionStorage.getItem(storageKey(role)) || '')
  const loading = ref(false)
  const error = ref('')
  const current = computed(() => list.value.find(item => String(item.bindingId) === String(bindingId.value)) || null)

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const res = await listContexts()
      list.value = Array.isArray(res.data) ? res.data : []
      const pinned = fixed()
      if (pinned) {
        bindingId.value = pinned
        return
      }
      const matched = list.value.some(item => String(item.bindingId) === String(bindingId.value))
      if (!matched) bindingId.value = list.value.length ? String(list.value[0].bindingId) : ''
      if (bindingId.value) sessionStorage.setItem(storageKey(role), bindingId.value)
    } catch (e) {
      error.value = e.message || '课程上下文加载失败'
    } finally {
      loading.value = false
    }
  }

  function select(value) {
    if (fixed()) return
    bindingId.value = String(value || '')
    if (bindingId.value) sessionStorage.setItem(storageKey(role), bindingId.value)
  }

  watch(fixed, value => {
    if (value) bindingId.value = value
  })

  return { role, list, bindingId, current, loading, error, load, select }
}
