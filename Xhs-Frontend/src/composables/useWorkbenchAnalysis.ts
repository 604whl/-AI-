import { ref, shallowRef } from 'vue'
import { detectSensitiveWords, fetchWorkbenchInsight, regenerateRecommendedTitles } from '@/api/workbench'
import type {
  AiOptimizationAdvice,
  AnalysisLoadingStep,
  HotPoint,
  RecommendedTitle,
  SensitiveWordResult,
  ViralScoreCard,
  WorkbenchAnalyzeParams,
} from '@/types/workbench'

export function useWorkbenchAnalysis() {
  const loading = ref(false)
  const loadingStep = ref<AnalysisLoadingStep>('idle')
  const hasResult = ref(false)

  const scores = shallowRef<ViralScoreCard | null>(null)
  const recommendedTitles = shallowRef<RecommendedTitle[]>([])
  const hotPoints = shallowRef<HotPoint[]>([])
  const hotTopics = shallowRef<string[]>([])
  const sensitiveWords = shallowRef<SensitiveWordResult>({ words: [], riskLevel: 'low' })
  const optimization = shallowRef<AiOptimizationAdvice | null>(null)

  const titlesRegenerating = ref(false)

  async function runLoadingSteps(task: () => Promise<void>) {
    loading.value = true
    loadingStep.value = 'structure'
    await delay(600)
    loadingStep.value = 'emotion'
    await delay(600)
    loadingStep.value = 'ctr'
    await task()
    loadingStep.value = 'done'
    await delay(300)
    loadingStep.value = 'idle'
    loading.value = false
    hasResult.value = true
  }

  async function analyze(params: WorkbenchAnalyzeParams) {
    await runLoadingSteps(async () => {
      const result = await fetchWorkbenchInsight(params)
      scores.value = result.scores
      recommendedTitles.value = result.recommendedTitles
      hotPoints.value = result.hotPoints
      hotTopics.value = result.hotTopics
      sensitiveWords.value = result.sensitiveWords
      optimization.value = result.optimization
    })
  }

  async function refresh(params: WorkbenchAnalyzeParams) {
    await analyze(params)
  }

  async function regenerateTitles(params: WorkbenchAnalyzeParams) {
    titlesRegenerating.value = true
    try {
      recommendedTitles.value = await regenerateRecommendedTitles(params)
    } finally {
      titlesRegenerating.value = false
    }
  }

  function scanSensitiveWords(title: string, body: string) {
    sensitiveWords.value = detectSensitiveWords(title, body)
  }

  function reset() {
    loading.value = false
    loadingStep.value = 'idle'
    hasResult.value = false
    scores.value = null
    recommendedTitles.value = []
    hotPoints.value = []
    hotTopics.value = []
    sensitiveWords.value = { words: [], riskLevel: 'low' }
    optimization.value = null
  }

  return {
    loading,
    loadingStep,
    hasResult,
    scores,
    recommendedTitles,
    hotPoints,
    hotTopics,
    sensitiveWords,
    optimization,
    titlesRegenerating,
    analyze,
    refresh,
    regenerateTitles,
    scanSensitiveWords,
    reset,
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
