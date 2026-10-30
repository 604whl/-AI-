package com.shortvideoscripagent.xhsagentyunying.billing.pricing;

import com.shortvideoscripagent.xhsagentyunying.billing.pricing.domain.entity.PriceBook;
import com.shortvideoscripagent.xhsagentyunying.billing.pricing.domain.mapper.PriceBookMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingMath;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.pricing.BillingDtos;
import com.shortvideoscripagent.xhsagentyunying.freeze.BillableUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * S4-3 计价服务（价格表 + 单价版本化）。
 * <p>
 * <b>单价版本化</b>：改价不追溯历史账单 —— 改价时插入新版本行
 * （{@code version + 1}），旧版本行的 {@code effective_from} 原样保留；
 * 用量台账在扣费时把当时的单价与价格版本<b>快照</b>写进
 * {@code app.usage_ledger.unit_price / price_version}，
 * 因此已出账金额永远可由「快照单价 × 数量」复算。
 * </p>
 * <p>
 * <b>D12 成本口径</b>：每个计量单位同时维护收入侧 {@code unit_price} 与
 * 成本侧 {@code cost_per_unit}，毛利 = 收入 − 成本。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    /** 默认地区 */
    public static final String DEFAULT_REGION = "CN";
    /** 默认币种 */
    public static final String DEFAULT_CURRENCY = "CNY";

    private static final int CODE_BAD_REQUEST = 40010;
    private static final int CODE_NOT_FOUND = 40410;

    private final PriceBookMapper priceBookMapper;

    /**
     * 解析计量单位；非法返回 null。
     */
    public static BillableUnit parseUnit(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BillableUnit.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 把台账 {@code action} 归一为<b>可计价</b>的计量单位。
     * <p>
     * 台账 {@code action} 的取值来自各业务调用点（{@code ANALYSIS} / {@code CHAT} /
     * {@code WEB_SEARCH} / {@code BODY} …），<b>与 {@link BillableUnit} 枚举名并不一致</b>。
     * 价格表只收录 {@link BillableUnit} 枚举名，若直接拿 {@code action} 去查价格表，
     * {@code CHAT} / {@code WEB_SEARCH} / {@code BODY} 都会查不到价格行 → 金额为 0。
     * </p>
     * <p>
     * 归一规则（与阶段 0 {@code Stage0Service#normalizeBillableUnit} 保持一致，
     * 额外把 {@code CHAT} 映射到 {@link BillableUnit#SEARCH}）：
     * <ul>
     *   <li>{@code ANALYSIS} / {@code ANALYZE} → {@code ANALYSIS}</li>
     *   <li>{@code TITLE} / {@code TITLE_GENERATION} / {@code TITLES} → {@code TITLE_GENERATION}</li>
     *   <li>{@code BODY} / {@code BODY_GENERATION} / {@code CONTENT_GENERATION} → {@code BODY_GENERATION}</li>
     *   <li>{@code COVER} / {@code COVER_ANALYSIS} → {@code COVER_ANALYSIS}</li>
     *   <li>{@code EXPORT} / {@code EXPORTS} → {@code EXPORT}</li>
     *   <li>{@code EMBEDDING} / {@code EMBED} → {@code EMBEDDING}</li>
     *   <li>{@code SEARCH} / {@code WEB_SEARCH} → {@code SEARCH}</li>
     *   <li>{@code CHAT}（Agent 对话）→ {@code SEARCH}（按联网搜索计价）</li>
     * </ul>
     * </p>
     *
     * @param action 台账 action（可为 null）
     * @return 对应的计量单位；无法识别时返回 {@code null}（<b>不兜底成 ANALYSIS</b>——
     *         兜底会把无关用量错记到 ANALYSIS 头上，导致账单金额虚高）
     */
    public static BillableUnit canonicalUnit(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        return switch (action.trim().toUpperCase()) {
            case "ANALYSIS", "ANALYZE" -> BillableUnit.ANALYSIS;
            case "TITLE", "TITLE_GENERATION", "TITLES" -> BillableUnit.TITLE_GENERATION;
            case "BODY", "BODY_GENERATION", "CONTENT_GENERATION" -> BillableUnit.BODY_GENERATION;
            case "COVER", "COVER_ANALYSIS" -> BillableUnit.COVER_ANALYSIS;
            case "COMPLIANCE_SCAN", "COMPLIANCE" -> BillableUnit.COMPLIANCE_SCAN;
            case "EXPORT", "EXPORTS" -> BillableUnit.EXPORT;
            case "EMBEDDING", "EMBED" -> BillableUnit.EMBEDDING;
            // CHAT / WEB_SEARCH / SEARCH 都归到联网搜索口径
            case "CHAT", "SEARCH", "WEB_SEARCH" -> BillableUnit.SEARCH;
            default -> null;
        };
    }

    /**
     * 取某计量单位在指定时点生效的价格行；没有则退化为最新一行。
     * <p>
     * 入参可以是<b>台账 action</b>（{@code CHAT} 等）或<b>计量单位枚举名</b>，
     * 内部统一走 {@link #canonicalUnit(String)} 归一。
     * </p>
     */
    public PriceBook resolvePrice(String unit, OffsetDateTime at) {
        BillableUnit parsed = canonicalUnit(unit);
        if (parsed == null) {
            parsed = parseUnit(unit);
        }
        if (parsed == null) {
            return null;
        }
        PriceBook effective = priceBookMapper.selectEffective(parsed.name(), DEFAULT_REGION,
                at == null ? OffsetDateTime.now() : at);
        if (effective != null) {
            return effective;
        }
        return priceBookMapper.selectLatest(parsed.name(), DEFAULT_REGION);
    }

    /**
     * 取某计量单位的当前价格行；不存在返回 null。
     */
    public PriceBook currentPrice(String unit) {
        return resolvePrice(unit, OffsetDateTime.now());
    }

    /**
     * 取收入侧单价；没有价格表记录时返回 0（不抛错，避免老数据阻断扣费）。
     */
    public BigDecimal unitPriceOf(String unit) {
        PriceBook price = currentPrice(unit);
        if (price == null || price.getUnitPrice() == null) {
            return BigDecimal.ZERO.setScale(BillingMath.SCALE, java.math.RoundingMode.HALF_UP);
        }
        return BillingMath.scale(price.getUnitPrice());
    }

    /**
     * 取成本侧单价；没有价格表记录时返回 0。
     */
    public BigDecimal costPerUnitOf(String unit) {
        PriceBook price = currentPrice(unit);
        if (price == null || price.getCostPerUnit() == null) {
            return BigDecimal.ZERO.setScale(BillingMath.SCALE, java.math.RoundingMode.HALF_UP);
        }
        return BillingMath.scale(price.getCostPerUnit());
    }

    /**
     * 取历史价格版本的成本单价（用于按历史台账回算成本）。
     */
    public BigDecimal costOfVersion(String unit, Integer priceVersion) {
        if (unit == null || priceVersion == null) {
            return BigDecimal.ZERO.setScale(BillingMath.SCALE, java.math.RoundingMode.HALF_UP);
        }
        BigDecimal cost = priceBookMapper.selectCostByVersion(
                canonicalUnitName(unit), DEFAULT_REGION, priceVersion);
        if (cost == null) {
            return BigDecimal.ZERO.setScale(BillingMath.SCALE, java.math.RoundingMode.HALF_UP);
        }
        return BillingMath.scale(cost);
    }

    /**
     * 把 action / 计量单位名归一为价格表使用的 {@link BillableUnit} 枚举名；
     * 无法识别时回退为入参本身（保持旧行为：查不到价格行就按 0 计价）。
     */
    private static String canonicalUnitName(String raw) {
        BillableUnit parsed = canonicalUnit(raw);
        if (parsed != null) {
            return parsed.name();
        }
        BillableUnit direct = parseUnit(raw);
        return direct != null ? direct.name() : raw;
    }

    /**
     * 列出全部价格行（管理端 / 对账用）。
     */
    public List<BillingDtos.PriceView> listPrices() {
        return priceBookMapper.selectAll().stream().map(this::toView).toList();
    }

    /**
     * 改价：插入新版本行，不修改旧版本行。
     * <p>
     * <b>版本语义</b>：{@code version} 是价格行版本（改价递增，用于台账 {@code price_version}
     * 回指历史成本）；{@code planVersion} 是<b>套餐版本</b>，与
     * {@code app.subscription_plan.version} 对齐。
     * </p>
     * <p>
     * 由于唯一键是 {@code unit + region + plan_version}，<b>同一套餐版本内只能有一行价格</b>。
     * 因此改价必须同时递增 {@code planVersion}（否则会撞唯一键），
     * 这与「套餐改价 → 生成新套餐版本」是同一件事：改价产生新的套餐版本。
     * 如果调用方显式传入了 {@code planVersion} 且该版本已存在价格行，
     * 则视为「覆盖该套餐版本的单价」→ 直接更新该行（不产生新版本）。
     * </p>
     * <p>
     * <b>不追溯历史账单</b>：旧版本行原样保留；已写入台账的
     * {@code unit_price / price_version} 快照不受影响。
     * </p>
     */
    @Transactional
    public PriceBook revisePrice(String unit, String region, BigDecimal unitPrice,
                                 BigDecimal costPerUnit, String currency,
                                 Integer planVersion, String reason) {
        if (unit == null || unit.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "unit_required");
        }
        BillableUnit parsed = parseUnit(unit);
        if (parsed == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_billable_unit");
        }
        if (unitPrice == null || unitPrice.signum() < 0 || costPerUnit == null || costPerUnit.signum() < 0) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_price_amount");
        }
        String reg = region == null || region.isBlank() ? DEFAULT_REGION : region.trim().toUpperCase();

        // 情况一：调用方指定了套餐版本，且该版本已有价格行 → 原地更新（覆盖该套餐版本的单价）
        if (planVersion != null) {
            PriceBook existing = priceBookMapper.selectByPlanVersion(parsed.name(), reg, planVersion);
            if (existing != null) {
                existing.setUnitPrice(BillingMath.scale(unitPrice));
                existing.setCostPerUnit(BillingMath.scale(costPerUnit));
                existing.setCurrency(currency == null || currency.isBlank()
                        ? DEFAULT_CURRENCY : currency.trim().toUpperCase());
                priceBookMapper.updateById(existing);
                log.info("Price updated in place: unit={} region={} planVersion={} version={} unitPrice={} reason={}",
                        parsed.name(), reg, existing.getPlanVersion(), existing.getVersion(),
                        existing.getUnitPrice(), reason);
                return existing;
            }
        }

        // 情况二：生成新的套餐版本 + 新的价格行版本
        PriceBook latest = priceBookMapper.selectLatest(parsed.name(), reg);
        int nextVersion = priceBookMapper.selectMaxVersion(parsed.name(), reg) + 1;
        int nextPlanVersion = planVersion != null ? planVersion
                : (latest == null || latest.getPlanVersion() == null ? 1 : latest.getPlanVersion() + 1);

        PriceBook row = new PriceBook();
        // UUID 主键必须手动生成（MyBatis-Plus 的 ASSIGN_UUID 只对 String 主键自动赋值）
        row.setId(UUID.randomUUID());
        row.setUnit(parsed.name());
        row.setRegion(reg);
        row.setPlanVersion(nextPlanVersion);
        row.setVersion(nextVersion);
        row.setUnitPrice(BillingMath.scale(unitPrice));
        row.setCostPerUnit(BillingMath.scale(costPerUnit));
        row.setCurrency(currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency.trim().toUpperCase());
        row.setEffectiveFrom(OffsetDateTime.now());
        row.setCreatedAt(OffsetDateTime.now());
        priceBookMapper.insert(row);

        log.info("Price revised: unit={} region={} planVersion={} version={} unitPrice={} cost={} reason={}",
                parsed.name(), reg, nextPlanVersion, nextVersion, row.getUnitPrice(), row.getCostPerUnit(), reason);
        return row;
    }

    /**
     * 按 id 取价格行；不存在抛 404。
     */
    public PriceBook requirePrice(java.util.UUID id) {
        PriceBook price = priceBookMapper.selectByUuid(id);
        if (price == null) {
            throw new BusinessException(CODE_NOT_FOUND, "price_not_found");
        }
        return price;
    }

    private BillingDtos.PriceView toView(PriceBook price) {
        BillingDtos.PriceView view = new BillingDtos.PriceView();
        view.setUnit(price.getUnit());
        BillableUnit unit = parseUnit(price.getUnit());
        view.setUnitDisplayName(unit == null ? price.getUnit() : unit.getDisplayName());
        view.setRegion(price.getRegion());
        view.setPlanVersion(price.getPlanVersion());
        view.setVersion(price.getVersion());
        view.setUnitPrice(price.getUnitPrice());
        view.setCostPerUnit(price.getCostPerUnit());
        view.setCurrency(price.getCurrency());
        view.setEffectiveFrom(price.getEffectiveFrom());
        return view;
    }
}
