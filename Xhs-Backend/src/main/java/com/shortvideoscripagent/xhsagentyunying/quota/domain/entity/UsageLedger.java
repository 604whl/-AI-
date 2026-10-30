package com.shortvideoscripagent.xhsagentyunying.quota.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用量审计 ledger：记录每一次扣减和冲正。
 * <p>
 * <b>S4-3 扩展（V64，只加列不改语义）</b>：
 * <ul>
 *   <li>{@code delta} 语义不变：+1 扣减 / -1 冲正；</li>
 *   <li>新增 {@code unitPrice / amount / priceVersion / statementId / provider}，
 *       用于把单次用量与价格表快照绑定，使账单金额<b>可复算</b>；</li>
 *   <li>历史数据 {@code amount} 允许为 NULL（不回填、不加 NOT NULL）。</li>
 * </ul>
 * </p>
 * <p>
 * 注意：本实体新增列都是普通列（NUMERIC / INT / VARCHAR），<b>没有 jsonb</b>，
 * 因此不要加 {@code autoResultMap = true}——那会触发 MyBatis-Plus 的全局 TypeHandler 校验。
 * </p>
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
    private String workspaceId;

    /**
     * 用户 ID（可选）。
     */
    private Long userId;

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
     * S4-3：计费单价快照（NUMERIC(18,4)），取自 app.price_book；历史数据为 NULL。
     */
    private BigDecimal unitPrice;

    /**
     * S4-3：计费金额快照（NUMERIC(18,4)）= unitPrice * delta；历史数据允许 NULL。
     */
    private BigDecimal amount;

    /**
     * S4-3：价格版本，对应 app.price_book.version，用于按历史版本回算成本。
     */
    private Integer priceVersion;

    /**
     * S4-3：已归集到的账单 ID；NULL 表示尚未出账。
     */
    private String statementId;

    /**
     * S4-3：供应商/模型来源（如 dashscope / mock），用于成本归因。
     */
    private String provider;

    /**
     * S4-3（P0-11）：幂等去重键。
     * <p>
     * <b>语义</b>：只有「一次业务事件只应计费 1 次」的调用点才写入，
     * 格式为 {@code <ACTION>:<ref_id>}。{@code null} 表示该行不参与去重
     * （例如同一会话的多条 Agent 消息各自计费、余额流水、冲正行）。
     * </p>
     * <p>
     * <b>作用</b>：数据库上有部分唯一索引
     * {@code (workspace_id, dedupe_key) WHERE status='CONSUMED'}，
     * 作为任务重试 / 孤儿任务恢复场景下的并发最终防线。
     * </p>
     */
    private String dedupeKey;

    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;
}
