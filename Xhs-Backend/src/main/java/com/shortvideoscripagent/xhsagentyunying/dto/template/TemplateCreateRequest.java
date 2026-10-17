package com.shortvideoscripagent.xhsagentyunying.dto.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建模板请求（P0-4）。
 */
@Data
public class TemplateCreateRequest {

    /**
     * 模板范围：WORKSPACE / PERSONAL
     * OFFICIAL 只能通过种子初始化，不能通过 API 创建。
     */
    @NotBlank(message = "scope is required")
    private String scope;

    /**
     * 工作区 ID（WORKSPACE 模板必填）。
     */
    private String workspaceId;

    /** 模板分类（必填） */
    @NotBlank(message = "category is required")
    private String category;

    /** 场景 */
    private String scenario;

    /** 人设：agency / mentor / senior */
    private String persona;

    /** 模板标题 */
    @NotBlank(message = "title is required")
    @Size(max = 255, message = "title max 255 chars")
    private String title;

    /** 正文模板，使用 {{variable}} 占位符 */
    @NotBlank(message = "body_template is required")
    private String bodyTemplate;

    /**
     * 变量列表，如 ["platform", "topic", "tone"]。
     * 为空时自动从 body_template 中提取。
     */
    private List<String> variables;

    /** 默认目标：high_conversion / brand_awareness / engagement */
    private String defaultGoal;

    /** 默认语气：friendly / professional / casual / motivational */
    private String defaultTone;
}
