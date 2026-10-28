package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricSyncState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * S3-5 指标采集同步状态 Mapper。
 * <p>
 * 主键是复合键（workspace_id + platform_code + account_id），
 * 所以这里用显式 SQL，不走 {@code updateById}。
 */
@Mapper
public interface MetricSyncStateMapper extends BaseMapper<MetricSyncState> {

    @Select("""
            SELECT * FROM app.metric_sync_state
             WHERE workspace_id = #{workspaceId}
               AND platform_code = #{platformCode}
               AND account_id = #{accountId}
            """)
    MetricSyncState selectState(@Param("workspaceId") String workspaceId,
                                @Param("platformCode") String platformCode,
                                @Param("accountId") String accountId);

    @Select("""
            SELECT * FROM app.metric_sync_state
             WHERE workspace_id = #{workspaceId}
               AND platform_code = #{platformCode}
             ORDER BY account_id ASC
            """)
    List<MetricSyncState> listByPlatform(@Param("workspaceId") String workspaceId,
                                         @Param("platformCode") String platformCode);

    @Insert("""
            INSERT INTO app.metric_sync_state
                (workspace_id, platform_code, account_id, status, consecutive_failures,
                 last_success_at, last_collected_at, last_error, updated_at)
            VALUES (#{workspaceId}, #{platformCode}, #{accountId}, #{status}, #{consecutiveFailures},
                    #{lastSuccessAt}, #{lastCollectedAt}, #{lastError}, #{updatedAt})
            ON CONFLICT (workspace_id, platform_code, account_id) DO UPDATE
               SET status = EXCLUDED.status,
                   consecutive_failures = EXCLUDED.consecutive_failures,
                   last_success_at = EXCLUDED.last_success_at,
                   last_collected_at = EXCLUDED.last_collected_at,
                   last_error = EXCLUDED.last_error,
                   updated_at = EXCLUDED.updated_at
            """)
    int upsert(@Param("workspaceId") String workspaceId,
               @Param("platformCode") String platformCode,
               @Param("accountId") String accountId,
               @Param("status") String status,
               @Param("consecutiveFailures") int consecutiveFailures,
               @Param("lastSuccessAt") OffsetDateTime lastSuccessAt,
               @Param("lastCollectedAt") OffsetDateTime lastCollectedAt,
               @Param("lastError") String lastError,
               @Param("updatedAt") OffsetDateTime updatedAt);
}
