import type {
  AiOptimizationAdvice,
  HotPoint,
  RecommendedTitle,
  SensitiveWordResult,
  ViralScoreCard,
  WorkbenchInsightResult,
} from '@/types/workbench'

export const MOCK_OPTIMIZATION: AiOptimizationAdvice = {
  issues: ['缺少结果展示', '没有身份标签', '缺少时间节点'],
  suggestions: [
    '增加 Offer / 上岸 / 逆袭等结果词',
    '补充时间线（如秋招第47天）',
    '增加真实经历细节增强可信度',
  ],
}

export const MOCK_VIRAL_SCORES: ViralScoreCard = {
  viralScore: 82,
  ctrScore: 88,
  emotionScore: 91,
  collectScore: 72,
  conversionScore: 85,
}

export const MOCK_RECOMMENDED_TITLES: RecommendedTitle[] = [
  { title: '海投300份后，我终于认清现实', ctr: 89 },
  { title: '留学生秋招已经卷疯了', ctr: 92 },
  { title: '英国硕士毕业后，我彻底破防', ctr: 87 },
]

export const MOCK_HOT_POINTS: HotPoint[] = [
  {
    point: '海投300份',
    type: '数字冲击',
    psychology: '量化焦虑，引发「我也一样」共鸣',
    emotion: '破防型',
  },
  {
    point: '留学生求职',
    type: '高需求赛道',
    psychology: '身份标签强化，精准触达目标人群',
    emotion: '焦虑型',
  },
  {
    point: '为什么这么难',
    type: '焦虑情绪',
    psychology: '痛点提问，激发评论区倾诉欲',
    emotion: '共鸣型',
  },
]

export const MOCK_HOT_TOPICS: string[] = [
  '留学生暑期实习',
  '英国硕士回国',
  '秋招崩溃',
  '留学生失业',
]

export const MOCK_SENSITIVE_WORDS: string[] = ['保Offer', '兼职代投', '保录']

export const MOCK_SENSITIVE_RESULT: SensitiveWordResult = {
  words: MOCK_SENSITIVE_WORDS,
  riskLevel: 'high',
}

export const MOCK_WORKBENCH_INSIGHT: WorkbenchInsightResult = {
  scores: MOCK_VIRAL_SCORES,
  recommendedTitles: MOCK_RECOMMENDED_TITLES,
  hotPoints: MOCK_HOT_POINTS,
  hotTopics: MOCK_HOT_TOPICS,
  sensitiveWords: MOCK_SENSITIVE_RESULT,
  optimization: MOCK_OPTIMIZATION,
}

/** 重新生成标题时的备选 mock */
export const MOCK_RECOMMENDED_TITLES_ALT: RecommendedTitle[] = [
  { title: '300份简历0回复，留学生的至暗时刻', ctr: 90 },
  { title: '秋招第47天，我悟了这3个真相', ctr: 88 },
  { title: '英国一年硕，回国求职有多卷？', ctr: 86 },
]
