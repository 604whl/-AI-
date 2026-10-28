package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.HotTopicCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 热点采集缓存 Mapper。
 */
@Mapper
public interface HotTopicCacheMapper extends BaseMapper<HotTopicCache> {

    /**
     * 按 (platform, category) 取缓存；category 传 {@code ""} 表示不限品类。
     */
    @Select("""
            SELECT * FROM app.hot_topic_cache
            WHERE platform = #{platform} AND category = #{category}
            LIMIT 1
            """)
    HotTopicCache selectByPlatformAndCategory(
            @Param("platform") String platform,
            @Param("category") String category
    );

    /**
     * 判断缓存是否仍然有效：{@code fetched_at + ttl_seconds > now()}。
     * <p>
     * <b>缓存命中判定</b>：行存在 且 该表达式为 true → 命中，不重复调用外部源。
     * </p>
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM app.hot_topic_cache
            WHERE platform = #{platform}
              AND category = #{category}
              AND fetched_at + (ttl_seconds * INTERVAL '1 second') > NOW()
            """)
    boolean isCacheValid(
            @Param("platform") String platform,
            @Param("category") String category
    );

    /**
     * upsert 缓存行（唯一键 platform + category）。
     */
    @Update("""
            INSERT INTO app.hot_topic_cache (platform, category, payload, fetched_at, ttl_seconds)
            VALUES (#{platform}, #{category}, CAST(#{payload} AS jsonb), NOW(), #{ttlSeconds})
            ON CONFLICT (platform, category) DO UPDATE
               SET payload = EXCLUDED.payload,
                   fetched_at = EXCLUDED.fetched_at,
                   ttl_seconds = EXCLUDED.ttl_seconds
            """)
    int upsert(
            @Param("platform") String platform,
            @Param("category") String category,
            @Param("payload") String payload,
            @Param("ttlSeconds") Integer ttlSeconds
    );
}
