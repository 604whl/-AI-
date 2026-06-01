<template>
  <el-card shadow="never" class="report-module cover-panel">
    <template #header>
      <span class="module-title">{{ t('report.moduleCover') }}</span>
    </template>

    <el-alert
      v-if="!coverImageUrl"
      type="info"
      :closable="false"
      show-icon
      :title="t('report.coverMissingTitle')"
      :description="t('report.coverMissingDesc')"
    />

    <template v-else>
      <div v-if="previewUrl" class="cover-preview">
        <img :src="previewUrl" :alt="t('analysis.cover')" />
      </div>

      <el-alert
        v-if="coverAnalysis && !coverAnalysis.available"
        type="warning"
        :closable="false"
        show-icon
        :title="t('report.coverUnavailableTitle')"
        :description="t('report.coverUnavailableDesc')"
      />

      <div v-else-if="coverAnalysis?.available" class="cover-insights">
        <div v-if="coverAnalysis.keywords?.length" class="insight-row">
          <span class="insight-label">{{ t('report.coverKeywords') }}</span>
          <div class="tag-row">
            <el-tag
              v-for="(kw, i) in coverAnalysis.keywords"
              :key="i"
              size="small"
              effect="plain"
            >
              {{ kw }}
            </el-tag>
          </div>
        </div>
        <p v-if="coverAnalysis.contrastComment" class="insight-text">
          <strong>{{ t('report.coverContrast') }}</strong>{{ coverAnalysis.contrastComment }}
        </p>
        <p v-if="coverAnalysis.emotionMatch" class="insight-text">
          <strong>{{ t('report.coverEmotion') }}</strong>{{ coverAnalysis.emotionMatch }}
        </p>
        <p v-if="coverAnalysis.ctrImpact" class="insight-text">
          <strong>{{ t('report.coverCtrImpact') }}</strong>{{ coverAnalysis.ctrImpact }}
        </p>
      </div>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveCoverPreviewUrl } from '@/api/file'
import type { CoverAnalysis } from '@/types/api'

const props = defineProps<{
  coverImageUrl?: string
  coverAnalysis?: CoverAnalysis | null
}>()

const { t } = useI18n()
const previewUrl = ref<string | null>(null)

watch(
  () => props.coverImageUrl,
  async (url) => {
    if (previewUrl.value) {
      URL.revokeObjectURL(previewUrl.value)
      previewUrl.value = null
    }
    if (!url) return
    try {
      previewUrl.value = await resolveCoverPreviewUrl(url)
    } catch {
      previewUrl.value = null
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
})
</script>

<style scoped>
.report-module {
  border-radius: 12px;
}

.module-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.cover-preview {
  margin-bottom: 16px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  max-width: 280px;
}

.cover-preview img {
  display: block;
  width: 100%;
  height: auto;
}

.cover-insights {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.insight-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.insight-label {
  font-size: 13px;
  color: #6b7280;
  font-weight: 600;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.insight-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #374151;
}

.insight-text strong {
  color: #111827;
  margin-right: 4px;
}
</style>
