import { login, logout, getInfo } from '@/api/login'
import { getToken, setToken, removeToken, getRole, setRole } from '@/utils/auth'
import defAva from '@/assets/images/profile.jpg'
import { resetEducation } from '@/education/context'
import useLockStore from './lock'
let infoSequence = 0
const useUserStore = defineStore('user', {
  state: () => ({ token: getToken(), id: '', profile: null, name: '', nickName: '', avatar: '', role: getRole(), roles: [], permissions: [] }),
  actions: {
    async login(info) {
      const role = info.role || 'admin'
      if (!['admin','teacher','student'].includes(role)) throw new Error('登录角色无效')
      this.clearIdentity()
      const result = await login(info.username.trim(), info.password, info.code, info.uuid, role)
      if (!result.token) throw new Error('登录成功但未返回令牌')
      setRole(role);setToken(result.token);this.token=result.token;this.role=role
      this.roles=[];this.permissions=[]
      return result
    },
    async getInfo() {
      const sequence=++infoSequence
      const result=await getInfo(), user=result.user
      if(sequence!==infoSequence)throw Object.assign(new Error('旧身份查询已取消'),{cancelled:true})
      const role=getRole()
      if (!user || !Array.isArray(result.roles) || !result.roles.includes(role)) {
        this.clearIdentity();throw new Error('当前账号没有该登录入口的权限')
      }
      this.role=role;this.roles=result.roles;this.permissions=result.permissions || []
      this.profile=user;this.id=user.userId;this.name=user.userName;this.nickName=user.nickName
      this.avatar=user.avatar ? (/^https?:/i.test(user.avatar) ? user.avatar : (import.meta.env.VITE_APP_BASE_API || '')+user.avatar) : defAva
      return result
    },
    clearIdentity() {
      infoSequence++
      useLockStore().unlockScreen()
      removeToken();this.token='';this.id='';this.profile=null;this.name='';this.nickName='';this.roles=[];this.permissions=[]
      resetEducation();window.dispatchEvent(new Event('edu:logout'))
    },
    async logOut() {
      const token=getToken()
      try { if (token) await logout() } finally { if(token===getToken())this.clearIdentity() }
    }
  }
})
export default useUserStore
