import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'
import type { LoginRequest, RegisterRequest, UserProfile } from '@/types/auth'
import { clearTokens, getAccessToken, setTokens } from '@/utils/token'

export const useUserStore = defineStore('user', () => {
  const profile = ref<UserProfile | null>(null)
  const loading = ref(false)

  const isLoggedIn = () => !!getAccessToken()

  function applyAuth(data: { accessToken: string; refreshToken: string; user: UserProfile }) {
    setTokens(data.accessToken, data.refreshToken)
    profile.value = data.user
  }

  async function login(payload: LoginRequest) {
    loading.value = true
    try {
      const res = await authApi.login(payload)
      applyAuth(res.data.data)
      return res.data.data
    } finally {
      loading.value = false
    }
  }

  async function register(payload: RegisterRequest) {
    loading.value = true
    try {
      const res = await authApi.register(payload)
      applyAuth(res.data.data)
      return res.data.data
    } finally {
      loading.value = false
    }
  }

  async function fetchProfile() {
    if (!getAccessToken()) return null
    loading.value = true
    try {
      const res = await authApi.fetchCurrentUser()
      profile.value = res.data.data
      return res.data.data
    } catch {
      logout()
      return null
    } finally {
      loading.value = false
    }
  }

  function logout() {
    clearTokens()
    profile.value = null
  }

  return {
    profile,
    loading,
    isLoggedIn,
    login,
    register,
    fetchProfile,
    logout,
  }
})
