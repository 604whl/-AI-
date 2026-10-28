package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.TextArrayTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * S3-10 竞品账号实体。
 * <p>
 * 对应 {@code app.competitor_account}（V58）。
 * <ul>
 *   <li>{@code status}：active / archived</li>
 *   <li>{@code category}：品类代码，同品类多账号用于互动率对比</li>
 *   <li>{@code updatedAt}：最后更新时间，接口必须暴露</li>
 * </ul>
 */
@Data
@TableName(value = "competitor_account", autoResultMap = true)
public class CompetitorAccount {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属工作区（VARCHAR(32)，与 app.workspace.id 一致） */
    private String workspaceId;

    /** 平台代码（大写枚举名，与 app.platform.code 一致） */
    private String platformCode;

    /** 竞品账号名称 */
    private String accountName;

    /** 竞品账号主页链接 */
    private String accountUrl;

    /** 平台侧账号 ID */
    private String externalAccountId;

    /** 粉丝数 */
    private Long followerCount;

    /** 品类代码 */
    private String category;

    /** 标签集合 */
    @TableField(typeHandler = TextArrayTypeHandler.class)
    private List<String> tags;

    /** active / archived */
    private String status;

    /** 录入人 */
    private Long addedBy;

    private OffsetDateTime createdAt;

    /** 最后更新时间（接口与 UI 必须展示） */
    private OffsetDateTime updatedAt;

    // ── 常量 ────────────────────────────────────────────────────────────
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ARCHIVED = "archived";
}
