<template>
  <el-card shadow="never" class="report-module">
    <template #header>
      <span class="module-title">{{ t('report.moduleScore') }}</span>
    </template>

    <div v-if="report" class="score-overview">
      <div class="main-score-block">
        <el-progress
          type="dashboard"
          :percentage="report.scores.viral.score"
          :width="140"
          :stroke-width="10"
          :color="scoreColor(report.scores.viral.score)"
        >
          <template #default="{ percentage }">
            <div class="dashboard-inner">
              <span class="dashboard-value">{{ percentage }}</span>
              <span class="dashboard-label">{{ t('workbench.viralIndex') }}</span>
              <span class="dashboard-level">{{ potentialLabel(report.scores.viral.level) }}</span>
            </div>
          </template>
        </el-progress>
        <p class="viral-reason">{{ report.scores.viral.reason }}</p>
      </div>

      <div class="dimension-grid">
        <div
          v-for="dim in dimensions"
          :key="dim.key"
          class="dimension-card"
        >
          <div class="dim-header">
            <span class="dim-label">{{ dim.label }}</span>
            <span class="dim-score" :style="{ color: scoreColor(dim.score) }">{{ dim.score }}</span>
          </div>
          <el-progress
            :percentage="dim.score"
            :stroke-width="6"
            :color="scoreColor(dim.score)"
            :show-text="false"
          />
          <p class="dim-reason">{{ dim.reason }}</p>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AnalysisReport } from '@/types/api'

const props = defineProps<{
  report: AnalysisReport | null
}>()

const { t } = useI18n()

const dimensions = computed(() => {
  if (!props.report) return []
  const { scores } = props.report
  return [
    { key: 'ctr', label: t('workbench.ctrScore'), score: scores.ctr.score, reason: scores.ctr.reason },
    { key: 'emotion', label: t('workbench.emotionScore'), score: scores.emotion.score, reason: scores.emotion.reason },
    { key: 'collect', label: t('workbench.collectScore'), score: scores.collect.score, reason: scores.collect.reason },
    { key: 'conversion', label: t('workbench.conversionScore'), score: scores.conversion.score, reason: scores.conversion.reason },
  ]
})

function scoreColor(value: number): string {
  if (value >= 80) return '#10b981'
  if (value >= 60) return '#f59e0b'
  return '#ef4444'
}

function potentialLabel(level?: string) {
  if (level === 'high') return t('report.potentialHigh')
  if (level === 'medium') return t('report.potentialMedium')
  if (level === 'low') return t('report.potentialLow')
  return ''
}
</script>

<style scoped>
.report-module {
  border-radius: 12px;
}

.module-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.score-overview {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.main-score-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.dashboard-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.dashboard-value {
  font-size: 32px;
  font-weight: 700;
  color: #111827;
  line-height: 1;
}

.dashboard-label {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
}

.dashboard-level {
  font-size: 11px;
  color: #10b981;
  margin-top: 2px;
  font-weight: 600;
}

.viral-reason {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
  text-align: center;
  max-width: 480px;
}

.dimension-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.dimension-card {
  padding: 14px;
  border-radius: 10px;
  background: #f9fafb;
  border: 1px solid #f3f4f6;
}

.dim-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.dim-label {
  font-size: 13px;
  color: #6b7280;
}

.dim-score {
  font-size: 16px;
  font-weight: 700;
}

.dim-reason {
  margin: 8px 0 0;
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.5;
}

@media (max-width: 640px) {
  .dimension-grid {
    grid-template-columns: 1fr;
  }
}
</style>
