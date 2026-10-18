package com.shortvideoscripagent.xhsagentyunying.service;

import java.util.List;

public interface AssetTagService {

    void setTags(String assetUuid, Long workspaceId, List<String> tags);

    List<String> getTags(String assetUuid, Long workspaceId);

    List<String> findAssetUuidsByTag(String tag, Long workspaceId);

    List<String> findAssetUuidsByAnyTag(List<String> tags, Long workspaceId);
}