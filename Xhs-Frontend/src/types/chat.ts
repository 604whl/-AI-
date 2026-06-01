import type { ComplianceWarning, GeneratedTitle, PersonaType, ScoreDimension } from '@/types/api'

export interface ChatSession {
  sessionId: string
  title?: string
  persona?: PersonaType
  linkedTaskId?: string
  status?: string
  createdAt?: string
  updatedAt?: string
}

export interface ChatToolTrace {
  tool: string
  success: boolean
  latencyMs: number
  error?: string
}

export type ChatCardType =
  | 'analysis_report'
  | 'title_list'
  | 'compliance_warnings'
  | 'cover_analysis'
  | 'optimize_draft'
  | 'web_search'
  | 'fetched_url'
  | 'hot_topics'
  | 'industry_calendar'

export interface ChatAgentCard {
  type: ChatCardType
  taskId?: string
  payload?: Record<string, unknown>
}

export interface ChatMessageResponse {
  messageId: number
  role: string
  content: string
  cards: ChatAgentCard[]
  toolTraces: ChatToolTrace[]
}

export interface ChatMessageItem {
  id: number
  role: string
  content?: string
  metadata?: Record<string, unknown>
  createdAt?: string
}

export interface ChatAttachments {
  title?: string
  body?: string
  coverImageUrl?: string
}

export interface AnalysisReportSummary {
  contentType?: string
  scores?: {
    ctr?: ScoreDimension
    emotion?: ScoreDimension
    collect?: ScoreDimension
    conversion?: ScoreDimension
    viral?: ScoreDimension
  }
  topIssues?: Array<{ severity?: string; category?: string; description?: string; suggestion?: string }>
  topOptimizations?: unknown[]
  taskId?: string
  status?: string
  title?: string
}

export interface TitleListCardPayload {
  goal?: string
  titles?: GeneratedTitle[]
}

export interface ComplianceCardPayload {
  warnings?: ComplianceWarning[]
}
