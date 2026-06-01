import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  archiveChatSession,
  createChatSession,
  fetchChatMessages,
  fetchChatSessions,
  sendChatMessage,
} from '@/api/chat'
import type { PersonaType } from '@/types/api'
import type { ChatAgentCard, ChatMessageItem, ChatToolTrace } from '@/types/chat'

export interface DisplayMessage {
  id: string
  role: 'user' | 'assistant' | 'tool' | 'system'
  content: string
  cards?: ChatAgentCard[]
  toolTraces?: ChatToolTrace[]
  createdAt?: string
}

export function useChat() {
  const { t } = useI18n()

  const sessions = ref<Array<{ sessionId: string; title?: string; updatedAt?: string }>>([])
  const activeSessionId = ref<string | null>(null)
  const messages = ref<DisplayMessage[]>([])
  const persona = ref<PersonaType>('agency')
  const sending = ref(false)
  const loadingSessions = ref(false)
  const loadingMessages = ref(false)

  const hasActiveSession = computed(() => !!activeSessionId.value)

  async function loadSessions() {
    loadingSessions.value = true
    try {
      const res = await fetchChatSessions({ page: 1, size: 20 })
      sessions.value = (res.data.data?.items ?? []).map((s) => ({
        sessionId: s.sessionId,
        title: s.title,
        updatedAt: s.updatedAt,
      }))
    } catch (err) {
      ElMessage.error((err as Error).message || t('chat.loadSessionsFailed'))
    } finally {
      loadingSessions.value = false
    }
  }

  async function startNewSession(title?: string) {
    try {
      const res = await createChatSession({
        persona: persona.value,
        title: title || t('chat.defaultSessionTitle'),
      })
      const session = res.data.data
      if (!session?.sessionId) {
        throw new Error(t('chat.createSessionFailed'))
      }
      activeSessionId.value = session.sessionId
      messages.value = []
      await loadSessions()
      return session.sessionId
    } catch (err) {
      ElMessage.error((err as Error).message || t('chat.createSessionFailed'))
      return null
    }
  }

  async function selectSession(sessionId: string) {
    if (activeSessionId.value === sessionId) return
    activeSessionId.value = sessionId
    await loadMessages(sessionId)
  }

  async function loadMessages(sessionId: string) {
    loadingMessages.value = true
    try {
      const res = await fetchChatMessages(sessionId, { page: 1, size: 100 })
      const items = res.data.data?.items ?? []
      messages.value = items
        .filter((m) => m.role === 'user' || m.role === 'assistant')
        .map(mapHistoryMessage)
    } catch (err) {
      ElMessage.error((err as Error).message || t('chat.loadMessagesFailed'))
    } finally {
      loadingMessages.value = false
    }
  }

  async function sendMessage(
    content: string,
    attachments?: { title?: string; body?: string; coverImageUrl?: string },
  ) {
    const trimmed = content.trim()
    if (!trimmed || sending.value) return false

    if (!activeSessionId.value) {
      const id = await startNewSession()
      if (!id) return false
    }

    const sessionId = activeSessionId.value!
    const userMsg: DisplayMessage = {
      id: `local-user-${Date.now()}`,
      role: 'user',
      content: trimmed,
      createdAt: new Date().toISOString(),
    }
    messages.value.push(userMsg)
    sending.value = true

    try {
      const res = await sendChatMessage(sessionId, { content: trimmed, attachments })
      const data = res.data.data
      if (!data) {
        throw new Error(t('chat.sendFailed'))
      }
      messages.value.push({
        id: String(data.messageId),
        role: 'assistant',
        content: data.content || '',
        cards: data.cards ?? [],
        toolTraces: data.toolTraces ?? [],
        createdAt: new Date().toISOString(),
      })
      await loadSessions()
      return true
    } catch (err) {
      ElMessage.error((err as Error).message || t('chat.sendFailed'))
      messages.value = messages.value.filter((m) => m.id !== userMsg.id)
      return false
    } finally {
      sending.value = false
    }
  }

  async function removeSession(sessionId: string) {
    try {
      await archiveChatSession(sessionId)
      sessions.value = sessions.value.filter((s) => s.sessionId !== sessionId)
      if (activeSessionId.value === sessionId) {
        activeSessionId.value = null
        messages.value = []
      }
    } catch (err) {
      ElMessage.error((err as Error).message || t('common.requestFailed'))
    }
  }

  return {
    sessions,
    activeSessionId,
    messages,
    persona,
    sending,
    loadingSessions,
    loadingMessages,
    hasActiveSession,
    loadSessions,
    startNewSession,
    selectSession,
    loadMessages,
    sendMessage,
    removeSession,
  }
}

function mapHistoryMessage(item: ChatMessageItem): DisplayMessage {
  const metadata = item.metadata ?? {}
  const cards = (metadata.cards as ChatAgentCard[] | undefined) ?? []
  const toolTraces = (metadata.toolTraces as ChatToolTrace[] | undefined) ?? []
  return {
    id: String(item.id),
    role: item.role as DisplayMessage['role'],
    content: item.content ?? '',
    cards,
    toolTraces,
    createdAt: item.createdAt,
  }
}
