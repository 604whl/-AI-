package com.shortvideoscripagent.xhsagentyunying.freeze.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * M0-5: platform connector authorization entity.
 * Maps to database table app.platform_connector_authorization.
 */
@Data
@TableName(value = "app.platform_connector_authorization", autoResultMap = true)
public class PlatformConnectorAuthorization {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    /** Platform code, corresponds to PlatformCode enum */
    @TableField("platform_code")
    private String platformCode;

    /** Connector provider code */
    @TableField("connector_provider_code")
    private String connectorProviderCode;

    /** Whether authorized */
    @TableField("authorized")
    private Boolean authorized;

    /** Authorization source description */
    @TableField("authorization_source")
    private String authorizationSource;

    /** Legal reviewer */
    @TableField("legal_reviewer")
    private String legalReviewer;

    /** Legal review timestamp */
    @TableField("reviewed_at")
    private OffsetDateTime reviewedAt;

    /** Authorization expiry, NULL means never expires */
    @TableField("expires_at")
    private OffsetDateTime expiresAt;

    /** Authorization scope description */
    @TableField("scope")
    private String scope;

    /** Notes */
    @TableField("notes")
    private String notes;

    @TableField("created_at")
    private OffsetDateTime createdAt;
}