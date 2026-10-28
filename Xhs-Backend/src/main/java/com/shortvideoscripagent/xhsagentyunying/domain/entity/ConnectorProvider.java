package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 连接器提供方实体（对应 {@code app.connector_provider}）。
 * <p>
 * 阶段 0 冻结表，每行代表一种具体的接入方式。
 */
@Data
@TableName("connector_provider")
public class ConnectorProvider {

    /** 连接器代码，如 XHS_BROWSER_SIDEBAR */
    @TableId(value = "code", type = IdType.INPUT)
    private String code;

    /** 展示名 */
    private String displayName;

    /** 平台代码，如 XIAOHONGSHU */
    private String platformCode;

    /** 连接器类型：BROWSER_EXTENSION / OFFICIAL_API / CSV_IMPORT / WEBHOOK_RECEIVER / MANUAL_FORM */
    private String connectorType;

    /** 是否需要 OAuth 授权 */
    private Boolean requiresOauth;

    /** 是否需要用户凭据 */
    private Boolean requiresUserCredentials;

    /** 风险说明 */
    private String riskNote;

    /** 是否启用 */
    private Boolean enabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
