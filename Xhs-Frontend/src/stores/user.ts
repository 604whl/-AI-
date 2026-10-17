import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import type {
  LoginRequest,
  RegisterRequest,
  UpdateProfileRequest,
  UserProfile,
  UserUsage,
  WorkspaceSummary,
  WorkspaceDetail,
  WorkspaceMember,
  WorkspaceRole,
} from '@/types/auth'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/token'
import {
  getActiveWorkspaceId,
  setActiveWorkspaceId,
  clearActiveWorkspaceId,
} from '@/utils/workspace'
import http from '@/api/http'

export const useUserStore = defineStore('user', () => {
  const profile = ref<UserProfile | null>(null)
  const usage = ref<UserUsage | null>(null)
  const loading = ref(false)
  const usageLoading = ref(false)
  const saving = ref(false)

  // 多租户：当前用户的所有 workspace
  const workspaces = ref<WorkspaceSummary[]>([])
  // 当前激活的 workspaceId；与 localStorage 同步
  const activeWorkspaceId = ref<string | null>(getActiveWorkspaceId())

  const activeWorkspace = computed<WorkspaceSummary | null>(() => {
    const id = activeWorkspaceId.value
    if (!id) return null
    return workspaces.value.find((w) => w.id === id) ?? null
  })

  const isLoggedIn = () => !!getAccessToken()

  function applyAuth(data: {
    accessToken: string
    refreshToken: string
    user: UserProfile
    workspaces?: WorkspaceSummary[]
    defaultWorkspaceId?: string | null
  }) {
    setTokens(data.accessToken, data.refreshToken)
    profile.value = data.user
    usage.value = null
    workspaces.value = data.workspaces ?? []
    const initialId =
      data.defaultWorkspaceId ??
      (data.workspaces && data.workspaces.length > 0 ? data.workspaces[0].id : null)
    setActiveWorkspace(initialId)
  }

  function setActiveWorkspace(id: string | null | undefined) {
    const next = id == null || id === '' ? null : id
    activeWorkspaceId.value = next
    setActiveWorkspaceId(next ?? null)
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

  /**
   * 拉取当前用户作为 member 的所有 workspace。
   * 同时保证本地激活的 workspaceId 仍然有效，否则自动切到第一个。
   */
  async function fetchWorkspaces() {
    if (!getAccessToken()) return []
    const res = await http.get<{ code: number; data: WorkspaceSummary[] }>('/workspaces')
    const items = (res.data.data ?? []) as WorkspaceSummary[]
    workspaces.value = items
    const validIds = new Set(items.map((w) => w.id))
    if (!activeWorkspaceId.value || !validIds.has(activeWorkspaceId.value)) {
      setActiveWorkspace(items[0]?.id ?? null)
    } else {
      // 同步一次 storage（防止从另一处被改）
      setActiveWorkspaceId(activeWorkspaceId.value)
    }
    return items
  }

  /**
   * 创建新 workspace（自动成为 OWNER），并切到新 workspace。
   */
  async function createWorkspace(payload: { name: string; slug?: string; industry?: string; region?: string; defaultLocale?: string; defaultQuota?: number }) {
    const res = await http.post<{ code: number; data: WorkspaceDetail | null }>('/workspaces', payload)
    const created = res.data.data
    if (created) {
      const summary: WorkspaceSummary = {
        id: created.id,
        name: created.name,
        slug: created.slug,
        role: created.role as WorkspaceRole,
      }
      // 避免重复
      const idx = workspaces.value.findIndex((w) => w.id === summary.id)
      if (idx === -1) {
        workspaces.value = [...workspaces.value, summary]
      } else {
        workspaces.value[idx] = summary
      }
      setActiveWorkspace(summary.id)
    }
    return created
  }

  /**
   * 切换激活 workspace。
   * <p>
   * 注意：调用方需要负责刷新数据；切换后应当清空跨 workspace 的列表/详情缓存。
   */
  function switchWorkspace(id: string) {
    if (!workspaces.value.some((w) => w.id === id)) {
      throw new Error('workspace_not_member')
    }
    setActiveWorkspace(id)
  }

  async function listMembers(workspaceId: string): Promise<WorkspaceMember[]> {
    const res = await http.get<{ code: number; data: WorkspaceMember[] | null }>(`/workspaces/${workspaceId}/members`)
    return res.data.data ?? []
  }

  async function inviteMember(workspaceId: string, email: string, role: WorkspaceRole) {
    const res = await http.post<{ code: number; data: WorkspaceMember | null }>(`/workspaces/${workspaceId}/members`, { email, role })
    return res.data.data
  }

  async function updateMemberRole(workspaceId: string, userId: number, role: WorkspaceRole) {
    const res = await http.patch<{ code: number; data: WorkspaceMember | null }>(`/workspaces/${workspaceId}/members/${userId}`, { role })
    return res.data.data
  }

  async function removeMember(workspaceId: string, userId: number) {
    await http.delete(`/workspaces/${workspaceId}/members/${userId}`)
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
    workspaces.value = []
    clearActiveWorkspaceId()
    activeWorkspaceId.value = null
  }

  return {
    profile,
    usage,
    loading,
    usageLoading,
    saving,
    workspaces,
    activeWorkspaceId,
    activeWorkspace,
    isLoggedIn,
    login,
    register,
    fetchProfile,
    updateProfile,
    fetchUsage,
    fetchWorkspaces,
    createWorkspace,
    switchWorkspace,
    listMembers,
    inviteMember,
    updateMemberRole,
    removeMember,
    logout,
  }
})