import http from './http'
import type { ApiResponse } from '@/types/api'
import type {
  ConnectorHealthStatus,
  ConnectorLevel,
  PlatformRolloutStatus,
} from '@/types/stage3'

// ── Types ────────────────────────────────────────────────────────────────────

export type { ConnectorHealthStatus, ConnectorLevel, PlatformRolloutStatus }

/** 连接器健康摘要（列表项内嵌） */
export interface ConnectorHealthSummary {
  status?: ConnectorHealthStatus
  lastSuccessAt?: string
  lastError?: string
}

/** 连接器列表项 */
export interface ConnectorListItem {
  platformCode: string
  platformName?: string
  connectorProviderCode: string
  level?: ConnectorLevel
  authorized?: boolean
  rolloutStatus?: PlatformRolloutStatus
  writeEnabled?: boolean
  readEnabled?: boolean
  health?: ConnectorHealthSummary
}

/** 连接器健康详情 */
export interface ConnectorHealth {
  status: ConnectorHealthStatus
  lastSuccessAt?: string
  lastCheckedAt?: string
  lastErrorCode?: string
  lastErrorMessage?: string
  consecutiveFailures?: number
  degradedTo?: string
}

/** 诊断检查项 */
export interface ConnectorCheckResult {
  name: string
  passed?: boolean
  detail?: string
}

/** 诊断结果 */
export interface ConnectorDiagnoseResult {
  status: ConnectorHealthStatus
  checks: ConnectorCheckResult[]
  degradedTo?: string
  checkedAt?: string
}

/** 平台降级状态 */
export interface ConnectorDegradation {
  platformCode: string
  currentLevel?: ConnectorLevel
  degradedTo?: string
  reason?: string
  since?: string
}

// ── API ─────────────────────────────────────────────────────────────────────

export function fetchConnectors() {
  return http.get<ApiResponse<ConnectorListItem[]>>('/connectors')
}

export function fetchConnectorHealth(providerCode: string) {
  return http.get<ApiResponse<ConnectorHealth>>(`/connectors/${providerCode}/health`)
}

/**
 * 诊断连接器（只读、无副作用）。
 * 后端该接口不接收请求体，仅接受可选的 workspaceId。
 */
export function diagnoseConnector(providerCode: string, workspaceId?: string) {
  return http.post<ApiResponse<ConnectorDiagnoseResult>>(
    `/connectors/${providerCode}/diagnose`,
    workspaceId ? { workspaceId } : {},
  )
}

export function fetchConnectorDegradation(params?: { platform?: string }) {
  return http.get<ApiResponse<ConnectorDegradation[]>>('/connectors/degradation', { params })
}
