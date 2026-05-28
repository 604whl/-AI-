<template>
  <div class="history-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ t('history.title') }}</h1>
        <p class="page-subtitle">{{ t('history.subtitle') }}</p>
      </div>
      <el-button type="primary" @click="router.push('/analysis/new')">
        {{ t('history.newAnalysis') }}
      </el-button>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filters">
        <el-input
          v-model="filters.keyword"
          :placeholder="t('history.keywordPlaceholder')"
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
          :placeholder="t('history.filterStatus')"
          clearable
          class="filter-select"
          @change="handleSearch"
        >
          <el-option :label="t('history.filterAll')" value="" />
          <el-option
            v-for="status in statusOptions"
            :key="status"
            :label="statusLabel(status)"
            :value="status"
          />
        </el-select>
        <el-select
          v-model="filters.scenario"
          :placeholder="t('history.filterScenario')"
          clearable
          class="filter-select"
          @change="handleSearch"
        >
          <el-option :label="t('history.filterAll')" value="" />
          <el-option
            v-for="scenario in scenarioOptions"
            :key="scenario"
            :label="scenarioLabel(scenario)"
            :value="scenario"
          />
        </el-select>
        <el-button :loading="loading" @click="handleSearch">{{ t('history.refresh') }}</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="table-header">
          <span>{{ t('history.total', { total }) }}</span>
        </div>
      </template>

      <el-skeleton v-if="loading && !records.length" :rows="6" animated />
      <el-empty v-else-if="!records.length" :description="t('history.noRecords')">
        <el-button type="primary" @click="router.push('/analysis/new')">
          {{ t('history.newAnalysis') }}
        </el-button>
      </el-empty>
      <template v-else>
        <el-table :data="records" stripe class="records-table" @row-click="goToReport">
          <el-table-column prop="title" :label="t('analysis.title')" min-width="220">
            <template #default="{ row }">
              <span class="record-title">{{ row.title || t('history.untitled') }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="scenario" :label="t('analysis.scenario')" width="120">
            <template #default="{ row }">
              {{ scenarioLabel(row.scenario) }}
            </template>
          </el-table-column>
          <el-table-column prop="persona" :label="t('history.persona')" width="110">
            <template #default="{ row }">
              {{ row.persona ? t(`persona.${row.persona}`) : '—' }}
            </template>
          </el-table-column>
          <el-table-column prop="status" :label="t('history.status')" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('history.score')" width="90" align="center">
            <template #default="{ row }">
              <span v-if="averageReportScore(row.report) !== null" class="score-badge">
                {{ averageReportScore(row.report) }}
              </span>
              <span v-else class="score-empty">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" :label="t('history.createdAt')" width="140">
            <template #default="{ row }">
              {{ formatRelativeTime(row.createdAt, locale) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('history.actions')" width="140" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click.stop="goToReport(row)">
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
            @current-change="loadRecords"
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
import { deleteAnalysis, fetchAnalysisList } from '@/api/analysis'
import type { AnalysisListItem, AnalysisScenario, AnalysisStatus } from '@/types/api'
import { averageReportScore, formatRelativeTime } from '@/utils/analysisDisplay'

const { t, locale } = useI18n()
const router = useRouter()

const statusOptions: AnalysisStatus[] = ['pending', 'processing', 'completed', 'failed']
const scenarioOptions: AnalysisScenario[] = ['draft', 'published', 'competitor']

const filters = reactive({
  keyword: '',
  status: '' as AnalysisStatus | '',
  scenario: '' as AnalysisScenario | '',
})

const loading = ref(true)
const records = ref<AnalysisListItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

function scenarioLabel(scenario: AnalysisScenario) {
  const map: Record<AnalysisScenario, string> = {
    draft: t('analysis.scenarioDraft'),
    published: t('analysis.scenarioPublished'),
    competitor: t('analysis.scenarioCompetitor'),
  }
  return map[scenario]
}

function statusLabel(status: AnalysisStatus) {
  return t(`history.statuses.${status}`)
}

function statusTagType(status: AnalysisStatus) {
  const map: Record<AnalysisStatus, 'success' | 'warning' | 'danger' | 'info'> = {
    completed: 'success',
    processing: 'warning',
    pending: 'info',
    failed: 'danger',
  }
  return map[status]
}

function goToReport(row: AnalysisListItem) {
  router.push(`/analysis/${row.id}`)
}

function handleSearch() {
  page.value = 1
  loadRecords()
}

function handleSizeChange() {
  page.value = 1
  loadRecords()
}

async function loadRecords() {
  loading.value = true
  try {
    const res = await fetchAnalysisList({
      page: page.value,
      size: pageSize.value,
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      scenario: filters.scenario || undefined,
    })
    const data = res.data.data
    records.value = data.items
    total.value = data.total
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function confirmDelete(row: AnalysisListItem) {
  try {
    await ElMessageBox.confirm(t('history.deleteConfirm'), t('history.delete'), {
      type: 'warning',
      confirmButtonText: t('history.delete'),
    })
    await deleteAnalysis(row.id)
    ElMessage.success(t('history.deleteSuccess'))
    if (records.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadRecords()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(t('history.deleteFailed'))
    }
  }
}

onMounted(loadRecords)
</script>

<style scoped>
.history-page {
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
  width: 140px;
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

.score-badge {
  font-weight: 700;
  color: #ff2442;
}

.score-empty {
  color: #d1d5db;
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
