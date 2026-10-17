package com.shortvideoscripagent.xhsagentyunying.dto.retention;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 保留策略响应 DTO（P0-9）。
 */
@Data
@Builder
public class RetentionPolicyResponse {

    private String workspaceId;

    /** 内容资产保留天数 */
    private Integer assetRetentionDays;

    /** 使用日志保留天数 */
    private Integer logRetentionDays;

    /** 媒体文件保留天数 */
    private Integer mediaRetentionDays;

    /** 聊天会话保留天数 */
    private Integer chatSessionRetentionDays;

    private OffsetDateTime updatedAt;

    private Long updatedBy;
}
