<template>
  <el-card :header="t('settings.title')">
    <el-form label-width="120px" @submit.prevent="handleSave">
      <el-form-item :label="t('settings.defaultPersona')">
        <el-select v-model="persona" style="width: 200px">
          <el-option :label="t('persona.agency')" value="agency" />
          <el-option :label="t('persona.mentor')" value="mentor" />
          <el-option :label="t('persona.senior')" value="senior" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="userStore.saving" @click="handleSave">
          {{ t('settings.save') }}
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { PersonaType } from '@/types/api'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()
const persona = ref<PersonaType>('agency')

watch(
  () => userStore.profile?.defaultPersona,
  (value) => {
    if (value) persona.value = value
  },
  { immediate: true },
)

async function handleSave() {
  try {
    await userStore.updateProfile({ defaultPersona: persona.value })
    ElMessage.success(t('settings.saveSuccess'))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : t('settings.saveFailed'))
  }
}
</script>
