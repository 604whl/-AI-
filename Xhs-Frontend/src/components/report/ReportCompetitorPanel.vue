<template>
  <el-card v-if="hasContent" shadow="never" class="report-module competitor-panel">
    <template #header>
      <span class="module-title">{{ t('report.moduleCompetitor') }}</span>
    </template>

    <div v-if="borrowPoints?.length" class="competitor-block">
      <h4 class="block-title borrow">{{ t('report.borrowPoints') }}</h4>
      <ul class="point-list">
        <li v-for="(item, i) in borrowPoints" :key="'b-' + i">{{ item }}</li>
      </ul>
    </div>

    <div v-if="doNotCopy?.length" class="competitor-block">
      <h4 class="block-title avoid">{{ t('report.doNotCopy') }}</h4>
      <ul class="point-list avoid-list">
        <li v-for="(item, i) in doNotCopy" :key="'d-' + i">{{ item }}</li>
      </ul>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  borrowPoints?: string[]
  doNotCopy?: string[]
}>()

const { t } = useI18n()

const hasContent = computed(
  () => (props.borrowPoints?.length ?? 0) > 0 || (props.doNotCopy?.length ?? 0) > 0,
)
</script>

<style scoped>
.report-module {
  border-radius: 12px;
}

.competitor-panel {
  border: 1px solid #e0e7ff;
  background: linear-gradient(180deg, #fff 0%, #f5f7ff 100%);
}

.module-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.competitor-block + .competitor-block {
  margin-top: 16px;
}

.block-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.block-title.borrow {
  color: #059669;
}

.block-title.avoid {
  color: #dc2626;
}

.point-list {
  margin: 0;
  padding-left: 18px;
  font-size: 14px;
  line-height: 1.7;
  color: #374151;
}

.avoid-list li::marker {
  color: #dc2626;
}
</style>
