<template>
  <div class="viral-score-card">
    <div class="card-header">
      <div class="header-left">
        <el-icon class="header-icon"><DataAnalysis /></el-icon>
        <span class="header-title">{{ t('workbench.viralScoreTitle') }}</span>
      </div>
      <el-button
        v-if="hasData"
        text
        size="small"
        :loading="loading"
        @click="$emit('refresh')"
      >
        <el-icon><Refresh /></el-icon>
      </el-button>
    </div>

    <AiLoadingSteps v-if="loading" :loading="loading" :step="loadingStep" />

    <template v-else-if="hasData && scores">
      <div class="main-score">
        <el-progress
          type="dashboard"
          :percentage="scores.viralScore"
          :width="120"
          :stroke-width="10"
          :color="scoreColor(scores.viralScore)"
        >
          <template #default="{ percentage }">
            <div class="dashboard-inner">
              <span class="dashboard-value">{{ percentage }}</span>
              <span class="dashboard-label">{{ t('workbench.viralIndex') }}</span>
            </div>
          </template>
        </el-progress>
      </div>

      <div class="score-grid">
        <div v-for="item in scoreItems" :key="item.key" class="score-item">
          <div class="score-item-header">
            <span class="score-item-label">{{ item.label }}</span>
            <span class="score-item-value" :style="{ color: scoreColor(item.value) }">
              {{ item.value }}
            </span>
          </div>
          <el-progress
            :percentage="item.value"
            :stroke-width="6"
            :color="scoreColor(item.value)"
            :show-text="false"
          />
        </div>
      </div>
    </template>

    <div v-else class="empty-state">
      <el-icon class="empty-icon"><MagicStick /></el-icon>
      <p>{{ t('workbench.viralScoreEmpty') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { DataAnalysis, MagicStick, Refresh } from '@element-plus/icons-vue'
import AiLoadingSteps from './AiLoadingSteps.vue'
import type { AnalysisLoadingStep, ViralScoreCard } from '@/types/workbench'

const props = defineProps<{
  scores: ViralScoreCard | null
  loading: boolean
  loadingStep: AnalysisLoadingStep
  hasData: boolean
}>()

defineEmits<{
  refresh: []
}>()

const { t } = useI18n()

const scoreItems = computed(() => {
  if (!props.scores) return []
  return [
    { key: 'ctr', label: t('workbench.ctrScore'), value: props.scores.ctrScore },
    { key: 'emotion', label: t('workbench.emotionScore'), value: props.scores.emotionScore },
    { key: 'collect', label: t('workbench.collectScore'), value: props.scores.collectScore },
    { key: 'conversion', label: t('workbench.conversionScore'), value: props.scores.conversionScore },
  ]
})

function scoreColor(value: number): string {
  if (value >= 85) return '#ff2442'
  if (value >= 70) return '#f59e0b'
  return '#6366f1'
}
</script>

<style scoped>
.viral-score-card {
  padding: 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.card-header {
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

.header-icon {
  font-size: 18px;
  color: #ff2442;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.main-score {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.dashboard-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.dashboard-value {
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  line-height: 1;
}

.dashboard-label {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 4px;
}

.score-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.score-item-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}

.score-item-label {
  font-size: 12px;
  color: #6b7280;
}

.score-item-value {
  font-size: 13px;
  font-weight: 700;
}

.empty-state {
  text-align: center;
  padding: 32px 16px;
  color: #9ca3af;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
  color: #d1d5db;
}

.empty-state p {
  margin: 0;
  font-size: 13px;
}
</style>
