<template>
  <el-card shadow="never" class="calendar-card">
    <template #header>
      <span>{{ t('chat.cardIndustryCalendar') }}</span>
    </template>
    <ul class="event-list">
      <li v-for="(item, i) in events" :key="i">
        <div class="period">{{ item.period }} · {{ item.event }}</div>
        <div class="audience">{{ item.audience }}</div>
        <div v-if="item.topics?.length" class="topics">
          <el-tag v-for="topic in item.topics" :key="topic" size="small" effect="plain">{{ topic }}</el-tag>
        </div>
        <p v-if="item.cta" class="cta">{{ item.cta }}</p>
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

const events = computed(() => {
  const raw = props.payload?.events
  if (!Array.isArray(raw)) return []
  return raw as Array<{ period?: string; event?: string; audience?: string; topics?: string[]; cta?: string }>
})
</script>

<style scoped>
.calendar-card {
  border: 1px solid #ddd6fe;
  background: #faf5ff;
}
.event-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.event-list li {
  padding: 10px 0;
  border-bottom: 1px dashed #c4b5fd;
}
.period {
  font-weight: 600;
  font-size: 14px;
  color: #5b21b6;
}
.audience {
  font-size: 12px;
  color: #7c3aed;
  margin: 4px 0;
}
.topics {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 6px 0;
}
.cta {
  margin: 0;
  font-size: 12px;
  color: #6d28d9;
}
</style>
