package com.shortvideoscripagent.xhsagentyunying.infra.health;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioHealthIndicator implements HealthIndicator {

    /**
     * MinIO 客户端，允许为空。
     * <p>
     * 项目里没有定义 {@code MinioClient} bean（各存储服务都是自行 {@code MinioClient.builder()} 构建），
     * 因此这里必须允许注入为空，否则 {@code local} 存储模式下应用会因缺少该 bean 直接启动失败。
     */
    private final Optional<MinioClient> minioClient;

    @Value("${app.storage.minio.bucket:xhs-agent}")
    private String bucketName;

    @Override
    public Health health() {
        if (minioClient == null || minioClient.isEmpty()) {
            return Health.up()
                    .withDetail("bucket", bucketName)
                    .withDetail("status", "not_configured")
                    .build();
        }
        MinioClient client = minioClient.get();
        try {
            List<String> buckets = client.listBuckets().stream()
                    .map(b -> b.name())
                    .toList();
            boolean bucketExists = false;
            try {
                client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
                bucketExists = true;
            } catch (Exception ignored) {
            }
            return Health.up()
                    .withDetail("bucket", bucketName)
                    .withDetail("bucketExists", bucketExists)
                    .withDetail("buckets", buckets)
                    .withDetail("status", "connected")
                    .build();
        } catch (Exception ex) {
            log.warn("MinIO health check failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}