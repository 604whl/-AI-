<template>
  <el-dropdown trigger="click" @command="onCommand">
    <span class="workspace-selector" :class="{ 'is-empty': !activeWorkspace }">
      <el-icon class="ws-icon"><OfficeBuilding /></el-icon>
      <span class="ws-label">{{ activeWorkspace?.name || t('workspace.select') }}</span>
      <el-icon class="ws-arrow"><ArrowDown /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="ws in workspaces"
          :key="ws.id"
          :command="`switch:${ws.id}`"
          :class="{ 'is-active': ws.id === activeWorkspaceId }"
        >
          <div class="ws-menu-item">
            <div class="ws-menu-name">{{ ws.name }}</div>
            <div class="ws-menu-meta">
              <el-tag size="small" type="info" effect="plain" round>{{ roleLabel(ws.role) }}</el-tag>
            </div>
          </div>
        </el-dropdown-item>
        <el-dropdown-item divided command="create" :disabled="!canCreate">
          <el-icon><Plus /></el-icon>
          <span>{{ t('workspace.createNew') }}</span>
        </el-dropdown-item>
        <el-dropdown-item command="manage" :disabled="!activeWorkspace">
          <el-icon><Setting /></el-icon>
          <span>{{ t('workspace.manage') }}</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>

  <el-dialog
    v-model="createDialogVisible"
    :title="t('workspace.createNew')"
    width="420px"
    :close-on-click-modal="false"
  >
    <el-form :model="createForm" label-position="top" @submit.prevent>
      <el-form-item :label="t('workspace.name')" required>
        <el-input v-model="createForm.name" maxlength="60" show-word-limit :placeholder="t('workspace.namePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('workspace.industry')">
        <el-input v-model="createForm.industry" maxlength="40" :placeholder="t('workspace.industryPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('workspace.region')">
        <el-select v-model="createForm.region" :placeholder="t('workspace.regionPlaceholder')" clearable>
          <el-option label="CN" value="CN" />
          <el-option label="HK" value="HK" />
          <el-option label="US" value="US" />
          <el-option label="UK" value="UK" />
          <el-option label="SG" value="SG" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('workspace.dailyQuota')">
        <el-input-number v-model="createForm.defaultQuota" :min="1" :max="500" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="createLoading" @click="confirmCreate">{{ t('common.confirm') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="manageDialogVisible"
    :title="t('workspace.manage')"
    width="560px"
    :close-on-click-modal="false"
  >
    <div v-if="activeWorkspace" class="manage-section">
      <div class="manage-summary">
        <div class="manage-name">{{ activeWorkspace.name }}</div>
        <el-tag size="small" type="info" effect="plain" round>{{ roleLabel(activeWorkspace.role) }}</el-tag>
      </div>
      <el-divider />
      <div v-loading="membersLoading">
        <div class="manage-row manage-row--header">
          <span>{{ t('workspace.member') }}</span>
          <el-button
            v-if="canManageMembers"
            size="small"
            type="primary"
            :icon="Plus"
            @click="inviteDialogVisible = true"
          >
            {{ t('workspace.invite') }}
          </el-button>
        </div>
        <div v-for="m in members" :key="m.userId" class="manage-row">
          <div class="manage-user">
            <div class="manage-user-name">{{ m.displayName || m.email || m.userId }}</div>
            <div class="manage-user-email">{{ m.email || `user#${m.userId}` }}</div>
          </div>
          <el-tag v-if="m.status !== 'active'" size="small" type="warning" round>{{ m.status }}</el-tag>
          <el-select
            v-if="canManageMembers && m.role !== 'OWNER'"
            v-model="m.role"
            size="small"
            style="width: 120px"
            @change="(role: WorkspaceRole) => onRoleChange(m.userId, role)"
          >
            <el-option label="ADMIN" value="ADMIN" />
            <el-option label="EDITOR" value="EDITOR" />
            <el-option label="REVIEWER" value="REVIEWER" />
            <el-option label="VIEWER" value="VIEWER" />
          </el-select>
          <el-tag v-else size="small" type="info" round>{{ m.role }}</el-tag>
          <el-button
            v-if="canManageMembers && m.role !== 'OWNER' && m.userId !== userStore.profile?.id"
            type="danger"
            size="small"
            link
            @click="onRemoveMember(m.userId)"
          >
            {{ t('workspace.remove') }}
          </el-button>
        </div>
      </div>
    </div>
  </el-dialog>

  <el-dialog
    v-model="inviteDialogVisible"
    :title="t('workspace.invite')"
    width="380px"
    :close-on-click-modal="false"
  >
    <el-form :model="inviteForm" label-position="top" @submit.prevent>
      <el-form-item :label="t('workspace.email')" required>
        <el-input v-model="inviteForm.email" :placeholder="t('workspace.emailPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('workspace.role')" required>
        <el-select v-model="inviteForm.role">
          <el-option label="ADMIN" value="ADMIN" />
          <el-option label="EDITOR" value="EDITOR" />
          <el-option label="REVIEWER" value="REVIEWER" />
          <el-option label="VIEWER" value="VIEWER" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="inviteDialogVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="inviteLoading" @click="confirmInvite">
        {{ t('workspace.invite') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowDown, OfficeBuilding, Plus, Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import type { WorkspaceMember, WorkspaceRole } from '@/types/auth'

const { t } = useI18n()
const userStore = useUserStore()

const workspaces = computed(() => userStore.workspaces)
const activeWorkspace = computed(() => userStore.activeWorkspace)
const activeWorkspaceId = computed(() => userStore.activeWorkspaceId)
const canCreate = computed(() => !!userStore.profile)
const canManageMembers = computed(() => {
  const r = activeWorkspace.value?.role
  return r === 'OWNER' || r === 'ADMIN'
})

const createDialogVisible = ref(false)
const createLoading = ref(false)
const createForm = ref({
  name: '',
  industry: 'general',
  region: 'CN' as string,
  defaultQuota: 3,
})

const manageDialogVisible = ref(false)
const membersLoading = ref(false)
const members = ref<WorkspaceMember[]>([])

const inviteDialogVisible = ref(false)
const inviteLoading = ref(false)
const inviteForm = ref({
  email: '',
  role: 'EDITOR' as WorkspaceRole,
})

function roleLabel(role: WorkspaceRole | undefined) {
  switch (role) {
    case 'OWNER':
      return t('workspace.roles.owner')
    case 'ADMIN':
      return t('workspace.roles.admin')
    case 'EDITOR':
      return t('workspace.roles.editor')
    case 'REVIEWER':
      return t('workspace.roles.reviewer')
    case 'VIEWER':
      return t('workspace.roles.viewer')
    default:
      return '—'
  }
}

function onCommand(cmd: string) {
  if (cmd === 'create') {
    createForm.value = { name: '', industry: 'general', region: 'CN', defaultQuota: 3 }
    createDialogVisible.value = true
  } else if (cmd === 'manage') {
    loadMembers()
    manageDialogVisible.value = true
  } else if (cmd.startsWith('switch:')) {
    const id = cmd.substring('switch:'.length)
    if (id !== activeWorkspaceId.value) {
      try {
        userStore.switchWorkspace(id)
        ElMessage.success(t('workspace.switched'))
        // 触发全局刷新：让所有依赖 workspaceId 的查询重新发起
        window.dispatchEvent(new CustomEvent('xhs:workspace-changed', { detail: { id } }))
      } catch (e) {
        ElMessage.error((e as Error).message)
      }
    }
  }
}

async function confirmCreate() {
  const name = createForm.value.name.trim()
  if (!name) {
    ElMessage.warning(t('workspace.nameRequired'))
    return
  }
  createLoading.value = true
  try {
    await userStore.createWorkspace({
      name,
      industry: createForm.value.industry || undefined,
      region: createForm.value.region || undefined,
      defaultQuota: createForm.value.defaultQuota,
    })
    createDialogVisible.value = false
    ElMessage.success(t('workspace.created'))
    window.dispatchEvent(new CustomEvent('xhs:workspace-changed', { detail: { id: userStore.activeWorkspaceId } }))
  } catch (e) {
    ElMessage.error((e as Error).message || 'create failed')
  } finally {
    createLoading.value = false
  }
}

async function loadMembers() {
  if (!activeWorkspaceId.value) {
    members.value = []
    return
  }
  membersLoading.value = true
  try {
    members.value = await userStore.listMembers(activeWorkspaceId.value)
  } catch (e) {
    members.value = []
    ElMessage.error((e as Error).message || 'load failed')
  } finally {
    membersLoading.value = false
  }
}

async function confirmInvite() {
  const email = inviteForm.value.email.trim()
  if (!email) {
    ElMessage.warning(t('workspace.emailRequired'))
    return
  }
  inviteLoading.value = true
  try {
    await userStore.inviteMember(activeWorkspaceId.value!, email, inviteForm.value.role)
    inviteDialogVisible.value = false
    inviteForm.value = { email: '', role: 'EDITOR' }
    ElMessage.success(t('workspace.invited'))
    await loadMembers()
  } catch (e) {
    ElMessage.error((e as Error).message || 'invite failed')
  } finally {
    inviteLoading.value = false
  }
}

async function onRoleChange(userId: number, role: WorkspaceRole) {
  try {
    await userStore.updateMemberRole(activeWorkspaceId.value!, userId, role)
    ElMessage.success(t('workspace.roleUpdated'))
    await loadMembers()
  } catch (e) {
    ElMessage.error((e as Error).message || 'update failed')
    await loadMembers()
  }
}

async function onRemoveMember(userId: number) {
  try {
    await ElMessageBox.confirm(t('workspace.confirmRemove'), t('common.confirm'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await userStore.removeMember(activeWorkspaceId.value!, userId)
    ElMessage.success(t('workspace.removed'))
    await loadMembers()
  } catch (e) {
    ElMessage.error((e as Error).message || 'remove failed')
  }
}

watch(activeWorkspaceId, () => {
  // 切换激活 workspace 后，关闭管理对话框避免错位
  if (manageDialogVisible.value) {
    loadMembers()
  }
})

onMounted(() => {
  if (userStore.isLoggedIn() && workspaces.value.length === 0) {
    userStore.fetchWorkspaces().catch(() => undefined)
  }
})
</script>

<style scoped>
.workspace-selector {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-primary);
  transition: border-color 0.15s, background 0.15s;
}
.workspace-selector:hover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.workspace-selector.is-empty {
  color: var(--el-text-color-secondary);
}
.ws-icon {
  font-size: 16px;
  color: var(--el-color-primary);
}
.ws-label {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ws-arrow {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ws-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 220px;
}
.ws-menu-name {
  font-weight: 500;
}
.ws-menu-meta {
  flex-shrink: 0;
}
:deep(.el-dropdown-menu__item.is-active) {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.manage-section {
  display: flex;
  flex-direction: column;
}
.manage-summary {
  display: flex;
  align-items: center;
  gap: 12px;
}
.manage-name {
  font-size: 16px;
  font-weight: 600;
  flex: 1;
}
.manage-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.manage-row:last-child {
  border-bottom: none;
}
.manage-row--header {
  font-weight: 600;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.manage-user {
  flex: 1;
  min-width: 0;
}
.manage-user-name {
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.manage-user-email {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>