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

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;

    @Value("${app.storage.minio.bucket:xhs-agent}")
    private String bucketName;

    @Override
    public Health health() {
        try {
            List<String> buckets = minioClient.listBuckets().stream()
                    .map(b -> b.name())
                    .toList();
            boolean bucketExists = false;
            try {
                minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
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