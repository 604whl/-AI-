package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AnalysisTaskMapper extends BaseMapper<AnalysisTask> {

    /**
     * 原子化取消任务。
     * <p>
     * 仅当 status 处于 {@code pending} 或 {@code running} 时更新为 {@code cancelled}，
     * 避免与已完成/已失败任务产生竞态。
     * </p>
     *
     * @return 实际受影响的行数（0 = 已被其他 worker 改写或任务不存在）
     */
    @Update("""
            UPDATE app.analysis_task
               SET status = 'cancelled',
                   finished_at = now(),
                   updated_at = now(),
                   failure_reason = COALESCE(failure_reason, 'cancelled_by_user')
             WHERE id = #{taskId}
               AND workspace_id = #{workspaceId}
               AND status IN ('pending', 'running', 'queued')
            """)
    int cancelIfActive(@Param("taskId") String taskId,
                       @Param("workspaceId") String workspaceId);
}
