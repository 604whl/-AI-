<template>
  <div v-if="contentType" class="content-type-tags">
    <div class="card-header">
      <div class="header-left">
        <el-icon class="header-icon"><CollectionTag /></el-icon>
        <span class="header-title">{{ t('workbench.contentTypeTitle') }}</span>
      </div>
    </div>
    <div class="tags-row">
      <el-tag effect="dark" type="danger" round>
        {{ contentTypeLabel(contentType) }}
      </el-tag>
      <el-tag
        v-for="tag in secondaryTags"
        :key="tag"
        effect="plain"
        type="warning"
        round
      >
        {{ contentTypeLabel(tag) }}
      </el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { CollectionTag } from '@element-plus/icons-vue'

defineProps<{
  contentType: string | null
  secondaryTags: string[]
}>()

const { t } = useI18n()

function contentTypeLabel(type: string) {
  const key = `workbench.contentTypes.${type}` as const
  const translated = t(key)
  return translated === key ? type : translated
}
</script>

<style scoped>
.content-type-tags {
  padding: 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.card-header {
  margin-bottom: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-icon {
  font-size: 18px;
  color: #ff2442;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
