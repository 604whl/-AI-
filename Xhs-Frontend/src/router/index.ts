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
        { path: 'chat', name: 'chat', component: () => import('@/views/chat/ChatView.vue') },
        { path: 'titles', name: 'titles', component: () => import('@/views/titles/TitlesView.vue') },
        { path: 'history', name: 'history', component: () => import('@/views/history/HistoryView.vue') },
        { path: 'settings', name: 'settings', component: () => import('@/views/settings/SettingsView.vue') },
        { path: 'settings/account', name: 'account-settings', component: () => import('@/views/account/AccountSettingsView.vue') },
        { path: 'settings/danger-zone', name: 'account-delete', component: () => import('@/views/user/AccountDeleteView.vue') },
        { path: 'workspace/settings', name: 'workspace-settings', component: () => import('@/views/workspace/WorkspaceSettingsView.vue') },
        { path: 'workspace/retention', name: 'retention-policy', component: () => import('@/views/workspace/RetentionPolicyView.vue') },
        { path: 'workspace/danger-zone', name: 'workspace-destroy', component: () => import('@/views/workspace/WorkspaceDestroyView.vue') },
        { path: 'admin/retention', name: 'admin-retention', component: () => import('@/views/admin/AdminRetentionView.vue') },
        { path: 'workspace/quota', name: 'quota-manage', component: () => import('@/views/workspace/QuotaManageView.vue') },
        { path: 'asset', name: 'asset-list', component: () => import('@/views/asset/ContentAssetListView.vue') },
        { path: 'asset/:id', name: 'asset-detail', component: () => import('@/views/asset/ContentAssetDetailView.vue') },
        { path: 'asset/:id/versions', name: 'asset-diff', component: () => import('@/views/asset/ContentAssetDiffView.vue') },
        { path: 'workspace/exports', name: 'export-log', component: () => import('@/views/workspace/ExportLogView.vue') },
        { path: 'template', name: 'template-library', component: () => import('@/views/template/TemplateLibraryView.vue') },
        { path: 'template/new', name: 'template-new', component: () => import('@/views/template/TemplateEditorView.vue') },
        { path: 'template/:id', name: 'template-edit', component: () => import('@/views/template/TemplateEditorView.vue') },
        { path: 'review/queue', name: 'review-queue', component: () => import('@/views/review/ReviewQueueView.vue') },
        { path: 'review/submissions', name: 'review-submissions', component: () => import('@/views/review/ReviewSubmissionListView.vue') },
        { path: 'review/:id', name: 'review-detail', component: () => import('@/views/review/ReviewDetailView.vue') },

        // --- stage 3 (S3-12) ---
        { path: 'publish/schedules', name: 'publish-schedules', component: () => import('@/views/publish/PublishCalendarView.vue') },
        { path: 'metrics', name: 'metrics-dashboard', component: () => import('@/views/metrics/MetricDashboardView.vue') },
        { path: 'retrospectives', name: 'retrospective-list', component: () => import('@/views/retrospective/RetrospectiveListView.vue') },
        { path: 'hot-topics', name: 'hot-topic-list', component: () => import('@/views/hotTopic/HotTopicListView.vue') },
        { path: 'competitors', name: 'competitor-library', component: () => import('@/views/competitor/CompetitorLibraryView.vue') },
        { path: 'admin/kb', name: 'kb-admin', component: () => import('@/views/kb/KbDocumentAdminView.vue') },
        { path: 'admin/connectors', name: 'connector-diagnostics', component: () => import('@/views/connector/ConnectorDiagnosticView.vue') },
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
  if (!userStore.usage) {
    await userStore.fetchUsage()
  }

  return true
})

export default router
