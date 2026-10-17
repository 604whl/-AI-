package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 操作审计实体。
 * <p>
 * 对应 {@code app.audit_event} 表。
 * </p>
 */
@Data
@TableName("audit_event")
public class AuditEvent {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;

    private String actorUserId;

    private String action;

    private String resourceType;

    private String resourceId;

    /** JSON 字符串（MyBatis-Plus 通过 TypeHandler 转换，DB 为 JSONB） */
    private Map<String, Object> metadata;

    private String ip;

    private String userAgent;

    /** success / failure */
    private String result;

    private Integer errorCode;

    private String errorMessage;

    private OffsetDateTime createdAt;
}
