package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.HotTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * S3-9 热点 Mapper。
 * <p>
 * <b>为什么不用 {@code (? IS NULL OR ...)} 的写法</b>：MyBatis 会把 {@code #{platform}}
 * 编译成 JDBC 占位符 {@code ?}，而 PostgreSQL 无法从 {@code ? IS NULL} 推断参数类型，
 * 直接报 {@code could not determine data type of parameter $1}。
 * 改用 {@code <script>} 动态 SQL —— 参数为 null 时直接不拼该条件，
 * 非 null 时用 {@code CAST(? AS VARCHAR)} 显式标注类型。
 */
@Mapper
public interface HotTopicMapper extends BaseMapper<HotTopic> {

    /**
     * 分页查询热门话题。
     * <p>
     * 默认只返回 {@code status='active'}（去重后的当前最新一条）；
     * 历史快照（{@code superseded}）不删除，供趋势对比。
     * </p>
     */
    @Select("""
            <script>
            SELECT * FROM app.hot_topic
            <where>
              <if test="platform != null and platform != ''">
                AND platform = CAST(#{platform} AS VARCHAR)
              </if>
              <if test="category != null and category != ''">
                AND category = CAST(#{category} AS VARCHAR)
              </if>
              <if test="promoted != null">
                AND promoted_to_pool = CAST(#{promoted} AS BOOLEAN)
              </if>
              <if test="sourceType != null and sourceType != ''">
                AND source_type = CAST(#{sourceType} AS VARCHAR)
              </if>
              <if test="status != null and status != ''">
                AND status = CAST(#{status} AS VARCHAR)
              </if>
            </where>
            ORDER BY fetched_at DESC, id DESC
            </script>
            """)
    IPage<HotTopic> selectPage(
            Page<HotTopic> page,
            @Param("platform") String platform,
            @Param("category") String category,
            @Param("promoted") Boolean promoted,
            @Param("sourceType") String sourceType,
            @Param("status") String status
    );

    /**
     * 统计符合条件的热点数（与 {@link #selectPage} 的过滤条件保持一致）。
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM app.hot_topic
            <where>
              <if test="platform != null and platform != ''">
                AND platform = CAST(#{platform} AS VARCHAR)
              </if>
              <if test="category != null and category != ''">
                AND category = CAST(#{category} AS VARCHAR)
              </if>
              <if test="promoted != null">
                AND promoted_to_pool = CAST(#{promoted} AS BOOLEAN)
              </if>
              <if test="sourceType != null and sourceType != ''">
                AND source_type = CAST(#{sourceType} AS VARCHAR)
              </if>
              <if test="status != null and status != ''">
                AND status = CAST(#{status} AS VARCHAR)
              </if>
            </where>
            </script>
            """)
    long count(
            @Param("platform") String platform,
            @Param("category") String category,
            @Param("promoted") Boolean promoted,
            @Param("sourceType") String sourceType,
            @Param("status") String status
    );

    /**
     * Select hot topics by platform + normalized title within a date window, ordered ASC.
     * Uses the normalized_title column (populated by V41 migration) for consistent matching.
     * <p>
     * 窗口天数用 {@code CAST(? AS INTEGER) * INTERVAL '1 day'} 计算，
     * 不使用 {@code ${}} 字符串拼接，避免 SQL 注入面。
     */
    @Select("""
            SELECT * FROM app.hot_topic
            WHERE platform = CAST(#{platform} AS VARCHAR)
              AND normalized_title = CAST(#{normalizedTitle} AS VARCHAR)
              AND fetched_at >= NOW() - CAST(#{windowDays} AS INTEGER) * INTERVAL '1 day'
            ORDER BY fetched_at ASC
            LIMIT 1000
            """)
    List<HotTopic> selectByTitleAndWindow(
            @Param("platform") String platform,
            @Param("normalizedTitle") String normalizedTitle,
            @Param("windowDays") int windowDays
    );

    /**
     * 取同平台 + 同归一化标题的全部历史快照（不限时间窗），按采集时间升序。
     * <p>
     * 供 {@code GET /hot-topics/{id}/samples} 使用 —— 去重保留历史，不删除旧记录。
     * </p>
     */
    @Select("""
            SELECT * FROM app.hot_topic
            WHERE platform = CAST(#{platform} AS VARCHAR)
              AND normalized_title = CAST(#{normalizedTitle} AS VARCHAR)
            ORDER BY fetched_at ASC, id ASC
            LIMIT 1000
            """)
    List<HotTopic> selectAllByPlatformAndNormalizedTitle(
            @Param("platform") String platform,
            @Param("normalizedTitle") String normalizedTitle
    );

    /**
     * 按归一化标题取最近一条（用于趋势接口推断平台）。
     */
    @Select("""
            SELECT * FROM app.hot_topic
            WHERE normalized_title = CAST(#{normalizedTitle} AS VARCHAR)
            ORDER BY fetched_at DESC, id DESC
            LIMIT 1
            """)
    HotTopic selectLatestByNormalizedTitle(@Param("normalizedTitle") String normalizedTitle);

    /**
     * 去重：把同平台 + 同归一化标题、但不是 {@code keepId} 的记录标为 {@code superseded}。
     * <p>
     * <b>只标状态，不删除</b> —— 历史快照必须保留供趋势对比。
     * </p>
     *
     * @return 被标为 superseded 的行数
     */
    @Update("""
            UPDATE app.hot_topic
            SET status = 'superseded'
            WHERE platform = CAST(#{platform} AS VARCHAR)
              AND normalized_title = CAST(#{normalizedTitle} AS VARCHAR)
              AND id <> CAST(#{keepId} AS BIGINT)
              AND status = 'active'
            """)
    int supersedeOthers(
            @Param("platform") String platform,
            @Param("normalizedTitle") String normalizedTitle,
            @Param("keepId") Long keepId
    );

    /**
     * 把同平台 + 同归一化标题的全部记录（含最新一条）都标为 superseded。
     * <p>
     * 供"先标历史、再插入新的 active 记录"的两步写法使用。
     * </p>
     */
    @Update("""
            UPDATE app.hot_topic
            SET status = 'superseded'
            WHERE platform = CAST(#{platform} AS VARCHAR)
              AND normalized_title = CAST(#{normalizedTitle} AS VARCHAR)
              AND status = 'active'
            """)
    int supersedeAll(
            @Param("platform") String platform,
            @Param("normalizedTitle") String normalizedTitle
    );
}
