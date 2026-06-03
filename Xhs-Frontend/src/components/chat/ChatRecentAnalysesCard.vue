<template>
  <el-card shadow="never" class="recent-card">
    <template #header>
      <span>{{ t('chat.cardRecentAnalyses') }}</span>
      <span v-if="total != null" class="total">{{ t('chat.recentAnalysesTotal', { count: total }) }}</span>
    </template>

    <el-empty v-if="!items.length" :description="t('chat.recentAnalysesEmpty')" />

    <ul v-else class="item-list">
      <li v-for="item in items" :key="item.taskId">
        <div class="item-head">
          <router-link v-if="item.taskId" :to="`/analysis/${item.taskId}`" class="title">
            {{ item.title || t('chat.untitledAnalysis') }}
          </router-link>
          <span v-else class="title">{{ item.title || t('chat.untitledAnalysis') }}</span>
          <el-tag v-if="item.status" size="small" :type="statusType(item.status)">{{ item.status }}</el-tag>
        </div>
        <div class="meta">
          <span v-if="item.overallScore != null">{{ t('chat.viralScore') }}: {{ item.overallScore }}</span>
          <span v-if="item.scenario">{{ item.scenario }}</span>
          <span v-if="item.createdAt">{{ formatTime(item.createdAt) }}</span>
        </div>
      </li>
    </ul>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  payload?: Record<string, unknown>
}>()

const { t } = useI18n()

interface RecentItem {
  taskId?: string
  title?: string
  scenario?: string
  status?: string
  createdAt?: string
  overallScore?: number
}

const items = computed(() => {
  const raw = props.payload?.items
  if (!Array.isArray(raw)) return []
  return raw as RecentItem[]
})

const total = computed(() => {
  const value = props.payload?.total
  return typeof value === 'number' ? value : null
})

function statusType(status: string) {
  if (status === 'completed') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'processing') return 'warning'
  return 'info'
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}
</script>

<style scoped>
.recent-card {
  border: 1px solid #dbeafe;
  background: #eff6ff;
}
.recent-card :deep(.el-card__header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.total {
  font-size: 12px;
  color: #64748b;
  font-weight: normal;
}
.item-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.item-list li {
  padding: 10px 0;
  border-bottom: 1px dashed #bfdbfe;
}
.item-list li:last-child {
  border-bottom: none;
}
.item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.title {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: #1e40af;
  text-decoration: none;
}
.title:hover {
  text-decoration: underline;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 11px;
  color: #64748b;
}
</style>
