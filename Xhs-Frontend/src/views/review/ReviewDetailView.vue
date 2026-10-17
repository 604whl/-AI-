<template>
  <div class="review-detail-page">
    <!-- 加载状态 -->
    <el-skeleton v-if="loading" :rows="8" animated />

    <!-- 错误状态 -->
    <el-empty v-else-if="!task" :description="t('review.detailNotFound')" />

    <template v-else>
      <!-- 页面头部 -->
      <div class="page-header">
        <div class="header-left">
          <el-button text @click="router.back()">
            <el-icon><ArrowLeft /></el-icon>
            {{ t('report.back') }}
          </el-button>
          <div>
            <h1 class="page-title">{{ t('review.detailTitle') }}</h1>
            <div class="meta-row">
              <ReviewStatusBadge :status="task.status" :high-risk="task.isHighRisk" show-risk />
              <span class="version-badge">v{{ task.versionNo }}</span>
              <span class="meta-text">{{ t('review.submittedBy', { name: task.submitterNickname || '—' }) }}</span>
              <span class="meta-text">{{ formatRelativeTime(task.createdAt, locale) }}</span>
            </div>
          </div>
        </div>
        <div class="header-actions">
          <!-- 高风险警告 -->
          <el-alert
            v-if="task.isHighRisk"
            type="warning"
            :title="t('review.highRiskAlert')"
            :description="t('review.highRiskDesc', { score: task.riskScore })"
            :closable="false"
            show-icon
          />

          <!-- Reviewer 操作按钮 -->
          <template v-if="isReviewer && (task.status === 'pending' || task.status === 'in_review')">
            <el-button type="success" :loading="actionLoading" @click="showApproveDialog = true">
              {{ t('review.approve') }}
            </el-button>
            <el-button type="danger" :loading="actionLoading" @click="showRejectDialog = true">
              {{ t('review.reject') }}
            </el-button>
          </template>

          <!-- Editor 撤回按钮 -->
          <template v-if="canCancel && (task.status === 'pending' || task.status === 'in_review')">
            <el-button :loading="actionLoading" @click="handleCancel">
              {{ t('review.cancel') }}
            </el-button>
          </template>
        </div>
      </div>

      <div class="content-grid">
        <!-- 左侧：资产内容 -->
        <div class="asset-panel">
          <el-card shadow="never" :header="t('review.assetContent')">
            <template v-if="task.asset">
              <div class="asset-meta">
                <span class="asset-title">{{ task.asset.title || t('history.untitled') }}</span>
                <span class="asset-platform">{{ task.asset.platform || '—' }}</span>
              </div>
              <div class="asset-body">
                {{ task.asset.body || t('review.bodyEmpty') }}
              </div>
            </template>
            <el-empty v-else :description="t('review.assetNotFound')" />
          </el-card>

          <!-- 风险标签 -->
          <el-card v-if="task.riskTags?.length" shadow="never" :header="t('review.riskTags')" class="risk-tags-card">
            <div class="risk-tags">
              <el-tag
                v-for="tag in task.riskTags"
                :key="tag"
                type="danger"
                size="small"
                effect="plain"
              >
                {{ tag }}
              </el-tag>
            </div>
          </el-card>
        </div>

        <!-- 右侧：审核批注 -->
        <div class="comments-panel">
          <el-card shadow="never" :header="t('review.comments')">
            <!-- 批注列表 -->
            <div v-if="task.comments?.length" class="comment-list">
              <div
                v-for="comment in task.comments"
                :key="comment.id"
                class="comment-item"
                :class="`comment-${comment.commentType}`"
              >
                <div class="comment-header">
                  <span class="comment-author">{{ comment.creatorNickname || '—' }}</span>
                  <el-tag
                    v-if="comment.commentType !== 'comment'"
                    :type="comment.commentType === 'approve' ? 'success' : 'danger'"
                    size="small"
                  >
                    {{ t(`review.commentType.${comment.commentType}`) }}
                  </el-tag>
                  <span class="comment-time">{{ formatRelativeTime(comment.createdAt, locale) }}</span>
                </div>

                <!-- 高亮文本 -->
                <div v-if="comment.highlightedText" class="comment-highlight">
                  {{ comment.highlightedText }}
                </div>

                <div class="comment-body">{{ comment.comment }}</div>
              </div>
            </div>
            <el-empty v-else :description="t('review.commentsEmpty')" />

            <!-- 添加批注 -->
            <div class="add-comment">
              <el-input
                v-model="newComment"
                type="textarea"
                :rows="3"
                :placeholder="t('review.commentPlaceholder')"
                maxlength="2000"
                show-word-limit
              />
              <el-button
                type="primary"
                :loading="commentLoading"
                :disabled="!newComment.trim()"
                style="margin-top: 8px;"
                @click="handleAddComment"
              >
                {{ t('review.addComment') }}
              </el-button>
            </div>
          </el-card>

          <!-- 审核历史 -->
          <el-card v-if="task.comment" shadow="never" :header="t('review.reviewDecision')" class="decision-card">
            <div class="decision-body">
              <el-tag
                :type="task.status === 'approved' ? 'success' : task.status === 'rejected' ? 'danger' : 'info'"
                size="small"
              >
                {{ t(`review.statuses.${task.status}`) }}
              </el-tag>
              <p class="decision-comment">{{ task.comment || '' }}</p>
              <span class="decision-time">{{ formatRelativeTime(task.decidedAt ?? '', locale) }}</span>
            </div>
          </el-card>
        </div>
      </div>
    </template>

    <!-- 审批对话框 -->
    <el-dialog v-model="showApproveDialog" :title="t('review.approveConfirmTitle')" width="480px">
      <el-input
        v-model="approveComment"
        type="textarea"
        :rows="3"
        :placeholder="t('review.approveCommentPlaceholder')"
      />
      <template #footer>
        <el-button @click="showApproveDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="success" :loading="actionLoading" @click="handleApprove">
          {{ t('review.confirmApprove') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRejectDialog" :title="t('review.rejectConfirmTitle')" width="480px">
      <el-input
        v-model="rejectComment"
        type="textarea"
        :rows="4"
        :placeholder="t('review.rejectCommentPlaceholder')"
      />
      <template #footer>
        <el-button @click="showRejectDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="actionLoading" :disabled="!rejectComment.trim()" @click="handleReject">
          {{ t('review.confirmReject') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useWorkspaceStore } from '@/stores/workspace'
import { formatRelativeTime } from '@/utils/analysisDisplay'
import {
  fetchReviewTask,
  approveReview,
  rejectReview,
  cancelReview,
  addReviewComment,
} from '@/api/review'
import ReviewStatusBadge from '@/components/review/ReviewStatusBadge.vue'
import type { ReviewTaskDetail } from '@/api/review'

const props = defineProps<{ taskId: string }>()

const { t, locale } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const workspaceStore = useWorkspaceStore()

const loading = ref(false)
const actionLoading = ref(false)
const commentLoading = ref(false)
const task = ref<ReviewTaskDetail | null>(null)

const showApproveDialog = ref(false)
const showRejectDialog = ref(false)
const approveComment = ref('')
const rejectComment = ref('')
const newComment = ref('')

const isReviewer = computed(() => {
  const role = workspaceStore.currentRole
  return role && ['ADMIN', 'OWNER', 'REVIEWER'].includes(role)
})

const canCancel = computed(() => {
  const userId = userStore.profile?.id
  return userId && task.value
    ? (task.value.submittedBy === userId || isReviewer.value)
    : false
})

async function loadTask() {
  loading.value = true
  try {
    const res = await fetchReviewTask(props.taskId)
    task.value = res.data.data
  } catch {
    task.value = null
    ElMessage.error(t('review.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function handleApprove() {
  actionLoading.value = true
  try {
    const res = await approveReview(props.taskId, { comment: approveComment.value || undefined })
    task.value = res.data.data
    showApproveDialog.value = false
    approveComment.value = ''
    ElMessage.success(t('review.approveSuccess'))
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || t('review.approveFailed'))
  } finally {
    actionLoading.value = false
  }
}

async function handleReject() {
  if (!rejectComment.value.trim()) {
    ElMessage.warning(t('review.rejectCommentRequired'))
    return
  }
  actionLoading.value = true
  try {
    const res = await rejectReview(props.taskId, { comment: rejectComment.value })
    task.value = res.data.data
    showRejectDialog.value = false
    rejectComment.value = ''
    ElMessage.success(t('review.rejectSuccess'))
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || t('review.rejectFailed'))
  } finally {
    actionLoading.value = false
  }
}

async function handleCancel() {
  actionLoading.value = true
  try {
    const res = await cancelReview(props.taskId)
    task.value = res.data.data
    ElMessage.success(t('review.cancelSuccess'))
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || t('review.cancelFailed'))
  } finally {
    actionLoading.value = false
  }
}

async function handleAddComment() {
  if (!newComment.value.trim()) return
  commentLoading.value = true
  try {
    await addReviewComment(props.taskId, { comment: newComment.value.trim() })
    newComment.value = ''
    await loadTask()
    ElMessage.success(t('review.addCommentSuccess'))
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || t('review.addCommentFailed'))
  } finally {
    commentLoading.value = false
  }
}

onMounted(async () => {
  await workspaceStore.fetchWorkspaces()
  await loadTask()
})
</script>

<style scoped>
.review-detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  color: #111827;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.meta-text {
  font-size: 13px;
  color: #6b7280;
}

.version-badge {
  font-family: monospace;
  font-size: 12px;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 4px;
  padding: 2px 8px;
}

.header-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 16px;
  align-items: start;
}

.asset-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.asset-meta {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}

.asset-title {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

.asset-platform {
  font-size: 13px;
  color: #6b7280;
}

.asset-body {
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.7;
  color: #374151;
  max-height: 500px;
  overflow-y: auto;
}

.risk-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.comments-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 16px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
  max-height: 400px;
  overflow-y: auto;
}

.comment-item {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
}

.comment-approve {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.comment-reject {
  border-color: #fecaca;
  background: #fef2f2;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.comment-author {
  font-weight: 600;
  font-size: 13px;
  color: #374151;
}

.comment-time {
  font-size: 12px;
  color: #9ca3af;
  margin-left: auto;
}

.comment-highlight {
  font-size: 12px;
  color: #6b7280;
  background: #fef3c7;
  border-radius: 4px;
  padding: 4px 8px;
  margin-bottom: 6px;
  font-style: italic;
}

.comment-body {
  font-size: 14px;
  color: #374151;
  white-space: pre-wrap;
}

.add-comment {
  border-top: 1px solid #e5e7eb;
  padding-top: 12px;
}

.decision-card .decision-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.decision-comment {
  margin: 0;
  font-size: 14px;
  color: #374151;
  white-space: pre-wrap;
}

.decision-time {
  font-size: 12px;
  color: #9ca3af;
}

@media (max-width: 900px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
  .comments-panel {
    position: static;
  }
}
</style>
