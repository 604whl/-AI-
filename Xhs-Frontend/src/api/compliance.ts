import http from './http'
import type { ApiResponse, ComplianceWarning } from '@/types/api'
import { scanComplianceLocal } from '@/utils/complianceScan'

export interface ComplianceScanResponse {
  warnings: ComplianceWarning[]
}

/** 合规扫描：优先走后端 ComplianceChecker，失败时回退本地同规则实现 */
export async function scanCompliance(title: string, body: string): Promise<ComplianceWarning[]> {
  try {
    const res = await http.post<ApiResponse<ComplianceScanResponse>>('/compliance/scan', {
      title,
      body,
    })
    return res.data.data.warnings ?? []
  } catch {
    return scanComplianceLocal(title, body)
  }
}
