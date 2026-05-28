<template>
  <div class="login-page">
    <div class="login-panel brand-panel">
      <div class="brand-toolbar">
        <LanguageSwitcher />
      </div>
      <div class="brand-content">
        <div class="brand-badge">{{ t('auth.brandBadge') }}</div>
        <h1>{{ brandTitle }}</h1>
        <p class="brand-desc">{{ t('auth.brandDesc') }}</p>
        <ul class="feature-list">
          <li>{{ t('auth.feature1') }}</li>
          <li>{{ t('auth.feature2') }}</li>
          <li>{{ t('auth.feature3') }}</li>
        </ul>
      </div>
    </div>

    <div class="login-panel form-panel">
      <div class="form-wrapper">
        <div class="form-header">
          <div class="form-header-top">
            <div>
              <h2>{{ activeTab === 'login' ? t('auth.welcomeBack') : t('auth.createAccount') }}</h2>
              <p>{{ activeTab === 'login' ? t('auth.loginSubtitle') : t('auth.registerSubtitle') }}</p>
            </div>
            <LanguageSwitcher class="form-language-switcher" />
          </div>
        </div>

        <el-tabs v-model="activeTab" class="auth-tabs" stretch>
          <el-tab-pane :label="t('auth.loginTab')" name="login">
            <el-form
              ref="loginFormRef"
              :model="loginForm"
              :rules="loginRules"
              label-position="top"
              size="large"
              @submit.prevent="handleLogin"
            >
              <el-form-item :label="t('auth.email')" prop="email">
                <el-input
                  v-model="loginForm.email"
                  :placeholder="t('auth.emailPlaceholder')"
                  :prefix-icon="Message"
                  autocomplete="email"
                />
              </el-form-item>
              <el-form-item :label="t('auth.password')" prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  :placeholder="t('auth.passwordPlaceholder')"
                  :prefix-icon="Lock"
                  show-password
                  autocomplete="current-password"
                  @keyup.enter="handleLogin"
                />
              </el-form-item>
              <el-button
                type="primary"
                class="submit-btn"
                :loading="userStore.loading"
                @click="handleLogin"
              >
                {{ t('auth.loginBtn') }}
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane :label="t('auth.registerTab')" name="register">
            <el-form
              ref="registerFormRef"
              :model="registerForm"
              :rules="registerRules"
              label-position="top"
              size="large"
              @submit.prevent="handleRegister"
            >
              <el-form-item :label="t('auth.nickname')" prop="displayName">
                <el-input
                  v-model="registerForm.displayName"
                  :placeholder="t('auth.nicknamePlaceholder')"
                  :prefix-icon="User"
                  autocomplete="nickname"
                />
              </el-form-item>
              <el-form-item :label="t('auth.email')" prop="email">
                <el-input
                  v-model="registerForm.email"
                  :placeholder="t('auth.emailPlaceholder')"
                  :prefix-icon="Message"
                  autocomplete="email"
                />
              </el-form-item>
              <el-form-item :label="t('auth.password')" prop="password">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  :placeholder="t('auth.passwordRegisterPlaceholder')"
                  :prefix-icon="Lock"
                  show-password
                  autocomplete="new-password"
                />
              </el-form-item>
              <el-form-item :label="t('auth.confirmPassword')" prop="confirmPassword">
                <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  :placeholder="t('auth.confirmPasswordPlaceholder')"
                  :prefix-icon="Lock"
                  show-password
                  autocomplete="new-password"
                  @keyup.enter="handleRegister"
                />
              </el-form-item>
              <el-button
                type="primary"
                class="submit-btn"
                :loading="userStore.loading"
                @click="handleRegister"
              >
                {{ t('auth.registerBtn') }}
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, Message, User } from '@element-plus/icons-vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()

const activeTab = ref<'login' | 'register'>('login')
const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

const loginForm = reactive({
  email: '',
  password: '',
})

const registerForm = reactive({
  displayName: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const brandTitle = computed(() => t('auth.brandTitle'))

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const loginRules = computed<FormRules>(() => ({
  email: [
    { required: true, message: t('auth.validation.emailRequired'), trigger: 'blur' },
    { pattern: emailPattern, message: t('auth.validation.emailInvalid'), trigger: 'blur' },
  ],
  password: [
    { required: true, message: t('auth.validation.passwordRequired'), trigger: 'blur' },
    { min: 8, message: t('auth.validation.passwordMin'), trigger: 'blur' },
  ],
}))

const registerRules = computed<FormRules>(() => ({
  displayName: [{ max: 32, message: t('auth.validation.nicknameMax'), trigger: 'blur' }],
  email: [
    { required: true, message: t('auth.validation.emailRequired'), trigger: 'blur' },
    { pattern: emailPattern, message: t('auth.validation.emailInvalid'), trigger: 'blur' },
  ],
  password: [
    { required: true, message: t('auth.validation.passwordRequired'), trigger: 'blur' },
    { min: 8, message: t('auth.validation.passwordMin'), trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
      message: t('auth.validation.passwordPattern'),
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: t('auth.validation.confirmPasswordRequired'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error(t('auth.validation.confirmPasswordMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}))

async function navigateAfterAuth() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  await router.replace(redirect)
}

async function handleLogin() {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    await userStore.login({
      email: loginForm.email.trim(),
      password: loginForm.password,
    })
    ElMessage.success(t('auth.loginSuccess'))
    await navigateAfterAuth()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : t('auth.loginFailed'))
  }
}

async function handleRegister() {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    await userStore.register({
      email: registerForm.email.trim(),
      password: registerForm.password,
      displayName: registerForm.displayName.trim() || undefined,
    })
    ElMessage.success(t('auth.registerSuccess'))
    await navigateAfterAuth()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : t('auth.registerFailed'))
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.brand-panel {
  background:
    radial-gradient(circle at 20% 20%, rgba(255, 36, 66, 0.35), transparent 45%),
    radial-gradient(circle at 80% 80%, rgba(255, 120, 140, 0.2), transparent 40%),
    linear-gradient(145deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  color: #fff;
  padding: 48px;
}

.brand-toolbar {
  position: absolute;
  top: 24px;
  right: 24px;
}

.brand-toolbar :deep(.language-btn) {
  color: rgba(255, 255, 255, 0.9);
}

.brand-content {
  max-width: 480px;
}

.brand-badge {
  display: inline-block;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(255, 36, 66, 0.2);
  border: 1px solid rgba(255, 36, 66, 0.45);
  color: #ff8a9b;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.04em;
  margin-bottom: 24px;
}

.brand-content h1 {
  margin: 0 0 16px;
  font-size: 36px;
  line-height: 1.25;
  font-weight: 700;
  white-space: pre-line;
}

.brand-desc {
  margin: 0 0 28px;
  color: rgba(255, 255, 255, 0.78);
  line-height: 1.7;
  font-size: 15px;
}

.feature-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.feature-list li {
  position: relative;
  padding-left: 18px;
  margin-bottom: 12px;
  color: rgba(255, 255, 255, 0.88);
  font-size: 14px;
  line-height: 1.6;
}

.feature-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 9px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff2442;
}

.form-panel {
  background: #f7f8fa;
  padding: 48px 32px;
}

.form-wrapper {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 36px 32px 28px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.08);
}

.form-header-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 24px;
}

.form-header h2 {
  margin: 0 0 8px;
  font-size: 26px;
  color: #1f2937;
}

.form-header p {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.form-language-switcher {
  flex-shrink: 0;
}

.auth-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
}

.auth-tabs :deep(.el-tabs__item.is-active) {
  color: #ff2442;
}

.auth-tabs :deep(.el-tabs__active-bar) {
  background-color: #ff2442;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
  height: 44px;
  font-size: 15px;
  --el-button-bg-color: #ff2442;
  --el-button-border-color: #ff2442;
  --el-button-hover-bg-color: #e61f3b;
  --el-button-hover-border-color: #e61f3b;
  --el-button-active-bg-color: #cc1a34;
  --el-button-active-border-color: #cc1a34;
}

@media (max-width: 960px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: 280px;
    padding: 32px 24px;
  }

  .brand-content h1 {
    font-size: 28px;
  }

  .form-panel {
    padding: 24px 16px 40px;
  }

  .form-language-switcher {
    display: none;
  }
}
</style>
