/** 标题类型标签 */
export type TitleTypeTag = 'anxiety' | 'info_gap' | 'comeback' | 'conflict'

/** 标题生成 loading 步骤 */
export type TitleLoadingStep =
  | 'idle'
  | 'emotion'
  | 'ctr'
  | 'structure'
  | 'optimize'
  | 'done'

/** 带评分的 AI 生成标题 */
export interface ScoredTitle {
  title: string
  ctr: number
  viral: number
  emotion: number
  typeTag: TitleTypeTag
  highlights: string[]
}

/** 标题生成请求参数 */
export interface TitleGenerateParams {
  goal: string
  count: number
  persona: string
  title?: string
  body?: string
}

/** 标题生成响应 */
export interface TitleGenerateResult {
  titles: ScoredTitle[]
  promptVersion: string
}
