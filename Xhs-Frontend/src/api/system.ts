import http from './http'
import type { ApiResponse } from '@/types/api'

export interface SystemInfo {
  architectureVersion: string
  defaultModelProvider: string
  openaiEnabled: boolean
  ragEnabled: boolean
}

export function fetchSystemInfo() {
  return http.get<ApiResponse<SystemInfo>>('/system/info')
}
