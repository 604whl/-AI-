package com.shortvideoscripagent.xhsagentyunying.dto.platformaccount;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 重新授权请求。
 * <p>
 * {@code credentialRef} 只接受引用（browser_assist_session.id），禁止明文凭据。
 * </p>
 */
@Data
public class PlatformAccountReauthorizeRequest {

    @Size(max = 128, message = "credential_ref_too_long")
    private String credentialRef;

    private OffsetDateTime expiresAt;
}
