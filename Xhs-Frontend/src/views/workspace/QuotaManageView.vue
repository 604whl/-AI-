<template>
  <div class="quota-manage">
    <div class="quota-header">
      <h2>{{ t('quota.title') }}</h2>
      <el-button v-if="canManage" type="primary" :icon="Edit" @click="editDialogVisible = true">
        {{ t('quota.editQuota') }}
      </el-button>
    </div>

    <!-- 当前配额概览 -->
    <el-card class="quota-overview" shadow="never">
      <div class="quota-stats">
        <div class="quota-stat">
          <div class="stat-label">{{ t('quota.dailyQuota') }}</div>
          <div class="stat-value">{{ quota?.dailyQuota ?? '—' }}</div>
        </div>
        <div class="quota-stat">
          <div class="stat-label">{{ t('quota.usedToday') }}</div>
          <div class="stat-value">{{ quota?.used ?? 0 }}</div>
        </div>
        <div class="quota-stat">
          <div class="stat-label">{{ t('quota.remaining') }}</div>
          <div class="stat-value stat-remaining">{{ remaining }}</div>
        </div>
        <div class="quota-stat">
          <div class="stat-label">{{ t('quota.resetAt') }}</div>
          <div class="stat-value">{{ formatResetAt }}</div>
        </div>
      </div>

      <el-progress
        :percentage="usagePercent"
        :color="progressColor"
        :stroke-width="12"
        class="quota-progress"
      >
        <span class="progress-text">{{ quota?.used ?? 0 }} / {{ quota?.dailyQuota ?? 100 }}</span>
      </el-progress>
    </el-card>

    <!-- 审计 Ledger 列表 -->
    <el-card class="ledger-list" shadow="never">
      <template #header>
        <div class="ledger-header">
          <span>{{ t('quota.ledgerTitle') }}</span>
          <el-select
            v-model="filterAction"
            :placeholder="t('quota.filterAction')"
            clearable
            style="width: 140px"
            @change="loadLedger"
          >
            <el-option label="ANALYSIS" value="ANALYSIS" />
            <el-option label="BODY" value="BODY" />
            <el-option label="TITLE" value="TITLE" />
            <el-option label="COVER" value="COVER" />
            <el-option label="CHAT" value="CHAT" />
          </el-select>
        </div>
      </template>

      <el-table :data="ledgerRecords" v-loading="ledgerLoading" stripe>
        <el-table-column prop="action" :label="t('quota.action')" width="120" />
        <el-table-column prop="delta" :label="t('quota.delta')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.delta > 0 ? 'danger' : 'success'" size="small">
              {{ row.delta > 0 ? '+' : '' }}{{ row.delta }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('quota.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CONSUMED' ? 'danger' : 'success'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" :label="t('quota.reason')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createdAt" :label="t('quota.createdAt')" width="170">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="ledger-pagination">
        <el-pagination
          v-model:current-page="ledgerPage"
          :page-size="ledgerSize"
          :total="ledgerTotal"
          layout="prev, pager, next"
          @current-change="loadLedger"
        />
      </div>
    </el-card>

    <!-- 编辑配额弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="t('quota.editQuota')" width="380px">
      <el-form :model="editForm" label-position="top">
        <el-form-item :label="t('quota.dailyQuota')">
          <el-input-number v-model="editForm.dailyQuota" :min="1" :max="10000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="editLoading" @click="confirmEdit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Edit } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { getQuota, getLedger, updateQuota, type QuotaResponse, type LedgerEntry } from '@/api/quota'

const { t } = useI18n()
const userStore = useUserStore()

const workspaceId = computed(() => userStore.activeWorkspaceId ?? '')
const canManage = computed(() => {
  const r = userStore.activeWorkspace?.role
  return r === 'OWNER' || r === 'ADMIN'
})

// quota overview
const quota = ref<QuotaResponse | null>(null)
const quotaLoading = ref(false)

const remaining = computed(() => {
  if (!quota.value) return '—'
  return Math.max(quota.value.dailyQuota - quota.value.used, 0)
})

const usagePercent = computed(() => {
  if (!quota.value || quota.value.dailyQuota === 0) return 0
  return Math.min(100, Math.round((quota.value.used / quota.value.dailyQuota) * 100))
})

const progressColor = computed(() => {
  const p = usagePercent.value
  if (p >= 90) return '#F56C6C'
  if (p >= 70) return '#E6A23C'
  return '#67C23A'
})

const formatResetAt = computed(() => {
  if (!quota.value?.resetAt) return '—'
  return new Date(quota.value.resetAt).toLocaleString()
})

// ledger
const ledgerRecords = ref<LedgerEntry[]>([])
const ledgerLoading = ref(false)
const ledgerPage = ref(1)
const ledgerSize = ref(20)
const ledgerTotal = ref(0)
const filterAction = ref('')

async function loadQuota() {
  if (!workspaceId.value) return
  quotaLoading.value = true
  try {
    const res = await getQuota(workspaceId.value)
    quota.value = res.data.data
  } catch {
    quota.value = null
  } finally {
    quotaLoading.value = false
  }
}

async function loadLedger() {
  if (!workspaceId.value) return
  ledgerLoading.value = true
  try {
    const res = await getLedger(workspaceId.value, {
      page: ledgerPage.value,
      size: ledgerSize.value,
      action: filterAction.value || undefined,
    })
    ledgerRecords.value = res.data.data?.records ?? []
    ledgerTotal.value = res.data.data?.total ?? 0
  } catch {
    ledgerRecords.value = []
    ledgerTotal.value = 0
  } finally {
    ledgerLoading.value = false
  }
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString()
}

// edit
const editDialogVisible = ref(false)
const editLoading = ref(false)
const editForm = ref({ dailyQuota: 100 })

function openEditDialog() {
  editForm.value.dailyQuota = quota.value?.dailyQuota ?? 100
}

async function confirmEdit() {
  if (!workspaceId.value) return
  editLoading.value = true
  try {
    await updateQuota(workspaceId.value, editForm.value.dailyQuota)
    editDialogVisible.value = false
    ElMessage.success(t('quota.updated'))
    await loadQuota()
  } catch (e) {
    ElMessage.error((e as Error).message || 'update failed')
  } finally {
    editLoading.value = false
  }
}

// watch workspace changes
onMounted(() => {
  loadQuota()
  loadLedger()
})

// reload on workspace switch
window.addEventListener('xhs:workspace-changed', () => {
  loadQuota()
  loadLedger()
})

// expose for external control
defineExpose({ openEditDialog })
</script>

<style scoped>
.quota-manage {
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
}

.quota-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.quota-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.quota-overview {
  margin-bottom: 20px;
}

.quota-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.quota-stat {
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.stat-remaining {
  color: var(--el-color-success);
}

.quota-progress {
  margin-top: 8px;
}

.progress-text {
  font-size: 13px;
  font-weight: 500;
}

.ledger-list {
  margin-bottom: 20px;
}

.ledger-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ledger-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
