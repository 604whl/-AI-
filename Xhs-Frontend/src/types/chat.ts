// Chat-related types mirroring backend DTOs

export interface ChatSession {
  sessionId: string
  title?: string
  persona?: string
  linkedTaskId?: string
  status?: string
  createdAt?: string
  updatedAt?: string
}

export interface ChatMessageItem {
  id: string | number
  role: string
  content?: string
  metadata?: Record<string, unknown>
  createdAt?: string
}

export interface ChatMessageResponse {
  messageId: string | number
  role: string
  content?: string
  cards?: ChatAgentCard[]
  toolTraces?: ChatToolTrace[]
}

export interface ChatToolTrace {
  tool: string
  success: boolean
  latencyMs: number
  error?: string
}

export interface ChatAgentCard {
  type: string
  taskId?: string
  payload?: Record<string, unknown>
}

export interface Issue {
  severity?: string
  category?: string
  description: string
  suggestion: string
}

export interface AnalysisReportSummary {
  contentType?: string
  scores?: Record<string, { score: number; reason?: string }>
  topIssues?: Issue[]
}

export interface ComplianceCardPayload {
  warnings?: Array<{ rule: string; matchedText: string; suggestion: string }>
}

export interface TitleListCardPayload {
  titles?: TitleItem[]
}

export interface TitleItem {
  text: string
  highlights?: string[]
  estimatedCtr?: string
}
