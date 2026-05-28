package com.shortvideoscripagent.xhsagentyunying.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileVo {

    private Long id;
    private String email;
    private String displayName;
    private String defaultPersona;
    private Integer dailyQuota;
}
