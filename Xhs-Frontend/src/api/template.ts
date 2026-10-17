import http from './http'
import type { ApiResponse, PaginatedData } from '@/types/api'

// ── Types ────────────────────────────────────────────────────────────────────

export type TemplateScope = 'OFFICIAL' | 'WORKSPACE' | 'PERSONAL'
export type TemplateStatus = 'active' | 'archived'

export interface Template {
  id: string
  scope: TemplateScope
  workspaceId?: string
  ownerUserId?: number
  category: string
  scenario?: string
  persona?: string
  title: string
  bodyTemplate: string
  variables: string[]
  defaultGoal?: string
  defaultTone?: string
  version: number
  status: TemplateStatus
  useCount: number
  createdAt: string
  updatedAt: string
}

export interface TemplateCreateRequest {
  scope: TemplateScope
  workspaceId?: string
  category: string
  scenario?: string
  persona?: string
  title: string
  bodyTemplate: string
  variables?: string[]
  defaultGoal?: string
  defaultTone?: string
}

export interface TemplateUpdateRequest {
  title?: string
  bodyTemplate?: string
  variables?: string[]
  category?: string
  scenario?: string
  persona?: string
  defaultGoal?: string
  defaultTone?: string
}

export interface TemplateRenderRequest {
  variables: Record<string, string>
}

export interface TemplateRenderResponse {
  templateId: string
  body: string
}

export interface TemplateApplyRequest {
  assetId?: string
  variables: Record<string, string>
}

export interface TemplateApplyResponse {
  templateId: string
  body: string
  useCount: number
}

export interface TemplateApplyStatsResponse {
  templateId: string
  totalCount: number
  dailyStats: Array<{
    day: string
    count: number
  }>
}

// ── CRUD ────────────────────────────────────────────────────────────────────

export function fetchTemplates(params?: {
  scope?: TemplateScope | string
  category?: string
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<PaginatedData<Template>>>('/templates', { params })
}

export function fetchTemplate(id: string) {
  return http.get<ApiResponse<Template>>(`/templates/${id}`)
}

export function createTemplate(payload: TemplateCreateRequest) {
  return http.post<ApiResponse<Template>>('/templates', payload)
}

export function updateTemplate(id: string, payload: TemplateUpdateRequest) {
  return http.patch<ApiResponse<Template>>(`/templates/${id}`, payload)
}

export function publishTemplate(id: string) {
  return http.post<ApiResponse<Template>>(`/templates/${id}/publish`, {})
}

export function archiveTemplate(id: string) {
  return http.post<ApiResponse<null>>(`/templates/${id}/archive`, {})
}

export function duplicateTemplate(id: string, targetWorkspaceId: string) {
  return http.post<ApiResponse<Template>>(`/templates/${id}/duplicate`, {
    targetWorkspaceId,
  })
}

// ── 渲染 / 应用 / 统计 ───────────────────────────────────────────────────────

export function renderTemplate(id: string, payload: TemplateRenderRequest) {
  return http.post<ApiResponse<TemplateRenderResponse>>(`/templates/${id}/render`, payload)
}

export function applyTemplate(id: string, payload: TemplateApplyRequest) {
  return http.post<ApiResponse<TemplateApplyResponse>>(`/templates/${id}/apply`, payload)
}

export function fetchTemplateApplyStats(id: string) {
  return http.get<ApiResponse<TemplateApplyStatsResponse>>(`/templates/${id}/apply-stats`)
}
