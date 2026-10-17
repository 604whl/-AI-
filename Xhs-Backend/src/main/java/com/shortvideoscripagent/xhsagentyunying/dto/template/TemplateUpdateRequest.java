package com.shortvideoscripagent.xhsagentyunying.dto.template;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新模板请求（P0-4）。
 */
@Data
public class TemplateUpdateRequest {

    /** 模板标题 */
    @Size(max = 255, message = "title max 255 chars")
    private String title;

    /** 正文模板 */
    private String bodyTemplate;

    /** 变量列表 */
    private List<String> variables;

    /** 模板分类 */
    private String category;

    /** 场景 */
    private String scenario;

    /** 人设 */
    private String persona;

    /** 默认目标 */
    private String defaultGoal;

    /** 默认语气 */
    private String defaultTone;
}
