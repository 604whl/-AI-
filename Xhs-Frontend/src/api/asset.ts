import http from './http'
import type { ApiResponse, PaginatedData } from '@/types/api'

// ── Types ────────────────────────────────────────────────────────────────────

export type AssetStatus = 'draft' | 'in_review' | 'approved' | 'scheduled' | 'published' | 'archived'

export interface MediaItem {
  id: string
  mediaType: 'cover' | 'image' | 'video'
  objectKey: string
  role: 'cover' | 'main' | 'attachment'
  sortOrder: number
  width?: number
  height?: number
  fileSize?: number
  createdAt: string
}

export interface ContentAsset {
  id: string
  workspaceId: string
  ownerUserId: number
  title: string
  body: string
  persona?: string
  scenario?: string
  industry?: string
  status: AssetStatus
  sourceTaskId?: string
  platform?: string
  accountId?: string
  currentVersion: number
  createdAt: string
  updatedAt: string
  media: MediaItem[]
  analysisReport?: Record<string, unknown>
}

export interface AssetCreateRequest {
  title: string
  body?: string
  persona?: string
  scenario?: string
  industry?: string
  sourceTaskId?: string
  platform?: string
  accountId?: string
}

export interface AssetUpdateRequest {
  title?: string
  body?: string
  persona?: string
  scenario?: string
  industry?: string
  status?: AssetStatus
  platform?: string
  accountId?: string
  editReason?: string
}

export interface AssetVersion {
  id: string
  assetId: string
  versionNo: number
  title: string
  body: string
  editedBy?: number
  editReason?: string
  createdAt: string
}

export interface DiffLine {
  type: 'added' | 'removed' | 'modified' | 'unchanged'
  content: string
  v?: number
}

export interface AssetMediaRequest {
  mediaType: 'cover' | 'image' | 'video'
  objectKey: string
  role?: 'cover' | 'main' | 'attachment'
  sortOrder?: number
  width?: number
  height?: number
  fileSize?: number
}

// ── CRUD ────────────────────────────────────────────────────────────────────

export function createAsset(payload: AssetCreateRequest) {
  return http.post<ApiResponse<ContentAsset>>('/assets', payload)
}

export function fetchAssets(params?: {
  page?: number
  size?: number
  status?: AssetStatus
}) {
  return http.get<ApiResponse<PaginatedData<ContentAsset>>>('/assets', { params })
}

export function fetchAsset(id: string) {
  return http.get<ApiResponse<ContentAsset>>(`/assets/${id}`)
}

export function updateAsset(id: string, payload: AssetUpdateRequest) {
  return http.patch<ApiResponse<ContentAsset>>(`/assets/${id}`, payload)
}

export function deleteAsset(id: string) {
  return http.delete<ApiResponse<null>>(`/assets/${id}`)
}

// ── 版本管理 ────────────────────────────────────────────────────────────────

export function fetchAssetVersions(id: string) {
  return http.get<ApiResponse<AssetVersion[]>>(`/assets/${id}/versions`)
}

export function rollbackAsset(id: string, versionNo: number) {
  return http.post<ApiResponse<ContentAsset>>(`/assets/${id}/rollback/${versionNo}`, {})
}

export function diffAssetVersions(id: string, v1: number, v2: number) {
  return http.get<ApiResponse<DiffLine[]>>(`/assets/${id}/diff`, { params: { v1, v2 } })
}

// ── 状态机 ──────────────────────────────────────────────────────────────────

export function submitAssetReview(id: string) {
  return http.post<ApiResponse<ContentAsset>>(`/assets/${id}/submit-review`, {})
}

// ── 多媒体附件 ──────────────────────────────────────────────────────────────

export function addAssetMedia(id: string, payload: AssetMediaRequest) {
  return http.post<ApiResponse<ContentAsset>>(`/assets/${id}/media`, payload)
}

export function removeAssetMedia(id: string, mediaId: string) {
  return http.delete<ApiResponse<ContentAsset>>(`/assets/${id}/media/${mediaId}`)
}
