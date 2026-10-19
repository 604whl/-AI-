package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicBulkImportRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicBulkImportResponse;
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

    /**
     * Bulk import topics from AI search results with deduplication.
     *
     * @param workspaceId workspace context
     * @param userId creator user ID
     * @param request bulk import request with items
     * @return import results with created and skipped items
     */
    TopicBulkImportResponse bulkImport(Long workspaceId, Long userId, TopicBulkImportRequest request);
}