/** 爆文五维评分 */
export interface ViralScoreCard {
  viralScore: number
  ctrScore: number
  emotionScore: number
  collectScore: number
  conversionScore: number
}

/** AI 推荐标题 */
export interface RecommendedTitle {
  title: string
  ctr: number
}

/** 爆点拆解项 */
export interface HotPoint {
  point: string
  type: string
  psychology: string
  emotion: string
}

/** 内容结构拆解（Hook / 情绪 / CTA） */
export interface StructureBreakdown {
  hook: string
  emotionArc: string[]
  savePoints: string[]
  conversionPath: string
  cta: { text: string; rating: number; comment: string }
}

/** 标题原句 → 优化句 */
export interface TitleOptimizationPair {
  original: string
  optimized: string
}

import type { ComplianceWarning } from '@/types/api'

/** 合规 / 敏感词风险等级 */
export type SensitiveRiskLevel = 'low' | 'medium' | 'high'

/** 工作台合规检测结果（与报告 complianceWarnings 同结构） */
export interface SensitiveWordResult {
  words: string[]
  riskLevel: SensitiveRiskLevel
  warnings: ComplianceWarning[]
}

/** AI 分析 loading 步骤 */
export type AnalysisLoadingStep =
  | 'idle'
  | 'structure'
  | 'emotion'
  | 'ctr'
  | 'done'

/** AI 优化建议 */
export interface AiOptimizationAdvice {
  issues: string[]
  suggestions: string[]
  titleOptimizations?: TitleOptimizationPair[]
}

/** 工作台 AI 洞察完整结果 */
export interface WorkbenchInsightResult {
  contentType?: string
  secondaryTags?: string[]
  structure?: StructureBreakdown
  scores: ViralScoreCard
  recommendedTitles: RecommendedTitle[]
  hotPoints: HotPoint[]
  hotTopics: string[]
  sensitiveWords: SensitiveWordResult
  optimization: AiOptimizationAdvice
}

/** 分析请求参数 */
export interface WorkbenchAnalyzeParams {
  title: string
  body: string
  persona?: string
  analysisId?: string
}
