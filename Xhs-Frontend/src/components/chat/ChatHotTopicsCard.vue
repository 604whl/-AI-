<template>
  <el-card shadow="never" class="topics-card">
    <template #header>
      <span>{{ t('chat.cardHotTopics') }}</span>
    </template>
    <ul class="topic-list">
      <li v-for="(item, i) in topics" :key="i">
        <div class="tag-row">
          <el-tag type="danger" effect="plain" size="small">{{ item.tag }}</el-tag>
          <span class="direction">{{ item.direction }}</span>
        </div>
        <div v-if="item.keywords?.length" class="keywords">
          <el-tag v-for="kw in item.keywords" :key="kw" size="small">{{ kw }}</el-tag>
        </div>
        <p v-if="item.tip" class="tip">{{ item.tip }}</p>
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

const topics = computed(() => {
  const raw = props.payload?.topics
  if (!Array.isArray(raw)) return []
  return raw as Array<{ tag?: string; direction?: string; keywords?: string[]; tip?: string }>
})
</script>

<style scoped>
.topics-card {
  border: 1px solid #fed7aa;
  background: #fff7ed;
}
.topic-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.topic-list li {
  padding: 10px 0;
  border-bottom: 1px dashed #fdba74;
}
.tag-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.direction {
  font-size: 12px;
  color: #9a3412;
}
.keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 6px;
}
.tip {
  margin: 0;
  font-size: 12px;
  color: #78350f;
}
</style>
