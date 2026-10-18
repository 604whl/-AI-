package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AssetTag;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AssetTagMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetTagServiceImpl implements AssetTagService {

    private static final int CODE_NOT_FOUND = 40410;
    private static final int CODE_BAD_REQUEST = 40010;
    private static final int MAX_TAGS_PER_ASSET = 20;

    private final AssetTagMapper assetTagMapper;
    private final ContentAssetMapper assetMapper;

    @Override
    @Transactional
    public void setTags(String assetUuid, Long workspaceId, List<String> tags) {
        validateAssetOwnership(assetUuid, workspaceId);
        List<String> normalized = normalizeTags(tags);
        if (normalized.size() > MAX_TAGS_PER_ASSET) {
            throw new BusinessException(CODE_BAD_REQUEST,
                    "too_many_tags: max " + MAX_TAGS_PER_ASSET + " per asset");
        }
        assetTagMapper.deleteByAssetUuid(assetUuid);
        for (String tag : normalized) {
            AssetTag at = new AssetTag();
            at.setAssetUuid(assetUuid);
            at.setTag(tag);
            at.setCreatedAt(OffsetDateTime.now());
            assetTagMapper.insert(at);
        }
        log.info("Tags set for asset {}: {}", assetUuid, normalized);
    }

    @Override
    public List<String> getTags(String assetUuid, Long workspaceId) {
        validateAssetOwnership(assetUuid, workspaceId);
        List<String> tags = assetTagMapper.selectTagsByAssetUuid(assetUuid);
        return tags != null ? tags : List.of();
    }

    @Override
    public List<String> findAssetUuidsByTag(String tag, Long workspaceId) {
        if (tag == null || tag.isBlank()) {
            return List.of();
        }
        List<String> candidates = assetTagMapper.selectAssetUuidsByTag(tag.toLowerCase().trim());
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return filterByWorkspace(candidates, workspaceId);
    }

    @Override
    public List<String> findAssetUuidsByAnyTag(List<String> tags, Long workspaceId) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::toLowerCase)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> candidates = assetTagMapper.selectAssetUuidsByAnyTag(normalized);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return filterByWorkspace(candidates, workspaceId);
    }

    private void validateAssetOwnership(String assetUuid, Long workspaceId) {
        if (assetUuid == null || assetUuid.isBlank()) {
            throw new BusinessException(CODE_NOT_FOUND, "asset_not_found");
        }
        ContentAsset asset = assetMapper.selectById(UUID.fromString(assetUuid));
        if (asset == null) {
            throw new BusinessException(CODE_NOT_FOUND, "asset_not_found");
        }
        long wsLeast = asset.getWorkspaceId().getLeastSignificantBits();
        long wsHash = (long) asset.getWorkspaceId().hashCode();
        if (wsLeast != workspaceId && wsHash != workspaceId) {
            throw new BusinessException(CODE_NOT_FOUND, "asset_not_found");
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::toLowerCase)
                .map(String::trim)
                .filter(t -> !t.isEmpty() && t.length() <= 64)
                .distinct()
                .toList();
    }

    private List<String> filterByWorkspace(List<String> assetUuids, Long workspaceId) {
        if (assetUuids == null || assetUuids.isEmpty()) {
            return List.of();
        }
        return assetUuids.stream()
                .map(uuid -> {
                    try { return UUID.fromString(uuid); }
                    catch (Exception e) { return null; }
                })
                .filter(uuid -> uuid != null)
                .map(uuid -> assetMapper.selectById(uuid))
                .filter(asset -> asset != null && asset.getWorkspaceId() != null)
                .filter(asset -> {
                    long wsLeast = asset.getWorkspaceId().getLeastSignificantBits();
                    long wsHash = (long) asset.getWorkspaceId().hashCode();
                    return wsLeast == workspaceId || wsHash == workspaceId;
                })
                .map(ContentAsset::getId)
                .map(UUID::toString)
                .toList();
    }
}