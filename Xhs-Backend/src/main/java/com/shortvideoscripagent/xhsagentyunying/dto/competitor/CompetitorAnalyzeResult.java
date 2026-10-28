package com.shortvideoscripagent.xhsagentyunying.dto.competitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * S3-10 竞品分析结果。
 * <p>
 * <b>输出 Schema（固定）</b>：
 * <pre>
 * {
 *   "borrowPoints": [ { "point", "evidenceRefs": [], "applySuggestion" } ],
 *   "avoidPoints":  [ { "point", "reason", "evidenceRefs": [] } ],
 *   "structureCompare": []
 * }
 * </pre>
 * <b>硬性验收</b>：每条"可借鉴点"必须带 {@code evidenceRefs[]}（指向 {@code competitor_note.id}
 * + {@code evidence_text} 片段）；无 {@code evidenceRefs} 的建议在落库时被丢弃，
 * 接口返回 400 {@code COMPETITOR_EVIDENCE_MISSING}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorAnalyzeResult {

    /** 可借鉴点 */
    private List<BorrowPoint> borrowPoints;

    /** 勿照搬点 */
    private List<AvoidPoint> avoidPoints;

    /** 结构对比 */
    private List<StructureCompareItem> structureCompare;

    /** 被丢弃的无证据建议数（落库前校验命中） */
    private Integer droppedPoints;

    /** 参与分析的竞品笔记数 */
    private Integer noteCount;

    /**
     * 可借鉴点。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BorrowPoint {
        /** 建议内容 */
        private String point;
        /** 证据引用（指向 competitor_note.id）；为空则该建议被丢弃 */
        private List<EvidenceRef> evidenceRefs;
        /** 可落地建议 */
        private String applySuggestion;
    }

    /**
     * 勿照搬点。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvoidPoint {
        /** 不宜照搬的做法 */
        private String point;
        /** 原因 */
        private String reason;
        /** 证据引用 */
        private List<EvidenceRef> evidenceRefs;
    }

    /**
     * 结构对比项。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StructureCompareItem {
        /** 对比维度（如"开头钩子"/"CTA 位置"） */
        private String dimension;
        /** 竞品 A 表现 */
        private String competitorA;
        /** 竞品 B 表现 */
        private String competitorB;
        /** 证据引用 */
        private List<EvidenceRef> evidenceRefs;
    }

    /**
     * 证据引用。
     *
     * @param noteId       competitor_note.id
     * @param noteTitle    竞品笔记标题
     * @param evidenceText 原文片段（可高亮）
     * @param capturedAt   采集时间
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceRef {
        private String noteId;
        private String noteTitle;
        private String evidenceText;
        private java.time.OffsetDateTime capturedAt;
    }
}
