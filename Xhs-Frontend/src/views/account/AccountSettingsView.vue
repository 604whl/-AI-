<template>
  <div class="account-settings">
    <!-- 账号信息 -->
    <el-card class="account-info" :header="t('settings.accountInfo')">
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('settings.email')">
          {{ userStore.profile?.email || '—' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('settings.displayName')">
          {{ userStore.profile?.displayName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('settings.defaultPersona')">
          {{ t(`persona.${userStore.profile?.defaultPersona || 'agency'}`) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('settings.memberSince')">
          {{ userStore.profile?.createdAt ? formatDate(userStore.profile.createdAt) : '—' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 个人数据导出 -->
    <el-card class="data-export" :header="t('retention.exportTitle')">
      <p class="section-desc">{{ t('retention.exportDesc') }}</p>

      <el-button type="primary" :loading="exporting" @click="handleExport">
        {{ t('retention.requestExport') }}
      </el-button>

      <!-- 导出历史 -->
      <div v-if="exports.length > 0" class="export-history">
        <h4>{{ t('retention.exportHistory') }}</h4>
        <el-table :data="exports" stripe size="small">
          <el-table-column :label="t('retention.exportType')" prop="exportType" width="140">
            <template #default="{ row }">
              {{ row.exportType === 'personal_data' ? t('retention.personalData') : t('retention.workspaceData') }}
            </template>
          </el-table-column>
          <el-table-column :label="t('retention.status')" prop="status" width="120">
            <template #default="{ row }">
              <el-tag :type="getExportStatusType(row.status)" size="small">
                {{ t(`retention.status_${row.status}`) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('retention.createdAt')" width="170">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('retention.actions')" width="100">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'ready' && row.downloadUrl"
                type="primary"
                link
                size="small"
                @click="downloadExport(row)"
              >
                {{ t('retention.download') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 账号注销（危险区） -->
    <el-card class="danger-zone" :header="t('retention.dangerZone')">
      <div class="danger-content">
        <div class="danger-info">
          <h4>{{ t('retention.deleteAccount') }}</h4>
          <p>{{ t('retention.deleteAccountDesc') }}</p>
          <p class="warning-text">{{ t('retention.deleteWarning') }}</p>
        </div>

        <!-- 无待处理注销 -->
        <div v-if="!deletionStatus || deletionStatus.deletionStatus === 'active'">
          <el-button type="danger" plain @click="showDeleteDialog = true">
            {{ t('retention.requestDelete') }}
          </el-button>
        </div>

        <!-- 注销倒计时中 -->
        <div v-else-if="deletionStatus.deletionStatus === 'pending_deletion'" class="pending-deletion">
          <el-alert type="warning" :closable="false">
            <template #title>
              {{ t('retention.deletionInProgress', { days: deletionStatus.daysRemaining || 0 }) }}
            </template>
            <p>{{ t('retention.deletionInProgressDesc') }}</p>
            <p v-if="deletionStatus.scheduledDeletionAt">
              {{ t('retention.scheduledAt') }}: {{ formatDate(deletionStatus.scheduledDeletionAt) }}
            </p>
          </el-alert>
          <el-button type="success" class="cancel-btn" @click="handleCancelDeletion">
            {{ t('retention.cancelDeletion') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 注销确认弹窗 -->
    <DangerConfirmDialog
      v-model="showDeleteDialog"
      :title="t('retention.confirmDelete')"
      :message="t('retention.confirmDeleteMessage')"
      :confirm-text="t('retention.confirmDeleteBtn')"
      :show-phrase-input="true"
      :loading="deleting"
      @confirm="handleDeleteAccount"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import DangerConfirmDialog from '@/components/DangerConfirmDialog.vue'
import {
  getDeletionStatus,
  requestAccountDeletion,
  cancelAccountDeletion,
  requestPersonalExport,
  listMyExports,
  type DeletionStatusResponse,
  type ExportRequestResponse,
} from '@/api/retention'

const { t } = useI18n()
const userStore = useUserStore()

// State
const deletionStatus = ref<DeletionStatusResponse | null>(null)
const exports = ref<ExportRequestResponse[]>([])
const showDeleteDialog = ref(false)
const exporting = ref(false)
const deleting = ref(false)

// Load data
async function loadDeletionStatus() {
  try {
    const res = await getDeletionStatus()
    deletionStatus.value = res.data.data
  } catch (e) {
    console.error('Failed to load deletion status:', e)
  }
}

async function loadExports() {
  try {
    const res = await listMyExports()
    exports.value = res.data.data || []
  } catch (e) {
    console.error('Failed to load exports:', e)
  }
}

async function handleExport() {
  exporting.value = true
  try {
    await requestPersonalExport()
    ElMessage.success(t('retention.exportQueued'))
    await loadExports()
  } catch (e) {
    ElMessage.error((e as Error).message || t('common.requestFailed'))
  } finally {
    exporting.value = false
  }
}

async function handleDeleteAccount() {
  deleting.value = true
  try {
    await requestAccountDeletion('DELETE')
    ElMessage.success(t('retention.deletionScheduled'))
    showDeleteDialog.value = false
    await loadDeletionStatus()
  } catch (e) {
    ElMessage.error((e as Error).message || t('common.requestFailed'))
  } finally {
    deleting.value = false
  }
}

async function handleCancelDeletion() {
  try {
    await cancelAccountDeletion()
    ElMessage.success(t('retention.deletionCancelled'))
    await loadDeletionStatus()
  } catch (e) {
    ElMessage.error((e as Error).message || t('common.requestFailed'))
  }
}

function downloadExport(row: ExportRequestResponse) {
  if (row.downloadUrl) {
    window.open(row.downloadUrl, '_blank')
  }
}

function getExportStatusType(status: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 'ready': return 'success'
    case 'processing': return 'warning'
    case 'failed': return 'danger'
    default: return 'info'
  }
}

function formatDate(iso: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}

onMounted(() => {
  loadDeletionStatus()
  loadExports()
})
</script>

<style scoped>
.account-settings {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.account-info {
  margin-bottom: 20px;
}

.data-export {
  margin-bottom: 20px;
}

.section-desc {
  color: var(--el-text-color-secondary);
  margin: 0 0 16px 0;
  font-size: 14px;
}

.export-history {
  margin-top: 24px;
}

.export-history h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.danger-zone {
  border: 1px solid var(--el-color-danger-light-3);
}

.danger-zone :deep(.el-card__header) {
  color: var(--el-color-danger);
}

.danger-content {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.danger-info h4 {
  margin: 0 0 8px 0;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.danger-info p {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.warning-text {
  color: var(--el-color-warning) !important;
  font-size: 12px !important;
}

.pending-deletion {
  flex: 1;
}

.pending-deletion .el-alert {
  margin-bottom: 12px;
}

.pending-deletion p {
  margin: 4px 0 0 0;
  font-size: 13px;
}

.cancel-btn {
  margin-top: 12px;
}
</style>
