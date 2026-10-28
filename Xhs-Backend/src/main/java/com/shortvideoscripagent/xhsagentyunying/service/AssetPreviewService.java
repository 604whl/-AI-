package com.shortvideoscripagent.xhsagentyunying.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.asset.AssetPreviewResponse;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Asset preview service for platform spec validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetPreviewService {

    private static final int CODE_BAD_REQUEST = 40020;

    private final ContentAssetMapper assetMapper;

    /**
     * Preview asset platform spec compliance.
     */
    public AssetPreviewResponse preview(Long userId, UUID assetId, String platform) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();

        ContentAsset asset = assetMapper.selectById(assetId);
        if (asset == null || !workspaceId.equals(asset.getWorkspaceId())) {
            throw new BusinessException(CODE_BAD_REQUEST, "asset_not_found");
        }

        PlatformSpec spec = PlatformSpec.forPlatform(platform);
        return buildPreview(asset, spec, platform);
    }

    private AssetPreviewResponse buildPreview(ContentAsset asset, PlatformSpec spec, String platform) {
        List<String> warnings = new ArrayList<>();
        List<String> info = new ArrayList<>();

        // Title length check
        String title = asset.getTitle() != null ? asset.getTitle() : "";
        if (title.length() > spec.maxTitleLength) {
            warnings.add("Title exceeds " + spec.platform + " limit " + spec.maxTitleLength + " chars, current " + title.length() + " chars");
        } else if (title.length() > spec.maxTitleLength * 0.8) {
            info.add("Title close to " + spec.platform + " limit, recommended max " + spec.recommendedTitleLength + " chars");
        }

        // Body length check
        String body = asset.getBody() != null ? asset.getBody() : "";
        if (body.length() > spec.maxBodyLength) {
            warnings.add("Body exceeds " + spec.platform + " limit " + spec.maxBodyLength + " chars, current " + body.length() + " chars");
        } else if (body.length() > spec.maxBodyLength * 0.8) {
            info.add("Body close to " + spec.platform + " limit, recommended max " + spec.recommendedBodyLength + " chars");
        }

        // Tags count check
        List<String> tags = parseTags(asset.getTags());
        if (tags.size() > spec.maxTags) {
            warnings.add("Tags count exceeds " + spec.platform + " limit " + spec.maxTags + ", current " + tags.size() + " tags");
        }

        return AssetPreviewResponse.builder()
                .assetId(asset.getId())
                .platform(spec.platform)
                .warnings(warnings)
                .info(info)
                .spec(Map.of(
                        "maxTitleLength", spec.maxTitleLength,
                        "maxBodyLength", spec.maxBodyLength,
                        "maxTags", spec.maxTags,
                        "recommendedCoverRatio", spec.recommendedCoverRatio,
                        "coverRatioOptions", spec.coverRatioOptions,
                        "recommendedTitleLength", spec.recommendedTitleLength,
                        "recommendedBodyLength", spec.recommendedBodyLength,
                        "maxVideoDuration", spec.maxVideoDuration != null ? spec.maxVideoDuration : 0,
                        "recommendedVideoDuration", spec.recommendedVideoDuration != null ? spec.recommendedVideoDuration : 0
                ))
                .build();
    }

    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return new ObjectMapper().readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse tags JSON: {}", json, e);
            return List.of();
        }
    }

    /**
     * Platform spec definition.
     */
    public static class PlatformSpec {
        public final String platform;
        public final int maxTitleLength;
        public final int recommendedTitleLength;
        public final int maxBodyLength;
        public final int recommendedBodyLength;
        public final int maxTags;
        public final String recommendedCoverRatio;
        public final List<String> coverRatioOptions;
        public final Integer maxVideoDuration;
        public final Integer recommendedVideoDuration;

        public PlatformSpec(String platform, int maxTitle, int recTitle, int maxBody, int recBody,
                            int maxTags, String coverRatio, List<String> ratios,
                            Integer maxVid, Integer recVid) {
            this.platform = platform;
            this.maxTitleLength = maxTitle;
            this.recommendedTitleLength = recTitle;
            this.maxBodyLength = maxBody;
            this.recommendedBodyLength = recBody;
            this.maxTags = maxTags;
            this.recommendedCoverRatio = coverRatio;
            this.coverRatioOptions = ratios;
            this.maxVideoDuration = maxVid;
            this.recommendedVideoDuration = recVid;
        }

        /**
         * Get spec for platform.
         */
        public static PlatformSpec forPlatform(String platform) {
            String p = platform.toLowerCase();
            return switch (p) {
                case "xiaohongshu", "xhs" -> new PlatformSpec(
                        "XIAOHONGSHU", 1000, 200, 10000, 3000, 20,
                        "3:4", List.of("1:1", "3:4", "16:9"), 300, 60);
                case "douyin", "dy" -> new PlatformSpec(
                        "DOUYIN", 55, 20, 2000, 500, 10,
                        "16:9", List.of("16:9", "9:16", "1:1"), 180, 60);
                case "weibo", "wb" -> new PlatformSpec(
                        "WEIBO", 140, 50, 2000, 500, 10,
                        "16:9", List.of("16:9", "3:4"), null, null);
                case "bili", "bilibili" -> new PlatformSpec(
                        "BILIBILI", 80, 30, 4000, 1000, 10,
                        "16:9", List.of("16:9"), 600, 180);
                default -> new PlatformSpec(
                        platform.toUpperCase(), 200, 100, 5000, 1000, 10,
                        "16:9", List.of("16:9", "1:1", "3:4"), null, null);
            };
        }
    }
}