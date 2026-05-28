<template>
  <div class="title-ai-loading" :class="{ active: loading }">
    <div class="loading-header">
      <span class="pulse-dot" />
      <span class="loading-title">{{ t('titles.aiGenerating') }}</span>
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
    <div class="shimmer-bar">
      <div class="shimmer-fill" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CircleCheck, Clock, Loading } from '@element-plus/icons-vue'
import type { TitleLoadingStep } from '@/types/title'

const props = defineProps<{
  loading: boolean
  step: TitleLoadingStep
}>()

const { t } = useI18n()

const steps = computed(() => [
  { key: 'emotion' as const, label: t('titles.stepEmotion') },
  { key: 'ctr' as const, label: t('titles.stepCtr') },
  { key: 'structure' as const, label: t('titles.stepStructure') },
  { key: 'optimize' as const, label: t('titles.stepOptimize') },
])

const stepOrder: TitleLoadingStep[] = ['emotion', 'ctr', 'structure', 'optimize', 'done']

function stepState(key: 'emotion' | 'ctr' | 'structure' | 'optimize'): 'pending' | 'active' | 'done' {
  if (!props.loading && props.step === 'idle') return 'pending'

  const currentIdx = stepOrder.indexOf(props.step)
  const keyIdx = stepOrder.indexOf(key)

  if (props.step === 'done' || currentIdx > keyIdx) return 'done'
  if (currentIdx === keyIdx) return 'active'
  return 'pending'
}
</script>

<style scoped>
.title-ai-loading {
  padding: 20px;
  border-radius: 12px;
  background: linear-gradient(135deg, #fff1f2 0%, #faf5ff 100%);
  border: 1px solid #fecdd3;
  margin-bottom: 16px;
}

.title-ai-loading.active {
  animation: glow 2s ease-in-out infinite;
}

@keyframes glow {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(255, 36, 66, 0.08);
  }
  50% {
    box-shadow: 0 0 20px 2px rgba(255, 36, 66, 0.12);
  }
}

.loading-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff2442;
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

.loading-title {
  font-size: 14px;
  font-weight: 600;
  color: #be123c;
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
  color: #ff2442;
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

.shimmer-bar {
  height: 3px;
  border-radius: 2px;
  background: #fce7f3;
  overflow: hidden;
}

.shimmer-fill {
  height: 100%;
  width: 40%;
  background: linear-gradient(90deg, #ff2442, #f472b6, #ff2442);
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
