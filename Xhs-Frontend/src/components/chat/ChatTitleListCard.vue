<template>
  <el-card shadow="never" class="title-card">
    <template #header>
      <span>{{ t('chat.cardTitles') }}</span>
    </template>
    <ul class="title-list">
      <li v-for="(item, index) in titles" :key="index">
        <div class="title-row">
          <span class="title-text">{{ item.text }}</span>
          <el-button size="small" text type="primary" @click="copy(item.text)">
            {{ t('dashboard.copy') }}
          </el-button>
        </div>
        <div v-if="item.highlights?.length" class="highlights">
          <el-tag v-for="h in item.highlights" :key="h" size="small" effect="plain">{{ h }}</el-tag>
        </div>
        <el-tag v-if="item.estimatedCtr" size="small" :type="ctrTagType(item.estimatedCtr)">
          CTR {{ item.estimatedCtr }}
        </el-tag>
      </li>
    </ul>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { TitleListCardPayload } from '@/types/chat'

const props = defineProps<{
  payload?: Record<string, unknown>
}>()

const { t } = useI18n()

const titles = computed(() => ((props.payload ?? {}) as TitleListCardPayload).titles ?? [])

function ctrTagType(level: string) {
  if (level === 'high') return 'success'
  if (level === 'medium') return 'warning'
  return 'info'
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('dashboard.copied'))
  } catch {
    ElMessage.error(t('common.requestFailed'))
  }
}
</script>

<style scoped>
.title-card {
  border: 1px solid #e0e7ff;
  background: #f8faff;
}
.title-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.title-list li {
  padding: 10px 0;
  border-bottom: 1px dashed #e5e7eb;
}
.title-list li:last-child {
  border-bottom: none;
}
.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.title-text {
  font-size: 14px;
  font-weight: 500;
  color: #111827;
  line-height: 1.5;
}
.highlights {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
</style>
