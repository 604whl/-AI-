<template>
  <div class="template-library-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ t('template.title') }}</h1>
        <p class="page-subtitle">{{ t('template.subtitle') }}</p>
      </div>
      <el-button v-if="canCreate" type="primary" @click="router.push('/template/new')">
        {{ t('template.create') }}
      </el-button>
    </div>

    <!-- Scope Tabs -->
    <el-card shadow="never" class="scope-tabs-card">
      <el-radio-group v-model="activeScope" @change="handleScopeChange">
        <el-radio-button value="OFFICIAL">{{ t('template.scopeOfficial') }}</el-radio-button>
        <el-radio-button value="WORKSPACE">{{ t('template.scopeWorkspace') }}</el-radio-button>
        <el-radio-button value="PERSONAL">{{ t('template.scopePersonal') }}</el-radio-button>
      </el-radio-group>
      <el-select
        v-model="activeCategory"
        :placeholder="t('template.categoryPlaceholder')"
        clearable
        class="category-filter"
        @change="handleSearch"
      >
        <el-option :label="t('template.categoryAll')" value="" />
        <el-option
          v-for="cat in categories"
          :key="cat"
          :label="t(`template.categories.${cat}`)"
          :value="cat"
        />
      </el-select>
    </el-card>

    <!-- Template Grid -->
    <el-card shadow="never" class="template-grid-card">
      <template #header>
        <div class="grid-header">
          <span>{{ t('template.total', { total }) }}</span>
        </div>
      </template>

      <el-skeleton v-if="loading && !templates.length" :rows="6" animated />
      <el-empty v-else-if="!templates.length" :description="t('template.noRecords')" />

      <div v-else class="template-grid">
        <div
          v-for="tpl in templates"
          :key="tpl.id"
          class="template-card"
          @click="goToDetail(tpl)"
        >
          <div class="template-card-header">
            <el-tag :type="scopeTagType(tpl.scope)" size="small">
              {{ t(`template.scope_${tpl.scope.toLowerCase()}`) }}
            </el-tag>
            <el-tag v-if="tpl.category" size="small">
              {{ t(`template.categories.${tpl.category}`) || tpl.category }}
            </el-tag>
          </div>
          <h3 class="template-card-title">{{ tpl.title }}</h3>
          <p class="template-card-body-preview">{{ tpl.bodyTemplate.substring(0, 120) }}...</p>
          <div class="template-card-footer">
            <span class="use-count">
              <el-icon><Connection /></el-icon>
              {{ tpl.useCount }} {{ t('template.uses') }}
            </span>
            <span class="template-version">v{{ tpl.version }}</span>
          </div>
          <div v-if="tpl.variables?.length" class="template-variables">
            <span
              v-for="v in tpl.variables.slice(0, 3)"
              :key="v"
              class="variable-tag"
            >{{ '&lbrace;&lbrace;' }}{{ v }}{{ '&rbrace;&rbrace;' }}</span>
            <span v-if="tpl.variables.length > 3" class="variable-more">
              +{{ tpl.variables.length - 3 }}
            </span>
          </div>
        </div>
      </div>

      <div v-if="templates.length" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[12, 24, 48]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadTemplates"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Connection } from '@element-plus/icons-vue'
import { useWorkspaceChange } from '@/composables/useWorkspaceChange'
import { fetchTemplates } from '@/api/template'
import type { Template, TemplateScope } from '@/api/template'
import { TEMPLATE_CATEGORIES } from '@/constants/templateCategories'

const { t } = useI18n()
const router = useRouter()

const activeScope = ref<TemplateScope>('OFFICIAL')
const activeCategory = ref('')
const loading = ref(true)
const templates = ref<Template[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(12)

const canCreate = true

const categories = TEMPLATE_CATEGORIES

function scopeTagType(scope: string) {
  switch (scope) {
    case 'OFFICIAL': return 'success'
    case 'WORKSPACE': return 'primary'
    case 'PERSONAL': return 'warning'
    default: return 'info'
  }
}

function handleScopeChange() {
  page.value = 1
  loadTemplates()
}

function handleSearch() {
  page.value = 1
  loadTemplates()
}

function handleSizeChange() {
  page.value = 1
  loadTemplates()
}

async function loadTemplates() {
  loading.value = true
  try {
    const res = await fetchTemplates({
      scope: activeScope.value,
      category: activeCategory.value || undefined,
      page: page.value,
      size: pageSize.value,
    })
    const data = res.data.data
    templates.value = data.items
    total.value = data.total
  } catch {
    templates.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function goToDetail(tpl: Template) {
  router.push(`/template/${tpl.id}`)
}

onMounted(loadTemplates)
useWorkspaceChange(() => {
  page.value = 1
  loadTemplates()
})
</script>

<style scoped>
.template-library-page {
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

.scope-tabs-card :deep(.el-card__body) {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.category-filter {
  width: 180px;
  margin-left: auto;
}

.template-grid-card :deep(.el-card__body) {
  padding: 0 16px 16px;
}

.grid-header {
  font-size: 14px;
  color: #6b7280;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.template-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.template-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-color: #409eff;
}

.template-card-header {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.template-card-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  line-height: 1.4;
}

.template-card-body-preview {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
  flex: 1;
}

.template-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #9ca3af;
}

.use-count {
  display: flex;
  align-items: center;
  gap: 4px;
}

.template-version {
  font-family: monospace;
}

.template-variables {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.variable-tag {
  font-size: 11px;
  font-family: monospace;
  color: #7c3aed;
  background: #f3e8ff;
  border-radius: 4px;
  padding: 1px 4px;
}

.variable-more {
  font-size: 11px;
  color: #9ca3af;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
  }
  .category-filter {
    margin-left: 0;
    width: 100%;
  }
  .template-grid {
    grid-template-columns: 1fr;
  }
}
</style>
