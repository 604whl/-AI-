package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 连接器健康状态实体（对应 {@code app.connector_health}）。
 * <p>
 * 记录每个连接器的最近成功/失败时间、连续失败次数与降级路径。
 */
@Data
@TableName("connector_health")
public class ConnectorHealth {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 连接器提供方代码 */
    private String connectorProviderCode;

    /** 平台代码 */
    private String platformCode;

    /** 工作区 ID，为空表示全局健康状态 */
    private String workspaceId;

    /** HEALTHY / DEGRADED / UNAVAILABLE / NOT_AUTHORIZED */
    private String status;

    /** 最近一次成功时间 */
    private OffsetDateTime lastSuccessAt;

    /** 最近一次检查时间 */
    private OffsetDateTime lastCheckedAt;

    /** 最近一次错误码 */
    private String lastErrorCode;

    /** 最近一次错误描述 */
    private String lastErrorMessage;

    /** 连续失败次数 */
    private Integer consecutiveFailures;

    /** 降级目标连接器 provider code */
    private String degradedTo;

    private OffsetDateTime updatedAt;
}
