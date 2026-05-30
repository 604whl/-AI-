import http from './http'
import type { ApiResponse } from '@/types/api'

export interface CoverUploadResult {
  coverImageUrl: string
  objectKey: string
}

export function uploadCoverImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<ApiResponse<CoverUploadResult>>('/files/cover', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 从 coverImageUrl 或 objectKey 提取存储 key */
export function extractCoverObjectKey(coverImageUrl: string): string {
  const marker = '/files/cover/'
  const idx = coverImageUrl.indexOf(marker)
  if (idx >= 0) {
    return coverImageUrl.slice(idx + marker.length)
  }
  return coverImageUrl.replace(/^\/+/, '')
}

export async function fetchCoverBlob(coverImageUrl: string): Promise<Blob> {
  const objectKey = extractCoverObjectKey(coverImageUrl)
  const res = await http.get<Blob>(`/files/cover/${objectKey}`, { responseType: 'blob' })
  return res.data
}

export async function resolveCoverPreviewUrl(coverImageUrl: string): Promise<string> {
  const blob = await fetchCoverBlob(coverImageUrl)
  return URL.createObjectURL(blob)
}
