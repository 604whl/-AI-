package com.shortvideoscripagent.xhsagentyunying.billing.plan;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortvideoscripagent.xhsagentyunying.billing.plan.domain.entity.SubscriptionPlan;
import com.shortvideoscripagent.xhsagentyunying.billing.plan.domain.mapper.SubscriptionPlanMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingPeriod;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingMath;
import com.shortvideoscripagent.xhsagentyunying.billing.support.PlanSnapshot;
import com.shortvideoscripagent.xhsagentyunying.billing.support.PlanStatus;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.PlanDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S4-1 套餐服务。
 * <p>
 * <b>版本化语义</b>：改价 / 改额度不 UPDATE 老版本，而是 INSERT {@code version + 1}
 * 并把老版本置 {@code archived}。存量订阅读的是 {@code plan_version_snapshot} 快照，
 * 因此套餐改价不影响存量订阅；下架套餐也不影响已订阅工作区。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private static final int CODE_NOT_FOUND = 40410;
    private static final int CODE_BAD_REQUEST = 40010;
    private static final int CODE_CONFLICT = 40910;

    private final SubscriptionPlanMapper planMapper;
    private final AuditWriter auditWriter;
    private final com.shortvideoscripagent.xhsagentyunying.billing.support.IdempotencyReplay idempotencyReplay;

    // ── 读 ───────────────────────────────────────────────────────────────

    /**
     * 公开套餐列表：每个 code 只取最新 active 版本。
     */
    public List<PlanDtos.PlanView> listPublicPlans() {
        Map<String, SubscriptionPlan> latest = new LinkedHashMap<>();
        for (SubscriptionPlan plan : planMapper.selectAllActive()) {
            latest.putIfAbsent(plan.getCode(), plan);
        }
        List<PlanDtos.PlanView> result = new ArrayList<>();
        for (SubscriptionPlan plan : latest.values()) {
            result.add(toView(plan));
        }
        return result;
    }

    /**
     * 按 code 取最新 active 版本，不存在抛 404。
     */
    public SubscriptionPlan requireActivePlanByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "plan_code_required");
        }
        List<SubscriptionPlan> plans = planMapper.selectActiveByCode(code.trim().toUpperCase());
        if (plans.isEmpty()) {
            throw new BusinessException(CODE_NOT_FOUND, "plan_not_found");
        }
        return plans.get(0);
    }

    /**
     * 按 code 取最新 active 版本的视图，不存在抛 404。
     */
    public PlanDtos.PlanView getPublicPlan(String code) {
        return toView(requireActivePlanByCode(code));
    }

    /**
     * 管理端分页查询（可按 code / status 过滤）。
     */
    public List<PlanDtos.PlanView> listAllForAdmin(String code, String status) {
        LambdaQueryWrapper<SubscriptionPlan> q = new LambdaQueryWrapper<>();
        if (code != null && !code.isBlank()) {
            q.eq(SubscriptionPlan::getCode, code.trim().toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            q.eq(SubscriptionPlan::getStatus, status.trim().toLowerCase());
        }
        q.orderByDesc(SubscriptionPlan::getUpdatedAt);
        return planMapper.selectList(q).stream().map(PlanService::toView).toList();
    }

    public SubscriptionPlan requirePlan(UUID planId) {
        SubscriptionPlan plan = planId == null ? null : planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(CODE_NOT_FOUND, "plan_not_found");
        }
        return plan;
    }

    // ── 管理端写 ─────────────────────────────────────────────────────────

    /**
     * 创建套餐（幂等：同一 idempotencyKey 重复提交返回同一套餐）。
     */
    @Transactional
    public PlanDtos.PlanView createPlan(PlanDtos.CreatePlanRequest request, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            SubscriptionPlan existing = planMapper.selectByIdempotencyKey(key);
            if (existing != null) {
                return toView(existing);
            }
        }
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "plan_code_required");
        }
        String code = request.getCode().trim().toUpperCase();
        String period = request.getBillingPeriod() == null ? "MONTH"
                : request.getBillingPeriod().trim().toUpperCase();
        BillingPeriod.parse(period); // 归一
        validatePeriod(period);

        SubscriptionPlan plan = new SubscriptionPlan();
        // UUID 主键必须手动生成（MyBatis-Plus 的 ASSIGN_UUID 只对 String 主键自动赋值）
        plan.setId(UUID.randomUUID());
        plan.setCode(code);
        plan.setVersion(1);
        plan.setName(request.getName() == null || request.getName().isBlank() ? code : request.getName().trim());
        plan.setBillingPeriod(period);
        plan.setPrice(BillingMath.scale(request.getPrice()));
        plan.setCurrency(request.getCurrency() == null || request.getCurrency().isBlank()
                ? "CNY" : request.getCurrency().trim().toUpperCase());
        plan.setIncludedUnits(request.getIncludedUnits() == null ? Map.of() : request.getIncludedUnits());
        plan.setOveragePrice(BillingMath.scale(request.getOveragePrice()));
        plan.setFeatureFlags(request.getFeatureFlags() == null ? Map.of() : request.getFeatureFlags());
        String status = request.getStatus() == null || request.getStatus().isBlank()
                ? PlanStatus.ACTIVE.getValue() : request.getStatus().trim().toLowerCase();
        validateStatus(status);
        plan.setStatus(status);
        plan.setDescription(request.getDescription());
        plan.setIdempotencyKey(key);
        OffsetDateTime now = OffsetDateTime.now();
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);

        try {
            planMapper.insert(plan);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 唯一冲突后当前事务已被 PostgreSQL 置为 aborted，回读必须放新事务。
            if (key != null) {
                SubscriptionPlan existing = idempotencyReplay.read(
                        () -> planMapper.selectByIdempotencyKey(key));
                if (existing != null) {
                    return toView(existing);
                }
            }
            SubscriptionPlan dup = idempotencyReplay.read(
                    () -> planMapper.selectActiveByCode(code).stream()
                            .filter(p -> p.getVersion() != null && p.getVersion() == 1)
                            .findFirst().orElse(null));
            if (dup != null) {
                throw new BusinessException(CODE_CONFLICT, "plan_code_version_exists");
            }
            throw new BusinessException(CODE_CONFLICT, "plan_code_version_exists");
        }

        auditWriter.record("PLAN_CREATED", "SUBSCRIPTION_PLAN", plan.getId().toString(),
                Map.of("code", code, "version", 1, "price", plan.getPrice().toPlainString()));

        log.info("Plan created: code={} version=1 id={}", code, plan.getId());
        return toView(plan);
    }

    /**
     * 修改套餐 —— 生成新版本（不 UPDATE 老版本），老版本置 archived。
     * <p>
     * 存量订阅读快照，因此不受影响。
     * </p>
     */
    @Transactional
    public PlanDtos.PlanView updatePlan(UUID planId, PlanDtos.UpdatePlanRequest request,
                                        String idempotencyKey) {
        SubscriptionPlan current = requirePlan(planId);
        if (request == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "plan_update_required");
        }
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            SubscriptionPlan existing = planMapper.selectByIdempotencyKey(key);
            if (existing != null) {
                return toView(existing);
            }
        }

        int nextVersion = planMapper.selectMaxVersion(current.getCode()) + 1;

        SubscriptionPlan next = new SubscriptionPlan();
        // UUID 主键必须手动生成（MyBatis-Plus 的 ASSIGN_UUID 只对 String 主键自动赋值）
        next.setId(UUID.randomUUID());
        next.setCode(current.getCode());
        next.setVersion(nextVersion);
        next.setName(request.getName() == null || request.getName().isBlank()
                ? current.getName() : request.getName().trim());
        next.setBillingPeriod(request.getBillingPeriod() == null || request.getBillingPeriod().isBlank()
                ? current.getBillingPeriod() : request.getBillingPeriod().trim().toUpperCase());
        validatePeriod(next.getBillingPeriod());
        next.setPrice(request.getPrice() == null ? current.getPrice() : BillingMath.scale(request.getPrice()));
        next.setCurrency(request.getCurrency() == null || request.getCurrency().isBlank()
                ? current.getCurrency() : request.getCurrency().trim().toUpperCase());
        next.setIncludedUnits(request.getIncludedUnits() == null
                ? current.getIncludedUnits() : request.getIncludedUnits());
        next.setOveragePrice(request.getOveragePrice() == null
                ? current.getOveragePrice() : BillingMath.scale(request.getOveragePrice()));
        next.setFeatureFlags(request.getFeatureFlags() == null
                ? current.getFeatureFlags() : request.getFeatureFlags());
        String status = request.getStatus() == null || request.getStatus().isBlank()
                ? PlanStatus.ACTIVE.getValue() : request.getStatus().trim().toLowerCase();
        validateStatus(status);
        next.setStatus(status);
        next.setDescription(request.getDescription() == null ? current.getDescription() : request.getDescription());
        next.setIdempotencyKey(key);
        OffsetDateTime now = OffsetDateTime.now();
        next.setCreatedAt(now);
        next.setUpdatedAt(now);

        try {
            planMapper.insert(next);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 唯一冲突后当前事务已被 PostgreSQL 置为 aborted，回读必须放新事务。
            if (key != null) {
                SubscriptionPlan existing = idempotencyReplay.read(
                        () -> planMapper.selectByIdempotencyKey(key));
                if (existing != null) {
                    return toView(existing);
                }
            }
            throw new BusinessException(CODE_CONFLICT, "plan_version_conflict");
        }

        // 老版本归档（仅归档 active，draft 不参与对外）
        archiveActiveVersions(current.getCode(), next.getId());

        // 改价必须写审计
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("code", current.getCode());
        meta.put("oldVersion", current.getVersion());
        meta.put("newVersion", nextVersion);
        meta.put("oldPrice", current.getPrice() == null ? null : current.getPrice().toPlainString());
        meta.put("newPrice", next.getPrice().toPlainString());
        meta.put("oldPlanId", current.getId().toString());
        meta.put("newPlanId", next.getId().toString());
        auditWriter.record("PLAN_VERSION_CREATED", "SUBSCRIPTION_PLAN", next.getId().toString(), meta);

        log.info("Plan version created: code={} v{} -> v{} id={}",
                current.getCode(), current.getVersion(), nextVersion, next.getId());
        return toView(next);
    }

    /**
     * 下架套餐（归档全部 active 版本）。
     * <p>
     * 下架不影响已订阅工作区：存量订阅读快照。
     * </p>
     */
    @Transactional
    public PlanDtos.PlanView archivePlan(UUID planId, String idempotencyKey) {
        SubscriptionPlan current = requirePlan(planId);
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            SubscriptionPlan existing = planMapper.selectByIdempotencyKey(key);
            if (existing != null) {
                return toView(existing);
            }
        }
        archiveActiveVersions(current.getCode(), null);
        auditWriter.record("PLAN_ARCHIVED", "SUBSCRIPTION_PLAN", current.getId().toString(),
                Map.of("code", current.getCode(), "version", String.valueOf(current.getVersion())));
        log.info("Plan archived: code={} id={}", current.getCode(), current.getId());

        SubscriptionPlan archived = planMapper.selectById(current.getId());
        return toView(archived == null ? current : archived);
    }

    // ── private ──────────────────────────────────────────────────────────

    private void archiveActiveVersions(String code, UUID keepId) {
        for (SubscriptionPlan plan : planMapper.selectActiveByCode(code)) {
            if (keepId != null && keepId.equals(plan.getId())) {
                continue;
            }
            SubscriptionPlan update = new SubscriptionPlan();
            update.setId(plan.getId());
            update.setStatus(PlanStatus.ARCHIVED.getValue());
            update.setUpdatedAt(OffsetDateTime.now());
            planMapper.updateById(update);
        }
    }

    private void validatePeriod(String period) {
        try {
            BillingPeriod.valueOf(period);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_billing_period");
        }
    }

    private void validateStatus(String status) {
        try {
            PlanStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_plan_status");
        }
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    public static PlanDtos.PlanView toView(SubscriptionPlan plan) {
        if (plan == null) {
            return null;
        }
        return PlanDtos.PlanView.of(
                plan.getId() == null ? null : plan.getId().toString(),
                plan.getCode(),
                plan.getVersion(),
                plan.getName(),
                plan.getBillingPeriod(),
                plan.getPrice(),
                plan.getCurrency(),
                plan.getIncludedUnits() == null ? Map.of() : plan.getIncludedUnits(),
                plan.getOveragePrice(),
                plan.getFeatureFlags() == null ? Map.of() : plan.getFeatureFlags(),
                plan.getStatus(),
                plan.getDescription());
    }

    /** 供订阅服务使用的快照工具引用。 */
    public static Map<String, Object> snapshotOf(SubscriptionPlan plan) {
        return PlanSnapshot.of(plan);
    }

    /** 供订阅服务使用：把价格归一到 4 位小数。 */
    public static BigDecimal normalizePrice(BigDecimal price) {
        return BillingMath.scale(price);
    }
}
