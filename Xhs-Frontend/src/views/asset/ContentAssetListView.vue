<template>
  <div class="asset-list-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ t('asset.title') }}</h1>
        <p class="page-subtitle">{{ t('asset.subtitle') }}</p>
      </div>
      <el-button type="primary" @click="router.push('/asset/new')">
        {{ t('asset.newAsset') }}
      </el-button>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filters">
        <el-input
          v-model="filters.keyword"
          :placeholder="t('asset.keywordPlaceholder')"
          clearable
          class="filter-keyword"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="filters.status"
          :placeholder="t('asset.filterStatus')"
          clearable
          class="filter-select"
          @change="handleSearch"
        >
          <el-option :label="t('asset.filterAll')" value="" />
          <el-option
            v-for="s in statusOptions"
            :key="s"
            :label="t(`asset.statuses.${s}`)"
            :value="s"
          />
        </el-select>
        <el-button :loading="loading" @click="handleSearch">{{ t('common.refresh') }}</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="table-header">
          <span>{{ t('asset.total', { total }) }}</span>
        </div>
      </template>

      <el-skeleton v-if="loading && !assets.length" :rows="6" animated />
      <el-empty v-else-if="!assets.length" :description="t('asset.noRecords')">
        <el-button type="primary" @click="router.push('/asset/new')">
          {{ t('asset.newAsset') }}
        </el-button>
      </el-empty>
      <template v-else>
        <el-table :data="assets" stripe class="records-table" @row-click="goToDetail">
          <el-table-column prop="title" :label="t('asset.titleCol')" min-width="220">
            <template #default="{ row }">
              <span class="record-title">{{ row.title || t('history.untitled') }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" :label="t('asset.statusCol')" width="130">
            <template #default="{ row }">
              <AssetStatusBadge :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column :label="t('asset.versionCol')" width="90" align="center">
            <template #default="{ row }">
              <span class="version-badge">v{{ row.currentVersion }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="platform" :label="t('asset.platformCol')" width="120">
            <template #default="{ row }">
              {{ row.platform || '—' }}
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" :label="t('asset.updatedAt')" width="140">
            <template #default="{ row }">
              {{ formatRelativeTime(row.updatedAt, locale) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('history.actions')" width="180" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click.stop="goToDetail(row)">
                {{ t('history.view') }}
              </el-button>
              <el-button text type="danger" size="small" @click.stop="confirmDelete(row)">
                {{ t('history.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="loadAssets"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useWorkspaceChange } from '@/composables/useWorkspaceChange'
import {
  createAsset,
  deleteAsset,
  fetchAssets,
} from '@/api/asset'
import AssetStatusBadge from '@/components/asset/AssetStatusBadge.vue'
import { formatRelativeTime } from '@/utils/analysisDisplay'
import type { AssetStatus, ContentAsset } from '@/api/asset'

const { t, locale } = useI18n()
const router = useRouter()

const statusOptions: AssetStatus[] = [
  'draft', 'in_review', 'approved', 'scheduled', 'published', 'archived',
]

const filters = reactive({
  keyword: '',
  status: '' as AssetStatus | '',
})

const loading = ref(true)
const assets = ref<ContentAsset[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

function goToDetail(row: ContentAsset) {
  router.push(`/asset/${row.id}`)
}

function handleSearch() {
  page.value = 1
  loadAssets()
}

function handleSizeChange() {
  page.value = 1
  loadAssets()
}

async function loadAssets() {
  loading.value = true
  try {
    const res = await fetchAssets({
      page: page.value,
      size: pageSize.value,
      status: filters.status || undefined,
    })
    const data = res.data.data
    assets.value = data.items
    total.value = data.total
  } catch {
    assets.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function confirmDelete(row: ContentAsset) {
  try {
    await ElMessageBox.confirm(t('asset.deleteConfirm'), t('history.delete'), {
      type: 'warning',
      confirmButtonText: t('history.delete'),
    })
    await deleteAsset(row.id)
    ElMessage.success(t('history.deleteSuccess'))
    if (assets.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadAssets()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(t('history.deleteFailed'))
    }
  }
}

onMounted(loadAssets)
useWorkspaceChange(() => {
  page.value = 1
  loadAssets()
})
</script>

<style scoped>
.asset-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
  color: #111827;
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.filter-keyword {
  width: 240px;
}

.filter-select {
  width: 160px;
}

.table-header {
  font-size: 14px;
  color: #6b7280;
}

.records-table {
  cursor: pointer;
}

.record-title {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.version-badge {
  font-family: monospace;
  font-size: 12px;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 4px;
  padding: 1px 6px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
  }
  .filter-keyword,
  .filter-select {
    width: 100%;
  }
}
</style>
