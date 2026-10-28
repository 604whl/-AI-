package com.shortvideoscripagent.xhsagentyunying.dto.platformaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 创建平台账号连接请求。
 * <p>
 * 注意：请求体**不接受** level（连接等级由服务端按授权矩阵判定）。
 * </p>
 */
@Data
public class PlatformAccountCreateRequest {

    /** 平台代码（大写枚举名，如 XIAOHONGSHU / DOUYIN） */
    @NotBlank(message = "platform_code_required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$", message = "invalid_platform_code")
    private String platformCode;

    /** 用户自定义账号标签 */
    @NotBlank(message = "account_label_required")
    @Size(max = 128, message = "account_label_too_long")
    private String accountLabel;

    /** 接入所用的连接器实现（app.connector_provider.code） */
    @NotBlank(message = "connector_provider_code_required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "invalid_connector_provider_code")
    private String connectorProviderCode;

    /** 平台侧账号 ID（可空） */
    @Size(max = 128, message = "external_account_id_too_long")
    private String externalAccountId;

    /**
     * 凭据**引用**（browser_assist_session.id）。只接受引用，禁止明文 Cookie / Token / 密码。
     */
    @Size(max = 128, message = "credential_ref_too_long")
    private String credentialRef;

    /** 授权到期时间（可空） */
    private OffsetDateTime expiresAt;
}
