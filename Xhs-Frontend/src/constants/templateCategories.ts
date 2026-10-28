/**
 * 模板 / 内容品类编码。
 *
 * 与后端 app.category（V34__stage0_freeze.sql）保持一致。
 * 产品定位为全平台全品类，品类可由运营在工作区扩展；
 * 这里的清单是官方模板内置品类的展示集合。
 */
export const TEMPLATE_CATEGORIES = [
  'BEAUTY',
  'FASHION',
  'FOOD',
  'TRAVEL',
  'LIFESTYLE',
  'KNOWLEDGE',
  'MOM_BABY',
  'CAREER',
  'STUDY_ABROAD',
  'OTHER',
] as const

export type TemplateCategory = (typeof TEMPLATE_CATEGORIES)[number]
