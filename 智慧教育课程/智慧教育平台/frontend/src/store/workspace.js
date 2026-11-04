import { defineStore } from 'pinia'

/** 三端页签。只记当前会话里打开过的路由，不在本地另存一份业务数据。 */
export const useWorkspaceStore = defineStore('workspace', {
  state: () => ({ tabs: [] }),
  actions: {
    open(route) {
      if (!route?.meta?.title || route.meta.public) return
      const path = route.path
      const found = this.tabs.find(tab => tab.path === path)
      if (found) { found.title = route.meta.title; return }
      this.tabs.push({ path, title: route.meta.title })
      if (this.tabs.length > 15) this.tabs.shift()
    },
    close(path) {
      const index = this.tabs.findIndex(tab => tab.path === path)
      if (index < 0) return null
      this.tabs.splice(index, 1)
      return this.tabs[Math.min(index, this.tabs.length - 1)] || null
    }
  }
})
