import type { AnalysisReport, GeneratedTitle } from '@/types/api'
import type {
  AiOptimizationAdvice,
  HotPoint,
  RecommendedTitle,
  StructureBreakdown,
  TitleOptimizationPair,
  ViralScoreCard,
  WorkbenchInsightResult,
} from '@/types/workbench'
import { complianceWarningsToResult } from '@/utils/complianceResult'

function structureToHotPoints(structure: AnalysisReport['structure']): HotPoint[] {
  const points: HotPoint[] = []

  if (structure.hook) {
    points.push({
      point: structure.hook.slice(0, 24) + (structure.hook.length > 24 ? '…' : ''),
      type: 'Hook',
      psychology: structure.hook,
      emotion: structure.emotionArc[0] ?? '—',
    })
  }

  structure.savePoints.forEach((savePoint, index) => {
    points.push({
      point: savePoint,
      type: '收藏点',
      psychology: '提供可收藏的结构化价值',
      emotion: structure.emotionArc[index + 1] ?? structure.emotionArc.at(-1) ?? '—',
    })
  })

  return points.slice(0, 4)
}

function buildOptimization(report: AnalysisReport): AiOptimizationAdvice {
  const titlePairs: TitleOptimizationPair[] = report.optimizations.title.map((item) => ({
    original: item.original,
    optimized: item.optimized,
  }))

  const suggestions = [
    ...report.issues.map((issue) => issue.suggestion).filter(Boolean),
    ...report.optimizations.structure,
    ...report.optimizations.emotion,
    ...report.optimizations.cta,
  ]

  return {
    issues: report.issues.map((issue) => issue.description),
    suggestions,
    titleOptimizations: titlePairs,
  }
}

function titlesFromReport(report: AnalysisReport): RecommendedTitle[] {
  const baseCtr = report.scores.ctr.score
  const fromOptimizations = report.optimizations.title.map((item, index) => ({
    title: item.optimized,
    ctr: Math.min(98, baseCtr + (index === 0 ? 4 : index)),
  }))

  if (fromOptimizations.length) return fromOptimizations

  return []
}

export function ctrFromEstimated(level?: GeneratedTitle['estimatedCtr']): number {
  if (level === 'high') return 90
  if (level === 'medium') return 80
  return 70
}

export function generatedTitlesToRecommended(titles: GeneratedTitle[]): RecommendedTitle[] {
  return titles.map((item, index) => ({
    title: item.text,
    ctr: ctrFromEstimated(item.estimatedCtr) + (index % 3),
  }))
}

export function reportToStructureBreakdown(report: AnalysisReport): StructureBreakdown {
  return {
    hook: report.structure.hook,
    emotionArc: report.structure.emotionArc,
    savePoints: report.structure.savePoints,
    conversionPath: report.structure.conversionPath,
    cta: report.structure.cta,
  }
}

export function reportToWorkbenchInsight(report: AnalysisReport): WorkbenchInsightResult {
  const scores: ViralScoreCard = {
    viralScore: report.scores.viral.score,
    ctrScore: report.scores.ctr.score,
    emotionScore: report.scores.emotion.score,
    collectScore: report.scores.collect.score,
    conversionScore: report.scores.conversion.score,
  }

  return {
    contentType: report.contentType,
    secondaryTags: report.secondaryTags ?? [],
    structure: reportToStructureBreakdown(report),
    scores,
    recommendedTitles: titlesFromReport(report),
    hotPoints: structureToHotPoints(report.structure),
    hotTopics: report.structure.savePoints.slice(0, 4),
    sensitiveWords: complianceWarningsToResult(report.complianceWarnings ?? []),
    optimization: buildOptimization(report),
  }
}
