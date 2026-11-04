import { defineStore } from 'pinia'
import { listMyBindings } from '@/api/education/classroom'

/** 当前课程/教学班。名单只来自 /education/course/bindings/mine。 */
export const useCourseStore = defineStore('course', {
  state: () => ({ bindings: [], bindingId: '', loaded: false, error: '', pending: null }),
  getters: {
    current: state => state.bindings.find(item => String(item.bindingId) === String(state.bindingId)) || null,
    forRole: state => role => state.bindings.filter(item => !role || item.role === role)
  },
  actions: {
    reset() { this.pending = null; this.$reset() },
    pick(apiRole) {
      const list = this.forRole(apiRole)
      if (!list.some(item => String(item.bindingId) === String(this.bindingId))) {
        this.bindingId = list[0] ? list[0].bindingId : ''
      }
    },
    async ensure(apiRole) {
      if (!this.loaded) {
        if (!this.pending) {
          this.error = ''
          this.pending = listMyBindings()
            .then(list => { this.bindings = Array.isArray(list) ? list : []; this.loaded = true })
            .catch(error => { this.error = error.message || '无法加载课程'; throw error })
            .finally(() => { this.pending = null })
        }
        await this.pending
      }
      this.pick(apiRole)
    },
    select(bindingId) {
      if (this.bindings.some(item => String(item.bindingId) === String(bindingId))) this.bindingId = bindingId
    }
  }
})
