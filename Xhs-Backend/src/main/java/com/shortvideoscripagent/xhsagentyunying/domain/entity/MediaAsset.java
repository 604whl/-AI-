package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("media_asset")
public class MediaAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private Long userId;
    private java.util.UUID assetId;
    private String mediaType;
    private String objectKey;
    private String filename;
    private Long fileSize;
    private String contentType;
    private Integer width;
    private Integer height;
    private Integer durationSec;
    private String thumbnailKey;
    private String status;
    private String scanStatus;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_VIDEO = "VIDEO";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_FAILED = "failed";
    public static final String SCAN_SCANNING = "scanning";
    public static final String SCAN_CLEAN = "clean";
    public static final String SCAN_INFECTED = "infected";
}