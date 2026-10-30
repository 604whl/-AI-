package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbMapTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 操作审计实体。
 * <p>
 * 对应 {@code app.audit_event} 表。
 * </p>
 * <p>
 * <b>主键类型必须是 {@code UUID}</b>（对应 {@code app.audit_event.id UUID}）。
 * 早期实现声明为 {@code String} + {@code IdType.ASSIGN_UUID}，MyBatis-Plus 会写入
 * 32 位十六进制字符串，PostgreSQL 无法隐式转换 →
 * {@code column "id" is of type uuid but expression is of type character varying}，
 * 而 {@link AuditWriter} 的兜底 catch 会把异常吞掉，
 * 结果是<b>所有审计记录 100% 写入失败</b>（线上 {@code audit_event} 表恒为 0 行）。
 * </p>
 */
@Data
@TableName(value = "audit_event", autoResultMap = true)
public class AuditEvent {

    /**
     * 审计事件 id。
     * <p>
     * 注意：MyBatis-Plus 的 {@code IdType.ASSIGN_UUID} 只对 {@code String} 主键自动赋值，
     * {@code UUID} 主键必须手动 {@code setId(UUID.randomUUID())}（见 {@link AuditWriter}）。
     * </p>
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private String workspaceId;

    private String actorUserId;

    private String action;

    private String resourceType;

    private String resourceId;

    /** JSON 字符串（MyBatis-Plus 通过 TypeHandler 转换，DB 为 JSONB） */
    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> metadata;

    private String ip;

    private String userAgent;

    /** success / failure */
    private String result;

    private Integer errorCode;

    private String errorMessage;

    private OffsetDateTime createdAt;
}
