import http from './http'
import type { ApiResponse, PaginatedData } from '@/types/api'
import type {
  HotTopicConfidence,
  HotTopicSourceType,
  HotTopicStatus,
  HotTopicTrend,
} from '@/types/stage3'

// ── Types ────────────────────────────────────────────────────────────────────

export type {
  HotTopicConfidence,
  HotTopicSourceType,
  HotTopicStatus,
  HotTopicTrend,
}

/** 可做方向（每条必须引用真实标题 + 来源 + 采集时间） */
export interface HotTopicSuggestedAngle {
  angle?: string
  title?: string
  sourceName?: string
  fetchedAt?: string
  evidence?: {
    title?: string
    sourceName?: string
    fetchedAt?: string
  }
}

/** 热点条目 */
export interface HotTopic {
  id: string | number
  platform?: string
  category?: string
  title: string
  heatScore?: number
  /** 趋势：rising / falling / flat / new */
  trend?: HotTopicTrend
  /** 来源名称（如 web_search / static_seed / providerCode） */
  sourceName?: string
  /** 来源类型：SEARCH_TOOL / OFFICIAL_API / AUTHORIZED_CONNECTOR / MANUAL_IMPORT / SEED */
  sourceType?: HotTopicSourceType
  /** 可信度：HIGH / MEDIUM / LOW / UNVERIFIED */
  confidence?: HotTopicConfidence
  /** 是否实时；静态种子必须为 false */
  isRealtime?: boolean
  sourceUrl?: string
  /** 去重状态：active / superseded */
  status?: HotTopicStatus
  promotedToPool?: boolean
  /** 可做方向 */
  suggestedAngles?: HotTopicSuggestedAngle[]
  /** 采集时间（必须在 UI 暴露） */
  fetchedAt?: string
  /** 单条降级标记 */
  degraded?: boolean
  degradedReason?: string
}

/**
 * 列表顶层降级字段。
 * 后端可能把降级信息放在列表响应的顶层，也可能放在每条 item 上，两种都兼容。
 */
export interface HotTopicListMeta {
  degraded?: boolean
  degradedReason?: string
  fallbackAvailable?: string
}

export interface HotTopicListResult extends HotTopicListMeta {
  items: HotTopic[]
  total: number
  page: number
  size: number
}

/** 热点样本 */
export interface HotTopicSample {
  id: string | number
  title?: string
  sourceUrl?: string
  fetchedAt?: string
}

export interface HotTopicRefreshResponse {
  taskId?: string
  status?: string
  count?: number
  message?: string
}

export interface HotTopicImportItem {
  title: string
  platform?: string
  heatScore?: number
  sourceName?: string
  sourceUrl?: string
}

export interface HotTopicImportResult {
  imported?: number
  skipped?: number
  degraded?: boolean
  degradedReason?: string
  importLogId?: number
  errorRows?: number
}

export interface HotTopicPromoteRequest {
  category?: string
  tags?: string[]
}

export interface HotTopicPromoteResponse {
  topicPoolId?: string
  id?: string
}

// ── 列表 ────────────────────────────────────────────────────────────────────

/**
 * 拉取热点列表（新版路径）。
 * 响应结构兼容两种形态：
 * - 直接是数组 `[{...}]`
 * - 或分页对象 `{items, total, page, size, degraded, degradedReason, fallbackAvailable}`
 */
export function fetchHotTopics(params?: {
  platform?: string
  category?: string
  from?: string
  to?: string
  sourceType?: HotTopicSourceType
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<HotTopic[] | HotTopicListResult>>('/hot-topics', { params })
}

/** 拉取热点列表（兼容后端保留的 `/hot-topics/list` 路径）。 */
export function fetchHotTopicList(params?: {
  platform?: string
  category?: string
  promoted?: boolean
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<PaginatedData<HotTopic>>>('/hot-topics/list', { params })
}

// ── 采集 / 导入 ─────────────────────────────────────────────────────────────

export function refreshHotTopics(payload?: { platform?: string; category?: string }) {
  return http.post<ApiResponse<HotTopicRefreshResponse>>('/hot-topics/refresh', payload ?? {})
}

/** JSON 批量导入热点。 */
export function importHotTopicsJson(items: HotTopicImportItem[]) {
  return http.post<ApiResponse<HotTopicImportResult>>('/hot-topics/import', { items })
}

/** CSV 导入热点（multipart）。 */
export function importHotTopicsCsv(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<ApiResponse<HotTopicImportResult>>('/hot-topics/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// ── 样本 / 选题池 ───────────────────────────────────────────────────────────

export function fetchHotTopicSamples(id: string | number) {
  return http.get<ApiResponse<HotTopicSample[]>>(`/hot-topics/${id}/samples`)
}

export function promoteHotTopic(id: string | number, payload?: HotTopicPromoteRequest) {
  return http.post<ApiResponse<HotTopicPromoteResponse>>(`/hot-topics/${id}/promote`, payload ?? {})
}
