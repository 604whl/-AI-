<template>
  <div class="review-queue-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ t('review.queueTitle') }}</h1>
        <p class="page-subtitle">{{ t('review.queueSubtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-select v-model="statusFilter" :placeholder="t('review.filterStatus')" clearable class="filter-select">
          <el-option :label="t('review.filterAll')" value="" />
          <el-option :label="t('review.statuses.pending')" value="pending" />
          <el-option :label="t('review.statuses.in_review')" value="in_review" />
          <el-option :label="t('review.statuses.approved')" value="approved" />
          <el-option :label="t('review.statuses.rejected')" value="rejected" />
        </el-select>
        <el-button :loading="loading" @click="loadQueue">
          {{ t('common.refresh') }}
        </el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-skeleton v-if="loading && !items.length" :rows="6" animated />
      <el-empty v-else-if="!items.length" :description="t('review.queueEmpty')" />
      <template v-else>
        <el-table :data="items" stripe class="records-table" @row-click="goToDetail">
          <el-table-column :label="t('review.colRisk')" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isHighRisk" type="danger" size="small" effect="plain">
                {{ t('review.highRisk') }}
              </el-tag>
              <span v-else class="safe-text">{{ t('review.lowRisk') }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="assetTitle" :label="t('review.colTitle')" min-width="220">
            <template #default="{ row }">
              <span class="record-title">{{ row.assetTitle || t('history.untitled') }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" :label="t('review.colStatus')" width="130">
            <template #default="{ row }">
              <ReviewStatusBadge :status="row.status" :high-risk="row.isHighRisk" show-risk />
            </template>
          </el-table-column>
          <el-table-column :label="t('review.colVersion')" width="90" align="center">
            <template #default="{ row }">
              <span class="version-badge">v{{ row.versionNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="submitterNickname" :label="t('review.colSubmitter')" width="120">
            <template #default="{ row }">
              {{ row.submitterNickname || '—' }}
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" :label="t('review.colCreated')" width="140">
            <template #default="{ row }">
              {{ formatRelativeTime(row.createdAt, locale) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('history.actions')" width="120" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click.stop="goToDetail(row)">
                {{ t('review.viewDetail') }}
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
            @current-change="loadQueue"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useWorkspaceChange } from '@/composables/useWorkspaceChange'
import { fetchReviewQueue } from '@/api/review'
import ReviewStatusBadge from '@/components/review/ReviewStatusBadge.vue'
import { formatRelativeTime } from '@/utils/analysisDisplay'
import type { ReviewQueueItem, ReviewStatus } from '@/api/review'

const { t, locale } = useI18n()
const router = useRouter()

const statusFilter = ref<ReviewStatus | ''>('')
const loading = ref(false)
const items = ref<ReviewQueueItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

function goToDetail(row: ReviewQueueItem) {
  router.push(`/review/${row.id}`)
}

async function loadQueue() {
  loading.value = true
  try {
    const res = await fetchReviewQueue({
      status: statusFilter.value || undefined,
      page: page.value,
      size: pageSize.value,
    })
    const data = res.data.data
    items.value = data.items
    total.value = data.total
  } catch {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  page.value = 1
  loadQueue()
}

onMounted(loadQueue)
useWorkspaceChange(() => {
  page.value = 1
  loadQueue()
})
</script>

<style scoped>
.review-queue-page {
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
.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
.filter-select {
  width: 160px;
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
.safe-text {
  font-size: 12px;
  color: #10b981;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
