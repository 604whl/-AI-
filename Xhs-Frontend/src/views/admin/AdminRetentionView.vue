<template>
  <div class="admin-retention-view">
    <div class="page-header">
      <h2>{{ t('adminRetention.title') }}</h2>
      <el-button type="primary" :loading="sweeperRunning" @click="handleRunSweeper">
        {{ t('adminRetention.runSweeper') }}
      </el-button>
    </div>

    <!-- 待删账号 -->
    <el-card shadow="never">
      <template #header>
        <span class="section-title">{{ t('adminRetention.scheduledDeletions') }}</span>
      </template>
      <el-table :data="deletions" v-loading="loadingDeletions" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="email" label="Email" min-width="180" />
        <el-table-column prop="displayName" label="Display Name" width="150" />
        <el-table-column :label="t('adminRetention.colScheduledAt')" width="170">
          <template #default="{ row }">
            {{ formatDate(row.scheduledDeletionAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="deletionStatus" label="Status" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ row.deletionStatus }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="deletionPage"
          :page-size="pageSize"
          :total="deletionTotal"
          layout="prev, pager, next"
          @current-change="loadDeletions"
        />
      </div>
    </el-card>

    <!-- 待删 Workspace -->
    <el-card shadow="never">
      <template #header>
        <span class="section-title">{{ t('adminRetention.pendingDestructions') }}</span>
      </template>
      <el-table :data="destructions" v-loading="loadingDestructions" stripe>
        <el-table-column prop="workspaceId" label="Workspace ID" width="240" show-overflow-tooltip />
        <el-table-column prop="workspaceName" label="Name" width="200" />
        <el-table-column :label="t('adminRetention.colExecuteAt')" width="170">
          <template #default="{ row }">
            {{ formatDate(row.executeAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="Status" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'confirmed' ? 'warning' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('adminRetention.colRequestedAt')" width="170">
          <template #default="{ row }">
            {{ formatDate(row.requestedAt) }}
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="destructionPage"
          :page-size="pageSize"
          :total="destructionTotal"
          layout="prev, pager, next"
          @current-change="loadDestructions"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const { t } = useI18n()
const pageSize = 20

// Deletions
const loadingDeletions = ref(false)
const deletionPage = ref(1)
const deletionTotal = ref(0)
const deletions = ref<any[]>([])

// Destructions
const loadingDestructions = ref(false)
const destructionPage = ref(1)
const destructionTotal = ref(0)
const destructions = ref<any[]>([])

// Sweeper
const sweeperRunning = ref(false)

function formatDate(iso: string | null | undefined) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}

async function loadDeletions() {
  loadingDeletions.value = true
  try {
    const res = await http.get('/admin/retention/scheduled-deletions', {
      params: { page: deletionPage.value - 1, size: pageSize },
    })
    const data = res.data.data
    deletions.value = data?.items ?? []
    deletionTotal.value = data?.total ?? 0
  } catch {
    deletions.value = []
  } finally {
    loadingDeletions.value = false
  }
}

async function loadDestructions() {
  loadingDestructions.value = true
  try {
    const res = await http.get('/admin/retention/pending-destructions', {
      params: { page: destructionPage.value - 1, size: pageSize },
    })
    const data = res.data.data
    destructions.value = data?.items ?? []
    destructionTotal.value = data?.total ?? 0
  } catch {
    destructions.value = []
  } finally {
    loadingDestructions.value = false
  }
}

async function handleRunSweeper() {
  sweeperRunning.value = true
  try {
    await http.post('/admin/retention/run-sweeper')
    ElMessage.success(t('adminRetention.sweeperTriggered'))
    await Promise.all([loadDeletions(), loadDestructions()])
  } catch (e: unknown) {
    const err = e as { message?: string }
    ElMessage.error(err?.message || t('common.requestFailed'))
  } finally {
    sweeperRunning.value = false
  }
}

onMounted(() => {
  loadDeletions()
  loadDestructions()
})
</script>

<style scoped>
.admin-retention-view {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.section-title {
  font-weight: 600;
  font-size: 15px;
}

.pagination-bar {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
