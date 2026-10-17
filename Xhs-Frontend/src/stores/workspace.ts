import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import http from '@/api/http'
import type { WorkspaceDetail, WorkspaceSummary } from '@/types/auth'

export const useWorkspaceStore = defineStore('workspace', () => {
  // 列表：当前用户的所有 workspace summary
  const workspaceList = ref<WorkspaceSummary[]>([])

  // 当前选中的 workspace 详情
  const currentWorkspace = ref<WorkspaceDetail | null>(null)

  /**
   * 当前 workspace 的 ID
   * 优先取 currentWorkspace，其次取 workspaceList[0]
   */
  const currentWorkspaceId = computed<string | null>(() => {
    return currentWorkspace.value?.id ?? workspaceList.value[0]?.id ?? null
  })

  /**
   * 当前用户在当前 workspace 的角色
   */
  const currentRole = computed<string | null>(() => {
    return currentWorkspace.value?.role ?? workspaceList.value.find(
      (w) => w.id === currentWorkspace.value?.id
    )?.role ?? null
  })

  function setCurrentWorkspace(ws: WorkspaceDetail | null) {
    currentWorkspace.value = ws
  }

  async function fetchWorkspaces(): Promise<WorkspaceSummary[]> {
    const res = await http.get<{ code: number; data: WorkspaceSummary[] }>('/workspaces')
    const items = (res.data.data ?? []) as WorkspaceSummary[]
    workspaceList.value = items
    return items
  }

  async function fetchWorkspaceDetail(id: string): Promise<WorkspaceDetail | null> {
    const res = await http.get<{ code: number; data: WorkspaceDetail | null }>(`/workspaces/${id}`)
    const detail = res.data.data
    if (detail) {
      currentWorkspace.value = detail
    }
    return detail
  }

  return {
    workspaceList,
    currentWorkspace,
    currentWorkspaceId,
    currentRole,
    setCurrentWorkspace,
    fetchWorkspaces,
    fetchWorkspaceDetail,
  }
})
