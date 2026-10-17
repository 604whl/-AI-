package com.shortvideoscripagent.xhsagentyunying.service.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppStorageProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.*;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.*;
import com.shortvideoscripagent.xhsagentyunying.dto.retention.WorkspaceDestructionResponse;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 工作区销毁服务（P0-9）。
 * <p>
 * 实现 7 天宽限期的 workspace 销毁流程：
 * 1. OWNER 发起销毁 → 7 天倒计时
 * 2. OWNER 二次确认 → 进入执行队列
 * 3. 7 天到期后 RetentionSweeper 执行物理删除（DB + MinIO 对象）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceDestructionService {

    private static final int CODE_NOT_FOUND = 40404;
    private static final int CODE_CONFLICT = 40904;
    private static final int CODE_BAD_REQUEST = 40004;

    /** 销毁宽限期天数 */
    private static final long DESTRUCTION_GRACE_DAYS = 7L;

    private final WorkspaceDestructionMapper destructionMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final WorkspaceService workspaceService;
    private final ContentAssetMapper contentAssetMapper;
    private final TemplateMapper templateMapper;
    private final ExportLogMapper exportLogMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper usageLedgerMapper;
    private final UsageLogMapper usageLogMapper;
    private final AppStorageProperties storageProperties;

    // ═══════════════════════════════════════════════════════════════════════════
    // 公开 API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 发起工作区销毁申请（7 天宽限期）。
     *
     * @param workspaceId   工作区 ID
     * @param requesterId  申请人 userId（必须是 OWNER）
     * @param confirmPhrase 确认短语（必须为 "DELETE"）
     * @return 销毁状态
     */
    @Transactional
    public WorkspaceDestructionResponse requestDestruction(
            String workspaceId, Long requesterId, String confirmPhrase) {
        validateConfirmPhrase(confirmPhrase);

        // 校验权限：只有 OWNER 可发起
        workspaceService.requireWorkspaceMember(requesterId, workspaceId, "OWNER");

        // 校验 workspace 存在且未在销毁中
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new BusinessException(CODE_NOT_FOUND, "workspace_not_found");
        }

        WorkspaceDestruction existing = destructionMapper.findPendingOrConfirmedByWorkspace(workspaceId);
        if (existing != null && !existing.isCancelled()) {
            throw new BusinessException(CODE_CONFLICT, "destruction_already_in_progress");
        }

        OffsetDateTime now = OffsetDateTime.now();
        WorkspaceDestruction destruction = new WorkspaceDestruction();
        destruction.setId(UUID.randomUUID());
        destruction.setWorkspaceId(workspaceId);
        destruction.setRequestedBy(requesterId);
        destruction.setConfirmPhrase(confirmPhrase);
        destruction.setStatus(WorkspaceDestruction.STATUS_PENDING);
        destruction.setExecuteAt(now.plusDays(DESTRUCTION_GRACE_DAYS));
        destruction.setCreatedAt(now);
        destructionMapper.insert(destruction);

        log.info("Workspace destruction requested for {} by user {}, will execute at {}",
                workspaceId, requesterId, destruction.getExecuteAt());

        return buildResponse(destruction);
    }

    /**
     * 二次确认销毁（OWNER 必须再次确认才会执行）。
     *
     * @param destructionId 销毁记录 ID
     * @param userId       确认人 userId（必须是 OWNER）
     * @param confirmPhrase 确认短语
     * @return 销毁状态
     */
    @Transactional
    public WorkspaceDestructionResponse confirmDestruction(
            UUID destructionId, Long userId, String confirmPhrase) {
        validateConfirmPhrase(confirmPhrase);

        WorkspaceDestruction destruction = requireDestruction(destructionId);
        String workspaceId = destruction.getWorkspaceId();

        // 校验权限
        workspaceService.requireWorkspaceMember(userId, workspaceId, "OWNER");

        if (!destruction.canBeConfirmed()) {
            throw new BusinessException(CODE_CONFLICT, "cannot_confirm_destruction_in_status: " + destruction.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();
        destruction.setStatus(WorkspaceDestruction.STATUS_CONFIRMED);
        destruction.setConfirmedAt(now);
        destructionMapper.updateById(destruction);

        log.info("Workspace destruction {} confirmed by user {}, will execute at {}",
                destructionId, userId, destruction.getExecuteAt());

        return buildResponse(destruction);
    }

    /**
     * 撤销销毁申请。
     *
     * @param destructionId 销毁记录 ID
     * @param userId        撤销人 userId（必须是 OWNER）
     * @return 销毁状态
     */
    @Transactional
    public WorkspaceDestructionResponse cancelDestruction(UUID destructionId, Long userId) {
        WorkspaceDestruction destruction = requireDestruction(destructionId);

        // 校验权限
        workspaceService.requireWorkspaceMember(userId, destruction.getWorkspaceId(), "OWNER");

        if (!destruction.canBeCancelled()) {
            throw new BusinessException(CODE_CONFLICT, "cannot_cancel_destruction_in_status: " + destruction.getStatus());
        }

        destruction.setStatus(WorkspaceDestruction.STATUS_CANCELLED);
        destructionMapper.updateById(destruction);

        log.info("Workspace destruction {} cancelled by user {}", destructionId, userId);

        return buildResponse(destruction);
    }

    /**
     * 查询销毁状态。
     *
     * @param workspaceId 工作区 ID
     * @return 销毁状态（无记录返回 null）
     */
    public WorkspaceDestructionResponse getStatus(String workspaceId) {
        WorkspaceDestruction destruction = destructionMapper.findPendingOrConfirmedByWorkspace(workspaceId);
        if (destruction == null) {
            return null;
        }
        return buildResponse(destruction);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 定时任务调用（RetentionSweeper）
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 执行已到期的 workspace 物理销毁（由 RetentionSweeper 定时调用）。
     * <p>
     * 销毁范围：
     * 1. MinIO 对象（covers/{workspaceId}/*, exports/{workspaceId}/*）
     * 2. 数据库记录（按外键 CASCADE + 手动清理）
     */
    @Transactional
    public void executeScheduledDestruction() {
        OffsetDateTime now = OffsetDateTime.now();

        List<WorkspaceDestruction> readyList = destructionMapper.findReadyToExecute(now);
        if (readyList.isEmpty()) {
            return;
        }

        log.info("Found {} workspaces ready to destroy", readyList.size());

        for (WorkspaceDestruction destruction : readyList) {
            try {
                String workspaceId = destruction.getWorkspaceId();
                destroyWorkspace(workspaceId);

                destruction.setStatus(WorkspaceDestruction.STATUS_EXECUTED);
                destruction.setExecutedAt(now);
                destructionMapper.updateById(destruction);

                log.info("Workspace {} physically destroyed", workspaceId);

            } catch (Exception e) {
                log.error("Failed to destroy workspace {}: {}",
                        destruction.getWorkspaceId(), e.getMessage(), e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 私有辅助方法
    // ═══════════════════════════════════════════════════════════════════════════

    private void validateConfirmPhrase(String confirmPhrase) {
        if (confirmPhrase == null || !confirmPhrase.trim().equalsIgnoreCase("DELETE")) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_confirm_phrase");
        }
    }

    private WorkspaceDestruction requireDestruction(UUID destructionId) {
        WorkspaceDestruction destruction = destructionMapper.selectById(destructionId);
        if (destruction == null) {
            throw new BusinessException(CODE_NOT_FOUND, "destruction_not_found");
        }
        return destruction;
    }

    private WorkspaceDestructionResponse buildResponse(WorkspaceDestruction d) {
        Long daysRemaining = null;
        if (!d.isExecuted() && !d.isCancelled() && d.getExecuteAt() != null) {
            long hours = Duration.between(OffsetDateTime.now(), d.getExecuteAt()).toHours();
            daysRemaining = Math.max(0, hours / 24);
        }

        return WorkspaceDestructionResponse.builder()
                .id(d.getId().toString())
                .workspaceId(d.getWorkspaceId())
                .status(d.getStatus())
                .executeAt(d.getExecuteAt())
                .daysRemaining(daysRemaining)
                .confirmedAt(d.getConfirmedAt())
                .executedAt(d.getExecutedAt())
                .createdAt(d.getCreatedAt())
                .build();
    }

    /**
     * 执行工作区物理销毁。
     */
    private void destroyWorkspace(String workspaceId) {
        log.info("Starting workspace destruction for {}", workspaceId);

        // 1. 清理 MinIO 对象
        cleanupMinioObjects(workspaceId);

        // 2. 清理数据库记录（外键 CASCADE 处理大部分，手动清理关键表）

        // content_asset（ON DELETE CASCADE 会清理 version/media/review）
        contentAssetMapper.delete(new LambdaQueryWrapper<ContentAsset>()
                .eq(ContentAsset::getWorkspaceId, workspaceId));

        // template（ON DELETE CASCADE 会清理 version/apply_log）
        templateMapper.delete(new LambdaQueryWrapper<Template>()
                .eq(Template::getWorkspaceId, workspaceId));

        // export_log
        exportLogMapper.delete(new LambdaQueryWrapper<ExportLog>()
                .eq(ExportLog::getWorkspaceId, workspaceId));

        // chat_session
        chatSessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getWorkspaceId, workspaceId));

        // workspace_member
        memberMapper.delete(new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId));

        // api_key
        apiKeyMapper.delete(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getWorkspaceId, workspaceId));

        // 3. 最后删除 workspace 主记录
        workspaceMapper.deleteById(workspaceId);

        log.info("Workspace {} destruction completed", workspaceId);
    }

    /**
     * 清理 MinIO 中该 workspace 的所有对象。
     */
    private void cleanupMinioObjects(String workspaceId) {
        try {
            if (!"minio".equalsIgnoreCase(storageProperties.getType())) {
                log.debug("Storage type is not minio, skipping MinIO cleanup");
                return;
            }

            AppStorageProperties.Minio minio = storageProperties.getMinio();
            MinioClient client = MinioClient.builder()
                    .endpoint(minio.getEndpoint())
                    .credentials(minio.getAccessKey(), minio.getSecretKey())
                    .build();

            String bucket = minio.getBucket();

            // 清理 covers/{workspaceId}/*
            deleteObjectsWithPrefix(client, bucket, "covers/" + workspaceId + "/");

            // 清理 exports/{workspaceId}/*
            deleteObjectsWithPrefix(client, bucket, "exports/" + workspaceId + "/");

            log.info("MinIO objects cleaned for workspace {}", workspaceId);

        } catch (Exception e) {
            log.error("Failed to cleanup MinIO objects for workspace {}: {}",
                    workspaceId, e.getMessage(), e);
            // MinIO 清理失败不阻塞数据库清理，继续执行
        }
    }

    private void deleteObjectsWithPrefix(MinioClient client, String bucket, String prefix) {
        try {
            // 列出并删除所有匹配前缀的对象
            ListObjectsArgs listArgs = ListObjectsArgs.builder().bucket(bucket).prefix(prefix).build();
            Object result = client.listObjects(listArgs);
            if (result instanceof Iterable) {
                for (Object item : (Iterable<?>) result) {
                    try {
                        if (item instanceof Result) {
                            Result<?> r = (Result<?>) item;
                            Object o = r.get();
                            if (o != null) {
                                // 获取对象名称
                                String objectKey = getObjectName(o);
                                if (objectKey != null) {
                                    client.removeObject(
                                            RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to delete MinIO object: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list/delete MinIO objects with prefix {}: {}", prefix, e.getMessage());
        }
    }

    private String getObjectName(Object obj) {
        try {
            // 使用反射获取 objectName 字段
            var method = obj.getClass().getMethod("objectName");
            return (String) method.invoke(obj);
        } catch (Exception e) {
            // 尝试 toString
            return obj.toString();
        }
    }
}
