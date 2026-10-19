package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicListItem;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicRefreshResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicTrendResponse;
import com.shortvideoscripagent.xhsagentyunying.service.HotTopicService;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Hot topic management API.
 * Base path: /api/v1/hot-topics
 */
@RestController
@RequestMapping("/api/v1/hot-topics")
@RequiredArgsConstructor
public class HotTopicController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final HotTopicService hotTopicService;

    /**
     * List hot topics with optional filters and pagination.
     */
    @GetMapping("/list")
    public ApiResponse<HotTopicService.PageResult<HotTopicListItem>> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean promoted,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        requireUserId();
        WorkspaceGuards.requireCurrentWorkspaceId();

        var pageResult = hotTopicService.list(platform, category, promoted, page, size);

        // Map from internal DTO to public DTO
        List<HotTopicListItem> items = pageResult.items().stream()
                .map(dto -> HotTopicListItem.builder()
                        .id(dto.id())
                        .platform(dto.platform())
                        .category(dto.category())
                        .title(dto.title())
                        .heatScore(dto.heatScore())
                        .fetchedAt(dto.fetchedAt())
                        .promotedToPool(dto.promotedToPool())
                        .build())
                .toList();

        var mapped = new HotTopicService.PageResult<HotTopicListItem>(
                items, pageResult.total(), pageResult.page(), pageResult.size());
        return ApiResponse.ok(mapped);
    }

    /**
     * Compute the heat-score trend for a specific topic title.
     * Workspace is taken from WorkspaceGuards context (not from query params).
     *
     * @param title       topic title (required)
     * @param windowDays  number of days to look back (1-30, default 7)
     */
    @GetMapping("/trend")
    public ApiResponse<HotTopicTrendResponse> trend(
            @RequestParam String title,
            @RequestParam(defaultValue = "7") int windowDays
    ) {
        requireUserId();
        WorkspaceGuards.requireCurrentWorkspaceId();

        if (title == null || title.isBlank()) {
            throw new BusinessException(40010, "title is required");
        }
        int w = Math.min(Math.max(windowDays, 1), 30);

        HotTopicTrendResponse response = hotTopicService.computeTrend(title, w);
        return ApiResponse.ok(response);
    }

    /**
     * Manually trigger a refresh from GetHotTopicsTool.
     */
    @PostMapping("/refresh")
    public ApiResponse<HotTopicRefreshResponse> refresh() {
        Long userId = requireUserId();
        WorkspaceGuards.requireCurrentWorkspaceId();

        int count;
        String message;
        try {
            count = hotTopicService.refreshFromAgentTool();
            message = "refreshed";
        } catch (UnsupportedOperationException e) {
            count = 0;
            message = "tool_not_configured";
        } catch (RuntimeException e) {
            count = 0;
            message = "refresh_failed: " + e.getMessage();
        }

        return ApiResponse.ok(new HotTopicRefreshResponse(count, message));
    }

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}
