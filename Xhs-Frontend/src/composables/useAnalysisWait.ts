import { onUnmounted, ref } from 'vue'
import { subscribeAnalysisStream } from '@/api/analysisStream'

/**
 * Wait for an analysis task via SSE (falls back to polling inside subscribeAnalysisStream).
 */
export function useAnalysisWait<T extends { status: string }>(fetchLatest: () => Promise<T>) {
  const loading = ref(false)
  const error = ref<string | null>(null)
  const progressMessage = ref<string | null>(null)
  let unsubscribe: (() => void) | null = null

  const stop = () => {
    unsubscribe?.()
    unsubscribe = null
  }

  const start = (analysisId: string): Promise<boolean> => {
    loading.value = true
    error.value = null
    progressMessage.value = null
    stop()

    return new Promise((resolve) => {
      unsubscribe = subscribeAnalysisStream(analysisId, {
        onProgress: (event) => {
          progressMessage.value = event.message
        },
        onDone: async (event) => {
          try {
            if (event.status === 'failed') {
              error.value = event.failureReason || 'analysis_failed'
              resolve(false)
              return
            }
            const data = await fetchLatest()
            resolve(data.status === 'completed')
          } catch (e) {
            error.value = e instanceof Error ? e.message : 'unknown_error'
            resolve(false)
          } finally {
            loading.value = false
            stop()
          }
        },
        onError: (msg) => {
          error.value = msg
          loading.value = false
          stop()
          resolve(false)
        },
      })
    })
  }

  onUnmounted(stop)

  return { loading, error, progressMessage, start, stop }
}
