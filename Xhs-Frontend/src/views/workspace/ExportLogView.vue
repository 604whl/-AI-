<template>
  <div class="export-log-view">
    <!-- Header -->
    <div class="page-header">
      <h2>{{ t('export.exportHistory') }}</h2>
      <el-button size="small" @click="router.push('/asset')">
        <el-icon><ArrowLeft /></el-icon>
        {{ t('report.back') }}
      </el-button>
    </div>

    <!-- Empty state -->
    <el-empty v-if="!loading && items.length === 0" :description="t('export.noExports')" />

    <!-- Loading -->
    <el-skeleton v-else-if="loading" :rows="6" animated />

    <!-- List -->
    <el-card v-else shadow="never">
      <el-table :data="items" stripe style="width: 100%">
        <el-table-column :label="t('export.colFormat')" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="formatTagType(row.format)">
              {{ row.format.toUpperCase() }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column :label="t('export.colStatus')" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column :label="t('export.colFileSize')" width="120">
          <template #default="{ row }">
            {{ row.fileSize ? formatFileSize(row.fileSize) : '—' }}
          </template>
        </el-table-column>

        <el-table-column :label="t('export.colExpiresAt')" width="180">
          <template #default="{ row }">
            {{ row.expiresAt ? formatDateTime(row.expiresAt) : '—' }}
          </template>
        </el-table-column>

        <el-table-column :label="t('export.colCreatedAt')" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column :label="t('export.colActions')">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ready'"
              type="primary"
              size="small"
              @click="handleDownload(row)"
            >
              <el-icon><Download /></el-icon>
              {{ t('export.downloadBtn') }}
            </el-button>
            <span v-else-if="row.status === 'failed'" style="color: #ef4444; font-size: 12px;">
              {{ row.errorMessage || t('export.failedTitle') }}
            </span>
            <span v-else-if="row.status === 'expired'" style="color: #f59e0b; font-size: 12px;">
              {{ t('export.expiredTitle') }}
            </span>
            <span v-else style="color: #9ca3af; font-size: 12px;">
              {{ t('export.generating') }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchExportHistory, getExportDownloadUrl, type ExportListItem } from '@/api/export'
import { useWorkspace } from '@/stores/workspace'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspace()

const loading = ref(true)
const items = ref<ExportListItem[]>([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString(locale.value === 'zh' ? 'zh-CN' : 'en-US')
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTagType(format: string) {
  return format === 'csv' ? 'success'
    : format === 'docx' ? 'primary'
    : format === 'zip' ? 'warning'
    : 'info'
}

function statusTagType(status: string) {
  return status === 'ready' ? 'success'
    : status === 'failed' ? 'danger'
    : status === 'expired' ? 'warning'
    : 'info'
}

function statusLabel(status: string) {
  return t('export.status_' + status)
}

function handleDownload(row: ExportListItem) {
  const url = getExportDownloadUrl(row.id)
  window.location.href = url
  ElMessage.success(t('export.downloadStarted'))
}

async function loadData() {
  loading.value = true
  try {
    const wsId = workspaceStore.currentWorkspaceId
    if (!wsId) return
    const res = await fetchExportHistory(wsId, { page: page.value, size: size.value })
    items.value = res.data.data || []
    // total 从 header 或直接设置
    total.value = items.value.length // 简化，实际可从 response meta 取
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } } }
    ElMessage.error(e?.response?.data?.message || t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.export-log-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.pagination-bar {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
