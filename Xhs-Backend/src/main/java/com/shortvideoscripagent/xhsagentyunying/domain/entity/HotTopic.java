package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbMapTypeHandler;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 热搜/热门话题池，Scheduler 每 4h 从 GetHotTopicsTool 拉取写入。
 *
 * <p>阶段 3（S3-9 热点数据产品化）新增来源与可信度字段：
 * <ul>
 *   <li>{@code sourceName} / {@code sourceType} —— 来源名称与来源类型，界面必须展示</li>
 *   <li>{@code confidence} —— 可信度分级 HIGH / MEDIUM / LOW / UNVERIFIED</li>
 *   <li>{@code isRealtime} —— 是否实时；静态种子必须为 false（禁止伪装成实时热点）</li>
 *   <li>{@code status} —— 去重状态 active / superseded（历史保留，不删除）</li>
 *   <li>{@code suggestedAngles} —— 可做方向数组 JSON，每条必须引用真实标题 + 来源 + 采集时间</li>
 * </ul>
 */
@Data
@TableName(value = "hot_topic", autoResultMap = true)
public class HotTopic {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String platform;

    private String category;

    private String title;

    private Integer heatScore;

    /** 采集时间（接口必须对外暴露） */
    private OffsetDateTime fetchedAt;

    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> rawPayload;

    private Boolean promotedToPool;

    /** 来源名称（如 web_search / static_seed / 连接器 providerCode） */
    private String sourceName;

    /** 来源类型：SEARCH_TOOL / OFFICIAL_API / AUTHORIZED_CONNECTOR / MANUAL_IMPORT / SEED */
    private String sourceType;

    /** 可信度分级：HIGH / MEDIUM / LOW / UNVERIFIED */
    private String confidence;

    /** 是否实时采集（静态种子为 false） */
    private Boolean isRealtime;

    /** 原始来源链接 */
    private String sourceUrl;

    /** 该条数据是否处于降级状态 */
    private Boolean degraded;

    /** 降级原因（禁止静默展示陈旧数据） */
    private String degradedReason;

    /** 去重状态：active / superseded */
    private String status;

    /** 可做方向数组 JSON：[{"angle":"...","evidence":{"title":"...","sourceName":"...","fetchedAt":"..."}}] */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String suggestedAngles;

    public static final String PLATFORM_XHS = "xiaohongshu";

    // ── 来源类型 ────────────────────────────────────────────────────────────
    /** 联网搜索工具（实时采集） */
    public static final String SOURCE_TYPE_SEARCH_TOOL = "SEARCH_TOOL";
    /** 平台官方接口 */
    public static final String SOURCE_TYPE_OFFICIAL_API = "OFFICIAL_API";
    /** 授权连接器 */
    public static final String SOURCE_TYPE_AUTHORIZED_CONNECTOR = "AUTHORIZED_CONNECTOR";
    /** 手动导入 */
    public static final String SOURCE_TYPE_MANUAL_IMPORT = "MANUAL_IMPORT";
    /** 静态种子（非实时） */
    public static final String SOURCE_TYPE_SEED = "SEED";

    // ── 可信度 ──────────────────────────────────────────────────────────────
    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";
    public static final String CONFIDENCE_UNVERIFIED = "UNVERIFIED";

    // ── 去重状态 ────────────────────────────────────────────────────────────
    /** 当前最新一条 */
    public static final String STATUS_ACTIVE = "active";
    /** 已被更新取代的历史记录（保留，不删除） */
    public static final String STATUS_SUPERSEDED = "superseded";

    /** 静态种子来源名称 */
    public static final String SOURCE_NAME_STATIC_SEED = "static_seed";
}
