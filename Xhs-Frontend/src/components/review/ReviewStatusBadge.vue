<template>
  <span class="review-status-wrapper">
    <el-tag v-if="highRisk && showRisk" type="danger" size="small" effect="plain">
      ⚠ {{ t('workbench.riskLevel.high') }}
    </el-tag>
    <el-tag :type="tagType" size="small">
      {{ label }}
    </el-tag>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ReviewStatus } from '@/api/review'

const props = defineProps<{
  status?: ReviewStatus
  highRisk?: boolean
  showRisk?: boolean   // 是否同时显示高风险标签
}>()

const { t } = useI18n()

const label = computed(() =>
  props.status ? t(`review.statuses.${props.status}`) : '—'
)

const tagType = computed(() => {
  switch (props.status) {
    case 'pending':    return 'info'
    case 'in_review':  return 'warning'
    case 'approved':   return 'success'
    case 'rejected':   return 'danger'
    case 'cancelled':  return 'info'
    default:           return 'info'
  }
})
</script>

<style scoped>
.review-status-wrapper {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
