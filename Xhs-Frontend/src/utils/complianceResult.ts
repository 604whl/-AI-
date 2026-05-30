import type { ComplianceWarning } from '@/types/api'
import type { SensitiveRiskLevel, SensitiveWordResult } from '@/types/workbench'

function calcRiskLevel(count: number): SensitiveRiskLevel {
  if (count >= 3) return 'high'
  if (count >= 1) return 'medium'
  return 'low'
}

export function complianceWarningsToResult(warnings: ComplianceWarning[]): SensitiveWordResult {
  const words = warnings.map((w) => w.matchedText).filter(Boolean)
  return {
    words,
    riskLevel: calcRiskLevel(words.length),
    warnings,
  }
}
