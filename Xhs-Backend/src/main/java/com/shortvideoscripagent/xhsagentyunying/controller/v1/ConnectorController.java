package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorAuthorizationService;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorHealthService;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorHealthStatus;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorHealthStatusEnum;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorRegistry;
import com.shortvideoscripagent.xhsagentyunying.connector.PlatformConnector;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ConnectorHealth;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ConnectorProvider;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PlatformConnectorAuthorization;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ConnectorProviderMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.connector.ConnectorDiagnoseResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.connector.ConnectorHealthResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.connector.ConnectorListItem;
import com.shortvideoscripagent.xhsagentyunying.dto.connector.DegradationStatusItem;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformEntity;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformEntityMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.Audited;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * S3-1 连接器基础层 API。
 * <p>
 * 实际路径：{@code server.servlet.context-path=/api} + {@code /v1/connectors}
 * → {@code /api/v1/connectors}。
 * <p>
 * 权限：
 * <ul>
 *   <li>列表 / 健康详情 / 降级状态 —— 全部角色可读</li>
 *   <li>诊断（diagnose）—— 仅 ADMIN / OWNER</li>
 * </ul>
 * 诊断是<b>只读、无副作用</b>的操作，绝不触发真实发布。
 */
@Slf4j
@RestController
@RequestMapping("/v1/connectors")
@RequiredArgsConstructor
@Tag(name = "Connectors", description = "连接器抽象 / 授权门禁 / 健康诊断（阶段 3 S3-1）")
public class ConnectorController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;
    private static final int CODE_NOT_FOUND = 40450;

    private final ConnectorRegistry connectorRegistry;
    private final ConnectorAuthorizationService authorizationService;
    private final ConnectorHealthService healthService;
    private final ConnectorProviderMapper providerMapper;
    private final PlatformEntityMapper platformMapper;
    private final WorkspaceService workspaceService;

    /**
     * 列出全部连接器。
     */
    @GetMapping
    @Operation(summary = "列出全部连接器", description = "全部角色可读；含平台信息、授权状态、上线状态与健康摘要")
    public ApiResponse<List<ConnectorListItem>> list() {
        Long userId = requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        WorkspaceGuards.requireMember(workspaceId);

        // 以授权矩阵为全量来源：既能展示已授权的，也能展示"未授权"的（authorized=false），
        // 让运营能看出哪些组合还没开通。
        Map<String, PlatformConnectorAuthorization> authMap = new LinkedHashMap<>();
        for (PlatformConnectorAuthorization auth : authorizationService.listAllAuthorizations()) {
            authMap.put(auth.getPlatformCode() + "::" + auth.getConnectorProviderCode(), auth);
        }

        List<ConnectorListItem> items = new ArrayList<>();
        for (PlatformConnectorAuthorization auth : authorizationService.listAllAuthorizations()) {
            String platformCode = auth.getPlatformCode();
            String providerCode = auth.getConnectorProviderCode();
            PlatformEntity platform = platformMapper.selectById(platformCode);
            ConnectorProvider provider = providerMapper.selectById(providerCode);
            ConnectorHealth health = healthService.find(providerCode, platformCode, workspaceId);

            items.add(ConnectorListItem.builder()
                    .platformCode(platformCode)
                    .platformName(platform != null ? platform.getDisplayName() : platformCode)
                    .connectorProviderCode(providerCode)
                    .level(resolveLevel(provider))
                    .authorized(Boolean.TRUE.equals(auth.getAuthorized()))
                    .rolloutStatus(platform != null ? platform.getRolloutStatus() : null)
                    .writeEnabled(platform != null && Boolean.TRUE.equals(platform.getWriteEnabled()))
                    .readEnabled(platform != null && !Boolean.FALSE.equals(platform.getReadEnabled()))
                    .health(ConnectorListItem.HealthSummary.builder()
                            .status(health.getStatus())
                            .lastSuccessAt(health.getLastSuccessAt())
                            .lastError(health.getLastErrorMessage())
                            .build())
                    .build());
        }
        return ApiResponse.ok(items);
    }

    /**
     * 查询某连接器的健康详情。
     */
    @GetMapping("/{providerCode}/health")
    @Operation(summary = "查询连接器健康状态", description = "全部角色可读；找不到连接器返回 404 CONNECTOR_NOT_FOUND")
    public ApiResponse<ConnectorHealthResponse> health(@PathVariable String providerCode) {
        requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        WorkspaceGuards.requireMember(workspaceId);

        ConnectorHealth health = resolveHealth(providerCode, workspaceId);
        return ApiResponse.ok(ConnectorHealthResponse.builder()
                .status(health.getStatus())
                .lastSuccessAt(health.getLastSuccessAt())
                .lastCheckedAt(health.getLastCheckedAt())
                .lastErrorCode(health.getLastErrorCode())
                .lastErrorMessage(health.getLastErrorMessage())
                .consecutiveFailures(health.getConsecutiveFailures())
                .degradedTo(health.getDegradedTo())
                .build());
    }

    /**
     * 连接器诊断（只读、无副作用）。
     */
    @PostMapping("/{providerCode}/diagnose")
    @Audited(action = "connector.diagnose", resourceType = "connector", resourceId = "#providerCode")
    @Operation(summary = "诊断连接器", description = "仅 ADMIN/OWNER；只读，绝不触发真实发布；未授权返回 403 CONNECTOR_NOT_AUTHORIZED")
    public ApiResponse<ConnectorDiagnoseResponse> diagnose(@PathVariable String providerCode) {
        Long userId = requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        requireAdmin(workspaceId, userId);

        Optional<PlatformConnector> found = connectorRegistry.find(providerCode);
        if (found.isEmpty()) {
            // 未注册 = 未通过授权门禁，或连接器根本不存在
            ConnectorProvider provider = providerMapper.selectById(providerCode);
            if (provider != null && !authorizationService.isAuthorized(provider.getPlatformCode(), providerCode)) {
                healthService.recordNotAuthorized(providerCode, provider.getPlatformCode(), workspaceId);
                throw new BusinessException(ConnectorAuthorizationService.CODE_NOT_AUTHORIZED,
                        ConnectorAuthorizationService.ERR_NOT_AUTHORIZED);
            }
            throw new BusinessException(CODE_NOT_FOUND, "CONNECTOR_NOT_FOUND");
        }

        PlatformConnector connector = found.get();
        List<ConnectorDiagnoseResponse.CheckResult> checks = new ArrayList<>();

        // 检查 1：授权状态
        boolean authorized = authorizationService.isAuthorized(connector.platformCode(), providerCode);
        checks.add(ConnectorDiagnoseResponse.CheckResult.builder()
                .name("authorization")
                .passed(authorized)
                .detail(authorized ? "授权有效" : "未授权或授权已过期")
                .build());

        // 检查 2：平台上线状态
        boolean platformEnabled = authorizationService.isPlatformEnabled(connector.platformCode());
        checks.add(ConnectorDiagnoseResponse.CheckResult.builder()
                .name("platform_enabled")
                .passed(platformEnabled)
                .detail(platformEnabled ? "平台已上线" : "平台 rollout_status=disabled 或不存在")
                .build());

        // 检查 3：平台读写开关
        boolean writeEnabled = authorizationService.isPlatformWriteEnabled(connector.platformCode());
        boolean readEnabled = authorizationService.isPlatformReadEnabled(connector.platformCode());
        checks.add(ConnectorDiagnoseResponse.CheckResult.builder()
                .name("platform_capability")
                .passed(readEnabled)
                .detail("write=" + writeEnabled + ", read=" + readEnabled)
                .build());

        // 检查 4：连接器健康自检（只读）
        ConnectorHealthStatus status = safeHealthCheck(connector);
        checks.add(ConnectorDiagnoseResponse.CheckResult.builder()
                .name("connector_health")
                .passed(ConnectorHealthStatusEnum.HEALTHY.name().equals(status.status()))
                .detail("status=" + status.status() + ", latencyMs=" + status.latencyMs())
                .build());

        String overall = authorized && platformEnabled
                ? ConnectorHealthStatusEnum.HEALTHY.name()
                : ConnectorHealthStatusEnum.NOT_AUTHORIZED.name();
        String degradedTo = ConnectorHealthStatusEnum.HEALTHY.name().equals(overall)
                ? null
                : healthService.resolveDegradedTo(providerCode);

        return ApiResponse.ok(ConnectorDiagnoseResponse.builder()
                .status(overall)
                .checks(checks)
                .degradedTo(degradedTo)
                .checkedAt(OffsetDateTime.now())
                .build());
    }

    /**
     * 查询平台降级状态。
     */
    @GetMapping("/degradation")
    @Operation(summary = "查询平台降级状态", description = "全部角色可读；返回各平台当前等级、降级目标与原因")
    public ApiResponse<List<DegradationStatusItem>> degradation(
            @RequestParam(required = false) String platform) {
        requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        WorkspaceGuards.requireMember(workspaceId);

        List<PlatformConnectorAuthorization> auths = authorizationService.listAllAuthorizations();
        Map<String, DegradationStatusItem> byPlatform = new LinkedHashMap<>();

        for (PlatformConnectorAuthorization auth : auths) {
            String platformCode = auth.getPlatformCode();
            if (platform != null && !platform.isBlank() && !platform.equalsIgnoreCase(platformCode)) {
                continue;
            }
            if (byPlatform.containsKey(platformCode)) {
                continue;
            }
            PlatformEntity platformEntity = platformMapper.selectById(platformCode);
            ConnectorHealth health = healthService.find(auth.getConnectorProviderCode(), platformCode, workspaceId);
            String currentLevel = platformEntity != null ? platformEntity.getConnectorLevel() : null;
            String degradedTo = health.getDegradedTo();
            byPlatform.put(platformCode, DegradationStatusItem.builder()
                    .platformCode(platformCode)
                    .currentLevel(currentLevel)
                    .degradedTo(degradedTo)
                    .reason(degradedTo == null ? null : health.getLastErrorMessage())
                    .since(health.getUpdatedAt())
                    .build());
        }
        return ApiResponse.ok(new ArrayList<>(byPlatform.values()));
    }

    // ── 私有辅助 ────────────────────────────────────────────────────────────

    private ConnectorHealth resolveHealth(String providerCode, String workspaceId) {
        ConnectorProvider provider = providerMapper.selectById(providerCode);
        if (provider == null) {
            throw new BusinessException(CODE_NOT_FOUND, "CONNECTOR_NOT_FOUND");
        }
        return healthService.find(providerCode, provider.getPlatformCode(), workspaceId);
    }

    private String resolveLevel(ConnectorProvider provider) {
        if (provider == null || provider.getConnectorType() == null) {
            return null;
        }
        return switch (provider.getConnectorType()) {
            case "OFFICIAL_API" -> "OFFICIAL_API";
            case "BROWSER_EXTENSION" -> "BROWSER_ASSIST";
            case "CSV_IMPORT", "MANUAL_FORM" -> "MANUAL_IMPORT";
            case "WEBHOOK_RECEIVER" -> "AUTHORIZED_CONNECTOR";
            default -> "DISABLED";
        };
    }

    private ConnectorHealthStatus safeHealthCheck(PlatformConnector connector) {
        try {
            ConnectorHealthStatus status = connector.healthCheck();
            if (status != null) {
                return status;
            }
        } catch (Exception e) {
            log.warn("Connector healthCheck failed for {}: {}", connector.providerCode(), e.getMessage());
        }
        return new ConnectorHealthStatus(ConnectorHealthStatusEnum.UNAVAILABLE.name(), null,
                OffsetDateTime.now(), null, null, 0, null, null);
    }

    private void requireAdmin(String workspaceId, Long userId) {
        WorkspaceGuards.requireMember(workspaceId);
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null || !role.atLeast(WorkspaceRole.ADMIN)) {
            throw new BusinessException(CODE_FORBIDDEN, "admin_role_required");
        }
    }

    private Long requireUserId() {
        Long userId = RequestContext.getUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}
