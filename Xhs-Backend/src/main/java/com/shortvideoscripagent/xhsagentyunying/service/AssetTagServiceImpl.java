package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AssetTag;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AssetTagMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

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
    public void setTags(Long assetId, Long workspaceId, List<String> tags) {
        validateAssetOwnership(assetId, workspaceId);
        List<String> normalized = normalizeTags(tags);
        if (normalized.size() > MAX_TAGS_PER_ASSET) {
            throw new BusinessException(CODE_BAD_REQUEST,
                    "too_many_tags: max " + MAX_TAGS_PER_ASSET + " per asset");
        }
        assetTagMapper.deleteByAssetId(assetId);
        for (String tag : normalized) {
            AssetTag at = new AssetTag();
            at.setAssetId(assetId);
            at.setTag(tag);
            at.setCreatedAt(OffsetDateTime.now());
            assetTagMapper.insert(at);
        }
        log.info("Tags set for asset {}: {}", assetId, normalized);
    }

    @Override
    public List<String> getTags(Long assetId, Long workspaceId) {
        validateAssetOwnership(assetId, workspaceId);
        List<String> tags = assetTagMapper.selectTagsByAssetId(assetId);
        return tags != null ? tags : List.of();
    }

    @Override
    public List<Long> findAssetIdsByTag(String tag, Long workspaceId) {
        if (tag == null || tag.isBlank()) {
            return List.of();
        }
        List<Long> candidateIds = assetTagMapper.selectAssetIdsByTag(tag.toLowerCase().trim());
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        return filterByWorkspace(candidateIds, workspaceId);
    }

    @Override
    public List<Long> findAssetIdsByAnyTag(List<String> tags, Long workspaceId) {
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
        List<Long> candidateIds = assetTagMapper.selectAssetIdsByAnyTag(normalized);
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        return filterByWorkspace(candidateIds, workspaceId);
    }

    private void validateAssetOwnership(Long assetId, Long workspaceId) {
        if (assetId == null) {
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

    private List<Long> filterByWorkspace(List<Long> assetIds, Long workspaceId) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }
        return assetMapper.selectBatchIds(assetIds).stream()
                .filter(asset -> asset.getWorkspaceId() != null)
                .filter(asset -> {
                    long wsLeast = asset.getWorkspaceId().getLeastSignificantBits();
                    long wsHash = (long) asset.getWorkspaceId().hashCode();
                    return wsLeast == workspaceId || wsHash == workspaceId;
                })
                .map(asset -> asset.getId().getLeastSignificantBits())
                .toList();
    }
}