package com.shortvideoscripagent.xhsagentyunying.dto.competitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * S3-10 新增竞品笔记响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorNoteCreateResponse {

    private String id;

    /** 采集时间 */
    private OffsetDateTime capturedAt;

    /** 采集方式 */
    private String sourceType;

    /** 竞品账号 ID */
    private String competitorAccountId;
}
