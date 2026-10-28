package com.shortvideoscripagent.xhsagentyunying.connector;

/**
 * 连接器错误码。
 * <p>
 * 用于健康状态落库时的失败分类。
 */
public enum ConnectorErrorCode {

    /** 授权过期 */
    AUTH_EXPIRED,

    /** 被平台限流 */
    RATE_LIMITED,

    /** 平台页面/接口结构变化 */
    PLATFORM_CHANGED,

    /** 网络错误 */
    NETWORK_ERROR,

    /** 未授权 */
    NOT_AUTHORIZED,

    /** 未知错误 */
    UNKNOWN
}
