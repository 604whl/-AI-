package com.shortvideoscripagent.xhsagentyunying.billing.support;

import com.shortvideoscripagent.xhsagentyunying.billing.plan.domain.entity.SubscriptionPlan;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.entity.Subscription;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S4-1 套餐快照工具。
 * <p>
 * 订阅生效时把套餐内容冻结成一份 JSON 快照（code / version / name / price /
 * included_units / feature_flags / billing_period），之后套餐改价生成新版本时，
 * 存量订阅读快照不受影响。
 * </p>
 */
public final class PlanSnapshot {

    private PlanSnapshot() {
    }

    public static Map<String, Object> of(SubscriptionPlan plan) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (plan == null) {
            return snapshot;
        }
        snapshot.put("planId", plan.getId() == null ? null : plan.getId().toString());
        snapshot.put("code", plan.getCode());
        snapshot.put("version", plan.getVersion());
        snapshot.put("name", plan.getName());
        snapshot.put("billingPeriod", plan.getBillingPeriod());
        snapshot.put("price", plan.getPrice() == null ? null : plan.getPrice().toPlainString());
        snapshot.put("currency", plan.getCurrency());
        snapshot.put("includedUnits", plan.getIncludedUnits() == null ? Map.of() : plan.getIncludedUnits());
        snapshot.put("featureFlags", plan.getFeatureFlags() == null ? Map.of() : plan.getFeatureFlags());
        snapshot.put("overagePrice", plan.getOveragePrice() == null ? null : plan.getOveragePrice().toPlainString());
        snapshot.put("description", plan.getDescription());
        return snapshot;
    }

    /** 从订阅快照里取包含额度。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> includedUnitsOf(Subscription subscription) {
        if (subscription == null || subscription.getPlanVersionSnapshot() == null) {
            return Map.of();
        }
        Object units = subscription.getPlanVersionSnapshot().get("includedUnits");
        return units instanceof Map ? (Map<String, Object>) units : Map.of();
    }

    /** 从订阅快照里取功能开关。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> featureFlagsOf(Subscription subscription) {
        if (subscription == null || subscription.getPlanVersionSnapshot() == null) {
            return Map.of();
        }
        Object flags = subscription.getPlanVersionSnapshot().get("featureFlags");
        return flags instanceof Map ? (Map<String, Object>) flags : Map.of();
    }

    /** 快照里的套餐编码。 */
    public static String codeOf(Subscription subscription) {
        if (subscription == null || subscription.getPlanVersionSnapshot() == null) {
            return null;
        }
        Object code = subscription.getPlanVersionSnapshot().get("code");
        return code == null ? null : String.valueOf(code);
    }

    /** 快照里的套餐名称。 */
    public static String nameOf(Subscription subscription) {
        if (subscription == null || subscription.getPlanVersionSnapshot() == null) {
            return null;
        }
        Object name = subscription.getPlanVersionSnapshot().get("name");
        return name == null ? null : String.valueOf(name);
    }

    /** 判断某个功能开关是否开启。 */
    public static boolean hasFeature(Subscription subscription, String flag) {
        Object value = featureFlagsOf(subscription).get(flag);
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }
}
