import { createI18n } from 'vue-i18n'
import type { AppLocale } from './types'
import zhCN from './zh-CN'
import zhTW from './zh-TW'
import en from './en'

export type { AppLocale, LocaleMessages } from './types'
export { LOCALE_OPTIONS } from './types'

const LOCALE_KEY = 'xhs_locale'
const SUPPORTED_LOCALES: AppLocale[] = ['zh-CN', 'zh-TW', 'en']

export function detectBrowserLocale(): AppLocale {
  const lang = navigator.language.toLowerCase()
  if (lang.startsWith('zh-tw') || lang.startsWith('zh-hk') || lang.startsWith('zh-mo')) {
    return 'zh-TW'
  }
  if (lang.startsWith('zh')) {
    return 'zh-CN'
  }
  return 'en'
}

export function resolveInitialLocale(): AppLocale {
  const saved = localStorage.getItem(LOCALE_KEY) as AppLocale | null
  if (saved && SUPPORTED_LOCALES.includes(saved)) {
    return saved
  }
  return detectBrowserLocale()
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: resolveInitialLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'zh-TW': zhTW,
    en,
  },
})

export function setAppLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_KEY, locale)
  document.documentElement.lang = locale
  document.title = i18n.global.t('app.title')
}

export function initDocumentLocale() {
  const locale = resolveInitialLocale()
  document.documentElement.lang = locale
  document.title = i18n.global.t('app.title')
}
