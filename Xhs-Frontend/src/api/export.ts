import http from './http'
import type { ApiResponse } from '@/types/api'

// ── Types ────────────────────────────────────────────────────────────────────

export type ExportFormat = 'md' | 'docx' | 'zip' | 'csv'

export type ExportStatus = 'pending' | 'processing' | 'ready' | 'failed' | 'expired'

export interface ExportResponse {
  id: string
  format: ExportFormat
  status: ExportStatus
  errorMessage?: string
  downloadUrl?: string
  fileSize?: number
  expiresAt?: string
  createdAt: string
  finishedAt?: string
}

export interface ExportListItem {
  id: string
  format: ExportFormat
  status: ExportStatus
  errorMessage?: string
  fileSize?: number
  expiresAt?: string
  createdAt: string
  finishedAt?: string
}

// ── 单资产异步导出（MD / DOCX / ZIP） ─────────────────────────────────────────

/**
 * 发起异步导出，返回 exportId。
 * 前端轮询 GET /v1/exports/{id} 直到 status === 'ready'。
 */
export function enqueueExport(assetId: string, format: ExportFormat) {
  return http.post<ApiResponse<ExportResponse>>(`/assets/${assetId}/export`, null, {
    params: { format },
  })
}

/**
 * 查询导出状态。
 */
export function fetchExportStatus(exportId: string) {
  return http.get<ApiResponse<ExportResponse>>(`/exports/${exportId}`)
}

/**
 * 获取下载 URL（302 重定向到签名 URL）。
 */
export function getExportDownloadUrl(exportId: string): string {
  return `${import.meta.env.VITE_API_BASE_URL}/v1/exports/${exportId}/download`
}

// ── CSV 批量导出 ─────────────────────────────────────────────────────────────

/**
 * 触发 CSV 同步下载。
 */
export function downloadCsv(workspaceId: string, assetIds?: string[]) {
  const params = assetIds?.length
    ? { assetIds: assetIds.join(',') }
    : {}
  const query = new URLSearchParams(params as Record<string, string>).toString()
  const base = import.meta.env.VITE_API_BASE_URL
  const url = `${base}/v1/workspaces/${workspaceId}/export.csv${query ? '?' + query : ''}`
  // 触发浏览器下载
  const a = document.createElement('a')
  a.href = url
  a.download = `xhs-assets-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
}

// ── 导出历史 ─────────────────────────────────────────────────────────────────

export function fetchExportHistory(workspaceId: string, params?: {
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<ExportListItem[]>>(`/workspaces/${workspaceId}/exports`, { params })
}
