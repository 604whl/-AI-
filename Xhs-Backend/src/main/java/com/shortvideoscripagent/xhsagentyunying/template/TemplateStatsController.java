package com.shortvideoscripagent.xhsagentyunying.template;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.template.TemplateApplyStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/templates")
@RequiredArgsConstructor
public class TemplateStatsController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final TemplateStatsService templateStatsService;

    /**
     * 单模板效果统计（30天引用次数 + 采用率 + 每日明细）。
     * GET /v1/templates/{id}/stats
     */
    @GetMapping("/{id}/stats")
    public ApiResponse<TemplateApplyStatsResponse> getStats(@PathVariable UUID id) {
        requireUserId();
        return ApiResponse.ok(templateStatsService.getApplyStats(id));
    }

    /**
     * 热门模板排行榜（按30天引用次数）。
     * GET /v1/templates/stats/leaderboard?limit=10
     */
    @GetMapping("/stats/leaderboard")
    public ApiResponse<List<TemplateLeaderboardEntry>> getLeaderboard(
            @RequestParam(defaultValue = "10") int limit
    ) {
        Long userId = requireUserId();
        return ApiResponse.ok(templateStatsService.getLeaderboard(userId, limit));
    }

    private Long requireUserId() {
        Long uid = RequestContext.getUserId();
        if (uid == null) throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        return uid;
    }
}