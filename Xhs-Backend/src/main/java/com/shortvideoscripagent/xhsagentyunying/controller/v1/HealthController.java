package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公网健康检查端点。
 * <p>
 * 与 Spring Boot Actuator 集成，对外只暴露"总体状态 + 关键依赖的 UP/DOWN 摘要"，不泄露
 * 内部细节（如数据库连接 URL、Redis host 等）。
 * <p>
 * 生产部署建议用 Nginx / SLB 把 {@code /api/v1/health} 暴露给健康检查工具，
 * 并把 Actuator 自身的 endpoints（如 {@code /actuator/health}）保持在内网或关闭。
 */
@RestController
@RequestMapping("/v1")
public class HealthController {

    private final HealthEndpoint healthEndpoint;

    public HealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            HealthComponent health = healthEndpoint.health();
            Status status = health.getStatus();
            body.put("status", Status.UP.equals(status) ? "UP" : "DOWN");
            Map<String, Object> components = new LinkedHashMap<>();
            if (health instanceof CompositeHealth composite) {
                composite.getComponents().forEach((name, component) -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("status", component.getStatus().getCode());
                    components.put(name, detail);
                });
            }
            body.put("components", components);
        } catch (Exception ex) {
            body.put("status", "DOWN");
            body.put("components", Map.of());
        }
        return ApiResponse.ok(body);
    }
}
