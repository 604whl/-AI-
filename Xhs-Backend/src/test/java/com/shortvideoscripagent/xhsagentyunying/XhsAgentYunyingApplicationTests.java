package com.shortvideoscripagent.xhsagentyunying;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试：使用 Testcontainers 启动真实 PostgreSQL 和 Redis。
 * <p>
 * 继承自 {@link IntegrationTestBase}，只验证 Spring context 可以正常加载。
 * 完整的业务测试在具体测试类中实现。
 * </p>
 */
@ActiveProfiles("test")
class XhsAgentYunyingApplicationTests extends IntegrationTestBase {

    @Test
    void contextLoads() {
        // Spring Boot 应用上下文正常加载即通过
    }
}
