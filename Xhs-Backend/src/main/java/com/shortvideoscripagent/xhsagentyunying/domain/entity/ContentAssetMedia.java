package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 内容资产多媒体附件（P0-3）：封面图、图片、视频。
 */
@Data
@TableName("content_asset_media")
public class ContentAssetMedia {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属资产 ID */
    private UUID assetId;

    /**
     * 媒体类型：cover / image / video
     */
    private String mediaType;

    /** MinIO 对象 key */
    private String objectKey;

    /**
     * 角色：cover（封面）/ main（主图）/ attachment（附件）
     */
    private String role;

    /** 排序序号 */
    private Integer sortOrder;

    /** 图片宽度（px） */
    private Integer width;

    /** 图片高度（px） */
    private Integer height;

    /** 文件大小（byte） */
    private Long fileSize;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    // ── 媒体类型常量 ───────────────────────────────────────

    public static final String TYPE_COVER = "cover";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";

    // ── 角色常量 ────────────────────────────────────────────

    public static final String ROLE_COVER     = "cover";
    public static final String ROLE_MAIN      = "main";
    public static final String ROLE_ATTACHMENT = "attachment";
}
