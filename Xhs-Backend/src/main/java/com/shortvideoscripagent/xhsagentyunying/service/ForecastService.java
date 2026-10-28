package com.shortvideoscripagent.xhsagentyunying.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.connector.MetricKeys;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricForecast;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MetricForecastMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.forecast.ForecastCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.forecast.ForecastResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * S3-6 预测存储服务。
 * <p>
 * <b>核心约束</b>：
 * <ul>
 *   <li>预测值只存 {@code metric_forecast.predicted_metrics}（统一口径 10 键，缺失补 0），
 *       实际值只存 {@code app.metric_snapshot.metrics}，<b>绝不混在同一列</b></li>
 *   <li>预测必须带来源与时间：{@code model_name} / {@code prompt_version} / {@code kb_version} /
 *       {@code generated_at}</li>
 *   <li>资产归属校验：{@code asset_id} 必须属于当前 workspace，越权按"未找到"处理</li>
 *   <li>幂等：同一 workspace 内 {@code Idempotency-Key} 重复提交返回同一条预测</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    // ── 错误码 ──────────────────────────────────────────────────────────
    /** 400 预测字段非法 */
    public static final int CODE_FORECAST_FIELDS_INVALID = 40071;
    /** 404 资产不存在 / 越权 */
    public static final int CODE_ASSET_NOT_FOUND = 40450;

    /** 允许的预测类型 */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            MetricForecast.TYPE_TITLE_CTR,
            MetricForecast.TYPE_IMPRESSION,
            MetricForecast.TYPE_ENGAGEMENT,
            MetricForecast.TYPE_CONVERSION);

    /** 允许的可信度 */
    private static final Set<String> ALLOWED_CONFIDENCE = Set.of(
            MetricForecast.CONFIDENCE_HIGH,
            MetricForecast.CONFIDENCE_MEDIUM,
            MetricForecast.CONFIDENCE_LOW,
            MetricForecast.CONFIDENCE_UNVERIFIED);

    /** 默认提示词版本 */
    private static final String DEFAULT_PROMPT_VERSION = "forecast-1.0.0";

    private final MetricForecastMapper forecastMapper;
    private final ContentAssetMapper contentAssetMapper;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════════════
    // 创建预测
    // ══════════════════════════════════════════════════════════════════

    /**
     * 创建预测（幂等）。
     *
     * @param idempotencyKey 可空；非空时同一 workspace 内重复提交返回既有预测
     */
    @Transactional
    public ForecastResponse create(String workspaceId, Long userId,
                                    ForecastCreateRequest request, String idempotencyKey) {
        requireWorkspace(workspaceId);

        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            MetricForecast existing = forecastMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                log.info("Forecast create skipped (idempotent): ws={} key={}", workspaceId, key);
                return toResponse(existing);
            }
        }

        String forecastType = upper(request.getForecastType());
        if (forecastType == null || !ALLOWED_TYPES.contains(forecastType)) {
            throw new BusinessException(CODE_FORECAST_FIELDS_INVALID,
                    "FORECAST_FIELDS_INVALID: forecast_type_not_allowed");
        }
        String confidence = upper(request.getConfidence());
        if (confidence == null) {
            confidence = MetricForecast.CONFIDENCE_LOW;
        }
        if (!ALLOWED_CONFIDENCE.contains(confidence)) {
            throw new BusinessException(CODE_FORECAST_FIELDS_INVALID,
                    "FORECAST_FIELDS_INVALID: invalid_confidence");
        }

        UUID assetId = resolveAndValidateAsset(workspaceId, request.getAssetId());
        String platformCode = upper(request.getPlatformCode());
        if (platformCode == null && assetId != null) {
            ContentAsset asset = contentAssetMapper.selectById(assetId);
            platformCode = asset == null ? null : upper(asset.getPlatform());
        }

        // 统一口径：只保留 10 个核心键，缺失补 0
        Map<String, Long> core = normalizeCoreMetrics(request.getPredictedMetrics());

        MetricForecast forecast = new MetricForecast();
        forecast.setId(UUID.randomUUID());
        forecast.setWorkspaceId(workspaceId);
        forecast.setAssetId(assetId);
        forecast.setPlatformCode(platformCode);
        forecast.setForecastType(forecastType);
        forecast.setPredictedMetrics(writeJson(core));
        forecast.setPredictedPublishedAt(request.getPredictedPublishedAt());
        forecast.setModelName(blankToNull(request.getModelName()));
        forecast.setPromptVersion(blankToNull(request.getPromptVersion()) == null
                ? DEFAULT_PROMPT_VERSION : request.getPromptVersion().trim());
        forecast.setKbVersion(blankToNull(request.getKbVersion()));
        forecast.setConfidence(confidence);
        forecast.setGeneratedAt(OffsetDateTime.now());
        forecast.setSourceTaskId(parseUuid(request.getSourceTaskId()));
        forecast.setIdempotencyKey(key);
        forecast.setCreatedAt(OffsetDateTime.now());

        try {
            forecastMapper.insert(forecast);
        } catch (DuplicateKeyException ex) {
            if (key == null) {
                throw ex;
            }
            MetricForecast existing = forecastMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                log.info("Forecast create skipped (idempotent race): ws={} key={}", workspaceId, key);
                return toResponse(existing);
            }
            throw ex;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("forecastId", forecast.getId().toString());
        meta.put("assetId", assetId == null ? null : assetId.toString());
        meta.put("forecastType", forecastType);
        auditWriter.record("forecast.create", "METRIC_FORECAST", forecast.getId().toString(), meta);

        log.info("Forecast created: id={} ws={} asset={} type={}",
                forecast.getId(), workspaceId, assetId, forecastType);
        return toResponse(forecast);
    }

    // ══════════════════════════════════════════════════════════════════
    // 列表
    // ══════════════════════════════════════════════════════════════════

    /**
     * 分页列出当前工作区的预测。
     */
    public PaginatedResponse<ForecastResponse> list(String workspaceId, String assetId,
                                                    String platformCode, String forecastType,
                                                    int page, int size) {
        requireWorkspace(workspaceId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        long offset = (long) (safePage - 1) * safeSize;

        UUID parsedAsset = parseUuid(assetId);
        String type = upper(forecastType);
        String platform = upper(platformCode);

        List<ForecastResponse> items = forecastMapper.listByWorkspace(workspaceId, parsedAsset,
                        platform, type, offset, safeSize)
                .stream().map(this::toResponse).toList();
        long total = forecastMapper.countByWorkspace(workspaceId, parsedAsset, platform, type);
        return new PaginatedResponse<>(items, total, safePage, safeSize);
    }

    /**
     * 按 ID 查询预测（工作区隔离）。
     */
    public ForecastResponse get(String workspaceId, UUID forecastId) {
        requireWorkspace(workspaceId);
        MetricForecast forecast = forecastMapper.selectByIdAndWorkspace(forecastId, workspaceId);
        if (forecast == null) {
            throw new BusinessException(CODE_ASSET_NOT_FOUND, "FORECAST_NOT_FOUND");
        }
        return toResponse(forecast);
    }

    // ══════════════════════════════════════════════════════════════════
    // 内部工具
    // ══════════════════════════════════════════════════════════════════

    /**
     * 归属校验：资产必须属于当前 workspace，否则按"未找到"处理。
     */
    private UUID resolveAndValidateAsset(String workspaceId, String rawAssetId) {
        if (rawAssetId == null || rawAssetId.isBlank()) {
            return null;
        }
        UUID assetId = parseUuid(rawAssetId);
        if (assetId == null) {
            throw new BusinessException(CODE_ASSET_NOT_FOUND, "ASSET_NOT_FOUND");
        }
        ContentAsset asset = contentAssetMapper.selectById(assetId);
        if (asset == null || !workspaceId.equals(asset.getWorkspaceId())) {
            throw new BusinessException(CODE_ASSET_NOT_FOUND, "ASSET_NOT_FOUND");
        }
        return assetId;
    }

    /**
     * 统一口径归一：只保留 {@link MetricKeys#CORE_KEYS}，缺失补 0，核心键之外的字段剔除。
     */
    private Map<String, Long> normalizeCoreMetrics(Map<String, Object> input) {
        Map<String, Long> core = new LinkedHashMap<>();
        for (String key : MetricKeys.CORE_KEYS) {
            core.put(key, 0L);
        }
        if (input == null) {
            return core;
        }
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null || !MetricKeys.CORE_KEYS.contains(key)) {
                // 非核心键不进入预测口径
                continue;
            }
            core.put(key, toLong(entry.getValue()));
        }
        return core;
    }

    private ForecastResponse toResponse(MetricForecast forecast) {
        return ForecastResponse.builder()
                .id(forecast.getId())
                .assetId(forecast.getAssetId())
                .platformCode(forecast.getPlatformCode())
                .forecastType(forecast.getForecastType())
                .predictedMetrics(readCoreMetrics(forecast.getPredictedMetrics()))
                .predictedPublishedAt(forecast.getPredictedPublishedAt())
                .confidence(forecast.getConfidence())
                .source(ForecastResponse.ForecastSource.builder()
                        .modelName(forecast.getModelName())
                        .promptVersion(forecast.getPromptVersion())
                        .kbVersion(forecast.getKbVersion())
                        .generatedAt(forecast.getGeneratedAt())
                        .build())
                .createdAt(forecast.getCreatedAt())
                .build();
    }

    private Map<String, Long> readCoreMetrics(String json) {
        Map<String, Long> core = new LinkedHashMap<>();
        for (String key : MetricKeys.CORE_KEYS) {
            core.put(key, 0L);
        }
        if (json == null || json.isBlank()) {
            return core;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            for (String key : MetricKeys.CORE_KEYS) {
                core.put(key, toLong(parsed.get(key)));
            }
        } catch (Exception ex) {
            log.warn("Failed to parse forecast predicted_metrics: {}", ex.getMessage());
        }
        return core;
    }

    private void requireWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_FORECAST_FIELDS_INVALID,
                    "FORECAST_FIELDS_INVALID: workspace_id_required");
        }
    }

    private String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    private String upper(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Failed to serialize forecast JSON: {}", ex.getMessage());
            return "{}";
        }
    }
}
