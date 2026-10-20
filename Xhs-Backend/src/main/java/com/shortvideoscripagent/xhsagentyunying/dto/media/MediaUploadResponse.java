package com.shortvideoscripagent.xhsagentyunying.dto.media;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class MediaUploadResponse {
    private Long id;
    private UUID workspaceId;
    private String mediaType;
    private String url;
    private String thumbnailUrl;
    private String filename;
    private Long fileSize;
    private String contentType;
    private Integer width;
    private Integer height;
    private Integer durationSec;
    private String status;
    private String scanStatus;
    private java.time.OffsetDateTime createdAt;
}