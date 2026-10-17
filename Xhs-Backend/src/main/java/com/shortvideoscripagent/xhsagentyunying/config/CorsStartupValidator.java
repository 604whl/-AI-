package com.shortvideoscripagent.xhsagentyunying.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * CORS 启动校验：生产/非本地 profile 下，{@code app.cors.allowed-origins} 必须是 https
 * 且不能包含 localhost / 127.0.0.1，列表必须非空。
 * <p>
 * 失败直接拒绝启动，避免部署时忘记改 CORS 配置就被发布到公网。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorsStartupValidator implements ApplicationRunner {

    private static final Set<String> FORBIDDEN_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "[::1]"
    );

    private final Environment environment;

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void run(ApplicationArguments args) {
        if (isLocalOrTestProfile()) {
            return;
        }
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS is required outside local/test. Set app.cors.allowed-origins to a comma-separated list of https origins."
            );
        }
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        for (String origin : origins) {
            if (!origin.startsWith("https://") && !origin.startsWith("http://")) {
                throw new IllegalStateException("CORS origin must be http(s) URL, got: " + origin);
            }
            if (origin.startsWith("http://")) {
                log.warn("CORS origin uses plain http (acceptable for staging), but https is required for production: {}", origin);
            }
            String host = extractHost(origin);
            if (host != null && FORBIDDEN_HOSTS.contains(host.toLowerCase())) {
                throw new IllegalStateException(
                        "CORS origin must not point to localhost / 127.0.0.1 in non-local profile: " + origin
                );
            }
        }
        log.info("CORS allowed origins: {}", origins);
    }

    private boolean isLocalOrTestProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "local".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p));
    }

    private static String extractHost(String origin) {
        try {
            String stripped = origin.replaceFirst("^https?://", "");
            int slash = stripped.indexOf('/');
            int colon = stripped.indexOf(':');
            int end = stripped.length();
            if (slash >= 0) end = Math.min(end, slash);
            if (colon >= 0) end = Math.min(end, colon);
            return stripped.substring(0, end);
        } catch (Exception e) {
            return null;
        }
    }
}
