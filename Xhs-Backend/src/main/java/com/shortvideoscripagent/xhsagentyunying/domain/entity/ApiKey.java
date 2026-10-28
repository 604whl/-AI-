package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbListTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Workspace 级 API Key 实体。
 * <p>
 * 设计参考 Stripe：前缀 xhs_sk_ + 24 字符 base62，明文仅展示一次。
 * 数据库仅存储 SHA-256 哈希。
 * </p>
 */
@Data
@TableName(value = "api_key", schema = "app", autoResultMap = true)
public class ApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属工作区 */
    private String workspaceId;

    /** SHA-256 哈希后的 key */
    private String keyHash;

    /** 前缀（如 xhs_sk_），用于展示识别 */
    private String prefix;

    /** 名称（用户自定义，如 "MCP CLI"） */
    private String name;

    /** 权限范围 JSONB 数组 */
        @TableField(typeHandler = JsonbListTypeHandler.class)
    private List<String> scopes;

    /** 创建人 */
    private Long createdBy;

    private OffsetDateTime createdAt;

    /** 最后使用时间 */
    private OffsetDateTime lastUsedAt;

    /** 撤销时间（NULL = 有效） */
    private OffsetDateTime revokedAt;
}
