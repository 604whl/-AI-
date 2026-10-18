package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicDto;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicListResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicUpdateRequest;

public interface TopicPoolService {

    TopicListResponse list(Long workspaceId, String status, int page, int size);

    TopicDto create(Long workspaceId, Long userId, TopicCreateRequest req);

    TopicDto update(Long topicId, Long workspaceId, TopicUpdateRequest req);

    ToAssetResponse toAsset(Long topicId, Long workspaceId, Long userId, String platform, String category);

    record ToAssetResponse(java.util.UUID assetId, java.util.UUID contentAssetVersionId) {}
}