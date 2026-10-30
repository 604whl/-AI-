package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicBulkImportRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicBulkImportResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicDto;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicListResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicUpdateRequest;

public interface TopicPoolService {

    TopicListResponse list(String workspaceId, String status, int page, int size);

    TopicDto create(String workspaceId, Long userId, TopicCreateRequest req);

    TopicDto update(Long topicId, String workspaceId, TopicUpdateRequest req);

    ToAssetResponse toAsset(Long topicId, String workspaceId, Long userId, String platform, String category);

    record ToAssetResponse(java.util.UUID assetId, java.util.UUID contentAssetVersionId) {}

    /**
     * 内部 / 批量创建入口（CSV 导入等）。
     * <p>
     * 与 {@link #create} 的差异：命中去重（同 workspace + source + 归一化标题）时返回 {@code null}
     * 表示「跳过」，不抛冲突异常，便于批量导入逐行统计。
     * </p>
     */
    TopicDto createInternal(String workspaceId, Long userId, TopicCreateRequest req);

    /**
     * Bulk import topics from AI search results with deduplication.
     *
     * @param workspaceId workspace context
     * @param userId creator user ID
     * @param request bulk import request with items
     * @return import results with created and skipped items
     */
    TopicBulkImportResponse bulkImport(String workspaceId, Long userId, TopicBulkImportRequest request);
}