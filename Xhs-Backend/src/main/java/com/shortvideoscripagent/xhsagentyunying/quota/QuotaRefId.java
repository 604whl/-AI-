package com.shortvideoscripagent.xhsagentyunying.quota;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 用量台账关联 ID（{@code app.usage_ledger.ref_id}）解析工具。
 * <p>
 * <b>背景</b>：{@code ref_id} 是 UUID 列，但业务侧的关联 ID 并不总是 UUID：
 * <ul>
 *   <li>分析任务 ID 形如 {@code ana_897b2412a401}（{@code AnalysisTask.id}）；</li>
 *   <li>聊天会话 ID 形如 {@code sess_1a2b3c4d5e6f}（{@code ChatHistoryService#generateSessionId}）。</li>
 * </ul>
 * 直接调用 {@link UUID#fromString(String)} 会抛
 * {@code IllegalArgumentException: Invalid UUID string}。
 * </p>
 *
 * <b>P0 缺陷背景</b>：{@code AnalysisTaskHandler} 曾直接调用
 * {@code UUID.fromString(taskId)} 作为 {@code tryConsume} 的入参，
 * 异常在<b>调用前</b>抛出 → 整个配额扣减被跳过 →
 * 分析用量<b>从未写入台账</b>、也从未计费（额度不扣、用量丢失）。
 * </p>
 *
 * <b>解决方式</b>：对非 UUID 的原始 ID 用 {@link UUID#nameUUIDFromBytes(byte[])}
 * 做<b>确定性</b>映射（同一个原始 ID 永远映射到同一个 UUID）。
 * 这样：
 * <ul>
 *   <li>配额扣减不再抛异常，台账行正常写入（含价格快照，可出账）；</li>
 *   <li>{@code QuotaRefundService#refundByRefId} 用同一套规则即可回溯冲正。</li>
 * </ul>
 * 注意这是<b>单向</b>映射：UUID 无法反推原始字符串，因此
 * {@code ref_id} 只作为「同一原始 ID → 同一 UUID」的关联锚点使用。
 */
public final class QuotaRefId {

    /**
     * 命名空间前缀，避免与真实 UUID 撞上（同一原始 ID 在不同语义下不共享 ref_id）。
     */
    private static final String NAMESPACE_PREFIX = "xhs:ref:";

    private QuotaRefId() {
    }

    /**
     * 把业务关联 ID 解析为 UUID。
     * <ul>
     *   <li>本来就是合法 UUID → 原样返回；</li>
     *   <li>非 UUID（{@code ana_xxx} / {@code sess_xxx}）→ 确定性映射为 name-based UUID；</li>
     *   <li>null / 空白 → 返回 {@code null}（表示「无关联对象」）。</li>
     * </ul>
     *
     * @param raw 业务关联 ID
     * @return UUID 或 {@code null}
     */
    public static UUID resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {
            // 非 UUID → 走确定性映射
        }
        return UUID.nameUUIDFromBytes(
                (NAMESPACE_PREFIX + trimmed).getBytes(StandardCharsets.UTF_8));
    }
}
