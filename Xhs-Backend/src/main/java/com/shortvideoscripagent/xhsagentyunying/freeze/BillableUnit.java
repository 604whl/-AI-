package com.shortvideoscripagent.xhsagentyunying.freeze;

/**
 * 阶段 0：计费计量单位。
 * <p>
 * 每个单位对应"一次 AI 操作的可计费单元"。所有扣费、退款、冲正、对账必须用本枚举。
 * <p>
 * 计费事件见 {@link BillableEvent}。
 */
public enum BillableUnit {
    ANALYSIS("内容分析"),
    TITLE_GENERATION("标题生成"),
    BODY_GENERATION("正文生成"),
    COVER_ANALYSIS("封面分析"),
    COMPLIANCE_SCAN("合规扫描"),
    EXPORT("导出"),
    EMBEDDING("嵌入"),
    SEARCH("联网搜索");

    private final String displayName;

    BillableUnit(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
