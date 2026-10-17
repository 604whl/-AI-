package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 模板主表（P0-4）。
 * <p>
 * 三级范围：OFFICIAL（平台官方，只读）/ WORKSPACE（工作区）/ PERSONAL（个人）
 * <p>
 * 状态枚举：active / archived
 */
@Data
@TableName("template")
public class Template {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /**
     * 模板范围：
     * OFFICIAL  - 平台官方模板，所有人可见，只读
     * WORKSPACE - 工作区模板，同 workspace 成员可见可编辑
     * PERSONAL  - 个人模板，仅 owner 可见可编辑
     */
    private String scope;

    /** 工作区 ID（WORKSPACE 模板必填） */
    private UUID workspaceId;

    /** 拥有者 userId（PERSONAL 模板必填） */
    private Long ownerUserId;

    /** 模板分类：career_fair / interview / visa / job_hunt / etc. */
    private String category;

    /** 场景：first_post / interview_share / etc. */
    private String scenario;

    /** 人设：agency / mentor / senior */
    private String persona;

    /** 模板标题 */
    private String title;

    /** 正文模板，使用 {{variable}} 占位符 */
    private String bodyTemplate;

    /** 变量列表，JSONB 数组，如 ["platform", "topic", "tone"] */
    private String variables;  // JSONB → MyBatis-Plus 存 String

    /** 默认目标：high_conversion / brand_awareness / engagement */
    private String defaultGoal;

    /** 默认语气：friendly / professional / casual / motivational */
    private String defaultTone;

    /** 当前版本号 */
    private Integer version;

    /**
     * 状态：active（可用）/ archived（归档）
     */
    private String status;

    /** 被引用次数 */
    private Integer useCount;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;

    // ── Scope 常量 ────────────────────────────────────────────────────────

    public static final String SCOPE_OFFICIAL  = "OFFICIAL";
    public static final String SCOPE_WORKSPACE = "WORKSPACE";
    public static final String SCOPE_PERSONAL  = "PERSONAL";

    // ── Status 常量 ───────────────────────────────────────────────────────

    public static final String STATUS_ACTIVE   = "active";
    public static final String STATUS_ARCHIVED = "archived";
}
