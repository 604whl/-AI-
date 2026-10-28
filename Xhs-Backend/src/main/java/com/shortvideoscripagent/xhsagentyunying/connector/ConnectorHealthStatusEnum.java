package com.shortvideoscripagent.xhsagentyunying.connector;

/**
 * 连接器健康状态枚举。
 * <p>
 * 与 {@code app.connector_health.status} 的 CHECK 约束保持一致。
 */
public enum ConnectorHealthStatusEnum {

    /** 正常 */
    HEALTHY,

    /** 降级可用（连续失败 3 次） */
    DEGRADED,

    /** 不可用（连续失败 10 次） */
    UNAVAILABLE,

    /** 未授权 */
    NOT_AUTHORIZED
}
