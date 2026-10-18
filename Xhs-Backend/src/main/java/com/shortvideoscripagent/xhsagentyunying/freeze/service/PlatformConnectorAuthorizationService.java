package com.shortvideoscripagent.xhsagentyunying.freeze.service;

import com.shortvideoscripagent.xhsagentyunying.common.exception.PlatformConnectorUnavailableException;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformConnectorLevel;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformCode;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformEntity;
import com.shortvideoscripagent.xhsagentyunying.freeze.domain.entity.PlatformConnectorAuthorization;
import com.shortvideoscripagent.xhsagentyunying.freeze.domain.mapper.PlatformConnectorAuthorizationMapper;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformEntityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * M0-5: platform connector authorization runtime guard service.
 * <p>
 * All write operations to platform must call assertCanWrite(PlatformCode, UUID) first.
 * <p>
 * Check logic:
 * <ol>
 *   <li>Query platform table, get connector level</li>
 *   <li>If level not in {OFFICIAL_API, AUTHORIZED_CONNECTOR, BROWSER_ASSIST} -> deny</li>
 *   <li>Query platform_connector_authorization table, find valid auth (authorized=TRUE and not expired)</li>
 *   <li>No valid auth -> deny (NOT_AUTHORIZED or EXPIRED)</li>
 * </ol>
 * <p>
 * Note: Stage 2 has no publish-to-platform feature yet, this service is reserved for:
 * <ul>
 *   <li>Comment write-back (stage 2 plan)</li>
 *   <li>Any new platform write API</li>
 * </ul>
 */
@Service
public class PlatformConnectorAuthorizationService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformConnectorAuthorizationService.class);

    private final PlatformConnectorAuthorizationMapper authMapper;
    private final PlatformEntityMapper platformMapper;

    public PlatformConnectorAuthorizationService(
            PlatformConnectorAuthorizationMapper authMapper,
            PlatformEntityMapper platformMapper) {
        this.authMapper = authMapper;
        this.platformMapper = platformMapper;
    }

    /**
     * Assert that workspace can write to specified platform.
     * <p>
     * Throws PlatformConnectorUnavailableException if check fails.
     *
     * @param platform    target platform
     * @param workspaceId workspace ID
     * @throws PlatformConnectorUnavailableException when platform connector is unavailable
     */
    public void assertCanWrite(PlatformCode platform, UUID workspaceId) {
        if (platform == null) {
            LOG.warn("M0-5: platform is null, deny write for workspace={}", workspaceId);
            throw new PlatformConnectorUnavailableException(
                    "UNKNOWN",
                    workspaceId != null ? workspaceId.toString() : null,
                    PlatformConnectorUnavailableException.reasonNotAuthorized()
            );
        }

        String platformCode = platform.name();
        String wsIdStr = workspaceId != null ? workspaceId.toString() : null;

        // Step 1: query platform connector level
        PlatformEntity platformEntity = platformMapper.selectById(platformCode);
        if (platformEntity == null) {
            LOG.warn("M0-5: platform '{}' not found in freeze list, deny write workspace={}",
                    platformCode, wsIdStr);
            throw new PlatformConnectorUnavailableException(
                    platformCode, wsIdStr, PlatformConnectorUnavailableException.reasonNotAuthorized());
        }

        String levelStr = platformEntity.getConnectorLevel();

        // Step 2: check if connector level allows write
        PlatformConnectorLevel level;
        try {
            level = PlatformConnectorLevel.valueOf(levelStr);
        } catch (IllegalArgumentException e) {
            LOG.error("M0-5: unknown connector level '{}' for platform={}, deny write workspace={}",
                    levelStr, platformCode, wsIdStr);
            throw new PlatformConnectorUnavailableException(
                    platformCode, wsIdStr, PlatformConnectorUnavailableException.reasonNotAuthorized());
        }

        if (!level.canWrite()) {
            String reason = PlatformConnectorLevel.DISABLED.name().equals(levelStr)
                    ? PlatformConnectorUnavailableException.reasonDisabled()
                    : PlatformConnectorUnavailableException.reasonNotAuthorized();

            LOG.warn("M0-5: platform '{}' level={} cannot write, workspace={}, reason={}",
                    platformCode, levelStr, wsIdStr, reason);
            throw new PlatformConnectorUnavailableException(platformCode, wsIdStr, reason);
        }

        // Step 3: query valid authorization record
        Optional<PlatformConnectorAuthorization> validAuth = findAnyValidAuthorization(platformCode);

        if (validAuth.isEmpty()) {
            LOG.warn("M0-5: platform '{}' has no valid authorization, workspace={}, reason=NOT_AUTHORIZED",
                    platformCode, wsIdStr);
            throw new PlatformConnectorUnavailableException(
                    platformCode, wsIdStr, PlatformConnectorUnavailableException.reasonNotAuthorized());
        }

        // Step 4: double-check expiry (defensive)
        PlatformConnectorAuthorization auth = validAuth.get();
        if (auth.getExpiresAt() != null && auth.getExpiresAt().isBefore(OffsetDateTime.now())) {
            LOG.warn("M0-5: platform '{}' authorization expired at {}, workspace={}",
                    platformCode, auth.getExpiresAt(), wsIdStr);
            throw new PlatformConnectorUnavailableException(
                    platformCode, wsIdStr, PlatformConnectorUnavailableException.reasonExpired());
        }

        LOG.debug("M0-5: write allowed for platform={}, workspace={}, connector={}",
                platformCode, wsIdStr, auth.getConnectorProviderCode());
    }

    /**
     * Query any valid authorization for the platform.
     * Finds the first connector provider with authorized=TRUE and not expired.
     */
    private Optional<PlatformConnectorAuthorization> findAnyValidAuthorization(String platformCode) {
        var q = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PlatformConnectorAuthorization>();
        q.eq("platform_code", platformCode)
         .eq("authorized", true)
         .and(w -> w.isNull("expires_at")
                     .or()
                     .gt("expires_at", OffsetDateTime.now()));
        q.last("LIMIT 1");

        PlatformConnectorAuthorization auth = authMapper.selectOne(q);
        return Optional.ofNullable(auth);
    }
}