<template>
  <el-card :header="t('settings.title')">
    <el-form label-width="120px">
      <el-form-item :label="t('settings.defaultPersona')">
        <el-select v-model="persona" style="width: 200px">
          <el-option :label="t('persona.agency')" value="agency" />
          <el-option :label="t('persona.mentor')" value="mentor" />
          <el-option :label="t('persona.senior')" value="senior" />
        </el-select>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PersonaType } from '@/types/api'

const { t } = useI18n()
const persona = ref<PersonaType>('agency')
</script>
