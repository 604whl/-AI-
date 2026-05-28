<template>
  <div class="hot-points">
    <div class="card-header">
      <div class="header-left">
        <el-icon class="header-icon"><Aim /></el-icon>
        <span class="header-title">{{ t('workbench.hotPointsTitle') }}</span>
        <el-tag size="small" effect="dark" type="warning" round>AI</el-tag>
      </div>
    </div>

    <div v-if="points.length" class="points-list">
      <div
        v-for="(item, index) in points"
        :key="index"
        class="point-item"
        :style="{ animationDelay: `${index * 0.1}s` }"
      >
        <div class="point-keyword">
          <el-tag effect="dark" type="danger" size="small" class="keyword-tag">
            {{ item.point }}
          </el-tag>
          <el-tag size="small" effect="plain" class="type-tag">
            {{ item.type }}
          </el-tag>
        </div>
        <div class="point-details">
          <div class="detail-row">
            <span class="detail-label">{{ t('workbench.userPsychology') }}</span>
            <span class="detail-value">{{ item.psychology }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('workbench.emotionType') }}</span>
            <span class="detail-value emotion-value">{{ item.emotion }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <p>{{ t('workbench.hotPointsEmpty') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Aim } from '@element-plus/icons-vue'
import type { HotPoint } from '@/types/workbench'

defineProps<{
  points: HotPoint[]
}>()

const { t } = useI18n()
</script>

<style scoped>
.hot-points {
  padding: 16px;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff 0%, #fffbeb 100%);
  border: 1px solid #fde68a;
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
  color: #f59e0b;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.points-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.point-item {
  padding: 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid #fef3c7;
  animation: fadeInUp 0.4s ease both;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.point-keyword {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.keyword-tag {
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(255, 36, 66, 0.25);
}

.type-tag {
  border-color: #fbbf24;
  color: #b45309;
  background: #fffbeb;
}

.point-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.detail-label {
  flex-shrink: 0;
  color: #9ca3af;
  min-width: 56px;
}

.detail-value {
  color: #4b5563;
}

.emotion-value {
  color: #dc2626;
  font-weight: 500;
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
