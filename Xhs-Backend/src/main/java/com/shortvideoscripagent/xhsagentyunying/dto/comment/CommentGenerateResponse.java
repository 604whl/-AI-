package com.shortvideoscripagent.xhsagentyunying.dto.comment;

import com.shortvideoscripagent.xhsagentyunying.freeze.ComplianceHit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评论生成响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentGenerateResponse {

    /** 生成的评论话术 */
    private String comment;

    /** 是否需要人工审核 */
    private Boolean requiresReview;

    /** 合规风险等级：HIGH / MEDIUM / LOW / NONE */
    private String complianceRisk;

    /** 风险级别：BLOCKED / HIGH / MEDIUM / LOW（ComplianceScanner.RiskLevel 字符串化） */
    private String riskLevel;

    /** 命中规则详情，MEDIUM+ 时必须返回 */
    private List<ComplianceHit> hitDetails;
}