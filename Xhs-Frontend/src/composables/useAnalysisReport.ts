import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import { fetchAnalysis } from '@/api/analysis'
import { subscribeAnalysisStream } from '@/api/analysisStream'
import type { AnalysisDetail, AnalysisStatus } from '@/types/api'
import { reportToWorkbenchInsight } from '@/utils/reportToWorkbenchInsight'

export function useAnalysisReport(analysisId: Ref<string>) {
  const detail = ref<AnalysisDetail | null>(null)
  const loading = ref(true)
  const polling = ref(false)
  const progressMessage = ref<string | null>(null)
  const error = ref<string | null>(null)
  let unsubscribeStream: (() => void) | null = null

  const status = computed<AnalysisStatus | null>(() => detail.value?.status ?? null)
  const isPending = computed(() => status.value === 'pending' || status.value === 'processing')
  const isCompleted = computed(() => status.value === 'completed')
  const isFailed = computed(() => status.value === 'failed')

  const insight = computed(() => {
    if (!detail.value?.report) return null
    return reportToWorkbenchInsight(detail.value.report)
  })

  function stopStream() {
    unsubscribeStream?.()
    unsubscribeStream = null
  }

  async function refreshDetail() {
    const res = await fetchAnalysis(analysisId.value)
    detail.value = res.data.data
    return detail.value
  }

  function startStream() {
    stopStream()
    polling.value = true
    progressMessage.value = null

    unsubscribeStream = subscribeAnalysisStream(analysisId.value, {
      onProgress: (event) => {
        progressMessage.value = event.message
        if (detail.value) {
          detail.value = { ...detail.value, status: event.status as AnalysisStatus }
        }
      },
      onDone: async () => {
        try {
          await refreshDetail()
        } catch (e) {
          error.value = e instanceof Error ? e.message : 'load_failed'
        } finally {
          polling.value = false
          stopStream()
        }
      },
      onError: (msg) => {
        polling.value = false
        error.value = msg
        stopStream()
      },
    })
  }

  async function load() {
    if (!analysisId.value) return
    loading.value = true
    error.value = null
    stopStream()
    try {
      await refreshDetail()
      if (!detail.value) return

      if (detail.value.status === 'pending' || detail.value.status === 'processing') {
        startStream()
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'load_failed'
      detail.value = null
    } finally {
      loading.value = false
    }
  }

  watch(analysisId, () => load(), { immediate: true })
  onUnmounted(stopStream)

  return {
    detail,
    loading,
    polling,
    progressMessage,
    error,
    status,
    isPending,
    isCompleted,
    isFailed,
    insight,
    reload: load,
  }
}
