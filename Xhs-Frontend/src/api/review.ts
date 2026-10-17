import http from './http'
import type { ApiResponse, PaginatedData } from '@/types/api'

// ── Types ────────────────────────────────────────────────────────────────────

export type ReviewStatus = 'pending' | 'in_review' | 'approved' | 'rejected' | 'cancelled'

export interface ReviewQueueItem {
  id: string
  assetId: string
  versionNo: number
  submittedBy: number
  submitterNickname: string
  status: ReviewStatus
  isHighRisk: boolean
  riskScore: number
  riskTags: string[]
  createdAt: string
  decidedAt: string | null
}

export interface ReviewAssetSnapshot {
  id: string
  title: string
  body: string
  persona: string
  platform: string
  accountId: string
  currentVersion: number
}

export interface ReviewCommentItem {
  id: string
  comment: string
  highlightedText: string | null
  commentType: 'comment' | 'approve' | 'reject'
  createdBy: number
  creatorNickname: string
  createdAt: string
}

export interface ReviewTaskDetail {
  id: string
  workspaceId: string
  assetId: string
  versionNo: number
  submittedBy: number
  submitterNickname: string
  reviewerUserId: number | null
  reviewerNickname: string | null
  status: ReviewStatus
  isHighRisk: boolean
  riskScore: number
  riskTags: string[]
  comment: string | null
  decidedAt: string | null
  createdAt: string
  updatedAt: string
  asset: ReviewAssetSnapshot | null
  comments: ReviewCommentItem[]
}

export interface ReviewAssignRequest {
  reviewerId: number
}

export interface ReviewDecisionRequest {
  comment?: string
}

export interface ReviewCommentRequest {
  comment: string
  highlightedText?: string
}

// ── Reviewer 端 ─────────────────────────────────────────────────────────────

/**
 * Reviewer 待办队列。
 * GET /v1/reviews/queue?status=pending
 */
export function fetchReviewQueue(params?: {
  status?: ReviewStatus
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<PaginatedData<ReviewQueueItem>>>('/reviews/queue', { params })
}

// ── Editor 端 ───────────────────────────────────────────────────────────────

/**
 * Editor 自己的提交记录。
 * GET /v1/reviews/submissions
 */
export function fetchMySubmissions(params?: {
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<PaginatedData<ReviewQueueItem>>>('/reviews/submissions', { params })
}

// ── 任务详情 ────────────────────────────────────────────────────────────────

/**
 * 获取审核任务详情（含资产快照 + 批注列表）。
 * GET /v1/reviews/{taskId}
 */
export function fetchReviewTask(taskId: string) {
  return http.get<ApiResponse<ReviewTaskDetail>>(`/reviews/${taskId}`)
}

/**
 * 获取审核任务批注列表。
 * GET /v1/reviews/{taskId}/comments
 */
export function fetchReviewComments(taskId: string) {
  return http.get<ApiResponse<ReviewCommentItem[]>>(`/reviews/${taskId}/comments`)
}

// ── 操作 ────────────────────────────────────────────────────────────────────

/**
 * ADMIN 指派审核人。
 * POST /v1/reviews/{taskId}/assign
 */
export function assignReviewer(taskId: string, payload: ReviewAssignRequest) {
  return http.post<ApiResponse<ReviewTaskDetail>>(`/reviews/${taskId}/assign`, payload)
}

/**
 * 审核通过。
 * POST /v1/reviews/{taskId}/approve
 */
export function approveReview(taskId: string, payload?: ReviewDecisionRequest) {
  return http.post<ApiResponse<ReviewTaskDetail>>(`/reviews/${taskId}/approve`, payload ?? {})
}

/**
 * 审核驳回（comment 必填）。
 * POST /v1/reviews/{taskId}/reject
 */
export function rejectReview(taskId: string, payload: ReviewDecisionRequest) {
  return http.post<ApiResponse<ReviewTaskDetail>>(`/reviews/${taskId}/reject`, payload)
}

/**
 * 撤回审核提交。
 * POST /v1/reviews/{taskId}/cancel
 */
export function cancelReview(taskId: string) {
  return http.post<ApiResponse<ReviewTaskDetail>>(`/reviews/${taskId}/cancel`, {})
}

/**
 * 添加批注。
 * POST /v1/reviews/{taskId}/comments
 */
export function addReviewComment(taskId: string, payload: ReviewCommentRequest) {
  return http.post<ApiResponse<ReviewCommentItem>>(`/reviews/${taskId}/comments`, payload)
}
