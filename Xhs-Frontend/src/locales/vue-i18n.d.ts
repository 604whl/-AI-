import type { LocaleMessages } from './types'

declare module 'vue-i18n' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  export interface DefineLocaleMessage extends LocaleMessages {}
}
