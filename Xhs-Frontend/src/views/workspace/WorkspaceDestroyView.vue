<template>
  <div class="workspace-destroy-view">
    <el-card class="danger-card">
      <template #header>
        <div class="card-header">
          <el-icon color="#F56C6C"><WarningFilled /></el-icon>
          <span>{{ t('workspaceDestroy.title') }}</span>
        </div>
      </template>

      <!-- 无待销毁记录：显示申请按钮 -->
      <div v-if="!destruction" class="action-section">
        <p class="description">{{ t('workspaceDestroy.description') }}</p>
        <el-alert type="error" :closable="false" class="danger-alert">
          <template #title>
            <ul class="warning-list">
              <li>{{ t('workspaceDestroy.warning1') }}</li>
              <li>{{ t('workspaceDestroy.warning2') }}</li>
              <li>{{ t('workspaceDestroy.warning3') }}</li>
              <li>{{ t('workspaceDestroy.warning4') }}</li>
            </ul>
          </template>
        </el-alert>
        <div class="confirm-input">
          <el-input
            v-model="confirmPhrase"
            :placeholder="t('workspaceDestroy.confirmPlaceholder')"
            maxlength="32"
            show-word-limit
          />
        </div>
        <el-button
          type="danger"
          :disabled="confirmPhrase !== 'DELETE'"
          :loading="loading"
          @click="handleRequestDestroy"
        >
          {{ t('workspaceDestroy.requestBtn') }}
        </el-button>
      </div>

      <!-- 待确认（pending）：显示待确认提示 -->
      <div v-else-if="destruction.status === 'pending'" class="pending-section">
        <div class="status-badge pending">{{ t('workspaceDestroy.status_pending') }}</div>
        <p class="info-text">{{ t('workspaceDestroy.pendingHint') }}</p>
        <div class="confirm-input">
          <el-input
            v-model="confirmPhrase"
            :placeholder="t('workspaceDestroy.confirmPlaceholder')"
            maxlength="32"
            show-word-limit
          />
        </div>
        <div class="action-buttons">
          <el-button
            type="danger"
            :disabled="confirmPhrase !== 'DELETE'"
            :loading="loading"
            @click="handleConfirmDestroy"
          >
            {{ t('workspaceDestroy.confirmBtn') }}
          </el-button>
          <el-button type="default" :loading="loading" @click="handleCancel">
            {{ t('workspaceDestroy.cancelBtn') }}
          </el-button>
        </div>
      </div>

      <!-- 已确认（confirmed）：显示倒计时 -->
      <div v-else-if="destruction.status === 'confirmed'" class="confirmed-section">
        <div class="status-badge confirmed">{{ t('workspaceDestroy.status_confirmed') }}</div>
        <div class="countdown-display">
          <div class="countdown-number">{{ destruction.daysRemaining }}</div>
          <div class="countdown-label">{{ t('workspaceDestroy.daysRemaining') }}</div>
        </div>
        <p class="info-text">
          {{ t('workspaceDestroy.executeAt') }}: {{ formatDate(destruction.executeAt) }}
        </p>
        <el-button type="default" :loading="loading" @click="handleCancel">
          {{ t('workspaceDestroy.cancelBtn') }}
        </el-button>
      </div>

      <!-- 已取消 -->
      <div v-else-if="destruction.status === 'cancelled'" class="cancelled-section">
        <div class="status-badge cancelled">{{ t('workspaceDestroy.status_cancelled') }}</div>
        <p class="info-text">{{ t('workspaceDestroy.cancelledHint') }}</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import {
  fetchDestructionStatus,
  requestWorkspaceDestruction,
  confirmWorkspaceDestruction,
  cancelWorkspaceDestruction,
  type WorkspaceDestructionResponse,
} from '@/api/retention'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const confirmPhrase = ref('')
const destruction = ref<WorkspaceDestructionResponse | null>(null)

const workspaceId = () => userStore.activeWorkspaceId ?? ''

function formatDate(iso: string | null | undefined) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}

async function loadStatus() {
  const wsId = workspaceId()
  if (!wsId) return
  try {
    const res = await fetchDestructionStatus(wsId)
    destruction.value = res.data.data
  } catch {
    destruction.value = null
  }
}

async function handleRequestDestroy() {
  if (confirmPhrase.value !== 'DELETE') return
  const wsId = workspaceId()
  if (!wsId) return
  try {
    await ElMessageBox.confirm(
      t('workspaceDestroy.confirmText'),
      t('workspaceDestroy.confirmTitle'),
      { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
    )
  } catch {
    return
  }
  loading.value = true
  try {
    await requestWorkspaceDestruction(wsId, 'DELETE')
    ElMessage.success(t('workspaceDestroy.requested'))
    await loadStatus()
  } catch (e: unknown) {
    const err = e as { message?: string }
    ElMessage.error(err?.message || t('common.requestFailed'))
  } finally {
    loading.value = false
  }
}

async function handleConfirmDestroy() {
  if (confirmPhrase.value !== 'DELETE') return
  const wsId = workspaceId()
  if (!wsId) return
  try {
    await ElMessageBox.confirm(
      t('workspaceDestroy.confirmText2'),
      t('workspaceDestroy.confirmTitle'),
      { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
    )
  } catch {
    return
  }
  loading.value = true
  try {
    await confirmWorkspaceDestruction(wsId, 'DELETE')
    ElMessage.success(t('workspaceDestroy.confirmed'))
    await loadStatus()
  } catch (e: unknown) {
    const err = e as { message?: string }
    ElMessage.error(err?.message || t('common.requestFailed'))
  } finally {
    loading.value = false
  }
}

async function handleCancel() {
  const wsId = workspaceId()
  if (!wsId) return
  try {
    await ElMessageBox.confirm(
      t('workspaceDestroy.cancelConfirmText'),
      t('workspaceDestroy.cancelConfirmTitle'),
      { type: 'info', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
    )
  } catch {
    return
  }
  loading.value = true
  try {
    await cancelWorkspaceDestruction(wsId)
    ElMessage.success(t('workspaceDestroy.cancelled'))
    await loadStatus()
  } catch (e: unknown) {
    const err = e as { message?: string }
    ElMessage.error(err?.message || t('common.requestFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadStatus)
</script>

<style scoped>
.workspace-destroy-view {
  padding: 24px;
  max-width: 600px;
  margin: 0 auto;
}

.danger-card { border-color: #fde2e2; }

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.description {
  color: var(--el-text-color-regular);
  line-height: 1.6;
  margin-bottom: 16px;
}

.warning-list {
  margin: 0;
  padding-left: 16px;
  line-height: 1.8;
}

.danger-alert { margin-bottom: 16px; }

.confirm-input {
  margin-bottom: 16px;
  max-width: 300px;
}

.status-badge {
  display: inline-block;
  padding: 4px 16px;
  border-radius: 4px;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 16px;
}

.status-badge.pending {
  background: #fef0f0;
  color: #f56c6c;
  border: 1px solid #fde2e2;
}

.status-badge.confirmed {
  background: #fdf6ec;
  color: #e6a23c;
  border: 1px solid #f6e7cc;
}

.status-badge.cancelled {
  background: #f4f4f5;
  color: #909399;
  border: 1px solid #e4e7ed;
}

.info-text {
  color: var(--el-text-color-regular);
  margin-bottom: 16px;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.countdown-display {
  text-align: center;
  margin-bottom: 20px;
}

.countdown-number {
  font-size: 72px;
  font-weight: 800;
  color: #f56c6c;
  line-height: 1;
}

.countdown-label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
}
</style>
