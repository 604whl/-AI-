<template>
  <el-tag :type="tagType" size="small">
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AssetStatus } from '@/api/asset'

const props = defineProps<{
  status?: AssetStatus
}>()

const { t } = useI18n()

const label = computed(() =>
  props.status ? t(`asset.statuses.${props.status}`) : '—'
)

const tagType = computed(() => {
  switch (props.status) {
    case 'draft':       return 'info'
    case 'in_review':   return 'warning'
    case 'approved':    return 'success'
    case 'scheduled':  return ''
    case 'published':   return 'success'
    case 'archived':    return 'info'
    default:            return 'info'
  }
})
</script>
