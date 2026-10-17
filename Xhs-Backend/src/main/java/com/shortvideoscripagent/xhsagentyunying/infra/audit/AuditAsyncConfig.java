package com.shortvideoscripagent.xhsagentyunying.infra.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 审计写入专用线程池。
 * <p>
 * 与请求线程隔离，避免审计落库慢阻塞主流程。
 * </p>
 */
@Configuration
@EnableAsync
public class AuditAsyncConfig {

    @Bean("auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler((r, e) -> {
            // 队列满时由调用线程兜底执行，确保审计不丢
            r.run();
        });
        executor.initialize();
        return executor;
    }
}
