import { onUnmounted, ref } from 'vue'

/**
 * 分析任务轮询：2s 间隔，最多 30 次（对齐 PRD P95 45s）
 */
export function useAnalysisPoll<T extends { status: string }>(fetcher: () => Promise<T>) {
  const loading = ref(false)
  const error = ref<string | null>(null)
  const lastResult = ref<T | null>(null)
  let timer: ReturnType<typeof setInterval> | null = null
  let count = 0

  const stop = () => {
    if (timer) clearInterval(timer)
    timer = null
  }

  const start = async (): Promise<boolean> => {
    loading.value = true
    error.value = null
    count = 0
    return new Promise((resolve) => {
      const tick = async () => {
        try {
          const data = await fetcher()
          if (data.status === 'completed') {
            lastResult.value = data
            stop()
            loading.value = false
            resolve(true)
            return
          }
          if (data.status === 'failed') {
            stop()
            loading.value = false
            error.value = '分析失败'
            resolve(false)
            return
          }
          count++
          if (count >= 30) {
            stop()
            loading.value = false
            error.value = '分析超时'
            resolve(false)
          }
        } catch (e) {
          stop()
          loading.value = false
          error.value = e instanceof Error ? e.message : '未知错误'
          resolve(false)
        }
      }
      tick()
      timer = setInterval(tick, 2000)
    })
  }

  onUnmounted(stop)

  return { loading, error, lastResult, start, stop }
}
