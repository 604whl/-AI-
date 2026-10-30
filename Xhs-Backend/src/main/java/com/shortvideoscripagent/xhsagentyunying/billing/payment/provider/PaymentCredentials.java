package com.shortvideoscripagent.xhsagentyunying.billing.payment.provider;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * S4-2 渠道凭据读取工具。
 * <p>
 * <b>安全约定</b>：
 * <ul>
 *   <li>凭据只从<b>环境变量</b>读取，<b>不落库</b>、<b>不落配置明文</b>；</li>
 *   <li>本类<b>不提供任何 toString / 日志输出凭据</b>的方法；</li>
 *   <li>凭据值永远不会出现在异常消息里（只出现<b>缺失的键名</b>）。</li>
 * </ul>
 * 切换真实渠道时<b>只需补环境变量</b>，代码零改动。
 */
@Slf4j
public final class PaymentCredentials {

    private PaymentCredentials() {
    }

    /**
     * 读取环境变量；缺失或空白返回 {@code null}。
     */
    public static String env(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 批量读取；返回的 Map 只包含<b>非空</b>项。
     */
    public static Map<String, String> envs(String... keys) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (String key : keys) {
            String value = env(key);
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * 列出<b>缺失</b>的配置键名（不含值，可安全写日志）。
     */
    public static java.util.List<String> missing(String... keys) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String key : keys) {
            if (env(key) == null) {
                missing.add(key);
            }
        }
        return missing;
    }

    /**
     * 判断是否全部配置齐全。
     */
    public static boolean allPresent(String... keys) {
        return missing(keys).isEmpty();
    }
}
