<template>
  <el-card shadow="never" class="url-card">
    <template #header>
      <span>{{ t('chat.cardFetchedUrl') }}</span>
    </template>
    <a v-if="url" :href="url" target="_blank" rel="noopener" class="url">{{ url }}</a>
    <p class="text">{{ text }}</p>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  payload?: Record<string, unknown>
}>()

const { t } = useI18n()

const url = computed(() => String(props.payload?.url ?? ''))
const text = computed(() => String(props.payload?.text ?? ''))
</script>

<style scoped>
.url-card {
  border: 1px solid #e5e7eb;
  background: #f9fafb;
}
.url {
  display: block;
  font-size: 12px;
  color: #2563eb;
  margin-bottom: 8px;
  word-break: break-all;
}
.text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #374151;
  white-space: pre-wrap;
  max-height: 200px;
  overflow-y: auto;
}
</style>
