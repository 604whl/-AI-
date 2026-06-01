<template>
  <aside class="report-sidebar">
    <el-card shadow="never" class="sidebar-card">
      <template #header>
        <span>{{ t('report.originalContent') }}</span>
      </template>

      <div v-if="detail" class="source-content">
        <div v-if="coverPreviewUrl" class="source-field">
          <span class="field-label">{{ t('analysis.cover') }}</span>
          <div class="cover-thumb">
            <img :src="coverPreviewUrl" :alt="t('analysis.cover')" />
          </div>
        </div>
        <div v-if="detail.title" class="source-field">
          <span class="field-label">{{ t('analysis.title') }}</span>
          <p class="field-text">{{ detail.title }}</p>
        </div>
        <div v-if="detail.body" class="source-field">
          <span class="field-label">{{ t('analysis.body') }}</span>
          <p class="field-text body-text" :class="{ collapsed: !bodyExpanded && isLongBody }">
            {{ displayBody }}
          </p>
          <el-button
            v-if="isLongBody"
            text
            type="primary"
            size="small"
            @click="bodyExpanded = !bodyExpanded"
          >
            {{ bodyExpanded ? t('report.collapse') : t('report.expand') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card
      v-if="detail?.scenario === 'published' && hasPublishedMetrics"
      shadow="never"
      class="sidebar-card"
    >
      <template #header>
        <span>{{ t('report.publishedMetricsTitle') }}</span>
      </template>
      <div class="metrics-grid">
        <div v-for="row in metricRows" :key="row.key" class="metric-row">
          <span class="metric-label">{{ row.label }}</span>
          <span class="metric-value">{{ row.value }}</span>
        </div>
      </div>
    </el-card>

    <el-card v-if="detail" shadow="never" class="sidebar-card meta-card">
      <div class="meta-row">
        <span class="meta-label">{{ t('analysis.scenario') }}</span>
        <el-tag size="small" effect="plain">{{ scenarioLabel(detail.scenario) }}</el-tag>
      </div>
      <div v-if="detail.persona" class="meta-row">
        <span class="meta-label">{{ t('analysis.persona') }}</span>
        <span class="meta-value">{{ t(`persona.${detail.persona}`) }}</span>
      </div>
      <div class="meta-row">
        <span class="meta-label">{{ t('report.createdAt') }}</span>
        <span class="meta-value">{{ formatDate(detail.createdAt) }}</span>
      </div>
    </el-card>
  </aside>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveCoverPreviewUrl } from '@/api/file'
import type { AnalysisDetail, AnalysisScenario } from '@/types/api'

const props = defineProps<{
  detail: AnalysisDetail | null
}>()

const { t, locale } = useI18n()
const bodyExpanded = ref(false)
const coverPreviewUrl = ref<string | null>(null)

const isLongBody = computed(() => (props.detail?.body?.length ?? 0) > 200)

const displayBody = computed(() => {
  const body = props.detail?.body ?? ''
  if (!isLongBody.value || bodyExpanded.value) return body
  return body.slice(0, 200) + '…'
})

const hasPublishedMetrics = computed(() => metricRows.value.length > 0)

const metricRows = computed(() => {
  const m = props.detail?.publishedMetrics
  if (!m) return []
  const rows: Array<{ key: string; label: string; value: string }> = []
  const add = (key: string, label: string, value: number | string | undefined) => {
    if (value !== undefined && value !== null && value !== '') {
      rows.push({ key, label, value: String(value) })
    }
  }
  add('impressions', t('analysis.impressions'), m.impressions)
  add('likes', t('analysis.likes'), m.likes)
  add('collects', t('analysis.collects'), m.collects)
  add('comments', t('analysis.comments'), m.comments)
  add('dmInquiries', t('analysis.dmInquiries'), m.dmInquiries)
  if (m.publishedAt) {
    rows.push({
      key: 'publishedAt',
      label: t('analysis.publishedAt'),
      value: new Date(m.publishedAt).toLocaleString(locale.value),
    })
  }
  return rows
})

watch(
  () => props.detail?.coverImageUrl,
  async (url) => {
    if (coverPreviewUrl.value) {
      URL.revokeObjectURL(coverPreviewUrl.value)
      coverPreviewUrl.value = null
    }
    if (!url) return
    try {
      coverPreviewUrl.value = await resolveCoverPreviewUrl(url)
    } catch {
      coverPreviewUrl.value = null
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (coverPreviewUrl.value) {
    URL.revokeObjectURL(coverPreviewUrl.value)
  }
})

function scenarioLabel(scenario: AnalysisScenario) {
  const map: Record<AnalysisScenario, string> = {
    draft: t('analysis.scenarioDraft'),
    published: t('analysis.scenarioPublished'),
    competitor: t('analysis.scenarioCompetitor'),
  }
  return map[scenario]
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString(locale.value)
}
</script>

<style scoped>
.report-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sidebar-card {
  border-radius: 12px;
}

.source-field {
  margin-bottom: 16px;
}

.source-field:last-child {
  margin-bottom: 0;
}

.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #9ca3af;
  margin-bottom: 6px;
}

.field-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #374151;
  word-break: break-word;
}

.body-text.collapsed {
  max-height: 120px;
  overflow: hidden;
}

.cover-thumb {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  max-width: 100%;
}

.cover-thumb img {
  display: block;
  width: 100%;
  height: auto;
}

.metrics-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  gap: 8px;
}

.metric-label {
  color: #9ca3af;
}

.metric-value {
  color: #374151;
  font-weight: 500;
}

.meta-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}

.meta-label {
  color: #9ca3af;
}

.meta-value {
  color: #374151;
  font-weight: 500;
}
</style>
