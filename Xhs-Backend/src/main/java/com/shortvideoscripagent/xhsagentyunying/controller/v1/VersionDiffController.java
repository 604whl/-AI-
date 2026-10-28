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
     * GET /v1/assets/{id}/version-diff?v1=1&v2=2
     * <p>
     * <b>注意</b>：路径为 {@code /version-diff} 而非 {@code /diff}，因为
     * {@code ContentAssetController#diffVersions} 已占用 {@code GET /v1/assets/{id}/diff}
     * （前端 {@code api/asset.ts} 使用的 line-based diff 契约），两者同路径会导致
     * Spring 启动期 {@code Ambiguous mapping} 报错。前端当前未使用本接口。
     */
    @GetMapping("/{id}/version-diff")
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