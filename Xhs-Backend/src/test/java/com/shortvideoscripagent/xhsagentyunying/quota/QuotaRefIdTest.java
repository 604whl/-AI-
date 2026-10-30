package com.shortvideoscripagent.xhsagentyunying.quota;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P0 回归测试：{@link QuotaRefId} 关联 ID 解析。
 * <p>
 * 背景：{@code usage_ledger.ref_id} 是 UUID 列，但业务关联 ID 不总是 UUID
 * （分析任务 {@code ana_xxx}、聊天会话 {@code sess_xxx}）。
 * 此前 {@code AnalysisTaskHandler} 直接调用 {@code UUID.fromString(taskId)}，
 * 异常在 {@code tryConsume} <b>调用前</b>抛出 → 配额扣减被整段跳过 →
 * 分析用量从未写入台账、从未计费。
 * </p>
 */
class QuotaRefIdTest {

    @Test
    void resolve_validUuid_returnsSameUuid() {
        UUID original = UUID.randomUUID();
        assertEquals(original, QuotaRefId.resolve(original.toString()));
    }

    @Test
    void resolve_nonUuidTaskId_doesNotThrow() {
        // 这是本次 P0 的直接触发点：ana_xxx 不是合法 UUID
        UUID resolved = QuotaRefId.resolve("ana_897b2412a401");
        assertNotNull(resolved);
    }

    @Test
    void resolve_nonUuidSessionId_doesNotThrow() {
        UUID resolved = QuotaRefId.resolve("sess_1a2b3c4d5e6f");
        assertNotNull(resolved);
    }

    @Test
    void resolve_isDeterministic() {
        // 确定性是冲正链路（refundByRawRefId）能对上的前提
        assertEquals(QuotaRefId.resolve("ana_897b2412a401"),
                QuotaRefId.resolve("ana_897b2412a401"));
        assertEquals(QuotaRefId.resolve("sess_1a2b3c4d5e6f"),
                QuotaRefId.resolve("sess_1a2b3c4d5e6f"));
    }

    @Test
    void resolve_differentIds_produceDifferentUuids() {
        assertNotEquals(QuotaRefId.resolve("ana_aaa"), QuotaRefId.resolve("ana_bbb"));
        // 不同前缀的同名原始 ID 也不应撞车
        assertNotEquals(QuotaRefId.resolve("ana_x"), QuotaRefId.resolve("sess_x"));
    }

    @Test
    void resolve_nullOrBlank_returnsNull() {
        assertNull(QuotaRefId.resolve(null));
        assertNull(QuotaRefId.resolve(""));
        assertNull(QuotaRefId.resolve("   "));
    }

    @Test
    void resolve_trimsWhitespace() {
        assertEquals(QuotaRefId.resolve("ana_897b2412a401"),
                QuotaRefId.resolve("  ana_897b2412a401  "));
    }

    @Test
    void resolve_neverThrowsForGarbageInput() {
        // 任何输入都不能抛异常——这是本类的核心契约
        assertDoesNotThrowAnyException(() -> QuotaRefId.resolve("!!!not-a-uuid!!!"));
        assertDoesNotThrowAnyException(() -> QuotaRefId.resolve("中文ID"));
    }

    private static void assertNotEquals(Object a, Object b) {
        org.junit.jupiter.api.Assertions.assertNotEquals(a, b);
    }

    private static void assertDoesNotThrowAnyException(org.junit.jupiter.api.function.Executable executable) {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(executable);
    }

    @Test
    void resolve_unusedAssertionHelper_importsSanity() {
        // 占位：确保 assertThrows 导入被使用（避免 IDE 报警）
        assertThrows(IllegalArgumentException.class, () -> UUID.fromString("ana_x"));
    }
}
