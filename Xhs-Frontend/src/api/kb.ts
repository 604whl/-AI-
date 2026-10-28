import http from './http'
import type { ApiResponse, PaginatedData } from '@/types/api'

// ── Types ────────────────────────────────────────────────────────────────────

/** 知识库文档摘要（按 docId 聚合） */
export interface KbDocumentSummary {
  docId: string
  title?: string
  docType?: string
  contentType?: string
  persona?: string[]
  tags?: string[]
  chunkCount?: number
  hasEmbedding?: boolean
  createdAt?: string
  updatedAt?: string
  workspaceId?: string
}

/** 文档 chunk 摘要 */
export interface KbDocumentChunkSummary {
  chunkId?: string
  chunkIndex?: number
  content?: string
  hasEmbedding?: boolean
  createdAt?: string
  updatedAt?: string
}

/** 文档详情（元信息 + chunk 列表） */
export interface KbDocumentDetail {
  docId: string
  title?: string
  docType?: string
  contentType?: string
  persona?: string[]
  tags?: string[]
  chunkCount?: number
  hasEmbedding?: boolean
  createdAt?: string
  updatedAt?: string
  workspaceId?: string
  chunks?: KbDocumentChunkSummary[]
}

/** 索引状态 */
export interface KbDocumentIndexStatus {
  docId: string
  sourceFilePresent?: boolean
  sourceFilePath?: string
  lastIndexedAt?: string
  chunkCount?: number
  embeddedChunkCount?: number
  /** 源文件比索引更新（需要重新入库） */
  stale?: boolean
}

/** 删除结果 */
export interface KbDocumentDeleteResult {
  docId: string
  deletedChunks?: number
  sourceFileRemoved?: boolean
  sourceFileCleanupNote?: string
}

/** 检索命中 */
export interface KbDocumentSearchHit {
  docId?: string
  title?: string
  content?: string
  docType?: string
  score?: number
}

// ── API ─────────────────────────────────────────────────────────────────────

/** 分页列出知识库文档。 */
export function fetchKbDocuments(params?: {
  docType?: string
  contentType?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<PaginatedData<KbDocumentSummary>>>('/admin/kb/documents', { params })
}

/** 按关键字检索知识库文档。 */
export function searchKbDocuments(params: { keyword: string; docTypes?: string; limit?: number }) {
  return http.get<ApiResponse<KbDocumentSearchHit[]>>('/admin/kb/documents/search', { params })
}

/** 查询单个文档的 chunk 明细。 */
export function fetchKbDocument(docId: string) {
  return http.get<ApiResponse<KbDocumentDetail>>(`/admin/kb/documents/${docId}`)
}

/** 查询单个文档的索引状态。 */
export function fetchKbDocumentIndexStatus(docId: string) {
  return http.get<ApiResponse<KbDocumentIndexStatus>>(`/admin/kb/documents/${docId}/index-status`)
}

/** 删除知识库文档（全部 chunk + 唯一匹配的源文件）。 */
export function deleteKbDocument(docId: string) {
  return http.delete<ApiResponse<KbDocumentDeleteResult>>(`/admin/kb/documents/${docId}`)
}
