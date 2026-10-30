package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.billing.order.OrderService;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.order.OrderDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.ratelimit.RateLimited;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * S4-2 工作区订单接口。
 * <p>
 * POST /v1/workspaces/{workspaceId}/orders
 * GET  /v1/workspaces/{workspaceId}/orders
 * GET  /v1/orders/{orderNo}
 * </p>
 * <p>
 * <b>租户隔离</b>：跨 workspace 访问一律 404。
 * 下单需 OWNER，列表 / 详情需 OWNER 或 ADMIN。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单（幂等；10 次/天/工作区；OWNER only）。
     */
    @PostMapping("/v1/workspaces/{workspaceId}/orders")
    @RateLimited(key = "order:create:${workspaceId}", limit = 10, window = 86400, dimension = "workspace")
    public ApiResponse<OrderDtos.OrderView> createOrder(
            @PathVariable String workspaceId,
            @RequestBody OrderDtos.CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = RequestContext.getUserIdOrNull();
        return ApiResponse.ok(orderService.createOrder(workspaceId, userId, request, idempotencyKey));
    }

    /**
     * 工作区订单分页列表（OWNER / ADMIN）。
     */
    @GetMapping("/v1/workspaces/{workspaceId}/orders")
    public ApiResponse<PageResult<OrderDtos.OrderView>> listOrders(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Long userId = RequestContext.getUserIdOrNull();
        Page<OrderDtos.OrderView> p = orderService.listByWorkspace(workspaceId, userId, status, page, size);
        return ApiResponse.ok(new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    /**
     * 订单详情（OWNER / ADMIN）。
     */
    @GetMapping("/v1/orders/{orderNo}")
    public ApiResponse<OrderDtos.OrderView> getOrder(@PathVariable String orderNo) {
        Long userId = RequestContext.getUserIdOrNull();
        return ApiResponse.ok(orderService.getByOrderNo(orderNo, userId));
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
