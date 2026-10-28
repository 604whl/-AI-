package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * S3-5 指标采集同步状态实体。
 * <p>
 * 对应 {@code app.metric_sync_state}（V52）。仅记录采集侧状态；
 * 连接器健康（app.connector_health）由 S3-1 负责，本表不写那张表。
 */
@Data
@TableName("metric_sync_state")
public class MetricSyncState {

    @TableId(type = IdType.INPUT)
    private String workspaceId;

    private String platformCode;

    /** 空字符串表示平台级（未指定账号） */
    private String accountId;

    /** HEALTHY / DEGRADED / UNKNOWN */
    private String status;

    private Integer consecutiveFailures;

    private OffsetDateTime lastSuccessAt;

    private OffsetDateTime lastCollectedAt;

    private String lastError;

    private OffsetDateTime updatedAt;

    public static final String STATUS_HEALTHY = "HEALTHY";
    public static final String STATUS_DEGRADED = "DEGRADED";
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    /** 连续失败达到该次数即标记 DEGRADED */
    public static final int DEGRADED_THRESHOLD = 3;
}
