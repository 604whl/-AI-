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

/** 敏感词检测结果 */
export type SensitiveRiskLevel = 'low' | 'medium' | 'high'

export interface SensitiveWordResult {
  words: string[]
  riskLevel: SensitiveRiskLevel
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
}

/** 工作台 AI 洞察完整结果 */
export interface WorkbenchInsightResult {
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
}
