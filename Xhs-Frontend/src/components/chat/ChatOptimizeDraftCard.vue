<template>
  <el-card shadow="never" class="draft-card">
    <template #header>
      <div class="head">
        <span>{{ t('chat.cardOptimizeDraft') }}</span>
        <el-button size="small" text type="primary" @click="copyAll">{{ t('dashboard.copy') }}</el-button>
      </div>
    </template>
    <h4 v-if="title" class="draft-title">{{ title }}</h4>
    <p class="draft-body">{{ body }}</p>
    <p v-if="cta" class="cta"><strong>CTA：</strong>{{ cta }}</p>
    <ul v-if="outline.length" class="outline">
      <li v-for="(item, i) in outline" :key="i">{{ item }}</li>
    </ul>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  payload?: Record<string, unknown>
}>()

const { t } = useI18n()

const title = computed(() => String(props.payload?.optimizedTitle ?? ''))
const body = computed(() => String(props.payload?.optimizedBody ?? ''))
const cta = computed(() => String(props.payload?.cta ?? ''))
const outline = computed(() => {
  const raw = props.payload?.structureOutline
  return Array.isArray(raw) ? raw.map(String) : []
})

async function copyAll() {
  const text = [title.value, body.value, cta.value ? `CTA: ${cta.value}` : ''].filter(Boolean).join('\n\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('dashboard.copied'))
  } catch {
    ElMessage.error(t('common.requestFailed'))
  }
}
</script>

<style scoped>
.draft-card {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.draft-title {
  margin: 0 0 8px;
  font-size: 15px;
  color: #111827;
}
.draft-body {
  margin: 0 0 10px;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  color: #374151;
}
.cta {
  margin: 0 0 8px;
  font-size: 13px;
  color: #166534;
}
.outline {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #6b7280;
}
</style>
