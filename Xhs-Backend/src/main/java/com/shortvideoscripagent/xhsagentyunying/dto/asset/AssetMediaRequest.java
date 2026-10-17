package com.shortvideoscripagent.xhsagentyunying.dto.asset;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 添加媒体请求（P0-3）。
 */
@Data
public class AssetMediaRequest {

    @NotBlank(message = "mediaType is required")
    private String mediaType;  // cover / image / video

    @NotBlank(message = "objectKey is required")
    private String objectKey;

    private String role = "attachment";  // cover / main / attachment

    private Integer sortOrder = 0;

    private Integer width;

    private Integer height;

    private Long fileSize;
}
