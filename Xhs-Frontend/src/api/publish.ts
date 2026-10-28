import http from './http'
import type { ApiResponse, PaginatedData } from '@/types/api'
import type {
  PublishScheduleStatus,
  PublishTaskStatus,
} from '@/types/stage3'

// ── Types ────────────────────────────────────────────────────────────────────

export type { PublishScheduleStatus, PublishTaskStatus }

/** 发布排期（POST/GET /publish-schedules） */
export interface PublishSchedule {
  id: string
  assetId: string
  title?: string
  platformCode: string
  accountId?: string
  scheduledAt: string
  timezone?: string
  status: PublishScheduleStatus
  /** 创建排期时回传的资产当前状态（如 approved） */
  assetStatus?: string
  publishTaskId?: string
  createdAt?: string
  updatedAt?: string
}

export interface PublishScheduleCreateRequest {
  assetId: string
  platformCode: string
  accountId?: string
  scheduledAt: string
  timezone?: string
}

export interface PublishScheduleCreateResponse {
  id: string
  status: PublishScheduleStatus
  assetStatus?: string
}

export interface PublishScheduleCancelResponse {
  id: string
  status: PublishScheduleStatus
}

export interface PublishScheduleRetryResponse {
  id: string
  publishTaskId?: string
  status: PublishScheduleStatus
}

/** 发布回执 */
export interface PublishReceipt {
  id: string
  publishTaskId?: string
  assetId?: string
  externalContentId?: string
  sourceUrl?: string
  publishedAt?: string
  publishedBy?: number
  verified?: boolean
  createdAt?: string
}

export interface PublishReceiptCreateRequest {
  publishTaskId: string
  externalContentId?: string
  sourceUrl?: string
  publishedAt?: string
}

/** 发布任务 */
export interface PublishTask {
  id: string
  taskId?: string
  assetId?: string
  platformCode?: string
  accountId?: string
  status: PublishTaskStatus
  attempt?: number
  errorCode?: string
  errorMessage?: string
  retryable?: boolean
  scheduledAt?: string
  idempotencyKey?: string
  receipt?: PublishReceipt
  createdAt?: string
  updatedAt?: string
}

export interface PublishTaskCreateRequest {
  assetId: string
  platformCode: string
  accountId?: string
  scheduledAt?: string
}

export interface PublishTaskCreateResponse {
  taskId: string
  status: PublishTaskStatus
  idempotencyKey?: string
}

export interface PublishTaskCancelResponse {
  id: string
  status: PublishTaskStatus
}

/** 发布日历 */
export interface PublishCalendarSlot {
  scheduleId: string
  platformCode?: string
  status?: PublishScheduleStatus
}

export interface PublishCalendarDay {
  date: string
  slots: PublishCalendarSlot[]
}

export interface PublishCalendar {
  days: PublishCalendarDay[]
}

// ── 排期 ────────────────────────────────────────────────────────────────────

export function fetchPublishSchedules(params?: {
  from?: string
  to?: string
  platform?: string
  status?: PublishScheduleStatus
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<PaginatedData<PublishSchedule>>>('/publish-schedules', { params })
}

export function createPublishSchedule(payload: PublishScheduleCreateRequest) {
  return http.post<ApiResponse<PublishScheduleCreateResponse>>('/publish-schedules', payload)
}

export function fetchPublishCalendar(params?: { from?: string; to?: string }) {
  return http.get<ApiResponse<PublishCalendar>>('/publish-schedules/calendar', { params })
}

export function cancelPublishSchedule(id: string) {
  return http.post<ApiResponse<PublishScheduleCancelResponse>>(`/publish-schedules/${id}/cancel`, {})
}

export function retryPublishSchedule(id: string) {
  return http.post<ApiResponse<PublishScheduleRetryResponse>>(`/publish-schedules/${id}/retry`, {})
}

// ── 发布任务 ────────────────────────────────────────────────────────────────

export function createPublishTask(payload: PublishTaskCreateRequest) {
  return http.post<ApiResponse<PublishTaskCreateResponse>>('/publish-tasks', payload)
}

export function fetchPublishTask(id: string) {
  return http.get<ApiResponse<PublishTask>>(`/publish-tasks/${id}`)
}

export function cancelPublishTask(id: string) {
  return http.post<ApiResponse<PublishTaskCancelResponse>>(`/publish-tasks/${id}/cancel`, {})
}

// ── 回执 ────────────────────────────────────────────────────────────────────

export function createPublishReceipt(payload: PublishReceiptCreateRequest) {
  return http.post<ApiResponse<{ id: string; verified: boolean }>>('/publish-receipts', payload)
}

export function fetchAssetReceipts(assetId: string) {
  return http.get<ApiResponse<PublishReceipt[]>>(`/assets/${assetId}/receipts`)
}
