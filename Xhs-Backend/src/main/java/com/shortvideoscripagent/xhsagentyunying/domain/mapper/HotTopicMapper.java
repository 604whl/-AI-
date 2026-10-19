package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.HotTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
@Mapper
public interface HotTopicMapper extends BaseMapper<HotTopic> {

    @Select("""
            SELECT * FROM app.hot_topic
            WHERE (#{platform} IS NULL OR #{platform} = '''' OR platform = #{platform})
              AND (#{category} IS NULL OR #{category} = '''' OR category = #{category})
              AND (#{promoted} IS NULL OR promoted_to_pool = #{promoted})
            ORDER BY fetched_at DESC
            OFFSET #{offset} LIMIT #{limit}
            """)
    IPage<HotTopic> selectPage(
            Page<HotTopic> page,
            @Param("platform") String platform,
            @Param("category") String category,
            @Param("promoted") Boolean promoted,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    @Select("""
            SELECT COUNT(*) FROM app.hot_topic
            WHERE (#{platform} IS NULL OR #{platform} = '''' OR platform = #{platform})
              AND (#{category} IS NULL OR #{category} = '''' OR category = #{category})
              AND (#{promoted} IS NULL OR promoted_to_pool = #{promoted})
            """)
    long count(
            @Param("platform") String platform,
            @Param("category") String category,
            @Param("promoted") Boolean promoted
    );

    /**
     * Select hot topics by platform + normalized title within a date window, ordered ASC.
     * Uses the normalized_title column (populated by V41 migration) for consistent matching.
     */
    @Select("""
            SELECT * FROM app.hot_topic
            WHERE platform = #{platform}
              AND normalized_title = #{normalizedTitle}
              AND fetched_at >= NOW() - INTERVAL '${windowDays} days'
            ORDER BY fetched_at ASC
            LIMIT 1000
            """)
    List<HotTopic> selectByTitleAndWindow(
            @Param("platform") String platform,
            @Param("normalizedTitle") String normalizedTitle,
            @Param("windowDays") int windowDays
    );
}