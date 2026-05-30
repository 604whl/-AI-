import { computed, ref, watch, type Ref } from 'vue'
import { fetchAnalysis } from '@/api/analysis'
import { useAnalysisPoll } from '@/composables/useAnalysisPoll'
import type { AnalysisDetail, AnalysisStatus } from '@/types/api'
import { reportToWorkbenchInsight } from '@/utils/reportToWorkbenchInsight'

export function useAnalysisReport(analysisId: Ref<string>) {
  const detail = ref<AnalysisDetail | null>(null)
  const loading = ref(true)
  const polling = ref(false)
  const error = ref<string | null>(null)

  const status = computed<AnalysisStatus | null>(() => detail.value?.status ?? null)
  const isPending = computed(() => status.value === 'pending' || status.value === 'processing')
  const isCompleted = computed(() => status.value === 'completed')
  const isFailed = computed(() => status.value === 'failed')

  const insight = computed(() => {
    if (!detail.value?.report) return null
    return reportToWorkbenchInsight(detail.value.report)
  })

  const poll = useAnalysisPoll(async () => {
    const res = await fetchAnalysis(analysisId.value)
    detail.value = res.data.data
    return res.data.data
  })

  async function load() {
    if (!analysisId.value) return
    loading.value = true
    error.value = null
    try {
      const res = await fetchAnalysis(analysisId.value)
      detail.value = res.data.data

      if (detail.value.status === 'pending' || detail.value.status === 'processing') {
        polling.value = true
        const ok = await poll.start()
        polling.value = false
        if (!ok && !detail.value.report) {
          error.value = poll.error.value || 'analysis_failed'
        }
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'load_failed'
      detail.value = null
    } finally {
      loading.value = false
    }
  }

  watch(analysisId, () => load(), { immediate: true })

  return {
    detail,
    loading,
    polling,
    error,
    status,
    isPending,
    isCompleted,
    isFailed,
    insight,
    reload: load,
  }
}
