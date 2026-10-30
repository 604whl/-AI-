package com.shortvideoscripagent.xhsagentyunying.billing.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 幂等重放回读工具。
 * <p>
 * <b>为什么需要它</b>：PostgreSQL 中任何约束冲突（唯一索引）都会把
 * <b>当前事务置为 aborted</b>，此后该事务内的任何 SQL 都会抛
 * {@code current transaction is aborted, commands ignored until end of transaction block}。
 * <p>
 * 于是这类写法是错的：
 * <pre>{@code
 * try {
 *     orderMapper.insert(order);          // 唯一冲突 -> 事务 aborted
 * } catch (DuplicateKeyException ex) {
 *     return orderMapper.selectByIdempotencyKey(ws, key);  // <- 在 aborted 事务里查，必然抛错 -> 500
 * }
 * }</pre>
 * <p>
 * 本工具在 <b>独立事务</b>（{@code PROPAGATION_REQUIRES_NEW}）中执行回读，
 * 并把外层事务标记为回滚（外层事务已经 aborted，本就无数据可提交）。
 * 结果是「并发重复提交同一幂等键」返回既有资源，而不是 500。
 * </p>
 */
@Slf4j
@Component
public class IdempotencyReplay {

    private final TransactionTemplate requiresNew;

    public IdempotencyReplay(PlatformTransactionManager transactionManager) {
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 在独立事务中回读既有资源。
     * <p>
     * 回读失败（返回 null 或抛异常）时返回 {@code null}，由调用方决定如何兜底。
     * </p>
     */
    public <T> T read(Supplier<T> loader) {
        try {
            return requiresNew.execute(status -> loader.get());
        } catch (Exception ex) {
            log.error("Idempotency replay read failed: {}", ex.getMessage(), ex);
            return null;
        }
    }
}
