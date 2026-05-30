<template>
  <el-card shadow="never" class="report-module">
    <template #header>
      <span class="module-title">{{ t('report.moduleOptimization') }}</span>
    </template>

    <el-tabs v-if="report" v-model="activeTab">
      <el-tab-pane :label="t('report.tabTitle')" name="title">
        <div
          v-for="(pair, index) in report.optimizations.title"
          :key="index"
          class="title-pair"
        >
          <div class="pair-row">
            <span class="pair-label">{{ t('workbench.originalSentence') }}</span>
            <span class="pair-text">{{ pair.original }}</span>
          </div>
          <div class="pair-arrow">↓</div>
          <div class="pair-row optimized">
            <span class="pair-label">{{ t('workbench.optimizedSentence') }}</span>
            <span class="pair-text">{{ pair.optimized }}</span>
          </div>
        </div>
        <el-empty
          v-if="!report.optimizations.title.length"
          :description="t('report.noOptimization')"
          :image-size="48"
        />
      </el-tab-pane>

      <el-tab-pane :label="t('report.tabStructure')" name="structure">
        <ul class="bullet-list">
          <li v-for="(item, index) in report.optimizations.structure" :key="index">{{ item }}</li>
        </ul>
        <el-empty
          v-if="!report.optimizations.structure.length"
          :description="t('report.noOptimization')"
          :image-size="48"
        />
      </el-tab-pane>

      <el-tab-pane :label="t('report.tabEmotion')" name="emotion">
        <ul class="bullet-list">
          <li v-for="(item, index) in report.optimizations.emotion" :key="index">{{ item }}</li>
        </ul>
        <el-empty
          v-if="!report.optimizations.emotion.length"
          :description="t('report.noOptimization')"
          :image-size="48"
        />
      </el-tab-pane>

      <el-tab-pane :label="t('report.tabCta')" name="cta">
        <ul class="bullet-list">
          <li v-for="(item, index) in report.optimizations.cta" :key="index">{{ item }}</li>
        </ul>
        <el-empty
          v-if="!report.optimizations.cta.length"
          :description="t('report.noOptimization')"
          :image-size="48"
        />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AnalysisReport } from '@/types/api'

defineProps<{
  report: AnalysisReport | null
}>()

const { t } = useI18n()
const activeTab = ref('title')
</script>

<style scoped>
.report-module {
  border-radius: 12px;
}

.module-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.title-pair {
  padding: 14px;
  margin-bottom: 12px;
  border-radius: 10px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
}

.pair-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pair-label {
  font-size: 11px;
  font-weight: 600;
  color: #9ca3af;
}

.pair-text {
  font-size: 14px;
  line-height: 1.5;
  color: #374151;
}

.pair-row.optimized .pair-text {
  color: #059669;
  font-weight: 500;
}

.pair-arrow {
  text-align: center;
  padding: 6px 0;
  color: #d1d5db;
}

.bullet-list {
  margin: 0;
  padding-left: 20px;
  line-height: 1.8;
  color: #374151;
  font-size: 14px;
}
</style>
