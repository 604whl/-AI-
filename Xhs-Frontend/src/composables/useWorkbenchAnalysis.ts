import { ref, shallowRef } from 'vue'
import { fetchAnalysis, generateTitlesByAnalysisId } from '@/api/analysis'
import { detectSensitiveWords } from '@/api/workbench'
import type { AnalysisReport, CoverAnalysis, PersonaType } from '@/types/api'
import type {
  AiOptimizationAdvice,
  AnalysisLoadingStep,
  HotPoint,
  RecommendedTitle,
  SensitiveWordResult,
  StructureBreakdown,
  ViralScoreCard,
  WorkbenchAnalyzeParams,
  WorkbenchInsightResult,
} from '@/types/workbench'
import {
  generatedTitlesToRecommended,
  reportToWorkbenchInsight,
} from '@/utils/reportToWorkbenchInsight'

export function useWorkbenchAnalysis() {
  const loading = ref(false)
  const loadingStep = ref<AnalysisLoadingStep>('idle')
  const hasResult = ref(false)
  const analysisId = ref<string | null>(null)

  const contentType = ref<string | null>(null)
  const secondaryTags = shallowRef<string[]>([])
  const structure = shallowRef<StructureBreakdown | null>(null)
  const scores = shallowRef<ViralScoreCard | null>(null)
  const recommendedTitles = shallowRef<RecommendedTitle[]>([])
  const hotPoints = shallowRef<HotPoint[]>([])
  const hotTopics = shallowRef<string[]>([])
  const sensitiveWords = shallowRef<SensitiveWordResult>({
    words: [],
    riskLevel: 'low',
    warnings: [],
  })
  /** 分析完成时正文快照，避免输入 watch 用实时扫描覆盖报告中的 complianceWarnings */
  const complianceContentSnapshot = ref<{ title: string; body: string } | null>(null)
  let complianceScanSeq = 0
  const optimization = shallowRef<AiOptimizationAdvice | null>(null)
  /** 分析详情中的完整 report JSON（侧栏摘要之外的 issues / optimizations / complianceWarnings） */
  const fullReport = shallowRef<AnalysisReport | null>(null)
  const coverAnalysis = shallowRef<CoverAnalysis | null>(null)
  const analysisScenario = ref<string | null>(null)
  const coverImageUrl = ref<string | undefined>(undefined)

  const titlesRegenerating = ref(false)

  function applyInsight(result: WorkbenchInsightResult) {
    contentType.value = result.contentType ?? null
    secondaryTags.value = result.secondaryTags ?? []
    structure.value = result.structure ?? null
    scores.value = result.scores
    recommendedTitles.value = result.recommendedTitles
    hotPoints.value = result.hotPoints
    hotTopics.value = result.hotTopics
    sensitiveWords.value = result.sensitiveWords
    optimization.value = result.optimization
    hasResult.value = true
  }

  function hydrateFromReport(
    report: AnalysisReport,
    content?: { title?: string; body?: string },
  ) {
    fullReport.value = report
    applyInsight(reportToWorkbenchInsight(report))
    if (content) {
      complianceContentSnapshot.value = {
        title: content.title ?? '',
        body: content.body ?? '',
      }
    }
  }

  async function animateLoadingSteps() {
    loadingStep.value = 'structure'
    await delay(600)
    loadingStep.value = 'emotion'
    await delay(600)
    loadingStep.value = 'ctr'
  }

  async function finishLoading() {
    loadingStep.value = 'done'
    await delay(300)
    loadingStep.value = 'idle'
    loading.value = false
  }

  async function runWithLoading(task: () => Promise<void>) {
    loading.value = true
    const stepsPromise = animateLoadingSteps()
    await task()
    await stepsPromise
    await finishLoading()
  }

  /** 轮询完成后回填侧栏 */
  async function hydrateFromAnalysisId(id: string) {
    analysisId.value = id
    await runWithLoading(async () => {
      const res = await fetchAnalysis(id)
      const detail = res.data.data
      analysisScenario.value = detail.scenario
      coverImageUrl.value = detail.coverImageUrl
      coverAnalysis.value = detail.coverAnalysis ?? null
      if (detail.report) {
        hydrateFromReport(detail.report, {
          title: detail.title,
          body: detail.body,
        })
      }
    })
  }

  /** 刷新洞察：重新拉取已有分析 report */
  async function refresh(params: WorkbenchAnalyzeParams) {
    if (params.analysisId || analysisId.value) {
      await hydrateFromAnalysisId(params.analysisId ?? analysisId.value!)
      return
    }
    if (!params.title.trim() && !params.body.trim()) {
      throw new Error('empty content')
    }
    scanSensitiveWords(params.title, params.body)
  }

  /** 重新生成推荐标题（需已有 analysisId） */
  async function regenerateTitles(params: WorkbenchAnalyzeParams & { persona?: PersonaType }) {
    const id = params.analysisId ?? analysisId.value
    if (!id) return

    titlesRegenerating.value = true
    try {
      const res = await generateTitlesByAnalysisId(id, {
        goal: 'high_ctr',
        count: 5,
        persona: params.persona,
      })
      recommendedTitles.value = generatedTitlesToRecommended(res.data.data.titles)
    } finally {
      titlesRegenerating.value = false
    }
  }

  function isUsingReportCompliance(title: string, body: string): boolean {
    const snap = complianceContentSnapshot.value
    return (
      fullReport.value !== null &&
      snap !== null &&
      snap.title === title &&
      snap.body === body
    )
  }

  async function scanSensitiveWords(title: string, body: string) {
    const snap = complianceContentSnapshot.value
    if (snap && snap.title === title && snap.body === body) {
      return
    }

    const seq = ++complianceScanSeq
    const result = await detectSensitiveWords(title, body)
    if (seq === complianceScanSeq) {
      sensitiveWords.value = result
    }
  }

  function setAnalysisId(id: string | null) {
    analysisId.value = id
  }

  function reset() {
    loading.value = false
    loadingStep.value = 'idle'
    hasResult.value = false
    analysisId.value = null
    contentType.value = null
    secondaryTags.value = []
    structure.value = null
    scores.value = null
    recommendedTitles.value = []
    hotPoints.value = []
    hotTopics.value = []
    sensitiveWords.value = { words: [], riskLevel: 'low', warnings: [] }
    complianceContentSnapshot.value = null
    complianceScanSeq++
    optimization.value = null
    fullReport.value = null
    coverAnalysis.value = null
    analysisScenario.value = null
    coverImageUrl.value = undefined
  }

  return {
    loading,
    loadingStep,
    hasResult,
    analysisId,
    contentType,
    secondaryTags,
    structure,
    scores,
    recommendedTitles,
    hotPoints,
    hotTopics,
    sensitiveWords,
    optimization,
    fullReport,
    coverAnalysis,
    analysisScenario,
    coverImageUrl,
    titlesRegenerating,
    isUsingReportCompliance,
    hydrateFromReport,
    hydrateFromAnalysisId,
    refresh,
    regenerateTitles,
    scanSensitiveWords,
    setAnalysisId,
    reset,
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
