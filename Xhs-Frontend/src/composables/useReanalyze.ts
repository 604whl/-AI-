import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createAnalysis, fetchAnalysis } from '@/api/analysis'
import { ERROR_QUOTA_EXCEEDED } from '@/constants/quota'
import { useUserStore } from '@/stores/user'
import { buildAnalysisCreatePayloadFromDetail } from '@/utils/analysisPayload'

export function useReanalyze() {
  const { t } = useI18n()
  const router = useRouter()
  const userStore = useUserStore()
  const running = ref(false)

  function isQuotaExceededError(err: unknown): boolean {
    return (err as Error & { code?: number }).code === ERROR_QUOTA_EXCEEDED
  }

  async function runReanalyze(analysisId: string): Promise<string | null> {
    if (running.value) return null
    running.value = true
    try {
      const detailRes = await fetchAnalysis(analysisId)
      const detail = detailRes.data.data
      const hasContent = Boolean(detail.title?.trim() || detail.body?.trim())
      if (!hasContent) {
        ElMessage.warning(t('dashboard.validationRequired'))
        return null
      }

      const createRes = await createAnalysis(buildAnalysisCreatePayloadFromDetail(detail))
      const newId = createRes.data.data.id
      await userStore.fetchUsage()
      ElMessage.success(t('history.reanalyzeStarted'))
      await router.push(`/analysis/${newId}`)
      return newId
    } catch (err) {
      if (isQuotaExceededError(err)) {
        await userStore.fetchUsage()
        ElMessage.warning(t('dashboard.quotaExceededMessage'))
        return null
      }
      const code = (err as Error & { code?: number }).code
      if (code === 40902) {
        ElMessage.warning(t('history.reanalyzeBusy'))
        return null
      }
      ElMessage.error(t('history.reanalyzeFailed'))
      return null
    } finally {
      running.value = false
    }
  }

  return { running, runReanalyze }
}
