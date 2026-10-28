package com.shortvideoscripagent.xhsagentyunying.dto.platformaccount;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 平台账号响应。
 * <p>
 * 安全约束：<b>绝不</b>返回凭据内容，只返回 {@code hasCredential} 布尔位表示是否已绑定凭据引用。
 * </p>
 */
@Data
public class PlatformAccountResponse {

    /** 账号主键 */
    private String id;

    private String platformCode;

    /** 平台展示名（来自 app.platform.display_name） */
    private String platformName;

    private String accountLabel;

    private String externalAccountId;

    private String connectorProviderCode;

    /** 连接等级（OFFICIAL_API / AUTHORIZED_CONNECTOR / BROWSER_ASSIST / MANUAL_IMPORT / DISABLED） */
    private String level;

    /** 连接状态（unconnected / connected / expired / reauthorizing / failed） */
    private String status;

    /**
     * 授权到期状态：
     * OK / EXPIRING_SOON（7 天内到期）/ EXPIRING_TODAY（今天到期）/ EXPIRED（已过期）/ UNKNOWN（未设置到期时间）
     */
    private String expiryState;

    private OffsetDateTime authorizedAt;

    private OffsetDateTime expiresAt;

    private OffsetDateTime lastSuccessAt;

    private String lastError;

    /** 是否已绑定凭据引用（只暴露布尔值，不暴露引用内容本身） */
    private boolean hasCredential;

    private OffsetDateTime createdAt;
}
