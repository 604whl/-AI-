<template>
  <div class="titles-page">
    <section class="page-header">
      <div class="header-content">
        <div>
          <h1 class="page-title">{{ t('titles.title') }}</h1>
          <p class="page-desc">{{ t('titles.desc') }}</p>
        </div>
        <el-tag effect="plain" type="danger" round class="ai-badge">
          <el-icon><MagicStick /></el-icon>
          {{ t('titles.aiEngine') }}
        </el-tag>
      </div>
    </section>

    <el-card shadow="never" class="workspace-card">
      <TitleGeneratorPanel
        split-layout
        :analysis-id="analysisId"
        :initial-persona="defaultPersona"
        :initial-title="draftTitle"
        :initial-body="draftBody"
      />
    </el-card>

    <el-card shadow="never" class="tips-card">
      <template #header>{{ t('titles.guideTitle') }}</template>
      <ul class="guide-list">
        <li v-for="(tip, index) in guideTips" :key="index">{{ tip }}</li>
      </ul>
      <el-divider />
      <p class="guide-note">{{ t('titles.guideNote') }}</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MagicStick } from '@element-plus/icons-vue'
import TitleGeneratorPanel from '@/components/title/TitleGeneratorPanel.vue'
import { useUserStore } from '@/stores/user'
import type { PersonaType } from '@/types/api'
import { loadAnalysisDraft } from '@/utils/analysisDraft'

const { t, tm } = useI18n()
const route = useRoute()
const userStore = useUserStore()

const analysisId = computed(() => {
  const id = route.query.analysisId
  return typeof id === 'string' && id.startsWith('ana_') ? id : undefined
})

const defaultPersona = computed(
  () => (userStore.profile?.defaultPersona as PersonaType | undefined) ?? 'agency',
)

const draft = loadAnalysisDraft()
const draftTitle = draft?.title
const draftBody = draft?.body

const guideTips = computed(() => tm('titles.guideTips') as string[])
</script>

<style scoped>
.titles-page {
  max-width: 1280px;
}

.page-header {
  margin-bottom: 20px;
}

.header-content {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(90deg, #111827, #ff2442);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.page-desc {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  max-width: 560px;
}

.ai-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
}

.workspace-card {
  margin-bottom: 16px;
  border-radius: 12px;
}

.tips-card {
  border-radius: 12px;
}

.guide-list {
  margin: 0;
  padding-left: 18px;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.8;
}

.guide-note {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>
