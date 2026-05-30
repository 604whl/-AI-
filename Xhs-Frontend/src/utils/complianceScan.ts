import type { ComplianceWarning } from '@/types/api'

/**
 * 与后端 ComplianceChecker 规则保持一致（离线/接口失败时回退）。
 * 修改规则时请同步更新 ComplianceChecker.java。
 */
const RULES: Array<{ id: string; pattern: RegExp; suggestion: string }> = [
  {
    id: 'absolute_promise',
    pattern: /保\s*[oO][fF]{2}[eE][rR]|100%\s*上岸| garantee|保证录取|必上岸/,
    suggestion: '避免绝对化承诺，改为「提升上岸概率」等可验证表述',
  },
  {
    id: 'fake_authority',
    pattern: /官方认证|小红书官方|教育部认证(?!号)/,
    suggestion: '勿冒充官方；若合作须标注广告/合作',
  },
  {
    id: 'discrimination',
    pattern: /水硕|野鸡大学|某国留学生都不行/,
    suggestion: '避免院校/国籍歧视性表述',
  },
  {
    id: 'excessive_fear',
    pattern: /再不.*就完了|签证马上取消|政策已崩/,
    suggestion: '避免制造不存在的政策恐慌，引用政策须注明来源',
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
