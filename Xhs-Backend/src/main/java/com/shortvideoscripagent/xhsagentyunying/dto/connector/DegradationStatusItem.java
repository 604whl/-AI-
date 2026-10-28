package com.shortvideoscripagent.xhsagentyunying.dto.connector;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 平台降级状态。
 */
@Data
@Builder
public class DegradationStatusItem {

    /** 平台代码 */
    private String platformCode;

    /** 当前连接器等级 */
    private String currentLevel;

    /** 降级目标 provider code */
    private String degradedTo;

    /** 降级原因 */
    private String reason;

    /** 降级起始时间 */
    private OffsetDateTime since;
}
