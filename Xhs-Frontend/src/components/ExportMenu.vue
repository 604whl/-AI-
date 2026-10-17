<template>
  <div class="export-menu">
    <el-dropdown trigger="click" @command="handleCommand">
      <el-button :loading="loading" size="small">
        <el-icon><Download /></el-icon>
        {{ t('export.exportBtn') }}
        <el-icon class="el-icon--right"><ArrowDown /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="md">
            <el-icon><Document /></el-icon>
            {{ t('export.formatMd') }}
          </el-dropdown-item>
          <el-dropdown-item command="docx">
            <el-icon><Document /></el-icon>
            {{ t('export.formatDocx') }}
          </el-dropdown-item>
          <el-dropdown-item command="zip">
            <el-icon><Folder /></el-icon>
            {{ t('export.formatZip') }}
          </el-dropdown-item>
          <el-dropdown-item command="csv" divided>
            <el-icon><Grid /></el-icon>
            {{ t('export.formatCsv') }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <!-- CSV directly download (no status polling) -->
    <template v-if="showCsvProgress">
      <el-progress
        v-if="csvProgress < 100"
        :percentage="csvProgress"
        :stroke-width="4"
        style="width: 120px; display: inline-block; margin-left: 8px;"
      />
      <span v-else style="margin-left: 8px; color: #22c55e; font-size: 12px;">
        ✓ {{ t('export.done') }}
      </span>
    </template>

    <!-- Export status dialog -->
    <el-dialog
      v-model="showStatusDialog"
      :title="t('export.exportingTitle')"
      width="400px"
      :close-on-click-modal="false"
    >
      <div class="export-status-body">
        <el-result
          v-if="exportStatus === 'ready'"
          icon="success"
          :title="t('export.readyTitle')"
          :sub-title="t('export.readySubtitle')"
        >
          <template #extra>
            <el-button type="primary" @click="handleDownload">
              {{ t('export.downloadBtn') }}
            </el-button>
          </template>
        </el-result>

        <el-result
          v-else-if="exportStatus === 'failed'"
          icon="error"
          :title="t('export.failedTitle')"
          :sub-title="exportError || ''"
        >
          <template #extra>
            <el-button @click="showStatusDialog = false">
              {{ t('common.close') }}
            </el-button>
          </template>
        </el-result>

        <el-result
          v-else-if="exportStatus === 'expired'"
          icon="warning"
          :title="t('export.expiredTitle')"
          :sub-title="t('export.expiredSubtitle')"
        >
          <template #extra>
            <el-button type="primary" @click="retryExport">
              {{ t('export.retryBtn') }}
            </el-button>
          </template>
        </el-result>

        <div v-else class="export-pending">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p>{{ t('export.generating') }}</p>
          <el-progress :percentage="exportProgress" :stroke-width="4" style="margin-top: 12px;" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Download, ArrowDown, Document, Folder, Grid, Loading } from '@element-plus/icons-vue'
import {
  enqueueExport,
  fetchExportStatus,
  getExportDownloadUrl,
  downloadCsv,
  type ExportFormat,
} from '@/api/export'

const props = defineProps<{
  assetId: string
  workspaceId: string
}>()

const { t } = useI18n()

const loading = ref(false)
const showStatusDialog = ref(false)
const exportId = ref<string | null>(null)
const exportStatus = ref<string>('pending')
const exportError = ref('')
const exportProgress = ref(0)
const showCsvProgress = ref(false)
const csvProgress = ref(0)
const pendingFormat = ref<ExportFormat | null>(null)

async function handleCommand(format: ExportFormat) {
  if (format === 'csv') {
    await handleCsv()
    return
  }

  pendingFormat.value = format
  loading.value = true
  exportError.value = ''
  exportProgress.value = 0
  exportStatus.value = 'pending'
  showStatusDialog.value = true

  try {
    const res = await enqueueExport(props.assetId, format)
    exportId.value = res.data.data.id
    pollExportStatus()
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } } }
    const msg = e?.response?.data?.message || t('export.enqueueFailed')
    ElMessage.error(msg)
    showStatusDialog.value = false
  } finally {
    loading.value = false
  }
}

async function handleCsv() {
  loading.value = true
  showCsvProgress.value = true
  csvProgress.value = 0

  try {
    // Simulate progress
    const interval = setInterval(() => {
      csvProgress.value = Math.min(csvProgress.value + 20, 90)
    }, 200)

    downloadCsv(props.workspaceId)

    clearInterval(interval)
    csvProgress.value = 100
    ElMessage.success(t('export.csvDownloadStarted'))

    setTimeout(() => {
      showCsvProgress.value = false
      csvProgress.value = 0
    }, 3000)
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } } }
    ElMessage.error(e?.response?.data?.message || t('export.csvFailed'))
    showCsvProgress.value = false
  } finally {
    loading.value = false
  }
}

function pollExportStatus() {
  if (!exportId.value) return

  let timer: ReturnType<typeof setTimeout>
  timer = setTimeout(async () => {
    clearTimeout(timer)
    try {
      const res = await fetchExportStatus(exportId.value!)
      const data = res.data.data
      exportStatus.value = data.status
      exportError.value = data.errorMessage || ''

      if (data.status === 'ready') {
        exportProgress.value = 100
      } else if (data.status === 'processing') {
        exportProgress.value = 50
        pollExportStatus()
      } else if (data.status === 'pending') {
        exportProgress.value = 20
        pollExportStatus()
      } else {
        // failed / expired, don't poll
      }
    } catch {
      exportStatus.value = 'failed'
      exportError.value = t('export.statusCheckFailed')
    }
  }, 2000)
}

function handleDownload() {
  if (!exportId.value) return
  const url = getExportDownloadUrl(exportId.value)
  window.location.href = url
  showStatusDialog.value = false
}

async function retryExport() {
  showStatusDialog.value = false
  if (pendingFormat.value) {
    await handleCommand(pendingFormat.value)
  }
}
</script>

<style scoped>
.export-menu {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.export-pending {
  text-align: center;
  padding: 24px 0;
  color: #6b7280;
}

.export-pending p {
  margin-top: 12px;
  font-size: 14px;
}
</style>
