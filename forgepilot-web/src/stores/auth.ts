import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { apiFetch, getAccessToken, setAccessToken } from '@/api/http'

export interface AuthUser {
  userId: string
  username: string
  displayName: string
  tenantId: string | null
  roles: string[]
  menus: string[]
}

interface LoginResponse {
  accessToken: string
  tokenType: string
  user: AuthUser
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const bootstrapped = ref(false)

  const isAuthenticated = computed(() => user.value != null && getAccessToken() != null)

  async function login(username: string, password: string) {
    const response = await apiFetch<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    setAccessToken(response.accessToken)
    user.value = response.user
  }

  async function restoreSession() {
    if (!getAccessToken()) {
      user.value = null
      bootstrapped.value = true
      return
    }
    try {
      user.value = await apiFetch<AuthUser>('/api/v1/me')
    } catch {
      setAccessToken(null)
      user.value = null
    } finally {
      bootstrapped.value = true
    }
  }

  function logout() {
    setAccessToken(null)
    user.value = null
  }

  return { user, bootstrapped, isAuthenticated, login, restoreSession, logout }
})
