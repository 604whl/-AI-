<template>
  <el-card shadow="never" class="compliance-card">
    <template #header>
      <span>{{ t('chat.cardCompliance') }}</span>
    </template>
    <el-empty v-if="!warnings.length" :description="t('chat.complianceClear')" :image-size="48" />
    <ul v-else class="warning-list">
      <li v-for="(w, i) in warnings" :key="i">
        <p class="matched">{{ w.matchedText }}</p>
        <p class="suggestion">{{ w.suggestion }}</p>
      </li>
    </ul>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ComplianceCardPayload } from '@/types/chat'

const props = defineProps<{
  payload?: Record<string, unknown>
}>()

const { t } = useI18n()

const warnings = computed(() => ((props.payload ?? {}) as ComplianceCardPayload).warnings ?? [])
</script>

<style scoped>
.compliance-card {
  border: 1px solid #fde68a;
  background: #fffbeb;
}
.warning-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.warning-list li {
  padding: 8px 0;
  border-bottom: 1px dashed #fcd34d;
}
.warning-list li:last-child {
  border-bottom: none;
}
.matched {
  margin: 0 0 4px;
  font-weight: 600;
  color: #b45309;
  font-size: 13px;
}
.suggestion {
  margin: 0;
  font-size: 13px;
  color: #78350f;
}
</style>
