<template>
  <el-card shadow="never" class="report-module actions-panel">
    <template #header>
      <span class="module-title">{{ t('report.moduleActions') }}</span>
    </template>

    <el-alert
      v-if="complianceWarnings.length"
      type="error"
      :closable="false"
      show-icon
      class="compliance-banner"
    >
      <p class="compliance-title">{{ t('report.complianceAlert') }}</p>
      <ul class="compliance-list">
        <li v-for="(w, i) in complianceWarnings" :key="i">
          <strong>{{ w.matchedText }}</strong> — {{ w.suggestion }}
        </li>
      </ul>
    </el-alert>

    <div class="action-buttons">
      <el-button type="primary" size="large" @click="$emit('optimize-draft')">
        {{ t('report.generateOptimizedDraft') }}
      </el-button>
      <el-button size="large" @click="$emit('generate-titles')">
        {{ t('report.generateTitles') }}
      </el-button>
      <el-button size="large" @click="$emit('generate-body')">
        {{ t('report.generateBody') }}
      </el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { AnalysisReport } from '@/types/api'

defineProps<{
  complianceWarnings: AnalysisReport['complianceWarnings']
}>()

defineEmits<{
  'optimize-draft': []
  'generate-titles': []
  'generate-body': []
}>()

const { t } = useI18n()
</script>

<style scoped>
.report-module {
  border-radius: 12px;
}

.actions-panel {
  background: linear-gradient(180deg, #fff 0%, #fef2f2 100%);
  border: 1px solid #fecaca;
}

.module-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.compliance-banner {
  margin-bottom: 16px;
}

.compliance-title {
  margin: 0 0 8px;
  font-weight: 600;
}

.compliance-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.6;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
