<template>
  <el-drawer
    :model-value="visible"
    :title="t('report.optimizeDraftTitle')"
    size="560px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <div v-if="loading" class="drawer-loading">
      <el-skeleton :rows="8" animated />
      <p class="loading-hint">{{ t('report.optimizeDraftLoading') }}</p>
    </div>

    <template v-else-if="draft">
      <el-form-item :label="t('analysis.title')">
        <el-input v-model="editableTitle" />
      </el-form-item>
      <el-form-item :label="t('analysis.body')">
        <el-input v-model="editableBody" type="textarea" :rows="14" />
      </el-form-item>

      <div v-if="draft.structureOutline.length" class="outline-section">
        <h4>{{ t('report.structureOutline') }}</h4>
        <ol class="outline-list">
          <li v-for="(item, index) in draft.structureOutline" :key="index">{{ item }}</li>
        </ol>
      </div>

      <p v-if="draft.cta" class="cta-hint">
        <strong>{{ t('workbench.structureCta') }}：</strong>{{ draft.cta }}
      </p>

      <el-alert
        v-if="draft.complianceWarnings.length"
        type="warning"
        :closable="false"
        show-icon
        class="compliance-alert"
      >
        <ul class="warning-list">
          <li v-for="(w, i) in draft.complianceWarnings" :key="i">
            {{ w.matchedText }} — {{ w.suggestion }}
          </li>
        </ul>
      </el-alert>
    </template>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="$emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="loading" :disabled="!draft" @click="copyDraft">
          {{ t('report.copyDraft') }}
        </el-button>
        <el-button :loading="loading" @click="$emit('regenerate')">
          {{ t('report.regenerateDraft') }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { OptimizeDraftResponse } from '@/api/analysis'

const props = defineProps<{
  visible: boolean
  loading: boolean
  draft: OptimizeDraftResponse | null
}>()

defineEmits<{
  'update:visible': [value: boolean]
  regenerate: []
}>()

const { t } = useI18n()
const editableTitle = ref('')
const editableBody = ref('')

watch(
  () => props.draft,
  (draft) => {
    if (draft) {
      editableTitle.value = draft.optimizedTitle
      editableBody.value = draft.optimizedBody
    }
  },
  { immediate: true },
)

async function copyDraft() {
  const text = `${editableTitle.value}\n\n${editableBody.value}`
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('report.copyDraftSuccess'))
  } catch {
    ElMessage.error(t('titles.copyFailed'))
  }
}
</script>

<style scoped>
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

.cta-hint {
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
</style>
