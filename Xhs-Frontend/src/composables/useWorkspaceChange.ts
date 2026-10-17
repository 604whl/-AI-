import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 监听全局 "workspace 切换" 事件，自动触发回调。
 * <p>
 * 当用户在顶部切换 workspace 时，所有需要按 workspace 拉数据的页面都应该重新加载；
 * 把这一段抽到 composable 里，避免每个 view 重复监听 / 解绑。
 */
export function useWorkspaceChange(handler: (newWorkspaceId: string | null) => void) {
  function onChange(event: Event) {
    const detail = (event as CustomEvent<{ id: string | null }>).detail
    handler(detail?.id ?? null)
  }

  onMounted(() => {
    window.addEventListener('xhs:workspace-changed', onChange as EventListener)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('xhs:workspace-changed', onChange as EventListener)
  })
}