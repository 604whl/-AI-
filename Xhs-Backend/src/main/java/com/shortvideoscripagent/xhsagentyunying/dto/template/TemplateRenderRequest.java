package com.shortvideoscripagent.xhsagentyunying.dto.template;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

/**
 * 模板渲染请求（P0-4）。
 * <p>
 * 将变量值传入模板，返回渲染后的正文。
 */
@Data
public class TemplateRenderRequest {

    /**
     * 变量值 map，key 为变量名（如 "platform"），value 为变量值。
     * 例如：{"platform": "小红书", "topic": "秋招", "tone": "friendly"}
     */
    @NotEmpty(message = "variables map is required")
    private Map<String, String> variables;
}
