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
 * Template Mapper锛圥0-4锛夈€? */
@Mapper
public interface TemplateMapper extends BaseMapper<Template> {

    /**
     * 鍒嗛〉鏌ヨ妯℃澘鍒楄〃锛屾寜 scope 闅旂鍙鎬с€?     * <ul>
     *   <li>OFFICIAL锛氭墍鏈変汉鍙锛堝拷鐣?workspaceId / userId锛?/li>
     *   <li>WORKSPACE锛氬悓 workspace_id</li>
     *   <li>PERSONAL锛氫粎 owner_user_id = userId</li>
     * </ul>
     */
    @Select("""
            SELECT * FROM app.template t
            WHERE t.status = 'active'
              AND (
                (t.scope = 'OFFICIAL')
                OR (t.scope = 'WORKSPACE' AND t.workspace_id = #{workspaceId})
                OR (t.scope = 'PERSONAL' AND t.owner_user_id = #{ownerUserId})
              )
              AND (#{category} IS NULL OR #{category} = '' OR t.category = #{category})
            ORDER BY t.use_count DESC, t.updated_at DESC
            OFFSET #{offset} LIMIT #{limit}
            """)
    IPage<Template> selectPageByScope(
            Page<Template> page,
            @Param("workspaceId") UUID workspaceId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("category") String category,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    /**
     * 缁熻 OFFICIAL 妯℃澘鏁伴噺锛堢敤浜庡垽鏂槸鍚﹂渶瑕佸垵濮嬪寲绉嶅瓙鏁版嵁锛夈€?     */
    @Select("SELECT COUNT(*) FROM app.template WHERE scope = 'OFFICIAL' AND status = 'active'")
    long countOfficialActive();

    /**
     * 鎵归噺鎻掑叆锛堢敤浜庣瀛愭暟鎹垵濮嬪寲锛夈€?     */
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
     * 閫掑 use_count銆?     */
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
            @Param("wsId") UUID wsId,
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
