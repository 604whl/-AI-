package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.billing.refund.RefundService;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.refund.RefundDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.ratelimit.RateLimited;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * S4-4 工作区退款接口。
 * <p>
 * POST /v1/workspaces/{workspaceId}/refunds
 * GET  /v1/workspaces/{workspaceId}/refunds
 * GET  /v1/workspaces/{workspaceId}/refunds/{refundNo}
 * </p>
 * <p>
 * <b>租户隔离</b>：跨 workspace 访问一律 404。
 * 申请退款需 OWNER，列表 / 详情需 OWNER 或 ADMIN。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /**
     * 申请退款（幂等；3 次/天/工作区；OWNER only）。
     */
    @PostMapping("/v1/workspaces/{workspaceId}/refunds")
    @RateLimited(key = "refund:create:${workspaceId}", limit = 3, window = 86400, dimension = "workspace")
    public ApiResponse<RefundDtos.RefundView> createRefund(
            @PathVariable String workspaceId,
            @RequestBody RefundDtos.CreateRefundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = RequestContext.getUserIdOrNull();
        return ApiResponse.ok(refundService.requestRefund(workspaceId, userId, request, idempotencyKey));
    }

    /**
     * 工作区退款单分页列表（OWNER / ADMIN）。
     */
    @GetMapping("/v1/workspaces/{workspaceId}/refunds")
    public ApiResponse<PageResult<RefundDtos.RefundView>> listRefunds(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Long userId = RequestContext.getUserIdOrNull();
        Page<RefundDtos.RefundView> p = refundService.listByWorkspace(workspaceId, userId, status, page, size);
        return ApiResponse.ok(new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    /**
     * 退款单详情（OWNER / ADMIN）。
     */
    @GetMapping("/v1/workspaces/{workspaceId}/refunds/{refundNo}")
    public ApiResponse<RefundDtos.RefundView> getRefund(
            @PathVariable String workspaceId,
            @PathVariable String refundNo) {
        Long userId = RequestContext.getUserIdOrNull();
        RefundDtos.RefundView view = refundService.getByRefundNo(refundNo, userId);
        // 跨 workspace 一律 404（不泄露存在性）
        if (view == null || !workspaceId.equals(view.getWorkspaceId())) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(view);
    }

    /**
     * 分页结果。
     */
    @Data
    public static class PageResult<T> {
        private final List<T> items;
        private final long total;
        private final long page;
        private final long size;

        public PageResult(List<T> items, long total, long page, long size) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.size = size;
        }
    }
}
