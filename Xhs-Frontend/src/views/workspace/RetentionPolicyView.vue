<template>
  <div class="retention-policy-view">
    <el-page-header @back="router.back()" :title="t('common.back')">
      <template #content>
        <span class="page-title">{{ t('retention.retentionPolicy') }}</span>
      </template>
    </el-page-header>

    <el-card class="policy-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('retention.policySettings') }}</span>
          <span class="workspace-name">{{ workspaceName }}</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        label-width="180px"
        :disabled="saving"
      >
        <el-form-item :label="t('retention.assetRetention')">
          <el-input-number
            v-model="form.assetRetentionDays"
            :min="30"
            :max="3650"
          />
          <span class="form-hint">{{ t('retention.assetRetentionHint') }}</span>
        </el-form-item>

        <el-form-item :label="t('retention.logRetention')">
          <el-input-number
            v-model="form.logRetentionDays"
            :min="7"
            :max="3650"
          />
          <span class="form-hint">{{ t('retention.logRetentionHint') }}</span>
        </el-form-item>

        <el-form-item :label="t('retention.mediaRetention')">
          <el-input-number
            v-model="form.mediaRetentionDays"
            :min="7"
            :max="3650"
          />
          <span class="form-hint">{{ t('retention.mediaRetentionHint') }}</span>
        </el-form-item>

        <el-form-item :label="t('retention.chatRetention')">
          <el-input-number
            v-model="form.chatSessionRetentionDays"
            :min="7"
            :max="3650"
          />
          <span class="form-hint">{{ t('retention.chatRetentionHint') }}</span>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">
            {{ t('common.save') }}
          </el-button>
          <el-button @click="handleReset">
            {{ t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="info-card" shadow="never">
      <el-alert type="info" :closable="false">
        <template #title>
          {{ t('retention.policyInfoTitle') }}
        </template>
        <ul class="info-list">
          <li>{{ t('retention.policyInfo1') }}</li>
          <li>{{ t('retention.policyInfo2') }}</li>
          <li>{{ t('retention.policyInfo3') }}</li>
        </ul>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { getRetentionPolicy, updateRetentionPolicy, type RetentionPolicyRequest } from '@/api/retention'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const workspaceId = computed(() => userStore.activeWorkspaceId || '')
const workspaceName = computed(() => userStore.activeWorkspace?.name || '')

const form = ref<RetentionPolicyRequest>({
  assetRetentionDays: 365,
  logRetentionDays: 90,
  mediaRetentionDays: 180,
  chatSessionRetentionDays: 180,
})

const defaultForm = { ...form.value }
const saving = ref(false)

async function loadPolicy() {
  if (!workspaceId.value) return
  try {
    const res = await getRetentionPolicy(workspaceId.value)
    const data = res.data.data
    if (data) {
      form.value = {
        assetRetentionDays: data.assetRetentionDays,
        logRetentionDays: data.logRetentionDays,
        mediaRetentionDays: data.mediaRetentionDays,
        chatSessionRetentionDays: data.chatSessionRetentionDays,
      }
      defaultForm.assetRetentionDays = data.assetRetentionDays
      defaultForm.logRetentionDays = data.logRetentionDays
      defaultForm.mediaRetentionDays = data.mediaRetentionDays
      defaultForm.chatSessionRetentionDays = data.chatSessionRetentionDays
    }
  } catch (e) {
    console.error('Failed to load retention policy:', e)
  }
}

async function handleSave() {
  if (!workspaceId.value) return
  saving.value = true
  try {
    await updateRetentionPolicy(workspaceId.value, form.value)
    ElMessage.success(t('retention.policySaved'))
    defaultForm.assetRetentionDays = form.value.assetRetentionDays!
    defaultForm.logRetentionDays = form.value.logRetentionDays!
    defaultForm.mediaRetentionDays = form.value.mediaRetentionDays!
    defaultForm.chatSessionRetentionDays = form.value.chatSessionRetentionDays!
  } catch (e) {
    ElMessage.error((e as Error).message || t('common.requestFailed'))
  } finally {
    saving.value = false
  }
}

function handleReset() {
  form.value = { ...defaultForm }
}

onMounted(() => {
  loadPolicy()
})
</script>

<style scoped>
.retention-policy-view {
  padding: 24px;
  max-width: 700px;
  margin: 0 auto;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.policy-card {
  margin-top: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.workspace-name {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.info-card {
  margin-top: 20px;
}

.info-list {
  margin: 8px 0 0 0;
  padding-left: 20px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.info-list li {
  margin-bottom: 4px;
}
</style>
