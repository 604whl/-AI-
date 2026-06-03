<template>
  <div class="report-page">
    <header class="report-header">
      <div class="header-left">
        <el-button text @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
          {{ t('report.back') }}
        </el-button>
        <div class="header-meta">
          <h1 class="report-heading">
            {{ detail?.title || t('dashboard.untitled') }}
          </h1>
          <div v-if="detail" class="header-tags">
            <el-tag size="small" effect="plain">{{ scenarioLabel(detail.scenario) }}</el-tag>
            <el-tag size="small" :type="statusTagType(detail.status)">
              {{ statusLabel(detail.status) }}
            </el-tag>
          </div>
        </div>
      </div>
      <div v-if="detail" class="header-actions">
        <el-button
          v-if="isCompleted && detail.report"
          @click="copyReport"
        >
          {{ t('report.copyReport') }}
        </el-button>
        <el-button
          :loading="reanalyzeRunning"
          @click="handleReanalyze"
        >
          {{ t('history.reanalyze') }}
        </el-button>
        <el-popconfirm :title="t('report.deleteConfirm')" @confirm="handleDelete">
          <template #reference>
            <el-button type="danger" plain>{{ t('report.delete') }}</el-button>
          </template>
        </el-popconfirm>
      </div>
    </header>

    <!-- 初次加载 -->
    <div v-if="loading && !detail" class="loading-state">
      <el-skeleton :rows="10" animated />
    </div>

    <!-- 排队 / 分析中 -->
    <div v-else-if="isPending || polling" class="loading-state">
      <el-skeleton :rows="10" animated />
      <p class="loading-hint">{{ progressMessage || t('report.analyzingHint') }}</p>
    </div>

    <!-- 失败态 -->
    <el-result
      v-else-if="isFailed"
      icon="error"
      :title="t('report.failedTitle')"
      :sub-title="failureMessage"
    >
      <template #extra>
        <el-button type="primary" :loading="reanalyzeRunning" @click="handleReanalyze">
          {{ t('history.reanalyze') }}
        </el-button>
        <el-button @click="reload">{{ t('report.retry') }}</el-button>
        <el-button @click="router.push('/')">{{ t('report.backHome') }}</el-button>
      </template>
    </el-result>

    <!-- 完整报告 -->
    <div v-else-if="isCompleted && detail?.report" class="report-layout">
      <main class="report-main">
        <ReportScoreOverview :report="detail.report" />

        <ReportCoverPanel
          :cover-image-url="detail.coverImageUrl"
          :cover-analysis="detail.coverAnalysis"
        />

        <el-card shadow="never" class="report-module">
          <template #header>
            <span class="module-title">{{ t('report.moduleStructure') }}</span>
          </template>
          <ContentTypeTags
            :content-type="detail.report.contentType"
            :secondary-tags="detail.report.secondaryTags ?? []"
          />
          <StructureBreakdownPanel
            class="structure-panel"
            :structure="insight?.structure ?? null"
          />
        </el-card>

        <ReportCompetitorPanel
          v-if="detail.scenario === 'competitor'"
          :borrow-points="detail.report.borrowPoints"
          :do-not-copy="detail.report.doNotCopy"
        />

        <ReportIssuesPanel :issues="detail.report.issues" />
        <ReportOptimizationTabs :report="detail.report" />
        <ReportActionsPanel
          :compliance-warnings="detail.report.complianceWarnings"
          @optimize-draft="openOptimizeDraft"
          @generate-titles="openTitleDrawer"
        />
      </main>

      <ReportSourceSidebar :detail="detail" />
    </div>

    <!-- 已完成但报告缺失 -->
    <el-result
      v-else-if="isCompleted && detail && !detail.report"
      icon="warning"
      :title="t('report.reportMissingTitle')"
      :sub-title="t('report.reportMissingDesc')"
    >
      <template #extra>
        <el-button type="primary" :loading="reanalyzeRunning" @click="handleReanalyze">
          {{ t('history.reanalyze') }}
        </el-button>
        <el-button @click="router.push('/history')">{{ t('dashboard.viewAll') }}</el-button>
      </template>
    </el-result>

    <!-- 未找到 -->
    <el-empty v-else :description="t('report.notFound')">
      <el-button type="primary" @click="router.push('/')">{{ t('report.backHome') }}</el-button>
    </el-empty>

    <OptimizeDraftDrawer
      v-model:visible="draftDrawerVisible"
      :loading="draftLoading"
      :draft="draftResult"
      @regenerate="loadOptimizeDraft"
    />

    <el-drawer v-model="titleDrawerVisible" :title="t('report.generateTitles')" size="520px">
      <TitleGeneratorPanel
        v-if="detail"
        compact
        :initial-title="detail.title ?? ''"
        :initial-body="detail.body ?? ''"
        :initial-persona="detail.persona ?? 'agency'"
        :analysis-id="detail.id"
      />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { deleteAnalysis, optimizeDraft, type OptimizeDraftResponse } from '@/api/analysis'
import ContentTypeTags from '@/components/workbench/ContentTypeTags.vue'
import StructureBreakdownPanel from '@/components/workbench/StructureBreakdownPanel.vue'
import TitleGeneratorPanel from '@/components/title/TitleGeneratorPanel.vue'
import OptimizeDraftDrawer from '@/components/report/OptimizeDraftDrawer.vue'
import ReportActionsPanel from '@/components/report/ReportActionsPanel.vue'
import ReportCompetitorPanel from '@/components/report/ReportCompetitorPanel.vue'
import ReportCoverPanel from '@/components/report/ReportCoverPanel.vue'
import ReportIssuesPanel from '@/components/report/ReportIssuesPanel.vue'
import ReportOptimizationTabs from '@/components/report/ReportOptimizationTabs.vue'
import ReportScoreOverview from '@/components/report/ReportScoreOverview.vue'
import ReportSourceSidebar from '@/components/report/ReportSourceSidebar.vue'
import { useAnalysisReport } from '@/composables/useAnalysisReport'
import { useReanalyze } from '@/composables/useReanalyze'
import type { AnalysisScenario, AnalysisStatus } from '@/types/api'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const analysisId = computed(() => String(route.params.id ?? ''))
const { detail, loading, polling, progressMessage, isPending, isCompleted, isFailed, insight, reload } =
  useAnalysisReport(analysisId)
const { running: reanalyzeRunning, runReanalyze } = useReanalyze()

const draftDrawerVisible = ref(false)
const draftLoading = ref(false)
const draftResult = ref<OptimizeDraftResponse | null>(null)
const titleDrawerVisible = ref(false)

const failureMessage = computed(() => {
  const failure = detail.value?.failure
  if (!failure) return t('report.failedDefault')
  return failure.message || t('report.failedDefault')
})

function scenarioLabel(scenario: AnalysisScenario) {
  const map: Record<AnalysisScenario, string> = {
    draft: t('analysis.scenarioDraft'),
    published: t('analysis.scenarioPublished'),
    competitor: t('analysis.scenarioCompetitor'),
  }
  return map[scenario]
}

function statusLabel(status: AnalysisStatus) {
  return t(`dashboard.statuses.${status}`)
}

function statusTagType(status: AnalysisStatus): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<AnalysisStatus, 'success' | 'warning' | 'danger' | 'info'> = {
    completed: 'success',
    processing: 'warning',
    pending: 'info',
    failed: 'danger',
  }
  return map[status]
}

async function copyReport() {
  if (!detail.value?.report) return
  const report = detail.value.report
  const text = [
    detail.value.title ?? '',
    '',
    `爆文指数: ${report.scores.viral.score}`,
    `CTR: ${report.scores.ctr.score} | 情绪: ${report.scores.emotion.score}`,
    '',
    ...report.issues.map((i) => `[${i.severity}] ${i.description}`),
  ].join('\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('report.copySuccess'))
  } catch {
    ElMessage.error(t('titles.copyFailed'))
  }
}

async function handleDelete() {
  if (!detail.value) return
  try {
    await deleteAnalysis(detail.value.id)
    ElMessage.success(t('report.deleteSuccess'))
    await router.push('/history')
  } catch {
    ElMessage.error(t('report.deleteFailed'))
  }
}

async function openOptimizeDraft() {
  draftDrawerVisible.value = true
  await loadOptimizeDraft()
}

async function loadOptimizeDraft() {
  if (!detail.value) return
  draftLoading.value = true
  draftResult.value = null
  try {
    const res = await optimizeDraft(detail.value.id)
    draftResult.value = res.data.data
  } catch {
    ElMessage.error(t('report.optimizeDraftFailed'))
    draftDrawerVisible.value = false
  } finally {
    draftLoading.value = false
  }
}

function openTitleDrawer() {
  titleDrawerVisible.value = true
}

async function handleReanalyze() {
  if (!analysisId.value) return
  await runReanalyze(analysisId.value)
}
</script>

<style scoped>
.report-page {
  max-width: 1280px;
}

.report-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.header-meta {
  padding-top: 4px;
}

.report-heading {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  line-height: 1.3;
}

.header-tags {
  display: flex;
  gap: 8px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.loading-state {
  padding: 24px 0;
}

.loading-hint {
  margin-top: 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 14px;
}

.report-layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 20px;
  align-items: start;
}

.report-main {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.report-module {
  border-radius: 12px;
}

.module-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.structure-panel {
  margin-top: 16px;
}

@media (max-width: 960px) {
  .report-layout {
    grid-template-columns: 1fr;
  }
}
</style>
