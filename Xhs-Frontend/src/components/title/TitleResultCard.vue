<template>
  <div class="title-result-card" :class="{ 'is-high-ctr': highCtr }">
    <div v-if="highCtr" class="high-ctr-badge">
      <el-icon><Trophy /></el-icon>
      {{ t('titles.highCtr') }}
    </div>

    <p class="title-text" v-html="highlightedHtml" />

    <div class="meta-row">
      <el-tag :type="ctrTagType" size="small" effect="plain" round>
        {{ t('titles.ctrPredict') }} {{ t(`titles.ctr.${item.estimatedCtr}`) }}
      </el-tag>
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

const highCtr = computed(() => isHighCtr(props.item.estimatedCtr))

const highlightedHtml = computed(() =>
  highlightTitleHtml(props.item.title, props.item.highlights),
)

const ctrTagType = computed(() => {
  if (props.item.estimatedCtr === 'high') return 'danger'
  if (props.item.estimatedCtr === 'medium') return 'warning'
  return 'info'
})

const typeTagColor = computed(() => {
  const map: Record<TitleTypeTag, 'danger' | 'warning' | 'success' | 'info'> = {
    anxiety: 'danger',
    info_gap: 'warning',
    comeback: 'success',
    conflict: 'info',
  }
  return map[props.item.typeTag]
})
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
</style>
