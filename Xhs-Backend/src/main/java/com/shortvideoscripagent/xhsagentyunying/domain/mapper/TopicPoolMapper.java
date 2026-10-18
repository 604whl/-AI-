package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.TopicPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TopicPoolMapper extends BaseMapper<TopicPool> {

    @Select("""
            SELECT * FROM app.topic_pool
            WHERE workspace_id = #{workspaceId}
              AND (#{status} IS NULL OR #{status} = '' OR status = #{status})
            ORDER BY created_at DESC
            OFFSET #{offset} LIMIT #{limit}
            """)
    IPage<TopicPool> selectPageByWorkspace(
            Page<TopicPool> page,
            @Param("workspaceId") Long workspaceId,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    @Select("""
            SELECT COUNT(*) FROM app.topic_pool
            WHERE workspace_id = #{workspaceId}
              AND (#{status} IS NULL OR #{status} = '' OR status = #{status})
            """)
    long countByWorkspace(@Param("workspaceId") Long workspaceId, @Param("status") String status);
}