package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 审核批注实体（P0-6）。
 */
@Data
@TableName("review_comment")
public class ReviewComment {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 关联的审核任务 ID */
    private UUID reviewTaskId;

    /** 批注正文 */
    private String comment;

    /** 被批注的正文片段（可选） */
    private String highlightedText;

    /**
     * 批注类型：comment / approve / reject
     */
    private String commentType;

    /** 创建人 userId */
    private Long createdBy;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    // ── 类型常量 ─────────────────────────────────────────────────────────

    public static final String TYPE_COMMENT = "comment";
    public static final String TYPE_APPROVE = "approve";
    public static final String TYPE_REJECT = "reject";
}
