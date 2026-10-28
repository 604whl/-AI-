package com.shortvideoscripagent.xhsagentyunying.dto.retrospective;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * S3-8 提交复盘回写申请请求。
 * <p>
 * {@code proposedContent} 只能是<b>效果统计样本说明</b>（指标 + 标签），
 * 不得直接成为知识库文档正文（污染风险控制）。
 * </p>
 */
@Data
public class RetrospectiveFeedbackRequest {

    /** 回写目标类型：TEMPLATE / KB_DOCUMENT */
    @NotBlank(message = "target_type_required")
    @Size(max = 32)
    private String targetType;

    /** 回写目标 ID */
    @NotBlank(message = "target_id_required")
    @Size(max = 128)
    private String targetId;

    /** 拟写入的内容（模板正文片段 / 知识库标签说明） */
    private String proposedContent;

    /** 拟写入的效果统计样本（指标 + 标签） */
    private Map<String, Object> proposedStats;
}
