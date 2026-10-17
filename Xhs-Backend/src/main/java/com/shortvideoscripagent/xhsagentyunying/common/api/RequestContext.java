package com.shortvideoscripagent.xhsagentyunying.common.api;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;

public final class RequestContext {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_IP = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_AGENT = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static String getRequestId() {
        String id = REQUEST_ID.get();
        return id != null ? id : "req_unknown";
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static Long getUserIdOrNull() {
        return USER_ID.get();
    }

    /**
     * 强制要求当前请求已经写入 userId；缺失时抛 401。
     */
    public static Long requireUserId() {
        Long userId = USER_ID.get();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }

    public static void setRequestIp(String ip) {
        REQUEST_IP.set(ip);
    }

    /**
     * 当前请求的来源 IP。优先取 X-Forwarded-For 首段；缺失时返回 "unknown"。
     */
    public static String getRequestIp() {
        String ip = REQUEST_IP.get();
        return ip != null ? ip : "unknown";
    }

    public static void setUserAgent(String ua) {
        USER_AGENT.set(ua);
    }

    public static String getUserAgent() {
        String ua = USER_AGENT.get();
        return ua != null ? ua : "unknown";
    }

    public static void clear() {
        REQUEST_ID.remove();
        USER_ID.remove();
        REQUEST_IP.remove();
        USER_AGENT.remove();
    }
}
