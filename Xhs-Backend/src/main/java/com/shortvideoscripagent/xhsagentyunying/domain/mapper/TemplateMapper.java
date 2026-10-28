package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Template;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Template Mapper（P0-4）。
 */
@Mapper
public interface TemplateMapper extends BaseMapper<Template> {

    /**
     * 分页查询模板列表，按 scope 隔离可见性。
     * <ul>
     *   <li>OFFICIAL：所有人可见（忽略 workspaceId / userId）</li>
     *   <li>WORKSPACE：同 workspace_id</li>
     *   <li>PERSONAL：仅 owner_user_id = userId</li>
     * </ul>
     * <p>
     * 分页由 MyBatis-Plus 的 {@code PaginationInnerInterceptor} 负责，
     * SQL 中不要再手写 OFFSET / LIMIT，否则会与插件重复分页。
     */
            @Select("""
            SELECT * FROM app.template t
            WHERE t.status = 'active'
              AND (
                (t.scope = 'OFFICIAL')
                OR (t.scope = 'WORKSPACE' AND t.workspace_id = #{workspaceId})
                OR (t.scope = 'PERSONAL' AND t.owner_user_id = #{ownerUserId})
              )
              AND (CAST(#{category} AS VARCHAR) IS NULL OR CAST(#{category} AS VARCHAR) = '' OR t.category = CAST(#{category} AS VARCHAR))
            ORDER BY t.use_count DESC, t.updated_at DESC
            """)
    IPage<Template> selectPageByScope(
            Page<Template> page,
            @Param("workspaceId") String workspaceId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("category") String category
    );

    /**
     * 统计 OFFICIAL 模板数量（用于判断是否需要初始化种子数据）。
    @Select("SELECT COUNT(*) FROM app.template WHERE scope = 'OFFICIAL' AND status = 'active'")
    long countOfficialActive();

    /**
     * 批量插入（用于种子数据初始化）。
    @Select("<script>" +
            "INSERT INTO app.template (id, scope, category, scenario, persona, title, body_template, " +
            "variables, default_goal, default_tone, version, status, use_count, created_at, updated_at) " +
            "VALUES " +
            "<foreach collection='list' item='t' separator=','>" +
            "(#{t.id}, #{t.scope}, #{t.category}, #{t.scenario}, #{t.persona}, #{t.title}, #{t.bodyTemplate}, " +
            "#{t.variables}::jsonb, #{t.defaultGoal}, #{t.defaultTone}, #{t.version}, #{t.status}, #{t.useCount}, #{t.createdAt}, #{t.updatedAt})" +
            "</foreach>" +
            "</script>")
    void batchInsert(@Param("list") List<Template> list);

    /**
     * 递增 use_count。
     */
    @Select("UPDATE app.template SET use_count = use_count + 1, updated_at = NOW() WHERE id = #{templateId}")
    void incrementUseCount(@Param("templateId") UUID templateId);

    @Select("""
            SELECT COUNT(*) FROM app.template_apply_log l
            JOIN app.content_asset a ON l.asset_id = a.id
            WHERE l.template_id = #{templateId}
              AND a.status = 'published'
            """)
    long selectAdoptionCount(@Param("templateId") UUID templateId);

    @Select("""
            SELECT t.id AS template_id, t.title,
                   COUNT(l.id) AS apply_count_30d,
                   CASE WHEN COUNT(l.id) = 0 THEN 0.0
                        ELSE ROUND(
                            COUNT(CASE WHEN a.status = 'published' THEN 1 END)::numeric
                            / GREATEST(COUNT(l.id), 1) * 100.0, 2)
                   END AS adoption_rate
            FROM app.template t
            LEFT JOIN app.template_apply_log l
                ON t.id = l.template_id
               AND l.created_at >= #{from}
            LEFT JOIN app.content_asset a ON l.asset_id = a.id
            WHERE t.workspace_id = #{wsId}
              AND t.status = 'active'
            GROUP BY t.id, t.title
            ORDER BY COUNT(l.id) DESC
            LIMIT #{limit}
            """)
    List<LeaderboardEntry> selectLeaderboard(
            @Param("wsId") String wsId,
            @Param("from") OffsetDateTime from,
            @Param("limit") int limit
    );

    interface LeaderboardEntry {
        UUID getTemplateId();
        String getTitle();
        Long getApplyCount30d();
        Double getAdoptionRate();
    }
}
