<template>
  <div class="ai-loading-steps" :class="{ active: loading }">
    <div class="ai-loading-header">
      <span class="ai-pulse-dot" />
      <span class="ai-loading-title">{{ t('workbench.aiAnalyzing') }}</span>
    </div>
    <div class="steps-list">
      <div
        v-for="step in steps"
        :key="step.key"
        class="step-item"
        :class="stepState(step.key)"
      >
        <el-icon class="step-icon">
          <Loading v-if="stepState(step.key) === 'active'" class="spin" />
          <CircleCheck v-else-if="stepState(step.key) === 'done'" />
          <Clock v-else />
        </el-icon>
        <span class="step-label">{{ step.label }}</span>
      </div>
    </div>
    <div class="ai-shimmer-bar">
      <div class="ai-shimmer-fill" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CircleCheck, Clock, Loading } from '@element-plus/icons-vue'
import type { AnalysisLoadingStep } from '@/types/workbench'

const props = defineProps<{
  loading: boolean
  step: AnalysisLoadingStep
}>()

const { t } = useI18n()

const steps = computed(() => [
  { key: 'structure' as const, label: t('workbench.stepStructure') },
  { key: 'emotion' as const, label: t('workbench.stepEmotion') },
  { key: 'ctr' as const, label: t('workbench.stepCtr') },
])

const stepOrder: AnalysisLoadingStep[] = ['structure', 'emotion', 'ctr', 'done']

function stepState(key: 'structure' | 'emotion' | 'ctr'): 'pending' | 'active' | 'done' {
  if (!props.loading && props.step === 'idle') return 'pending'

  const currentIdx = stepOrder.indexOf(props.step)
  const keyIdx = stepOrder.indexOf(key)

  if (props.step === 'done' || currentIdx > keyIdx) return 'done'
  if (currentIdx === keyIdx) return 'active'
  return 'pending'
}
</script>

<style scoped>
.ai-loading-steps {
  padding: 20px;
  border-radius: 12px;
  background: linear-gradient(135deg, #faf5ff 0%, #f0f9ff 100%);
  border: 1px solid #e9d5ff;
}

.ai-loading-steps.active {
  animation: glow 2s ease-in-out infinite;
}

@keyframes glow {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(139, 92, 246, 0.1);
  }
  50% {
    box-shadow: 0 0 20px 2px rgba(139, 92, 246, 0.15);
  }
}

.ai-loading-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.ai-pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #8b5cf6;
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(1.3);
  }
}

.ai-loading-title {
  font-size: 14px;
  font-weight: 600;
  color: #6d28d9;
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #9ca3af;
  transition: color 0.3s;
}

.step-item.active {
  color: #7c3aed;
  font-weight: 500;
}

.step-item.done {
  color: #059669;
}

.step-icon {
  font-size: 16px;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.ai-shimmer-bar {
  height: 3px;
  border-radius: 2px;
  background: #e5e7eb;
  overflow: hidden;
}

.ai-shimmer-fill {
  height: 100%;
  width: 40%;
  background: linear-gradient(90deg, #8b5cf6, #6366f1, #8b5cf6);
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(350%);
  }
}
</style>
