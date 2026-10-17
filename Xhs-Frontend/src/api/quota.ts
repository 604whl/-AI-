import http from './http'
import type { ApiResponse } from '@/types/api'

export interface QuotaResponse {
  workspaceId: string
  dailyQuota: number
  used: number
  remaining: number
  resetAt: string | null
}

export interface LedgerEntry {
  id: string
  workspaceId: string
  userId: string | null
  action: string
  refId: string | null
  delta: number
  status: 'CONSUMED' | 'REFUNDED'
  relatedLogId: string | null
  reason: string | null
  createdAt: string
}

export interface LedgerPage {
  records: LedgerEntry[]
  total: number
  size: number
  current: number
}

/**
 * 获取 workspace 配额信息
 */
export function getQuota(workspaceId: string) {
  return http.get<ApiResponse<QuotaResponse>>(`/workspaces/${workspaceId}/quota`)
}

/**
 * 获取配额审计 ledger 列表
 */
export function getLedger(
  workspaceId: string,
  params?: { page?: number; size?: number; action?: string; status?: string }
) {
  return http.get<ApiResponse<LedgerPage>>(`/workspaces/${workspaceId}/quota/ledger`, { params })
}

/**
 * 更新 workspace 每日配额（仅 ADMIN/OWNER）
 */
export function updateQuota(workspaceId: string, dailyQuota: number) {
  return http.patch<ApiResponse<QuotaResponse>>(`/workspaces/${workspaceId}/quota`, { dailyQuota })
}
