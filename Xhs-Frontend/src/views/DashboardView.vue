<template>
  <div class="dashboard">
    <section class="welcome-row">
      <div class="welcome-text">
        <h1 class="welcome-title">{{ greeting }}</h1>
        <p class="welcome-desc">{{ t('dashboard.welcomeDesc') }}</p>
      </div>
      <el-card class="quota-card" shadow="never">
        <div class="quota-header">
          <span>{{ t('dashboard.quotaTitle') }}</span>
          <el-tag size="small" type="info">{{ personaLabel }}</el-tag>
        </div>
        <div class="quota-numbers">
          <span class="quota-used">{{ usedToday }}</span>
          <span class="quota-sep">/</span>
          <span class="quota-total">{{ dailyQuota }}</span>
        </div>
        <el-progress
          :percentage="quotaPercent"
          :stroke-width="8"
          :color="quotaColor"
          :show-text="false"
        />
        <p class="quota-hint">{{ t('dashboard.quotaHint', { remaining: remainingQuota }) }}</p>
      </el-card>
    </section>

    <el-card class="section-card quick-analyze" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('dashboard.quickAnalyze') }}</span>
          <el-tag size="small" effect="plain">{{ t('dashboard.quickAnalyzeTag') }}</el-tag>
        </div>
      </template>
      <div class="workbench-layout">
        <div class="workbench-main">
          <el-form label-position="top" @submit.prevent="handleQuickAnalyze">
            <div class="form-row">
              <el-form-item :label="t('analysis.scenario')" class="form-item-scenario">
                <el-radio-group v-model="form.scenario">
                  <el-radio-button value="draft">{{ t('analysis.scenarioDraft') }}</el-radio-button>
                  <el-radio-button value="published">{{ t('analysis.scenarioPublished') }}</el-radio-button>
                  <el-radio-button value="competitor">{{ t('analysis.scenarioCompetitor') }}</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item :label="t('analysis.persona')" class="form-item-persona">
                <el-select v-model="form.persona" style="width: 100%">
                  <el-option :label="t('persona.agency')" value="agency" />
                  <el-option :label="t('persona.mentor')" value="mentor" />
                  <el-option :label="t('persona.senior')" value="senior" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item :label="t('analysis.title')">
              <el-input
                v-model="form.title"
                maxlength="100"
                show-word-limit
                :placeholder="t('dashboard.titlePlaceholder')"
              />
            </el-form-item>
            <el-form-item :label="t('analysis.body')">
              <el-input
                v-model="form.body"
                type="textarea"
                :rows="5"
                :placeholder="t('dashboard.bodyPlaceholder')"
              />
            </el-form-item>
            <SensitiveWordDetector
              :result="workbench.sensitiveWords.value"
              :title="form.title"
              :body="form.body"
            />
            <div class="form-actions">
              <el-button
                type="primary"
                :loading="analyzing || workbench.loading.value"
                @click="handleQuickAnalyze"
              >
                {{ t('dashboard.analyzeNow') }}
              </el-button>
              <el-button @click="openTitleDrawer">{{ t('dashboard.generateTitles') }}</el-button>
              <el-button text @click="fillSample">{{ t('dashboard.trySample') }}</el-button>
            </div>
          </el-form>
        </div>
        <div class="workbench-insight">
          <WorkbenchInsightPanel
            :scores="workbench.scores.value"
            :recommended-titles="workbench.recommendedTitles.value"
            :hot-points="workbench.hotPoints.value"
            :hot-topics="workbench.hotTopics.value"
            :optimization="workbench.optimization.value"
            :loading="workbench.loading.value"
            :loading-step="workbench.loadingStep.value"
            :has-result="workbench.hasResult.value"
            :titles-regenerating="workbench.titlesRegenerating.value"
            @refresh="refreshInsight"
            @regenerate-titles="regenerateTitles"
            @select-topic="applyHotTopic"
          />
        </div>
      </div>
    </el-card>

    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('dashboard.recentRecords') }}</span>
          <div class="card-header-actions">
            <el-button text :loading="recordsLoading" @click="loadRecentRecords">
              {{ t('dashboard.refresh') }}
            </el-button>
            <el-button text type="primary" @click="$router.push('/history')">
              {{ t('dashboard.viewAll') }}
            </el-button>
          </div>
        </div>
      </template>
      <el-skeleton v-if="recordsLoading && !recentRecords.length" :rows="4" animated />
      <el-empty v-else-if="!recentRecords.length" :description="t('dashboard.noRecords')">
        <el-button type="primary" @click="fillSample">{{ t('dashboard.trySample') }}</el-button>
      </el-empty>
      <el-table
        v-else
        :data="recentRecords"
        stripe
        class="records-table"
        @row-click="goToReport"
      >
        <el-table-column prop="title" :label="t('analysis.title')" min-width="200">
          <template #default="{ row }">
            <span class="record-title">{{ row.title || t('dashboard.untitled') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="scenario" :label="t('analysis.scenario')" width="120">
          <template #default="{ row }">
            {{ scenarioLabel(row.scenario) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('dashboard.status')" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('dashboard.score')" width="90" align="center">
          <template #default="{ row }">
            <span v-if="averageReportScore(row.report) !== null" class="score-badge">
              {{ averageReportScore(row.report) }}
            </span>
            <span v-else class="score-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('dashboard.createdAt')" width="120">
          <template #default="{ row }">
            {{ formatRelativeTime(row.createdAt, locale) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-row :gutter="16" class="bottom-row">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>{{ t('dashboard.tipsTitle') }}</template>
          <ul class="tips-list">
            <li v-for="(tip, index) in tips" :key="index">{{ tip }}</li>
          </ul>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>{{ t('dashboard.system') }}</template>
          <el-skeleton v-if="systemLoading" :rows="3" animated />
          <div v-else-if="systemInfo" class="system-info">
            <div class="system-item">
              <span>{{ t('dashboard.architecture') }}</span>
              <strong>{{ systemInfo.architectureVersion }}</strong>
            </div>
            <div class="system-item">
              <span>{{ t('dashboard.model') }}</span>
              <strong>{{ systemInfo.defaultModelProvider }}</strong>
            </div>
            <div class="system-item">
              <span>{{ t('dashboard.rag') }}</span>
              <el-tag size="small" :type="systemInfo.ragEnabled ? 'success' : 'info'">
                {{ systemInfo.ragEnabled ? t('common.on') : t('common.off') }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-drawer v-model="titleDrawerVisible" :title="t('dashboard.titleDrawerTitle')" size="520px">
      <TitleGeneratorPanel
        compact
        show-apply
        :initial-title="form.title"
        :initial-body="form.body"
        :initial-persona="form.persona"
        @apply="applyGeneratedTitle"
      />
      <div class="drawer-footer">
        <el-button text type="primary" @click="router.push('/titles')">
          {{ t('titles.openFullPage') }}
        </el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createAnalysis, fetchAnalysis, fetchAnalysisList } from '@/api/analysis'
import { fetchSystemInfo, type SystemInfo } from '@/api/system'
import SensitiveWordDetector from '@/components/workbench/SensitiveWordDetector.vue'
import WorkbenchInsightPanel from '@/components/workbench/WorkbenchInsightPanel.vue'
import TitleGeneratorPanel from '@/components/title/TitleGeneratorPanel.vue'
import { useAnalysisPoll } from '@/composables/useAnalysisPoll'
import { useWorkbenchAnalysis } from '@/composables/useWorkbenchAnalysis'
import { useUserStore } from '@/stores/user'
import type { AnalysisListItem, AnalysisScenario, AnalysisStatus, PersonaType } from '@/types/api'
import { saveAnalysisDraft } from '@/utils/analysisDraft'
import { averageReportScore, formatRelativeTime, isToday } from '@/utils/analysisDisplay'

const { t, locale } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const workbench = useWorkbenchAnalysis()

const form = reactive({
  scenario: 'draft' as AnalysisScenario,
  persona: 'agency' as PersonaType,
  title: '',
  body: '',
})

const analyzing = ref(false)
const recordsLoading = ref(true)
const systemLoading = ref(true)
const titleDrawerVisible = ref(false)
const recentRecords = ref<AnalysisListItem[]>([])
const systemInfo = ref<SystemInfo | null>(null)
const pollingId = ref<string | null>(null)

const poll = useAnalysisPoll(async () => {
  if (!pollingId.value) throw new Error('missing analysis id')
  const res = await fetchAnalysis(pollingId.value)
  return res.data.data
})

const dailyQuota = computed(() => userStore.profile?.dailyQuota ?? 10)
const usedToday = computed(() => recentRecords.value.filter((item) => isToday(item.createdAt)).length)
const remainingQuota = computed(() => Math.max(dailyQuota.value - usedToday.value, 0))
const quotaPercent = computed(() =>
  dailyQuota.value ? Math.min(Math.round((usedToday.value / dailyQuota.value) * 100), 100) : 0,
)
const quotaColor = computed(() => {
  if (quotaPercent.value >= 90) return '#ef4444'
  if (quotaPercent.value >= 70) return '#f59e0b'
  return '#ff2442'
})

const greeting = computed(() => {
  const name = userStore.profile?.displayName || userStore.profile?.email?.split('@')[0] || ''
  return t('dashboard.greeting', { name })
})

const personaLabel = computed(() => {
  const persona = userStore.profile?.defaultPersona ?? form.persona
  return t(`persona.${persona}`)
})

const tips = computed(() => [
  t('dashboard.tips.0'),
  t('dashboard.tips.1'),
  t('dashboard.tips.2'),
])

watch(
  () => userStore.profile?.defaultPersona,
  (persona) => {
    if (persona) form.persona = persona
  },
  { immediate: true },
)

let sensitiveScanTimer: ReturnType<typeof setTimeout> | null = null

watch(
  () => [form.title, form.body],
  () => {
    if (sensitiveScanTimer) clearTimeout(sensitiveScanTimer)
    sensitiveScanTimer = setTimeout(() => {
      workbench.scanSensitiveWords(form.title, form.body)
    }, 400)
  },
)

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

function statusTagType(status: AnalysisStatus) {
  const map: Record<AnalysisStatus, 'success' | 'warning' | 'danger' | 'info'> = {
    completed: 'success',
    processing: 'warning',
    pending: 'info',
    failed: 'danger',
  }
  return map[status]
}

function fillSample() {
  form.title = t('dashboard.sampleTitle')
  form.body = t('dashboard.sampleBody')
  workbench.scanSensitiveWords(form.title, form.body)
}

function applyHotTopic(topic: string) {
  form.title = topic
  ElMessage.success(t('workbench.topicApplied'))
}

function getAnalyzeParams() {
  return {
    title: form.title.trim(),
    body: form.body.trim(),
  }
}

async function refreshInsight() {
  if (!form.title.trim() && !form.body.trim()) {
    ElMessage.warning(t('dashboard.validationRequired'))
    return
  }
  await workbench.refresh(getAnalyzeParams())
}

async function regenerateTitles() {
  await workbench.regenerateTitles(getAnalyzeParams())
}

function openTitleDrawer() {
  titleDrawerVisible.value = true
}

function applyGeneratedTitle(title: string) {
  form.title = title
  titleDrawerVisible.value = false
}

async function loadRecentRecords() {
  recordsLoading.value = true
  try {
    const res = await fetchAnalysisList({ page: 1, size: 8 })
    recentRecords.value = res.data.data.items
  } catch {
    recentRecords.value = []
  } finally {
    recordsLoading.value = false
  }
}

async function loadSystemInfo() {
  systemLoading.value = true
  try {
    const res = await fetchSystemInfo()
    systemInfo.value = res.data.data
  } finally {
    systemLoading.value = false
  }
}

function goToReport(row: AnalysisListItem) {
  router.push(`/analysis/${row.id}`)
}

async function handleQuickAnalyze() {
  if (!form.title.trim() && !form.body.trim()) {
    ElMessage.warning(t('dashboard.validationRequired'))
    return
  }

  analyzing.value = true
  const insightPromise = workbench.analyze(getAnalyzeParams())
  try {
    const res = await createAnalysis({
      scenario: form.scenario,
      persona: form.persona,
      title: form.title.trim() || undefined,
      body: form.body.trim() || undefined,
    })
    const { id } = res.data.data
    pollingId.value = id
    const ok = await poll.start()
    await insightPromise
    if (ok) {
      ElMessage.success(t('dashboard.analyzeSuccess'))
      await router.push(`/analysis/${id}`)
      return
    }
    ElMessage.error(poll.error.value || t('dashboard.analyzeFailed'))
    await router.push(`/analysis/${id}`)
  } catch {
    await insightPromise
    saveAnalysisDraft({ ...form })
    ElMessage.info(t('dashboard.draftSaved'))
    await router.push({ name: 'analysis-new' })
  } finally {
    analyzing.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadRecentRecords(), loadSystemInfo()])
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-row {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 16px;
}

.welcome-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.welcome-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
  color: #111827;
}

.welcome-desc {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.quota-card {
  width: 280px;
  flex-shrink: 0;
}

.quota-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 13px;
  color: #6b7280;
}

.quota-numbers {
  margin-bottom: 8px;
}

.quota-used {
  font-size: 28px;
  font-weight: 700;
  color: #ff2442;
}

.quota-sep,
.quota-total {
  font-size: 16px;
  color: #9ca3af;
}

.quota-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #9ca3af;
}

.section-card :deep(.el-card__header) {
  padding-bottom: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 200px;
  gap: 16px;
}

.workbench-layout {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 20px;
  align-items: start;
}

.workbench-main {
  min-width: 0;
}

.workbench-insight {
  position: sticky;
  top: 16px;
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.records-table {
  cursor: pointer;
}

.record-title {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.score-badge {
  font-weight: 700;
  color: #ff2442;
}

.score-empty {
  color: #d1d5db;
}

.tips-list {
  margin: 0;
  padding-left: 18px;
  color: #4b5563;
  line-height: 1.8;
}

.system-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.system-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  color: #6b7280;
}

.drawer-footer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
  text-align: center;
}

@media (max-width: 960px) {
  .welcome-row {
    flex-direction: column;
  }

  .quota-card {
    width: 100%;
  }

  .workbench-layout {
    grid-template-columns: 1fr;
  }

  .workbench-insight {
    position: static;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .bottom-row .el-col {
    max-width: 100%;
    flex: 0 0 100%;
    margin-bottom: 16px;
  }
}
</style>
