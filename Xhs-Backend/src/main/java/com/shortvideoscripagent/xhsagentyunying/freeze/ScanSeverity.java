package com.shortvideoscripagent.xhsagentyunying.freeze;

import java.util.Optional;

/**
 * 阶段 0：合规扫描结果严重性（与 {@link RiskLevel} 略有不同：本枚举用于单条扫描结果）。
 * <p>
 * {@link RiskLevel} 用于规则定义和数据语义，{@link ScanSeverity} 用于单次扫描动作的输出。
 */
public enum ScanSeverity {
    INFO,
    WARN,
    ERROR,
    CRITICAL
}
