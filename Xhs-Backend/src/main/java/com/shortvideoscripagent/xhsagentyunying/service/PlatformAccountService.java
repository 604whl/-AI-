package com.shortvideoscripagent.xhsagentyunying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.connector.ConnectorAuthorizationService;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ConnectorProvider;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PlatformAccount;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ConnectorProviderMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PlatformAccountMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.platformaccount.PlatformAccountCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.platformaccount.PlatformAccountReauthorizeRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.platformaccount.PlatformAccountResponse;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformConnectorLevel;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformEntity;
import com.shortvideoscripagent.xhsagentyunying.freeze.PlatformEntityMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * S3-2 平台账号连接服务。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>工作区维度的平台账号绑定（创建 / 重新授权 / 解绑 / 查询）</li>
 *   <li>连接状态机（unconnected → connected → expired → reauthorizing → connected，失败落 failed）</li>
 *   <li>授权门禁：禁用平台拒绝、授权矩阵未授权组合拒绝且不落库</li>
 *   <li>最小权限：{@code write_enabled=false} 的平台只允许 MANUAL_IMPORT 账号</li>
 *   <li>凭据不入库明文：{@code credential_ref} 只接受引用</li>
 *   <li>授权到期三态提示（EXPIRING_SOON / EXPIRING_TODAY / EXPIRED）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAccountService {

    // ── 错误码 ──────────────────────────────────────────────────────────
    /** 403 授权门禁失败 */
    private static final int CODE_CONNECTOR_NOT_AUTHORIZED = 40302;
    /** 403 平台不允许写 */
    private static final int CODE_WRITE_NOT_ALLOWED = 40303;
    /** 400 参数非法（凭据必须是指引用） */
    private static final int CODE_BAD_REQUEST = 40060;
    /** 404 资源不存在 */
    private static final int CODE_NOT_FOUND = 40450;
    /** 404 连接器实现不存在 */
    private static final int CODE_CONNECTOR_PROVIDER_NOT_FOUND = 40452;
    /** 409 状态机非法转换 */
    private static final int CODE_INVALID_TRANSITION = 40950;
    /** 409 账号重复 */
    private static final int CODE_DUPLICATE = 40951;

    /** 距到期多少天以内算"即将到期" */
    private static final int EXPIRING_SOON_DAYS = 7;

    /** 状态机允许的转换 */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = buildTransitions();

    private final PlatformAccountMapper platformAccountMapper;
    private final PlatformEntityMapper platformEntityMapper;
    private final ConnectorProviderMapper connectorProviderMapper;
    private final ConnectorAuthorizationService connectorAuthorizationService;
    private final AuditWriter auditWriter;

    // ── 对外接口 ────────────────────────────────────────────────────────

    /**
     * 创建平台账号连接（ADMIN/OWNER 由 Controller 保证）。
     * <p>
     * 顺序：平台合法性 → 授权矩阵 → 最小权限 → 凭据引用校验 → 唯一性 → 落库 → 审计。
     * 任何一步失败都<b>不落库</b>。
     * </p>
     */
    @Transactional
    public PlatformAccountResponse create(String workspaceId, Long userId, PlatformAccountCreateRequest request) {
        String platformCode = normalizePlatformCode(request.getPlatformCode());
        String providerCode = normalizeProviderCode(request.getConnectorProviderCode());

        // 1) 平台必须存在于 app.platform 且 rollout_status != 'disabled'
        PlatformEntity platform = requirePlatformEnabled(platformCode);

        // 2) 授权矩阵门禁：未授权组合一律拒绝（不落库）
        connectorAuthorizationService.requireAuthorized(platformCode, providerCode);

        // 3) 连接器实现必须属于该平台，并解析出连接等级
        ProviderInfo provider = requireProvider(platformCode, providerCode);

        // 4) 最小权限：平台不允许写 → 只允许 MANUAL_IMPORT
        if (!Boolean.TRUE.equals(platform.getWriteEnabled())
                && provider.level() != PlatformConnectorLevel.MANUAL_IMPORT) {
            throw new BusinessException(CODE_WRITE_NOT_ALLOWED, "WRITE_NOT_ALLOWED");
        }

        // 5) 凭据只接受引用，不接受明文
        String credentialRef = normalizeCredentialRef(request.getCredentialRef());

        // 6) 唯一性：同 workspace + 平台 + 标签
        String accountLabel = request.getAccountLabel().trim();
        PlatformAccount existing = platformAccountMapper.selectByWorkspacePlatformLabel(
                workspaceId, platformCode, accountLabel);
        if (existing != null) {
            throw new BusinessException(CODE_DUPLICATE, "platform_account_duplicate");
        }

        OffsetDateTime now = OffsetDateTime.now();
        PlatformAccount account = new PlatformAccount();
        account.setId(UUID.randomUUID());
        account.setWorkspaceId(workspaceId);
        account.setPlatformCode(platformCode);
        account.setAccountLabel(accountLabel);
        account.setExternalAccountId(blankToNull(request.getExternalAccountId()));
        account.setConnectorProviderCode(providerCode);
        account.setLevel(provider.level().name());
        account.setStatus(PlatformAccount.STATUS_UNCONNECTED);
        account.setCredentialRef(credentialRef);
        account.setCreatedBy(userId);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);

        // 7) 状态机：unconnected → connected（仅当已绑定凭据引用）
        if (credentialRef != null) {
            assertTransition(PlatformAccount.STATUS_UNCONNECTED, PlatformAccount.STATUS_CONNECTED);
            account.setStatus(PlatformAccount.STATUS_CONNECTED);
            account.setAuthorizedAt(now);
        }
        if (request.getExpiresAt() != null) {
            account.setExpiresAt(request.getExpiresAt());
        }

        platformAccountMapper.insert(account);

        auditWriter.record("platform_account.connect", "platform_account",
                String.valueOf(account.getId()), auditMeta(account, provider));

        return toResponse(account, platform.getDisplayName());
    }

    /**
     * 重新授权（ADMIN/OWNER）。
     * <p>
     * 状态机：当前状态 → reauthorizing → connected，并刷新 authorized_at / expires_at。
     * </p>
     */
    @Transactional
    public PlatformAccountResponse reauthorize(String workspaceId, Long userId, UUID id,
                                               PlatformAccountReauthorizeRequest request) {
        PlatformAccount account = requireOwned(workspaceId, id);

        // 授权门禁在重新授权时同样生效（授权可能被撤销 / 过期）
        String platformCode = account.getPlatformCode();
        String providerCode = account.getConnectorProviderCode();
        connectorAuthorizationService.requireAuthorized(platformCode, providerCode);
        ProviderInfo provider = requireProvider(platformCode, providerCode);

        String credentialRef = normalizeCredentialRef(
                request != null && request.getCredentialRef() != null
                        ? request.getCredentialRef()
                        : account.getCredentialRef());

        String from = account.getStatus() == null ? PlatformAccount.STATUS_UNCONNECTED : account.getStatus();
        assertTransition(from, PlatformAccount.STATUS_REAUTHORIZING);
        assertTransition(PlatformAccount.STATUS_REAUTHORIZING, PlatformAccount.STATUS_CONNECTED);

        OffsetDateTime now = OffsetDateTime.now();
        account.setStatus(PlatformAccount.STATUS_CONNECTED);
        account.setCredentialRef(credentialRef);
        account.setLevel(provider.level().name());
        account.setAuthorizedAt(now);
        if (request != null && request.getExpiresAt() != null) {
            account.setExpiresAt(request.getExpiresAt());
        }
        account.setLastError(null);
        account.setUpdatedAt(now);

        // 显式 set：MyBatis-Plus 的 updateById 默认跳过 null 字段，
        // 而"清除 last_error / 覆盖 expires_at"恰恰需要把字段写成 NULL。
        platformAccountMapper.update(null, new LambdaUpdateWrapper<PlatformAccount>()
                .eq(PlatformAccount::getId, account.getId())
                .eq(PlatformAccount::getWorkspaceId, workspaceId)
                .set(PlatformAccount::getStatus, PlatformAccount.STATUS_CONNECTED)
                .set(PlatformAccount::getCredentialRef, credentialRef)
                .set(PlatformAccount::getLevel, provider.level().name())
                .set(PlatformAccount::getAuthorizedAt, now)
                .set(PlatformAccount::getExpiresAt, account.getExpiresAt())
                .set(PlatformAccount::getLastError, null)
                .set(PlatformAccount::getUpdatedAt, now));

        auditWriter.record("platform_account.reauthorize", "platform_account",
                String.valueOf(account.getId()), auditMeta(account, provider));

        return toResponse(account, displayName(platformCode));
    }

    /**
     * 解绑（ADMIN/OWNER）：清空凭据引用，状态回到 unconnected。
     */
    @Transactional
    public PlatformAccountResponse disconnect(String workspaceId, Long userId, UUID id) {
        PlatformAccount account = requireOwned(workspaceId, id);

        String from = account.getStatus() == null ? PlatformAccount.STATUS_UNCONNECTED : account.getStatus();
        assertTransition(from, PlatformAccount.STATUS_UNCONNECTED);

        OffsetDateTime now = OffsetDateTime.now();
        account.setStatus(PlatformAccount.STATUS_UNCONNECTED);
        account.setCredentialRef(null);
        account.setAuthorizedAt(null);
        account.setExpiresAt(null);
        account.setUpdatedAt(now);

        // 显式 set：解绑必须真正把 credential_ref / authorized_at / expires_at 写成 NULL，
        // MyBatis-Plus 的 updateById 默认跳过 null 字段做不到这一点。
        platformAccountMapper.update(null, new LambdaUpdateWrapper<PlatformAccount>()
                .eq(PlatformAccount::getId, account.getId())
                .eq(PlatformAccount::getWorkspaceId, workspaceId)
                .set(PlatformAccount::getStatus, PlatformAccount.STATUS_UNCONNECTED)
                .set(PlatformAccount::getCredentialRef, null)
                .set(PlatformAccount::getAuthorizedAt, null)
                .set(PlatformAccount::getExpiresAt, null)
                .set(PlatformAccount::getUpdatedAt, now));

        auditWriter.record("platform_account.disconnect", "platform_account",
                String.valueOf(account.getId()), Map.of("platformCode", String.valueOf(account.getPlatformCode())));

        return toResponse(account, displayName(account.getPlatformCode()));
    }

    /**
     * 列出当前工作区的账号（可按平台 / 状态过滤）。
     */
    public List<PlatformAccountResponse> list(String workspaceId, String platformCode, String status) {
        LambdaQueryWrapper<PlatformAccount> wrapper = new LambdaQueryWrapper<PlatformAccount>()
                .eq(PlatformAccount::getWorkspaceId, workspaceId)
                .orderByDesc(PlatformAccount::getCreatedAt);
        if (platformCode != null && !platformCode.isBlank()) {
            wrapper.eq(PlatformAccount::getPlatformCode, normalizePlatformCode(platformCode));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(PlatformAccount::getStatus, status.trim().toLowerCase(Locale.ROOT));
        }
        List<PlatformAccount> accounts = platformAccountMapper.selectList(wrapper);
        Map<String, String> names = platformDisplayNames();
        List<PlatformAccountResponse> result = new ArrayList<>(accounts.size());
        for (PlatformAccount account : accounts) {
            result.add(toResponse(account, names.get(account.getPlatformCode())));
        }
        return result;
    }

    /**
     * 查询单个账号（工作区隔离）。
     */
    public PlatformAccountResponse get(String workspaceId, UUID id) {
        PlatformAccount account = requireOwned(workspaceId, id);
        return toResponse(account, displayName(account.getPlatformCode()));
    }

    /**
     * 连接器调用失败时把账号标记为 failed，并保留失败原因（{@code last_error}）。
     * <p>
     * 供 S3-4 发布回写 / S3-5 指标回流等链路调用；状态机非法转换一律拒绝。
     * </p>
     */
    @Transactional
    public void markFailed(String workspaceId, UUID id, String errorMessage) {
        PlatformAccount account = requireOwned(workspaceId, id);
        String from = account.getStatus() == null ? PlatformAccount.STATUS_UNCONNECTED : account.getStatus();
        assertTransition(from, PlatformAccount.STATUS_FAILED);

        OffsetDateTime now = OffsetDateTime.now();
        platformAccountMapper.update(null, new LambdaUpdateWrapper<PlatformAccount>()
                .eq(PlatformAccount::getId, account.getId())
                .eq(PlatformAccount::getWorkspaceId, workspaceId)
                .set(PlatformAccount::getStatus, PlatformAccount.STATUS_FAILED)
                .set(PlatformAccount::getLastError, truncate(errorMessage))
                .set(PlatformAccount::getUpdatedAt, now));
    }

    /**
     * 连接器调用成功时刷新 {@code last_success_at}。
     */
    @Transactional
    public void markSuccess(String workspaceId, UUID id) {
        PlatformAccount account = requireOwned(workspaceId, id);
        OffsetDateTime now = OffsetDateTime.now();
        platformAccountMapper.update(null, new LambdaUpdateWrapper<PlatformAccount>()
                .eq(PlatformAccount::getId, account.getId())
                .eq(PlatformAccount::getWorkspaceId, workspaceId)
                .set(PlatformAccount::getLastSuccessAt, now)
                .set(PlatformAccount::getUpdatedAt, now));
    }

    /**
     * 授权到期扫描：把 {@code connected} 且已过期的账号落为 {@code expired}。
     * <p>
     * 只处理 {@code connected → expired} 这一条合法转换，不影响其他状态。
     * </p>
     */
    @Transactional
    public int expireOverdueAccounts(String workspaceId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<PlatformAccount> overdue = platformAccountMapper.selectList(
                new LambdaQueryWrapper<PlatformAccount>()
                        .eq(PlatformAccount::getWorkspaceId, workspaceId)
                        .eq(PlatformAccount::getStatus, PlatformAccount.STATUS_CONNECTED)
                        .isNotNull(PlatformAccount::getExpiresAt)
                        .le(PlatformAccount::getExpiresAt, now));
        for (PlatformAccount account : overdue) {
            platformAccountMapper.update(null, new LambdaUpdateWrapper<PlatformAccount>()
                    .eq(PlatformAccount::getId, account.getId())
                    .eq(PlatformAccount::getWorkspaceId, workspaceId)
                    .eq(PlatformAccount::getStatus, PlatformAccount.STATUS_CONNECTED)
                    .set(PlatformAccount::getStatus, PlatformAccount.STATUS_EXPIRED)
                    .set(PlatformAccount::getUpdatedAt, now));
        }
        return overdue.size();
    }

    // ── 校验与工具 ──────────────────────────────────────────────────────

    /**
     * 所有权校验：不属于当前工作区的一律按"未找到"处理，不泄露存在性。
     */
    private PlatformAccount requireOwned(String workspaceId, UUID id) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_NOT_FOUND, "platform_account_not_found");
        }
        PlatformAccount account = platformAccountMapper.selectByIdAndWorkspace(id, workspaceId);
        if (account == null) {
            throw new BusinessException(CODE_NOT_FOUND, "platform_account_not_found");
        }
        return account;
    }

    /**
     * 平台必须存在且 rollout_status != 'disabled'（禁用平台一律拒绝）。
     */
    private PlatformEntity requirePlatformEnabled(String platformCode) {
        PlatformEntity platform = platformEntityMapper.selectById(platformCode);
        if (platform == null) {
            throw new BusinessException(CODE_CONNECTOR_NOT_AUTHORIZED, "CONNECTOR_NOT_AUTHORIZED");
        }
        if ("disabled".equalsIgnoreCase(String.valueOf(platform.getRolloutStatus()))) {
            throw new BusinessException(CODE_CONNECTOR_NOT_AUTHORIZED, "CONNECTOR_NOT_AUTHORIZED");
        }
        if (!connectorAuthorizationService.isPlatformEnabled(platformCode)) {
            throw new BusinessException(CODE_CONNECTOR_NOT_AUTHORIZED, "CONNECTOR_NOT_AUTHORIZED");
        }
        return platform;
    }

    /**
     * 解析连接器实现：必须属于该平台，并给出连接等级。
     */
    private ProviderInfo requireProvider(String platformCode, String providerCode) {
        ConnectorProvider provider = connectorProviderMapper.selectById(providerCode);
        if (provider == null) {
            throw new BusinessException(CODE_CONNECTOR_PROVIDER_NOT_FOUND, "connector_provider_not_found");
        }
        if (!platformCode.equalsIgnoreCase(String.valueOf(provider.getPlatformCode()))) {
            // 连接器实现不属于该平台 → 未授权
            throw new BusinessException(CODE_CONNECTOR_NOT_AUTHORIZED, "CONNECTOR_NOT_AUTHORIZED");
        }
        PlatformConnectorLevel level = mapLevel(provider.getConnectorType());
        if (level == PlatformConnectorLevel.DISABLED) {
            // 连接器类型未知或被显式禁用 → 未授权
            throw new BusinessException(CODE_CONNECTOR_NOT_AUTHORIZED, "CONNECTOR_NOT_AUTHORIZED");
        }
        return new ProviderInfo(provider.getCode(), level);
    }

    /**
     * 连接器类型 → 连接等级（不新增等级，复用 freeze/PlatformConnectorLevel）。
     */
    private PlatformConnectorLevel mapLevel(String connectorType) {
        String type = connectorType == null ? "" : connectorType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "OFFICIAL_API" -> PlatformConnectorLevel.OFFICIAL_API;
            case "BROWSER_EXTENSION" -> PlatformConnectorLevel.BROWSER_ASSIST;
            case "CSV_IMPORT", "MANUAL_FORM" -> PlatformConnectorLevel.MANUAL_IMPORT;
            case "WEBHOOK_RECEIVER" -> PlatformConnectorLevel.AUTHORIZED_CONNECTOR;
            default -> PlatformConnectorLevel.DISABLED;
        };
    }

    /**
     * 凭据只接受引用：长度受限、不得包含等号 / 分号 / Cookie / Token 等明文特征。
     */
    private String normalizeCredentialRef(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > 128) {
            throw new BusinessException(CODE_BAD_REQUEST, "credential_must_be_reference_not_value");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("=") || lower.contains(";") || lower.contains("cookie") || lower.contains("token")
                || lower.contains("password") || lower.contains("passwd") || lower.contains("secret")
                || lower.contains("authorization") || lower.contains("bearer")) {
            throw new BusinessException(CODE_BAD_REQUEST, "credential_must_be_reference_not_value");
        }
        for (char c : value.toCharArray()) {
            boolean allowed = Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':' || c == '.';
            if (!allowed) {
                throw new BusinessException(CODE_BAD_REQUEST, "credential_must_be_reference_not_value");
            }
        }
        return value;
    }

    /**
     * 状态机：非法转换直接拒绝。
     */
    private void assertTransition(String from, String to) {
        Set<String> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException(CODE_INVALID_TRANSITION, "invalid_account_status_transition");
        }
    }

    private String normalizePlatformCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_platform_code");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeProviderCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_connector_provider_code");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    /**
     * 失败原因截断（避免超长文本把 last_error 撑爆）。
     */
    private String truncate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.length() > 2000 ? raw.substring(0, 2000) : raw;
    }

    private String displayName(String platformCode) {
        if (platformCode == null) {
            return null;
        }
        PlatformEntity platform = platformEntityMapper.selectById(platformCode);
        return platform == null ? null : platform.getDisplayName();
    }

    private Map<String, String> platformDisplayNames() {
        Map<String, String> names = new HashMap<>();
        for (PlatformEntity platform : platformEntityMapper.selectList(null)) {
            names.put(platform.getCode(), platform.getDisplayName());
        }
        return names;
    }

    /**
     * 授权到期状态（四态互不相同，供前端显示不同提示）。
     * <p>
     * 以"自然日"为口径：距到期 &gt; 7 天 → {@code OK}；
     * 距到期 1~7 天 → {@code EXPIRING_SOON}（未到期预警）；
     * 恰好到期当天 → {@code EXPIRING_TODAY}（到期提醒）；
     * 到期日期已过 → {@code EXPIRED}（需重新授权）。
     * 未设置到期时间 → {@code UNKNOWN}。
     * </p>
     */
    private String computeExpiryState(OffsetDateTime expiresAt) {
        if (expiresAt == null) {
            return "UNKNOWN";
        }
        LocalDate today = OffsetDateTime.now().toLocalDate();
        LocalDate expiryDate = expiresAt.toLocalDate();
        if (expiryDate.isBefore(today)) {
            return "EXPIRED";
        }
        if (expiryDate.equals(today)) {
            // 到期当天：时间点还没到 → 到期提醒；已过 → 已过期
            return expiresAt.isBefore(OffsetDateTime.now()) ? "EXPIRED" : "EXPIRING_TODAY";
        }
        if (!expiryDate.isAfter(today.plusDays(EXPIRING_SOON_DAYS))) {
            return "EXPIRING_SOON";
        }
        return "OK";
    }

    /**
     * 实体 → 响应。绝不暴露凭据内容，只暴露 hasCredential 布尔位。
     */
    private PlatformAccountResponse toResponse(PlatformAccount account, String platformName) {
        PlatformAccountResponse dto = new PlatformAccountResponse();
        dto.setId(account.getId() == null ? null : account.getId().toString());
        dto.setPlatformCode(account.getPlatformCode());
        dto.setPlatformName(platformName);
        dto.setAccountLabel(account.getAccountLabel());
        dto.setExternalAccountId(account.getExternalAccountId());
        dto.setConnectorProviderCode(account.getConnectorProviderCode());
        dto.setLevel(account.getLevel());
        dto.setStatus(derivedStatus(account));
        dto.setExpiryState(computeExpiryState(account.getExpiresAt()));
        dto.setAuthorizedAt(account.getAuthorizedAt());
        dto.setExpiresAt(account.getExpiresAt());
        dto.setLastSuccessAt(account.getLastSuccessAt());
        dto.setLastError(account.getLastError());
        dto.setHasCredential(account.getCredentialRef() != null && !account.getCredentialRef().isBlank());
        dto.setCreatedAt(account.getCreatedAt());
        return dto;
    }

    /**
     * 派生状态：存储状态为 connected 但授权已过期时，对外展示 expired。
     */
    private String derivedStatus(PlatformAccount account) {
        String status = account.getStatus();
        if (PlatformAccount.STATUS_CONNECTED.equals(status)
                && "EXPIRED".equals(computeExpiryState(account.getExpiresAt()))) {
            return PlatformAccount.STATUS_EXPIRED;
        }
        return status;
    }

    /**
     * 审计元数据：<b>不包含</b>任何凭据内容。
     */
    private Map<String, Object> auditMeta(PlatformAccount account, ProviderInfo provider) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("platformCode", account.getPlatformCode());
        meta.put("accountLabel", account.getAccountLabel());
        meta.put("connectorProviderCode", account.getConnectorProviderCode());
        meta.put("level", provider.level().name());
        meta.put("status", account.getStatus());
        meta.put("hasCredential", account.getCredentialRef() != null && !account.getCredentialRef().isBlank());
        return meta;
    }

    private static Map<String, Set<String>> buildTransitions() {
        Map<String, Set<String>> transitions = new HashMap<>();
        transitions.put(PlatformAccount.STATUS_UNCONNECTED,
                Set.of(PlatformAccount.STATUS_CONNECTED, PlatformAccount.STATUS_REAUTHORIZING,
                        PlatformAccount.STATUS_FAILED));
        transitions.put(PlatformAccount.STATUS_CONNECTED,
                Set.of(PlatformAccount.STATUS_EXPIRED, PlatformAccount.STATUS_REAUTHORIZING,
                        PlatformAccount.STATUS_FAILED, PlatformAccount.STATUS_UNCONNECTED));
        transitions.put(PlatformAccount.STATUS_EXPIRED,
                Set.of(PlatformAccount.STATUS_REAUTHORIZING, PlatformAccount.STATUS_UNCONNECTED));
        transitions.put(PlatformAccount.STATUS_REAUTHORIZING,
                Set.of(PlatformAccount.STATUS_CONNECTED, PlatformAccount.STATUS_FAILED,
                        PlatformAccount.STATUS_UNCONNECTED));
        transitions.put(PlatformAccount.STATUS_FAILED,
                Set.of(PlatformAccount.STATUS_REAUTHORIZING, PlatformAccount.STATUS_UNCONNECTED));
        return transitions;
    }

    /** 连接器实现 + 等级 */
    private record ProviderInfo(String code, PlatformConnectorLevel level) {
    }
}
