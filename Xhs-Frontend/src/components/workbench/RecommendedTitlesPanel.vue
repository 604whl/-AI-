<template>
  <div class="recommended-titles">
    <div class="card-header">
      <div class="header-left">
        <el-icon class="header-icon"><EditPen /></el-icon>
        <span class="header-title">{{ t('workbench.recommendedTitles') }}</span>
      </div>
      <el-button
        text
        size="small"
        :loading="regenerating"
        :disabled="!canRegenerate"
        @click="$emit('regenerate')"
      >
        <el-icon><RefreshRight /></el-icon>
        {{ t('workbench.regenerate') }}
      </el-button>
    </div>

    <div v-if="titles.length" class="title-list">
      <div
        v-for="(item, index) in titles"
        :key="index"
        class="title-card"
      >
        <div class="title-rank">{{ index + 1 }}</div>
        <div class="title-content">
          <p class="title-text">{{ item.title }}</p>
          <div class="title-meta">
            <el-tag size="small" type="danger" effect="plain" round>
              CTR {{ item.ctr }}%
            </el-tag>
            <el-progress
              :percentage="item.ctr"
              :stroke-width="4"
              :show-text="false"
              color="#ff2442"
              class="ctr-bar"
            />
          </div>
        </div>
        <div class="title-actions">
          <el-tooltip :content="t('dashboard.copy')" placement="top">
            <el-button
              circle
              size="small"
              text
              @click="copyTitle(item.title)"
            >
              <el-icon><CopyDocument /></el-icon>
            </el-button>
          </el-tooltip>
          <el-button size="small" type="primary" plain @click="applyTitle(item.title)">
            {{ t('titles.applyToAnalysis') }}
          </el-button>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <p>{{ t('workbench.titlesEmpty') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { CopyDocument, EditPen, RefreshRight } from '@element-plus/icons-vue'
import type { RecommendedTitle } from '@/types/workbench'

defineProps<{
  titles: RecommendedTitle[]
  regenerating: boolean
  canRegenerate: boolean
}>()

const emit = defineEmits<{
  regenerate: []
  apply: [title: string]
}>()

const { t } = useI18n()

async function copyTitle(title: string) {
  try {
    await navigator.clipboard.writeText(title)
    ElMessage.success(t('dashboard.copied'))
  } catch {
    ElMessage.error(t('titles.copyFailed'))
  }
}

function applyTitle(title: string) {
  emit('apply', title)
  ElMessage.success(t('workbench.topicApplied'))
}
</script>

<style scoped>
.recommended-titles {
  padding: 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-icon {
  font-size: 18px;
  color: #6366f1;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.title-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.title-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border-radius: 10px;
  background: linear-gradient(135deg, #fafafa 0%, #f9fafb 100%);
  border: 1px solid #f3f4f6;
  transition: all 0.2s;
}

.title-card:hover {
  border-color: #fecdd3;
  box-shadow: 0 2px 8px rgba(255, 36, 66, 0.08);
  transform: translateY(-1px);
}

.title-rank {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #ff2442;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.title-content {
  flex: 1;
  min-width: 0;
}

.title-text {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
  line-height: 1.5;
}

.title-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ctr-bar {
  flex: 1;
  max-width: 80px;
}

.title-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
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
