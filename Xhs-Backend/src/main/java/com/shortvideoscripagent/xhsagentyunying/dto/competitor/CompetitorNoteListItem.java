package com.shortvideoscripagent.xhsagentyunying.dto.competitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * S3-10 竞品笔记列表项。
 * <p>
 * {@code capturedAt} / {@code sourceType} 必须暴露 —— 竞品数据的更新时间与来源必须可见。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorNoteListItem {

    private String id;

    private String noteTitle;

    private String noteUrl;

    /** 笔记发布时间 */
    private OffsetDateTime publishedAt;

    /** 内容结构拆解 JSONB */
    private Map<String, Object> contentStructure;

    /** 互动数据 JSONB（统一口径，缺失补 0） */
    private Map<String, Object> engagement;

    /** 采集时间（接口与 UI 必须展示） */
    private OffsetDateTime capturedAt;

    /** 采集方式：BROWSER_ASSIST / MANUAL_IMPORT / CSV_IMPORT / AUTHORIZED_CONNECTOR / OFFICIAL_API */
    private String sourceType;

    /** 真实原文片段引用（可高亮） */
    private String evidenceText;
}
