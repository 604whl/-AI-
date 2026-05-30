<template>
  <el-card shadow="never" class="report-module">
    <template #header>
      <span class="module-title">{{ t('report.moduleIssues') }}</span>
    </template>

    <ul v-if="issues.length" class="issue-list">
      <li v-for="(issue, index) in issues" :key="index" class="issue-item">
        <span class="severity-dot" :class="issue.severity" />
        <div class="issue-body">
          <div class="issue-header">
            <el-tag size="small" :type="severityTagType(issue.severity)" effect="plain">
              {{ severityLabel(issue.severity) }}
            </el-tag>
            <el-tag size="small" type="info" effect="plain">
              {{ categoryLabel(issue.category) }}
            </el-tag>
          </div>
          <p class="issue-desc">{{ issue.description }}</p>
          <p class="issue-suggestion">{{ issue.suggestion }}</p>
        </div>
      </li>
    </ul>
    <el-empty v-else :description="t('report.noIssues')" :image-size="64" />
  </el-card>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { AnalysisReport } from '@/types/api'

defineProps<{
  issues: AnalysisReport['issues']
}>()

const { t } = useI18n()

function severityLabel(severity: string) {
  const map: Record<string, string> = {
    high: t('report.severityHigh'),
    medium: t('report.severityMedium'),
    low: t('report.severityLow'),
  }
  return map[severity] ?? severity
}

function severityTagType(severity: string): 'danger' | 'warning' | 'success' {
  if (severity === 'high') return 'danger'
  if (severity === 'medium') return 'warning'
  return 'success'
}

function categoryLabel(category: string) {
  const map: Record<string, string> = {
    ctr: t('workbench.ctrScore'),
    emotion: t('workbench.emotionScore'),
    collect: t('workbench.collectScore'),
    conversion: t('workbench.conversionScore'),
    compliance: t('report.categoryCompliance'),
  }
  return map[category] ?? category
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

.issue-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.issue-item {
  display: flex;
  gap: 12px;
  padding: 14px;
  border-radius: 10px;
  background: #fafafa;
  border: 1px solid #f3f4f6;
}

.severity-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
}

.severity-dot.high {
  background: #ef4444;
}

.severity-dot.medium {
  background: #f59e0b;
}

.severity-dot.low {
  background: #10b981;
}

.issue-body {
  flex: 1;
  min-width: 0;
}

.issue-header {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.issue-desc {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 500;
  color: #111827;
  line-height: 1.5;
}

.issue-suggestion {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}
</style>
