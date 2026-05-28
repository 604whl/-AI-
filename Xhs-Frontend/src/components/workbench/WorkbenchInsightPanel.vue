<template>
  <div class="insight-panel">
    <ViralScoreCard
      :scores="scores"
      :loading="loading"
      :loading-step="loadingStep"
      :has-data="hasResult"
      @refresh="$emit('refresh')"
    />
    <RecommendedTitlesPanel
      :titles="recommendedTitles"
      :regenerating="titlesRegenerating"
      @regenerate="$emit('regenerate-titles')"
    />
    <HotPointsBreakdown :points="hotPoints" />
    <AiOptimizationPanel :advice="optimization" :has-data="hasResult" />
    <HotTopicsPanel :topics="hotTopics" @select="$emit('select-topic', $event)" />
  </div>
</template>

<script setup lang="ts">
import ViralScoreCard from './ViralScoreCard.vue'
import RecommendedTitlesPanel from './RecommendedTitlesPanel.vue'
import HotPointsBreakdown from './HotPointsBreakdown.vue'
import HotTopicsPanel from './HotTopicsPanel.vue'
import AiOptimizationPanel from './AiOptimizationPanel.vue'
import type {
  AiOptimizationAdvice,
  AnalysisLoadingStep,
  HotPoint,
  RecommendedTitle,
  ViralScoreCard as ViralScoreCardType,
} from '@/types/workbench'

defineProps<{
  scores: ViralScoreCardType | null
  recommendedTitles: RecommendedTitle[]
  hotPoints: HotPoint[]
  hotTopics: string[]
  optimization: AiOptimizationAdvice | null
  loading: boolean
  loadingStep: AnalysisLoadingStep
  hasResult: boolean
  titlesRegenerating: boolean
}>()

defineEmits<{
  refresh: []
  'regenerate-titles': []
  'select-topic': [topic: string]
}>()
</script>

<style scoped>
.insight-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}
</style>
