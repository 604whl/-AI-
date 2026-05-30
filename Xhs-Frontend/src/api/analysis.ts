import http from './http'
import type {
  AnalysisCreateRequest,
  AnalysisDetail,
  AnalysisListItem,
  AnalysisScenario,
  AnalysisStatus,
  ApiResponse,
  ComplianceWarning,
  PaginatedData,
  TitleGenerateRequest,
  TitleGenerateResponse,
} from '@/types/api'

export function createAnalysis(payload: AnalysisCreateRequest) {
  return http.post<ApiResponse<Pick<AnalysisDetail, 'id' | 'status'>>>('/analysis', payload)
}

export function fetchAnalysis(id: string) {
  return http.get<ApiResponse<AnalysisDetail>>(`/analysis/${id}`)
}

export function fetchAnalysisList(params?: {
  page?: number
  size?: number
  status?: AnalysisStatus
  scenario?: AnalysisScenario
  keyword?: string
}) {
  return http.get<ApiResponse<PaginatedData<AnalysisListItem>>>('/analysis', { params })
}

export function deleteAnalysis(id: string) {
  return http.delete<ApiResponse<null>>(`/analysis/${id}`)
}

export function generateTitles(payload: TitleGenerateRequest) {
  return http.post<ApiResponse<TitleGenerateResponse>>('/titles', payload)
}

export function generateTitlesByAnalysisId(
  id: string,
  payload: Omit<TitleGenerateRequest, 'analysisId' | 'title' | 'body'>,
) {
  return http.post<ApiResponse<TitleGenerateResponse>>(`/analysis/${id}/titles`, payload)
}

export interface OptimizeDraftRequest {
  includeTitle?: boolean
  tone?: string
  maxLength?: number
}

export interface OptimizeDraftResponse {
  analysisId: string
  optimizedTitle: string
  optimizedBody: string
  structureOutline: string[]
  cta: string
  complianceWarnings: ComplianceWarning[]
  promptVersion: string
  wordCount: number
}

export function optimizeDraft(id: string, payload?: OptimizeDraftRequest) {
  return http.post<ApiResponse<OptimizeDraftResponse>>(`/analysis/${id}/optimize-draft`, payload ?? {})
}
