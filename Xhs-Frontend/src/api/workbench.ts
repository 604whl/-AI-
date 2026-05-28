import {
  MOCK_HOT_POINTS,
  MOCK_HOT_TOPICS,
  MOCK_OPTIMIZATION,
  MOCK_RECOMMENDED_TITLES,
  MOCK_RECOMMENDED_TITLES_ALT,
  MOCK_SENSITIVE_WORDS,
  MOCK_VIRAL_SCORES,
  MOCK_WORKBENCH_INSIGHT,
} from '@/mocks/workbench'
import type {
  RecommendedTitle,
  SensitiveRiskLevel,
  SensitiveWordResult,
  WorkbenchAnalyzeParams,
  WorkbenchInsightResult,
} from '@/types/workbench'

const USE_MOCK = true

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function calcRiskLevel(count: number): SensitiveRiskLevel {
  if (count >= 3) return 'high'
  if (count >= 1) return 'medium'
  return 'low'
}

/** 扫描文本中的敏感词 */
export function detectSensitiveWords(title: string, body: string): SensitiveWordResult {
  const content = `${title} ${body}`
  const words = MOCK_SENSITIVE_WORDS.filter((word) => content.includes(word))
  return {
    words,
    riskLevel: calcRiskLevel(words.length),
  }
}

/** 获取完整 AI 洞察（mock / 真实接口切换点） */
export async function fetchWorkbenchInsight(
  params: WorkbenchAnalyzeParams,
): Promise<WorkbenchInsightResult> {
  if (USE_MOCK) {
    await delay(2200)
    const sensitiveWords = detectSensitiveWords(params.title, params.body)
    return {
      ...MOCK_WORKBENCH_INSIGHT,
      sensitiveWords,
    }
  }

  // TODO: 接入真实后端接口
  // const res = await http.post<ApiResponse<WorkbenchInsightResult>>('/workbench/insight', params)
  // return res.data.data
  await delay(2200)
  return {
    scores: MOCK_VIRAL_SCORES,
    recommendedTitles: MOCK_RECOMMENDED_TITLES,
    hotPoints: MOCK_HOT_POINTS,
    hotTopics: MOCK_HOT_TOPICS,
    sensitiveWords: detectSensitiveWords(params.title, params.body),
    optimization: MOCK_OPTIMIZATION,
  }
}

let titleAltToggle = false

/** 重新生成推荐标题 */
export async function regenerateRecommendedTitles(
  _params: WorkbenchAnalyzeParams,
): Promise<RecommendedTitle[]> {
  if (USE_MOCK) {
    await delay(1200)
    titleAltToggle = !titleAltToggle
    return titleAltToggle ? MOCK_RECOMMENDED_TITLES_ALT : MOCK_RECOMMENDED_TITLES
  }

  // TODO: 接入真实后端接口
  await delay(1200)
  return MOCK_RECOMMENDED_TITLES
}
