package com.shortvideoscripagent.xhsagentyunying.billing.balance.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.balance.domain.entity.WorkspaceBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * S4-3 工作区余额 Mapper。
 * <p>
 * <b>原子扣减</b>：{@link #atomicDebit} 用单条 UPDATE 完成「校验 + 扣减」，
 * 受影响行数为 0 即表示余额不足或账户不存在，由 Service 层抛可读错误。
 * 并发下不会出现负余额（数据库行锁 + 条件更新）。
 * </p>
 */
@Mapper
public interface WorkspaceBalanceMapper extends BaseMapper<WorkspaceBalance> {

    /**
     * 原子扣减余额：<b>可用余额</b>足够才扣。
     * <p>
     * 可用余额口径 = {@code balance - frozen}，与 {@code BalanceService#toView}
     * 的 {@code available} 字段、以及 {@link #atomicFreeze} 的判定条件保持一致。
     * <p>
     * <b>为什么不能只判 {@code balance >= amount}</b>：{@code frozen} 是已被未支付账单
     * 占用的金额。若扣减只看 {@code balance}，则被冻结的钱会被消费两次
     * （一次被账单冻结，一次被按量消费扣走），导致余额可为负。
     * </p>
     *
     * @return 受影响行数；1 = 扣减成功，0 = 可用余额不足 / 账户不存在
     */
    @Update("""
            UPDATE app.workspace_balance
            SET balance = balance - CAST(#{amount} AS NUMERIC),
                updated_at = NOW()
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND status = 'active'
              AND balance - frozen >= CAST(#{amount} AS NUMERIC)
            """)
    int atomicDebit(@Param("workspaceId") String workspaceId,
                    @Param("amount") BigDecimal amount);

    /**
     * 原子入账（充值 / 退款回补）。
     *
     * @return 受影响行数
     */
    @Update("""
            UPDATE app.workspace_balance
            SET balance = balance + CAST(#{amount} AS NUMERIC),
                updated_at = NOW()
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND status <> 'closed'
            """)
    int atomicCredit(@Param("workspaceId") String workspaceId,
                     @Param("amount") BigDecimal amount);

    /**
     * 原子冻结金额（未支付账单占用）。
     */
    @Update("""
            UPDATE app.workspace_balance
            SET frozen = frozen + CAST(#{amount} AS NUMERIC),
                updated_at = NOW()
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND status = 'active'
              AND balance - frozen >= CAST(#{amount} AS NUMERIC)
            """)
    int atomicFreeze(@Param("workspaceId") String workspaceId,
                     @Param("amount") BigDecimal amount);

    /**
     * 原子解冻金额。
     */
    @Update("""
            UPDATE app.workspace_balance
            SET frozen = frozen - CAST(#{amount} AS NUMERIC),
                updated_at = NOW()
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND frozen >= CAST(#{amount} AS NUMERIC)
            """)
    int atomicUnfreeze(@Param("workspaceId") String workspaceId,
                       @Param("amount") BigDecimal amount);

    /**
     * 按工作区取余额行。
     */
    @Select("""
            SELECT * FROM app.workspace_balance
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
            """)
    WorkspaceBalance selectByWorkspace(@Param("workspaceId") String workspaceId);

    /**
     * 按 id 取余额行。
     */
    @Select("""
            SELECT * FROM app.workspace_balance WHERE id = CAST(#{id} AS UUID)
            """)
    WorkspaceBalance selectByUuid(@Param("id") UUID id);
}
