package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicBulkImportRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicBulkImportResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicDto;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicListResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicUpdateRequest;
import com.shortvideoscripagent.xhsagentyunying.service.TopicPoolService;
import com.shortvideoscripagent.xhsagentyunying.service.TopicPoolService.ToAssetResponse;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicPoolController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final TopicPoolService topicPoolService;

    @GetMapping
    public ApiResponse<TopicListResponse> list(
            @RequestParam Long workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long userId = requireUserId();
        WorkspaceGuards.requireMember(String.valueOf(workspaceId));
        return ApiResponse.ok(topicPoolService.list(workspaceId, status, page, size));
    }

    @PostMapping
    public ApiResponse<TopicDto> create(@Valid @RequestBody TopicCreateRequest request) {
        Long userId = requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        return ApiResponse.ok(topicPoolService.create(
                Long.parseLong(workspaceId), userId, request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<TopicDto> update(
            @PathVariable Long id,
            @RequestBody TopicUpdateRequest request
    ) {
        Long userId = requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        return ApiResponse.ok(topicPoolService.update(
                id, Long.parseLong(workspaceId), request));
    }

    @PostMapping("/{id}/to-asset")
    public ApiResponse<ToAssetResponse> toAsset(
            @PathVariable Long id,
            @RequestBody ToAssetRequest request
    ) {
        Long userId = requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        ToAssetResponse result = topicPoolService.toAsset(
                id, Long.parseLong(workspaceId), userId,
                request.getPlatform(), request.getCategory());
        return ApiResponse.ok(result);
    }

    @PostMapping("/bulk-import")
    public ApiResponse<TopicBulkImportResponse> bulkImport(
            @Valid @RequestBody TopicBulkImportRequest request
    ) {
        Long userId = requireUserId();
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        return ApiResponse.ok(topicPoolService.bulkImport(
                Long.parseLong(workspaceId), userId, request));
    }

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }

    @lombok.Data
    public static class ToAssetRequest {
        private String platform;
        private String category;
    }
}