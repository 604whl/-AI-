import type { ChatMessageResponse } from '@/types/chat'
import type { SendChatMessageRequest } from '@/api/chat'
import { authFetch } from '@/api/authFetch'
import { consumeFetchSse } from '@/utils/sse'

const baseURL = import.meta.env.VITE_API_BASE || '/api/v1'

export interface ChatStreamHandlers {
  onStepStart?: (step: number, maxSteps: number) => void
  onToolStart?: (tool: string, step: number) => void
  onToolEnd?: (tool: string, success: boolean, latencyMs: number, error?: string) => void
  onDelta?: (chunk: string) => void
  onDone?: (message: ChatMessageResponse) => void
  onError?: (code: number, message: string) => void
}

export async function streamChatMessage(
  sessionId: string,
  payload: SendChatMessageRequest,
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }

  const response = await authFetch(`${baseURL}/chat/sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
    signal,
  })

  await consumeFetchSse(response, (event, data) => {
    let parsed: Record<string, unknown>
    try {
      parsed = JSON.parse(data) as Record<string, unknown>
    } catch {
      return
    }

    switch (event) {
      case 'step_start':
        handlers.onStepStart?.(Number(parsed.step), Number(parsed.maxSteps))
        break
      case 'tool_start':
        handlers.onToolStart?.(String(parsed.tool), Number(parsed.step))
        break
      case 'tool_end':
        handlers.onToolEnd?.(
          String(parsed.tool),
          Boolean(parsed.success),
          Number(parsed.latencyMs ?? 0),
          parsed.error ? String(parsed.error) : undefined,
        )
        break
      case 'delta':
        handlers.onDelta?.(String(parsed.content ?? ''))
        break
      case 'done':
        handlers.onDone?.(parsed as unknown as ChatMessageResponse)
        break
      case 'error': {
        const code = Number(parsed.code ?? 50000)
        const message = String(parsed.message ?? 'error')
        if (handlers.onError) {
          handlers.onError(code, message)
        } else {
          throw new Error(message)
        }
        break
      }
      default:
        break
    }
  })
}
