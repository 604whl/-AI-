<template>
  <div ref="containerRef" class="message-list">
    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="4" animated />
    </div>

    <div v-else-if="!messages.length" class="empty-wrap">
      <el-empty :description="t('chat.emptyHint')">
        <template #image>
          <div class="empty-icon">AI</div>
        </template>
      </el-empty>
    </div>

    <template v-else>
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="message-row"
        :class="msg.role"
      >
        <div class="avatar">{{ msg.role === 'user' ? t('chat.you') : 'AI' }}</div>
        <div class="bubble">
          <p v-if="msg.content" class="content">{{ msg.content }}</p>
          <ChatAgentCards v-if="msg.cards?.length" :cards="msg.cards" />
          <el-collapse v-if="msg.toolTraces?.length" class="tool-traces">
            <el-collapse-item :title="t('chat.toolTraces', { count: msg.toolTraces.length })">
              <div v-for="trace in msg.toolTraces" :key="trace.tool" class="trace-item">
                <el-tag size="small" :type="trace.success ? 'success' : 'danger'">{{ trace.tool }}</el-tag>
                <span class="trace-ms">{{ trace.latencyMs }}ms</span>
                <span v-if="trace.error" class="trace-error">{{ trace.error }}</span>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>

      <div v-if="sending" class="message-row assistant">
        <div class="avatar">AI</div>
        <div class="bubble typing">
          <span class="dot" />
          <span class="dot" />
          <span class="dot" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ChatAgentCards from '@/components/chat/ChatAgentCards.vue'
import type { DisplayMessage } from '@/composables/useChat'

const props = defineProps<{
  messages: DisplayMessage[]
  loading?: boolean
  sending?: boolean
}>()

const { t } = useI18n()
const containerRef = ref<HTMLElement | null>(null)

watch(
  () => [props.messages.length, props.sending],
  async () => {
    await nextTick()
    const el = containerRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  },
  { flush: 'post' },
)
</script>

<style scoped>
.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px 20px;
  background: #f9fafb;
  scroll-behavior: smooth;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: #d1d5db #f3f4f6;
}
.message-list::-webkit-scrollbar {
  width: 8px;
}
.message-list::-webkit-scrollbar-track {
  background: #f3f4f6;
  border-radius: 4px;
}
.message-list::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 4px;
}
.message-list::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}
.loading-wrap,
.empty-wrap {
  padding: 40px 20px;
}
.empty-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto;
  border-radius: 16px;
  background: linear-gradient(135deg, #ff2442, #ff6b81);
  color: #fff;
  font-weight: 700;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.message-row.user {
  flex-direction: row-reverse;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #e5e7eb;
  color: #374151;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.message-row.assistant .avatar {
  background: #ff2442;
  color: #fff;
}
.bubble {
  max-width: min(720px, 85%);
  padding: 12px 14px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
}
.message-row.user .bubble {
  background: #fff1f2;
  border-color: #fecdd3;
}
.content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 14px;
  color: #111827;
}
.tool-traces {
  margin-top: 10px;
  border: none;
}
.trace-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 6px;
}
.trace-ms {
  color: #9ca3af;
}
.trace-error {
  color: #ef4444;
}
.typing {
  display: flex;
  gap: 6px;
  align-items: center;
  min-height: 24px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff2442;
  animation: bounce 1.2s infinite ease-in-out;
}
.dot:nth-child(2) { animation-delay: 0.15s; }
.dot:nth-child(3) { animation-delay: 0.3s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}
</style>
