/**
 * 阶段 3（S3-12）前端共享类型。
 *
 * 与后端契约对应关系见 `STAGE3_ACCEPTANCE.md`（接口契约表）。
 * 本文件只放**跨模块共用**的类型（枚举值 / 通用结构）；
 * 各模块独有的请求 / 响应结构放在对应的 `src/api/*.ts` 内。
 */

// ── 连接器等级 / 健康状态（子能力 5）────────────────────────────────────────

export type ConnectorLevel =
  | 'OFFICIAL_API'
  | 'AUTHORIZED_CONNECTOR'
  | 'BROWSER_ASSIST'
  | 'MANUAL_IMPORT'
  | 'DISABLED'

export type ConnectorHealthStatus =
  | 'HEALTHY'
  | 'DEGRADED'
  | 'UNAVAILABLE'
  | 'NOT_AUTHORIZED'

export type PlatformRolloutStatus = 'launched' | 'planned' | 'disabled'

// ── 指标来源 / 可信度（子能力 2）────────────────────────────────────────────

export type MetricSource =
  | 'OFFICIAL_API'
  | 'AUTHORIZED_CONNECTOR'
  | 'BROWSER_ASSIST'
  | 'MANUAL_IMPORT'
  | 'CSV_IMPORT'
  | 'LEGACY_JSONB'

export type MetricConfidence = 'HIGH' | 'MEDIUM' | 'LOW' | 'UNVERIFIED'

export type MetricGranularity = 'day' | 'week'

export type MetricSyncStatus = 'HEALTHY' | 'DEGRADED' | 'UNKNOWN'

/**
 * 统一指标口径（固定键集）。
 * 后端保证缺失键补 0，平台差异走 `metricExtra`。
 */
export interface MetricValues {
  impressions: number
  plays: number
  clicks: number
  likes: number
  collects: number
  comments: number
  shares: number
  follows: number
  dmInquiries: number
  conversions: number
}

export type MetricValuesInput = Partial<MetricValues> & Record<string, number | undefined>

// ── 热点来源 / 可信度 / 趋势（子能力 4.1）──────────────────────────────────

export type HotTopicSourceType =
  | 'SEARCH_TOOL'
  | 'OFFICIAL_API'
  | 'AUTHORIZED_CONNECTOR'
  | 'MANUAL_IMPORT'
  | 'SEED'

export type HotTopicConfidence = 'HIGH' | 'MEDIUM' | 'LOW' | 'UNVERIFIED'

export type HotTopicTrend = 'rising' | 'falling' | 'flat' | 'new'

export type HotTopicStatus = 'active' | 'superseded'

// ── 竞品来源（子能力 4.2）──────────────────────────────────────────────────

export type CompetitorSourceType = 'BROWSER_ASSIST' | 'MANUAL_IMPORT' | 'OFFICIAL_API'

export type CompetitorStatus = 'active' | 'archived'

// ── 平台账号（子能力 1）────────────────────────────────────────────────────

export type PlatformAccountStatus =
  | 'unconnected'
  | 'connected'
  | 'expired'
  | 'reauthorizing'
  | 'failed'

export type PlatformAccountExpiryState =
  | 'OK'
  | 'EXPIRING_SOON'
  | 'EXPIRING_TODAY'
  | 'EXPIRED'
  | 'UNKNOWN'

// ── 发布 / 复盘（子能力 1、3）──────────────────────────────────────────────

export type PublishScheduleStatus =
  | 'pending'
  | 'scheduled'
  | 'published'
  | 'cancelled'
  | 'failed'

export type PublishTaskStatus =
  | 'pending'
  | 'running'
  | 'succeeded'
  | 'failed'
  | 'manual_required'
  | 'cancelled'

export type RetrospectiveStatus = 'completed' | 'degraded' | 'insufficient_data'

export type RetrospectiveFeedbackStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'

export type RetrospectiveFeedbackTargetType = 'TEMPLATE' | 'KB_DOCUMENT'

export type RetrospectiveScopeType =
  | 'ASSET'
  | 'ACCOUNT'
  | 'PLATFORM'
  | 'CATEGORY'
  | 'CAMPAIGN'
  | 'PERIOD'
