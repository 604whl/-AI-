import http from './http'
import type { ApiResponse } from '@/types/api'
import type {
  PlatformAccountExpiryState,
  PlatformAccountStatus,
} from '@/types/stage3'

// ── Types ────────────────────────────────────────────────────────────────────

export type { PlatformAccountExpiryState, PlatformAccountStatus }

/** 平台账号（绝不返回凭据内容，只返回 hasCredential 布尔位） */
export interface PlatformAccount {
  id: string
  platformCode?: string
  platformName?: string
  accountLabel?: string
  externalAccountId?: string
  connectorProviderCode?: string
  level?: string
  status: PlatformAccountStatus
  /** OK / EXPIRING_SOON / EXPIRING_TODAY / EXPIRED / UNKNOWN */
  expiryState?: PlatformAccountExpiryState
  authorizedAt?: string
  expiresAt?: string
  lastSuccessAt?: string
  lastError?: string
  hasCredential?: boolean
  createdAt?: string
}

export interface PlatformAccountCreateRequest {
  platformCode: string
  accountLabel: string
  connectorProviderCode: string
  credentialRef?: string
}

export interface PlatformAccountReauthorizeRequest {
  credentialRef?: string
}

// ── API ─────────────────────────────────────────────────────────────────────

export function fetchPlatformAccounts(params?: {
  platformCode?: string
  platform?: string
  status?: PlatformAccountStatus
}) {
  return http.get<ApiResponse<PlatformAccount[]>>('/platform-accounts', { params })
}

export function fetchPlatformAccount(id: string) {
  return http.get<ApiResponse<PlatformAccount>>(`/platform-accounts/${id}`)
}

export function createPlatformAccount(payload: PlatformAccountCreateRequest) {
  return http.post<ApiResponse<PlatformAccount>>('/platform-accounts', payload)
}

export function reauthorizePlatformAccount(id: string, payload?: PlatformAccountReauthorizeRequest) {
  return http.post<ApiResponse<PlatformAccount>>(`/platform-accounts/${id}/reauthorize`, payload ?? {})
}

export function disconnectPlatformAccount(id: string) {
  return http.post<ApiResponse<PlatformAccount>>(`/platform-accounts/${id}/disconnect`, {})
}
