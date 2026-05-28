<template>
  <div class="analysis-new-page">
    <section class="page-header">
      <div>
        <h1 class="page-title">{{ t('analysis.newTitle') }}</h1>
        <p class="page-desc">{{ t('analysis.newDesc') }}</p>
      </div>
      <el-tag effect="plain" type="danger" round class="ai-badge">
        <el-icon><DataAnalysis /></el-icon>
        {{ t('analysis.aiInsight') }}
      </el-tag>
    </section>

    <div class="workbench-layout">
      <el-card shadow="never" class="form-card">
        <el-form label-position="top" @submit.prevent="handleAnalyze">
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
              :rows="10"
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
              size="large"
              :loading="submitting || workbench.loading.value"
              @click="handleAnalyze"
            >
              {{ t('dashboard.analyzeNow') }}
            </el-button>
            <el-button text @click="fillSample">{{ t('dashboard.trySample') }}</el-button>
            <el-button
              v-if="workbench.hasResult.value"
              text
              @click="router.push('/titles')"
            >
              {{ t('dashboard.generateTitles') }}
            </el-button>
          </div>
        </el-form>

        <HotTopicsPanel
          class="bottom-topics"
          :topics="displayTopics"
          @select="applyHotTopic"
        />
      </el-card>

      <aside class="insight-aside">
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
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataAnalysis } from '@element-plus/icons-vue'
import { createAnalysis, fetchAnalysis } from '@/api/analysis'
import { MOCK_HOT_TOPICS } from '@/mocks/workbench'
import SensitiveWordDetector from '@/components/workbench/SensitiveWordDetector.vue'
import HotTopicsPanel from '@/components/workbench/HotTopicsPanel.vue'
import WorkbenchInsightPanel from '@/components/workbench/WorkbenchInsightPanel.vue'
import { useAnalysisPoll } from '@/composables/useAnalysisPoll'
import { useWorkbenchAnalysis } from '@/composables/useWorkbenchAnalysis'
import { useUserStore } from '@/stores/user'
import type { AnalysisScenario, PersonaType } from '@/types/api'
import { clearAnalysisDraft, loadAnalysisDraft } from '@/utils/analysisDraft'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const workbench = useWorkbenchAnalysis()

const form = reactive<{
  scenario: AnalysisScenario
  persona: PersonaType
  title: string
  body: string
}>({
  scenario: 'draft',
  persona: 'agency',
  title: '',
  body: '',
})

const submitting = ref(false)
const pollingId = ref<string | null>(null)

const poll = useAnalysisPoll(async () => {
  if (!pollingId.value) throw new Error('missing analysis id')
  const res = await fetchAnalysis(pollingId.value)
  return res.data.data
})

const displayTopics = computed(() =>
  workbench.hotTopics.value.length ? workbench.hotTopics.value : MOCK_HOT_TOPICS,
)

watch(
  () => [form.title, form.body],
  () => {
    workbench.scanSensitiveWords(form.title, form.body)
  },
  { immediate: true },
)

onMounted(() => {
  const draft = loadAnalysisDraft()
  if (draft) {
    form.scenario = draft.scenario
    form.persona = draft.persona
    form.title = draft.title
    form.body = draft.body
    clearAnalysisDraft()
    return
  }
  if (userStore.profile?.defaultPersona) {
    form.persona = userStore.profile.defaultPersona
  }
})

function getAnalyzeParams() {
  return { title: form.title.trim(), body: form.body.trim() }
}

function fillSample() {
  form.title = t('dashboard.sampleTitle')
  form.body = t('dashboard.sampleBody')
}

function applyHotTopic(topic: string) {
  form.title = topic
  ElMessage.success(t('workbench.topicApplied'))
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

async function handleAnalyze() {
  if (!form.title.trim() && !form.body.trim()) {
    ElMessage.warning(t('dashboard.validationRequired'))
    return
  }

  submitting.value = true
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
    ElMessage.success(t('analysis.mockAnalyzeDone'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.analysis-new-page {
  max-width: 1280px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
  color: #111827;
}

.page-desc {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  max-width: 520px;
}

.ai-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
}

.workbench-layout {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 16px;
  align-items: start;
}

.form-card {
  border-radius: 12px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 200px;
  gap: 16px;
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.bottom-topics {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.insight-aside {
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
}

@media (max-width: 1100px) {
  .workbench-layout {
    grid-template-columns: 1fr;
  }

  .insight-aside {
    position: static;
    max-height: none;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
