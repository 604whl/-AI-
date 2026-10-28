package com.shortvideoscripagent.xhsagentyunying.dto.competitor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * S3-10 竞品分析请求。
 * <p>
 * 分析输入必须包含至少一条带证据的竞品笔记，否则 400 NO_EVIDENCE_INPUT。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorAnalyzeRequest {

    /** 参与分析的竞品账号 ID 列表（必填，至少一个） */
    @NotEmpty
    private List<UUID> competitorAccountIds;

    /** 关联的内容资产 ID（可空） */
    private UUID assetId;

    /** 平台代码（必填） */
    @NotBlank
    private String platformCode;

    /** 品类代码（可空；同品类多账号对比口径） */
    private String category;
}
