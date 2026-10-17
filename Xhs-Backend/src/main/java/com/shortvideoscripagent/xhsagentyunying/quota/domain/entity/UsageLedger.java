package com.shortvideoscripagent.xhsagentyunying.quota.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用量审计 ledger：记录每一次扣减和冲正。
 */
@Data
@TableName("usage_ledger")
public class UsageLedger {

    /**
     * 主键。
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /**
     * Workspace ID。
     */
    private UUID workspaceId;

    /**
     * 用户 ID（可选）。
     */
    private UUID userId;

    /**
     * 操作类型：ANALYSIS / TITLE / BODY / COVER / CHAT。
     */
    private String action;

    /**
     * 关联任务/会话 ID。
     */
    private UUID refId;

    /**
     * 变更量：+1 扣减 / -1 冲正。
     */
    private Integer delta;

    /**
     * 状态：CONSUMED / REFUNDED。
     */
    private String status;

    /**
     * 冲正时引用原 ledger id。
     */
    private UUID relatedLogId;

    /**
     * 备注原因。
     */
    private String reason;

    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;
}
