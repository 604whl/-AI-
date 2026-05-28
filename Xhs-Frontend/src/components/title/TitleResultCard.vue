<template>
  <div class="title-result-card" :class="{ 'is-high-ctr': highCtr }">
    <div v-if="highCtr" class="high-ctr-badge">
      <el-icon><Trophy /></el-icon>
      {{ t('titles.highCtr') }}
    </div>

    <p class="title-text" v-html="highlightedHtml" />

    <div class="score-row">
      <div class="score-item">
        <span class="score-label">{{ t('titles.ctrPredict') }}</span>
        <el-progress
          :percentage="item.ctr"
          :stroke-width="6"
          :color="scoreColor(item.ctr)"
          :format="() => `${item.ctr}`"
        />
      </div>
      <div class="score-item">
        <span class="score-label">{{ t('titles.viralIndex') }}</span>
        <el-progress
          :percentage="item.viral"
          :stroke-width="6"
          color="#6366f1"
          :format="() => `${item.viral}`"
        />
      </div>
      <div class="score-item">
        <span class="score-label">{{ t('titles.emotionValue') }}</span>
        <el-progress
          :percentage="item.emotion"
          :stroke-width="6"
          color="#f59e0b"
          :format="() => `${item.emotion}`"
        />
      </div>
    </div>

    <div class="meta-row">
      <el-tag :type="typeTagColor" size="small" effect="dark" round>
        {{ t(`titles.typeTags.${item.typeTag}`) }}
      </el-tag>
      <div class="actions">
        <el-tooltip :content="t('dashboard.copy')" placement="top">
          <el-button circle size="small" @click="emit('copy', item.title)">
            <el-icon><CopyDocument /></el-icon>
          </el-button>
        </el-tooltip>
        <el-button size="small" type="primary" plain @click="emit('apply', item)">
          {{ t('titles.applyToAnalysis') }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CopyDocument, Trophy } from '@element-plus/icons-vue'
import type { ScoredTitle, TitleTypeTag } from '@/types/title'
import { highlightTitleHtml, isHighCtr } from '@/utils/titleHighlight'

const props = defineProps<{
  item: ScoredTitle
}>()

const emit = defineEmits<{
  copy: [title: string]
  apply: [item: ScoredTitle]
}>()

const { t } = useI18n()

const highCtr = computed(() => isHighCtr(props.item.ctr))

const highlightedHtml = computed(() =>
  highlightTitleHtml(props.item.title, props.item.highlights),
)

const typeTagColor = computed(() => {
  const map: Record<TitleTypeTag, 'danger' | 'warning' | 'success' | 'info'> = {
    anxiety: 'danger',
    info_gap: 'warning',
    comeback: 'success',
    conflict: 'info',
  }
  return map[props.item.typeTag]
})

function scoreColor(value: number): string {
  if (value >= 88) return '#ff2442'
  if (value >= 75) return '#f59e0b'
  return '#6366f1'
}
</script>

<style scoped>
.title-result-card {
  position: relative;
  padding: 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #f3f4f6;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.title-result-card:hover {
  border-color: #fecdd3;
  box-shadow: 0 8px 24px rgba(255, 36, 66, 0.1);
  transform: translateY(-2px);
}

.title-result-card.is-high-ctr {
  border-color: #ff2442;
  background: linear-gradient(135deg, #fff5f5 0%, #fff 60%);
}

.high-ctr-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 10px;
  padding: 2px 10px;
  border-radius: 20px;
  background: linear-gradient(90deg, #ff2442, #fb7185);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.title-text {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.6;
  color: #111827;
}

.title-text :deep(.title-highlight) {
  background: linear-gradient(180deg, transparent 60%, #fecdd3 60%);
  color: #be123c;
  font-weight: 700;
  padding: 0 2px;
  border-radius: 2px;
}

.score-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.score-label {
  display: block;
  font-size: 11px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

@media (max-width: 576px) {
  .score-row {
    grid-template-columns: 1fr;
  }
}
</style>
