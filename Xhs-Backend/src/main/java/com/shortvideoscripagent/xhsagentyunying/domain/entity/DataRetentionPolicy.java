package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 数据保留策略（P0-9）。
 * <p>
 * 每个 workspace 有一份保留策略，控制各类数据的保留天数。
 */
@Data
@TableName("data_retention_policy")
public class DataRetentionPolicy {

    @TableId(type = IdType.ASSIGN_UUID)
    private String workspaceId;

    /** 内容资产保留天数，默认 365 */
    private Integer assetRetentionDays = 365;

    /** 使用日志保留天数，默认 90 */
    private Integer logRetentionDays = 90;

    /** 媒体文件保留天数，默认 180 */
    private Integer mediaRetentionDays = 180;

    /** 聊天会话保留天数，默认 180 */
    private Integer chatSessionRetentionDays = 180;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private Long updatedBy;
}
