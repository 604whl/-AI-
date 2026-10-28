package com.shortvideoscripagent.xhsagentyunying.connector;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 连接器注册表。
 * <p>
 * Spring 启动时收集所有 {@link PlatformConnector} bean，按 {@code providerCode} 建索引。
 * <p>
 * <b>授权门禁在加载期生效</b>：未通过授权检查（平台被禁用 / 授权矩阵中 {@code authorized=FALSE}）
 * 的连接器<b>不会注册到注册表</b>，因此业务代码根本拿不到它 —— 从根上杜绝"绕过门禁直接调用"。
 * 被拒绝的连接器会记录 WARN 日志，方便运维发现配置问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectorRegistry {

    private final List<PlatformConnector> connectors;
    private final ConnectorAuthorizationService authorizationService;

    /** providerCode → 连接器 */
    private final Map<String, PlatformConnector> byProviderCode = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        List<PlatformConnector> candidates = connectors == null ? List.of() : connectors;
        for (PlatformConnector connector : candidates) {
            String providerCode = connector.providerCode();
            String platformCode = connector.platformCode();
            try {
                if (!authorizationService.isAuthorized(platformCode, providerCode)) {
                    log.warn("Connector {} ({}) rejected by authorization gate — not registered",
                            providerCode, platformCode);
                    continue;
                }
                PlatformConnector previous = byProviderCode.put(providerCode, connector);
                if (previous != null) {
                    log.warn("Duplicate connector providerCode {} — the later bean replaces the earlier one",
                            providerCode);
                }
                log.info("Connector registered: {} platform={} level={}",
                        providerCode, platformCode, connector.level());
            } catch (Exception e) {
                log.warn("Failed to evaluate authorization for connector {}: {}", providerCode, e.getMessage());
            }
        }
        log.info("Connector registry initialized with {} connector(s)", byProviderCode.size());
    }

    /**
     * 按 providerCode 查找连接器。
     */
    public Optional<PlatformConnector> find(String providerCode) {
        if (providerCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byProviderCode.get(providerCode));
    }

    /**
     * 全部已注册连接器。
     */
    public List<PlatformConnector> listAll() {
        return new ArrayList<>(byProviderCode.values());
    }

    /**
     * 某平台下的全部已注册连接器。
     */
    public List<PlatformConnector> listByPlatform(String platformCode) {
        if (platformCode == null) {
            return List.of();
        }
        return byProviderCode.values().stream()
                .filter(c -> platformCode.equalsIgnoreCase(c.platformCode()))
                .toList();
    }

    /**
     * 已注册的 providerCode 列表。
     */
    public List<String> listProviderCodes() {
        return new ArrayList<>(byProviderCode.keySet());
    }

    /**
     * 已注册连接器数量。
     */
    public int size() {
        return byProviderCode.size();
    }
}
