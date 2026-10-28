package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * S3-4 发布任务 Mapper。
 * <p>
 * <b>工作区隔离是硬性要求</b>：所有查询都必须带上 {@code workspace_id = ?}。
 */
@Mapper
public interface PublishTaskMapper extends BaseMapper<PublishTask> {

    /**
     * 按主键 + 工作区查询（所有权校验：不属于本 workspace 一律视为未找到）。
     */
    @Select("SELECT * FROM app.publish_task WHERE id = #{id} AND workspace_id = #{workspaceId}")
    PublishTask selectByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") String workspaceId);

    /**
     * 按幂等键查询（唯一约束的兜底读取路径）。
     */
    @Select("SELECT * FROM app.publish_task WHERE idempotency_key = #{idempotencyKey}")
    PublishTask selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 按排期查询最近一次任务（重试时复用原任务的幂等键）。
     */
    @Select("""
            SELECT * FROM app.publish_task
             WHERE workspace_id = #{workspaceId}
               AND schedule_id = #{scheduleId}
             ORDER BY created_at DESC
             LIMIT 1
            """)
    PublishTask selectLatestBySchedule(@Param("workspaceId") String workspaceId,
                                       @Param("scheduleId") UUID scheduleId);
}
