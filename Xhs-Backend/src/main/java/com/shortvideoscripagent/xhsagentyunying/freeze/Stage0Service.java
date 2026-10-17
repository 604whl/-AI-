package com.shortvideoscripagent.xhsagentyunying.freeze;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 阶段 0：产品冻结的核心服务。
 * <p>
 * 提供：
 * <ul>
 *   <li>平台连接等级校验：判断某平台某能力是否被允许</li>
 *   <li>平台 × 连接器授权校验：运行时拒绝未授权连接</li>
 *   <li>内容状态机校验</li>
 *   <li>合规扫描聚合</li>
 *   <li>计费计量单位标准化</li>
 * </ul>
 * <p>
 * 所有方法都是无状态的，可被任意线程并发调用。
 */
@Service
public class Stage0Service {

    private static final Logger LOG = LoggerFactory.getLogger(Stage0Service.class);

    private final Stage0Repository repository;
    private final ObjectMapper objectMapper;

    public Stage0Service(Stage0Repository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 列出所有平台（按 display_name 排序）。
     */
    public List<PlatformEntity> listPlatforms() {
        List<PlatformEntity> platforms = repository.listAllPlatforms();
        if (platforms == null) {
            return Collections.emptyList();
        }
        return platforms.stream()
                .sorted((a, b) -> a.getDisplayName().compareTo(b.getDisplayName()))
                .collect(Collectors.toList());
    }

    /**
     * 校验某平台是否被允许"写入"操作。
     * <p>
     * 阶段 0 默认：连接等级非 MANUAL_IMPORT / DISABLED 才允许写入。
     * <p>
     * 未在数据库中注册的平台视为"未知"，一律拒绝。
     */
    public boolean canPlatformWrite(String platformCode) {
        if (platformCode == null || platformCode.isBlank()) {
            return false;
        }
        Optional<PlatformEntity> opt = repository.findPlatform(platformCode.toUpperCase());
        if (opt.isEmpty()) {
            LOG.warn("stage0: platform '{}' is not in the freeze list, default deny", platformCode);
            return false;
        }
        PlatformEntity p = opt.get();
        if (!Boolean.TRUE.equals(p.getWriteEnabled())) {
            return false;
        }
        ConnectorLevel level = ConnectorLevel.of(p.getConnectorLevel());
        return level != null && level.canWrite();
    }

    /**
     * 校验某平台是否被允许"读取"操作。
     */
    public boolean canPlatformRead(String platformCode) {
        if (platformCode == null || platformCode.isBlank()) {
            return false;
        }
        Optional<PlatformEntity> opt = repository.findPlatform(platformCode.toUpperCase());
        if (opt.isEmpty()) {
            return false;
        }
        PlatformEntity p = opt.get();
        if (!Boolean.TRUE.equals(p.getReadEnabled())) {
            return false;
        }
        ConnectorLevel level = ConnectorLevel.of(p.getConnectorLevel());
        return level != null && level.canRead();
    }

    /**
     * 校验是否允许某条转换。状态机本身已经判断合法性；本方法补足业务上下文（角色、所有权）。
     */
    public void requireTransitionAllowed(ContentStatus from, ContentStatus to) {
        if (!ContentStatus.canTransition(from, to)) {
            throw new IllegalStateException(
                    "阶段 0 状态机禁止 " + (from == null ? "null" : from.name())
                            + " → " + (to == null ? "null" : to.name())
                            + " 转换；如需调整请升级产品冻结文档");
        }
    }

    /**
     * 把数据库列存的 String 形式转换为 ConnectorLevel 枚举。
     */
    public ConnectorLevel toConnectorLevel(String raw) {
        return ConnectorLevel.of(raw);
    }

    /**
     * 把数据库列存的 String 转换为 RiskLevel 枚举。
     */
    public RiskLevel toRiskLevel(String raw) {
        return RiskLevel.tryParse(raw).orElse(RiskLevel.LOW);
    }

    /**
     * 友好的内部枚举别名（不引入新概念，与 PlatformConnectorLevel 等价）。
     */
    public enum ConnectorLevel {
        OFFICIAL_API, AUTHORIZED_CONNECTOR, BROWSER_ASSIST, MANUAL_IMPORT, DISABLED;

        public boolean canWrite() {
            return this == OFFICIAL_API || this == AUTHORIZED_CONNECTOR || this == BROWSER_ASSIST;
        }

        public boolean canRead() {
            return this != DISABLED;
        }

        public static ConnectorLevel of(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return ConnectorLevel.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    /**
     * 把任意 AI 操作归一为可计费单位。外部 service 调用前必须先归一。
     * <p>
     * 例如 title service 调用前调用 {@code normalize(ACTION_TITLE_BATCH)}，返回 {@link BillableUnit#TITLE_GENERATION}。
     */
    public BillableUnit normalizeBillableUnit(String actionName) {
        if (actionName == null) {
            return BillableUnit.ANALYSIS;
        }
        String upper = actionName.trim().toUpperCase();
        return switch (upper) {
            case "ANALYSIS", "ANALYZE" -> BillableUnit.ANALYSIS;
            case "TITLE", "TITLE_GENERATION", "TITLES" -> BillableUnit.TITLE_GENERATION;
            case "BODY", "BODY_GENERATION", "CONTENT_GENERATION" -> BillableUnit.BODY_GENERATION;
            case "COVER", "COVER_ANALYSIS" -> BillableUnit.COVER_ANALYSIS;
            case "EXPORT", "EXPORTS" -> BillableUnit.EXPORT;
            case "EMBEDDING", "EMBED" -> BillableUnit.EMBEDDING;
            case "SEARCH", "WEB_SEARCH" -> BillableUnit.SEARCH;
            default -> {
                LOG.debug("unknown action '{}', fallback to ANALYSIS", actionName);
                yield BillableUnit.ANALYSIS;
            }
        };
    }

    /**
     * 解析 JSON 字符串为 JsonNode，错误时返回空对象。
     */
    public Optional<JsonNode> parseJsonSafely(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readTree(raw));
        } catch (Exception ex) {
            LOG.debug("stage0: parseJsonSafely failed: {}", ex.toString());
            return Optional.empty();
        }
    }

    /**
     * 把一组 Map 安全转换为 JSON Array 字符串。
     */
    public List<String> stringifySafely(List<?> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return Collections.singletonList(objectMapper.writeValueAsString(list));
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
