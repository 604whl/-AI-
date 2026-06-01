<template>
  <el-card shadow="never" class="report-card">
    <template #header>
      <div class="card-head">
        <span>{{ t('chat.cardAnalysis') }}</span>
        <el-button v-if="taskId" link type="primary" @click="goReport">{{ t('chat.viewFullReport') }}</el-button>
      </div>
    </template>

    <div v-if="summary.contentType" class="content-type">
      <el-tag size="small" effect="plain">{{ contentTypeLabel }}</el-tag>
    </div>

    <div v-if="scoreEntries.length" class="score-grid">
      <div v-for="item in scoreEntries" :key="item.key" class="score-item">
        <span class="score-label">{{ item.label }}</span>
        <el-progress
          :percentage="item.score"
          :stroke-width="6"
          :color="scoreColor(item.score)"
          :format="() => String(item.score)"
        />
      </div>
    </div>

    <ul v-if="issues.length" class="issue-list">
      <li v-for="(issue, i) in issues" :key="i">
        <el-tag size="small" :type="severityType(issue.severity)">{{ issue.category }}</el-tag>
        <span>{{ issue.description }}</span>
      </li>
    </ul>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { AnalysisReportSummary } from '@/types/chat'

const props = defineProps<{
  payload?: Record<string, unknown>
  taskId?: string
}>()

const router = useRouter()
const { t } = useI18n()

const summary = computed(() => (props.payload ?? {}) as AnalysisReportSummary)

const contentTypeLabel = computed(() => {
  const key = summary.value.contentType
  if (!key) return ''
  const i18nKey = `workbench.contentTypes.${key}`
  const translated = t(i18nKey)
  return translated === i18nKey ? key : translated
})

const scoreEntries = computed(() => {
  const scores = summary.value.scores
  if (!scores) return []
  return [
    { key: 'ctr', label: t('workbench.ctrScore'), score: scores.ctr?.score ?? 0 },
    { key: 'emotion', label: t('workbench.emotionScore'), score: scores.emotion?.score ?? 0 },
    { key: 'collect', label: t('workbench.collectScore'), score: scores.collect?.score ?? 0 },
    { key: 'conversion', label: t('workbench.conversionScore'), score: scores.conversion?.score ?? 0 },
    { key: 'viral', label: t('workbench.viralIndex'), score: scores.viral?.score ?? 0 },
  ].filter((item) => item.score > 0)
})

const issues = computed(() => summary.value.topIssues ?? [])

function scoreColor(value: number) {
  if (value >= 80) return '#10b981'
  if (value >= 60) return '#f59e0b'
  return '#ef4444'
}

function severityType(severity?: string) {
  if (severity === 'high') return 'danger'
  if (severity === 'medium') return 'warning'
  return 'info'
}

function goReport() {
  if (props.taskId) {
    router.push({ name: 'analysis-report', params: { id: props.taskId } })
  }
}
</script>

<style scoped>
.report-card {
  border: 1px solid #fecdd3;
  background: #fffafb;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.content-type {
  margin-bottom: 12px;
}
.score-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.score-label {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}
.issue-list {
  margin: 0;
  padding-left: 0;
  list-style: none;
}
.issue-list li {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 13px;
  color: #374151;
  margin-bottom: 8px;
}
</style>
