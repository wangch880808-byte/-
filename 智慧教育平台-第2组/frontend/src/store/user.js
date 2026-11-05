import { defineStore } from 'pinia'
import * as api from '@/api/auth'
import { clearSession, getToken, getRole, setToken, setRole } from '@/utils/auth'
import { pickRole } from '@/utils/access'
import { eduError } from '@/utils/request'
export const useUserStore = defineStore('user', {
  state: () => ({ token: getToken(), user: null, roles: [], permissions: [], menus: [], role: '', loaded: false }),
  actions: {
    reset() { clearSession(); this.$reset() },
    async loadIdentity(preferred = getRole()) {
      const info = await api.getInfo()
      const roles = Array.isArray(info.roles) ? info.roles : []
      const role = pickRole(roles, preferred)
      if (!role || (preferred && !roles.includes(preferred))) throw eduError('此账号没有所选入口的角色，请选择正确入口或联系管理员', 'forbidden', 403)
      const routers = await api.getRouters()
      this.user = info.user || {}
      this.roles = roles
      this.permissions = Array.isArray(info.permissions) ? info.permissions : []
      this.menus = routers.data || []
      this.role = role
      this.loaded = true
      setRole(role)
    },
    async signIn(form) {
      this.reset()
      const result = await api.login(form)
      if (!result.token) throw eduError('服务器未返回登录令牌')
      this.token = result.token
      setToken(result.token)
      try { await this.loadIdentity(form.role) } catch (error) {
        try { await api.logout() } catch { /* 本地令牌仍需清理 */ }
        this.reset()
        throw error
      }
    },
    async signOut() { try { await api.logout() } finally { this.reset() } }
  }
})
