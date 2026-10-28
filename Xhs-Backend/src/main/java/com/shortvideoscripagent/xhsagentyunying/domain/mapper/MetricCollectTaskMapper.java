package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricCollectTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * S3-5 指标采集任务台账 Mapper。
 */
@Mapper
public interface MetricCollectTaskMapper extends BaseMapper<MetricCollectTask> {

    @Select("SELECT * FROM app.metric_collect_task WHERE id = #{id} AND workspace_id = #{workspaceId}")
    MetricCollectTask selectByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") String workspaceId);

    @Select("""
            SELECT * FROM app.metric_collect_task
             WHERE workspace_id = #{workspaceId}
               AND platform_code = #{platformCode}
             ORDER BY created_at DESC
             LIMIT 50
            """)
    List<MetricCollectTask> listRecent(@Param("workspaceId") String workspaceId,
                                       @Param("platformCode") String platformCode);
}
