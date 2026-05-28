<template>
  <div>
    <el-page-header @back="$router.back()" :content="t('analysis.reportTitle')" />
    <el-alert type="info" show-icon :closable="false" style="margin: 16px 0">
      {{ t('analysis.reportHint') }}
    </el-alert>
    <el-skeleton :rows="8" animated />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>
