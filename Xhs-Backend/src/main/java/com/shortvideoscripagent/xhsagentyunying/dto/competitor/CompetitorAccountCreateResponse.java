package com.shortvideoscripagent.xhsagentyunying.dto.competitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * S3-10 创建竞品账号响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorAccountCreateResponse {

    private String id;

    /** active / archived */
    private String status;

    private String platformCode;

    private String accountName;
}
