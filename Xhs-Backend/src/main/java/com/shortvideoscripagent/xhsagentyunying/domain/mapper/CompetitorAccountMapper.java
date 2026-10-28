package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.CompetitorAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * S3-10 竞品账号 Mapper。
 */
@Mapper
public interface CompetitorAccountMapper extends BaseMapper<CompetitorAccount> {

    /**
     * 按 id + workspace 查询（工作区隔离；越权按"未找到"处理）。
     */
    @Select("""
            SELECT * FROM app.competitor_account
            WHERE id = #{id} AND workspace_id = #{workspaceId}
            LIMIT 1
            """)
    CompetitorAccount selectByIdAndWorkspace(
            @Param("id") UUID id,
            @Param("workspaceId") String workspaceId);

    /**
     * 列出当前工作区的竞品账号（可按平台 / 品类 / 状态过滤）。
     * <p>
     * 参数为 null / 空串时直接不拼该条件（避免 PostgreSQL 无法推断 {@code ? IS NULL} 的参数类型）。
     * </p>
     */
    @Select("""
            <script>
            SELECT * FROM app.competitor_account
            <where>
              workspace_id = CAST(#{workspaceId} AS VARCHAR)
              <if test="platformCode != null and platformCode != ''">
                AND platform_code = CAST(#{platformCode} AS VARCHAR)
              </if>
              <if test="category != null and category != ''">
                AND category = CAST(#{category} AS VARCHAR)
              </if>
              <if test="status != null and status != ''">
                AND status = CAST(#{status} AS VARCHAR)
              </if>
            </where>
            ORDER BY updated_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CompetitorAccount> listByWorkspace(
            @Param("workspaceId") String workspaceId,
            @Param("platformCode") String platformCode,
            @Param("category") String category,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") long limit);

    /**
     * 统计当前工作区符合条件的竞品账号数。
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM app.competitor_account
            <where>
              workspace_id = CAST(#{workspaceId} AS VARCHAR)
              <if test="platformCode != null and platformCode != ''">
                AND platform_code = CAST(#{platformCode} AS VARCHAR)
              </if>
              <if test="category != null and category != ''">
                AND category = CAST(#{category} AS VARCHAR)
              </if>
              <if test="status != null and status != ''">
                AND status = CAST(#{status} AS VARCHAR)
              </if>
            </where>
            </script>
            """)
    long countByWorkspace(
            @Param("workspaceId") String workspaceId,
            @Param("platformCode") String platformCode,
            @Param("category") String category,
            @Param("status") String status);

    /**
     * 同 workspace + 平台 + 账号名是否已存在（避免重复录入）。
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM app.competitor_account
            WHERE workspace_id = #{workspaceId}
              AND platform_code = #{platformCode}
              AND LOWER(TRIM(account_name)) = LOWER(TRIM(#{accountName}))
            """)
    boolean existsByWorkspacePlatformName(
            @Param("workspaceId") String workspaceId,
            @Param("platformCode") String platformCode,
            @Param("accountName") String accountName);
}
