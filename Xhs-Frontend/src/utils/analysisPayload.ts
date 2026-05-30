import type {
  AnalysisCreateRequest,
  AnalysisDetail,
  AnalysisScenario,
  CompetitorContext,
  PersonaType,
  PublishedMetrics,
} from '@/types/api'

export interface AnalysisFormState {
  scenario: AnalysisScenario
  persona: PersonaType
  title: string
  body: string
  coverImageUrl?: string
  publishedMetrics: PublishedMetrics
  competitorContext: CompetitorContext
}

export function createEmptyPublishedMetrics(): PublishedMetrics {
  return {}
}

export function createEmptyCompetitorContext(): CompetitorContext {
  return {}
}

function hasPublishedMetrics(metrics: PublishedMetrics): boolean {
  return Boolean(
    metrics.noteUrl?.trim()
      || metrics.impressions != null
      || metrics.likes != null
      || metrics.collects != null
      || metrics.comments != null
      || metrics.dmInquiries != null
      || metrics.publishedAt,
  )
}

function hasCompetitorContext(context: CompetitorContext): boolean {
  return Boolean(
    context.accountName?.trim() || context.noteUrl?.trim() || context.learningFocus?.trim(),
  )
}

function trimPublishedMetrics(metrics: PublishedMetrics): PublishedMetrics {
  const result: PublishedMetrics = {}
  const noteUrl = metrics.noteUrl?.trim()
  if (noteUrl) result.noteUrl = noteUrl
  if (metrics.impressions != null) result.impressions = metrics.impressions
  if (metrics.likes != null) result.likes = metrics.likes
  if (metrics.collects != null) result.collects = metrics.collects
  if (metrics.comments != null) result.comments = metrics.comments
  if (metrics.dmInquiries != null) result.dmInquiries = metrics.dmInquiries
  if (metrics.publishedAt) result.publishedAt = metrics.publishedAt
  return result
}

function trimCompetitorContext(context: CompetitorContext): CompetitorContext {
  const result: CompetitorContext = {}
  const accountName = context.accountName?.trim()
  const noteUrl = context.noteUrl?.trim()
  const learningFocus = context.learningFocus?.trim()
  if (accountName) result.accountName = accountName
  if (noteUrl) result.noteUrl = noteUrl
  if (learningFocus) result.learningFocus = learningFocus
  return result
}

export function buildAnalysisCreatePayload(form: AnalysisFormState): AnalysisCreateRequest {
  const payload: AnalysisCreateRequest = {
    scenario: form.scenario,
    persona: form.persona,
    title: form.title.trim() || undefined,
    body: form.body.trim() || undefined,
    coverImageUrl: form.coverImageUrl?.trim() || undefined,
  }
  if (form.scenario === 'published' && hasPublishedMetrics(form.publishedMetrics)) {
    payload.publishedMetrics = trimPublishedMetrics(form.publishedMetrics)
  }
  if (form.scenario === 'competitor' && hasCompetitorContext(form.competitorContext)) {
    payload.competitorContext = trimCompetitorContext(form.competitorContext)
  }
  return payload
}

export function applyAnalysisDetailToForm(form: AnalysisFormState, detail: AnalysisDetail) {
  form.scenario = detail.scenario
  if (detail.persona) form.persona = detail.persona
  form.title = detail.title ?? ''
  form.body = detail.body ?? ''
  form.coverImageUrl = detail.coverImageUrl
  form.publishedMetrics = {
    ...createEmptyPublishedMetrics(),
    ...detail.publishedMetrics,
  }
  form.competitorContext = {
    ...createEmptyCompetitorContext(),
    ...detail.competitorContext,
  }
}

export function buildAnalysisCreatePayloadFromDetail(detail: AnalysisDetail): AnalysisCreateRequest {
  return buildAnalysisCreatePayload({
    scenario: detail.scenario,
    persona: detail.persona ?? 'agency',
    title: detail.title ?? '',
    body: detail.body ?? '',
    coverImageUrl: detail.coverImageUrl,
    publishedMetrics: detail.publishedMetrics ?? createEmptyPublishedMetrics(),
    competitorContext: detail.competitorContext ?? createEmptyCompetitorContext(),
  })
}
