<template>
  <el-dropdown trigger="click" @command="handleChange">
    <el-button text class="language-btn">
      <el-icon><Operation /></el-icon>
      <span>{{ currentLabel }}</span>
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="option in LOCALE_OPTIONS"
          :key="option.value"
          :command="option.value"
          :class="{ 'is-active': locale === option.value }"
        >
          {{ t(option.labelKey) }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Operation } from '@element-plus/icons-vue'
import { LOCALE_OPTIONS, setAppLocale, type AppLocale } from '@/locales'

const { t, locale } = useI18n()

const currentLabel = computed(() => {
  const option = LOCALE_OPTIONS.find((item) => item.value === locale.value)
  return option ? t(option.labelKey) : t('language.label')
})

function handleChange(next: AppLocale) {
  setAppLocale(next)
}
</script>

<style scoped>
.language-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: inherit;
}

:deep(.el-dropdown-menu__item.is-active) {
  color: #ff2442;
  font-weight: 600;
}
</style>
