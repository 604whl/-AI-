package com.shortvideoscripagent.xhsagentyunying.billing.subscription;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.billing.plan.PlanService;
import com.shortvideoscripagent.xhsagentyunying.billing.plan.domain.entity.SubscriptionPlan;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.entity.Subscription;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.mapper.SubscriptionMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingMath;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingPeriod;
import com.shortvideoscripagent.xhsagentyunying.billing.support.OveragePolicy;
import com.shortvideoscripagent.xhsagentyunying.billing.support.PlanSnapshot;
import com.shortvideoscripagent.xhsagentyunying.billing.support.SubscriptionStatus;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Workspace;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.PlanDtos;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.SubscriptionDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.infra.ratelimit.RedisRateLimiter;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.WorkspaceQuota;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.WorkspaceQuotaMapper;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S4-1 订阅生命周期服务。
 * <p>
 * <b>状态机</b>：{@code trialing → active → past_due → (active | canceled)}，
 * 到期未续费 → {@code expired}（并降级到 FREE，<b>不删除任何数据</b>）。
 * </p>
 * <p>
 * <b>额度写入策略</b>：订阅生效（active / trialing）时把套餐包含额度写入
 * {@code app.workspace_quota} 的<b>周期额度</b>列（{@code plan_id / period_quota /
 * period_start / period_end / overage_policy}），<b>不改</b>原
 * {@code daily_quota / used / reset_at} 语义。
 * </p>
 * <p>
 * <b>幂等</b>：写接口的 {@code Idempotency-Key} 落在 {@code app.subscription.idempotency_key}
 * 上，配合唯一索引 {@code (workspace_id, idempotency_key)}——重复提交返回既有订阅。
 * 与项目既有 {@code ForecastService / PublishTaskService} 的幂等模式一致（DB 唯一索引兜底），
 * 不依赖 Redis，Redis 故障时仍能保证不重复开通。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final int CODE_NOT_FOUND = 40410;
    private static final int CODE_BAD_REQUEST = 40010;
    private static final int CODE_CONFLICT = 40910;
    private static final int CODE_FORBIDDEN = 40310;

    /** 试用默认天数 */
    public static final int DEFAULT_TRIAL_DAYS = 14;
    /** 试用天数上限 */
    public static final int MAX_TRIAL_DAYS = 30;
    /** D9：宽限期默认 7 天 */
    public static final int DEFAULT_GRACE_PERIOD_DAYS = 7;
    /** 到期降级目标套餐编码 */
    public static final String FREE_PLAN_CODE = "FREE";

    // ── 幂等作用域（P3-4 / P3-5）────────────────────────────────────────
    // 同一订阅行上会先后发生多种写操作，若共用一个幂等键空间，
    // 先到的操作会占住键空间导致后到的操作永远绑不上。
    // 这里按「操作种类」划分作用域，不同作用域互不抢占。
    /** 开通订阅 */
    public static final String SCOPE_SUBSCRIBE = "subscribe";
    /** 发起试用 */
    public static final String SCOPE_TRIAL = "trial";
    /** 取消订阅 */
    public static final String SCOPE_CANCEL = "cancel";
    /** 变更套餐 */
    public static final String SCOPE_CHANGE_PLAN = "change_plan";
    /** 管理端补偿 / 延期 */
    public static final String SCOPE_ADMIN_ADJUST = "admin_adjust";

    private static final int LIMIT_SUBSCRIBE_PER_DAY = 5;
    private static final int LIMIT_CHANGE_PLAN_PER_DAY = 3;
    private static final int WINDOW_ONE_DAY = 24 * 60 * 60;

    private final SubscriptionMapper subscriptionMapper;
    private final com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.mapper.SubscriptionWriteIdempotencyMapper
            writeIdempotencyMapper;
    private final WorkspaceQuotaMapper workspaceQuotaMapper;
    private final WorkspaceMapper workspaceMapper;
    private final PlanService planService;
    private final WorkspaceService workspaceService;
    private final AuditWriter auditWriter;
    private final RedisRateLimiter rateLimiter;
    private final com.shortvideoscripagent.xhsagentyunying.billing.support.IdempotencyReplay idempotencyReplay;

    // ══════════════════════════════════════════════════════════════════
    // 读
    // ══════════════════════════════════════════════════════════════════

    /**
     * 读工作区当前订阅。
     * <p>
     * OWNER / ADMIN 读全量；其他成员只读「当前可用能力」（隐藏价格与账期细节）。
     * 跨 workspace 访问一律按「未找到」处理（404），不泄露存在性。
     * </p>
     */
    public SubscriptionDtos.SubscriptionView getSubscription(String workspaceId, Long userId) {
        WorkspaceRole role = requireMember(workspaceId, userId);
        Subscription sub = subscriptionMapper.selectLatestByWorkspace(workspaceId);
        if (sub == null) {
            return null;
        }
        SubscriptionDtos.SubscriptionView view = toView(sub);
        if (role == null || !role.atLeast(WorkspaceRole.ADMIN)) {
            // 非 OWNER/ADMIN：只暴露「当前可用能力」
            view.setPrice(null);
            view.setCurrentPeriodStart(null);
            view.setCurrentPeriodEnd(null);
            view.setSubscribedAt(null);
            view.setCanceledAt(null);
        }
        return view;
    }

    /**
     * 管理端分页查询订阅。
     */
    public Page<PlanDtos.AdminSubscriptionItem> listForAdmin(String status, String workspaceId,
                                                              int page, int size) {
        LambdaQueryWrapper<Subscription> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(Subscription::getStatus, status.trim().toLowerCase());
        }
        if (workspaceId != null && !workspaceId.isBlank()) {
            q.eq(Subscription::getWorkspaceId, workspaceId.trim());
        }
        q.orderByDesc(Subscription::getUpdatedAt);

        Page<Subscription> p = subscriptionMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), q);
        Page<PlanDtos.AdminSubscriptionItem> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toAdminItem).toList());
        return result;
    }

    // ══════════════════════════════════════════════════════════════════
    // 写：订阅
    // ══════════════════════════════════════════════════════════════════

    /**
     * 开通订阅（幂等）。
     */
    @Transactional
    public SubscriptionDtos.SubscriptionView subscribe(String workspaceId, Long userId,
                                                       SubscriptionDtos.SubscribeRequest request,
                                                       String idempotencyKey) {
        requireOwner(workspaceId, userId);
        rateLimiter.require("workspace", workspaceId + ":subscription:create",
                LIMIT_SUBSCRIBE_PER_DAY, WINDOW_ONE_DAY);

        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            Subscription existing = subscriptionMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                return toView(existing);
            }
        }
        if (request == null || request.getPlanCode() == null || request.getPlanCode().isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "plan_code_required");
        }
        if (Boolean.TRUE.equals(request.getTrial())) {
            SubscriptionDtos.TrialRequest trial = new SubscriptionDtos.TrialRequest();
            trial.setPlanCode(request.getPlanCode());
            trial.setTrialDays(request.getTrialDays());
            return startTrial(workspaceId, userId, trial, idempotencyKey);
        }

        SubscriptionPlan plan = planService.requireActivePlanByCode(request.getPlanCode());

        // ── P0-1 付费套餐不得零元开通 ──
        // 付费套餐（PRO / TEAM）必须走「创建订单 → 渠道支付 → 回调履约」，
        // 否则任意 OWNER 一次 POST 就能拿到全量额度，收入侧完全敞开。
        // 这里直接拒绝并指向订单接口；FREE / USAGE（price = 0）可直接开通。
        BigDecimal planPrice = plan.getPrice() == null ? BigDecimal.ZERO : BillingMath.scale(plan.getPrice());
        if (planPrice.signum() > 0) {
            log.warn("Paid plan subscribe rejected (payment required): ws={} plan={} price={}",
                    workspaceId, plan.getCode(), planPrice);
            throw new BusinessException(CODE_CONFLICT, "payment_required_create_order");
        }

        // 已有生效订阅 → 冲突（幂等重放在上面已返回）
        Subscription live = subscriptionMapper.selectActiveByWorkspace(workspaceId);
        if (live != null) {
            throw new BusinessException(CODE_CONFLICT, "subscription_already_active");
        }

        OffsetDateTime now = OffsetDateTime.now();
        BillingPeriod period = BillingPeriod.parse(plan.getBillingPeriod());
        OffsetDateTime periodStart = now;
        OffsetDateTime periodEnd = now.plusMonths(period.getMonths());

        Subscription sub = new Subscription();
        // MyBatis-Plus 的 IdType.ASSIGN_UUID 只对 String 主键自动赋值；
        // UUID 主键必须手动生成（与本仓库其余实体保持一致），否则插入时 id 为 null 触发非空约束报错。
        sub.setId(UUID.randomUUID());
        sub.setWorkspaceId(workspaceId);
        sub.setPlanId(plan.getId());
        sub.setPlanVersionSnapshot(PlanSnapshot.of(plan));
        sub.setStatus(SubscriptionStatus.ACTIVE.getValue());
        sub.setCurrentPeriodStart(periodStart);
        sub.setCurrentPeriodEnd(periodEnd);
        sub.setTrialEnd(null);
        sub.setCancelAtPeriodEnd(false);
        sub.setPendingPlanId(null);
        sub.setAutoRenew(request.getAutoRenew() == null || request.getAutoRenew());
        sub.setGracePeriodDays(DEFAULT_GRACE_PERIOD_DAYS);
        sub.setSubscribedAt(now);
        sub.setIdempotencyKey(key);
        sub.setCreatedAt(now);
        sub.setUpdatedAt(now);

        try {
            subscriptionMapper.insert(sub);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 唯一冲突后当前事务已被 PostgreSQL 置为 aborted，
            // 回读必须放新事务，否则抛 "current transaction is aborted" -> 500。
            if (key != null) {
                Subscription existing = idempotencyReplay.read(
                        () -> subscriptionMapper.selectByIdempotencyKey(workspaceId, key));
                if (existing != null) {
                    return toView(existing);
                }
            }
            throw new BusinessException(CODE_CONFLICT, "subscription_already_active");
        }

        applyPlanQuota(workspaceId, plan, periodStart, periodEnd);
        syncWorkspacePlanState(workspaceId, sub);

        auditWriter.record("SUBSCRIPTION_CREATED", "SUBSCRIPTION", sub.getId().toString(),
                Map.of("workspaceId", workspaceId,
                        "planCode", plan.getCode(),
                        "planVersion", String.valueOf(plan.getVersion()),
                        "price", plan.getPrice() == null ? "0" : plan.getPrice().toPlainString(),
                        "periodEnd", String.valueOf(periodEnd)));

        log.info("Subscription created: ws={} plan={} v{} end={}",
                workspaceId, plan.getCode(), plan.getVersion(), periodEnd);
        return toView(sub);
    }

    /**
     * 发起试用（同一 workspace 只能试用一次）。
     */
    @Transactional
    public SubscriptionDtos.SubscriptionView startTrial(String workspaceId, Long userId,
                                                         SubscriptionDtos.TrialRequest request,
                                                         String idempotencyKey) {
        requireOwner(workspaceId, userId);
        rateLimiter.require("workspace", workspaceId + ":subscription:trial", 1, WINDOW_ONE_DAY);

        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            Subscription existing = subscriptionMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                return toView(existing);
            }
        }
        // 同一 workspace 只能试用一次（trial_end IS NOT NULL 即视为已用过）
        if (subscriptionMapper.selectTrialUsedByWorkspace(workspaceId) != null) {
            throw new BusinessException(CODE_CONFLICT, "trial_already_used");
        }
        Subscription live = subscriptionMapper.selectActiveByWorkspace(workspaceId);
        if (live != null) {
            throw new BusinessException(CODE_CONFLICT, "subscription_already_active");
        }

        String planCode = request == null || request.getPlanCode() == null || request.getPlanCode().isBlank()
                ? "PRO" : request.getPlanCode().trim().toUpperCase();
        SubscriptionPlan plan = planService.requireActivePlanByCode(planCode);

        int trialDays = request == null || request.getTrialDays() == null
                ? DEFAULT_TRIAL_DAYS : Math.min(Math.max(request.getTrialDays(), 1), MAX_TRIAL_DAYS);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime trialEnd = now.plusDays(trialDays);

        Subscription sub = new Subscription();
        // UUID 主键必须手动生成（见上方 subscribe 方法说明）
        sub.setId(UUID.randomUUID());
        sub.setWorkspaceId(workspaceId);
        sub.setPlanId(plan.getId());
        sub.setPlanVersionSnapshot(PlanSnapshot.of(plan));
        sub.setStatus(SubscriptionStatus.TRIALING.getValue());
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd(trialEnd);
        sub.setTrialEnd(trialEnd);
        sub.setCancelAtPeriodEnd(false);
        sub.setPendingPlanId(null);
        sub.setAutoRenew(true);
        sub.setGracePeriodDays(DEFAULT_GRACE_PERIOD_DAYS);
        sub.setSubscribedAt(now);
        sub.setIdempotencyKey(key);
        sub.setCreatedAt(now);
        sub.setUpdatedAt(now);

        try {
            subscriptionMapper.insert(sub);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 唯一冲突后当前事务已被 PostgreSQL 置为 aborted，回读必须放新事务。
            if (key != null) {
                Subscription existing = idempotencyReplay.read(
                        () -> subscriptionMapper.selectByIdempotencyKey(workspaceId, key));
                if (existing != null) {
                    return toView(existing);
                }
            }
            throw new BusinessException(CODE_CONFLICT, "trial_already_used");
        }

        // 试用额度 = 套餐包含额度（included_units）
        applyPlanQuota(workspaceId, plan, now, trialEnd);
        syncWorkspacePlanState(workspaceId, sub);

        auditWriter.record("SUBSCRIPTION_TRIAL_STARTED", "SUBSCRIPTION", sub.getId().toString(),
                Map.of("workspaceId", workspaceId,
                        "planCode", plan.getCode(),
                        "trialDays", String.valueOf(trialDays),
                        "trialEnd", String.valueOf(trialEnd)));

        log.info("Subscription trial started: ws={} plan={} trialEnd={}", workspaceId, plan.getCode(), trialEnd);
        return toView(sub);
    }

    /**
     * 取消订阅。
     * <p>
     * 默认在周期末取消（{@code cancel_at_period_end = true}，功能在周期内保留）；
     * 立即取消时直接把状态置 {@code canceled} 并降级到 FREE（不删数据）。
     * </p>
     */
    @Transactional
    public SubscriptionDtos.SubscriptionView cancel(String workspaceId, Long userId,
                                                    SubscriptionDtos.CancelRequest request,
                                                    String idempotencyKey) {
        requireOwner(workspaceId, userId);
        Subscription sub = subscriptionMapper.selectActiveByWorkspace(workspaceId);
        if (sub == null) {
            // 已经没有生效订阅：按「未找到」处理
            throw new BusinessException(CODE_NOT_FOUND, "subscription_not_found");
        }
        // P3-4：cancel 是「修改已有订阅」，幂等键要落在独立的
        // subscription_write_idempotency 表（scope = cancel），
        // 而不是抢 subscribe 写在 subscription.idempotency_key 上的那个键。
        // 返回 0 = 该键此前已绑定 -> 幂等重放，直接返回当前订阅视图，
        // 绝不能再次执行「取消 / 降级额度」这些副作用。
        if (bindIdempotencyKey(workspaceId, sub.getId(), SCOPE_CANCEL, idempotencyKey) == 0) {
            return toView(subscriptionMapper.selectById(sub.getId()));
        }
        boolean atPeriodEnd = request == null || request.getAtPeriodEnd() == null || request.getAtPeriodEnd();

        if (atPeriodEnd) {
            sub.setCancelAtPeriodEnd(true);
            sub.setAutoRenew(false);
            sub.setUpdatedAt(OffsetDateTime.now());
            subscriptionMapper.updateById(sub);
            syncWorkspacePlanState(workspaceId, sub);
            auditWriter.record("SUBSCRIPTION_CANCEL_SCHEDULED", "SUBSCRIPTION", sub.getId().toString(),
                    Map.of("workspaceId", workspaceId,
                            "atPeriodEnd", "true",
                            "reason", request == null || request.getReason() == null ? "" : request.getReason()));
            return toView(sub);
        }

        int updated = subscriptionMapper.compareAndSetStatus(sub.getId(),
                sub.getStatus(), SubscriptionStatus.CANCELED.getValue());
        if (updated == 0) {
            throw new BusinessException(CODE_CONFLICT, "subscription_state_conflict");
        }
        Subscription canceled = subscriptionMapper.selectById(sub.getId());
        canceled.setCanceledAt(OffsetDateTime.now());
        canceled.setAutoRenew(false);
        canceled.setCancelAtPeriodEnd(false);
        canceled.setUpdatedAt(OffsetDateTime.now());
        subscriptionMapper.updateById(canceled);

        downgradeToFree(workspaceId, canceled);
        syncWorkspacePlanState(workspaceId, canceled);

        auditWriter.record("SUBSCRIPTION_CANCELED", "SUBSCRIPTION", canceled.getId().toString(),
                Map.of("workspaceId", workspaceId,
                        "atPeriodEnd", "false",
                        "reason", request == null || request.getReason() == null ? "" : request.getReason()));
        return toView(canceled);
    }

    /**
     * 因订单退款而取消订阅（管理端退款链路调用，不走用户侧限流）。
     * <p>
     * P1-3：退款后必须收回权益，否则用户退了钱仍享受 PRO 额度。
     * 不删除工作区数据，只把订阅置 {@code canceled} 并降级额度到 FREE。
     * </p>
     *
     * @return true = 本次执行了取消
     */
    @Transactional
    public boolean cancelForRefund(String workspaceId) {
        Subscription sub = subscriptionMapper.selectActiveByWorkspace(workspaceId);
        if (sub == null) {
            log.debug("No active subscription to cancel for refund: ws={}", workspaceId);
            return false;
        }
        int updated = subscriptionMapper.compareAndSetCanceled(sub.getId());
        if (updated == 0) {
            log.info("Subscription already terminal, skip refund cancel: ws={} id={}",
                    workspaceId, sub.getId());
            return false;
        }
        Subscription canceled = subscriptionMapper.selectById(sub.getId());
        canceled.setCanceledAt(OffsetDateTime.now());
        canceled.setAutoRenew(false);
        canceled.setCancelAtPeriodEnd(false);
        canceled.setUpdatedAt(OffsetDateTime.now());
        subscriptionMapper.updateById(canceled);

        downgradeToFree(workspaceId, canceled);
        syncWorkspacePlanState(workspaceId, canceled);

        auditWriter.record("SUBSCRIPTION_CANCELED", "SUBSCRIPTION", canceled.getId().toString(),
                Map.of("workspaceId", workspaceId,
                        "atPeriodEnd", "false",
                        "reason", "order_refund"));
        return true;
    }

    /**
     * 变更套餐。
     * <ul>
     *   <li><b>升级</b>（目标价更高）：立即生效，并按本期剩余天数比例算出补差价返回前端展示 + 写审计。
     *       （S4-2 才有订单，差价金额此处只计算 + 记录，不落订单。）</li>
     *   <li><b>降级</b>：周期末生效（{@code cancel_at_period_end = true} + {@code pending_plan_id}）。</li>
     * </ul>
     */
    @Transactional
    public SubscriptionDtos.ChangePlanResult changePlan(String workspaceId, Long userId,
                                                         SubscriptionDtos.ChangePlanRequest request,
                                                         String idempotencyKey) {
        requireOwner(workspaceId, userId);
        rateLimiter.require("workspace", workspaceId + ":subscription:change_plan",
                LIMIT_CHANGE_PLAN_PER_DAY, WINDOW_ONE_DAY);

        if (request == null || request.getPlanCode() == null || request.getPlanCode().isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "plan_code_required");
        }
        Subscription sub = subscriptionMapper.selectActiveByWorkspace(workspaceId);
        if (sub == null) {
            throw new BusinessException(CODE_NOT_FOUND, "subscription_not_found");
        }
        // P3-4：change-plan 与 cancel 一样共用 scope 化的幂等键空间，
        // 两者的幂等键互不抢占。返回 0 = 幂等重放，直接返回当前订阅视图，
        // 不再执行「切套餐 / 写额度」这些副作用。
        if (bindIdempotencyKey(workspaceId, sub.getId(), SCOPE_CHANGE_PLAN, idempotencyKey) == 0) {
            SubscriptionDtos.ChangePlanResult replay = new SubscriptionDtos.ChangePlanResult();
            replay.setEffective("replay");
            replay.setSubscription(toView(subscriptionMapper.selectById(sub.getId())));
            return replay;
        }
        SubscriptionPlan target = planService.requireActivePlanByCode(request.getPlanCode());
        if (target.getId().equals(sub.getPlanId())) {
            throw new BusinessException(CODE_CONFLICT, "plan_unchanged");
        }

        BigDecimal currentPrice = priceOfSnapshot(sub);
        BigDecimal targetPrice = BillingMath.scale(target.getPrice());
        Map<String, Object> currentUnits = PlanSnapshot.includedUnitsOf(sub);
        Map<String, Object> targetUnits = target.getIncludedUnits() == null ? Map.of() : target.getIncludedUnits();

        String fromPlanCode = PlanSnapshot.codeOf(sub);
        SubscriptionDtos.ChangePlanResult result = new SubscriptionDtos.ChangePlanResult();
        result.setFromPlanCode(fromPlanCode);
        result.setToPlanCode(target.getCode());
        result.setCurrency(target.getCurrency() == null ? "CNY" : target.getCurrency());

        if (targetPrice.compareTo(currentPrice) > 0) {
            // ── 升级：立即生效 + 补差价 ──
            // P1-2：补差价必须真的收钱。早期实现只算出 prorated 金额回给前端，
            // 既不落订单也不扣款，升级收入 100% 漏收。
            // 这里与 subscribe 保持同一口径：付费升级必须走订单 + 支付回调履约，
            // 因此直接拒绝并指向订单接口。
            BigDecimal prorated = BillingMath.proratedUpgradeAmount(
                    currentPrice, targetPrice, sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd());
            BigDecimal quotaDelta = BillingMath.proratedQuotaDelta(
                    currentUnits, targetUnits, sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd());

            if (prorated.signum() > 0) {
                log.warn("Paid upgrade rejected (payment required): ws={} {} -> {} prorated={}",
                        workspaceId, fromPlanCode, target.getCode(), prorated);
                result.setEffective("payment_required");
                result.setProratedAmount(prorated);
                result.setProratedQuotaDelta(quotaDelta);
                result.setSubscription(toView(sub));
                throw new BusinessException(CODE_CONFLICT, "payment_required_create_order");
            }

            sub.setPlanId(target.getId());
            sub.setPlanVersionSnapshot(PlanSnapshot.of(target));
            sub.setUpdatedAt(OffsetDateTime.now());
            subscriptionMapper.updateById(sub);

            // P3-2：升级后额度必须按「剩余周期」折算，不能把目标套餐的全额额度一次性发放。
            // 早期实现直接 applyPlanQuota(target) -> period_quota = 目标套餐 includedUnits 全额，
            // 结果月中升级的用户在剩余半周期就拿到了整月额度，额度成本失控。
            // 口径：period_quota = 已消耗（当前套餐额度 × 剩余周期占比）+ 目标套餐额度 × 剩余周期占比
            //      = 目标套餐额度 × 剩余周期占比（向上取整，最小 1），且不小于已用掉的部分。
            applyPlanQuotaProrated(workspaceId, target, sub.getCurrentPeriodStart(),
                    sub.getCurrentPeriodEnd(), currentUnits, targetUnits);

            syncWorkspacePlanState(workspaceId, sub);

            result.setEffective("immediate");
            result.setProratedAmount(prorated);
            result.setProratedQuotaDelta(quotaDelta);
            result.setSubscription(toView(sub));

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("workspaceId", workspaceId);
            meta.put("fromPlanCode", fromPlanCode);
            meta.put("toPlanCode", target.getCode());
            meta.put("proratedAmount", prorated.toPlainString());
            meta.put("currency", result.getCurrency());
            meta.put("effective", "immediate");
            auditWriter.record("SUBSCRIPTION_PLAN_UPGRADED", "SUBSCRIPTION", sub.getId().toString(), meta);
            log.info("Subscription upgraded: ws={} {} -> {} prorated={}",
                    workspaceId, result.getFromPlanCode(), target.getCode(), prorated);
        } else {
            // ── 降级：周期末生效 ──
            BigDecimal quotaDelta = BillingMath.proratedQuotaDelta(
                    currentUnits, targetUnits, sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd());

            sub.setCancelAtPeriodEnd(true);
            sub.setPendingPlanId(target.getId());
            sub.setUpdatedAt(OffsetDateTime.now());
            subscriptionMapper.updateById(sub);
            syncWorkspacePlanState(workspaceId, sub);

            result.setEffective("period_end");
            result.setProratedAmount(BigDecimal.ZERO.setScale(BillingMath.SCALE, java.math.RoundingMode.HALF_UP));
            result.setProratedQuotaDelta(quotaDelta);
            result.setSubscription(toView(sub));

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("workspaceId", workspaceId);
            meta.put("fromPlanCode", fromPlanCode);
            meta.put("toPlanCode", target.getCode());
            meta.put("effective", "period_end");
            auditWriter.record("SUBSCRIPTION_PLAN_DOWNGRADE_SCHEDULED", "SUBSCRIPTION", sub.getId().toString(), meta);
            log.info("Subscription downgrade scheduled: ws={} {} -> {}",
                    workspaceId, result.getFromPlanCode(), target.getCode());
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════
    // 写：管理端
    // ══════════════════════════════════════════════════════════════════

    /**
     * 管理端补偿 / 延期。
     */
    @Transactional
    public PlanDtos.AdminSubscriptionItem adminAdjust(UUID subscriptionId,
                                                       SubscriptionDtos.AdminAdjustRequest request,
                                                       String idempotencyKey) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "reason_required");
        }
        Subscription sub = subscriptionMapper.selectById(subscriptionId);
        if (sub == null) {
            throw new BusinessException(CODE_NOT_FOUND, "subscription_not_found");
        }
        // P3-5：管理端补偿必须幂等。
        // extendDays 是以「当前 current_period_end」为基准累加的，
        // 重复提交同一个 Idempotency-Key 会把延期天数叠加两次，误操作不可撤销。
        // 这里用 scope = admin_adjust 的独立幂等键空间：
        // 返回 0 = 幂等重放 -> 直接返回当前订阅视图，不再叠加延期天数。
        if (bindIdempotencyKey(sub.getWorkspaceId(), sub.getId(),
                SCOPE_ADMIN_ADJUST, idempotencyKey) == 0) {
            log.info("Admin adjust replay (skipped): sub={} ws={}",
                    subscriptionId, sub.getWorkspaceId());
            return toAdminItem(subscriptionMapper.selectById(subscriptionId));
        }
        if (request.getExtendDays() != null && request.getExtendDays() != 0) {
            OffsetDateTime base = sub.getCurrentPeriodEnd() == null
                    ? OffsetDateTime.now() : sub.getCurrentPeriodEnd();
            sub.setCurrentPeriodEnd(base.plusDays(request.getExtendDays()));
        }
        if (request.getGracePeriodDays() != null) {
            sub.setGracePeriodDays(Math.max(request.getGracePeriodDays(), 0));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim().toLowerCase();
            if (SubscriptionStatus.parse(status) == null) {
                throw new BusinessException(CODE_BAD_REQUEST, "invalid_subscription_status");
            }
            sub.setStatus(status);
        }
        sub.setUpdatedAt(OffsetDateTime.now());
        subscriptionMapper.updateById(sub);
        syncWorkspacePlanState(sub.getWorkspaceId(), sub);

        auditWriter.record("SUBSCRIPTION_ADMIN_ADJUSTED", "SUBSCRIPTION", sub.getId().toString(),
                Map.of("workspaceId", sub.getWorkspaceId(),
                        "reason", request.getReason(),
                        "status", String.valueOf(sub.getStatus()),
                        "extendDays", String.valueOf(request.getExtendDays() == null ? 0 : request.getExtendDays())));
        return toAdminItem(sub);
    }

    // ══════════════════════════════════════════════════════════════════
    // 额度绑定与降级（供订阅服务与到期任务共用）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 把套餐包含额度写入 workspace_quota 的周期额度列。
     * <p>
     * <b>不改</b> daily_quota / used / reset_at 语义。
     * </p>
     */
    public void applyPlanQuota(String workspaceId, SubscriptionPlan plan,
                               OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        int periodQuota = BillingMath.sumIncludedUnits(
                plan.getIncludedUnits() == null ? Map.of() : plan.getIncludedUnits());
        writePlanQuota(workspaceId, plan, periodStart, periodEnd, periodQuota);
    }

    /**
     * <b>P3-2</b>：按「剩余周期比例」折算后写入周期额度（用于月中升级）。
     * <p>
     * <b>口径</b>：
     * <pre>
     *   剩余周期占比 r = 剩余天数 / 本周期总天数        (0 &lt; r &lt;= 1)
     *   目标周期额度   = ceil(目标套餐全额额度 × r)
     * </pre>
     * 升级后用户在剩余周期内最多只能用到折算后的额度，
     * 因此额度成本与「实际付费周期」匹配，不会月中升级就白送整月额度。
     * <p>
     * <b>下界保护</b>：折算结果至少为 1，避免剩余周期极短时额度变成 0 导致用户完全不可用；
     * 同时不超过目标套餐全额（r ≤ 1）。
     * <p>
     * <b>不改</b> daily_quota / used / reset_at 语义。
     * </p>
     */
    private void applyPlanQuotaProrated(String workspaceId, SubscriptionPlan targetPlan,
                                        OffsetDateTime periodStart, OffsetDateTime periodEnd,
                                        Map<String, Object> currentUnits,
                                        Map<String, Object> targetUnits) {
        int fullTarget = BillingMath.sumIncludedUnits(
                targetUnits == null ? Map.of() : targetUnits);
        if (fullTarget <= 0) {
            writePlanQuota(workspaceId, targetPlan, periodStart, periodEnd, 0);
            return;
        }
        // proratedQuotaDelta 在「剩余天数为 0」时返回 0，无法表达占比；
        // 这里直接用剩余天数 / 总天数自己算占比，避免依赖 delta 的符号语义。
        BigDecimal ratio = remainingPeriodRatio(periodStart, periodEnd);
        int prorated = BigDecimal.valueOf(fullTarget)
                .multiply(ratio)
                .setScale(0, java.math.RoundingMode.CEILING)
                .intValue();
        if (prorated < 1) {
            prorated = 1;
        }
        if (prorated > fullTarget) {
            prorated = fullTarget;
        }
        log.info("Prorated quota applied: ws={} plan={} full={} ratio={} prorated={}",
                workspaceId, targetPlan == null ? null : targetPlan.getCode(), fullTarget, ratio, prorated);
        writePlanQuota(workspaceId, targetPlan, periodStart, periodEnd, prorated);
    }

    /**
     * 剩余周期占比（0 &lt; r ≤ 1）。
     * <p>
     * 周期非法（结束不晚于开始）时返回 1（视为完整周期）。
     * </p>
     */
    private static BigDecimal remainingPeriodRatio(OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        if (periodStart == null || periodEnd == null || !periodEnd.isAfter(periodStart)) {
            return BigDecimal.ONE;
        }
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd);
        if (totalDays <= 0) {
            return BigDecimal.ONE;
        }
        long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(OffsetDateTime.now(), periodEnd);
        if (remainingDays < 0) {
            remainingDays = 0;
        }
        if (remainingDays > totalDays) {
            remainingDays = totalDays;
        }
        return BigDecimal.valueOf(remainingDays)
                .divide(BigDecimal.valueOf(totalDays), 6, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 写入周期额度（懒创建 workspace_quota 行）。
     */
    private void writePlanQuota(String workspaceId, SubscriptionPlan plan,
                                OffsetDateTime periodStart, OffsetDateTime periodEnd,
                                int periodQuota) {
        String planId = plan == null || plan.getId() == null ? null : plan.getId().toString();
        String overagePolicy = OveragePolicy.REJECT.getValue();

        WorkspaceQuota quota = workspaceQuotaMapper.selectById(workspaceId);
        if (quota == null) {
            // 懒创建（不改动既有 daily_quota 语义，仅补齐新列）
            WorkspaceQuota created = new WorkspaceQuota();
            created.setWorkspaceId(workspaceId);
            created.setDailyQuota(100);
            created.setUsed(0);
            created.setResetAt(OffsetDateTime.now().plusDays(1));
            created.setPlanId(planId);
            created.setPeriodQuota(periodQuota);
            created.setPeriodStart(periodStart);
            created.setPeriodEnd(periodEnd);
            created.setOveragePolicy(overagePolicy);
            created.setCreatedAt(OffsetDateTime.now());
            created.setUpdatedAt(OffsetDateTime.now());
            try {
                workspaceQuotaMapper.insert(created);
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                workspaceQuotaMapper.applySubscriptionQuota(workspaceId, planId, periodQuota,
                        periodStart, periodEnd, overagePolicy);
            }
            return;
        }
        workspaceQuotaMapper.applySubscriptionQuota(workspaceId, planId, periodQuota,
                periodStart, periodEnd, overagePolicy);
    }

    /**
     * 降级到 FREE 套餐（<b>不删除任何数据</b>，只收窄功能与额度）。
     * <p>
     * <b>幂等</b>：只有状态流转成功的调用方才执行额度重写；
     * 额度写入用 {@code plan_id} 作为乐观锁条件，重复执行不会重复写。
     * </p>
     *
     * @return true 表示本次调用真正执行了降级
     */
    public boolean downgradeToFree(String workspaceId, Subscription subscription) {
        SubscriptionPlan free = null;
        try {
            free = planService.requireActivePlanByCode(FREE_PLAN_CODE);
        } catch (BusinessException ex) {
            log.warn("FREE plan not found, skip quota downgrade for ws={}", workspaceId);
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        String expectedPlanId = subscription.getPlanId() == null ? null : subscription.getPlanId().toString();
        String newPlanId = free.getId() == null ? null : free.getId().toString();
        int periodQuota = BillingMath.sumIncludedUnits(
                free.getIncludedUnits() == null ? Map.of() : free.getIncludedUnits());

        int affected = workspaceQuotaMapper.applyDowngradeQuota(workspaceId, expectedPlanId, newPlanId,
                periodQuota, now, now.plusMonths(BillingPeriod.MONTH.getMonths()),
                OveragePolicy.REJECT.getValue());
        if (affected == 0) {
            // 额度已经是别的套餐（或已降级过）→ 幂等跳过
            log.debug("Quota downgrade skipped (already applied): ws={} expectedPlan={}", workspaceId, expectedPlanId);
            return false;
        }
        auditWriter.record("SUBSCRIPTION_QUOTA_DOWNGRADED", "SUBSCRIPTION",
                subscription.getId() == null ? workspaceId : subscription.getId().toString(),
                Map.of("workspaceId", workspaceId,
                        "targetPlan", FREE_PLAN_CODE,
                        "periodQuota", String.valueOf(periodQuota)));
        log.info("Subscription quota downgraded to FREE: ws={} periodQuota={}", workspaceId, periodQuota);
        return true;
    }

    /**
     * 在周期末把降级目标套餐（{@code pending_plan_id}）落地。
     * <p>
     * P1-4：早期实现把 {@code pending_plan_id} 写进去就再没人读，
     * 「降级到某套餐（周期末生效）」永远是死数据。这里补齐落地逻辑：
     * <ol>
     *   <li>切 {@code plan_id} + 冻结新的套餐快照；</li>
     *   <li>按新套餐写额度（{@code applyPlanQuota}）；</li>
     *   <li>周期顺延一个计费周期（从原周期末继续）；</li>
     *   <li>清空 {@code pending_plan_id} 与 {@code cancel_at_period_end}。</li>
     * </ol>
     * <p>
     * <b>幂等</b>：{@code applyPlanQuota} 以 {@code plan_id} 作为乐观锁条件，
     * 重复执行不会重复写额度；{@code pending_plan_id} 清空后第二次执行直接返回。
     * </p>
     *
     * @return true = 本次完成了落地
     */
    @Transactional
    public boolean applyPendingPlanAtPeriodEnd(Subscription subscription, UUID targetPlanId) {
        if (subscription == null || targetPlanId == null || subscription.getId() == null) {
            return false;
        }
        SubscriptionPlan target = planService.requirePlan(targetPlanId);
        OffsetDateTime now = OffsetDateTime.now();
        BillingPeriod period = BillingPeriod.parse(target.getBillingPeriod());

        OffsetDateTime periodStart = subscription.getCurrentPeriodEnd() != null
                ? subscription.getCurrentPeriodEnd() : now;
        OffsetDateTime periodEnd = periodStart.plusMonths(period.getMonths());

        subscription.setPlanId(target.getId());
        subscription.setPlanVersionSnapshot(PlanSnapshot.of(target));
        subscription.setCurrentPeriodStart(periodStart);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setPendingPlanId(null);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setUpdatedAt(now);
        subscriptionMapper.updateById(subscription);

        applyPlanQuota(subscription.getWorkspaceId(), target, periodStart, periodEnd);
        syncWorkspacePlanState(subscription.getWorkspaceId(), subscription);
        return true;
    }

    /**
     * 把写操作幂等键绑定到订阅行（用于 cancel / change-plan 这类「修改已有订阅」的操作）。
     * <p>
     * 语义：
     * <ul>
     *   <li>key 为空 → 不绑定（不提供幂等键时不做重放判定）；</li>
     *   <li>该行已经绑定了<b>同一个</b> key → 说明本次是重放，直接返回，调用方应识别为幂等命中；</li>
     *   <li>该行已经绑定了<b>其他</b> key → 说明上一次写操作用了别的键，本次是新操作，允许覆盖绑定；</li>
     *   <li>该行还没绑定 → 绑定本次 key（唯一索引冲突时视为并发重放）。</li>
     * </ul>
     * 绑定失败（唯一索引冲突）说明同一 workspace 下该 key 已被别的订阅行使用，
     * 这在业务上等价于「这次请求已经处理过」，因此静默忽略而不是抛错。
     * </p>
     */
    /**
     * <b>P3-4 / P3-5</b>：绑定写操作幂等键（cancel / change-plan / admin-adjust 这类
     * 「修改已有订阅」的操作）。
     * <p>
     * <b>为什么不用 {@code app.subscription.idempotency_key} 那一列</b>：
     * 订阅行上会先后发生多种写操作（subscribe / trial / cancel / change-plan / admin-adjust）。
     * 早期实现把<b>原始键</b>写进那一列，SQL 守卫是 {@code idempotency_key IS NULL} ——
     * 于是先到的操作占住这一列，后到的操作永远绑不上：
     * <ul>
     *   <li>{@code subscribe} 写入 K1 后，{@code cancel} 想绑 K2 时 UPDATE 影响 0 行，
     *       K2 <b>从未落库</b> -&gt; 同一 K2 重复提交不会被识别为重放；</li>
     *   <li>K1 也因此在 cancel 之后<b>彻底丢失</b>。</li>
     * </ul>
     * 现在改为独立的 {@code app.subscription_write_idempotency} 表，
     * 主键 {@code (workspace_id, scope, idempotency_key)}，
     * {@code scope} 区分操作种类，不同 scope 的同一个原始键互不抢占。
     * <p>
     * <b>写入语义</b>：{@code INSERT ... ON CONFLICT DO NOTHING}，
     * 返回 1 = 本次首次绑定（调用方应真正执行）；
     * 返回 0 = 该键此前已绑定（<b>幂等重放</b>，调用方必须跳过副作用直接返回既有结果）。
     * <p>
     * 用 {@code ON CONFLICT} 而不是 catch {@code DuplicateKeyException}：
     * PostgreSQL 中唯一冲突会把当前事务置为 aborted，
     * 之后任何 SQL 都会抛 {@code current transaction is aborted}，
     * 导致后续的回读 SELECT 也失败（与 V65 dedupe_key 同一教训）。
     *
     * @return 1 = 首次绑定（真正执行）；0 = 幂等重放（跳过副作用）
     */
    private int bindIdempotencyKey(String workspaceId, UUID subscriptionId,
                                   String scope, String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            // 没有幂等键：不提供幂等保护，按「首次执行」处理
            return 1;
        }
        String key = normalizeKey(rawKey);
        int inserted = writeIdempotencyMapper.insertIdempotent(workspaceId, scope, key, subscriptionId);
        if (inserted == 0) {
            log.info("Idempotency replay (already bound): ws={} scope={} key={}",
                    workspaceId, scope, key);
        }
        return inserted;
    }

    /**
     * 同步 workspace 的冗余列（subscription_status / plan_code）。
     */
    public void syncWorkspacePlanState(String workspaceId, Subscription subscription) {
        Workspace ws = workspaceMapper.selectById(workspaceId);
        if (ws == null) {
            return;
        }
        String status = subscription == null ? null : subscription.getStatus();
        String planCode = subscription == null ? null : PlanSnapshot.codeOf(subscription);
        if (java.util.Objects.equals(ws.getSubscriptionStatus(), status)
                && java.util.Objects.equals(ws.getPlanCode(), planCode)) {
            return;
        }
        Workspace update = new Workspace();
        update.setId(workspaceId);
        update.setSubscriptionStatus(status);
        update.setPlanCode(planCode);
        update.setUpdatedAt(OffsetDateTime.now());
        workspaceMapper.updateById(update);
    }

    // ══════════════════════════════════════════════════════════════════
    // 视图转换
    // ══════════════════════════════════════════════════════════════════

    public SubscriptionDtos.SubscriptionView toView(Subscription sub) {
        if (sub == null) {
            return null;
        }
        SubscriptionDtos.SubscriptionView v = new SubscriptionDtos.SubscriptionView();
        v.setId(sub.getId() == null ? null : sub.getId().toString());
        v.setWorkspaceId(sub.getWorkspaceId());
        v.setPlanId(sub.getPlanId() == null ? null : sub.getPlanId().toString());
        v.setPlanCode(PlanSnapshot.codeOf(sub));
        v.setPlanName(PlanSnapshot.nameOf(sub));
        Object version = sub.getPlanVersionSnapshot() == null ? null
                : sub.getPlanVersionSnapshot().get("version");
        v.setPlanVersion(version instanceof Number n ? n.intValue() : null);
        Object billingPeriod = sub.getPlanVersionSnapshot() == null ? null
                : sub.getPlanVersionSnapshot().get("billingPeriod");
        v.setBillingPeriod(billingPeriod == null ? null : String.valueOf(billingPeriod));
        v.setPrice(priceOfSnapshot(sub));
        Object currency = sub.getPlanVersionSnapshot() == null ? null
                : sub.getPlanVersionSnapshot().get("currency");
        v.setCurrency(currency == null ? "CNY" : String.valueOf(currency));
        v.setIncludedUnits(PlanSnapshot.includedUnitsOf(sub));
        v.setFeatureFlags(PlanSnapshot.featureFlagsOf(sub));
        v.setStatus(sub.getStatus());
        v.setCurrentPeriodStart(sub.getCurrentPeriodStart());
        v.setCurrentPeriodEnd(sub.getCurrentPeriodEnd());
        v.setTrialEnd(sub.getTrialEnd());
        v.setCancelAtPeriodEnd(sub.getCancelAtPeriodEnd());
        v.setAutoRenew(sub.getAutoRenew());
        v.setGracePeriodDays(sub.getGracePeriodDays());
        v.setSubscribedAt(sub.getSubscribedAt());
        v.setCanceledAt(sub.getCanceledAt());

        if (sub.getPendingPlanId() != null) {
            SubscriptionPlan pending = planService.requirePlan(sub.getPendingPlanId());
            v.setPendingPlanCode(pending.getCode());
        }
        WorkspaceQuota quota = workspaceQuotaMapper.selectById(sub.getWorkspaceId());
        if (quota != null) {
            v.setPeriodQuota(quota.getPeriodQuota());
            v.setOveragePolicy(quota.getOveragePolicy());
        }
        return v;
    }

    private PlanDtos.AdminSubscriptionItem toAdminItem(Subscription sub) {
        PlanDtos.AdminSubscriptionItem item = new PlanDtos.AdminSubscriptionItem();
        item.setId(sub.getId() == null ? null : sub.getId().toString());
        item.setWorkspaceId(sub.getWorkspaceId());
        item.setPlanCode(PlanSnapshot.codeOf(sub));
        item.setPlanName(PlanSnapshot.nameOf(sub));
        item.setStatus(sub.getStatus());
        item.setCurrentPeriodStart(sub.getCurrentPeriodStart());
        item.setCurrentPeriodEnd(sub.getCurrentPeriodEnd());
        item.setTrialEnd(sub.getTrialEnd());
        item.setCancelAtPeriodEnd(sub.getCancelAtPeriodEnd());
        item.setAutoRenew(sub.getAutoRenew());
        item.setGracePeriodDays(sub.getGracePeriodDays());
        item.setPrice(priceOfSnapshot(sub));
        Object currency = sub.getPlanVersionSnapshot() == null ? null
                : sub.getPlanVersionSnapshot().get("currency");
        item.setCurrency(currency == null ? "CNY" : String.valueOf(currency));
        return item;
    }

    // ══════════════════════════════════════════════════════════════════
    // private
    // ══════════════════════════════════════════════════════════════════

    /**
     * 校验当前用户是 workspace 成员；不是成员一律按「未找到」返回 404（不泄露存在性）。
     */
    private WorkspaceRole requireMember(String workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(CODE_FORBIDDEN, "unauthorized");
        }
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_NOT_FOUND, "subscription_not_found");
        }
        return role;
    }

    private void requireOwner(String workspaceId, Long userId) {
        WorkspaceRole role = requireMember(workspaceId, userId);
        if (role != WorkspaceRole.OWNER) {
            throw new BusinessException(CODE_FORBIDDEN, "owner_required");
        }
    }

    private static BigDecimal priceOfSnapshot(Subscription sub) {
        if (sub == null || sub.getPlanVersionSnapshot() == null) {
            return null;
        }
        Object price = sub.getPlanVersionSnapshot().get("price");
        if (price == null) {
            return null;
        }
        try {
            return BillingMath.scale(new BigDecimal(String.valueOf(price)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    /** 供 Controller 暴露的列表方法（公开套餐）。 */
    public List<PlanDtos.PlanView> listPublicPlans() {
        return planService.listPublicPlans();
    }
}
