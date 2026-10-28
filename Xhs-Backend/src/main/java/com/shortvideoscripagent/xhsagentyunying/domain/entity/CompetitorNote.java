package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbMapTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * S3-10 竞品笔记实体。
 * <p>
 * 对应 {@code app.competitor_note}（V58）。
 * <ul>
 *   <li>{@code evidenceText} —— <b>真实原文片段引用</b>，没有原文片段不得入库
 *       （数据库层 {@code chk_competitor_note_evidence_not_empty} + 服务层 400 EVIDENCE_TEXT_REQUIRED 双重保证）</li>
 *   <li>{@code contentStructure} —— 段落结构 / 钩子类型 / CTA 位置 / 标签集合</li>
 *   <li>{@code engagement} —— likes / collects / comments / shares / impressions（统一口径，缺失补 0）</li>
 *   <li>{@code capturedAt} —— 采集时间，接口与 UI 必须展示</li>
 * </ul>
 */
@Data
@TableName(value = "competitor_note", autoResultMap = true)
public class CompetitorNote {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属竞品账号 */
    private UUID competitorAccountId;

    /** 所属工作区（VARCHAR(32)） */
    private String workspaceId;

    /** 平台代码 */
    private String platformCode;

    /** 竞品笔记标题 */
    private String noteTitle;

    /** 竞品笔记链接 */
    private String noteUrl;

    /** 笔记发布时间 */
    private OffsetDateTime publishedAt;

    /** 内容结构拆解 JSONB */
    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> contentStructure;

    /** 互动数据 JSONB */
    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> engagement;

    /** 采集时间（接口与 UI 必须展示） */
    private OffsetDateTime capturedAt;

    /** 真实原文片段引用（可高亮）；为空一律拒绝入库 */
    private String evidenceText;

    /** 采集方式：BROWSER_ASSIST / MANUAL_IMPORT / CSV_IMPORT / AUTHORIZED_CONNECTOR / OFFICIAL_API */
    private String sourceType;

    private OffsetDateTime createdAt;

    // ── 常量 ────────────────────────────────────────────────────────────
    public static final String SOURCE_BROWSER_ASSIST = "BROWSER_ASSIST";
    public static final String SOURCE_MANUAL_IMPORT = "MANUAL_IMPORT";
    public static final String SOURCE_CSV_IMPORT = "CSV_IMPORT";
    public static final String SOURCE_AUTHORIZED_CONNECTOR = "AUTHORIZED_CONNECTOR";
    public static final String SOURCE_OFFICIAL_API = "OFFICIAL_API";

    /** 允许的采集方式 */
    public static final java.util.Set<String> ALLOWED_SOURCE_TYPES = java.util.Set.of(
            SOURCE_BROWSER_ASSIST, SOURCE_MANUAL_IMPORT, SOURCE_CSV_IMPORT,
            SOURCE_AUTHORIZED_CONNECTOR, SOURCE_OFFICIAL_API);

    /** 互动数据统一口径键集（缺失补 0，不省略） */
    public static final java.util.List<String> ENGAGEMENT_KEYS = java.util.List.of(
            "impressions", "likes", "collects", "comments", "shares");
}
