import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'
import type { LoginRequest, RegisterRequest, UpdateProfileRequest, UserProfile, UserUsage } from '@/types/auth'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/token'

export const useUserStore = defineStore('user', () => {
  const profile = ref<UserProfile | null>(null)
  const usage = ref<UserUsage | null>(null)
  const loading = ref(false)
  const usageLoading = ref(false)
  const saving = ref(false)

  const isLoggedIn = () => !!getAccessToken()

  function applyAuth(data: { accessToken: string; refreshToken: string; user: UserProfile }) {
    setTokens(data.accessToken, data.refreshToken)
    profile.value = data.user
    usage.value = null
  }

  function syncProfileQuotaFromUsage(data: UserUsage) {
    if (profile.value) {
      profile.value.dailyQuota = data.dailyQuota
    }
  }

  async function login(payload: LoginRequest) {
    loading.value = true
    try {
      const res = await authApi.login(payload)
      applyAuth(res.data.data)
      await fetchUsage()
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
      await fetchUsage()
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
      await logout()
      return null
    } finally {
      loading.value = false
    }
  }

  async function updateProfile(payload: UpdateProfileRequest) {
    saving.value = true
    try {
      const res = await authApi.updateProfile(payload)
      profile.value = res.data.data
      return res.data.data
    } finally {
      saving.value = false
    }
  }

  async function fetchUsage() {
    if (!getAccessToken()) return null
    usageLoading.value = true
    try {
      const res = await authApi.fetchUsage()
      usage.value = res.data.data
      syncProfileQuotaFromUsage(res.data.data)
      return res.data.data
    } catch {
      usage.value = null
      return null
    } finally {
      usageLoading.value = false
    }
  }

  async function logout() {
    const refresh = getRefreshToken()
    try {
      if (refresh) {
        await authApi.logout(refresh)
      }
    } catch {
      // ignore revoke failures
    }
    clearTokens()
    profile.value = null
    usage.value = null
  }

  return {
    profile,
    usage,
    loading,
    usageLoading,
    saving,
    isLoggedIn,
    login,
    register,
    fetchProfile,
    updateProfile,
    fetchUsage,
    logout,
  }
})
