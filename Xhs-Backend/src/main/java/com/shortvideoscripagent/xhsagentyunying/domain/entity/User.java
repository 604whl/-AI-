package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户实体（P0-9 扩展）。
 * <p>
 * 新增字段用于账号注销（GDPR-like 30 天软删流程）。
 */
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

    // ═══════════════════════════════════════════════════════════════════════════
    // P0-9 新增字段
    // ═══════════════════════════════════════════════════════════════════════════

    /** 计划删除时间（30 天后执行物理删除） */
    private OffsetDateTime scheduledDeletionAt;

    /** 账号状态：active / scheduled / cancelled / deleted */
    private String deletionStatus;

    /** 用户确认注销的时间 */
    private OffsetDateTime deletionConfirmedAt;
}
