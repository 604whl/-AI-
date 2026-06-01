<template>
  <el-card shadow="never" class="cover-card">
    <template #header>
      <span>{{ t('chat.cardCover') }}</span>
    </template>
    <el-empty v-if="!available" :description="t('chat.coverUnavailable')" :image-size="48" />
    <template v-else>
      <div v-if="keywords.length" class="keywords">
        <el-tag v-for="kw in keywords" :key="kw" size="small" effect="plain">{{ kw }}</el-tag>
      </div>
      <p v-if="contrastComment" class="line"><strong>{{ t('chat.coverContrast') }}：</strong>{{ contrastComment }}</p>
      <p v-if="emotionMatch" class="line"><strong>{{ t('chat.coverEmotion') }}：</strong>{{ emotionMatch }}</p>
      <p v-if="ctrImpact" class="line impact"><strong>CTR：</strong>{{ ctrImpact }}</p>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CoverAnalysis } from '@/types/api'

const props = defineProps<{
  payload?: Record<string, unknown>
}>()

const { t } = useI18n()

const data = computed(() => (props.payload ?? {}) as unknown as CoverAnalysis)
const available = computed(() => data.value.available === true)
const keywords = computed(() => data.value.keywords ?? [])
const contrastComment = computed(() => data.value.contrastComment ?? '')
const emotionMatch = computed(() => data.value.emotionMatch ?? '')
const ctrImpact = computed(() => data.value.ctrImpact ?? '')
</script>

<style scoped>
.cover-card {
  border: 1px solid #c7d2fe;
  background: #f5f7ff;
}
.keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}
.line {
  margin: 0 0 8px;
  font-size: 13px;
  color: #374151;
  line-height: 1.5;
}
.impact {
  color: #4338ca;
  font-weight: 500;
}
</style>
