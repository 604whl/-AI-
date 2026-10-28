package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * S3-3 排期 Mapper。
 * <p>
 * <b>工作区隔离是硬性要求</b>：所有查询都必须带上 {@code workspace_id = ?}。
 */
@Mapper
public interface PublishScheduleMapper extends BaseMapper<PublishSchedule> {

    /**
     * 按主键 + 工作区查询（所有权校验：不属于本 workspace 一律视为未找到）。
     */
    @Select("SELECT * FROM app.publish_schedule WHERE id = #{id} AND workspace_id = #{workspaceId}")
    PublishSchedule selectByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") String workspaceId);

    /**
     * 冲突检测：同一工作区 + 同一平台 + 同一账号 + 同一时刻（精确到分钟）已有非终态排期。
     * <p>
     * 非终态 = {@code scheduled} / {@code executing}。
     * <b>排除自身</b>（重试场景用）。
     */
    @Select("""
            SELECT * FROM app.publish_schedule
             WHERE workspace_id = #{workspaceId}
               AND platform_code = #{platformCode}
               AND COALESCE(account_id, '') = COALESCE(#{accountId}, '')
               AND status IN ('scheduled', 'executing')
               AND date_trunc('minute', scheduled_at) = date_trunc('minute', #{scheduledAt})
               AND (CAST(#{excludeId} AS UUID) IS NULL OR id <> CAST(#{excludeId} AS UUID))
             LIMIT 1
            """)
    PublishSchedule selectConflicting(@Param("workspaceId") String workspaceId,
                                       @Param("platformCode") String platformCode,
                                       @Param("accountId") String accountId,
                                       @Param("scheduledAt") OffsetDateTime scheduledAt,
                                       @Param("excludeId") UUID excludeId);

    /**
     * 列出工作区内的排期（可按平台 / 状态 / 时间范围过滤）。
     */
    @Select("""
            SELECT * FROM app.publish_schedule
             WHERE workspace_id = #{workspaceId}
               AND (CAST(#{platformCode} AS VARCHAR) IS NULL OR platform_code = CAST(#{platformCode} AS VARCHAR))
               AND (CAST(#{status} AS VARCHAR) IS NULL OR status = CAST(#{status} AS VARCHAR))
               AND (CAST(#{from} AS TIMESTAMPTZ) IS NULL OR scheduled_at >= CAST(#{from} AS TIMESTAMPTZ))
               AND (CAST(#{to} AS TIMESTAMPTZ) IS NULL OR scheduled_at <= CAST(#{to} AS TIMESTAMPTZ))
             ORDER BY scheduled_at ASC
            """)
    List<PublishSchedule> selectListFiltered(@Param("workspaceId") String workspaceId,
                                             @Param("platformCode") String platformCode,
                                             @Param("status") String status,
                                             @Param("from") OffsetDateTime from,
                                             @Param("to") OffsetDateTime to);

    /**
     * 查询到期但仍未执行的排期（用于标记 expired，不静默丢弃）。
     */
    @Select("""
            SELECT * FROM app.publish_schedule
             WHERE workspace_id = #{workspaceId}
               AND status = 'scheduled'
               AND scheduled_at < #{now}
             ORDER BY scheduled_at ASC
            """)
    List<PublishSchedule> selectOverdueScheduled(@Param("workspaceId") String workspaceId,
                                                 @Param("now") OffsetDateTime now);
}
