<template>
  <el-card shadow="never" class="search-card">
    <template #header>
      <span>{{ t('chat.cardWebSearch') }}</span>
    </template>
    <p v-if="query" class="query">{{ t('chat.searchQuery') }}：{{ query }}</p>
    <ul class="result-list">
      <li v-for="(item, i) in results" :key="i">
        <a v-if="item.url" :href="String(item.url)" target="_blank" rel="noopener" class="title">{{ item.title }}</a>
        <span v-else class="title">{{ item.title }}</span>
        <p class="snippet">{{ item.snippet }}</p>
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

const query = computed(() => String(props.payload?.query ?? ''))
const results = computed(() => {
  const raw = props.payload?.results
  if (!Array.isArray(raw)) return []
  return raw as Array<{ title?: string; url?: string; snippet?: string }>
})
</script>

<style scoped>
.search-card {
  border: 1px solid #bae6fd;
  background: #f0f9ff;
}
.query {
  margin: 0 0 10px;
  font-size: 13px;
  color: #0369a1;
}
.result-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.result-list li {
  padding: 8px 0;
  border-bottom: 1px dashed #bae6fd;
}
.title {
  font-weight: 600;
  font-size: 13px;
  color: #0c4a6e;
  text-decoration: none;
}
.snippet {
  margin: 4px 0 0;
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
}
</style>
