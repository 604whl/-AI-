package com.shortvideoscripagent.xhsagentyunying.connector;

import java.time.OffsetDateTime;

/**
 * 连接器健康状态。
 *
 * @param status             状态，取值见 {@link ConnectorHealthStatusEnum}
 * @param lastSuccessAt      最近一次成功时间
 * @param lastCheckedAt      最近一次检查时间
 * @param lastErrorCode      最近一次错误码，取值见 {@link ConnectorErrorCode}
 * @param lastErrorMessage   最近一次错误描述
 * @param consecutiveFailures 连续失败次数
 * @param degradedTo         降级目标 provider code
 * @param latencyMs          最近一次检查耗时（毫秒）
 */
public record ConnectorHealthStatus(String status, OffsetDateTime lastSuccessAt, OffsetDateTime lastCheckedAt,
                                    String lastErrorCode, String lastErrorMessage,
                                    Integer consecutiveFailures, String degradedTo, Long latencyMs) {
}
