package com.shortvideoscripagent.xhsagentyunying.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserUsageVo {

    private int dailyQuota;
    private int usedToday;
    private int remaining;
}
