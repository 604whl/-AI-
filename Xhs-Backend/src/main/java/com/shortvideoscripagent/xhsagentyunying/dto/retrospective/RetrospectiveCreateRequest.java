package com.shortvideoscripagent.xhsagentyunying.dto.retrospective;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * S3-7 创建复盘报告请求。
 * <p>
 * 报表维度 6 种：单篇（ASSET）/ 账号（ACCOUNT）/ 平台（PLATFORM）/ 品类（CATEGORY）/
 * 活动（CAMPAIGN）/ 时间段（PERIOD）。
 * </p>
 */
@Data
public class RetrospectiveCreateRequest {

    /** 报表维度：ASSET / ACCOUNT / PLATFORM / CATEGORY / CAMPAIGN / PERIOD */
    @NotBlank(message = "scope_type_required")
    @Size(max = 16)
    private String scopeType;

    /** 维度键（资产 ID / 账号 ID / 平台码 / 品类码 / 活动 ID / 时间段标识） */
    @Size(max = 255)
    private String scopeKey;

    /** 关联内容资产（可空） */
    @Size(max = 64)
    private String assetId;

    /** 平台账号 ID */
    @Size(max = 64)
    private String accountId;

    /** 平台代码 */
    @Size(max = 32)
    private String platformCode;

    /** 品类编码 */
    @Size(max = 64)
    private String categoryCode;

    /** 活动 ID */
    @Size(max = 64)
    private String campaignId;

    /** 统计周期开始 */
    private OffsetDateTime periodStart;

    /** 统计周期结束 */
    private OffsetDateTime periodEnd;
}
