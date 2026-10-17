/**
 * 当前激活的工作区 ID 工具。
 * <p>
 * 登录成功时由 {@link setActiveWorkspace} 写入 localStorage，HTTP 拦截器从这里读取
 * 并塞到 {@code X-Workspace-Id} 头里；切换工作区后调用 {@link setActiveWorkspace} 同步。
 */

const STORAGE_KEY = 'xhs.activeWorkspaceId'

export function getActiveWorkspaceId(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

export function setActiveWorkspaceId(id: string | null | undefined): void {
  try {
    if (id == null || id === '') {
      localStorage.removeItem(STORAGE_KEY)
    } else {
      localStorage.setItem(STORAGE_KEY, id)
    }
  } catch {
    // 静默失败（隐私模式或 SSR 无 window）
  }
}

export function clearActiveWorkspaceId(): void {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // noop
  }
}