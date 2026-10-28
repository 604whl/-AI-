package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.RetrospectiveReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * S3-7 复盘报告 Mapper。
 * <p>
 * 所有查询都必须带 {@code workspace_id} 过滤（工作区隔离是硬性要求）。
 * </p>
 */
@Mapper
public interface RetrospectiveReportMapper extends BaseMapper<RetrospectiveReport> {

    /**
     * 按主键 + 工作区查询。
     */
    @Select("SELECT * FROM app.retrospective_report WHERE id = #{id} AND workspace_id = #{workspaceId}")
    RetrospectiveReport selectByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") String workspaceId);

    /**
     * 按幂等键查询（同一工作区内幂等）。
     */
    @Select("""
            SELECT * FROM app.retrospective_report
            WHERE workspace_id = #{workspaceId}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    RetrospectiveReport selectByIdempotencyKey(@Param("workspaceId") String workspaceId,
                                                @Param("idempotencyKey") String idempotencyKey);

    /**
     * 列出工作区内的复盘报告（分页）。
     */
    @Select("""
            SELECT * FROM app.retrospective_report
            WHERE workspace_id = #{workspaceId}
              AND (CAST(#{scopeType} AS VARCHAR) IS NULL OR scope_type = CAST(#{scopeType} AS VARCHAR))
              AND (CAST(#{platformCode} AS VARCHAR) IS NULL OR platform_code = CAST(#{platformCode} AS VARCHAR))
              AND (CAST(#{assetId} AS UUID) IS NULL OR asset_id = CAST(#{assetId} AS UUID))
              AND (CAST(#{from} AS TIMESTAMPTZ) IS NULL OR generated_at >= CAST(#{from} AS TIMESTAMPTZ))
              AND (CAST(#{to} AS TIMESTAMPTZ) IS NULL OR generated_at <= CAST(#{to} AS TIMESTAMPTZ))
            ORDER BY generated_at DESC, created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<RetrospectiveReport> listByWorkspace(@Param("workspaceId") String workspaceId,
                                              @Param("scopeType") String scopeType,
                                              @Param("platformCode") String platformCode,
                                              @Param("assetId") UUID assetId,
                                              @Param("from") OffsetDateTime from,
                                              @Param("to") OffsetDateTime to,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit);

    /**
     * 统计工作区内复盘报告总数（相同过滤条件）。
     */
    @Select("""
            SELECT COUNT(*) FROM app.retrospective_report
            WHERE workspace_id = #{workspaceId}
              AND (CAST(#{scopeType} AS VARCHAR) IS NULL OR scope_type = CAST(#{scopeType} AS VARCHAR))
              AND (CAST(#{platformCode} AS VARCHAR) IS NULL OR platform_code = CAST(#{platformCode} AS VARCHAR))
              AND (CAST(#{assetId} AS UUID) IS NULL OR asset_id = CAST(#{assetId} AS UUID))
              AND (CAST(#{from} AS TIMESTAMPTZ) IS NULL OR generated_at >= CAST(#{from} AS TIMESTAMPTZ))
              AND (CAST(#{to} AS TIMESTAMPTZ) IS NULL OR generated_at <= CAST(#{to} AS TIMESTAMPTZ))
            """)
    long countByWorkspace(@Param("workspaceId") String workspaceId,
                          @Param("scopeType") String scopeType,
                          @Param("platformCode") String platformCode,
                          @Param("assetId") UUID assetId,
                          @Param("from") OffsetDateTime from,
                          @Param("to") OffsetDateTime to);
}
