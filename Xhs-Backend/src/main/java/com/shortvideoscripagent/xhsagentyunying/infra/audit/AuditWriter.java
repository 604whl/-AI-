package com.shortvideoscripagent.xhsagentyunying.infra.audit;

import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AuditEvent;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AuditEventMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.metrics.BusinessMetrics;
import com.shortvideoscripagent.xhsagentyunying.service.admin.PlatformAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 审计写入服务。
 * <p>
 * 异步落库（{@code @Async}），不影响主请求延迟。
 * 失败时仅记日志，不向上抛出（审计是 best-effort）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditWriter {

    private final AuditEventMapper auditEventMapper;
    private final BusinessMetrics metrics;
    private final PlatformAdminService platformAdminService;

    /**
     * 异步记录一次操作。
     */
    @Async("auditExecutor")
    public void record(String action,
                       String resourceType,
                       String resourceId,
                       Map<String, Object> metadata) {
        write(action, resourceType, resourceId, metadata, "success", null, null);
    }

    @Async("auditExecutor")
    public void recordFailure(String action,
                              String resourceType,
                              String resourceId,
                              Integer errorCode,
                              String errorMessage,
                              Map<String, Object> metadata) {
        write(action, resourceType, resourceId, metadata, "failure", errorCode, errorMessage);
    }

    private void write(String action,
                       String resourceType,
                       String resourceId,
                       Map<String, Object> metadata,
                       String result,
                       Integer errorCode,
                       String errorMessage) {
        try {
            AuditEvent event = new AuditEvent();
            // UUID 主键不会被 IdType.ASSIGN_UUID 自动赋值，必须手动赋值
            event.setId(UUID.randomUUID());
            event.setWorkspaceId(WorkspaceContext.getWorkspaceId());
            Long uid = RequestContext.getUserId();
            event.setActorUserId(uid == null ? null : String.valueOf(uid));

            // 平台管理员的操作记录为 PLATFORM 类型
            String finalResourceType = resourceType;
            if (uid != null && platformAdminService.isPlatformAdmin(uid)) {
                finalResourceType = "PLATFORM";
            }
            event.setAction(action);
            event.setResourceType(finalResourceType);
            event.setResourceId(resourceId);
            event.setMetadata(metadata == null ? new HashMap<>() : metadata);
            event.setIp(RequestContext.getRequestIp());
            event.setUserAgent(RequestContext.getUserAgent());
            event.setResult(result);
            event.setErrorCode(errorCode);
            event.setErrorMessage(errorMessage);
            event.setCreatedAt(OffsetDateTime.now());

            auditEventMapper.insert(event);
            metrics.auditWritten(action);
        } catch (Exception ex) {
            // 审计是 best-effort，不向上抛；但必须留下可检索的 ERROR 记录，
            // 否则「审计整体失效」这类问题会被静默吞掉、无从发现。
            log.error("Failed to write audit event action={} resourceType={} resourceId={}",
                    action, resourceType, resourceId, ex);
        }
    }
}
