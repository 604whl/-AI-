package com.shortvideoscripagent.xhsagentyunying.freeze;

import java.util.Optional;

/**
 * 阶段 0：风险等级。
 * <p>
 * 用于合规扫描结果分级：
 * <ul>
 *   <li>{@link #LOW}：通过，仅记录</li>
 *   <li>{@link #MEDIUM}：提示用户知晓，不阻断流程</li>
 *   <li>{@link #HIGH}：必须人工审核，不可自动发布</li>
 *   <li>{@link #BLOCKED}：直接拒绝，例如医疗、金融、具体法律案件</li>
 * </ul>
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    BLOCKED;

    public boolean blocksPublication() {
        return this == HIGH || this == BLOCKED;
    }

    public boolean requiresManualReview() {
        return this == HIGH || this == BLOCKED;
    }

    public static Optional<RiskLevel> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(RiskLevel.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
