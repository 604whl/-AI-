package com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.entity;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S4-4 订阅写操作幂等键实体（{@code app.subscription_write_idempotency}，V66）。
 * <p>
 * <b>为什么单独建表</b>：{@code app.subscription.idempotency_key} 只有一列，
 * 而订阅行上会先后发生多种写操作（subscribe / trial / cancel / change-plan / admin-adjust）。
 * 早期实现把<b>原始键</b>写进那一列，SQL 守卫是 {@code idempotency_key IS NULL}，
 * 于是先到的操作占住这一列，后到的操作永远绑不上：
 * <ul>
 *   <li>{@code subscribe} 写入 K1 后，{@code cancel} 想绑 K2 时 UPDATE 影响 0 行，
 *       K2 <b>从未落库</b> -&gt; 同一 K2 重复提交不会被识别为重放；</li>
 *   <li>K1 也因此在 cancel 之后<b>彻底丢失</b>。</li>
 * </ul>
 * <p>
 * 本表以 {@code (workspace_id, scope, idempotency_key)} 为主键，
 * {@code scope} 区分操作种类，不同 scope 的同一个原始键互不抢占，
 * 各操作都能独立识别重放。
 * <p>
 * <b>幂等判定</b>：写入用 {@code INSERT ... ON CONFLICT DO NOTHING}，
 * 返回 1 = 本次首次写入（真正执行）；0 = 已存在（幂等重放）。
 */
@Data
public class SubscriptionWriteIdempotency {

    /** 所属工作区 */
    private String workspaceId;

    /** 操作作用域：subscribe / trial / cancel / change_plan / admin_adjust */
    private String scope;

    /** 客户端提供的 Idempotency-Key */
    private String idempotencyKey;

    /** 被本幂等键作用的订阅行 id（app.subscription.id） */
    private UUID resourceId;

    /** 首次写入时间 */
    private OffsetDateTime createdAt;
}