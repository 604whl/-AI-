<template>
  <el-drawer
    v-model="visible"
    :title="t('template.pickerTitle')"
    size="600px"
    direction="rtl"
  >
    <div class="picker-body">
      <el-radio-group v-model="activeScope" @change="loadTemplates" class="scope-group">
        <el-radio-button value="OFFICIAL">{{ t('template.scopeOfficial') }}</el-radio-button>
        <el-radio-button value="WORKSPACE">{{ t('template.scopeWorkspace') }}</el-radio-button>
        <el-radio-button value="PERSONAL">{{ t('template.scopePersonal') }}</el-radio-button>
      </el-radio-group>

      <el-select
        v-model="activeCategory"
        :placeholder="t('template.categoryPlaceholder')"
        clearable
        class="cat-select"
        @change="loadTemplates"
      >
        <el-option :label="t('template.categoryAll')" value="" />
        <el-option
          v-for="cat in categories"
          :key="cat"
          :label="t(`template.categories.${cat}`)"
          :value="cat"
        />
      </el-select>

      <el-skeleton v-if="loading" :rows="5" animated />
      <el-empty v-else-if="!templates.length" :description="t('template.noRecords')" />

      <div v-else class="template-list">
        <div
          v-for="tpl in templates"
          :key="tpl.id"
          class="picker-template-item"
          :class="{ selected: selectedId === tpl.id }"
          @click="selectTemplate(tpl)"
        >
          <div class="item-header">
            <el-tag :type="scopeTagType(tpl.scope)" size="small">
              {{ t(`template.scope_${tpl.scope.toLowerCase()}`) }}
            </el-tag>
            <span class="item-title">{{ tpl.title }}</span>
          </div>
          <p class="item-preview">{{ tpl.bodyTemplate.substring(0, 80) }}...</p>
          <div v-if="tpl.variables?.length" class="item-vars">
            <span
              v-for="v in tpl.variables.slice(0, 4)"
              :key="v"
              class="var-chip"
            >{{ '&lbrace;&lbrace;' }}{{ v }}{{ '&rbrace;&rbrace;' }}</span>
          </div>
        </div>
      </div>

      <div v-if="total > templates.length" class="load-more">
        <el-button text @click="loadMore">{{ t('common.loadMore') }}</el-button>
      </div>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!selected" @click="confirm">
          {{ t('template.confirmSelect') }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { fetchTemplates } from '@/api/template'
import type { Template, TemplateScope } from '@/api/template'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'select', template: Template): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const activeScope = ref<TemplateScope>('OFFICIAL')
const activeCategory = ref('')
const loading = ref(false)
const templates = ref<Template[]>([])
const total = ref(0)
const page = ref(1)
const selectedId = ref<string | null>(null)
const selected = computed(() => templates.value.find(t => t.id === selectedId.value) || null)

const categories = [
  'career_fair', 'interview', 'visa', 'job_hunt',
  'career_development', 'salary', 'networking',
]

watch(visible, (v) => {
  if (v) {
    activeScope.value = 'OFFICIAL'
    activeCategory.value = ''
    selectedId.value = null
    page.value = 1
    templates.value = []
    loadTemplates()
  }
})

function scopeTagType(scope: string) {
  switch (scope) {
    case 'OFFICIAL': return 'success'
    case 'WORKSPACE': return 'primary'
    case 'PERSONAL': return 'warning'
    default: return 'info'
  }
}

async function loadTemplates() {
  loading.value = true
  page.value = 1
  try {
    const res = await fetchTemplates({
      scope: activeScope.value,
      category: activeCategory.value || undefined,
      page: 1,
      size: 20,
    })
    templates.value = res.data.data.items
    total.value = res.data.data.total
  } catch {
    templates.value = []
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  page.value++
  try {
    const res = await fetchTemplates({
      scope: activeScope.value,
      category: activeCategory.value || undefined,
      page: page.value,
      size: 20,
    })
    templates.value.push(...res.data.data.items)
  } catch {
    page.value--
  }
}

function selectTemplate(tpl: Template) {
  selectedId.value = tpl.id
}

function confirm() {
  if (!selected.value) {
    ElMessage.warning(t('template.pleaseSelect'))
    return
  }
  emit('select', selected.value)
  visible.value = false
}
</script>

<style scoped>
.picker-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 4px;
}

.scope-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cat-select {
  width: 200px;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 280px);
  overflow-y: auto;
}

.picker-template-item {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.picker-template-item:hover {
  border-color: #409eff;
  background: #f0f9ff;
}

.picker-template-item.selected {
  border-color: #409eff;
  background: #eff6ff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.item-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.item-preview {
  margin: 0 0 6px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}

.item-vars {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.var-chip {
  font-size: 11px;
  font-family: monospace;
  color: #7c3aed;
  background: #f3e8ff;
  border-radius: 4px;
  padding: 1px 4px;
}

.load-more {
  text-align: center;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
