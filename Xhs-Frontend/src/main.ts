import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { i18n, initDocumentLocale, resolveInitialLocale } from './locales'
import { elementPlusLocales } from './utils/elementLocale'

initDocumentLocale()

const app = createApp(App)
app.config.errorHandler = (err) => {
  console.error('[App Error]', err)
}
app.use(createPinia())
app.use(i18n)
app.use(ElementPlus, { locale: elementPlusLocales[resolveInitialLocale()] })
app.use(router)
app.mount('#app')
