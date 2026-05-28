import zhCn from 'element-plus/es/locale/lang/zh-cn'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import en from 'element-plus/es/locale/lang/en'
import type { AppLocale } from '@/locales'

export const elementPlusLocales: Record<AppLocale, typeof zhCn> = {
  'zh-CN': zhCn,
  'zh-TW': zhTw,
  en,
}
