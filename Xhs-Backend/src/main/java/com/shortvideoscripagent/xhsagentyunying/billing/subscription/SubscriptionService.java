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

    private static final int LIMIT_SUBSCRIBE_PER_DAY = 5;
    private static final int LIMIT_CHANGE_PLAN_PER_DAY = 3;
    private static final int WINDOW_ONE_DAY = 24 * 60 * 60;

    private final SubscriptionMapper subscriptionMapper;
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
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            Subscription existing = subscriptionMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                return toView(existing);
            }
        }
        Subscription sub = subscriptionMapper.selectActiveByWorkspace(workspaceId);
        if (sub == null) {
            // 已经没有生效订阅：按「未找到」处理
            throw new BusinessException(CODE_NOT_FOUND, "subscription_not_found");
        }
        // cancel 是「修改已有订阅」，创建时并没有把幂等键写进行里，
        // 这里补绑一次，重复提交同一 Idempotency-Key 才会命中唯一索引并被识别为重放。
        bindIdempotencyKey(sub, key);
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

        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            Subscription existing = subscriptionMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                SubscriptionDtos.ChangePlanResult replay = new SubscriptionDtos.ChangePlanResult();
                replay.setEffective("replay");
                replay.setSubscription(toView(existing));
                return replay;
            }
        }
        if (request == null || request.getPlanCode() == null || request.getPlanCode().isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "plan_code_required");
        }
        Subscription sub = subscriptionMapper.selectActiveByWorkspace(workspaceId);
        if (sub == null) {
            throw new BusinessException(CODE_NOT_FOUND, "subscription_not_found");
        }
        // change-plan 同样是「修改已有订阅」，补绑幂等键（见 cancel 的说明）。
        bindIdempotencyKey(sub, key);
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

            applyPlanQuota(workspaceId, target, sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd());
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
                                                       SubscriptionDtos.AdminAdjustRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "reason_required");
        }
        Subscription sub = subscriptionMapper.selectById(subscriptionId);
        if (sub == null) {
            throw new BusinessException(CODE_NOT_FOUND, "subscription_not_found");
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
        String planId = plan.getId() == null ? null : plan.getId().toString();
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
    private void bindIdempotencyKey(Subscription sub, String key) {
        if (key == null || sub == null || sub.getId() == null) {
            return;
        }
        String current = sub.getIdempotencyKey();
        if (key.equals(current)) {
            return;
        }
        try {
            int updated = subscriptionMapper.bindIdempotencyKey(sub.getId(), key);
            if (updated == 0) {
                // 该订阅行已经绑定了<b>另一个</b>幂等键（例如先 cancel 后 change-plan），
                // SQL 的 idempotency_key IS NULL 守卫使本次绑定不生效。
                // 此时不能把 key 写进内存对象，否则内存与 DB 不一致。
                log.warn("Idempotency key not bound (subscription already holds another key): "
                                + "ws={} sub={} key={} existing={}",
                        sub.getWorkspaceId(), sub.getId(), key, current);
                return;
            }
            sub.setIdempotencyKey(key);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            log.debug("Idempotency key already bound to another subscription: ws={} key={}",
                    sub.getWorkspaceId(), key);
        }
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
