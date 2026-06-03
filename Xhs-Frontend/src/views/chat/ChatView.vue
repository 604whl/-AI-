<template>
  <div class="chat-view">
    <aside class="session-panel">
      <div class="session-head">
        <span class="session-title">{{ t('chat.sessions') }}</span>
        <el-button type="primary" size="small" @click="handleNewSession">
          {{ t('chat.newSession') }}
        </el-button>
      </div>

      <el-skeleton v-if="loadingSessions" :rows="5" animated />

      <ul v-else class="session-list">
        <li
          v-for="session in sessions"
          :key="session.sessionId"
          :class="{ active: session.sessionId === activeSessionId }"
          @click="selectSession(session.sessionId)"
        >
          <div class="session-item-title">{{ session.title || t('chat.defaultSessionTitle') }}</div>
          <div class="session-item-meta">
            <span>{{ formatTime(session.updatedAt) }}</span>
            <el-button
              link
              type="danger"
              size="small"
              @click.stop="removeSession(session.sessionId)"
            >
              {{ t('chat.deleteSession') }}
            </el-button>
          </div>
        </li>
      </ul>

      <el-empty v-if="!loadingSessions && !sessions.length" :description="t('chat.noSessions')" />
    </aside>

    <section class="chat-main">
      <header class="chat-header">
        <div>
          <h1>{{ t('chat.pageTitle') }}</h1>
          <p>{{ t('chat.pageDesc') }}</p>
        </div>
        <el-select v-model="persona" size="small" style="width: 140px">
          <el-option :label="t('persona.agency')" value="agency" />
          <el-option :label="t('persona.mentor')" value="mentor" />
          <el-option :label="t('persona.senior')" value="senior" />
        </el-select>
      </header>

      <ChatMessageList
        :messages="messages"
        :loading="loadingMessages"
        :sending="sending"
      />

      <footer class="chat-input">
        <el-collapse v-model="draftOpen" class="draft-collapse">
          <el-collapse-item :title="t('chat.draftAttachments')" name="draft">
            <el-form label-position="top" size="small">
              <el-form-item :label="t('analysis.title')">
                <el-input
                  v-model="draft.title"
                  maxlength="100"
                  show-word-limit
                  :placeholder="t('dashboard.titlePlaceholder')"
                />
              </el-form-item>
              <el-form-item :label="t('analysis.body')">
                <el-input
                  v-model="draft.body"
                  type="textarea"
                  :rows="4"
                  :placeholder="t('dashboard.bodyPlaceholder')"
                />
              </el-form-item>
              <el-form-item :label="t('analysis.cover')">
                <div class="cover-upload">
                  <el-upload
                    :auto-upload="false"
                    :show-file-list="false"
                    accept="image/jpeg,image/png,image/webp"
                    :disabled="coverUploading"
                    @change="handleCoverSelect"
                  >
                    <el-button size="small" :loading="coverUploading">{{ t('chat.uploadCover') }}</el-button>
                  </el-upload>
                  <img v-if="coverPreviewUrl" :src="coverPreviewUrl" alt="cover" class="cover-preview" />
                  <el-button
                    v-if="draft.coverImageUrl"
                    link
                    type="danger"
                    size="small"
                    @click="clearCover"
                  >
                    {{ t('chat.removeCover') }}
                  </el-button>
                </div>
              </el-form-item>
            </el-form>
          </el-collapse-item>
        </el-collapse>

        <div class="quick-actions">
          <el-button
            v-for="action in quickActions"
            :key="action.key"
            size="small"
            round
            :disabled="sending"
            @click="applyQuickAction(action)"
          >
            {{ action.label }}
          </el-button>
        </div>

        <div class="input-row">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            :placeholder="t('chat.inputPlaceholder')"
            :disabled="sending"
            @keydown.ctrl.enter.prevent="handleSend"
            @keydown.meta.enter.prevent="handleSend"
          />
          <el-button
            type="primary"
            class="send-btn"
            :loading="sending"
            :disabled="!inputText.trim()"
            @click="handleSend"
          >
            {{ t('chat.send') }}
          </el-button>
        </div>
        <p class="input-hint">{{ t('chat.sendHint') }}</p>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import ChatMessageList from '@/components/chat/ChatMessageList.vue'
import { useChat } from '@/composables/useChat'
import { uploadCoverImage, resolveCoverPreviewUrl } from '@/api/file'
import { useUserStore } from '@/stores/user'
import type { PersonaType } from '@/types/api'

const { t } = useI18n()
const userStore = useUserStore()

const {
  sessions,
  activeSessionId,
  messages,
  persona,
  sending,
  loadingSessions,
  loadingMessages,
  loadSessions,
  startNewSession,
  selectSession,
  sendMessage,
  removeSession,
} = useChat()

const inputText = ref('')
const draftOpen = ref<string[]>(['draft'])
const draft = reactive({
  title: '',
  body: '',
  coverImageUrl: '',
})
const coverUploading = ref(false)
const coverPreviewUrl = ref('')

const quickActions = computed(() => [
  { key: 'analyze', label: t('chat.quickAnalyze'), content: t('chat.quickAnalyzePrompt') },
  { key: 'titles', label: t('chat.quickTitles'), content: t('chat.quickTitlesPrompt') },
  { key: 'optimize', label: t('chat.quickOptimize'), content: t('chat.quickOptimizePrompt') },
  { key: 'compliance', label: t('chat.quickCompliance'), content: t('chat.quickCompliancePrompt') },
  { key: 'topics', label: t('chat.quickTopics'), content: t('chat.quickTopicsPrompt') },
  { key: 'search', label: t('chat.quickSearch'), content: t('chat.quickSearchPrompt') },
  { key: 'history', label: t('chat.quickHistory'), content: t('chat.quickHistoryPrompt') },
])

onMounted(async () => {
  if (userStore.profile?.defaultPersona) {
    persona.value = userStore.profile.defaultPersona as PersonaType
  }
  await loadSessions()
  if (sessions.value.length) {
    await selectSession(sessions.value[0].sessionId)
  }
})

watch(
  () => userStore.profile?.defaultPersona,
  (value) => {
    if (value) persona.value = value as PersonaType
  },
)

async function handleNewSession() {
  await startNewSession()
  inputText.value = ''
  clearCover()
}

async function handleCoverSelect(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) return
  coverUploading.value = true
  try {
    const res = await uploadCoverImage(file)
    draft.coverImageUrl = res.data.data?.coverImageUrl ?? ''
    if (coverPreviewUrl.value.startsWith('blob:')) {
      URL.revokeObjectURL(coverPreviewUrl.value)
    }
    if (draft.coverImageUrl) {
      coverPreviewUrl.value = await resolveCoverPreviewUrl(draft.coverImageUrl)
    }
  } catch (err) {
    ElMessage.error((err as Error).message || t('common.requestFailed'))
  } finally {
    coverUploading.value = false
  }
}

function clearCover() {
  draft.coverImageUrl = ''
  if (coverPreviewUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(coverPreviewUrl.value)
  }
  coverPreviewUrl.value = ''
}

onUnmounted(() => {
  if (coverPreviewUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(coverPreviewUrl.value)
  }
})

function applyQuickAction(action: { content: string }) {
  inputText.value = action.content
}

async function handleSend() {
  const attachments = buildAttachments()
  const ok = await sendMessage(inputText.value, attachments)
  if (ok) {
    inputText.value = ''
  }
}

function buildAttachments() {
  const title = draft.title.trim()
  const body = draft.body.trim()
  const coverImageUrl = draft.coverImageUrl.trim()
  if (!title && !body && !coverImageUrl) return undefined
  return {
    title: title || undefined,
    body: body || undefined,
    coverImageUrl: coverImageUrl || undefined,
  }
}

function formatTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString()
}
</script>

<style scoped>
.chat-view {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 0;
  height: calc(100vh - 60px - 40px);
  margin: -20px;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  overflow: hidden;
}
.session-panel {
  border-right: 1px solid #eee;
  padding: 16px;
  overflow-y: auto;
  overflow-x: hidden;
  background: #fafafa;
  scrollbar-width: thin;
  scrollbar-color: #d1d5db #f3f4f6;
}
.session-panel::-webkit-scrollbar {
  width: 8px;
}
.session-panel::-webkit-scrollbar-track {
  background: #f3f4f6;
  border-radius: 4px;
}
.session-panel::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 4px;
}
.session-panel::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}
.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.session-title {
  font-weight: 600;
  color: #374151;
}
.session-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.session-list li {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 6px;
  border: 1px solid transparent;
}
.session-list li:hover {
  background: #fff;
  border-color: #e5e7eb;
}
.session-list li.active {
  background: #fff1f2;
  border-color: #fecdd3;
}
.session-item-title {
  font-size: 13px;
  font-weight: 500;
  color: #111827;
  margin-bottom: 4px;
}
.session-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #9ca3af;
}
.chat-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #eee;
}
.chat-header h1 {
  margin: 0 0 4px;
  font-size: 18px;
  color: #111827;
}
.chat-header p {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
}
.chat-input {
  border-top: 1px solid #eee;
  padding: 12px 20px 16px;
  background: #fff;
}
.draft-collapse {
  margin-bottom: 8px;
  border: none;
}
.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.input-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: end;
}
.send-btn {
  height: 40px;
  min-width: 88px;
}
.input-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #9ca3af;
}
.cover-upload {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.cover-preview {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}
@media (max-width: 960px) {
  .chat-view {
    grid-template-columns: 1fr;
    height: auto;
    min-height: calc(100vh - 100px);
  }
  .session-panel {
    max-height: 180px;
    border-right: none;
    border-bottom: 1px solid #eee;
  }
}
</style>
