package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricForecast;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * S3-6 预测 Mapper。
 * <p>
 * 所有查询都必须带 {@code workspace_id} 过滤（工作区隔离是硬性要求）。
 * </p>
 */
@Mapper
public interface MetricForecastMapper extends BaseMapper<MetricForecast> {

    /**
     * 按主键 + 工作区查询。
     */
    @Select("SELECT * FROM app.metric_forecast WHERE id = #{id} AND workspace_id = #{workspaceId}")
    MetricForecast selectByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") String workspaceId);

    /**
     * 按幂等键查询（同一工作区内幂等）。
     */
    @Select("""
            SELECT * FROM app.metric_forecast
            WHERE workspace_id = #{workspaceId}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    MetricForecast selectByIdempotencyKey(@Param("workspaceId") String workspaceId,
                                            @Param("idempotencyKey") String idempotencyKey);

    /**
     * 列出工作区内的预测（可选过滤）。
     */
    @Select("""
            SELECT * FROM app.metric_forecast
            WHERE workspace_id = #{workspaceId}
              AND (CAST(#{assetId} AS UUID) IS NULL OR asset_id = CAST(#{assetId} AS UUID))
              AND (CAST(#{platformCode} AS VARCHAR) IS NULL OR platform_code = CAST(#{platformCode} AS VARCHAR))
              AND (CAST(#{forecastType} AS VARCHAR) IS NULL OR forecast_type = CAST(#{forecastType} AS VARCHAR))
            ORDER BY generated_at DESC, created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<MetricForecast> listByWorkspace(@Param("workspaceId") String workspaceId,
                                         @Param("assetId") UUID assetId,
                                         @Param("platformCode") String platformCode,
                                         @Param("forecastType") String forecastType,
                                         @Param("offset") long offset,
                                         @Param("limit") int limit);

    /**
     * 统计工作区内预测总数（相同过滤条件）。
     */
    @Select("""
            SELECT COUNT(*) FROM app.metric_forecast
            WHERE workspace_id = #{workspaceId}
              AND (CAST(#{assetId} AS UUID) IS NULL OR asset_id = CAST(#{assetId} AS UUID))
              AND (CAST(#{platformCode} AS VARCHAR) IS NULL OR platform_code = CAST(#{platformCode} AS VARCHAR))
              AND (CAST(#{forecastType} AS VARCHAR) IS NULL OR forecast_type = CAST(#{forecastType} AS VARCHAR))
            """)
    long countByWorkspace(@Param("workspaceId") String workspaceId,
                          @Param("assetId") UUID assetId,
                          @Param("platformCode") String platformCode,
                          @Param("forecastType") String forecastType);

    /**
     * 查询某资产最近的预测（复盘时用；无资产时按工作区取最近一条）。
     */
    @Select("""
            SELECT * FROM app.metric_forecast
            WHERE workspace_id = #{workspaceId}
              AND (CAST(#{assetId} AS UUID) IS NULL OR asset_id = CAST(#{assetId} AS UUID))
              AND (CAST(#{platformCode} AS VARCHAR) IS NULL OR platform_code = CAST(#{platformCode} AS VARCHAR))
            ORDER BY generated_at DESC, created_at DESC
            LIMIT 1
            """)
    MetricForecast selectLatest(@Param("workspaceId") String workspaceId,
                                 @Param("assetId") UUID assetId,
                                 @Param("platformCode") String platformCode);
}
