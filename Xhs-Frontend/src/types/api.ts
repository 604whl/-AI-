export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
  timestamp: number
}

export type AnalysisScenario = 'draft' | 'published' | 'competitor'
export type PersonaType = 'agency' | 'mentor' | 'senior'
export type AnalysisStatus = 'pending' | 'processing' | 'completed' | 'failed'

export interface ScoreDimension {
  score: number
  reason: string
  level?: 'low' | 'medium' | 'high'
}

export type TitleGenerateGoal =
  | 'high_ctr'
  | 'high_collect'
  | 'high_conversion'
  | 'anxiety'
  | 'offer'
  | 'info_gap'

export interface AnalysisCreateRequest {
  scenario: AnalysisScenario
  persona?: PersonaType
  title?: string
  body?: string
  coverImageUrl?: string
}

export interface AnalysisDetail {
  id: string
  status: AnalysisStatus
  scenario: AnalysisScenario
  persona?: PersonaType
  title?: string
  body?: string
  createdAt: string
  updatedAt: string
  report?: AnalysisReport
  failure?: {
    reason: string
    code: number
    message?: string
  }
}

export interface AnalysisListItem {
  id: string
  status: AnalysisStatus
  scenario: AnalysisScenario
  persona?: PersonaType
  title?: string
  createdAt: string
  updatedAt: string
  report?: Pick<AnalysisReport, 'scores'>
}

export interface PaginatedData<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface TitleGenerateRequest {
  goal: TitleGenerateGoal
  count?: number
  title?: string
  body?: string
  persona?: PersonaType
  analysisId?: string
}

export interface GeneratedTitle {
  text: string
  highlights: string[]
  estimatedCtr?: 'low' | 'medium' | 'high'
}

export interface TitleGenerateResponse {
  goal: TitleGenerateGoal
  titles: GeneratedTitle[]
  promptVersion: string
  analysisId?: string | null
}

export interface AnalysisReport {
  contentType: string
  secondaryTags?: string[]
  structure: {
    hook: string
    emotionArc: string[]
    savePoints: string[]
    conversionPath: string
    cta: { text: string; rating: number; comment: string }
  }
  scores: {
    ctr: ScoreDimension
    emotion: ScoreDimension
    collect: ScoreDimension
    conversion: ScoreDimension
    viral: ScoreDimension
  }
  issues: Array<{
    severity: string
    category: string
    description: string
    suggestion: string
  }>
  optimizations: {
    title: Array<{ original: string; optimized: string }>
    structure: string[]
    emotion: string[]
    cta: string[]
  }
  complianceWarnings: Array<{ rule: string; matchedText: string; suggestion: string }>
}
