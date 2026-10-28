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
              AND (CAST(#{status} AS VARCHAR) IS NULL OR CAST(#{status} AS VARCHAR) = '' OR status = CAST(#{status} AS VARCHAR))
            ORDER BY created_at DESC
            """)
    IPage<TopicPool> selectPageByWorkspace(
            Page<TopicPool> page,
            @Param("workspaceId") String workspaceId,
            @Param("status") String status
    );

    @Select("""
            SELECT COUNT(*) FROM app.topic_pool
            WHERE workspace_id = #{workspaceId}
              AND (CAST(#{status} AS VARCHAR) IS NULL OR CAST(#{status} AS VARCHAR) = '' OR status = CAST(#{status} AS VARCHAR))
            """)
    long countByWorkspace(@Param("workspaceId") String workspaceId, @Param("status") String status);

    /**
     * Check if a topic with normalized title + source already exists in workspace.
     * Used for bulk import deduplication (LOWER + TRIM equivalent in SQL).
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM app.topic_pool
            WHERE workspace_id = #{workspaceId}
              AND LOWER(TRIM(title)) = LOWER(TRIM(#{normalizedTitle}))
              AND LOWER(TRIM(source)) = LOWER(TRIM(#{source}))
            """)
    boolean existsByNormalizedTitleAndSource(
            @Param("workspaceId") String workspaceId,
            @Param("normalizedTitle") String normalizedTitle,
            @Param("source") String source
    );

    /**
     * Find an existing topic by dedupe key (workspace + source + normalized title).
     * Used in create() dedup check. Returns null if not found.
     */
    @Select("""
            SELECT * FROM app.topic_pool
            WHERE workspace_id = #{workspaceId}
              AND LOWER(TRIM(source)) = LOWER(TRIM(#{source}))
              AND LOWER(TRIM(normalized_title)) = LOWER(TRIM(#{normalizedTitle}))
            LIMIT 1
            """)
    TopicPool selectByDedupeKey(
            @Param("workspaceId") String workspaceId,
            @Param("source") String source,
            @Param("normalizedTitle") String normalizedTitle
    );

}