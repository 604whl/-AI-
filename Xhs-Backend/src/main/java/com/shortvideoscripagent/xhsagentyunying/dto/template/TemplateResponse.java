package com.shortvideoscripagent.xhsagentyunying.dto.template;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 模板响应（P0-4）。
 */
@Data
@Builder
public class TemplateResponse {

    private UUID id;

    /** 范围：OFFICIAL / WORKSPACE / PERSONAL */
    private String scope;

    /** 工作区 ID（WORKSPACE 时有值） */
    private String workspaceId;

    /** 拥有者 userId（PERSONAL 时有值） */
    private Long ownerUserId;

    private String category;

    /** 官方种子幂等键（OFFICIAL 模板有值，用户自建模板为 null） */
    private String code;

    private String scenario;

    private String persona;

    private String title;

    private String bodyTemplate;

    /** 变量列表 */
    private List<String> variables;

    private String defaultGoal;

    private String defaultTone;

    private Integer version;

    /** 状态：active / archived */
    private String status;

    /** 被引用次数 */
    private Integer useCount;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
