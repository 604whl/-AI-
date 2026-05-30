<template>
  <div v-if="visible" class="sensitive-detector">
    <div class="detector-header">
      <div class="header-left">
        <el-icon class="risk-icon" :class="riskLevel"><WarningFilled /></el-icon>
        <span class="header-title">{{ t('workbench.sensitiveTitle') }}</span>
        <el-tag :type="riskTagType" size="small" effect="dark" round>
          {{ riskLabel }}
        </el-tag>
        <el-tag v-if="fromAnalysisReport" size="small" type="info" effect="plain">
          {{ t('workbench.complianceFromReport') }}
        </el-tag>
      </div>
    </div>

    <div v-if="result.warnings.length" class="risk-content">
      <p class="risk-desc">{{ t('workbench.sensitiveFound', { count: result.warnings.length }) }}</p>
      <ul class="compliance-list">
        <li v-for="(w, i) in result.warnings" :key="`${w.rule}-${i}`">
          <strong>{{ w.matchedText }}</strong>
          <span class="compliance-suggestion"> — {{ w.suggestion }}</span>
        </li>
      </ul>
      <div class="word-tags">
        <el-tag
          v-for="word in result.words"
          :key="word"
          type="danger"
          effect="dark"
          size="small"
          class="sensitive-tag"
        >
          {{ word }}
        </el-tag>
      </div>
      <div v-if="highlightedText" class="highlight-preview">
        <span class="preview-label">{{ t('workbench.highlightPreview') }}</span>
        <p class="preview-text" v-html="highlightedText" />
      </div>
    </div>

    <div v-else class="safe-state">
      <el-icon class="safe-icon"><CircleCheckFilled /></el-icon>
      <span>{{ t('workbench.sensitiveSafe') }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'
import type { SensitiveWordResult, SensitiveRiskLevel } from '@/types/workbench'

const props = defineProps<{
  result: SensitiveWordResult
  title: string
  body: string
  /** 展示分析报告中的 complianceWarnings（后端 ComplianceChecker 合并结果） */
  fromAnalysisReport?: boolean
}>()

const { t } = useI18n()

const visible = computed(() => props.title.trim() || props.body.trim())

const riskLevel = computed(() => props.result.riskLevel)

const riskTagType = computed(() => {
  const map: Record<SensitiveRiskLevel, 'success' | 'warning' | 'danger'> = {
    low: 'success',
    medium: 'warning',
    high: 'danger',
  }
  return map[props.result.riskLevel]
})

const riskLabel = computed(() => t(`workbench.riskLevel.${props.result.riskLevel}`))

const highlightedText = computed(() => {
  const text = [props.title, props.body].filter(Boolean).join('\n')
  if (!text || !props.result.words.length) return ''

  let html = escapeHtml(text)
  for (const word of props.result.words) {
    const escaped = escapeHtml(word)
    html = html.replace(
      new RegExp(escaped, 'g'),
      `<mark class="sensitive-mark">${escaped}</mark>`,
    )
  }
  return html.replace(/\n/g, '<br/>')
})

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
</script>

<style scoped>
.sensitive-detector {
  margin-top: -8px;
  margin-bottom: 8px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid #fecaca;
  background: linear-gradient(135deg, #fef2f2 0%, #fff5f5 100%);
}

.detector-header {
  margin-bottom: 8px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-icon {
  font-size: 18px;
}

.risk-icon.low {
  color: #059669;
}

.risk-icon.medium {
  color: #f59e0b;
}

.risk-icon.high {
  color: #dc2626;
}

.header-title {
  font-size: 13px;
  font-weight: 600;
  color: #991b1b;
}

.risk-desc {
  margin: 0 0 8px;
  font-size: 12px;
  color: #b91c1c;
}

.compliance-list {
  margin: 0 0 10px;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.6;
  color: #991b1b;
}

.compliance-suggestion {
  font-weight: 400;
  color: #b91c1c;
}

.word-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.sensitive-tag {
  font-weight: 600;
  animation: shake 0.5s ease;
}

@keyframes shake {
  0%,
  100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-2px);
  }
  75% {
    transform: translateX(2px);
  }
}

.highlight-preview {
  padding: 8px 10px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px dashed #fca5a5;
}

.preview-label {
  display: block;
  font-size: 11px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.preview-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #374151;
  word-break: break-all;
}

.preview-text :deep(.sensitive-mark) {
  background: #fecaca;
  color: #991b1b;
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: 600;
}

.safe-state {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #059669;
}

.safe-icon {
  font-size: 16px;
}
</style>
