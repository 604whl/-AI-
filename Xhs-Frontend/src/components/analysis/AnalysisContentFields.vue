<template>
  <div class="content-fields">
    <el-form-item :label="t('contentInput.cover')">
      <div class="cover-row">
        <el-upload
          class="cover-uploader"
          :show-file-list="false"
          accept="image/jpeg,image/png,image/webp"
          :disabled="coverUploading"
          :before-upload="beforeCoverUpload"
          :http-request="handleCoverUpload"
        >
          <img v-if="coverPreview" :src="coverPreview" class="cover-preview" alt="" />
          <div v-else class="cover-placeholder">
            <el-icon><Plus /></el-icon>
            <span>{{ t('contentInput.coverUpload') }}</span>
          </div>
        </el-upload>
        <div class="cover-meta">
          <p class="cover-hint">{{ t('contentInput.coverHint') }}</p>
          <el-button
            v-if="coverImageUrl || coverPreview"
            text
            type="danger"
            size="small"
            @click="clearCover"
          >
            {{ t('contentInput.coverRemove') }}
          </el-button>
        </div>
      </div>
    </el-form-item>

    <template v-if="scenario === 'published'">
      <el-divider content-position="left">{{ t('contentInput.publishedSection') }}</el-divider>
      <p class="section-hint">{{ t('contentInput.publishedHint') }}</p>
      <el-form-item :label="t('contentInput.noteUrl')">
        <el-input v-model="publishedMetrics.noteUrl" maxlength="500" :placeholder="t('contentInput.noteUrlPlaceholder')" />
      </el-form-item>
      <div class="metrics-grid">
        <el-form-item :label="t('contentInput.impressions')">
          <el-input-number v-model="publishedMetrics.impressions" :min="0" :controls="true" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('contentInput.likes')">
          <el-input-number v-model="publishedMetrics.likes" :min="0" :controls="true" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('contentInput.collects')">
          <el-input-number v-model="publishedMetrics.collects" :min="0" :controls="true" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('contentInput.comments')">
          <el-input-number v-model="publishedMetrics.comments" :min="0" :controls="true" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('contentInput.dmInquiries')">
          <el-input-number v-model="publishedMetrics.dmInquiries" :min="0" :controls="true" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('contentInput.publishedAt')">
          <el-date-picker
            v-model="publishedAtDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width: 100%"
            :placeholder="t('contentInput.publishedAtPlaceholder')"
          />
        </el-form-item>
      </div>
    </template>

    <template v-if="scenario === 'competitor'">
      <el-divider content-position="left">{{ t('contentInput.competitorSection') }}</el-divider>
      <p class="section-hint">{{ t('contentInput.competitorHint') }}</p>
      <el-form-item :label="t('contentInput.competitorAccount')">
        <el-input
          v-model="competitorContext.accountName"
          maxlength="64"
          :placeholder="t('contentInput.competitorAccountPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('contentInput.noteUrl')">
        <el-input
          v-model="competitorContext.noteUrl"
          maxlength="500"
          :placeholder="t('contentInput.competitorUrlPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('contentInput.learningFocus')">
        <el-input
          v-model="competitorContext.learningFocus"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          :placeholder="t('contentInput.learningFocusPlaceholder')"
        />
      </el-form-item>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type UploadRequestOptions } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { resolveCoverPreviewUrl, uploadCoverImage } from '@/api/file'
import type { AnalysisScenario, CompetitorContext, PublishedMetrics } from '@/types/api'

defineProps<{
  scenario: AnalysisScenario
}>()

const coverImageUrl = defineModel<string | undefined>('coverImageUrl')
const publishedMetrics = defineModel<PublishedMetrics>('publishedMetrics', { required: true })
const competitorContext = defineModel<CompetitorContext>('competitorContext', { required: true })

const { t } = useI18n()
const coverUploading = ref(false)
const coverPreview = ref('')
let previewObjectUrl: string | null = null

function revokePreviewObjectUrl() {
  if (previewObjectUrl) {
    URL.revokeObjectURL(previewObjectUrl)
    previewObjectUrl = null
  }
}

watch(
  () => coverImageUrl.value,
  async (url) => {
    if (!url) {
      if (!coverPreview.value.startsWith('data:')) {
        coverPreview.value = ''
      }
      revokePreviewObjectUrl()
      return
    }
    if (coverPreview.value.startsWith('data:')) {
      return
    }
    try {
      revokePreviewObjectUrl()
      previewObjectUrl = await resolveCoverPreviewUrl(url)
      coverPreview.value = previewObjectUrl
    } catch {
      coverPreview.value = ''
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  revokePreviewObjectUrl()
})

const publishedAtDate = computed({
  get: () => publishedMetrics.value.publishedAt ?? '',
  set: (value: string) => {
    publishedMetrics.value = {
      ...publishedMetrics.value,
      publishedAt: value || undefined,
    }
  },
})

function beforeCoverUpload(file: File) {
  const allowed = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowed.includes(file.type)) {
    ElMessage.warning(t('contentInput.coverInvalid'))
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning(t('contentInput.coverTooLarge'))
    return false
  }
  return true
}

async function handleCoverUpload(options: UploadRequestOptions) {
  const file = options.file as File
  coverUploading.value = true
  try {
    const reader = new FileReader()
    reader.onload = () => {
      coverPreview.value = String(reader.result ?? '')
    }
    reader.readAsDataURL(file)

    const res = await uploadCoverImage(file)
    coverImageUrl.value = res.data.data.coverImageUrl
    ElMessage.success(t('contentInput.coverUploadSuccess'))
    options.onSuccess?.(res.data)
  } catch (err) {
    coverPreview.value = ''
    coverImageUrl.value = undefined
    const message = err instanceof Error ? err.message : t('contentInput.coverUploadFailed')
    ElMessage.error(message)
  } finally {
    coverUploading.value = false
  }
}

function clearCover() {
  coverPreview.value = ''
  coverImageUrl.value = undefined
  revokePreviewObjectUrl()
}
</script>

<style scoped>
.cover-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.cover-uploader :deep(.el-upload) {
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
  width: 120px;
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-uploader :deep(.el-upload:hover) {
  border-color: var(--el-color-primary);
}

.cover-preview {
  width: 120px;
  height: 160px;
  object-fit: cover;
  display: block;
}

.cover-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding: 8px;
  text-align: center;
}

.cover-meta {
  flex: 1;
}

.cover-hint {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.section-hint {
  margin: -8px 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0 16px;
}

@media (max-width: 640px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .cover-row {
    flex-direction: column;
  }
}
</style>
