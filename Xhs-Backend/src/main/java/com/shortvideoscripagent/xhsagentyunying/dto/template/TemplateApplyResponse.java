package com.shortvideoscripagent.xhsagentyunying.dto.template;

import lombok.Builder;
import lombok.Data;

/**
 * 模板应用响应（P0-4）。
 */
@Data
@Builder
public class TemplateApplyResponse {

    /** 模板 ID */
    private String templateId;

    /** 渲染后的正文 */
    private String body;

    /** 更新后的引用次数 */
    private Integer useCount;
}
