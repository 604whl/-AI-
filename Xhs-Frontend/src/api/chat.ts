import http from './http'
import type {
  ApiResponse,
  PaginatedData,
  PersonaType,
} from '@/types/api'
import type {
  ChatMessageItem,
  ChatMessageResponse,
  ChatSession,
} from '@/types/chat'

export interface CreateChatSessionRequest {
  persona?: PersonaType
  linkedTaskId?: string
  title?: string
}

export interface SendChatMessageRequest {
  content: string
  attachments?: {
    title?: string
    body?: string
    coverImageUrl?: string
  }
}

export function createChatSession(payload: CreateChatSessionRequest) {
  return http.post<ApiResponse<ChatSession>>('/chat/sessions', payload)
}

export function fetchChatSessions(params?: { page?: number; size?: number }) {
  return http.get<ApiResponse<PaginatedData<ChatSession>>>('/chat/sessions', { params })
}

export function sendChatMessage(sessionId: string, payload: SendChatMessageRequest) {
  return http.post<ApiResponse<ChatMessageResponse>>(`/chat/sessions/${sessionId}/messages`, payload, {
    timeout: 180000,
  })
}

export function fetchChatMessages(sessionId: string, params?: { page?: number; size?: number }) {
  return http.get<ApiResponse<PaginatedData<ChatMessageItem>>>(`/chat/sessions/${sessionId}/messages`, { params })
}

export function archiveChatSession(sessionId: string) {
  return http.delete<ApiResponse<null>>(`/chat/sessions/${sessionId}`)
}
