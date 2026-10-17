import { generateTitles, generateTitlesByAnalysisId } from '@/api/analysis'
import type { GeneratedTitle, TitleGenerateRequest } from '@/types/api'
import type { ScoredTitle, TitleGenerateParams, TitleGenerateResult, TitleTypeTag } from '@/types/title'
import { extractHighlightSegments } from '@/utils/titleHighlight'

function inferTypeTag(text: string): TitleTypeTag {
  if (/信息差|没人告诉|真相|秘密/.test(text)) return 'info_gap'
  if (/上岸|Offer|逆袭|从0/.test(text)) return 'comeback'
  if (/卷|破防|HR|却|但/.test(text)) return 'conflict'
  return 'anxiety'
}

function mapApiTitle(item: GeneratedTitle): ScoredTitle {
  return {
    title: item.text,
    estimatedCtr: item.estimatedCtr ?? 'medium',
    typeTag: inferTypeTag(item.text),
    highlights: item.highlights.length ? item.highlights : extractHighlightSegments(item.text),
  }
}

export async function fetchScoredTitles(
  params: TitleGenerateParams,
  analysisId?: string,
): Promise<TitleGenerateResult> {
  const payload: TitleGenerateRequest = {
    goal: params.goal as TitleGenerateRequest['goal'],
    count: params.count,
    persona: params.persona as TitleGenerateRequest['persona'],
    title: params.title,
    body: params.body,
    analysisId,
  }

  const res = analysisId
    ? await generateTitlesByAnalysisId(analysisId, payload)
    : await generateTitles(payload)

  const data = res.data.data
  return {
    titles: data.titles.map(mapApiTitle),
    promptVersion: data.promptVersion,
  }
}
