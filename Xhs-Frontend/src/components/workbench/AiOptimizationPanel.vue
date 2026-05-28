<template>
  <div class="ai-optimization">
    <div class="card-header">
      <div class="header-left">
        <el-icon class="header-icon"><Opportunity /></el-icon>
        <span class="header-title">{{ t('workbench.optimizationTitle') }}</span>
        <el-tag size="small" effect="dark" type="primary" round>AI</el-tag>
      </div>
    </div>

    <template v-if="hasData && advice">
      <div class="section issues-section">
        <h5 class="section-title">
          <el-icon><Warning /></el-icon>
          {{ t('workbench.currentIssues') }}
        </h5>
        <ul class="issue-list">
          <li v-for="(issue, index) in advice.issues" :key="index">
            <span class="issue-index">{{ index + 1 }}</span>
            {{ issue }}
          </li>
        </ul>
      </div>

      <div class="section suggestions-section">
        <h5 class="section-title">
          <el-icon><MagicStick /></el-icon>
          {{ t('workbench.optimizationSuggestions') }}
        </h5>
        <div class="suggestion-tags">
          <el-tag
            v-for="(item, index) in advice.suggestions"
            :key="index"
            type="success"
            effect="plain"
            class="suggestion-tag"
          >
            {{ item }}
          </el-tag>
        </div>
      </div>
    </template>

    <div v-else class="empty-state">
      <p>{{ t('workbench.optimizationEmpty') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { MagicStick, Opportunity, Warning } from '@element-plus/icons-vue'
import type { AiOptimizationAdvice } from '@/types/workbench'

defineProps<{
  advice: AiOptimizationAdvice | null
  hasData: boolean
}>()

const { t } = useI18n()
</script>

<style scoped>
.ai-optimization {
  padding: 16px;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff 0%, #f0fdf4 100%);
  border: 1px solid #bbf7d0;
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
  color: #059669;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.section {
  margin-bottom: 14px;
}

.section:last-child {
  margin-bottom: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.issues-section .section-title {
  color: #b45309;
}

.issue-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.issue-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border-radius: 8px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  font-size: 13px;
  color: #78350f;
  line-height: 1.5;
}

.issue-index {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #f59e0b;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.suggestion-tags {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.suggestion-tag {
  width: 100%;
  height: auto;
  padding: 8px 12px;
  white-space: normal;
  line-height: 1.5;
  justify-content: flex-start;
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
