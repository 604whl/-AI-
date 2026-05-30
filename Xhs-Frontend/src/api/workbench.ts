import { scanCompliance } from '@/api/compliance'
import { complianceWarningsToResult } from '@/utils/complianceResult'
import type { SensitiveWordResult } from '@/types/workbench'

/** 实时合规扫描（规则与后端 ComplianceChecker 一致） */
export async function detectSensitiveWords(
  title: string,
  body: string,
): Promise<SensitiveWordResult> {
  const warnings = await scanCompliance(title, body)
  return complianceWarningsToResult(warnings)
}
