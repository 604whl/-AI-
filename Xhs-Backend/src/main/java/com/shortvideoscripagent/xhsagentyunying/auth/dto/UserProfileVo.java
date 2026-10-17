package com.shortvideoscripagent.xhsagentyunying.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class UserProfileVo {

    private Long id;
    private String email;
    private String displayName;
    private String defaultPersona;
    private Integer dailyQuota;

    // P0-9: 账号注销状态
    /** 账号状态：active / scheduled / cancelled / deleted */
    private String deletionStatus;
    /** 计划删除时间 */
    private OffsetDateTime scheduledDeletionAt;
}
