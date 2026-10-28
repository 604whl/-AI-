import http from './http'
import type { ApiResponse, PaginatedData } from '@/types/api'
import type { CompetitorSourceType, CompetitorStatus } from '@/types/stage3'

// ── Types ────────────────────────────────────────────────────────────────────

export type { CompetitorSourceType, CompetitorStatus }

/** 竞品账号 */
export interface CompetitorAccount {
  id: string
  platformCode: string
  accountName: string
  accountUrl?: string
  externalAccountId?: string
  followerCount?: number
  category?: string
  tags?: string[]
  noteCount?: number
  status: CompetitorStatus
  addedBy?: number
  createdAt?: string
  updatedAt?: string
}

/** 竞品笔记内容结构（段落结构 / 钩子类型 / CTA 位置 / 标签集合） */
export interface CompetitorContentStructure {
  sections?: string[]
  hookType?: string
  ctaPosition?: string
  tags?: string[]
  [key: string]: unknown
}

/** 竞品笔记互动数据（likes / collects / comments / shares） */
export interface CompetitorEngagement {
  likes?: number
  collects?: number
  comments?: number
  shares?: number
  [key: string]: unknown
}

/** 竞品笔记 */
export interface CompetitorNote {
  id: string
  competitorAccountId?: string
  platformCode?: string
  noteTitle?: string
  noteUrl?: string
  publishedAt?: string
  contentStructure?: CompetitorContentStructure
  engagement?: CompetitorEngagement
  /** 真实片段引用（原文片段，可高亮） */
  evidenceText?: string
  sourceType?: CompetitorSourceType
  capturedAt?: string
  createdAt?: string
}

export interface CompetitorAccountCreateRequest {
  platformCode: string
  accountName: string
  accountUrl?: string
  category?: string
  tags?: string[]
}

export interface CompetitorAccountCreateResponse {
  id: string
  status: CompetitorStatus
}

export interface CompetitorNoteCreateRequest {
  noteTitle?: string
  noteUrl?: string
  publishedAt?: string
  contentStructure?: CompetitorContentStructure
  engagement?: CompetitorEngagement
  /** 必填：没有原文片段不得入库 */
  evidenceText: string
  sourceType?: CompetitorSourceType
}

export interface CompetitorNoteCreateResponse {
  id: string
  capturedAt?: string
}

export interface CompetitorNoteImportResult {
  importLogId?: number
  imported?: number
  skipped?: number
  errorRows?: number
  errors?: string[]
}

export interface CompetitorAnalyzeRequest {
  competitorAccountIds: string[]
  assetId?: string
  platformCode: string
  category?: string
}

export interface CompetitorAnalyzeResponse {
  taskId: string
  status: string
}

// ── 账号 ────────────────────────────────────────────────────────────────────

export function fetchCompetitors(params?: {
  platform?: string
  category?: string
  status?: CompetitorStatus
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<PaginatedData<CompetitorAccount>>>('/competitors', { params })
}

export function createCompetitor(payload: CompetitorAccountCreateRequest) {
  return http.post<ApiResponse<CompetitorAccountCreateResponse>>('/competitors', payload)
}

// ── 笔记 ────────────────────────────────────────────────────────────────────

export function fetchCompetitorNotes(id: string, params?: { page?: number; size?: number }) {
  return http.get<ApiResponse<PaginatedData<CompetitorNote>>>(`/competitors/${id}/notes`, { params })
}

export function createCompetitorNote(id: string, payload: CompetitorNoteCreateRequest) {
  return http.post<ApiResponse<CompetitorNoteCreateResponse>>(`/competitors/${id}/notes`, payload)
}

/** CSV 导入竞品笔记（multipart）。 */
export function importCompetitorNotes(id: string, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<ApiResponse<CompetitorNoteImportResult>>(`/competitors/${id}/notes/import`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// ── 分析 ────────────────────────────────────────────────────────────────────

export function analyzeCompetitors(payload: CompetitorAnalyzeRequest) {
  return http.post<ApiResponse<CompetitorAnalyzeResponse>>('/competitors/analyze', payload)
}
