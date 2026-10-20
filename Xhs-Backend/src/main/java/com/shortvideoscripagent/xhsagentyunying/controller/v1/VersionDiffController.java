package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.asset.VersionDiffResponse;
import com.shortvideoscripagent.xhsagentyunying.service.VersionDiffService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/assets")
@RequiredArgsConstructor
public class VersionDiffController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final VersionDiffService versionDiffService;

    /**
     * 对比资产的两个版本，输出结构化 diff 结果（LCS 算法）。
     * GET /v1/assets/{id}/diff?v1=1&v2=2
     */
    @GetMapping("/{id}/diff")
    public ApiResponse<VersionDiffResponse> diff(
            @PathVariable String id,
            @RequestParam int v1,
            @RequestParam int v2
    ) {
        requireUserId();
        UUID assetId = UUID.fromString(id);
        return ApiResponse.ok(versionDiffService.diff(assetId, v1, v2));
    }

    private Long requireUserId() {
        Long uid = RequestContext.getUserId();
        if (uid == null) throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        return uid;
    }
}