package com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.mapper;

import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.entity.SubscriptionWriteIdempotency;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * S4-4 订阅写操作幂等键 Mapper（{@code app.subscription_write_idempotency}，V66）。
 */
@Mapper
public interface SubscriptionWriteIdempotencyMapper {

    /**
     * <b>幂等写入</b>：命中主键冲突时静默跳过。
     * <p>
     * <b>为什么用 {@code ON CONFLICT DO NOTHING} 而不是「先 insert 再 catch 异常」</b>：
     * PostgreSQL 中任何语句失败都会把整个事务置为 <i>aborted</i> 状态，
     * 后续所有语句都会抛 {@code current transaction is aborted, commands ignored until
     * end of transaction block}。若靠捕获 {@code DuplicateKeyException} 来处理冲突，
     * 冲突本身已经污染了事务 —— 回查订阅行的 {@code SELECT} 同样会失败，
     * 导致幂等重放退化成 500（与 V65 的 dedupe_key 同一教训）。
     * {@code ON CONFLICT DO NOTHING} 不产生错误，事务保持健康。
     * </p>
     * <p>
     * <b>返回行数</b>：1 = 首次写入（本次真正执行）；0 = 已存在（幂等重放）。
     * </p>
     */
    @Insert("""
            INSERT INTO app.subscription_write_idempotency
                (workspace_id, scope, idempotency_key, resource_id, created_at)
            VALUES
                (CAST(#{workspaceId} AS VARCHAR),
                 CAST(#{scope} AS VARCHAR),
                 CAST(#{idempotencyKey} AS VARCHAR),
                 CAST(#{resourceId} AS UUID),
                 NOW())
            ON CONFLICT (workspace_id, scope, idempotency_key)
                DO NOTHING
            """)
    int insertIdempotent(@Param("workspaceId") String workspaceId,
                         @Param("scope") String scope,
                         @Param("idempotencyKey") String idempotencyKey,
                         @Param("resourceId") UUID resourceId);

    /**
     * 取既有幂等记录（回溯用）。
     */
    @Select("""
            SELECT * FROM app.subscription_write_idempotency
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND scope = CAST(#{scope} AS VARCHAR)
              AND idempotency_key = CAST(#{idempotencyKey} AS VARCHAR)
            LIMIT 1
            """)
    SubscriptionWriteIdempotency selectOne(@Param("workspaceId") String workspaceId,
                                           @Param("scope") String scope,
                                           @Param("idempotencyKey") String idempotencyKey);
}