<template>
  <div class="account-delete-view">
    <el-card class="danger-card">
      <template #header>
        <div class="card-header">
          <el-icon color="#F56C6C"><WarningFilled /></el-icon>
          <span>{{ t('accountDelete.title') }}</span>
        </div>
      </template>

      <!-- 正常状态：显示注销按钮 -->
      <div v-if="!isScheduled" class="action-section">
        <p class="warning-text">{{ t('accountDelete.description') }}</p>
        <el-alert type="warning" :closable="false" class="warning-alert">
          <template #title>
            <ul class="warning-list">
              <li>{{ t('accountDelete.warning1') }}</li>
              <li>{{ t('accountDelete.warning2') }}</li>
              <li>{{ t('accountDelete.warning3') }}</li>
            </ul>
          </template>
        </el-alert>
        <div class="confirm-input">
          <el-input
            v-model="confirmPhrase"
            :placeholder="t('accountDelete.confirmPlaceholder')"
            maxlength="32"
            show-word-limit
          />
        </div>
        <el-button
          type="danger"
          :disabled="confirmPhrase !== 'DELETE'"
          :loading="loading"
          @click="handleRequestDeletion"
        >
          {{ t('accountDelete.requestBtn') }}
        </el-button>
      </div>

      <!-- 已申请注销：显示倒计时和取消按钮 -->
      <div v-else class="scheduled-section">
        <div class="countdown-display">
          <div class="countdown-number">{{ daysRemaining }}</div>
          <div class="countdown-label">{{ t('accountDelete.daysRemaining') }}</div>
        </div>
        <p class="scheduled-info">
          {{ t('accountDelete.scheduledAt') }}: {{ formatDate(status?.scheduledDeletionAt) }}
        </p>
        <p class="scheduled-hint">{{ t('accountDelete.hint') }}</p>
        <div class="scheduled-actions">
          <el-button type="success" :loading="loading" @click="handleCancel">
            {{ t('accountDelete.cancelBtn') }}
          </el-button>
          <el-button @click="router.push('/settings/danger-zone?tab=export')">
            {{ t('accountDelete.exportBtn') }}
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import {
  fetchDeletionStatus,
  requestAccountDeletion,
  cancelAccountDeletion,
  type DeletionStatusResponse,
} from '@/api/retention'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const confirmPhrase = ref('')
const status = ref<DeletionStatusResponse | null>(null)

const isScheduled = computed(() => status.value?.deletionStatus === 'scheduled')
const daysRemaining = computed(() => status.value?.daysRemaining ?? 0)

function formatDate(iso: string | null | undefined) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}

async function loadStatus() {
  try {
    const res = await fetchDeletionStatus()
    status.value = res.data.data
  } catch {
    status.value = null
  }
}

async function handleRequestDeletion() {
  if (confirmPhrase.value !== 'DELETE') return
  try {
    await ElMessageBox.confirm(
      t('accountDelete.confirmText'),
      t('accountDelete.confirmTitle'),
      { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
    )
  } catch {
    return
  }
  loading.value = true
  try {
    await requestAccountDeletion('DELETE')
    ElMessage.success(t('accountDelete.requested'))
    await loadStatus()
  } catch (e: unknown) {
    const err = e as { message?: string }
    ElMessage.error(err?.message || t('common.requestFailed'))
  } finally {
    loading.value = false
  }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm(
      t('accountDelete.cancelConfirmText'),
      t('accountDelete.cancelConfirmTitle'),
      { type: 'info', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
    )
  } catch {
    return
  }
  loading.value = true
  try {
    await cancelAccountDeletion()
    ElMessage.success(t('accountDelete.cancelled'))
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
.account-delete-view {
  padding: 24px;
  max-width: 600px;
  margin: 0 auto;
}

.danger-card {
  border-color: #fde2e2;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.warning-text {
  color: var(--el-text-color-regular);
  margin-bottom: 16px;
  line-height: 1.6;
}

.warning-list {
  margin: 0;
  padding-left: 16px;
  line-height: 1.8;
}

.warning-alert {
  margin-bottom: 16px;
}

.confirm-input {
  margin-bottom: 16px;
  max-width: 300px;
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

.scheduled-info {
  text-align: center;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.scheduled-hint {
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-bottom: 20px;
}

.scheduled-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
