package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricComparison;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * S3-6 偏差对比 Mapper。
 * <p>
 * 所有查询都必须带 {@code workspace_id} 过滤（工作区隔离是硬性要求）。
 * </p>
 */
@Mapper
public interface MetricComparisonMapper extends BaseMapper<MetricComparison> {

    /**
     * 按主键 + 工作区查询。
     */
    @Select("SELECT * FROM app.metric_comparison WHERE id = #{id} AND workspace_id = #{workspaceId}")
    MetricComparison selectByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") String workspaceId);

    /**
     * 按幂等键查询（同一工作区内幂等）。
     */
    @Select("""
            SELECT * FROM app.metric_comparison
            WHERE workspace_id = #{workspaceId}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    MetricComparison selectByIdempotencyKey(@Param("workspaceId") String workspaceId,
                                              @Param("idempotencyKey") String idempotencyKey);

    /**
     * 某预测 + 某快照的全部维度对比行。
     */
    @Select("""
            SELECT * FROM app.metric_comparison
            WHERE workspace_id = #{workspaceId}
              AND forecast_id = #{forecastId}
              AND snapshot_id = #{snapshotId}
            ORDER BY dimension ASC
            """)
    List<MetricComparison> selectByForecastAndSnapshot(@Param("workspaceId") String workspaceId,
                                                        @Param("forecastId") UUID forecastId,
                                                        @Param("snapshotId") UUID snapshotId);

    /**
     * 列出工作区内的对比行（可选过滤）。
     */
    @Select("""
            SELECT * FROM app.metric_comparison
            WHERE workspace_id = #{workspaceId}
              AND (CAST(#{assetId} AS UUID) IS NULL OR asset_id = CAST(#{assetId} AS UUID))
              AND (CAST(#{dimension} AS VARCHAR) IS NULL OR dimension = CAST(#{dimension} AS VARCHAR))
              AND (CAST(#{hit} AS VARCHAR) IS NULL OR hit = CAST(#{hit} AS VARCHAR))
            ORDER BY computed_at DESC, created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<MetricComparison> listByWorkspace(@Param("workspaceId") String workspaceId,
                                            @Param("assetId") UUID assetId,
                                            @Param("dimension") String dimension,
                                            @Param("hit") String hit,
                                            @Param("offset") long offset,
                                            @Param("limit") int limit);

    /**
     * 统计工作区内对比行总数（相同过滤条件）。
     */
    @Select("""
            SELECT COUNT(*) FROM app.metric_comparison
            WHERE workspace_id = #{workspaceId}
              AND (CAST(#{assetId} AS UUID) IS NULL OR asset_id = CAST(#{assetId} AS UUID))
              AND (CAST(#{dimension} AS VARCHAR) IS NULL OR dimension = CAST(#{dimension} AS VARCHAR))
              AND (CAST(#{hit} AS VARCHAR) IS NULL OR hit = CAST(#{hit} AS VARCHAR))
            """)
    long countByWorkspace(@Param("workspaceId") String workspaceId,
                          @Param("assetId") UUID assetId,
                          @Param("dimension") String dimension,
                          @Param("hit") String hit);

    /**
     * 列出工作区内全部对比行（复盘聚合用，不分页）。
     */
    @Select("""
            SELECT * FROM app.metric_comparison
            WHERE workspace_id = #{workspaceId}
              AND (CAST(#{assetId} AS UUID) IS NULL OR asset_id = CAST(#{assetId} AS UUID))
            ORDER BY computed_at DESC, created_at DESC
            """)
    List<MetricComparison> listAllForWorkspace(@Param("workspaceId") String workspaceId,
                                               @Param("assetId") UUID assetId);
}
