import type { HotPoint, RecommendedTitle } from '@/types/workbench'

export const MOCK_RECOMMENDED_TITLES: RecommendedTitle[] = [
  { title: '小个子春日穿搭｜3套公式抄作业', ctr: 88 },
  { title: '跟风买防晒后脸更红了？敏感肌避坑', ctr: 85 },
  { title: '人均50吃遍老城区｜周末6站路线', ctr: 90 },
]

export const MOCK_HOT_POINTS: HotPoint[] = [
  {
    point: '护肤避坑',
    type: '痛点',
    psychology: '怕踩雷、怕烂脸',
    emotion: '焦虑共鸣',
  },
  {
    point: '本地探店',
    type: '结果',
    psychology: '周末决策、怕排队',
    emotion: '结果导向',
  },
  {
    point: '穿搭公式',
    type: '干货',
    psychology: '想抄作业、怕显矮',
    emotion: '信息差',
  },
]

export const MOCK_HOT_TOPICS: string[] = [
  '春日小个子穿搭',
  '敏感肌防晒清单',
  '周末人均50探店',
  '7天入门摄影',
]

export const MOCK_WORKBENCH_INSIGHT = {
  recommendedTitles: MOCK_RECOMMENDED_TITLES,
  hotPoints: MOCK_HOT_POINTS,
  hotTopics: MOCK_HOT_TOPICS,
}

export const MOCK_SCORED_TITLES_FOR_WORKBENCH = [
  { title: '小个子春日穿搭已经卷疯了', ctr: 92 },
  { title: '没人告诉你的护肤信息差', ctr: 87 },
  { title: '跟风10款防晒，敏感肌的至暗时刻', ctr: 90 },
]
