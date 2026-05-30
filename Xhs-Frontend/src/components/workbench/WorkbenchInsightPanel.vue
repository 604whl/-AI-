<template>
  <div class="insight-panel">
    <ContentTypeTags
      :content-type="contentType"
      :secondary-tags="secondaryTags"
    />
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
      :can-regenerate="hasResult"
      @regenerate="$emit('regenerate-titles')"
      @apply="$emit('apply-title', $event)"
    />
    <StructureBreakdownPanel :structure="structure" />
    <HotPointsBreakdown :points="hotPoints" />
    <AiOptimizationPanel :advice="optimization" :has-data="hasResult" />
    <HotTopicsPanel :topics="hotTopics" @select="$emit('select-topic', $event)" />
  </div>
</template>

<script setup lang="ts">
import ViralScoreCard from './ViralScoreCard.vue'
import ContentTypeTags from './ContentTypeTags.vue'
import RecommendedTitlesPanel from './RecommendedTitlesPanel.vue'
import HotPointsBreakdown from './HotPointsBreakdown.vue'
import HotTopicsPanel from './HotTopicsPanel.vue'
import AiOptimizationPanel from './AiOptimizationPanel.vue'
import StructureBreakdownPanel from './StructureBreakdownPanel.vue'
import type {
  AiOptimizationAdvice,
  AnalysisLoadingStep,
  HotPoint,
  RecommendedTitle,
  StructureBreakdown,
  ViralScoreCard as ViralScoreCardType,
} from '@/types/workbench'

defineProps<{
  contentType: string | null
  secondaryTags: string[]
  structure: StructureBreakdown | null
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
  'apply-title': [title: string]
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
