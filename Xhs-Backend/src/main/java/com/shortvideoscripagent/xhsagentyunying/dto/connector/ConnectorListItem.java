package com.shortvideoscripagent.xhsagentyunying.dto.connector;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 连接器列表项。
 */
@Data
@Builder
public class ConnectorListItem {

    /** 平台代码 */
    private String platformCode;

    /** 平台展示名 */
    private String platformName;

    /** 连接器提供方代码 */
    private String connectorProviderCode;

    /** 连接器等级：OFFICIAL_API / AUTHORIZED_CONNECTOR / BROWSER_ASSIST / MANUAL_IMPORT */
    private String level;

    /** 是否已授权 */
    private Boolean authorized;

    /** 平台上线状态：launched / planned / disabled */
    private String rolloutStatus;

    /** 平台是否允许写 */
    private Boolean writeEnabled;

    /** 平台是否允许读 */
    private Boolean readEnabled;

    /** 健康状态摘要 */
    private HealthSummary health;

    /**
     * 健康状态摘要。
     */
    @Data
    @Builder
    public static class HealthSummary {
        /** HEALTHY / DEGRADED / UNAVAILABLE / NOT_AUTHORIZED */
        private String status;
        /** 最近成功时间 */
        private OffsetDateTime lastSuccessAt;
        /** 最近错误描述 */
        private String lastError;
    }
}
