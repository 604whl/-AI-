package com.shortvideoscripagent.xhsagentyunying.dto.competitor;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * S3-10 新增竞品笔记请求。
 * <p>
 * {@code evidenceText} 必填 —— 没有原文片段不得入库（400 EVIDENCE_TEXT_REQUIRED）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorNoteCreateRequest {

    /** 笔记标题（必填） */
    @NotBlank
    private String noteTitle;

    /** 笔记链接 */
    private String noteUrl;

    /** 发布时间 */
    private OffsetDateTime publishedAt;

    /** 内容结构拆解 */
    private Map<String, Object> contentStructure;

    /** 互动数据（统一口径，缺失补 0） */
    private Map<String, Object> engagement;

    /** 真实原文片段引用（必填，没有不得入库） */
    @NotBlank
    private String evidenceText;

    /** 采集方式（默认 MANUAL_IMPORT） */
    private String sourceType;
}
