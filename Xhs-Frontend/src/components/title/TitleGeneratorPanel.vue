<template>
  <div class="title-generator" :class="{ compact, 'split-layout': splitLayout }">
    <el-alert
      v-if="analysisContext"
      type="info"
      :closable="false"
      show-icon
      class="context-alert"
    >
      {{ t('titles.linkedAnalysis', { title: analysisContext.title || t('dashboard.untitled') }) }}
    </el-alert>

    <div class="generator-body" :class="{ 'is-split': splitLayout }">
      <div class="generator-form">
        <el-form label-position="top" @submit.prevent="handleGenerate">
          <el-row :gutter="16">
            <el-col :xs="24" :sm="splitLayout || compact ? 24 : 12">
              <el-form-item :label="t('titles.goal')">
                <el-select v-model="form.goal" style="width: 100%">
                  <el-option
                    v-for="goal in TITLE_GOALS"
                    :key="goal"
                    :label="t(`dashboard.titleGoals.${goal}`)"
                    :value="goal"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="splitLayout || compact ? 24 : 12">
              <el-form-item :label="t('analysis.persona')">
                <el-select v-model="form.persona" style="width: 100%">
                  <el-option :label="t('persona.agency')" value="agency" />
                  <el-option :label="t('persona.mentor')" value="mentor" />
                  <el-option :label="t('persona.senior')" value="senior" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item :label="t('titles.count')">
            <el-slider v-model="form.count" :min="5" :max="10" :step="1" show-stops show-input />
          </el-form-item>

          <el-form-item :label="t('analysis.title')">
            <el-input
              v-model="form.title"
              maxlength="100"
              show-word-limit
              :placeholder="t('dashboard.titlePlaceholder')"
              :disabled="!!lockedAnalysisId"
            />
          </el-form-item>

          <el-form-item :label="t('analysis.body')">
            <el-input
              v-model="form.body"
              type="textarea"
              :rows="compact ? 4 : splitLayout ? 8 : 6"
              maxlength="10000"
              show-word-limit
              :placeholder="t('dashboard.bodyPlaceholder')"
              :disabled="!!lockedAnalysisId"
            />
          </el-form-item>

          <div class="form-actions">
            <el-button type="primary" :loading="generation.loading.value" @click="handleGenerate">
              {{ t('dashboard.generateTitles') }}
            </el-button>
            <el-button v-if="!compact" text @click="fillSample">{{ t('dashboard.trySample') }}</el-button>
            <el-button v-if="lockedAnalysisId" text @click="clearAnalysisLink">
              {{ t('titles.clearLink') }}
            </el-button>
          </div>
        </el-form>
      </div>

      <div v-if="splitLayout" class="generator-results">
        <TitleResultsList
          :titles="generation.titles.value"
          :loading="generation.loading.value"
          :loading-step="generation.loadingStep.value"
          :has-generated="hasGenerated"
          :prompt-version="generation.promptVersion.value"
          @copy="copyTitle"
          @apply="applyToAnalysis"
        />
      </div>
    </div>

    <TitleResultsList
      v-if="!splitLayout && showResults"
      :titles="generation.titles.value"
      :loading="generation.loading.value"
      :loading-step="generation.loadingStep.value"
      :has-generated="hasGenerated"
      :prompt-version="generation.promptVersion.value"
      @copy="copyTitle"
      @apply="applyToAnalysis"
    />

    <div v-else-if="!splitLayout && hasGenerated && !generation.loading.value && !generation.hasResult.value">
      <el-empty :description="t('titles.empty')" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchAnalysis } from '@/api/analysis'
import { useTitleGeneration } from '@/composables/useTitleGeneration'
import TitleResultsList from '@/components/title/TitleResultsList.vue'
import { TITLE_GOALS } from '@/constants/titleGoals'
import type { PersonaType, TitleGenerateGoal } from '@/types/api'
import type { ScoredTitle } from '@/types/title'
import { saveAnalysisDraft } from '@/utils/analysisDraft'

const props = withDefaults(
  defineProps<{
    compact?: boolean
    splitLayout?: boolean
    showApply?: boolean
    analysisId?: string
    initialTitle?: string
    initialBody?: string
    initialPersona?: PersonaType
  }>(),
  {
    compact: false,
    splitLayout: false,
    showApply: false,
    initialPersona: 'agency',
  },
)

const emit = defineEmits<{
  apply: [title: string]
}>()

const { t } = useI18n()
const router = useRouter()
const generation = useTitleGeneration()

const form = reactive({
  goal: 'high_ctr' as TitleGenerateGoal,
  persona: props.initialPersona,
  count: 8,
  title: props.initialTitle ?? '',
  body: props.initialBody ?? '',
})

const lockedAnalysisId = ref<string | undefined>(props.analysisId)
const analysisContext = ref<{ title?: string } | null>(null)
const hasGenerated = ref(false)

const showResults = computed(
  () => generation.loading.value || generation.hasResult.value || hasGenerated.value,
)

watch(
  () => props.initialTitle,
  (value) => {
    if (value !== undefined && !lockedAnalysisId.value) {
      form.title = value
    }
  },
)

watch(
  () => props.initialBody,
  (value) => {
    if (value !== undefined && !lockedAnalysisId.value) {
      form.body = value
    }
  },
)

watch(
  () => props.initialPersona,
  (value) => {
    if (value) form.persona = value
  },
)

watch(
  () => props.analysisId,
  (value) => {
    if (value) {
      lockedAnalysisId.value = value
      loadAnalysisContext(value)
    }
  },
)

onMounted(() => {
  if (props.analysisId) {
    loadAnalysisContext(props.analysisId)
  }
})

async function loadAnalysisContext(id: string) {
  try {
    const res = await fetchAnalysis(id)
    const detail = res.data.data
    analysisContext.value = { title: detail.title }
    form.title = detail.title ?? ''
    form.body = detail.body ?? ''
    if (detail.persona) form.persona = detail.persona
  } catch (err) {
    ElMessage.error(resolveErrorMessage(err, t('titles.loadAnalysisFailed')))
    lockedAnalysisId.value = undefined
    analysisContext.value = null
  }
}

function clearAnalysisLink() {
  lockedAnalysisId.value = undefined
  analysisContext.value = null
}

function fillSample() {
  form.title = t('dashboard.sampleTitle')
  form.body = t('dashboard.sampleBody')
}

function validateForm() {
  if (lockedAnalysisId.value) return true
  if (!form.title.trim() && form.body.trim().length < 10) {
    ElMessage.warning(t('dashboard.titleValidation'))
    return false
  }
  return true
}

async function handleGenerate() {
  if (!validateForm()) return

  hasGenerated.value = true

  try {
    await generation.generate(
      {
        goal: form.goal,
        count: form.count,
        persona: form.persona,
        title: form.title.trim() || undefined,
        body: form.body.trim() || undefined,
      },
      lockedAnalysisId.value,
    )
  } catch (err) {
    ElMessage.error(resolveErrorMessage(err, t('titles.generateFailed')))
  }
}

function resolveErrorMessage(err: unknown, fallback: string) {
  if (err instanceof Error && err.message) return err.message
  return fallback
}

async function copyTitle(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('dashboard.copied'))
  } catch {
    ElMessage.error(t('titles.copyFailed'))
  }
}

function applyToAnalysis(item: ScoredTitle) {
  if (props.showApply) {
    emit('apply', item.title)
    ElMessage.success(t('titles.applied'))
    return
  }

  saveAnalysisDraft({
    scenario: 'draft',
    persona: form.persona,
    title: item.title,
    body: form.body.trim() || '',
  })
  ElMessage.success(t('titles.applyToAnalysisSuccess'))
  router.push({ name: 'analysis-new' })
}

defineExpose({ fillSample, handleGenerate })
</script>

<style scoped>
.title-generator.compact .form-actions {
  margin-top: 0;
}

.context-alert {
  margin-bottom: 16px;
}

.generator-body.is-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  align-items: start;
}

.generator-results {
  padding-left: 24px;
  border-left: 1px solid var(--el-border-color-lighter);
  min-height: 400px;
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

@media (max-width: 992px) {
  .generator-body.is-split {
    grid-template-columns: 1fr;
  }

  .generator-results {
    padding-left: 0;
    border-left: none;
    padding-top: 20px;
    border-top: 1px solid var(--el-border-color-lighter);
  }
}
</style>
