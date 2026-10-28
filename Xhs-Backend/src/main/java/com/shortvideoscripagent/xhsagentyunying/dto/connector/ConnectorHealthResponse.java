package com.shortvideoscripagent.xhsagentyunying.dto.connector;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 连接器健康详情。
 */
@Data
@Builder
public class ConnectorHealthResponse {

    /** HEALTHY / DEGRADED / UNAVAILABLE / NOT_AUTHORIZED */
    private String status;

    /** 最近成功时间 */
    private OffsetDateTime lastSuccessAt;

    /** 最近检查时间 */
    private OffsetDateTime lastCheckedAt;

    /** 最近错误码 */
    private String lastErrorCode;

    /** 最近错误描述 */
    private String lastErrorMessage;

    /** 连续失败次数 */
    private Integer consecutiveFailures;

    /** 降级目标 provider code */
    private String degradedTo;
}
