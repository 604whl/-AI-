package com.shortvideoscripagent.xhsagentyunying.dto.template;

import lombok.Builder;
import lombok.Data;

/**
 * 模板渲染响应（P0-4）。
 */
@Data
@Builder
public class TemplateRenderResponse {

    /** 渲染后的正文 */
    private String body;

    /** 使用的变量集合 */
    private String templateId;
}
