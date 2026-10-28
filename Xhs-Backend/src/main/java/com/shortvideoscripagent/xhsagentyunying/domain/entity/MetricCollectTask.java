package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S3-5 指标采集任务台账实体。
 * <p>
 * 对应 {@code app.metric_collect_task}（V52）。采集任务<b>不计费</b>，不写 usage_ledger DEBIT。
 */
@Data
@TableName("metric_collect_task")
public class MetricCollectTask {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private String workspaceId;

    private String platformCode;

    private String accountId;

    /** queued / running / succeeded / failed / dead_letter */
    private String status;

    private Integer attempt;

    private OffsetDateTime windowStart;

    private OffsetDateTime windowEnd;

    private Integer errorCode;

    private String errorMessage;

    private OffsetDateTime lastSuccessAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_DEAD_LETTER = "dead_letter";
}
