<template>
  <el-drawer
    :model-value="visible"
    :title="t('report.bodyDrawerTitle')"
    size="600px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-form label-position="top" class="body-form">
      <el-form-item :label="t('report.bodyGoal')">
        <el-select v-model="localRequest.goal" :disabled="loading">
          <el-option
            v-for="goal in goalOptions"
            :key="goal"
            :label="t(`dashboard.titleGoals.${goal}`)"
            :value="goal"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="t('report.bodyTone')">
        <el-select v-model="localRequest.tone" :disabled="loading">
          <el-option value="default" :label="t('report.bodyToneDefault')" />
          <el-option value="more_anxiety" :label="t('report.bodyToneAnxiety')" />
          <el-option value="more_professional" :label="t('report.bodyToneProfessional')" />
          <el-option value="more_friendly" :label="t('report.bodyToneFriendly')" />
        </el-select>
      </el-form-item>

      <el-form-item :label="t('report.bodyLength')">
        <el-slider
          v-model="localRequest.maxLength"
          :min="500"
          :max="1500"
          :step="100"
          :disabled="loading"
          show-input
        />
      </el-form-item>
    </el-form>

    <div v-if="loading" class="drawer-loading">
      <el-skeleton :rows="8" animated />
      <p class="loading-hint">{{ t('report.bodyGenerateLoading') }}</p>
    </div>

    <template v-else-if="body">
      <el-form-item :label="t('analysis.body')">
        <el-input v-model="editableBody" type="textarea" :rows="16" />
      </el-form-item>

      <div v-if="body.structureOutline.length" class="outline-section">
        <h4>{{ t('report.structureOutline') }}</h4>
        <ol class="outline-list">
          <li v-for="item in body.structureOutline" :key="item.section">
            <strong>{{ sectionLabel(item.section) }}：</strong>{{ item.summary }}
          </li>
        </ol>
      </div>

      <p v-if="body.cta" class="cta-hint">
        <strong>{{ t('workbench.structureCta') }}：</strong>{{ body.cta }}
      </p>

      <p class="meta-hint">
        {{ t('report.bodyWordCount', { count: body.wordCount }) }}
      </p>

      <el-alert
        v-if="body.complianceWarnings.length"
        type="warning"
        :closable="false"
        show-icon
        class="compliance-alert"
      >
        <ul class="warning-list">
          <li v-for="(w, i) in body.complianceWarnings" :key="i">
            {{ w.matchedText }} — {{ w.suggestion }}
          </li>
        </ul>
      </el-alert>
    </template>

    <el-empty v-else :description="t('report.bodyEmpty')" />

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="$emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="loading" :disabled="!body" @click="copyBody">
          {{ t('report.copyBody') }}
        </el-button>
        <el-button :loading="loading" @click="$emit('generate', { ...localRequest })">
          {{ body ? t('report.regenerateBody') : t('report.generateBody') }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type {
  BodyGenerateRequest,
  BodyGenerateResponse,
  BodyStructureSection,
} from '@/api/analysis'
import type { TitleGenerateGoal } from '@/types/api'

const props = defineProps<{
  visible: boolean
  loading: boolean
  body: BodyGenerateResponse | null
}>()

defineEmits<{
  'update:visible': [value: boolean]
  generate: [value: BodyGenerateRequest]
}>()

const { t } = useI18n()
const editableBody = ref('')
const localRequest = reactive<Required<Omit<BodyGenerateRequest, 'keywords'>>>({
  goal: 'high_conversion',
  tone: 'default',
  maxLength: 900,
})
const goalOptions: TitleGenerateGoal[] = [
  'high_ctr',
  'high_collect',
  'high_conversion',
  'anxiety',
  'offer',
  'info_gap',
]

watch(
  () => props.body,
  (body) => {
    if (body) {
      editableBody.value = body.body
      localRequest.goal = body.goal
    }
  },
  { immediate: true },
)

function sectionLabel(section: BodyStructureSection['section']) {
  const labels: Record<BodyStructureSection['section'], string> = {
    hook: t('report.bodySectionHook'),
    problem_amplification: t('report.bodySectionProblem'),
    real_experience: t('report.bodySectionExperience'),
    result_showcase: t('report.bodySectionResult'),
    cta: t('report.bodySectionCta'),
  }
  return labels[section]
}

async function copyBody() {
  try {
    await navigator.clipboard.writeText(editableBody.value)
    ElMessage.success(t('report.copyBodySuccess'))
  } catch {
    ElMessage.error(t('titles.copyFailed'))
  }
}
</script>

<style scoped>
.body-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}

.body-form :deep(.el-form-item:last-child) {
  grid-column: 1 / -1;
}

.drawer-loading {
  padding: 8px 0;
}

.loading-hint {
  margin-top: 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

.outline-section h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #374151;
}

.outline-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.7;
  color: #6b7280;
}

.cta-hint,
.meta-hint {
  margin: 16px 0 0;
  font-size: 13px;
  color: #6b7280;
}

.compliance-alert {
  margin-top: 16px;
}

.warning-list {
  margin: 0;
  padding-left: 16px;
  font-size: 13px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 640px) {
  .body-form {
    grid-template-columns: 1fr;
  }
}
</style>
