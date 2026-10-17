package com.shortvideoscripagent.xhsagentyunying.dto.asset;

import lombok.Data;

/**
 * 媒体项（内嵌在 AssetResponse 中，P0-3）。
 */
@Data
public class MediaItem {

    private java.util.UUID id;
    private String mediaType;   // cover / image / video
    private String objectKey;
    private String role;         // cover / main / attachment
    private Integer sortOrder;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private java.time.OffsetDateTime createdAt;
}
