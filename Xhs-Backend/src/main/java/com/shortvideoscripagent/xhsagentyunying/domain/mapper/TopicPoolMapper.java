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
     * <p>
     * <b>注意</b>：这里用 {@code LOWER(TRIM(title))} 而非 {@code normalized_title} 列。
     * {@code app.topic_pool} 上<b>没有</b> normalized_title 触发器（对比 app.hot_topic 有），
     * 该列只在 V40 迁移时对存量数据回填过一次，之后所有 INSERT 都写 NULL；
     * 若按列比较，去重查询永远查不到已有行，重复导入会不断插入重复选题。
     * {@code existsByNormalizedTitleAndSource} 同理，已经使用 title 的 LOWER+TRIM 表达式。
     * </p>
     */
    @Select("""
            SELECT * FROM app.topic_pool
            WHERE workspace_id = #{workspaceId}
              AND LOWER(TRIM(source)) = LOWER(TRIM(#{source}))
              AND LOWER(TRIM(title)) = LOWER(TRIM(#{normalizedTitle}))
            LIMIT 1
            """)
    TopicPool selectByDedupeKey(
            @Param("workspaceId") String workspaceId,
            @Param("source") String source,
            @Param("normalizedTitle") String normalizedTitle
    );

}