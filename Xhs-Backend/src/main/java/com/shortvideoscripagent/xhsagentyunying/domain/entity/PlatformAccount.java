package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S3-2 平台账号绑定实体。
 * <p>
 * 对应 {@code app.platform_account} 表。
 * </p>
 * <p>
 * 凭据托管决策 D1 = C（仅人工导入）：系统不持有平台账号密码 / Cookie / Token 明文，
 * {@code credentialRef} 只保存 {@code browser_assist_session.id} 这样的**引用**。
 * </p>
 */
@Data
@TableName("platform_account")
public class PlatformAccount {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属工作区（VARCHAR(32)，与 app.workspace.id 一致） */
    private String workspaceId;

    /** 平台代码（大写枚举名，与 app.platform.code 一致） */
    private String platformCode;

    /** 用户自定义账号标签 */
    private String accountLabel;

    /** 平台侧账号 ID（可空） */
    private String externalAccountId;

    /** 接入所用的连接器实现（app.connector_provider.code） */
    private String connectorProviderCode;

    /** 连接等级（freeze/PlatformConnectorLevel 名称），服务端判定 */
    private String level;

    /** 连接状态：unconnected / connected / expired / reauthorizing / failed */
    private String status;

    /** 凭据**引用**（browser_assist_session.id 或 NULL），禁止明文 */
    private String credentialRef;

    private OffsetDateTime authorizedAt;

    private OffsetDateTime expiresAt;

    private OffsetDateTime lastSuccessAt;

    private String lastError;

    private Long createdBy;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    // ── 状态常量 ────────────────────────────────────────────────────────
    public static final String STATUS_UNCONNECTED = "unconnected";
    public static final String STATUS_CONNECTED = "connected";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_REAUTHORIZING = "reauthorizing";
    public static final String STATUS_FAILED = "failed";
}
