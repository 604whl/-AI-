<template>
  <div class="hot-topics">
    <div class="card-header">
      <div class="header-left">
        <el-icon class="header-icon"><TrendCharts /></el-icon>
        <span class="header-title">{{ t('workbench.hotTopicsTitle') }}</span>
        <el-tag size="small" type="danger" effect="plain" round>HOT</el-tag>
      </div>
    </div>

    <div v-if="topics.length" class="topics-wrap">
      <button
        v-for="(topic, index) in topics"
        :key="index"
        type="button"
        class="topic-tag"
        @click="$emit('select', topic)"
      >
        <span class="topic-fire">🔥</span>
        {{ topic }}
      </button>
    </div>

    <div v-else class="empty-state">
      <p>{{ t('workbench.hotTopicsEmpty') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { TrendCharts } from '@element-plus/icons-vue'

defineProps<{
  topics: string[]
}>()

defineEmits<{
  select: [topic: string]
}>()

const { t } = useI18n()
</script>

<style scoped>
.hot-topics {
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
  color: #ff2442;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.topics-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.topic-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border: 1px solid #fecdd3;
  border-radius: 20px;
  background: linear-gradient(135deg, #fff1f2 0%, #fff 100%);
  color: #be123c;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.topic-tag:hover {
  background: #ff2442;
  color: #fff;
  border-color: #ff2442;
  transform: translateY(-2px) scale(1.03);
  box-shadow: 0 4px 12px rgba(255, 36, 66, 0.3);
}

.topic-tag:active {
  transform: translateY(0) scale(0.98);
}

.topic-fire {
  font-size: 12px;
  transition: transform 0.25s;
}

.topic-tag:hover .topic-fire {
  transform: scale(1.2);
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
