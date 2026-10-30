package com.shortvideoscripagent.xhsagentyunying.billing.payment;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;

/**
 * S4-2 支付相关错误码常量。
 * <p>
 * 分段约定（见 {@code common/exception/GlobalExceptionHandler}）：
 * 4xx = 调用方 / 参数问题，5xx = 服务端或上游渠道问题。
 * </p>
 */
public final class PaymentErrors {

    /** 渠道未配置（503）：环境变量缺失，需要运维补配置 */
    public static final int PROVIDER_NOT_CONFIGURED = 50301;
    /** 渠道不可用（503）：渠道返回系统繁忙 / 网络不通 */
    public static final int PROVIDER_UNAVAILABLE = 50302;
    /** 上游渠道故障（502）：渠道返回错误 / 超时 */
    public static final int PROVIDER_ERROR = 50201;
    /** 上游渠道超时（502） */
    public static final int PROVIDER_TIMEOUT = 50202;

    /** 未知渠道（400） */
    public static final int PROVIDER_UNKNOWN = 40011;
    /** 回调验签失败（400） */
    public static final int SIGNATURE_INVALID = 40012;
    /** 回调时间戳超出窗口（400） */
    public static final int TIMESTAMP_OUT_OF_WINDOW = 40013;
    /** 回调报文无法解析（400） */
    public static final int CALLBACK_MALFORMED = 40014;
    /** 订单参数非法（400） */
    public static final int ORDER_INVALID = 40015;

    /** 订单不存在（404） */
    public static final int ORDER_NOT_FOUND = 40412;
    /** 订单状态冲突（409） */
    public static final int ORDER_STATE_CONFLICT = 40912;
    /** 回调金额与订单金额不一致（409） */
    public static final int AMOUNT_MISMATCH = 40913;
    /** 回调重放（409）：同一渠道事件 ID 已处理 */
    public static final int CALLBACK_REPLAY = 40914;

    private PaymentErrors() {
    }

    public static BusinessException notConfigured(String provider) {
        return new BusinessException(PROVIDER_NOT_CONFIGURED,
                "PAYMENT_PROVIDER_NOT_CONFIGURED:" + provider);
    }

    public static BusinessException providerError(String provider, String reason) {
        return new BusinessException(PROVIDER_ERROR, "payment_provider_error:" + provider + ":" + reason);
    }

    public static BusinessException providerTimeout(String provider) {
        return new BusinessException(PROVIDER_TIMEOUT, "payment_provider_timeout:" + provider);
    }
}
