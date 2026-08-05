import { defineStore } from 'pinia'
import { login as apiLogin, me as apiMe, type AppUser } from '@/api/auth'
import { TOKEN_KEY } from '@/api/http'
import { canAccessAdmin, ROLE_LABEL, type UserRole } from '@/config/roles'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as AppUser | null,
    token: localStorage.getItem(TOKEN_KEY) || '',
  }),
  getters: {
    isLoggedIn: (s) => !!s.token && !!s.user,
    role: (s) => s.user?.role || 'user',
    roleLabel(): string {
      return ROLE_LABEL[(this.role as UserRole) || 'user'] || this.role
    },
    canAccessAdmin(): boolean {
      return canAccessAdmin(this.role)
    },
  },
  actions: {
    setToken(token: string) {
      this.token = token
      if (token) localStorage.setItem(TOKEN_KEY, token)
      else localStorage.removeItem(TOKEN_KEY)
    },
    async init() {
      if (!this.token) return
      try {
        this.user = await apiMe()
      } catch {
        this.setToken('')
        this.user = null
      }
    },
    async login(username: string, password: string) {
      const res = await apiLogin(username, password)
      this.setToken(res.token)
      this.user = res.user
      if (!canAccessAdmin(this.user.role)) {
        this.logout()
        throw new Error('该账号无后台权限，请联系管理员开通录入员/审核员角色')
      }
    },
    logout() {
      this.setToken('')
      this.user = null
    },
  },
})
