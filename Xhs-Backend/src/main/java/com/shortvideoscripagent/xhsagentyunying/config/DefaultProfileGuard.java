package com.shortvideoscripagent.xhsagentyunying.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 部署安全护栏：未显式设置 SPRING_PROFILES_ACTIVE 时强制要求以 prod 启动。
 * <p>
 * 设计目标：
 * <ul>
 *   <li>避免"忘记设 profile 就直接以 local 启动"被部署到公网后留下 localhost JWT 密钥、localhost CORS 等隐患。</li>
 *   <li>本地开发保留兼容：设置 {@code SPRING_PROFILES_ACTIVE=local} 即可正常启动。</li>
 *   <li>CI / 单元测试场景：设置 {@code XHS_ALLOW_DEFAULT_PROFILE=true}（或环境变量
 *       {@code xhs.allow-default-profile=true}）可绕过强制校验。</li>
 * </ul>
 * <p>
 * 注册方式：META-INF/spring.factories（旧）或 spring.factories 替代品。
 * 这里使用 Spring Boot 3 通用的 {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}。
 */
public class DefaultProfileGuard implements EnvironmentPostProcessor {

    private static final String ALLOW_FLAG = "xhs.allow-default-profile";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return;
        }
        String allow = environment.getProperty(ALLOW_FLAG);
        if ("true".equalsIgnoreCase(allow)) {
            return;
        }
        // 没有显式 profile 也没有放行标记 → 直接抛错中断启动
        Map<String, Object> props = new HashMap<>();
        props.put("spring.profiles.active", "prod");
        // 注意：这里不实际切换，只把建议写到控制台和异常信息里。
        // 实际启动仍由 application.yml 中 ${SPRING_PROFILES_ACTIVE:local} 控制。
        throw new IllegalStateException(
                "SPRING_PROFILES_ACTIVE is not set. "
                        + "Production deployment MUST set SPRING_PROFILES_ACTIVE=prod explicitly. "
                        + "For local dev, set SPRING_PROFILES_ACTIVE=local or XHS_ALLOW_DEFAULT_PROFILE=true (CI only). "
                        + "Suggested active profile: prod"
        );
    }
}
