package com.shortvideoscripagent.xhsagentyunying.quota;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Quota 模块配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.quota")
public class QuotaProperties {

    /**
     * 默认每日配额（workspace 无配置时使用）。
     */
    private int defaultDailyQuota = 100;

    /**
     * Cron 表达式，定时重置过期配额（默认每日 UTC 00:00）。
     */
    private String resetCron = "0 0 0 * * *";

    /**
     * 懒创建配额时，是否立即设置为已过期（触发第一次自动重置）。
     */
    private boolean lazyInitExpired = false;
}
