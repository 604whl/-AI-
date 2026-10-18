package com.shortvideoscripagent.xhsagentyunying.common.exception;

import lombok.Getter;

/**
 * M0-5: platform connector unavailable exception.
 * <p>
 * Thrown when workspace attempts to write to a platform but the connector authorization check fails.
 * <p>
 * code: 50301
 * message: platform_connector_unavailable
 * <p>
 * Possible reasons:
 * <ul>
 *   <li>DISABLED - platform explicitly disabled</li>
 *   <li>NOT_AUTHORIZED - no valid authorization for platform</li>
 *   <li>EXPIRED - authorization has expired</li>
 * </ul>
 */
@Getter
public class PlatformConnectorUnavailableException extends RuntimeException {

    /** HTTP 503 Service Unavailable */
    public static final int HTTP_STATUS = 503;

    /** Business error code */
    public static final int CODE = 50301;

    /** Error message key */
    public static final String MESSAGE_KEY = "platform_connector_unavailable";

    private final int code;
    private final String platformCode;
    private final String workspaceId;
    private final String reason;

    public PlatformConnectorUnavailableException(String platformCode, String workspaceId, String reason) {
        super(buildMessage(platformCode, workspaceId, reason));
        this.code = CODE;
        this.platformCode = platformCode;
        this.workspaceId = workspaceId;
        this.reason = reason;
    }

    private static String buildMessage(String platformCode, String workspaceId, String reason) {
        return String.format(
                "Platform connector unavailable: platform=%s, workspaceId=%s, reason=%s",
                platformCode,
                workspaceId != null ? workspaceId : "unknown",
                reason
        );
    }

    public static String reasonDisabled() { return "DISABLED"; }
    public static String reasonNotAuthorized() { return "NOT_AUTHORIZED"; }
    public static String reasonExpired() { return "EXPIRED"; }
}