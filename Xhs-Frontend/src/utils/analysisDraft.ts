import type { AnalysisScenario, CompetitorContext, PersonaType, PublishedMetrics } from '@/types/api'
import { createEmptyCompetitorContext, createEmptyPublishedMetrics } from '@/utils/analysisPayload'

const DRAFT_KEY = 'xhs_analysis_draft'

export interface AnalysisDraft {
  scenario: AnalysisScenario
  persona: PersonaType
  title: string
  body: string
  coverImageUrl?: string
  publishedMetrics?: PublishedMetrics
  competitorContext?: CompetitorContext
}

export function createDefaultDraft(): AnalysisDraft {
  return {
    scenario: 'draft',
    persona: 'agency',
    title: '',
    body: '',
    publishedMetrics: createEmptyPublishedMetrics(),
    competitorContext: createEmptyCompetitorContext(),
  }
}

export function saveAnalysisDraft(draft: AnalysisDraft) {
  sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft))
}

export function loadAnalysisDraft(): AnalysisDraft | null {
  const raw = sessionStorage.getItem(DRAFT_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AnalysisDraft
  } catch {
    return null
  }
}

export function clearAnalysisDraft() {
  sessionStorage.removeItem(DRAFT_KEY)
}
