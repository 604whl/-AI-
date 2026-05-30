import { generateTitles, generateTitlesByAnalysisId } from '@/api/analysis'
import type { GeneratedTitle, TitleGenerateRequest } from '@/types/api'
import type { ScoredTitle, TitleGenerateParams, TitleGenerateResult, TitleTypeTag } from '@/types/title'
import { extractHighlightSegments } from '@/utils/titleHighlight'

/** 开发时可设 VITE_USE_TITLE_MOCK=true 走本地 Mock */
const USE_MOCK = import.meta.env.VITE_USE_TITLE_MOCK === 'true'

const TYPE_TAGS: TitleTypeTag[] = ['anxiety', 'info_gap', 'comeback', 'conflict']

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function inferTypeTag(text: string, index: number): TitleTypeTag {
  if (/信息差|没人告诉|真相|秘密/.test(text)) return 'info_gap'
  if (/上岸|Offer|逆袭|从0/.test(text)) return 'comeback'
  if (/卷|破防|HR|却|但/.test(text)) return 'conflict'
  if (/焦虑|崩溃|难|海投|0回复/.test(text)) return 'anxiety'
  return TYPE_TAGS[index % TYPE_TAGS.length]
}

function mapApiTitle(item: GeneratedTitle, index: number): ScoredTitle {
  const baseCtr =
    item.estimatedCtr === 'high' ? 88 + (index % 5) : item.estimatedCtr === 'medium' ? 78 + (index % 7) : 68 + (index % 6)

  return {
    title: item.text,
    ctr: Math.min(98, baseCtr),
    viral: Math.min(96, baseCtr - 2 + (index % 4)),
    emotion: Math.min(97, baseCtr + 1 + (index % 3)),
    typeTag: inferTypeTag(item.text, index),
    highlights: item.highlights.length ? item.highlights : extractHighlightSegments(item.text),
  }
}

/** Mock / 真实接口统一的标题生成 */
export async function fetchScoredTitles(
  params: TitleGenerateParams,
  analysisId?: string,
): Promise<TitleGenerateResult> {
  if (USE_MOCK) {
    await delay(2400)
    return {
      titles: [],
      promptVersion: 'mock-pending',
    }
  }

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
