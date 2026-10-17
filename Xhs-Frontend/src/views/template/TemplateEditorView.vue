<template>
  <div class="template-editor-page">
    <div class="page-header">
      <el-button text @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
        {{ t('common.back') }}
      </el-button>
      <h1 class="page-title">{{ isEdit ? t('template.edit') : t('template.create') }}</h1>
    </div>

    <el-card shadow="never" class="editor-card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="140px"
        class="template-form"
      >
        <!-- 基础信息 -->
        <el-divider content-position="left">{{ t('template.basicInfo') }}</el-divider>

        <el-form-item :label="t('template.scope')">
          <el-tag :type="scopeTagType(form.scope)" size="large">
            {{ t(`template.scope_${form.scope.toLowerCase()}`) }}
          </el-tag>
          <span class="form-hint">{{ t('template.scopeHint') }}</span>
        </el-form-item>

        <el-form-item :label="t('template.category')" prop="category">
          <el-select v-model="form.category" :placeholder="t('template.categoryPlaceholder')" class="full-width">
            <el-option
              v-for="cat in categories"
              :key="cat"
              :label="t(`template.categories.${cat}`)"
              :value="cat"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('template.scenario')" prop="scenario">
          <el-input v-model="form.scenario" :placeholder="t('template.scenarioPlaceholder')" />
        </el-form-item>

        <el-form-item :label="t('template.persona')" prop="persona">
          <el-select v-model="form.persona" :placeholder="t('template.personaPlaceholder')">
            <el-option :label="t('persona.agency')" value="agency" />
            <el-option :label="t('persona.mentor')" value="mentor" />
            <el-option :label="t('persona.senior')" value="senior" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('template.titleField')" prop="title">
          <el-input v-model="form.title" :placeholder="t('template.titlePlaceholder')" maxlength="255" show-word-limit />
        </el-form-item>

        <!-- 正文模板 -->
        <el-divider content-position="left">{{ t('template.bodyTemplate') }}</el-divider>

        <el-form-item :label="t('template.bodyTemplate')" prop="bodyTemplate">
          <el-input
            v-model="form.bodyTemplate"
            type="textarea"
            :rows="12"
            :placeholder="t('template.bodyTemplatePlaceholder')"
            class="body-textarea"
          />
        </el-form-item>

        <!-- 变量 -->
        <el-form-item :label="t('template.variables')">
          <div class="variables-editor">
            <div class="variables-tags">
              <el-tag
                v-for="v in form.variables"
                :key="v"
                closable
                size="large"
                @close="removeVariable(v)"
              >
                {{ '{{' }}{{ v }}}}{{ '}}' }}
              </el-tag>
              <el-input
                v-model="newVariable"
                size="small"
                :placeholder="t('template.addVariable')"
                class="variable-input"
                @keyup.enter="addVariable"
              />
            </div>
            <p class="variables-hint">
              {{ t('template.variablesHint') }}
            </p>
            <el-button size="small" @click="extractVariables">
              {{ t('template.extractFromBody') }}
            </el-button>
          </div>
        </el-form-item>

        <!-- 渲染预览 -->
        <el-divider content-position="left">{{ t('template.preview') }}</el-divider>

        <el-form-item :label="t('template.previewVars')">
          <div class="preview-vars">
            <el-input
              v-for="v in form.variables"
              :key="v"
              v-model="previewValues[v]"
              :placeholder="`{{ ${v} }}`"
              class="preview-var-input"
              @input="updatePreview"
            />
          </div>
        </el-form-item>

        <el-form-item :label="t('template.previewResult')">
          <div class="preview-result" v-html="renderedPreview" />
        </el-form-item>

        <!-- 默认设置 -->
        <el-divider content-position="left">{{ t('template.defaultSettings') }}</el-divider>

        <el-form-item :label="t('template.defaultGoal')" prop="defaultGoal">
          <el-select v-model="form.defaultGoal">
            <el-option :label="t('goal.high_conversion')" value="high_conversion" />
            <el-option :label="t('goal.engagement')" value="engagement" />
            <el-option :label="t('goal.brand_awareness')" value="brand_awareness" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('template.defaultTone')" prop="defaultTone">
          <el-select v-model="form.defaultTone">
            <el-option :label="t('tone.friendly')" value="friendly" />
            <el-option :label="t('tone.professional')" value="professional" />
            <el-option :label="t('tone.casual')" value="casual" />
            <el-option :label="t('tone.motivational')" value="motivational" />
          </el-select>
        </el-form-item>

        <!-- 操作按钮 -->
        <el-divider />
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">
            {{ t('common.save') }}
          </el-button>
          <el-button @click="router.back()">{{ t('common.cancel') }}</el-button>
          <el-button v-if="isEdit" type="warning" :loading="publishing" @click="handlePublish">
            {{ t('template.publish') }}
          </el-button>
          <el-button v-if="isEdit && form.scope !== 'OFFICIAL'" type="info" @click="handleDuplicate">
            {{ t('template.duplicate') }}
          </el-button>
          <el-button v-if="isEdit && form.scope !== 'OFFICIAL'" type="danger" plain @click="handleArchive">
            {{ t('template.archive') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  createTemplate,
  updateTemplate,
  publishTemplate,
  archiveTemplate,
  duplicateTemplate,
  fetchTemplate,
  type TemplateScope,
} from '@/api/template'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)
const templateId = computed(() => route.params.id as string)

const formRef = ref()
const saving = ref(false)
const publishing = ref(false)
const newVariable = ref('')
const previewValues = reactive<Record<string, string>>({})

const categories = [
  'career_fair', 'interview', 'visa', 'job_hunt',
  'career_development', 'salary', 'networking',
]

const form = reactive({
  scope: 'WORKSPACE' as TemplateScope,
  category: '',
  scenario: '',
  persona: 'agency',
  title: '',
  bodyTemplate: '',
  variables: [] as string[],
  defaultGoal: 'engagement',
  defaultTone: 'friendly',
})

const rules = {
  category: [{ required: true, message: 'category required', trigger: 'change' }],
  title: [{ required: true, message: 'title required', trigger: 'blur' }],
  bodyTemplate: [{ required: true, message: 'bodyTemplate required', trigger: 'blur' }],
}

function scopeTagType(scope: string) {
  switch (scope) {
    case 'OFFICIAL': return 'success'
    case 'WORKSPACE': return 'primary'
    case 'PERSONAL': return 'warning'
    default: return 'info'
  }
}

function addVariable() {
  const v = newVariable.value.trim()
  if (v && !form.variables.includes(v)) {
    form.variables.push(v)
    previewValues[v] = ''
  }
  newVariable.value = ''
}

function removeVariable(v: string) {
  form.variables = form.variables.filter(x => x !== v)
  delete previewValues[v]
}

function extractVariables() {
  const matches = form.bodyTemplate.match(/\{\{(\w+)\}\}/g) || []
  const extracted = matches.map(m => m.replace(/\{\{|\}\}/g, ''))
  const unique = [...new Set([...form.variables, ...extracted])]
  form.variables = unique
  extracted.forEach(v => { if (!previewValues[v]) previewValues[v] = '' })
  ElMessage.success(t('template.extractedVariables', { count: extracted.length }))
}

const renderedPreview = computed(() => {
  let result = form.bodyTemplate
  for (const v of form.variables) {
    const val = previewValues[v] || `{{ ${v} }}`
    result = result.replace(new RegExp(`\\{\\{${v}\\}\\}`, 'g'), `<strong style="color:#7c3aed">${val}</strong>`)
  }
  return result.replace(/\n/g, '<br>')
})

function updatePreview() {
  // reactive update is automatic
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (isEdit.value) {
      await updateTemplate(templateId.value, {
        title: form.title,
        bodyTemplate: form.bodyTemplate,
        variables: form.variables,
        category: form.category,
        scenario: form.scenario,
        persona: form.persona,
        defaultGoal: form.defaultGoal,
        defaultTone: form.defaultTone,
      })
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await createTemplate({
        scope: form.scope,
        category: form.category,
        scenario: form.scenario,
        persona: form.persona,
        title: form.title,
        bodyTemplate: form.bodyTemplate,
        variables: form.variables,
        defaultGoal: form.defaultGoal,
        defaultTone: form.defaultTone,
      })
      ElMessage.success(t('template.createSuccess'))
      router.push('/template')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || t('common.requestFailed'))
  } finally {
    saving.value = false
  }
}

async function handlePublish() {
  publishing.value = true
  try {
    await publishTemplate(templateId.value)
    ElMessage.success(t('template.publishSuccess'))
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || t('common.requestFailed'))
  } finally {
    publishing.value = false
  }
}

async function handleDuplicate() {
  try {
    await duplicateTemplate(templateId.value, '')
    ElMessage.success(t('template.duplicateSuccess'))
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || t('common.requestFailed'))
  }
}

async function handleArchive() {
  try {
    await archiveTemplate(templateId.value)
    ElMessage.success(t('template.archiveSuccess'))
    router.push('/template')
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || t('common.requestFailed'))
  }
}

async function loadTemplate() {
  if (!isEdit.value) return
  try {
    const res = await fetchTemplate(templateId.value)
    const tpl = res.data.data
    form.scope = tpl.scope
    form.category = tpl.category
    form.scenario = tpl.scenario || ''
    form.persona = tpl.persona || 'agency'
    form.title = tpl.title
    form.bodyTemplate = tpl.bodyTemplate
    form.variables = tpl.variables || []
    form.defaultGoal = tpl.defaultGoal || 'engagement'
    form.defaultTone = tpl.defaultTone || 'friendly'
    tpl.variables?.forEach((v: string) => { previewValues[v] = '' })
  } catch (err: any) {
    ElMessage.error(t('common.requestFailed'))
  }
}

onMounted(loadTemplate)
</script>

<style scoped>
.template-editor-page {
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

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #9ca3af;
}

.full-width {
  width: 100%;
  max-width: 400px;
}

.body-textarea :deep(textarea) {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.variables-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.variables-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.variable-input {
  width: 140px;
}

.variables-hint {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
}

.preview-vars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-var-input {
  max-width: 400px;
}

.preview-result {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px 16px;
  min-height: 80px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  max-width: 800px;
}
</style>
