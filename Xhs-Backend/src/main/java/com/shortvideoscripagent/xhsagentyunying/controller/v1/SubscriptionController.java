package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.billing.subscription.SubscriptionService;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.SubscriptionDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.ratelimit.RateLimited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S4-1 工作区订阅接口。
 * <p>
 * GET  /v1/workspaces/{workspaceId}/subscription
 * POST /v1/workspaces/{workspaceId}/subscription
 * POST /v1/workspaces/{workspaceId}/subscription/cancel
 * POST /v1/workspaces/{workspaceId}/subscription/change-plan
 * POST /v1/workspaces/{workspaceId}/subscription/trial
 * </p>
 * <p>
 * <b>租户隔离</b>：跨 workspace 访问一律返回 404（不泄露存在性）。
 * 写操作需 OWNER 角色，且带 {@code Idempotency-Key} 幂等。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * 查询订阅。OWNER/ADMIN 读全量；其他成员只读当前可用能力。
     */
    @GetMapping
    public ApiResponse<SubscriptionDtos.SubscriptionView> getSubscription(@PathVariable String workspaceId) {
        Long userId = RequestContext.getUserIdOrNull();
        return ApiResponse.ok(subscriptionService.getSubscription(workspaceId, userId));
    }

    /**
     * 开通订阅（幂等）。
     * <p>
     * <b>限流分两层</b>：
     * <ul>
     *   <li>本注解层：按<b>登录用户</b>（{@code ${userId}}）的<b>粗粒度防刷</b>上限。
     *       之所以不直接用 {@code dimension="workspace"} + {@code ${workspaceId}}：
     *       拦截器的 {@code ${workspaceId}} 取自 {@code X-Workspace-Id} 请求头，
     *       与路径变量可能不一致，用请求头分桶会被「换请求头」绕过；
     *       且一个用户可拥有多个工作区，按用户分桶会误伤正常多工作区用户。</li>
     *   <li>Service 层：按<b>路径中的 workspaceId</b> 的<b>权威频次限制</b>
     *       ——订阅 5 次/天、改套餐 3 次/天、试用 1 次/工作区
     *       （{@code RedisRateLimiter.require("workspace", ...)}）。</li>
     * </ul>
     * 请求头与路径不一致时，Service 层按路径 workspaceId 校验归属，仍然是有效拦截。
     * </p>
     */
    @PostMapping
    @RateLimited(key = "subscription:create:${userId}", limit = 50, window = 86400, dimension = "user")
    public ApiResponse<SubscriptionDtos.SubscriptionView> subscribe(
            @PathVariable String workspaceId,
            @RequestBody SubscriptionDtos.SubscribeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = RequestContext.getUserIdOrNull();
        return ApiResponse.ok(subscriptionService.subscribe(workspaceId, userId, request, idempotencyKey));
    }

    /**
     * 取消订阅（幂等；默认周期末取消）。
     * <p>注解层为按用户的粗粒度防刷，Service 层按 workspaceId 权威计数。
     * </p>
     */
    @PostMapping("/cancel")
    @RateLimited(key = "subscription:cancel:${userId}", limit = 50, window = 86400, dimension = "user")
    public ApiResponse<SubscriptionDtos.SubscriptionView> cancel(
            @PathVariable String workspaceId,
            @RequestBody(required = false) SubscriptionDtos.CancelRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = RequestContext.getUserIdOrNull();
        return ApiResponse.ok(subscriptionService.cancel(workspaceId, userId, request, idempotencyKey));
    }

    /**
     * 变更套餐（幂等；3 次/天/工作区）。
     * <p>
     * 升级立即生效并返回按比例补差价；降级周期末生效。
     * <p>注解层为按用户的粗粒度防刷，Service 层按 workspaceId 权威计数（3 次/天）。
     * </p>
     */
    @PostMapping("/change-plan")
    @RateLimited(key = "subscription:change_plan:${userId}", limit = 30, window = 86400, dimension = "user")
    public ApiResponse<SubscriptionDtos.ChangePlanResult> changePlan(
            @PathVariable String workspaceId,
            @RequestBody SubscriptionDtos.ChangePlanRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = RequestContext.getUserIdOrNull();
        return ApiResponse.ok(subscriptionService.changePlan(workspaceId, userId, request, idempotencyKey));
    }

    /**
     * 发起试用（幂等；1 次/工作区）。
     * <p>注解层为按用户的粗粒度防刷，Service 层按 workspaceId 权威计数（1 次/工作区）。
     * </p>
     */
    @PostMapping("/trial")
    @RateLimited(key = "subscription:trial:${userId}", limit = 10, window = 86400, dimension = "user")
    public ApiResponse<SubscriptionDtos.SubscriptionView> startTrial(
            @PathVariable String workspaceId,
            @RequestBody(required = false) SubscriptionDtos.TrialRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = RequestContext.getUserIdOrNull();
        return ApiResponse.ok(subscriptionService.startTrial(workspaceId, userId, request, idempotencyKey));
    }
}
