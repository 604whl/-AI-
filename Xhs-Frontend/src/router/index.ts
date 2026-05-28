import { createRouter, createWebHistory } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { getAccessToken } from '@/utils/token'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: DefaultLayout,
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: 'analysis/new', name: 'analysis-new', component: () => import('@/views/analysis/AnalysisNewView.vue') },
        { path: 'analysis/:id', name: 'analysis-report', component: () => import('@/views/analysis/AnalysisReportView.vue') },
        { path: 'titles', name: 'titles', component: () => import('@/views/titles/TitlesView.vue') },
        { path: 'history', name: 'history', component: () => import('@/views/history/HistoryView.vue') },
        { path: 'settings', name: 'settings', component: () => import('@/views/settings/SettingsView.vue') },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const isPublic = to.meta.public === true
  const hasToken = !!getAccessToken()

  if (isPublic) {
    if (hasToken && to.name === 'login') {
      return { path: '/' }
    }
    return true
  }

  if (!hasToken) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const userStore = useUserStore()
  if (!userStore.profile) {
    await userStore.fetchProfile()
  }

  return true
})

export default router
