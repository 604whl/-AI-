package com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.WorkspaceQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * WorkspaceQuota Mapper。
 */
@Mapper
public interface WorkspaceQuotaMapper extends BaseMapper<WorkspaceQuota> {

    /**
     * 原子扣减：仅当未超配额时 used + 1。
     * 返回受影响的行数：1 = 成功，0 = 超额或 workspace 不存在。
     */
    @Update("""
            UPDATE app.workspace_quota
            SET used = used + 1,
                updated_at = NOW()
            WHERE workspace_id = #{workspaceId}
              AND (used < daily_quota OR reset_at < NOW())
            """)
    int atomicConsume(@Param("workspaceId") java.util.UUID workspaceId);

    /**
     * 重置已用次数（reset_at 过期时调用）。
     */
    @Update("""
            UPDATE app.workspace_quota
            SET used = 0,
                reset_at = NOW() + INTERVAL '1 day',
                updated_at = NOW()
            WHERE workspace_id = #{workspaceId}
              AND reset_at < NOW()
            """)
    int resetIfExpired(@Param("workspaceId") java.util.UUID workspaceId);

    /**
     * 冲正：used - 1。
     */
    @Update("""
            UPDATE app.workspace_quota
            SET used = GREATEST(used - 1, 0),
                updated_at = NOW()
            WHERE workspace_id = #{workspaceId}
            """)
    int refund(@Param("workspaceId") java.util.UUID workspaceId);
}
