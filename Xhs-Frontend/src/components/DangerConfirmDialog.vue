<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    @closed="onClosed"
  >
    <div class="danger-confirm">
      <div class="danger-icon">
        <el-icon :size="48" color="#F56C6C"><WarningFilled /></el-icon>
      </div>

      <p class="danger-message">{{ message }}</p>

      <div v-if="showPhraseInput" class="phrase-input">
        <p class="phrase-hint">
          {{ phraseHint }}
        </p>
        <el-input
          v-model="inputPhrase"
          :placeholder="phrasePlaceholder"
          :class="{ 'is-error': phraseError }"
          @input="phraseError = false"
        />
        <p v-if="phraseError" class="phrase-error">
          {{ phraseErrorText }}
        </p>
      </div>

      <div v-if="countdownDays !== null" class="countdown-info">
        <el-alert type="warning" :closable="false">
          <template #title>
            {{ t('retention.countdownWarning', { days: countdownDays }) }}
          </template>
        </el-alert>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleCancel">
        {{ cancelText || t('common.cancel') }}
      </el-button>
      <el-button
        :type="dangerType"
        :disabled="showPhraseInput && inputPhrase !== requiredPhrase"
        :loading="loading"
        @click="handleConfirm"
      >
        {{ confirmText || t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title?: string
  message?: string
  confirmText?: string
  cancelText?: string
  dangerType?: 'danger' | 'warning'
  width?: string
  showPhraseInput?: boolean
  requiredPhrase?: string
  phrasePlaceholder?: string
  phraseHint?: string
  phraseErrorText?: string
  countdownDays?: number | null
  loading?: boolean
}>(), {
  title: 'Confirm Action',
  message: 'Are you sure you want to proceed?',
  dangerType: 'danger',
  width: '420px',
  showPhraseInput: false,
  requiredPhrase: 'DELETE',
  phrasePlaceholder: 'DELETE',
  phraseHint: 'Please type DELETE to confirm',
  phraseErrorText: 'Please type DELETE exactly',
  countdownDays: null,
  loading: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

const { t } = useI18n()

const visible = ref(props.modelValue)
const inputPhrase = ref('')
const phraseError = ref(false)

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (!val) {
    inputPhrase.value = ''
    phraseError.value = false
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

function onClosed() {
  inputPhrase.value = ''
  phraseError.value = false
}

function handleCancel() {
  visible.value = false
  emit('cancel')
}

function handleConfirm() {
  if (props.showPhraseInput) {
    if (inputPhrase.value.trim().toUpperCase() !== props.requiredPhrase.toUpperCase()) {
      phraseError.value = true
      return
    }
  }
  emit('confirm')
}
</script>

<style scoped>
.danger-confirm {
  text-align: center;
  padding: 16px 0;
}

.danger-icon {
  margin-bottom: 16px;
}

.danger-message {
  font-size: 15px;
  color: var(--el-text-color-regular);
  margin: 0 0 20px 0;
  line-height: 1.6;
}

.phrase-input {
  text-align: left;
  margin-top: 8px;
}

.phrase-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0 0 8px 0;
}

.phrase-input :deep(.el-input) {
  max-width: 200px;
  margin: 0 auto;
}

.phrase-error {
  color: var(--el-color-danger);
  font-size: 12px;
  margin: 8px 0 0 0;
}

.countdown-info {
  margin-top: 16px;
  text-align: left;
}
</style>
