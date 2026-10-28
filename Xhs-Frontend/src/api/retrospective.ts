import http from './http'
import type { ApiResponse, PaginatedData } from '@/types/api'
import type {
  RetrospectiveFeedbackStatus,
  RetrospectiveFeedbackTargetType,
  RetrospectiveScopeType,
  RetrospectiveStatus,
} from '@/types/stage3'

// ── Types ────────────────────────────────────────────────────────────────────

export type {
  RetrospectiveFeedbackStatus,
  RetrospectiveFeedbackTargetType,
  RetrospectiveScopeType,
  RetrospectiveStatus,
}

/** 预测来源（模型 / 提示词 / 知识库版本） */
export interface ForecastSource {
  modelName?: string
  promptVersion?: string
  kbVersion?: string
  generatedAt?: string
}

/** 预测 */
export interface Forecast {
  id: string
  assetId?: string
  platformCode?: string
  forecastType?: string
  predictedMetrics?: Record<string, number>
  predictedPublishedAt?: string
  source?: ForecastSource
  createdAt?: string
}

export interface ForecastCreateRequest {
  assetId: string
  platformCode: string
  forecastType: string
  predictedMetrics: Record<string, number>
  predictedPublishedAt?: string
}

/** 对比行 */
export interface ComparisonRow {
  dimension: string
  predictedValue?: number
  actualValue?: number
  deviationPct?: number
  hit?: boolean
  dimensionCategory?: string
  reason?: string
}

/** 对比 */
export interface Comparison {
  id?: string
  forecastId?: string
  snapshotId?: string
  assetId?: string
  rows: ComparisonRow[]
  createdAt?: string
}

export interface ComparisonCreateRequest {
  forecastId: string
  snapshotId: string
}

/** 复盘建议 */
export interface RetrospectiveSuggestion {
  id?: string
  dimension?: string
  content?: string
  suggestion?: string
  evidence?: string
  evidenceRefs?: string[]
  applySuggestion?: string
  confidence?: string
}

/** 复盘报告 */
export interface Retrospective {
  id: string
  scopeType: RetrospectiveScopeType
  scopeKey?: string
  assetId?: string
  accountId?: string
  platformCode?: string
  categoryCode?: string
  campaignId?: string
  periodStart?: string
  periodEnd?: string
  summary?: string
  predictions?: Array<Record<string, unknown>>
  actuals?: Array<Record<string, unknown>>
  hitDimensions?: string[]
  missedDimensions?: string[]
  suggestions?: RetrospectiveSuggestion[]
  status: RetrospectiveStatus
  degraded?: boolean
  degradedReason?: string
  suggestionCount?: number
  generatedAt?: string
  createdAt?: string
}

export interface RetrospectiveListItem {
  id: string
  scopeType: RetrospectiveScopeType
  scopeKey?: string
  suggestionCount?: number
  status: RetrospectiveStatus
  generatedAt?: string
}

export interface RetrospectiveCreateRequest {
  scopeType: RetrospectiveScopeType
  scopeKey: string
  assetId?: string
  accountId?: string
  platformCode?: string
  categoryCode?: string
  campaignId?: string
  periodStart?: string
  periodEnd?: string
}

export interface RetrospectiveCreateResponse {
  reportId: string
  taskId?: string
  status: RetrospectiveStatus
}

/** 复盘回写（反馈） */
export interface RetrospectiveFeedback {
  id: string
  reportId?: string
  targetType: RetrospectiveFeedbackTargetType
  targetId: string
  status: RetrospectiveFeedbackStatus
  proposedContent?: string
  proposedStats?: Record<string, unknown>
  reviewedBy?: number
  reviewedAt?: string
  reviewComment?: string
  createdAt?: string
}

export interface RetrospectiveFeedbackCreateRequest {
  targetType: RetrospectiveFeedbackTargetType
  targetId: string
  proposedContent: string
  proposedStats?: Record<string, unknown>
}

/** 模板效果统计 */
export interface TemplateEffectStats {
  applyCount?: number
  adoptionRate?: number
  avgImpressions?: number
  avgCollectRate?: number
  avgDmInquiries?: number
  sampleSize?: number
}

// ── 预测 / 对比 ──────────────────────────────────────────────────────────────

export function createForecast(payload: ForecastCreateRequest) {
  return http.post<ApiResponse<Forecast>>('/forecasts', payload)
}

export function fetchForecasts(params?: {
  assetId?: string
  platform?: string
  forecastType?: string
}) {
  return http.get<ApiResponse<Forecast[]>>('/forecasts', { params })
}

export function createComparison(payload: ComparisonCreateRequest) {
  return http.post<ApiResponse<Comparison>>('/comparisons', payload)
}

export function fetchComparisons(params?: {
  assetId?: string
  dimension?: string
  hit?: boolean
}) {
  return http.get<ApiResponse<Comparison[]>>('/comparisons', { params })
}

// ── 复盘报告 ────────────────────────────────────────────────────────────────

export function createRetrospective(payload: RetrospectiveCreateRequest) {
  return http.post<ApiResponse<RetrospectiveCreateResponse>>('/retrospectives', payload)
}

export function fetchRetrospectives(params?: {
  scopeType?: RetrospectiveScopeType
  platform?: string
  from?: string
  to?: string
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<PaginatedData<RetrospectiveListItem>>>('/retrospectives', { params })
}

export function fetchRetrospective(id: string) {
  return http.get<ApiResponse<Retrospective>>(`/retrospectives/${id}`)
}

/**
 * 导出复盘报告。
 *
 * 后端返回二进制（消耗 EXPORT 额度），`http.ts` 的响应拦截器对
 * `responseType === 'blob'` 直接返回 response，不做 code 检查。
 */
export function exportRetrospective(id: string) {
  return http.get(`/retrospectives/${id}/export`, { responseType: 'blob' })
}

// ── 回写审批 ────────────────────────────────────────────────────────────────

export function fetchRetrospectiveFeedback(id: string) {
  return http.get<ApiResponse<RetrospectiveFeedback[]>>(`/retrospectives/${id}/feedback`)
}

export function createRetrospectiveFeedback(id: string, payload: RetrospectiveFeedbackCreateRequest) {
  return http.post<ApiResponse<RetrospectiveFeedback>>(`/retrospectives/${id}/feedback`, payload)
}

export function approveRetrospectiveFeedback(id: string, comment?: string) {
  return http.post<ApiResponse<RetrospectiveFeedback>>(`/retrospectives/feedback/${id}/approve`, { comment })
}

export function rejectRetrospectiveFeedback(id: string, comment: string) {
  return http.post<ApiResponse<RetrospectiveFeedback>>(`/retrospectives/feedback/${id}/reject`, { comment })
}

// ── 模板效果统计 ────────────────────────────────────────────────────────────

export function fetchTemplateEffectStats(id: string) {
  return http.get<ApiResponse<TemplateEffectStats>>(`/templates/${id}/effect-stats`)
}
