package com.shortvideoscripagent.xhsagentyunying.dto.asset;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publish preview response with platform spec validation.
 */
@Data
@Builder
public class AssetPreviewResponse {
    private UUID assetId;
    private String platform;
    private List<String> warnings;
    private List<String> info;
    private Map<String, Object> spec;
}