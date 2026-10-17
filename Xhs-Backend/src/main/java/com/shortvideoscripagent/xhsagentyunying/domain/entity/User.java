package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    private String passwordHash;

    private String displayName;

    private String defaultPersona;

    private Integer dailyQuota;

    private String agentPreferences;

    /** 连续密码错误次数 */
    private Integer failedAttempts;

    /** 账号锁定截止时间（NULL = 未锁定） */
    private OffsetDateTime lockedUntil;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
