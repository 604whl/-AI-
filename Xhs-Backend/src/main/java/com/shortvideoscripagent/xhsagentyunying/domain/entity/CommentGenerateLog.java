package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 评论生成审计日志实体。
 */
@Data
@TableName("comment_generate_log")
public class CommentGenerateLog {

    @TableId
    private Long id;

    private Long userId;

    private Long workspaceId;

    private String commentType;

    private String platform;

    private String persona;

    private String draftContext;

    private String generatedComment;

    private Boolean requiresReview;

    private String complianceRisk;

    private String riskLevel;

    private OffsetDateTime createdAt;
}