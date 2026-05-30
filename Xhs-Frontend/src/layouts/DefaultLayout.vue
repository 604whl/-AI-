<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">{{ t('nav.logo') }}</div>
      <el-menu router :default-active="route.path">
        <el-menu-item index="/">{{ t('nav.dashboard') }}</el-menu-item>
        <el-menu-item index="/analysis/new">{{ t('nav.analysisNew') }}</el-menu-item>
        <el-menu-item index="/titles">{{ t('nav.titles') }}</el-menu-item>
        <el-menu-item index="/history">{{ t('nav.history') }}</el-menu-item>
        <el-menu-item index="/settings">{{ t('nav.settings') }}</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="header-title">{{ t('nav.headerTitle') }}</span>
        <div class="header-actions">
          <LanguageSwitcher />
          <span v-if="userStore.profile" class="user-name">
            {{ userStore.profile.displayName || userStore.profile.email }}
          </span>
          <el-button text type="danger" @click="handleLogout">{{ t('nav.logout') }}</el-button>
        </div>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()

async function handleLogout() {
  await userStore.logout()
  await router.push({ name: 'login' })
}
</script>

<style scoped>
.layout { min-height: 100vh; }
.aside { border-right: 1px solid #eee; }
.logo { padding: 20px 16px; font-weight: 700; color: #ff2442; }
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eee;
  line-height: 60px;
}
.header-title { font-size: 15px; color: #374151; }
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-name {
  font-size: 14px;
  color: #6b7280;
}
</style>
