<template>
  <div class="structure-breakdown">
    <div class="card-header">
      <div class="header-left">
        <el-icon class="header-icon"><Grid /></el-icon>
        <span class="header-title">{{ t('workbench.structureTitle') }}</span>
        <el-tag size="small" effect="dark" type="primary" round>AI</el-tag>
      </div>
    </div>

    <template v-if="structure">
      <div class="structure-section">
        <h5 class="section-label">{{ t('workbench.structureHook') }}</h5>
        <p class="section-text">{{ structure.hook }}</p>
      </div>

      <div class="structure-section">
        <h5 class="section-label">{{ t('workbench.structureEmotion') }}</h5>
        <div class="emotion-steps">
          <div
            v-for="(step, index) in structure.emotionArc"
            :key="index"
            class="emotion-step"
          >
            <span class="step-index">{{ index + 1 }}</span>
            <span class="step-text">{{ step }}</span>
          </div>
        </div>
      </div>

      <div v-if="structure.savePoints.length" class="structure-section">
        <h5 class="section-label">{{ t('workbench.structureSavePoints') }}</h5>
        <ul class="save-list">
          <li v-for="(point, index) in structure.savePoints" :key="index">{{ point }}</li>
        </ul>
      </div>

      <div class="structure-section cta-section">
        <h5 class="section-label">{{ t('workbench.structureCta') }}</h5>
        <p class="section-text cta-text">{{ structure.cta.text }}</p>
        <div class="cta-meta">
          <el-rate
            :model-value="structure.cta.rating"
            disabled
            show-score
            score-template="{value} / 5"
          />
          <span class="cta-comment">{{ structure.cta.comment }}</span>
        </div>
      </div>
    </template>

    <div v-else class="empty-state">
      <p>{{ t('workbench.structureEmpty') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Grid } from '@element-plus/icons-vue'
import type { StructureBreakdown } from '@/types/workbench'

defineProps<{
  structure: StructureBreakdown | null
}>()

const { t } = useI18n()
</script>

<style scoped>
.structure-breakdown {
  padding: 16px;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff 0%, #eff6ff 100%);
  border: 1px solid #bfdbfe;
}

.card-header {
  margin-bottom: 14px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-icon {
  font-size: 18px;
  color: #2563eb;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.structure-section {
  margin-bottom: 14px;
}

.structure-section:last-child {
  margin-bottom: 0;
}

.section-label {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.section-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #374151;
}

.emotion-steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.emotion-step {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid #dbeafe;
}

.step-index {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #2563eb;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-text {
  font-size: 13px;
  color: #1f2937;
}

.save-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: #374151;
  line-height: 1.7;
}

.cta-section {
  padding-top: 12px;
  border-top: 1px dashed #bfdbfe;
}

.cta-text {
  font-weight: 500;
  color: #1e40af;
}

.cta-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}

.cta-comment {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}

.empty-state {
  text-align: center;
  padding: 16px;
  color: #9ca3af;
  font-size: 13px;
}

.empty-state p {
  margin: 0;
}
</style>
