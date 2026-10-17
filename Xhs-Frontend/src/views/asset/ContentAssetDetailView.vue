<template>
  <div class="asset-detail-page">
    <!-- Header -->
    <div class="page-header">
      <el-button text @click="router.push('/asset')">
        <el-icon><ArrowLeft /></el-icon>
        {{ t('report.back') }}
      </el-button>
      <div class="header-actions">
        <AssetStatusBadge :status="asset?.status" />
        <el-button
          v-if="asset?.status === 'draft'"
          type="primary"
          :loading="saving"
          @click="handleSave"
        >
          {{ t('common.save') }}
        </el-button>
        <el-button
          v-if="asset?.status === 'draft'"
          :loading="submitting"
          @click="handleSubmitReview"
        >
          {{ t('asset.submitReview') }}
        </el-button>
        <el-button @click="goToVersions">{{ t('asset.viewVersions') }}</el-button>
      </div>
    </div>

    <!-- Loading / Error -->
    <el-skeleton v-if="loading" :rows="8" animated />
    <el-alert v-else-if="loadError" type="error" :title="loadError" show-icon :closable="false" />

    <template v-else-if="asset">
      <el-row :gutter="24">
        <!-- Left: Editor -->
        <el-col :xs="24" :md="14">
          <el-card shadow="never">
            <template #header>
              <span>{{ t('asset.editorTitle') }}</span>
            </template>
            <el-form label-position="top">
              <el-form-item :label="t('analysis.title')" required>
                <el-input
                  v-model="form.title"
                  :placeholder="t('asset.titlePlaceholder')"
                  maxlength="255"
                  show-word-limit
                />
              </el-form-item>
              <el-form-item :label="t('analysis.body')">
                <el-input
                  v-model="form.body"
                  type="textarea"
                  :rows="12"
                  :placeholder="t('asset.bodyPlaceholder')"
                  @input="onAutoSave"
                />
              </el-form-item>
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item :label="t('analysis.persona')">
                    <el-select v-model="form.persona" class="full-width">
                      <el-option :label="t('persona.agency')" value="agency" />
                      <el-option :label="t('persona.mentor')" value="mentor" />
                      <el-option :label="t('persona.senior')" value="senior" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="t('analysis.scenario')">
                    <el-select v-model="form.scenario" class="full-width">
                      <el-option :label="t('analysis.scenarioDraft')" value="draft" />
                      <el-option :label="t('analysis.scenarioPublished')" value="published" />
                      <el-option :label="t('analysis.scenarioCompetitor')" value="competitor" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>

            <!-- Auto-save indicator -->
            <div class="autosave-hint">
              <el-icon v-if="autoSaving" class="is-loading"><Loading /></el-icon>
              <span v-else-if="lastSavedAt">{{ t('asset.savedAt', { time: formatTime(lastSavedAt) }) }}</span>
              <span v-else-if="saveError" class="save-error">{{ t('asset.saveFailed') }}</span>
            </div>
          </el-card>
        </el-col>

        <!-- Right: Meta + Media -->
        <el-col :xs="24" :md="10">
          <el-card shadow="never" class="meta-card">
            <template #header>
              <span>{{ t('asset.metaTitle') }}</span>
            </template>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item :label="t('asset.versionCol')">
                v{{ asset.currentVersion }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('asset.platformCol')">
                <el-input v-model="form.platform" size="small" :placeholder="t('asset.platformPlaceholder')" />
              </el-descriptions-item>
              <el-descriptions-item :label="t('asset.accountCol')">
                <el-input v-model="form.accountId" size="small" :placeholder="t('asset.accountPlaceholder')" />
              </el-descriptions-item>
              <el-descriptions-item :label="t('asset.createdAt')">
                {{ formatDateTime(asset.createdAt, locale) }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('asset.updatedAt')">
                {{ formatDateTime(asset.updatedAt, locale) }}
              </el-descriptions-item>
            </el-descriptions>

            <div class="meta-actions">
              <el-button type="primary" size="small" :loading="saving" @click="handleSave">
                {{ t('common.save') }}
              </el-button>
              <el-button type="danger" size="small" @click="confirmDelete">
                {{ t('history.delete') }}
              </el-button>
            </div>
          </el-card>

          <!-- Media -->
          <el-card shadow="never" class="media-card">
            <template #header>
              <span>{{ t('asset.mediaTitle') }}</span>
            </template>
            <div v-if="asset.media?.length" class="media-grid">
              <div v-for="m in asset.media" :key="m.id" class="media-item">
                <div class="media-thumb">
                  <el-icon v-if="m.mediaType === 'video'" :size="32"><VideoPlay /></el-icon>
                  <img v-else :src="mediaUrl(m)" alt="media" class="media-img" />
                </div>
                <div class="media-info">
                  <span class="media-type">{{ m.mediaType }}</span>
                  <span class="media-role">{{ m.role }}</span>
                </div>
                <el-button
                  text
                  type="danger"
                  size="small"
                  @click="handleRemoveMedia(m.id)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
            <el-empty v-else :description="t('asset.noMedia')" :image-size="60" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Loading, Delete, VideoPlay } from '@element-plus/icons-vue'
import { useWorkspaceChange } from '@/composables/useWorkspaceChange'
import {
  fetchAsset,
  updateAsset,
  deleteAsset,
  removeAssetMedia,
  submitAssetReview,
} from '@/api/asset'
import AssetStatusBadge from '@/components/asset/AssetStatusBadge.vue'
import { formatDateTime } from '@/utils/analysisDisplay'
import type { ContentAsset, AssetUpdateRequest } from '@/api/asset'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const assetId = route.params.id as string

const loading = ref(true)
const loadError = ref('')
const saving = ref(false)
const submitting = ref(false)
const autoSaving = ref(false)
const lastSavedAt = ref<string | null>(null)
const saveError = ref(false)

const asset = ref<ContentAsset | null>(null)
const form = reactive<{
  title: string
  body: string
  persona: string
  scenario: string
  platform: string
  accountId: string
}>({
  title: '',
  body: '',
  persona: 'agency',
  scenario: 'draft',
  platform: '',
  accountId: '',
})

let autoSaveTimer: ReturnType<typeof setTimeout> | null = null
let isDirty = false

function syncForm(a: ContentAsset) {
  form.title = a.title
  form.body = a.body
  form.persona = a.persona || 'agency'
  form.scenario = a.scenario || 'draft'
  form.platform = a.platform || ''
  form.accountId = a.accountId || ''
}

function onAutoSave() {
  isDirty = true
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => {
    if (isDirty && form.title.trim()) {
      doSave(true)
    }
  }, 2000)
}

async function doSave(isAuto = false) {
  if (!form.title.trim()) {
    if (!isAuto) ElMessage.warning(t('asset.titleRequired'))
    return
  }
  if (isAuto) {
    autoSaving.value = true
  } else {
    saving.value = true
  }
  saveError.value = false
  try {
    const payload: AssetUpdateRequest = {
      title: form.title,
      body: form.body,
      persona: form.persona,
      scenario: form.scenario,
      platform: form.platform || undefined,
      accountId: form.accountId || undefined,
      editReason: isAuto ? 'auto-save' : 'manual save',
    }
    const res = await updateAsset(assetId, payload)
    asset.value = res.data.data
    syncForm(asset.value)
    isDirty = false
    lastSavedAt.value = new Date().toISOString()
    if (!isAuto) ElMessage.success(t('settings.saveSuccess'))
  } catch {
    saveError.value = true
    if (!isAuto) ElMessage.error(t('settings.saveFailed'))
  } finally {
    autoSaving.value = false
    saving.value = false
  }
}

async function handleSave() {
  await doSave(false)
}

async function handleSubmitReview() {
  submitting.value = true
  try {
    // 先保存
    await doSave(false)
    const res = await submitAssetReview(assetId)
    asset.value = res.data.data
    ElMessage.success(t('asset.submitReviewSuccess'))
  } catch {
    ElMessage.error(t('asset.submitReviewFailed'))
  } finally {
    submitting.value = false
  }
}

async function handleRemoveMedia(mediaId: string) {
  try {
    await ElMessageBox.confirm(t('asset.confirmRemoveMedia'), t('history.delete'), { type: 'warning' })
    const res = await removeAssetMedia(assetId, mediaId)
    asset.value = res.data.data
    ElMessage.success(t('history.deleteSuccess'))
  } catch {
    // ignore
  }
}

async function confirmDelete() {
  try {
    await ElMessageBox.confirm(t('asset.deleteConfirm'), t('history.delete'), { type: 'warning' })
    await deleteAsset(assetId)
    ElMessage.success(t('history.deleteSuccess'))
    router.push('/asset')
  } catch {
    // ignore
  }
}

function goToVersions() {
  router.push(`/asset/${assetId}/versions`)
}

function mediaUrl(m: { objectKey: string }) {
  // 假设有签名 URL 服务，暂用 placeholder
  return `/api/v1/media/${m.objectKey}`
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString()
}

async function loadAsset() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await fetchAsset(assetId)
    asset.value = res.data.data
    syncForm(asset.value)
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } } }
    loadError.value = e?.response?.data?.message || t('report.notFound')
  } finally {
    loading.value = false
  }
}

onMounted(loadAsset)
useWorkspaceChange(loadAsset)
</script>

<style scoped>
.asset-detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.autosave-hint {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
  min-height: 18px;
}

.save-error {
  color: #ef4444;
}

.meta-card {
  margin-bottom: 16px;
}

.meta-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.media-card {
  margin-top: 16px;
}

.media-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.media-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}

.media-thumb {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
  border-radius: 4px;
  overflow: hidden;
  color: #9ca3af;
  flex-shrink: 0;
}

.media-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.media-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  font-size: 12px;
}

.media-type {
  font-weight: 600;
  color: #374151;
}

.media-role {
  color: #9ca3af;
}

.full-width {
  width: 100%;
}
</style>
