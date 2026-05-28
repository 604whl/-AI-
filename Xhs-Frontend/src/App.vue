<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { i18n, type AppLocale } from '@/locales'
import { elementPlusLocales } from '@/utils/elementLocale'

const elementLocale = computed(
  () => elementPlusLocales[i18n.global.locale.value as AppLocale],
)

watch(
  () => i18n.global.locale.value,
  () => {
    document.title = i18n.global.t('app.title')
  },
  { immediate: true },
)
</script>

<style>
*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body,
#app {
  margin: 0;
  min-height: 100%;
  font-family:
    'PingFang SC',
    'Microsoft YaHei',
    -apple-system,
    BlinkMacSystemFont,
    'Segoe UI',
    sans-serif;
  color: #1f2937;
  background: #fff;
}
</style>
