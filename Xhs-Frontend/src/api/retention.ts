/**
 * Data Retention & Export API
 * P0-9: Account Deletion, Workspace Destruction, Personal Data Export
 */
import { get, post, del, put } from './http'
import type { ApiResponse } from './http'

// ── Types ────────────────────────────────────────────────────────────────────

export interface DeletionStatusResponse {
  deletionStatus: 'active' | 'pending_deletion' | 'cancelled'
  scheduledDeletionAt: string | null
  daysRemaining: number | null
  deletionConfirmedAt: string | null
}

export interface WorkspaceDestructionResponse {
  id: string
  workspaceId: string
  status: 'pending' | 'confirmed' | 'cancelled' | 'executed'
  executeAt: string | null
  daysRemaining: number | null
  confirmedAt: string | null
  executedAt: string | null
  createdAt: string
}

export interface RetentionPolicyResponse {
  workspaceId: string
  assetRetentionDays: number
  logRetentionDays: number
  mediaRetentionDays: number
  chatSessionRetentionDays: number
  updatedAt: string | null
  updatedBy: number | null
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
  downloadUrl: string | null
  fileSize: number | null
  expiresAt: string | null
  errorMessage: string | null
  createdAt: string
  finishedAt: string | null
}

// ── Account Deletion API ─────────────────────────────────────────────────────

/**
 * DELETE /v1/auth/me?confirm=DELETE
 * 申请账号注销（30 天软删）
 */
export async function requestAccountDeletion(confirm: string): Promise<ApiResponse<DeletionStatusResponse>> {
  return del('/v1/auth/me?confirm=' + encodeURIComponent(confirm))
}

/**
 * POST /v1/auth/me/cancel-deletion
 * 撤销账号注销
 */
export async function cancelAccountDeletion(): Promise<ApiResponse<DeletionStatusResponse>> {
  return post('/v1/auth/me/cancel-deletion')
}

/**
 * GET /v1/auth/me/deletion-status
 * 查询注销状态
 */
export async function getDeletionStatus(): Promise<ApiResponse<DeletionStatusResponse>> {
  return get('/v1/auth/me/deletion-status')
}

// ── Data Export API ──────────────────────────────────────────────────────────

/**
 * GET /v1/auth/me/export
 * 请求个人数据导出
 */
export async function requestPersonalExport(): Promise<ApiResponse<ExportRequestResponse>> {
  return get('/v1/auth/me/export')
}

/**
 * GET /v1/auth/me/exports
 * 查询导出历史
 */
export async function listMyExports(): Promise<ApiResponse<ExportRequestResponse[]>> {
  return get('/v1/auth/me/exports')
}

/**
 * GET /v1/auth/me/exports/{exportId}
 * 查询导出状态
 */
export async function getExportStatus(exportId: string): Promise<ApiResponse<ExportRequestResponse>> {
  return get('/v1/auth/me/exports/' + exportId)
}

// ── Workspace Destruction API ───────────────────────────────────────────────

/**
 * POST /v1/workspaces/{id}/destroy?confirm=DELETE
 * 发起工作区销毁（7 天宽限）
 */
export async function requestWorkspaceDestruction(
  workspaceId: string,
  confirm: string
): Promise<ApiResponse<WorkspaceDestructionResponse>> {
  return post(`/v1/workspaces/${workspaceId}/destroy?confirm=` + encodeURIComponent(confirm))
}

/**
 * POST /v1/workspaces/{id}/confirm-destroy?confirm=DELETE
 * 二次确认销毁
 */
export async function confirmWorkspaceDestruction(
  workspaceId: string,
  confirm: string
): Promise<ApiResponse<WorkspaceDestructionResponse>> {
  return post(`/v1/workspaces/${workspaceId}/confirm-destroy?confirm=` + encodeURIComponent(confirm))
}

/**
 * POST /v1/workspaces/{id}/cancel-destroy
 * 撤销销毁
 */
export async function cancelWorkspaceDestruction(
  workspaceId: string
): Promise<ApiResponse<WorkspaceDestructionResponse>> {
  return post(`/v1/workspaces/${workspaceId}/cancel-destroy`)
}

/**
 * GET /v1/workspaces/{id}/destruction-status
 * 查询销毁状态
 */
export async function getDestructionStatus(
  workspaceId: string
): Promise<ApiResponse<WorkspaceDestructionResponse | null>> {
  return get(`/v1/workspaces/${workspaceId}/destruction-status`)
}

// ── Retention Policy API ────────────────────────────────────────────────────

/**
 * GET /v1/workspaces/{id}/retention
 * 查询保留策略
 */
export async function getRetentionPolicy(
  workspaceId: string
): Promise<ApiResponse<RetentionPolicyResponse>> {
  return get(`/v1/workspaces/${workspaceId}/retention`)
}

/**
 * PUT /v1/workspaces/{id}/retention
 * 更新保留策略
 */
export async function updateRetentionPolicy(
  workspaceId: string,
  policy: RetentionPolicyRequest
): Promise<ApiResponse<RetentionPolicyResponse>> {
  return put(`/v1/workspaces/${workspaceId}/retention`, policy)
}

/**
 * GET /v1/workspaces/{id}/export
 * 请求工作区全量导出
 */
export async function requestWorkspaceExport(
  workspaceId: string
): Promise<ApiResponse<ExportRequestResponse>> {
  return get(`/v1/workspaces/${workspaceId}/export`)
}
