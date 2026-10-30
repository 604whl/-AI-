package com.shortvideoscripagent.xhsagentyunying.billing.balance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S4-3 工作区余额实体（{@code app.workspace_balance}，V64）。
 * <p>
 * 按量计费（USAGE 套餐）的预付余额形态。余额扣减由 {@code BalanceService} 用
 * 单条原子 SQL（{@code UPDATE ... WHERE balance >= ?}）完成，并发下不会出现负余额。
 * </p>
 */
@Data
@TableName("workspace_balance")
public class WorkspaceBalance {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 工作区 ID（VARCHAR(32)） */
    private String workspaceId;

    /** 可用余额 NUMERIC(18,4) */
    private BigDecimal balance;

    private String currency;

    /** 冻结金额（未支付账单占用） */
    private BigDecimal frozen;

    /** active / frozen / closed */
    private String status;

    private OffsetDateTime updatedAt;

    private OffsetDateTime createdAt;
}
