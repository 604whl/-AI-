package com.shortvideoscripagent.xhsagentyunying.service;

import java.util.List;

public interface AssetTagService {

    void setTags(String assetUuid, String workspaceId, List<String> tags);

    List<String> getTags(String assetUuid, String workspaceId);

    List<String> findAssetUuidsByTag(String tag, String workspaceId);

    List<String> findAssetUuidsByAnyTag(List<String> tags, String workspaceId);
}