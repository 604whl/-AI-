package com.shortvideoscripagent.xhsagentyunying.service;

import java.util.List;

public interface AssetTagService {

    void setTags(Long assetId, Long workspaceId, List<String> tags);

    List<String> getTags(Long assetId, Long workspaceId);

    List<Long> findAssetIdsByTag(String tag, Long workspaceId);

    List<Long> findAssetIdsByAnyTag(List<String> tags, Long workspaceId);
}