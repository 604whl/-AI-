package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 平台 × 连接器 授权实体（对应 {@code app.platform_connector_authorization}）。
 * <p>
 * 阶段 0 冻结表，是运行时授权门禁的唯一数据来源。
 */
@Data
@TableName("platform_connector_authorization")
public class PlatformConnectorAuthorization {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 平台代码，如 XIAOHONGSHU */
    private String platformCode;

    /** 连接器提供方代码，如 XHS_BROWSER_SIDEBAR */
    private String connectorProviderCode;

    /** 是否已授权 */
    private Boolean authorized;

    /** 授权来源说明 */
    private String authorizationSource;

    /** 法务审核人 */
    private String legalReviewer;

    /** 法务审核时间 */
    private OffsetDateTime reviewedAt;

    /** 授权截止时间，null 表示长期有效 */
    private OffsetDateTime expiresAt;

    /** 授权范围说明 */
    private String scope;

    /** 备注 */
    private String notes;

    private OffsetDateTime createdAt;
}
