import http from './http'
import type { ApiResponse, PaginatedData } from '@/types/api'
import type {
  MetricConfidence,
  MetricGranularity,
  MetricSource,
  MetricSyncStatus,
  MetricValues,
  MetricValuesInput,
} from '@/types/stage3'

// ── Types ────────────────────────────────────────────────────────────────────

export type {
  MetricConfidence,
  MetricGranularity,
  MetricSource,
  MetricSyncStatus,
  MetricValues,
  MetricValuesInput,
}

/** 指标快照 */
export interface MetricSnapshot {
  id: string
  assetId?: string
  platformCode: string
  accountId?: string
  externalContentId?: string
  contentType?: string
  publishedAt?: string
  /** 统一口径指标（固定键集，缺失补 0） */
  metrics?: MetricValues
  /** 平台扩展指标 */
  metricExtra?: Record<string, unknown>
  metricSource: MetricSource
  confidence: MetricConfidence
  collectedAt?: string
  isManual?: boolean
  revisedFromId?: string
  revisedBy?: number
  createdAt?: string
  /** 修订链（仅详情接口返回） */
  revisions?: MetricSnapshot[]
  reviseReason?: string
}

export interface MetricSnapshotCreateRequest {
  assetId?: string
  platformCode: string
  accountId?: string
  externalContentId?: string
  contentType?: string
  publishedAt?: string
  metrics: MetricValuesInput
  metricExtra?: Record<string, unknown>
  metricSource: MetricSource
  confidence?: MetricConfidence
  collectedAt?: string
}

export interface MetricSnapshotReviseRequest {
  metrics: MetricValuesInput
  metricExtra?: Record<string, unknown>
  confidence?: MetricConfidence
  reason?: string
}

/** CSV 导入结果 */
export interface MetricImportResult {
  importLogId?: number
  totalRows?: number
  importedRows?: number
  skippedRows?: number
  errorRows?: number
  errors?: string[]
}

/** 趋势桶 */
export interface MetricTrendBucket {
  bucket: string
  impressions?: number
  likes?: number
  collects?: number
  comments?: number
  dmInquiries?: number
}

/** 同步状态 */
export interface MetricSyncStatusItem {
  platformCode?: string
  accountId?: string
  status: MetricSyncStatus
  lastCollectedAt?: string
  lastSuccessAt?: string
  consecutiveFailures?: number
  lastError?: string
  delaySeconds?: number
}

export interface MetricCollectRequest {
  platformCode: string
  accountId?: string
}

export interface MetricCollectResponse {
  taskId: string
  status: string
  platformCode?: string
  accountId?: string
}

// ── 快照 ────────────────────────────────────────────────────────────────────

export function fetchMetricSnapshots(params?: {
  assetId?: string
  platform?: string
  from?: string
  to?: string
  source?: MetricSource
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<PaginatedData<MetricSnapshot>>>('/metric-snapshots', { params })
}

export function createMetricSnapshot(payload: MetricSnapshotCreateRequest) {
  return http.post<ApiResponse<MetricSnapshot>>('/metric-snapshots', payload)
}

export function fetchMetricSnapshot(id: string) {
  return http.get<ApiResponse<MetricSnapshot>>(`/metric-snapshots/${id}`)
}

export function reviseMetricSnapshot(id: string, payload: MetricSnapshotReviseRequest) {
  return http.post<ApiResponse<MetricSnapshot>>(`/metric-snapshots/${id}/revise`, payload)
}

/**
 * CSV 导入指标快照（multipart）。
 * 后端要求 `file` + `platformCode`（+ 可选 `accountId`）三个 RequestParam。
 */
export function importMetricSnapshotsCsv(file: File, platformCode: string, accountId?: string) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('platformCode', platformCode)
  if (accountId) {
    formData.append('accountId', accountId)
  }
  return http.post<ApiResponse<MetricImportResult>>('/metric-snapshots/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// ── 趋势 / 采集 / 同步状态 ──────────────────────────────────────────────────

export function fetchMetricTrend(params?: {
  assetId?: string
  platform?: string
  granularity?: MetricGranularity
  from?: string
  to?: string
}) {
  return http.get<ApiResponse<MetricTrendBucket[]>>('/metric-snapshots/trend', { params })
}

export function collectMetrics(payload: MetricCollectRequest) {
  return http.post<ApiResponse<MetricCollectResponse>>('/metric-snapshots/collect', payload)
}

export function fetchMetricSyncStatus(params?: { platform?: string; accountId?: string }) {
  return http.get<ApiResponse<MetricSyncStatusItem[]>>('/metric-snapshots/sync-status', { params })
}
