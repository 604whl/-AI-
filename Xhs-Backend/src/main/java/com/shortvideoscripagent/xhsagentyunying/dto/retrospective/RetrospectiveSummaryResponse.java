package com.shortvideoscripagent.xhsagentyunying.dto.retrospective;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S3-7 复盘报告列表项响应。
 */
@Data
@Builder
public class RetrospectiveSummaryResponse {

    /** 报告 ID */
    private UUID id;

    /** 报表维度 */
    private String scopeType;

    /** 维度键 */
    private String scopeKey;

    /** 建议条数 */
    private Integer suggestionCount;

    /** 状态 */
    private String status;

    /** 是否降级 */
    private Boolean degraded;

    /** 生成时间 */
    private OffsetDateTime generatedAt;
}
