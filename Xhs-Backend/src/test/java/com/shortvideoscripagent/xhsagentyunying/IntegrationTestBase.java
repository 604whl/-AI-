package com.shortvideoscripagent.xhsagentyunying;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers 基类：启动 PostgreSQL 和 Redis 容器，
 * 让 mvn test 不依赖本机数据库。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",          // 集成测试禁用 Flyway，由容器初始化
        "spring.datasource.url=${TESTcontainers_POSTGRES_URL}",
        "spring.datasource.username=${TESTcontainers_POSTGRES_USER}",
        "spring.datasource.password=${TESTcontainers_POSTGRES_PASSWORD}",
        "spring.data.redis.host=${TESTcontainers_REDIS_HOST}",
        "spring.data.redis.port=${TESTcontainers_REDIS_PORT}",
})
public abstract class IntegrationTestBase {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("xhsagent")
            .withUsername("xhsagent")
            .withPassword("xhsagent_test")
            .withInitScript("db/migration/V1__init_schema.sql")
            .withReuse(false);

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(false);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("TESTcontainers_POSTGRES_URL", postgres::getJdbcUrl);
        registry.add("TESTcontainers_POSTGRES_USER", postgres::getUsername);
        registry.add("TESTcontainers_POSTGRES_PASSWORD", postgres::getPassword);
        registry.add("TESTcontainers_REDIS_HOST", redis::getHost);
        registry.add("TESTcontainers_REDIS_PORT", () -> redis.getMappedPort(6379));
    }
}
