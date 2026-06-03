import { fetchAnalysis } from '@/api/analysis'
import type { AnalysisDetail } from '@/types/api'
import { consumeFetchSse } from '@/utils/sse'
import { getAccessToken } from '@/utils/token'

const baseURL = import.meta.env.VITE_API_BASE || '/api/v1'

export interface AnalysisProgressPayload {
  taskId: string
  status: string
  phase: string
  message: string
  processingMs?: number
  failureCode?: number
  failureReason?: string
}

export interface AnalysisStreamHandlers {
  onProgress?: (event: AnalysisProgressPayload) => void
  onDone?: (event: AnalysisProgressPayload) => void
  onError?: (message: string) => void
}

/**
 * Subscribe to analysis task SSE. Falls back to polling if the stream fails to connect.
 */
export function subscribeAnalysisStream(
  analysisId: string,
  handlers: AnalysisStreamHandlers,
): () => void {
  const controller = new AbortController()
  const token = getAccessToken()
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let stopped = false

  const run = async () => {
    try {
      const response = await fetch(`${baseURL}/analysis/${analysisId}/stream`, {
        method: 'GET',
        headers,
        signal: controller.signal,
      })
      await consumeFetchSse(response, (event, data) => {
        if (stopped) return
        try {
          const parsed = JSON.parse(data) as AnalysisProgressPayload
          if (event === 'done' || parsed.status === 'completed' || parsed.status === 'failed') {
            handlers.onDone?.(parsed)
          } else {
            handlers.onProgress?.(parsed)
          }
        } catch {
          handlers.onError?.('invalid_sse_payload')
        }
      })
    } catch (err) {
      if (stopped || controller.signal.aborted) return
      await fallbackPoll(analysisId, handlers, () => stopped)
    }
  }

  void run()

  return () => {
    stopped = true
    controller.abort()
  }
}

async function fallbackPoll(
  analysisId: string,
  handlers: AnalysisStreamHandlers,
  isStopped: () => boolean,
) {
  let count = 0
  while (!isStopped() && count < 30) {
    try {
      const res = await fetchAnalysis(analysisId)
      const data = res.data.data as AnalysisDetail
      if (data.status === 'completed' || data.status === 'failed') {
        handlers.onDone?.({
          taskId: analysisId,
          status: data.status,
          phase: data.status === 'completed' ? 'finished' : 'failed',
          message: data.status,
        })
        return
      }
      handlers.onProgress?.({
        taskId: analysisId,
        status: data.status,
        phase: 'processing',
        message: data.status,
      })
    } catch (e) {
      handlers.onError?.(e instanceof Error ? e.message : 'poll_failed')
      return
    }
    count++
    await new Promise((r) => setTimeout(r, 2000))
  }
  if (!isStopped()) {
    handlers.onError?.('analysis_timeout')
  }
}
