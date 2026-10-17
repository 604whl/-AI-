package com.shortvideoscripagent.xhsagentyunying.freeze;

/**
 * 阶段 0：计费事件类型。
 * <p>
 * 详见 <code>docs/BILLING-RULES.md</code>。
 */
public enum BillableEvent {
    /** 扣费：用户成功调用一次 AI 操作 */
    DEBIT,
    /** 退款：调用失败或被拒绝，给用户返还额度 */
    REFUND,
    /** 冲正：任务因超时/重复请求撤销的扣费 */
    WRITE_OFF,
    /** 调整：人工调整（运营活动、客服补偿等） */
    ADJUSTMENT
}
