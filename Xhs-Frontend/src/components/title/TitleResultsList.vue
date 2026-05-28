<template>
  <div class="title-results-list">
    <div class="results-header">
      <div class="header-left">
        <el-icon class="header-icon"><MagicStick /></el-icon>
        <h4>{{ t('titles.resultsTitle') }}</h4>
        <el-tag size="small" type="danger" effect="plain" round>AI</el-tag>
      </div>
      <span v-if="promptVersion" class="prompt-version">{{ promptVersion }}</span>
    </div>

    <TitleAiLoadingSteps v-if="loading" :loading="loading" :step="loadingStep" />

    <div v-else-if="titles.length" class="cards-grid">
      <TitleResultCard
        v-for="(item, index) in titles"
        :key="index"
        :item="item"
        :style="{ animationDelay: `${index * 0.08}s` }"
        class="card-animate"
        @copy="$emit('copy', $event)"
        @apply="$emit('apply', $event)"
      />
    </div>

    <el-empty v-else-if="hasGenerated && !loading" :description="t('titles.empty')" />
    <div v-else class="results-placeholder">
      <el-icon class="placeholder-icon"><EditPen /></el-icon>
      <p>{{ t('titles.resultsPlaceholder') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { EditPen, MagicStick } from '@element-plus/icons-vue'
import TitleAiLoadingSteps from './TitleAiLoadingSteps.vue'
import TitleResultCard from './TitleResultCard.vue'
import type { ScoredTitle, TitleLoadingStep } from '@/types/title'

defineProps<{
  titles: ScoredTitle[]
  loading: boolean
  loadingStep: TitleLoadingStep
  hasGenerated: boolean
  promptVersion: string
}>()

defineEmits<{
  copy: [title: string]
  apply: [item: ScoredTitle]
}>()

const { t } = useI18n()
</script>

<style scoped>
.title-results-list {
  min-height: 200px;
}

.results-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-left h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.header-icon {
  font-size: 18px;
  color: #ff2442;
}

.prompt-version {
  font-size: 12px;
  color: #9ca3af;
}

.cards-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-animate {
  animation: fadeInUp 0.45s ease both;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.results-placeholder {
  text-align: center;
  padding: 48px 24px;
  border-radius: 12px;
  border: 1px dashed #e5e7eb;
  background: linear-gradient(180deg, #fafafa 0%, #fff 100%);
}

.placeholder-icon {
  font-size: 36px;
  color: #d1d5db;
  margin-bottom: 12px;
}

.results-placeholder p {
  margin: 0;
  font-size: 13px;
  color: #9ca3af;
  line-height: 1.6;
}
</style>
