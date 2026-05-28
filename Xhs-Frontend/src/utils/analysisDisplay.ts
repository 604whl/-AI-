import type { AnalysisReport } from '@/types/api'

export function averageReportScore(report?: AnalysisReport | Pick<AnalysisReport, 'scores'>): number | null {
  if (!report?.scores) return null
  const dims = Object.values(report.scores)
  if (!dims.length) return null
  return Math.round(dims.reduce((sum, dim) => sum + dim.score, 0) / dims.length)
}

export function formatRelativeTime(iso: string, locale: string): string {
  const date = new Date(iso)
  const diffMs = Date.now() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })

  if (diffMin < 1) return rtf.format(0, 'minute')
  if (diffMin < 60) return rtf.format(-diffMin, 'minute')

  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return rtf.format(-diffHour, 'hour')

  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 7) return rtf.format(-diffDay, 'day')

  return date.toLocaleDateString(locale)
}

export function isToday(iso: string): boolean {
  const date = new Date(iso)
  const now = new Date()
  return (
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  )
}
