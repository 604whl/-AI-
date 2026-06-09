import type { ComplianceWarning } from '@/types/api'

/**
 * 与后端 ComplianceChecker 规则保持一致（离线/接口失败时回退）。
 * 修改规则时请同步更新 ComplianceChecker.java。
 */
const RULES: Array<{ id: string; pattern: RegExp; suggestion: string }> = [
  {
    id: 'absolute_promise',
    pattern: /100%\s*有效| garantee|国家级(?!产品)|(?:最好|第一)(?!手)|百分百|绝对有效|必上岸|保证录取/,
    suggestion: '避免绝对化承诺与广告法禁用表述，改为可验证的客观描述',
  },
  {
    id: 'fake_authority',
    pattern: /官方认证|小红书官方|教育部认证(?!号)/,
    suggestion: '勿冒充官方；若合作须标注广告/合作',
  },
  {
    id: 'discrimination',
    pattern: /某省人都不行|某国人都不行|学历低的人都不行/,
    suggestion: '避免地域、国籍、群体歧视性表述',
  },
  {
    id: 'excessive_fear',
    pattern: /再不.*就完了|马上关停|政策已崩|立刻下架/,
    suggestion: '避免制造不实恐慌，引用政策须注明来源',
  },
]

export function scanComplianceLocal(title: string, body: string): ComplianceWarning[] {
  const text = `${title}\n${body}`.trim()
  if (!text) return []

  const warnings: ComplianceWarning[] = []
  for (const rule of RULES) {
    const match = rule.pattern.exec(text)
    if (match) {
      warnings.push({
        rule: rule.id,
        matchedText: match[0].trim(),
        suggestion: rule.suggestion,
      })
    }
  }
  return warnings
}
