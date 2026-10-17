package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.UsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UsageLogMapper extends BaseMapper<UsageLog> {

    /**
     * 按 user 统计当日用量（保留旧 API，兼容未携带 workspaceId 的旧代码路径）。
     */
    @Select("""
            SELECT COUNT(*) FROM usage_log
            WHERE user_id = #{userId}
              AND action = #{action}
              AND created_at >= CURRENT_DATE
            """)
    int countTodayByUserAndAction(@Param("userId") Long userId, @Param("action") String action);

    /**
     * 按 workspace 统计当日用量（多租户隔离后的主路径）。
     */
    @Select("""
            SELECT COUNT(*) FROM usage_log
            WHERE workspace_id = #{workspaceId}
              AND action = #{action}
              AND created_at >= CURRENT_DATE
            """)
    int countTodayByWorkspaceAndAction(@Param("workspaceId") String workspaceId, @Param("action") String action);
}