package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("browser_assist_session")
public class BrowserAssistSession {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private Long userId;

    /** Workspace ID（VARCHAR(32)，与 app.workspace.id 一致；V49 由 UUID 归一） */
    private String workspaceId;

    private String platform;

    private String accountLabel;

    private String status;

    private UUID assetId;

    private String cookieEncrypted;

    private OffsetDateTime cookieExpiresAt;

    private OffsetDateTime lastUsedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public static final String STATUS_CREATED = "created";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_FILLED = "filled";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_CLOSED = "closed";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_FAILED = "failed";
}