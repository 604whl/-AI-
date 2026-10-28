package com.shortvideoscripagent.xhsagentyunying.connector;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PlatformConnectorAuthorization;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PlatformConnectorAuthorizationMapper;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformEntity;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformEntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 连接器授权门禁（运行时唯一门禁）。
 * <p>
 * 数据来源：
 * <ul>
 *   <li>{@code app.platform_connector_authorization} —— 平台 × 连接器授权矩阵</li>
 *   <li>{@code app.platform} —— 平台上线状态与读写开关</li>
 * </ul>
 * 判定顺序（任一不满足即拒绝）：
 * <ol>
 *   <li>平台存在且 {@code rollout_status != 'disabled'}（KUAISHOU / INSTAGRAM / TIKTOK 一律拒绝）</li>
 *   <li>授权记录存在且 {@code authorized = TRUE}</li>
 *   <li>授权未过期（{@code expires_at} 为空或晚于当前时间）</li>
 * </ol>
 * <p>
 * <b>注意</b>：未授权的连接器在 {@link ConnectorRegistry} 加载阶段就不会注册进 Spring 容器，
 * 即"未授权 → 不存在 → 调用即 404/403"，调用方拿不到任何可执行的未授权连接器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorAuthorizationService {

    /** 未授权 */
    public static final int CODE_NOT_AUTHORIZED = 40302;

    /** 未授权错误标识 */
    public static final String ERR_NOT_AUTHORIZED = "CONNECTOR_NOT_AUTHORIZED";

    /** 平台被禁用 */
    public static final String ERR_PLATFORM_DISABLED = "CONNECTOR_PLATFORM_DISABLED";

    private static final String ROLLOUT_DISABLED = "disabled";

    private final PlatformConnectorAuthorizationMapper authorizationMapper;
    private final PlatformEntityMapper platformMapper;

    /**
     * 判断指定平台 + 连接器是否已授权。
     * <p>
     * 要求：平台未被禁用 + 授权记录 {@code authorized=TRUE} + 授权未过期。
     */
    public boolean isAuthorized(String platformCode, String providerCode) {
        if (platformCode == null || platformCode.isBlank()
                || providerCode == null || providerCode.isBlank()) {
            return false;
        }
        if (!isPlatformEnabled(platformCode)) {
            return false;
        }
        PlatformConnectorAuthorization auth =
                authorizationMapper.selectByPlatformAndProvider(platformCode, providerCode);
        if (auth == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(auth.getAuthorized())) {
            return false;
        }
        // 授权过期视为未授权
        if (auth.getExpiresAt() != null && auth.getExpiresAt().isBefore(OffsetDateTime.now())) {
            log.warn("Connector authorization expired: platform={} provider={} expiresAt={}",
                    platformCode, providerCode, auth.getExpiresAt());
            return false;
        }
        return true;
    }

    /**
     * 未授权抛 403。
     */
    public void requireAuthorized(String platformCode, String providerCode) {
        if (!isAuthorized(platformCode, providerCode)) {
            throw new BusinessException(CODE_NOT_AUTHORIZED, ERR_NOT_AUTHORIZED);
        }
    }

    /**
     * 判断平台是否已上线可用。
     * <p>
     * {@code rollout_status = 'disabled'} 的平台（KUAISHOU / INSTAGRAM / TIKTOK）一律拒绝。
     * 平台不存在同样视为不可用。
     */
    public boolean isPlatformEnabled(String platformCode) {
        if (platformCode == null || platformCode.isBlank()) {
            return false;
        }
        PlatformEntity platform = platformMapper.selectById(platformCode);
        if (platform == null) {
            return false;
        }
        return !ROLLOUT_DISABLED.equalsIgnoreCase(platform.getRolloutStatus());
    }

    /**
     * 判断平台是否允许自动写。
     * <p>
     * {@code write_enabled = false} 的平台不允许自动写，只能走 {@code MANUAL_IMPORT}。
     */
    public boolean isPlatformWriteEnabled(String platformCode) {
        if (platformCode == null || platformCode.isBlank()) {
            return false;
        }
        PlatformEntity platform = platformMapper.selectById(platformCode);
        if (platform == null) {
            return false;
        }
        if (ROLLOUT_DISABLED.equalsIgnoreCase(platform.getRolloutStatus())) {
            return false;
        }
        return Boolean.TRUE.equals(platform.getWriteEnabled());
    }

    /**
     * 判断平台是否允许读。
     */
    public boolean isPlatformReadEnabled(String platformCode) {
        if (platformCode == null || platformCode.isBlank()) {
            return false;
        }
        PlatformEntity platform = platformMapper.selectById(platformCode);
        if (platform == null) {
            return false;
        }
        if (ROLLOUT_DISABLED.equalsIgnoreCase(platform.getRolloutStatus())) {
            return false;
        }
        return !Boolean.FALSE.equals(platform.getReadEnabled());
    }

    /**
     * 查询某平台下所有已授权连接器。
     */
    public List<PlatformConnectorAuthorization> listAuthorized(String platformCode) {
        return authorizationMapper.selectAuthorizedByPlatform(platformCode);
    }

    /**
     * 查询全部授权记录。
     */
    public List<PlatformConnectorAuthorization> listAllAuthorizations() {
        return authorizationMapper.selectAllAuthorizations();
    }

    /**
     * 查询授权记录（可能为 null）。
     */
    public PlatformConnectorAuthorization findAuthorization(String platformCode, String providerCode) {
        return authorizationMapper.selectByPlatformAndProvider(platformCode, providerCode);
    }
}
