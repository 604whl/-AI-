/**
 * Data Retention & Export API
 * P0-9: Account Deletion, Workspace Destruction, Personal Data Export
 */
import http from './http'
import type { ApiResponse } from './http'

// ── Types ────────────────────────────────────────────────────────────────────

export interface DeletionStatusResponse {
  deletionStatus: 'active' | 'scheduled' | 'cancelled' | 'deleted'
  scheduledDeletionAt: string | null
  daysRemaining: number | null
  deletionConfirmedAt: string | null
}

export interface WorkspaceDestructionResponse {
  id?: string
  workspaceId: string
  status: 'pending' | 'confirmed' | 'cancelled' | 'executed'
  executeAt: string | null
  daysRemaining: number | null
  confirmedAt?: string | null
  executedAt?: string | null
  createdAt?: string | null
}

export interface RetentionPolicyResponse {
  workspaceId: string
  assetRetentionDays: number
  logRetentionDays: number
  mediaRetentionDays: number
  chatSessionRetentionDays: number
  updatedAt?: string | null
  updatedBy?: number | null
}

export interface RetentionPolicyRequest {
  assetRetentionDays?: number
  logRetentionDays?: number
  mediaRetentionDays?: number
  chatSessionRetentionDays?: number
}

export interface ExportRequestResponse {
  id: string
  exportType: 'personal_data' | 'workspace_data'
  status: 'pending' | 'processing' | 'ready' | 'failed' | 'expired'
  downloadUrl?: string | null
  fileSize?: number | null
  expiresAt?: string | null
  errorMessage?: string | null
  createdAt: string
  finishedAt?: string | null
}

// ── Account Deletion API ─────────────────────────────────────────────────────

/**
 * DELETE /v1/auth/me?confirm=DELETE
 * 申请账号注销（30 天软删）
 */
export function requestAccountDeletion(confirm: string) {
  return http.delete<ApiResponse<DeletionStatusResponse>>('/auth/me', {
    params: { confirm },
  })
}

/**
 * POST /v1/auth/me/cancel-deletion
 * 撤销账号注销
 */
export function cancelAccountDeletion() {
  return http.post<ApiResponse<DeletionStatusResponse>>('/auth/me/cancel-deletion')
}

/**
 * GET /v1/auth/me/deletion-status
 * 查询注销状态
 */
export function fetchDeletionStatus() {
  return http.get<ApiResponse<DeletionStatusResponse>>('/auth/me/deletion-status')
}

// ── Data Export API ──────────────────────────────────────────────────────────

/**
 * GET /v1/auth/me/export
 * 请求个人数据导出
 */
export function requestPersonalExport(format: string = 'zip') {
  return http.get<ApiResponse<ExportRequestResponse>>('/auth/me/export', {
    params: { format },
  })
}

/**
 * GET /v1/auth/me/exports
 * 查询导出历史
 */
export function fetchPersonalExports() {
  return http.get<ApiResponse<ExportRequestResponse[]>>('/auth/me/exports')
}

/**
 * GET /v1/auth/me/exports/{exportId}
 * 查询导出状态
 */
export function fetchPersonalExport(exportId: string) {
  return http.get<ApiResponse<ExportRequestResponse>>(`/auth/me/exports/${exportId}`)
}

// ── Workspace Destruction API ───────────────────────────────────────────────

/**
 * POST /v1/workspaces/{id}/destroy?confirm=DELETE
 * 发起工作区销毁（7 天宽限）
 */
export function requestWorkspaceDestruction(workspaceId: string, confirm: string) {
  return http.post<ApiResponse<WorkspaceDestructionResponse>>(
    `/workspaces/${workspaceId}/destroy`, null, { params: { confirm } }
  )
}

/**
 * POST /v1/workspaces/{id}/confirm-destroy?confirm=DELETE
 * 二次确认销毁
 */
export function confirmWorkspaceDestruction(workspaceId: string, confirm: string) {
  return http.post<ApiResponse<WorkspaceDestructionResponse>>(
    `/workspaces/${workspaceId}/confirm-destroy`, null, { params: { confirm } }
  )
}

/**
 * POST /v1/workspaces/{id}/cancel-destroy
 * 撤销销毁
 */
export function cancelWorkspaceDestruction(workspaceId: string) {
  return http.post<ApiResponse<WorkspaceDestructionResponse>>(
    `/workspaces/${workspaceId}/cancel-destroy`
  )
}

/**
 * GET /v1/workspaces/{id}/destruction-status
 * 查询销毁状态
 */
export function fetchDestructionStatus(workspaceId: string) {
  return http.get<ApiResponse<WorkspaceDestructionResponse | null>>(
    `/workspaces/${workspaceId}/destruction-status`
  )
}

// ── Retention Policy API ────────────────────────────────────────────────────

/**
 * GET /v1/workspaces/{id}/retention
 * 查询保留策略
 */
export function fetchRetentionPolicy(workspaceId: string) {
  return http.get<ApiResponse<RetentionPolicyResponse>>(
    `/workspaces/${workspaceId}/retention`
  )
}

/**
 * PUT /v1/workspaces/{id}/retention
 * 更新保留策略
 */
export function updateRetentionPolicy(workspaceId: string, policy: RetentionPolicyRequest) {
  return http.put<ApiResponse<RetentionPolicyResponse>>(
    `/workspaces/${workspaceId}/retention`, policy
  )
}

// Aliases for backward compatibility
export const getRetentionPolicy = fetchRetentionPolicy
export const getDestructionStatus = fetchDestructionStatus

/**
 * GET /v1/workspaces/{id}/export
 * 请求工作区全量导出
 */
export function requestWorkspaceExport(workspaceId: string) {
  return http.get<ApiResponse<ExportRequestResponse>>(
    `/workspaces/${workspaceId}/export`
  )
}

