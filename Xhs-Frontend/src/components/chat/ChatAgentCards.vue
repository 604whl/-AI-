<template>
  <div v-if="cards.length" class="agent-cards">
    <div v-for="(card, index) in cards" :key="`${card.type}-${index}`" class="agent-card">
      <ChatAnalysisReportCard
        v-if="card.type === 'analysis_report'"
        :payload="card.payload"
        :task-id="card.taskId"
      />
      <ChatTitleListCard v-else-if="card.type === 'title_list'" :payload="card.payload" />
      <ChatComplianceCard v-else-if="card.type === 'compliance_warnings'" :payload="card.payload" />
      <ChatCoverAnalysisCard v-else-if="card.type === 'cover_analysis'" :payload="card.payload" />
      <ChatOptimizeDraftCard v-else-if="card.type === 'optimize_draft'" :payload="card.payload" />
      <ChatWebSearchCard v-else-if="card.type === 'web_search'" :payload="card.payload" />
      <ChatFetchedUrlCard v-else-if="card.type === 'fetched_url'" :payload="card.payload" />
      <ChatHotTopicsCard v-else-if="card.type === 'hot_topics'" :payload="card.payload" />
      <ChatIndustryCalendarCard v-else-if="card.type === 'industry_calendar'" :payload="card.payload" />
      <ChatRecentAnalysesCard v-else-if="card.type === 'recent_analyses'" :payload="card.payload" />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ChatAgentCard } from '@/types/chat'
import ChatAnalysisReportCard from '@/components/chat/ChatAnalysisReportCard.vue'
import ChatTitleListCard from '@/components/chat/ChatTitleListCard.vue'
import ChatComplianceCard from '@/components/chat/ChatComplianceCard.vue'
import ChatCoverAnalysisCard from '@/components/chat/ChatCoverAnalysisCard.vue'
import ChatOptimizeDraftCard from '@/components/chat/ChatOptimizeDraftCard.vue'
import ChatWebSearchCard from '@/components/chat/ChatWebSearchCard.vue'
import ChatFetchedUrlCard from '@/components/chat/ChatFetchedUrlCard.vue'
import ChatHotTopicsCard from '@/components/chat/ChatHotTopicsCard.vue'
import ChatIndustryCalendarCard from '@/components/chat/ChatIndustryCalendarCard.vue'
import ChatRecentAnalysesCard from '@/components/chat/ChatRecentAnalysesCard.vue'

defineProps<{
  cards: ChatAgentCard[]
}>()
</script>

<style scoped>
.agent-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}
</style>
