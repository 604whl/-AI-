<template>
  <div class="workspace-settings">
    <!-- 工作区信息 -->
    <el-card class="workspace-info" :header="t('workspace.info')">
      <el-form
        v-if="canEdit"
        :model="editForm"
        label-width="120px"
        :disabled="saving"
      >
        <el-form-item :label="t('workspace.name')">
          <el-input v-model="editForm.name" :placeholder="t('workspace.namePlaceholder')" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('workspace.industry')">
          <el-input v-model="editForm.industry" :placeholder="t('workspace.industryPlaceholder')" maxlength="40" />
        </el-form-item>
        <el-form-item :label="t('workspace.region')">
          <el-input v-model="editForm.region" :placeholder="t('workspace.regionPlaceholder')" maxlength="8" />
        </el-form-item>
        <el-form-item :label="t('workspace.defaultLocale')">
          <el-input v-model="editForm.defaultLocale" placeholder="zh-CN" maxlength="16" />
        </el-form-item>
        <el-form-item :label="t('workspace.dailyQuota')">
          <el-input-number v-model="editForm.defaultQuota" :min="0" :max="10000" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSaveWorkspace">
            {{ t('common.save') }}
          </el-button>
          <el-button @click="resetForm">{{ t('common.cancel') }}</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-else :column="2" border>
        <el-descriptions-item :label="t('workspace.name')">
          {{ workspaceName }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('workspace.industry')">
          {{ currentWorkspace?.industry || '—' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('workspace.region')">
          {{ currentWorkspace?.region || '—' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('workspace.createdAt')">
          {{ currentWorkspace?.createdAt ? formatDate(currentWorkspace.createdAt) : '—' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 保留策略 -->
    <el-card class="retention-policy" :header="t('retention.retentionPolicy')">
      <p class="section-desc">{{ t('retention.retentionPolicyDesc') }}</p>

      <el-button type="primary" @click="router.push('/workspace/retention')">
        {{ t('retention.configurePolicy') }}
      </el-button>

      <div v-if="policy" class="policy-preview">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="t('retention.assetRetention')">
            {{ policy.assetRetentionDays }} {{ t('retention.days') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('retention.logRetention')">
            {{ policy.logRetentionDays }} {{ t('retention.days') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('retention.mediaRetention')">
            {{ policy.mediaRetentionDays }} {{ t('retention.days') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('retention.chatRetention')">
            {{ policy.chatSessionRetentionDays }} {{ t('retention.days') }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <!-- 数据导出 -->
    <el-card class="data-export" :header="t('retention.exportTitle')">
      <p class="section-desc">{{ t('retention.workspaceExportDesc') }}</p>

      <el-button type="primary" :loading="exporting" @click="handleExport">
        {{ t('retention.requestWorkspaceExport') }}
      </el-button>
    </el-card>

    <!-- 销毁工作区（危险区） -->
    <el-card class="danger-zone" :header="t('retention.dangerZone')">
      <div class="danger-content">
        <div class="danger-info">
          <h4>{{ t('retention.destroyWorkspace') }}</h4>
          <p>{{ t('retention.destroyWorkspaceDesc') }}</p>
          <p class="warning-text">{{ t('retention.destroyWarning') }}</p>
        </div>

        <!-- 无待处理销毁 -->
        <div v-if="!destructionStatus">
          <el-button type="danger" plain @click="showDestroyDialog = true">
            {{ t('retention.requestDestroy') }}
          </el-button>
        </div>

        <!-- 待确认销毁 -->
        <div v-else-if="destructionStatus.status === 'pending'" class="destruction-pending">
          <el-alert type="warning" :closable="false">
            <template #title>
              {{ t('retention.destroyPending', { days: destructionStatus.daysRemaining || 0 }) }}
            </template>
            <p>{{ t('retention.destroyPendingDesc') }}</p>
          </el-alert>
          <div class="destruction-actions">
            <el-button type="primary" :loading="confirming" @click="handleConfirmDestroy">
              {{ t('retention.confirmDestroyNow') }}
            </el-button>
            <el-button type="info" :loading="cancelling" @click="handleCancelDestroy">
              {{ t('retention.cancelDestroy') }}
            </el-button>
          </div>
        </div>

        <!-- 已确认待执行 -->
        <div v-else-if="destructionStatus.status === 'confirmed'" class="destruction-confirmed">
          <el-alert type="danger" :closable="false">
            <template #title>
              {{ t('retention.destroyConfirmed', { days: destructionStatus.daysRemaining || 0 }) }}
            </template>
            <p>{{ t('retention.destroyConfirmedDesc') }}</p>
          </el-alert>
          <el-button type="info" :loading="cancelling" @click="handleCancelDestroy">
            {{ t('retention.cancelDestroy') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 销毁确认弹窗 -->
    <DangerConfirmDialog
      v-model="showDestroyDialog"
      :title="t('retention.confirmDestroy')"
      :message="t('retention.confirmDestroyMessage')"
      :confirm-text="t('retention.confirmDestroyBtn')"
      :show-phrase-input="true"
      :loading="destroying"
      @confirm="handleRequestDestroy"
    />

    <!-- 二次确认弹窗 -->
    <DangerConfirmDialog
      v-model="showConfirmDialog"
      :title="t('retention.finalConfirm')"
      :message="t('retention.finalConfirmMessage')"
      :confirm-text="t('retention.finalConfirmBtn')"
      :show-phrase-input="true"
      :loading="confirming"
      @confirm="handleFinalConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { useWorkspaceStore } from '@/stores/workspace'
import DangerConfirmDialog from '@/components/DangerConfirmDialog.vue'
import {
  fetchDestructionStatus,
  requestWorkspaceDestruction,
  confirmWorkspaceDestruction,
  cancelWorkspaceDestruction,
  requestWorkspaceExport,
  fetchRetentionPolicy,
  type WorkspaceDestructionResponse,
  type RetentionPolicyResponse,
} from '@/api/retention'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const workspaceStore = useWorkspaceStore()

const workspaceId = computed(() => userStore.activeWorkspaceId || '')
const workspaceName = computed(() => userStore.activeWorkspace?.name || '')
const currentWorkspace = computed(() => workspaceStore.currentWorkspace)

// State
const destructionStatus = ref<WorkspaceDestructionResponse | null>(null)
const policy = ref<RetentionPolicyResponse | null>(null)
const showDestroyDialog = ref(false)
const showConfirmDialog = ref(false)
const exporting = ref(false)
const destroying = ref(false)
const confirming = ref(false)
const cancelling = ref(false)
const saving = ref(false)

const editForm = ref({
  name: '',
  industry: '',
  region: '',
  defaultLocale: '',
  defaultQuota: 100,
})

const canEdit = computed(() => {
  const role = workspaceStore.currentWorkspace?.role
  return role === 'OWNER' || role === 'ADMIN'
})

function resetForm() {
  const w = currentWorkspace.value
  editForm.value.name = w?.name ?? ''
  editForm.value.industry = w?.industry ?? ''
  editForm.value.region = w?.region ?? ''
  editForm.value.defaultLocale = w?.defaultLocale ?? ''
  editForm.value.defaultQuota = w?.defaultQuota ?? 100
}

async function handleSaveWorkspace() {
  if (!workspaceId.value) return
  saving.value = true
  try {
    await workspaceStore.updateWorkspace(workspaceId.value, editForm.value)
    await workspaceStore.fetchWorkspaceDetail(workspaceId.value)
    ElMessage.success(t('common.saveSuccess'))
  } catch (e) {
    ElMessage.error((e as Error).message || t('common.requestFailed'))
  } finally {
    saving.value = false
  }
}

// Load data
async function loadDestructionStatus() {
  if (!workspaceId.value) return
  try {
    const res = await fetchDestructionStatus(workspaceId.value)
    destructionStatus.value = res.data.data
  } catch (e) {
    console.error('Failed to load destruction status:', e)
  }
}

async function loadRetentionPolicy() {
  if (!workspaceId.value) return
  try {
    const res = await fetchRetentionPolicy(workspaceId.value)
    policy.value = res.data.data
  } catch (e) {
    console.error('Failed to load retention policy:', e)
  }
}

async function handleExport() {
  if (!workspaceId.value) return
  exporting.value = true
  try {
    await requestWorkspaceExport(workspaceId.value)
    ElMessage.success(t('retention.exportQueued'))
  } catch (e) {
    ElMessage.error((e as Error).message || t('common.requestFailed'))
  } finally {
    exporting.value = false
  }
}

async function handleRequestDestroy() {
  if (!workspaceId.value) return
  destroying.value = true
  try {
    const res = await requestWorkspaceDestruction(workspaceId.value, 'DELETE')
    destructionStatus.value = res.data.data
    ElMessage.success(t('retention.destroyRequested'))
    showDestroyDialog.value = false
  } catch (e) {
    ElMessage.error((e as Error).message || t('common.requestFailed'))
  } finally {
    destroying.value = false
  }
}

async function handleConfirmDestroy() {
  showConfirmDialog.value = true
}

async function handleFinalConfirm() {
  if (!workspaceId.value) return
  confirming.value = true
  try {
    const res = await confirmWorkspaceDestruction(workspaceId.value, 'DELETE')
    destructionStatus.value = res.data.data
    ElMessage.success(t('retention.destroyConfirmedSuccess'))
    showConfirmDialog.value = false
  } catch (e) {
    ElMessage.error((e as Error).message || t('common.requestFailed'))
  } finally {
    confirming.value = false
  }
}

async function handleCancelDestroy() {
  if (!workspaceId.value) return
  cancelling.value = true
  try {
    const res = await cancelWorkspaceDestruction(workspaceId.value)
    destructionStatus.value = res.data.data
    ElMessage.success(t('retention.destroyCancelled'))
  } catch (e) {
    ElMessage.error((e as Error).message || t('common.requestFailed'))
  } finally {
    cancelling.value = false
  }
}

function formatDate(iso: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}

onMounted(async () => {
  if (workspaceId.value) {
    await workspaceStore.fetchWorkspaceDetail(workspaceId.value)
  }
  resetForm()
  loadDestructionStatus()
  loadRetentionPolicy()
})
</script>

<style scoped>
.workspace-settings {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.workspace-info {
  margin-bottom: 20px;
}

.retention-policy,
.data-export {
  margin-bottom: 20px;
}

.section-desc {
  color: var(--el-text-color-secondary);
  margin: 0 0 16px 0;
  font-size: 14px;
}

.policy-preview {
  margin-top: 20px;
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

.destruction-pending,
.destruction-confirmed {
  flex: 1;
}

.destruction-pending .el-alert,
.destruction-confirmed .el-alert {
  margin-bottom: 12px;
}

.destruction-pending p,
.destruction-confirmed p {
  margin: 4px 0 0 0;
  font-size: 13px;
}

.destruction-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
</style>
