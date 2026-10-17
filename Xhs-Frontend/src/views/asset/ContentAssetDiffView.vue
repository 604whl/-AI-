<template>
  <div class="asset-diff-page">
    <div class="page-header">
      <el-button text @click="router.push(`/asset/${assetId}`)">
        <el-icon><ArrowLeft /></el-icon>
        {{ t('report.back') }}
      </el-button>
      <h2 class="page-title">{{ t('asset.diffTitle') }}</h2>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="version-selectors">
          <el-select v-model.number="v1" :placeholder="t('asset.versionSelect')" class="version-select">
            <el-option
              v-for="v in versionList"
              :key="v.versionNo"
              :label="`v${v.versionNo}`"
              :value="v.versionNo"
            />
          </el-select>
          <span class="vs-label">vs</span>
          <el-select v-model.number="v2" :placeholder="t('asset.versionSelect')" class="version-select">
            <el-option
              v-for="v in versionList"
              :key="v.versionNo"
              :label="`v${v.versionNo}`"
              :value="v.versionNo"
            />
          </el-select>
          <el-button :disabled="!v1 || !v2 || v1 === v2 || loading" :loading="loading" @click="loadDiff">
            {{ t('asset.compare') }}
          </el-button>
        </div>
      </template>

      <el-skeleton v-if="loading && !diffLines.length" :rows="6" animated />
      <div v-else-if="diffLines.length" class="diff-container">
        <div
          v-for="(line, i) in diffLines"
          :key="i"
          class="diff-line"
          :class="`diff-${line.type}`"
        >
          <span class="diff-marker">{{ diffMarker(line.type) }}</span>
          <span class="diff-content">{{ line.content }}</span>
        </div>
      </div>
      <el-empty v-else :description="t('asset.noDiff')" />
    </el-card>

    <!-- Version list -->
    <el-card shadow="never">
      <template #header>
        <span>{{ t('asset.versionHistory') }}</span>
      </template>
      <el-table :data="versionList" stripe size="small">
        <el-table-column :label="t('asset.versionCol')" width="80">
          <template #default="{ row }">
            <span class="version-badge">v{{ row.versionNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="t('analysis.title')" min-width="180" />
        <el-table-column prop="editReason" :label="t('asset.editReason')" min-width="120" />
        <el-table-column prop="createdAt" :label="t('asset.createdAt')" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt, locale) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('history.actions')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="goToVersion(row.versionNo)">
              {{ t('asset.view') }}
            </el-button>
            <el-button text type="warning" size="small" @click="confirmRollback(row.versionNo)">
              {{ t('asset.rollback') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { fetchAssetVersions, rollbackAsset, diffAssetVersions } from '@/api/asset'
import { formatDateTime } from '@/utils/analysisDisplay'
import type { AssetVersion, DiffLine } from '@/api/asset'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const assetId = route.params.id as string

const versionList = ref<AssetVersion[]>([])
const diffLines = ref<DiffLine[]>([])
const v1 = ref<number | null>(null)
const v2 = ref<number | null>(null)
const loading = ref(false)

function diffMarker(type: DiffLine['type']) {
  switch (type) {
    case 'added': return '+'
    case 'removed': return '-'
    case 'modified': return '~'
    default: return ' '
  }
}

async function loadVersions() {
  try {
    const res = await fetchAssetVersions(assetId)
    versionList.value = res.data.data
  } catch {
    versionList.value = []
  }
}

async function loadDiff() {
  if (!v1.value || !v2.value) return
  loading.value = true
  try {
    const res = await diffAssetVersions(assetId, v1.value, v2.value)
    diffLines.value = res.data.data
  } catch {
    diffLines.value = []
  } finally {
    loading.value = false
  }
}

function goToVersion(versionNo: number) {
  v1.value = versionNo
  v2.value = versionNo
  loadDiff()
}

async function confirmRollback(versionNo: number) {
  try {
    await ElMessageBox.confirm(
      t('asset.rollbackConfirm', { version: versionNo }),
      t('asset.rollback'),
      { type: 'warning', confirmButtonText: t('asset.rollback') }
    )
    await rollbackAsset(assetId, versionNo)
    ElMessage.success(t('asset.rollbackSuccess'))
    await loadVersions()
  } catch {
    // ignore
  }
}

onMounted(loadVersions)
</script>

<style scoped>
.asset-diff-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.version-selectors {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-select {
  width: 120px;
}

.vs-label {
  font-size: 14px;
  color: #6b7280;
}

.diff-container {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
}

.diff-line {
  display: flex;
  gap: 8px;
  padding: 1px 8px;
  white-space: pre-wrap;
  word-break: break-all;
}

.diff-marker {
  width: 16px;
  flex-shrink: 0;
  text-align: center;
}

.diff-added {
  background: #dcfce7;
  color: #166534;
}
.diff-added .diff-marker { color: #16a34a; }

.diff-removed {
  background: #fee2e2;
  color: #991b1b;
}
.diff-removed .diff-marker { color: #dc2626; }

.diff-modified {
  background: #fef9c3;
  color: #854d0e;
}
.diff-modified .diff-marker { color: #ca8a04; }

.diff-unchanged {
  color: #6b7280;
}

.version-badge {
  font-family: monospace;
  font-size: 12px;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 4px;
  padding: 1px 6px;
}
</style>
